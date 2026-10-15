package com.trailersaas.notifications.internal;

import com.trailersaas.identity.TenantTransactionExecutor;
import com.trailersaas.jobs.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class StaleScanScheduler {
 final StaleDetectorProperties settings;
 final StaleDetectorConfiguration.StaleDeploymentAuthorization authorization;
 final TenantTransactionExecutor transactions;final JdbcTemplate jdbc;final DurableJobPublisher publisher;final StaleDetectorMetrics metrics;
 private final AtomicInteger roundRobin=new AtomicInteger();private final AtomicBoolean polling=new AtomicBoolean();
 StaleScanScheduler(StaleDetectorProperties settings,StaleDetectorConfiguration.StaleDeploymentAuthorization authorization,
  TenantTransactionExecutor transactions,JdbcTemplate jdbc,DurableJobPublisher publisher,StaleDetectorMetrics metrics){
  this.settings=settings;this.authorization=authorization;this.transactions=transactions;this.jdbc=jdbc;this.publisher=publisher;this.metrics=metrics;
 }
 @Scheduled(fixedDelayString="${trailersaas.stale-detector.poll-interval:30s}",initialDelayString="${trailersaas.stale-detector.poll-interval:30s}")
 void poll(){
  if(!settings.enabled()||settings.tenants().isEmpty()||!polling.compareAndSet(false,true))return;
  try{schedule(settings.tenants().get(Math.floorMod(roundRobin.getAndIncrement(),settings.tenants().size())));}
  catch(RuntimeException failure){metrics.count("scans.failed");org.slf4j.LoggerFactory.getLogger(getClass()).atWarn().addKeyValue("event","stale_schedule_unavailable").log("Detector schedule unavailable; durable work remains recoverable");}
  finally{polling.set(false);}
 }
 record ScheduleResult(boolean started,boolean resumed,long dead){}
 boolean schedule(UUID tenant){
  if(!authorization.permits(tenant))return false;
  var result=transactions.execute(tenant,()->{
   jdbc.queryForObject("select set_config('statement_timeout','5s',true)",String.class);jdbc.queryForObject("select set_config('lock_timeout','1s',true)",String.class);
   if(!"ACTIVE".equals(jdbc.queryForObject("select status from tenants where id=?",String.class,tenant)))return new ScheduleResult(false,false,0);
   jdbc.update("insert into stale_trailer_scans(tenant_id) values(?) on conflict do nothing",tenant);
   var row=jdbc.queryForMap("select *,next_scan_at<=clock_timestamp() as due from stale_trailer_scans where tenant_id=? for update",tenant);
   long dead=jdbc.queryForObject("select count(*) from outbox_jobs where tenant_id=? and job_type='STALE_SCAN' and status='DEAD'",Long.class,tenant);
   var job=(UUID)row.get("job_id");String status=job==null?null:jdbc.queryForObject("select status from outbox_jobs where tenant_id=? and id=?",String.class,tenant,job);
   if(!Boolean.TRUE.equals(row.get("due"))||Set.of("PENDING","PROCESSING").contains(status==null?"":status))return new ScheduleResult(false,false,dead);
   boolean resume=Boolean.TRUE.equals(row.get("in_progress"));UUID scan=resume?(UUID)row.get("scan_id"):UUID.randomUUID();
   if(!resume){
    var bound=jdbc.queryForList("select id from trailers where tenant_id=? order by id desc limit 1",UUID.class,tenant).stream().findFirst().orElse(null);
    jdbc.update("update stale_trailer_scans set scan_id=?,in_progress=true,cursor_id=null,upper_bound=?,started_at=clock_timestamp(),completed_at=null where tenant_id=?",scan,bound,tenant);
   }
   var id=publisher.enqueue(tenant,StaleScanHandler.TYPE,new JobAggregate("TENANT",tenant),new StaleScanHandler.Payload(scan),Instant.now(),null);
   jdbc.update("update stale_trailer_scans set job_id=?,next_scan_at=clock_timestamp()+(? * interval '1 millisecond') where tenant_id=?",id,settings.scanInterval().toMillis(),tenant);
   return new ScheduleResult(true,resume,dead);
  });
  metrics.dead(tenant,result.dead());if(result.started())metrics.count(result.resumed()?"scans.recovered":"scans.started");return result.started();
 }
}
