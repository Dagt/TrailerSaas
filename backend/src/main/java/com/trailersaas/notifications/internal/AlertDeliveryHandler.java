package com.trailersaas.notifications.internal;

import com.trailersaas.jobs.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class AlertDeliveryHandler implements JobHandler<AlertDeliveryHandler.Payload> {
 record Payload(UUID recipientId){}
 static final JobType<Payload> TYPE=new JobType<>("ALERT_FCM",1,Payload.class,"ALERT");
 record Target(UUID id,UUID user,String token,long revision){@Override public String toString(){return "Push target [redacted]";}}
 record Envelope(UUID alert,UUID user,String policy){}
 final JdbcTemplate jdbc;final RecipientPolicies policies;final NotificationEligibility eligibility;final FcmTransport fcm;
 AlertDeliveryHandler(JdbcTemplate jdbc,RecipientPolicies policies,NotificationEligibility eligibility,FcmTransport fcm){this.jdbc=jdbc;this.policies=policies;this.eligibility=eligibility;this.fcm=fcm;}
 public JobType<Payload> type(){return TYPE;}
 public void validate(UUID tenant,JobAggregate aggregate,Payload payload){
  if(payload.recipientId()==null||aggregate==null||!"ALERT".equals(aggregate.type())||jdbc.queryForObject("select count(*) from alert_recipients where tenant_id=? and id=? and alert_id=?",Long.class,tenant,payload.recipientId(),aggregate.id())!=1L)throw new JobFailure(JobFailure.Code.INVALID_SCOPE,false);
 }
 Envelope envelope(UUID tenant,UUID recipient){return jdbc.query("select alert_id,user_id,policy_code from alert_recipients where tenant_id=? and id=?",(r,n)->new Envelope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)),tenant,recipient).stream().findFirst().orElseThrow(()->new JobFailure(JobFailure.Code.INVALID_SCOPE,false));}
 String eligibility(UUID tenant,Envelope e){
  if("RESOLVED".equals(jdbc.queryForObject("select status from alerts where tenant_id=? and id=?",String.class,tenant,e.alert())))return "ALERT_INELIGIBLE";
  var c=eligibility.context(tenant,e.alert());return eligibility.authorized(c,e.user())&&policies.permits(e.policy(),c,e.user())?null:"SUPPRESSED_AUTHORIZATION";
 }
 void state(UUID tenant,UUID recipient,String status){jdbc.update("update alert_recipients set push_status=?,updated_at=clock_timestamp() where tenant_id=? and id=?",status,tenant,recipient);}
 public void execute(JobExecution<Payload> job){
  UUID tenant=job.tenantId(),recipient=job.payload().recipientId();
  var targets=job.inTenantTransaction(()->{
   var e=envelope(tenant,recipient);var denied=eligibility(tenant,e);if(denied!=null){state(tenant,recipient,denied);return List.<Target>of();}
   var all=jdbc.query("select * from alert_delivery_devices(?,?)",(r,n)->new Target(r.getObject("device_id",UUID.class),r.getObject("user_id",UUID.class),r.getString("fcm_token"),r.getLong("token_version")),job.jobId(),recipient);
   var pending=new ArrayList<Target>();for(var t:all){
    jdbc.update("insert into alert_deliveries(id,tenant_id,recipient_id,user_id,device_id,token_version,status) values(?,?,?,?,?,?,'PENDING') on conflict(tenant_id,recipient_id,device_id) do nothing",UUID.randomUUID(),tenant,recipient,t.user(),t.id(),t.revision());
    String status=jdbc.queryForObject("select status from alert_deliveries where tenant_id=? and recipient_id=? and device_id=?",String.class,tenant,recipient,t.id());
    if(Set.of("PENDING","ATTEMPTED","RETRYABLE_FAILURE").contains(status)){
     jdbc.update("update alert_deliveries set status='ATTEMPTED',token_version=?,attempt_count=attempt_count+1,updated_at=clock_timestamp() where tenant_id=? and recipient_id=? and device_id=?",t.revision(),tenant,recipient,t.id());pending.add(t);
    }
   }
   if(all.isEmpty())jdbc.update("update alert_recipients set push_status='NO_ELIGIBLE_DEVICE',updated_at=clock_timestamp() where tenant_id=? and id=? and push_status in ('PENDING','ATTEMPTED','RETRYABLE_FAILURE')",tenant,recipient);
   else if(!pending.isEmpty())state(tenant,recipient,"ATTEMPTED");return List.copyOf(pending);
  });
  if(targets.isEmpty()){
   boolean failed=job.inTenantTransaction(()->{
    String state=jdbc.queryForObject("select push_status from alert_recipients where tenant_id=? and id=?",String.class,tenant,recipient);
    return Set.of("PERMANENT_FAILURE","PARTIAL_FAILURE").contains(state)&&jdbc.queryForObject("select count(*) from alert_deliveries where tenant_id=? and recipient_id=? and status='PERMANENT_FAILURE'",Long.class,tenant,recipient)>0;
   });
   if(failed)throw new JobFailure(JobFailure.Code.HANDLER_PERMANENT,false);
   return;
  }
  // Revalidate immediately before network, after committing attempt metadata.
  boolean allowed=job.inTenantTransaction(()->{var denied=eligibility(tenant,envelope(tenant,recipient));if(denied!=null){state(tenant,recipient,denied);return false;}
   var current=jdbc.queryForList("select device_id,token_version from alert_delivery_devices(?,?)",job.jobId(),recipient);
   return targets.stream().allMatch(t->current.stream().anyMatch(r->t.id().equals(r.get("device_id"))&&t.revision()==((Number)r.get("token_version")).longValue()));});
  if(!allowed)throw new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true);
  var response=fcm.send(targets.stream().map(Target::token).toList(),Map.of("kind","ALERT","tenantId",tenant.toString(),"alertId",job.aggregate().id().toString()));
  if(response==null||response.size()!=targets.size()||response.stream().anyMatch(Objects::isNull))throw new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true);
  var outcomes=job.inTenantTransaction(()->{
   for(int i=0;i<targets.size();i++){var t=targets.get(i);var r=response.get(i);
    if(r.outcome()==null||!Set.of("FCM_ACCEPTED","FCM_TRANSIENT","FCM_PERMANENT","TOKEN_INVALID","TRANSPORT_DISABLED").contains(r.code()))throw new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true);
    var outcome=r.outcome();
    if(outcome==FcmTransport.Outcome.INVALIDATED_TOKEN){
     boolean invalidated=Boolean.TRUE.equals(jdbc.queryForObject("select invalidate_alert_device(?,?,?,?)",Boolean.class,job.jobId(),recipient,t.id(),t.revision()));
     // An old invalid-token receipt must not invalidate or strand a newly rotated token.
     if(!invalidated&&jdbc.queryForObject("select count(*) from alert_delivery_devices(?,?) where device_id=? and token_version<>?",Long.class,job.jobId(),recipient,t.id(),t.revision())>0)outcome=FcmTransport.Outcome.RETRYABLE_FAILURE;
    }
    jdbc.update("update alert_deliveries set status=?,last_code=?,updated_at=clock_timestamp() where tenant_id=? and recipient_id=? and device_id=? and token_version=?",outcome.name(),outcome==r.outcome()?r.code():"FCM_TRANSIENT",tenant,recipient,t.id(),t.revision());
   }
   var statuses=jdbc.queryForList("select status from alert_deliveries where tenant_id=? and recipient_id=?",String.class,tenant,recipient);
   String state=statuses.stream().allMatch("ACCEPTED_BY_FCM"::equals)?"ACCEPTED_BY_FCM":statuses.stream().anyMatch(s->Set.of("PENDING","ATTEMPTED","RETRYABLE_FAILURE").contains(s))?"RETRYABLE_FAILURE":statuses.stream().anyMatch("ACCEPTED_BY_FCM"::equals)?"PARTIAL_FAILURE":statuses.stream().allMatch("INVALIDATED_TOKEN"::equals)?"INVALIDATED_TOKEN":"PERMANENT_FAILURE";
   state(tenant,recipient,state);return List.copyOf(statuses);
  });
  // Use all durable destination receipts, including failures from previous attempts.
  if(outcomes.stream().anyMatch(s->Set.of("PENDING","ATTEMPTED","RETRYABLE_FAILURE").contains(s)))throw new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true);
  if(outcomes.contains("PERMANENT_FAILURE"))throw new JobFailure(JobFailure.Code.HANDLER_PERMANENT,false);
 }
}
