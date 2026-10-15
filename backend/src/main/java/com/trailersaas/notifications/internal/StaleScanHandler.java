package com.trailersaas.notifications.internal;

import com.trailersaas.jobs.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class StaleScanHandler implements JobHandler<StaleScanHandler.Payload> {
 record Payload(UUID scanId){}
 static final JobType<Payload> TYPE=new JobType<>("STALE_SCAN",1,Payload.class,"TENANT");
 final JdbcTemplate jdbc;final org.springframework.beans.factory.ObjectProvider<DurableJobPublisher> publisher;final StaleDetectorProperties settings;
 final StaleDetectorConfiguration.StaleDeploymentAuthorization authorization;final StaleDetectorMetrics metrics;
 final org.springframework.beans.factory.ObjectProvider<com.trailersaas.notifications.AlertNotifications> notifications;
 final org.springframework.beans.factory.ObjectProvider<com.trailersaas.notifications.StaleAlertRecipientPolicy> policies;
 StaleScanHandler(JdbcTemplate jdbc,org.springframework.beans.factory.ObjectProvider<DurableJobPublisher> publisher,StaleDetectorProperties settings,
  StaleDetectorConfiguration.StaleDeploymentAuthorization authorization,StaleDetectorMetrics metrics,
  org.springframework.beans.factory.ObjectProvider<com.trailersaas.notifications.AlertNotifications> notifications,
  org.springframework.beans.factory.ObjectProvider<com.trailersaas.notifications.StaleAlertRecipientPolicy> policies){
  this.jdbc=jdbc;this.publisher=publisher;this.settings=settings;this.authorization=authorization;this.metrics=metrics;
  this.notifications=notifications;this.policies=policies;
 }
 public JobType<Payload> type(){return TYPE;}
 public void validate(UUID tenant,JobAggregate aggregate,Payload payload){
  if(tenant==null||payload.scanId()==null||aggregate==null||!"TENANT".equals(aggregate.type())||!tenant.equals(aggregate.id())
   ||jdbc.queryForObject("select count(*) from tenants where id=?",Long.class,tenant)!=1)
   throw new JobFailure(JobFailure.Code.INVALID_SCOPE,false);
 }
 record Trailer(UUID id,boolean active,Instant seen,boolean finite){}
 record Batch(int evaluated,int stale,int created,int reused,int resolved,int insufficient,int future,boolean completed,Duration duration){}
 public void execute(JobExecution<Payload> job){
  if(!authorization.permits(job.tenantId()))return;
  try{
   var b=job.inTenantTransaction(()->process(job));if(b==null)return;
   metrics.add("trailers.evaluated",b.evaluated());metrics.add("trailers.stale",b.stale());metrics.add("alerts.created",b.created());
   metrics.add("alerts.reused",b.reused());metrics.add("alerts.resolved",b.resolved());metrics.add("observations.insufficient",b.insufficient());metrics.add("observations.future",b.future());
   if(b.completed()){metrics.count("scans.completed");metrics.count("tenants.processed");metrics.duration(b.duration());}
  }catch(RuntimeException failure){metrics.count("scans.failed");metrics.count("errors.retryable");throw failure;}
 }
 Batch process(JobExecution<Payload> job){
  UUID tenant=job.tenantId();var rows=jdbc.queryForList("select * from stale_trailer_scans where tenant_id=? for update",tenant);
  if(rows.isEmpty())return null;var scan=rows.getFirst();
  if(!job.jobId().equals(scan.get("job_id"))||!job.payload().scanId().equals(scan.get("scan_id"))||!Boolean.TRUE.equals(scan.get("in_progress")))return null;
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select lock_stale_tenant(?,?)",Boolean.class,tenant,job.jobId()))){
   jdbc.update("update stale_trailer_scans set in_progress=false,next_scan_at=clock_timestamp()+(?*interval '1 millisecond') where tenant_id=?",settings.scanInterval().toMillis(),tenant);return null;
  }
  var threshold=jdbc.queryForList("select stale_trailer_threshold_hours from tenant_settings where tenant_id=? for share",Integer.class,tenant);
  if(threshold.isEmpty())throw new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true);
  int hours=threshold.getFirst();UUID cursor=(UUID)scan.get("cursor_id"),bound=(UUID)scan.get("upper_bound");
  var trailers=bound==null?List.<Trailer>of():jdbc.query("""
   select id,active,last_seen_at,isfinite(last_seen_at) as finite from trailers
    where tenant_id=? and (?::uuid is null or id>?::uuid) and id<=? order by id limit ? for update
   """,(r,n)->new Trailer(r.getObject(1,UUID.class),r.getBoolean(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant(),r.getBoolean(4)),tenant,cursor,cursor,bound,settings.batchSize());
  Instant at=jdbc.queryForObject("select clock_timestamp()",(r,n)->r.getTimestamp(1).toInstant());
  int stale=0,created=0,reused=0,resolved=0,insufficient=0,future=0;
  for(var t:trailers){
   var condition=StaleCondition.evaluate(t.active(),t.seen(),t.seen()==null||t.finite(),at,hours);
   if(condition==StaleCondition.Result.NO_OBSERVATION||condition==StaleCondition.Result.INVALID_TIMESTAMP){insufficient++;continue;}
   if(condition==StaleCondition.Result.FUTURE){future++;continue;}
   if(condition==StaleCondition.Result.INACTIVE)continue;
   var open=jdbc.queryForList("select id,detected_threshold_hours from alerts where tenant_id=? and related_entity_id=? and alert_type='STALE_TRAILER' and status in ('OPEN','ACKNOWLEDGED') for update",tenant,t.id());
   if(condition==StaleCondition.Result.STALE){
    stale++;if(!open.isEmpty()){reused++;continue;}
    UUID alert=UUID.randomUUID();int inserted=jdbc.update("""
     insert into alerts(id,tenant_id,alert_type,severity,status,related_entity_type,related_entity_id,message_data,created_at,
      origin_actor_type,detector_job_id,detected_last_seen_at,detected_threshold_hours)
     values(?,?,'STALE_TRAILER','WARNING','OPEN','TRAILER',?,'{}',?,'SYSTEM',?,?,?)
     on conflict(tenant_id,related_entity_id) where alert_type='STALE_TRAILER' and status in ('OPEN','ACKNOWLEDGED') do nothing
     """,alert,tenant,t.id(),Timestamp.from(at),job.jobId(),Timestamp.from(t.seen()),hours);
    created+=inserted;
    if(inserted==1)for(var policy:policies)notifications.getObject().requestDelivery(tenant,alert,policy.code());
   }else if(!open.isEmpty()){
    String reason=((Number)open.getFirst().get("detected_threshold_hours")).intValue()==hours?"Physical observation is within the current Tenant stale threshold":"Current Tenant threshold no longer classifies this observation as stale";
    resolved+=jdbc.update("update alerts set status='RESOLVED',version=version+1,resolution_reason=?,resolution_job_id=? where tenant_id=? and id=?",reason,job.jobId(),tenant,open.getFirst().get("id"));
   }
  }
  UUID next=trailers.isEmpty()?cursor:trailers.getLast().id();
  boolean more=next!=null&&bound!=null&&jdbc.queryForObject("select count(*) from (select 1 from trailers where tenant_id=? and id>? and id<=? limit 1) remaining",Long.class,tenant,next,bound)>0;
  if(more){
   var id=publisher.getObject().enqueue(tenant,TYPE,new JobAggregate("TENANT",tenant),job.payload(),at,null);
   jdbc.update("update stale_trailer_scans set cursor_id=?,job_id=? where tenant_id=?",next,id,tenant);
  }else jdbc.update("update stale_trailer_scans set cursor_id=?,in_progress=false,completed_at=clock_timestamp(),next_scan_at=clock_timestamp()+(?*interval '1 millisecond') where tenant_id=?",next,settings.scanInterval().toMillis(),tenant);
  return new Batch(trailers.size(),stale,created,reused,resolved,insufficient,future,!more,Duration.between(((Timestamp)scan.get("started_at")).toInstant(),at));
 }
}
