package com.trailersaas.jobs.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.jobs.*;
import com.trailersaas.notifications.*;
import com.trailersaas.notifications.internal.*;
import com.trailersaas.trailers.TrailerMovementProjection;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({StaleDetectorTestAccess.class,DeliveryTestConfiguration.class,StaleDetectorPostgresTests.StalePolicy.class})
class StaleDetectorPostgresTests extends JobsFixture {
 static final List<UUID> ALLOWED=java.util.stream.Stream.generate(UUID::randomUUID).limit(256).toList();
 static final AtomicInteger NEXT=new AtomicInteger();
 @DynamicPropertySource static void deployment(DynamicPropertyRegistry r){
  var ids=String.join(",",ALLOWED.stream().map(UUID::toString).toList());
  r.add("trailersaas.jobs.enabled",()->"true");r.add("trailersaas.jobs.tenants",()->ids);r.add("trailersaas.jobs.poll-interval",()->"1h");
  r.add("trailersaas.stale-detector.enabled",()->"true");r.add("trailersaas.stale-detector.tenants",()->ids);
  r.add("trailersaas.stale-detector.poll-interval",()->"1h");r.add("trailersaas.stale-detector.scan-interval",()->"1m");r.add("trailersaas.stale-detector.batch-size",()->"2");
 }
 @Autowired StaleDetectorTestAccess.Harness scans;
 @Autowired DeliveryTestConfiguration.Probe push;
 @Autowired TrailerMovementProjection movements;
 @Autowired io.micrometer.core.instrument.MeterRegistry meters;
 @MockitoBean FirebaseTokenVerifier verifier;
 @Value("${local.server.port}")int port;
 UUID user,trailer,location;
 @BeforeEach void prepareStale()throws Exception{
  tenant=ALLOWED.get(NEXT.getAndIncrement());other=ALLOWED.get(NEXT.getAndIncrement());
  for(var id:List.of(tenant,other)){
   db.execute("insert into tenants values(?,?,'ACTIVE',now(),now())",id,"Allowed-"+id);
   db.execute("insert into tenant_settings values(?,72,null,now(),now(),0)",id);
  }
  customer=customer(tenant);var f=new TrailerFixtures(flyway);location=f.location(tenant);trailer=asset(tenant,customer,Duration.ofHours(80));
  user=db.user();db.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
  when(verifier.verify("stale")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));push.reset(user);push.recipients.clear();
 }
 @TestConfiguration(proxyBeanMethods=false) static class StalePolicy {
  @Bean StaleAlertRecipientPolicy approvedOnlyInTest(DeliveryTestConfiguration.Probe p){return new StaleAlertRecipientPolicy(){public String code(){return "STALE_TEST_APPROVED";}public Set<UUID> recipients(Context c){return Set.copyOf(p.recipients);}};}
 }
 UUID asset(UUID owner,UUID designated,Duration age)throws Exception{
  var f=new TrailerFixtures(flyway);var id=f.trailer(owner,"Stale-"+UUID.randomUUID(),designated);
  db.execute("update trailers set location_state='AT_LOCATION',current_location_id=?,last_known_location_id=?,last_seen_at=? where id=?",owner.equals(tenant)?location:f.location(owner),owner.equals(tenant)?location:null,java.sql.Timestamp.from(Instant.now().minus(age)),id);return id;
 }
 DurableJobWorker detectorWorker(UUID owner){return worker(new JobsProperties(true,List.of(owner),Duration.ofSeconds(5),4,4,Duration.ofSeconds(5),Duration.ofSeconds(15),5,Duration.ofSeconds(1),Duration.ofSeconds(2)),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());}
 void drain(UUID owner)throws Exception{
  var w=detectorWorker(owner);for(int i=0;i<40;i++){
   long pending=(Long)db.value("select count(*) from outbox_jobs where tenant_id=? and status in ('PENDING','PROCESSING')",owner);
   if(pending==0)return;w.pollOnce();
  }throw new AssertionError("Bounded test drain did not settle");
 }
 void scan()throws Exception{assertThat(scans.schedule(tenant)).isTrue();drain(tenant);}
 void nextCycle()throws Exception{transactions.execute(tenant,()->jdbc.update("update stale_trailer_scans set next_scan_at=clock_timestamp() where tenant_id=?",tenant));scan();}
 UUID alert()throws Exception{return (UUID)db.value("select id from alerts where tenant_id=? and alert_type='STALE_TRAILER' order by created_at desc limit 1",tenant);}
 long alerts()throws Exception{return (Long)db.value("select count(*) from alerts where tenant_id=?",tenant);}
 @Test void actualDurableScanCreatesSystemEvidenceWithoutAnyPhysicalOrMobileSideEffect()throws Exception{
  var before=db.value("select row_to_json(t)::text from trailers t where id=?",trailer);
  assertThat(scans.schedule(tenant)).isTrue();var claim=store.claim(tenant,1,Duration.ofSeconds(15)).claims().getFirst();executeClaim(claim);assertThat(store.succeed(claim)).isTrue();var id=alert();
  assertThat(db.value("select alert_type||'/'||severity||'/'||status||'/'||origin_actor_type from alerts where id=?",id)).isEqualTo("STALE_TRAILER/WARNING/OPEN/SYSTEM");
  assertThat(db.value("select client_operation_id from alerts where id=?",id)).isNull();assertThat(db.value("select detected_threshold_hours from alerts where id=?",id)).isEqualTo(72);
  assertThat(db.value("select row_to_json(t)::text from trailers t where id=?",trailer)).isEqualTo(before);
  assertThat(db.value("select count(*) from trailer_movement_events where tenant_id=?",tenant)).isEqualTo(0L);
  assertThat(db.value("select count(*) from processed_client_operations where tenant_id=?",tenant)).isEqualTo(0L);
  assertThat(db.value("select count(*) from alert_recipients where tenant_id=?",tenant)).isEqualTo(0L);
 }
 @Test void tenRepeatedScansDoNotRewriteOriginalOrSpamAcknowledgedOccurrence()throws Exception{
  scan();var id=alert();db.execute("update alerts set status='ACKNOWLEDGED',acknowledged_by=?,version=1 where id=?",user,id);
  var original=db.value("select row_to_json(a)::text from alerts a where id=?",id);for(int i=0;i<10;i++)nextCycle();
  assertThat(alerts()).isEqualTo(1L);assertThat(db.value("select row_to_json(a)::text from alerts a where id=?",id)).isEqualTo(original);
 }
 @Test void thresholdsAreIndependentAndCurrentAtEveryEvaluation()throws Exception{
  db.execute("update tenant_settings set stale_trailer_threshold_hours=96 where tenant_id=?",other);
  asset(other,customer(other),Duration.ofHours(80));scan();assertThat(scans.schedule(other)).isTrue();drain(other);
  assertThat(db.value("select count(*) from alerts where tenant_id=?",other)).isEqualTo(0L);assertThat(alerts()).isEqualTo(1L);
  db.execute("update tenant_settings set stale_trailer_threshold_hours=96 where tenant_id=?",tenant);nextCycle();
  assertThat(db.value("select status||'/'||resolution_actor_type from alerts where id=?",alert())).isEqualTo("RESOLVED/SYSTEM");
  assertThat(db.value("select resolution_threshold_hours from alerts where id=?",alert())).isEqualTo(96);
 }
 @Test void noObservationFutureInfiniteAndInactiveProduceOnlyTechnicalDiagnostics()throws Exception{
  db.execute("update trailers set last_seen_at=null where id=?",trailer);
  var future=asset(tenant,customer,Duration.ofHours(-1));var inactive=asset(tenant,customer,Duration.ofHours(100));var infinite=asset(tenant,customer,Duration.ofHours(100));
  db.execute("update trailers set active=false where id=?",inactive);db.execute("update trailers set last_seen_at='-infinity' where id=?",infinite);scan();assertThat(alerts()).isZero();
 }
 @Test void boundedPagesCommitContinuationAndDoNotStarveIndependentTenant()throws Exception{
  for(int i=0;i<6;i++)asset(tenant,customer,Duration.ofHours(80));scan();assertThat(alerts()).isEqualTo(7L);
  assertThat(db.value("select count(*) from outbox_jobs where tenant_id=? and job_type='STALE_SCAN'",tenant)).isEqualTo(4L);
  assertThat(db.value("select in_progress from stale_trailer_scans where tenant_id=?",tenant)).isEqualTo(false);
 }
 @Test void simultaneousSchedulersAndWorkersHaveOneCurrentChainAndOneOccurrence()throws Exception{
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->scans.schedule(tenant));var b=pool.submit(()->scans.schedule(tenant));assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(true,false);
   var one=detectorWorker(tenant);var two=detectorWorker(tenant);var x=pool.submit(one::pollOnce);var y=pool.submit(two::pollOnce);x.get();y.get();
  }assertThat(alerts()).isEqualTo(1L);
 }
 @Test void latePhysicalMovementStillOutsideThresholdAndAdministrativeEditsDoNotResolve()throws Exception{
  scan();transactions.execute(tenant,()->movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minus(Duration.ofHours(79))))));
  db.execute("update trailers set operational_number='Admin edited',updated_at=clock_timestamp() where id=?",trailer);nextCycle();assertThat(db.value("select status from alerts where id=?",alert())).isEqualTo("OPEN");
 }
 @Test void realRecentDepartureResolvesInTransitPreservesAcknowledgementAndAllowsRecurrence()throws Exception{
  scan();var first=alert();db.execute("update alerts set status='ACKNOWLEDGED',acknowledged_by=?,version=1 where id=?",user,first);var ack=db.value("select acknowledged_at from alerts where id=?",first);
  transactions.execute(tenant,()->movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minusSeconds(1)))));
  nextCycle();assertThat(db.value("select status||'/'||resolution_actor_type from alerts where id=?",first)).isEqualTo("RESOLVED/SYSTEM");assertThat(db.value("select resolved_by from alerts where id=?",first)).isNull();
  assertThat(db.value("select acknowledged_at from alerts where id=?",first)).isEqualTo(ack);assertThat(db.value("select location_state from trailers where id=?",trailer)).isEqualTo("IN_TRANSIT");
  db.execute("update trailers set last_seen_at=clock_timestamp()-interval '80 hours' where id=?",trailer);nextCycle();assertThat(alerts()).isEqualTo(2L);assertThat(alert()).isNotEqualTo(first);
 }
 TrailerMovementProjection.Context context(Instant at){return new TrailerMovementProjection.Context(at,new TrailerMovementProjection.Provenance(TrailerMovementProjection.ActorType.HUMAN_USER,TrailerMovementProjection.SourceType.USER_ACTION,user),null,null,null,null,null);}
 @Test void acceptedCompiledTestPolicyUsesExistingRecipientOutboxAndFcmHandler()throws Exception{
  push.recipients.add(user);
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/devices")).header("Authorization","Bearer stale").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"platform\":\"ANDROID\",\"fcmToken\":\"stale-private-test-token\"}"));
  assertThat(HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);scan();
  assertThat(push.calls.get()).isEqualTo(1);assertThat(db.value("select push_status from alert_recipients where tenant_id=?",tenant)).isEqualTo("ACCEPTED_BY_FCM");
  assertThat(db.value("select payload::text from outbox_jobs where tenant_id=? and job_type='ALERT_FCM'",tenant).toString()).doesNotContain("stale-private-test-token");
 }
 void executeClaim(JobClaim c){executeTyped(registry.decode(c.type(),c.payload()),c);}
 @Test void failedTenantCannotBlockAnotherTenantAndRecoversThroughExistingRetry()throws Exception{
  asset(other,customer(other),Duration.ofHours(80));assertThat(scans.schedule(tenant)).isTrue();assertThat(scans.schedule(other)).isTrue();
  db.execute("delete from tenant_settings where tenant_id=?",tenant);detectorWorker(tenant).pollOnce();
  assertThat(alerts()).isZero();drain(other);assertThat(db.value("select count(*) from alerts where tenant_id=?",other)).isEqualTo(1L);
  db.execute("insert into tenant_settings values(?,72,null,now(),now(),0)",tenant);
  var id=(UUID)db.value("select job_id from stale_trailer_scans where tenant_id=?",tenant);due(id);drain(tenant);assertThat(alerts()).isEqualTo(1L);
 }
 @Test void technicalMetricsReflectCommittedEffectsWithoutIdentifierLabels()throws Exception{
  double before=meters.find("stale.detector.alerts.created").counter()==null?0:meters.get("stale.detector.alerts.created").counter().count();
  scan();assertThat(meters.get("stale.detector.alerts.created").counter().count()).isEqualTo(before+1);
  assertThat(meters.get("stale.detector.scan.duration").timer().count()).isPositive();
  for(var meter:meters.getMeters())if(meter.getId().getName().startsWith("stale.detector."))assertThat(meter.getId().getTags()).isEmpty();
 }
 @Test void databaseUniqueIndexRejectsASecondUnresolvedStaleEvenWithValidLiveEvidence()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();var c=store.claim(tenant,1,Duration.ofSeconds(15)).claims().getFirst();executeClaim(c);
  // Retain the live claim and explicitly restore this technical test context to
  // exercise the index rather than merely the normal handler's SELECT-before-write.
  transactions.execute(tenant,()->jdbc.update("update stale_trailer_scans set in_progress=true where tenant_id=?",tenant));
  assertThatThrownBy(()->store.fencedTransaction(c,()->jdbc.update("""
   insert into alerts(id,tenant_id,alert_type,severity,status,related_entity_type,related_entity_id,message_data,created_at,
    origin_actor_type,detector_job_id,detected_last_seen_at,detected_threshold_hours)
   select ?,tenant_id,alert_type,severity,'OPEN',related_entity_type,related_entity_id,message_data,created_at,
    origin_actor_type,detector_job_id,detected_last_seen_at,detected_threshold_hours from alerts where tenant_id=?
   """,UUID.randomUUID(),tenant))).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  assertThat(alerts()).isEqualTo(1L);
 }
 @Test void busyPhysicalProjectionRetriesWithoutSkippingTrailerThenUsesCommittedObservation()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var move=pool.submit(()->transactions.execute(tenant,()->{var result=movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minusSeconds(1))));entered.countDown();try{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test deadline");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return result;}));
   assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();try{detectorWorker(tenant).pollOnce();}finally{release.countDown();}move.get();
  }
  var id=(UUID)db.value("select job_id from stale_trailer_scans where tenant_id=?",tenant);assertThat(status(id)).isEqualTo("PENDING");assertThat(db.value("select cursor_id from stale_trailer_scans where tenant_id=?",tenant)).isNull();due(id);drain(tenant);assertThat(alerts()).isZero();
 }
 @Test void committedBatchSurvivesLostAckAndExpiredLeaseWithoutDuplicatingAlert()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();var c=store.claim(tenant,1,Duration.ofSeconds(2)).claims().getFirst();executeClaim(c);
  assertThat(alerts()).isEqualTo(1L);assertThat(status(c.id())).isEqualTo("PROCESSING");expired(c);drain(tenant);
  assertThat(status(c.id())).isEqualTo("SUCCEEDED");assertThat(alerts()).isEqualTo(1L);assertThat(store.succeed(c)).isFalse();
 }
 @Test void failedContinuationWriteRollsBackAlertsAndCursorThenExistingWorkerRetries()throws Exception{
  for(int i=0;i<3;i++)asset(tenant,customer,Duration.ofHours(80));assertThat(scans.schedule(tenant)).isTrue();var c=store.claim(tenant,1,Duration.ofSeconds(15)).claims().getFirst();
  db.execute("revoke insert on outbox_jobs from "+RUNTIME_USERNAME);
  try{assertThatThrownBy(()->executeClaim(c)).isInstanceOf(org.springframework.dao.DataAccessException.class);}
  finally{db.execute("grant insert on outbox_jobs to "+RUNTIME_USERNAME);}
  assertThat(alerts()).isZero();assertThat(db.value("select cursor_id from stale_trailer_scans where tenant_id=?",tenant)).isNull();
  assertThat(store.fail(c,new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true),Duration.ofSeconds(1))).isEqualTo(OutboxStore.Outcome.RETRY);due(c.id());drain(tenant);assertThat(alerts()).isEqualTo(4L);
 }
 @Test void deadJobIsNotResurrectedAndNextAuthorizedScheduleResumesDurableCursor()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();var c=store.claim(tenant,1,Duration.ofSeconds(15)).claims().getFirst();
  assertThat(store.fail(c,new JobFailure(JobFailure.Code.HANDLER_PERMANENT,false),Duration.ofSeconds(1))).isEqualTo(OutboxStore.Outcome.DEAD);
  transactions.execute(tenant,()->jdbc.update("update stale_trailer_scans set next_scan_at=clock_timestamp() where tenant_id=?",tenant));assertThat(scans.schedule(tenant)).isTrue();
  assertThat(db.value("select job_id from stale_trailer_scans where tenant_id=?",tenant)).isNotEqualTo(c.id());drain(tenant);assertThat(alerts()).isEqualTo(1L);assertThat(status(c.id())).isEqualTo("DEAD");
 }
 @Test void recentRealMovementBetweenEnqueueAndClaimCannotCreateAnObsoleteOccurrence()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();transactions.execute(tenant,()->movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minusSeconds(1)))));
  drain(tenant);assertThat(alerts()).isZero();
 }
 @Test void currentTenantDisabledAfterScheduleCannotCreateAlertsOrBorrowAnotherTenant()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();db.execute("update tenants set status='DISABLED' where id=?",tenant);drain(tenant);assertThat(alerts()).isZero();
  var foreign=db.tenant();assertThat(scans.schedule(foreign)).isFalse();
  assertThat(transactions.execute(other,()->jdbc.queryForList("select * from stale_trailer_scans"))).isEmpty();
  assertThat(transactions.execute(other,()->jdbc.queryForObject("select lock_stale_tenant(?,?)",Boolean.class,tenant,UUID.randomUUID()))).isFalse();
  assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'tenants','UPDATE')",Boolean.class)).isFalse();
 }
 @Test void acceptedMaximumThresholdDoesNotOverflowPostgresqlDateArithmetic()throws Exception{
  db.execute("update tenant_settings set stale_trailer_threshold_hours=? where tenant_id=?",Integer.MAX_VALUE,tenant);db.execute("update trailers set last_seen_at='0001-01-01' where id=?",trailer);scan();assertThat(alerts()).isZero();
 }
 @Test void deactivationOrCorruptedObservationCannotSilentlyResolveExistingCondition()throws Exception{
  scan();var id=alert();db.execute("update trailers set active=false,updated_at=clock_timestamp() where id=?",trailer);nextCycle();assertThat(db.value("select status from alerts where id=?",id)).isEqualTo("OPEN");
  db.execute("update trailers set active=true,last_seen_at=clock_timestamp()+interval '1 day' where id=?",trailer);nextCycle();assertThat(db.value("select status from alerts where id=?",id)).isEqualTo("OPEN");
 }
 @Test void independentRuntimeWorkerCannotReadOrClaimAnotherTenantScan()throws Exception{
  assertThat(scans.schedule(tenant)).isTrue();assertThat(store.claim(other,5,Duration.ofSeconds(15)).claims()).isEmpty();
  assertThat(transactions.execute(other,()->jdbc.queryForList("select * from alerts"))).isEmpty();drain(tenant);assertThat(alerts()).isEqualTo(1L);
 }
 @Test void malformedOrForeignScanJobsFailClosedWithoutSystemAlerts()throws Exception{
  for(var payload:List.of("{\"schemaVersion\":2,\"data\":{\"scanId\":\""+UUID.randomUUID()+"\"}}","{\"schemaVersion\":1,\"data\":{\"scanId\":\""+UUID.randomUUID()+"\",\"trailerId\":\""+trailer+"\"}}")){
   transactions.execute(tenant,()->jdbc.update("insert into outbox_jobs(id,tenant_id,job_type,aggregate_type,aggregate_id,payload,status,available_at,max_attempts) values(?,?,'STALE_SCAN','TENANT',?,?::jsonb,'PENDING',clock_timestamp(),5)",UUID.randomUUID(),tenant,tenant,payload));
  }
  transactions.execute(tenant,()->jdbc.update("insert into outbox_jobs(id,tenant_id,job_type,aggregate_type,aggregate_id,payload,status,available_at,max_attempts) values(?,?,'STALE_SCAN','TENANT',?,?::jsonb,'PENDING',clock_timestamp(),5)",UUID.randomUUID(),tenant,other,"{\"schemaVersion\":1,\"data\":{\"scanId\":\""+UUID.randomUUID()+"\"}}"));
  drain(tenant);assertThat(alerts()).isZero();assertThat(db.value("select count(*) from outbox_jobs where tenant_id=? and status='DEAD'",tenant)).isEqualTo(3L);
 }
 HttpResponse<String> request(String method,String suffix,String body)throws Exception{
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+suffix)).header("Authorization","Bearer stale").header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
  return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void existingInboxExposesTypedSystemEvidenceWithoutGrantingHumanWrites()throws Exception{
  scan();var id=alert();var page=request("GET","/alerts?alertType=STALE_TRAILER&limit=1",null);
  assertThat(page.statusCode()).as(page.body()).isEqualTo(200);assertThat(com.jayway.jsonpath.JsonPath.<String>read(page.body(),"$.items[0].originActorType")).isEqualTo("SYSTEM");
  assertThat(com.jayway.jsonpath.JsonPath.<Integer>read(page.body(),"$.items[0].detection.thresholdHours")).isEqualTo(72);
  assertThat(page.body()).doesNotContain("clientOperationId","fcmToken","firebase_uid","message_data");
  for(var action:List.of("acknowledgement","resolution"))assertThat(request("POST","/alerts/"+id+"/"+action,"{\"expectedVersion\":0,\"reason\":\"Not authorized\"}").statusCode()).isEqualTo(403);
  transactions.execute(tenant,()->movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minusSeconds(1)))));nextCycle();
  var detail=request("GET","/alerts/"+id,null);assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
  assertThat(com.jayway.jsonpath.JsonPath.<String>read(detail.body(),"$.resolutionActorType")).isEqualTo("SYSTEM");assertThat(com.jayway.jsonpath.JsonPath.<Object>read(detail.body(),"$.resolvedBy")).isNull();
  assertThat(com.jayway.jsonpath.JsonPath.<String>read(detail.body(),"$.relatedResource.locationState")).isEqualTo("IN_TRANSIT");
 }
 @Test void foreignAlertIsInvisibleAndRevokedMembershipCannotReadItsOwnHistory()throws Exception{
  scan();var id=alert();var original=tenant;db.membership(other,user,"ACTIVE","TENANT_ADMIN");tenant=other;
  assertThat(request("GET","/alerts/"+id,null).statusCode()).isEqualTo(404);var page=request("GET","/alerts?alertType=STALE_TRAILER",null);assertThat(page.statusCode()).isEqualTo(200);assertThat(com.jayway.jsonpath.JsonPath.<List<?>>read(page.body(),"$.items")).isEmpty();
  tenant=original;db.execute("update tenant_memberships set status='DISABLED' where tenant_id=? and user_id=?",tenant,user);assertThat(request("GET","/alerts/"+id,null).statusCode()).isEqualTo(403);
 }
 @Test void sqlRejectsEvidenceRewritesMobileIdentityBorrowingAndTerminalResurrection()throws Exception{
  scan();var id=alert();
  for(var assignment:List.of("detected_threshold_hours=96","detected_last_seen_at=created_at","origin_actor_type='HUMAN_USER'","client_operation_id='"+UUID.randomUUID()+"'","detector_job_id='"+UUID.randomUUID()+"'"))
   assertThatThrownBy(()->transactions.execute(tenant,()->jdbc.update("update alerts set "+assignment+",version=version+1 where tenant_id=? and id=?",tenant,id))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  transactions.execute(tenant,()->movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minusSeconds(1)))));nextCycle();
  assertThatThrownBy(()->transactions.execute(tenant,()->jdbc.update("update alerts set status='OPEN',version=version+1 where id=?",id))).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void twoRecoveryWorkersResolveExactlyOnceAndOldClaimCannotOverwriteHistory()throws Exception{
  scan();var id=alert();transactions.execute(tenant,()->movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(trailer,0),context(Instant.now().minusSeconds(1)))));
  transactions.execute(tenant,()->jdbc.update("update stale_trailer_scans set next_scan_at=clock_timestamp() where tenant_id=?",tenant));assertThat(scans.schedule(tenant)).isTrue();
  var old=store.claim(tenant,1,Duration.ofSeconds(1)).claims().getFirst();expired(old);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var a=pool.submit(detectorWorker(tenant)::pollOnce);var b=pool.submit(detectorWorker(tenant)::pollOnce);a.get();b.get();}
  assertThat(db.value("select status from alerts where id=?",id)).isEqualTo("RESOLVED");assertThat(db.value("select version from alerts where id=?",id)).isEqualTo(1L);
  assertThatThrownBy(()->executeClaim(old)).isInstanceOf(JobFailure.class);assertThat(store.succeed(old)).isFalse();
 }
 <P>void executeTyped(JobRegistry.Decoded<P>d,JobClaim c){d.handler().execute(new JobExecution<>(c.id(),c.tenantId(),c.attempt(),c.aggregate(),d.payload(),new JobExecution.TenantWork(){public <T>T execute(java.util.function.Supplier<T> work){return store.fencedTransaction(c,work);}}));}
}
