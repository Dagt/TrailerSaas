package com.trailersaas.jobs.internal;

import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.jobs.*;
import com.trailersaas.notifications.*;
import com.trailersaas.notifications.internal.DeliveryTestConfiguration;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DeliveryTestConfiguration.class)
class AlertDeliveryPostgresTests extends JobsFixture {
 @Autowired AlertNotifications notifications;
 @Autowired DeliveryTestConfiguration.Probe push;
 @MockitoBean FirebaseTokenVerifier verifier;
 @Value("${local.server.port}")int port;
 final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 final HttpClient http=HttpClient.newHttpClient();
 UUID user,membership,alert,trailer,location;
 @BeforeEach void notificationFixture()throws Exception{
  user=db.user();membership=db.membership(tenant,user,"ACTIVE","YARD_SPOTTER");db.execute("update tenants set status='ACTIVE' where id=?",tenant);
  when(verifier.verify("notify")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
  var f=new TrailerFixtures(flyway);location=f.location(tenant);trailer=f.trailer(tenant,"Notify-"+UUID.randomUUID(),customer);
  db.execute("update trailers set location_state='AT_LOCATION',current_location_id=?,last_known_location_id=? where id=?",location,location,trailer);
  UUID site=(UUID)db.value("select site_id from operational_locations where id=?",location);
  var check=request("POST",path("yard-checks"),Map.of("siteId",site.toString()));assertThat(check.statusCode()).as(check.body()).isEqualTo(201);
  var id=json.readTree(check.body()).path("check").path("id").asText();
  var completed=request("POST",path("yard-checks/"+id+"/completion"),Map.of("expectedVersion",0));assertThat(completed.statusCode()).as(completed.body()).isEqualTo(200);
  var detail=request("GET",path("yard-checks/"+id),null);var item=json.readTree(detail.body()).path("items").get(0).path("id").asText();
  var reconciled=request("POST",path("yard-checks/"+id+"/items/"+item+"/mark-location-unknown"),Map.of("expectedTrailerVersion",0,"reason","Explicit verified missing finding"));
  assertThat(reconciled.statusCode()).as(reconciled.body()).isEqualTo(200);alert=UUID.fromString(json.readTree(reconciled.body()).path("alertId").asText());
  f.role(tenant,membership,"TENANT_ADMIN",null);push.reset(user);
 }
 String path(String suffix){return "tenants/"+tenant+"/"+suffix;}
 HttpResponse<String> request(String method,String path,Map<String,?> body)throws Exception{
  var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/"+path)).header("Authorization","Bearer notify").header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json");
  return http.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
 }
 UUID device(String token)throws Exception{var r=request("POST","devices",Map.of("platform","ANDROID","fcmToken",token));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return UUID.fromString(json.readTree(r.body()).path("id").asText());}
 UUID queue(){var ids=transactions.execute(tenant,()->notifications.requestDelivery(tenant,alert,"TEST_APPROVED"));assertThat(ids).hasSize(1);return ids.getFirst();}
 UUID job(UUID recipient)throws Exception{return (UUID)db.value("select id from outbox_jobs where tenant_id=? and payload->'data'->>'recipientId'=?",tenant,recipient.toString());}
 String recipientStatus(UUID recipient)throws Exception{return (String)db.value("select push_status from alert_recipients where id=?",recipient);}
 DurableJobWorker deliveryWorker(){return worker(new JobsProperties(true,List.of(tenant),Duration.ofSeconds(5),4,4,Duration.ofSeconds(5),Duration.ofSeconds(15),5,Duration.ofSeconds(1),Duration.ofSeconds(2)),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());}
 @Test void realMissingAlertRemainsSynchronousWithoutAnyImplicitRecipientsOrJobs()throws Exception{
  assertThat(db.value("select status from alerts where id=?",alert)).isEqualTo("OPEN");
  assertThat(db.value("select count(*) from alert_recipients where tenant_id=?",tenant)).isEqualTo(0L);
  assertThat(db.value("select count(*) from outbox_jobs where tenant_id=?",tenant)).isEqualTo(0L);
 }
 @Test void explicitPolicyEnqueuesAtomicallyAndAcceptedIsNotDelivered()throws Exception{
  device("private-fcm-token");var recipient=queue();var job=job(recipient);
  assertThat(transactions.execute(tenant,()->notifications.requestDelivery(tenant,alert,"TEST_APPROVED"))).isEmpty();
  assertThat(db.value("select payload::text from outbox_jobs where id=?",job).toString()).doesNotContain("private-fcm-token","message","credential");
  deliveryWorker().pollOnce();assertThat(status(job)).isEqualTo("SUCCEEDED");assertThat(recipientStatus(recipient)).isEqualTo("ACCEPTED_BY_FCM");
  assertThat(push.calls.get()).isEqualTo(1);assertThat(push.references).containsOnlyKeys("kind","tenantId","alertId");
  assertThat(db.value("select status from alerts where id=?",alert)).isEqualTo("OPEN");assertThat(db.value("select location_state from trailers where id=?",trailer)).isEqualTo("LOCATION_UNKNOWN");
 }
 @Test void outboxFailureRollsBackRecipientAndBusinessMutation()throws Exception{
  db.execute("revoke insert on outbox_jobs from "+RUNTIME_USERNAME);
  try{assertThatThrownBy(()->transactions.execute(tenant,()->{jdbc.update("update customers set name='Must rollback' where id=?",customer);notifications.requestDelivery(tenant,alert,"TEST_APPROVED");return null;})).isInstanceOf(RuntimeException.class);}
  finally{db.execute("grant insert on outbox_jobs to "+RUNTIME_USERNAME);}
  assertThat(db.value("select count(*) from alert_recipients where tenant_id=?",tenant)).isEqualTo(0L);
  assertThat(db.value("select name from customers where id=?",customer)).isEqualTo("Before");
 }
 @Test void noApprovedPolicyOrForeignRecipientCannotEnqueue()throws Exception{
  assertThatThrownBy(()->transactions.execute(tenant,()->notifications.requestDelivery(tenant,alert,"UNAPPROVED"))).isInstanceOf(RuntimeException.class);
  push.recipients.clear();push.recipients.add(db.user());
  assertThatThrownBy(()->queue()).isInstanceOf(RuntimeException.class);
  assertThat(db.value("select count(*) from outbox_jobs where tenant_id=?",tenant)).isEqualTo(0L);
 }
 @Test void permissionRevocationBeforeDeliverySuppressesWithoutCallingFcm()throws Exception{
  device("revoked-user-token");var r=queue();db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);
  deliveryWorker().pollOnce();assertThat(status(job(r))).isEqualTo("SUCCEEDED");assertThat(recipientStatus(r)).isEqualTo("SUPPRESSED_AUTHORIZATION");assertThat(push.calls.get()).isZero();
 }
 @Test void withdrawnPolicySuppressesEvenWithActiveMembership()throws Exception{
  device("policy-token");var r=queue();push.recipients.clear();deliveryWorker().pollOnce();assertThat(recipientStatus(r)).isEqualTo("SUPPRESSED_AUTHORIZATION");assertThat(push.calls.get()).isZero();
 }
 @Test void noDeviceDoesNotClaimFcmAcceptance()throws Exception{
  var r=queue();deliveryWorker().pollOnce();assertThat(recipientStatus(r)).isEqualTo("NO_ELIGIBLE_DEVICE");assertThat(push.calls.get()).isZero();
 }
 @Test void transientFailureRetriesOnlyThroughExistingWorker()throws Exception{
  device("retry-token");push.scenario="RETRY_ONCE";var r=queue();var id=job(r);var w=deliveryWorker();w.pollOnce();
  assertThat(status(id)).isEqualTo("PENDING");assertThat(recipientStatus(r)).isEqualTo("RETRYABLE_FAILURE");due(id);w.pollOnce();
  assertThat(status(id)).isEqualTo("SUCCEEDED");assertThat(attempts(id)).isEqualTo(2);assertThat(push.calls.get()).isEqualTo(2);
 }
 @ParameterizedTest @ValueSource(strings={"PERMANENT_FAILURE","INVALIDATED_TOKEN"})
 void permanentAndInvalidTokenOutcomesAreDistinct(String mode)throws Exception{
  var d=device("invalid-token");push.scenario=mode;var r=queue();deliveryWorker().pollOnce();assertThat(recipientStatus(r)).isEqualTo(mode);
  assertThat(status(job(r))).isEqualTo(mode.equals("PERMANENT_FAILURE")?"DEAD":"SUCCEEDED");
  assertThat(db.value("select active from user_devices where id=?",d)).isEqualTo(!mode.equals("INVALIDATED_TOKEN"));
 }
 @Test void multipleDevicesDoNotTurnMixedResultsIntoFalseSuccess()throws Exception{
  device("device-good");device("device-bad");push.scenario="MIXED";var r=queue();deliveryWorker().pollOnce();assertThat(recipientStatus(r)).isEqualTo("PARTIAL_FAILURE");
  assertThat(db.value("select count(*) from alert_deliveries where tenant_id=? and recipient_id=?",tenant,r)).isEqualTo(2L);
 }
 @Test void malformedProviderReceiptCannotProduceAcceptedAck()throws Exception{
  device("malformed-token");push.scenario="MALFORMED";var r=queue();deliveryWorker().pollOnce();assertThat(status(job(r))).isEqualTo("PENDING");
  assertThat(db.value("select count(*) from alert_deliveries where recipient_id=? and status='ACCEPTED_BY_FCM'",r)).isEqualTo(0L);
 }
 @Test void inboxReadAuthorizationAndGlobalDeviceResponseNeverExposeTokens()throws Exception{
  device("never-in-inbox-token");var page=request("GET",path("alerts?limit=1&status=OPEN"),null);assertThat(page.statusCode()).as(page.body()).isEqualTo(200);assertThat(page.body()).contains(alert.toString()).doesNotContain("never-in-inbox-token");
  assertThat(request("GET",path("alerts/"+UUID.randomUUID()),null).statusCode()).isEqualTo(404);
  assertThat(request("GET",path("alerts?limit=101"),null).statusCode()).isEqualTo(400);
  db.execute("delete from role_assignments where membership_id=? and role_code='TENANT_ADMIN'",membership);
  assertThat(request("GET",path("alerts"),null).statusCode()).isEqualTo(403);
 }
 @Test void sameUserDeviceRotatesAndForeignUserCannotDeactivate()throws Exception{
  var id=device("token-old");var rotated=request("POST","devices",Map.of("deviceId",id.toString(),"platform","ANDROID","fcmToken","token-new"));assertThat(rotated.statusCode()).isEqualTo(200);assertThat(json.readTree(rotated.body()).path("id").asText()).isEqualTo(id.toString());
  var different=db.user();when(verifier.verify("notify")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+different,null,null));assertThat(request("DELETE","devices/"+id,null).statusCode()).isEqualTo(404);
  var transferred=device("token-new");assertThat(transferred).isNotEqualTo(id);assertThat(db.value("select not active and fcm_token is null from user_devices where id=?",id)).isEqualTo(true);
 }
 @Test void twoWorkerInstancesOnlySendOneValidClaim()throws Exception{
  device("concurrent-token");var r=queue();var a=deliveryWorker();var b=deliveryWorker();try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var one=pool.submit(a::pollOnce);var two=pool.submit(b::pollOnce);one.get();two.get();
  }assertThat(status(job(r))).isEqualTo("SUCCEEDED");assertThat(push.calls.get()).isEqualTo(1);
 }
 @Test void acceptedReceiptSurvivesLostJobAckAndExpiredClaim()throws Exception{
  device("lost-ack-token");var r=queue();var id=job(r);var claim=store.claim(tenant,1,Duration.ofSeconds(2)).claims().getFirst();executeClaim(claim);
  assertThat(push.calls.get()).isEqualTo(1);assertThat(status(id)).isEqualTo("PROCESSING");expired(claim);deliveryWorker().pollOnce();assertThat(status(id)).isEqualTo("SUCCEEDED");assertThat(push.calls.get()).isEqualTo(1);assertThat(store.succeed(claim)).isFalse();
 }
 void executeClaim(JobClaim claim){executeTyped(registry.decode(claim.type(),claim.payload()),claim);}
 <P> void executeTyped(JobRegistry.Decoded<P> d,JobClaim c){d.handler().execute(new JobExecution<>(c.id(),c.tenantId(),c.attempt(),c.aggregate(),d.payload(),new JobExecution.TenantWork(){public <T>T execute(java.util.function.Supplier<T> f){return store.fencedTransaction(c,f);}}));}
}
