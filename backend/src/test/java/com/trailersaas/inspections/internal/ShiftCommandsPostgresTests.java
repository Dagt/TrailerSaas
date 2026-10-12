package com.trailersaas.inspections.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShiftCommandsPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  EquipmentFixtures db;
  UUID tenant,foreign,user,driver,membership;
  Instant on;
  @BeforeEach void seed() throws Exception {
    db=new EquipmentFixtures(flyway);tenant=db.tenant();foreign=db.tenant();user=db.user();membership=db.membership(tenant,user,"ACTIVE","DRIVER");driver=db.driver(tenant,user);
    on=Instant.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    when(verifier.verify("driver")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
  }
  String base() {return "/api/v1/tenants/"+tenant+"/driver/me";}
  HttpResponse<String> request(String method,String path,String body,UUID key,String token) throws Exception {
    var q=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
    if(token!=null)q.header("Authorization","Bearer "+token);if(key!=null)q.header("Idempotency-Key",key.toString());
    return HttpClient.newHttpClient().send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }
  String body() {return "{\"occurredAt\":\""+on+"\"}";}
  HttpResponse<String> start(UUID key) throws Exception {return request("POST",base()+"/shifts/on-duty",body(),key,"driver");}
  UUID opened() throws Exception {var r=start(UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);return UUID.fromString(JsonPath.read(r.body(),"$.id"));}
  HttpResponse<String> end(UUID shift,UUID key) throws Exception {return request("POST",base()+"/shifts/"+shift+"/off-duty","{\"occurredAt\":\""+on.plusSeconds(1)+"\",\"expectedShiftState\":\"ON_DUTY\"}",key,"driver");}
  void problem(HttpResponse<String> r,int status,String code) {assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);assertThat(JsonPath.<String>read(r.body(),"$.code")).isEqualTo(code);assertThat(r.body()).doesNotContain("constraint","SQL",driver.toString(),"tenant/");}
  @Test void onDutyAndOwnHomeAreAuthoritativeWithStableLostAckReplay() throws Exception {
    var key=UUID.randomUUID();var first=start(key);assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);
    var repeat=start(key);assertThat(repeat.body()).isEqualTo(first.body());
    assertThat(db.value("SELECT count(*) FROM shifts WHERE tenant_id=?",tenant)).isEqualTo(1L);
    problem(start(UUID.randomUUID()),409,"RESOURCE_CONFLICT");
    var home=request("GET",base(),null,null,"driver");assertThat(home.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<String>read(home.body(),"$.shiftContext.currentShift.id")).isEqualTo(JsonPath.<String>read(first.body(),"$.id"));
    assertThat(home.body()).doesNotContain("tenantId","userId","actorUserId");
  }
  @Test void incompatibleKeyAndRevokedAuthorityNeverReplay() throws Exception {
    var key=UUID.randomUUID();assertThat(start(key).statusCode()).isEqualTo(200);
    problem(request("POST",base()+"/shifts/on-duty","{\"occurredAt\":\""+on.plusSeconds(1)+"\"}",key,"driver"),409,"IDEMPOTENCY_KEY_REUSED");
    db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
    problem(start(key),403,"CAPABILITY_DENIED");
  }
  @Test void offDutyWithoutEquipmentClosesOnlyShiftAndReplaysWithoutAnotherEffect() throws Exception {
    var shift=opened();var key=UUID.randomUUID();var r=end(shift,key);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.state")).isEqualTo("OFF_DUTY");assertThat(end(shift,key).body()).isEqualTo(r.body());
    problem(end(shift,UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");
    assertThat(JsonPath.<Object>read(request("GET",base(),null,null,"driver").body(),"$.shiftContext.currentShift")).isNull();
  }
  @Test void activeAssignedWorkBlocksClosureWithoutChangingTrip() throws Exception {
    var shift=opened();UUID trip=UUID.randomUUID();db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,now(),now())",trip,tenant,driver);
    problem(end(shift,UUID.randomUUID()),409,"RESOURCE_CONFLICT");assertThat(db.value("SELECT off_duty_at IS NULL FROM shifts WHERE id=?",shift)).isEqualTo(true);
    assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("ASSIGNED");
    db.execute("UPDATE trips SET status='CANCELLED' WHERE id=?",trip);assertThat(end(shift,UUID.randomUUID()).statusCode()).isEqualTo(200);
  }
  @ParameterizedTest @ValueSource(strings={"SUSPENDED","DISABLED"}) void inactiveMembershipCannotOpenShift(String status) throws Exception {
    db.execute("UPDATE tenant_memberships SET status=? WHERE id=?",status,membership);assertThat(start(UUID.randomUUID()).statusCode()).isEqualTo(403);assertThat(db.value("SELECT count(*) FROM shifts WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void driverForeignTenantMissingAndStrictFieldsFailSafely() throws Exception {
    assertThat(request("POST",base()+"/shifts/on-duty",body(),UUID.randomUUID(),null).statusCode()).isEqualTo(401);
    problem(request("POST",base()+"/shifts/on-duty","{\"occurredAt\":\""+on+"\",\"driverId\":\""+driver+"\"}",UUID.randomUUID(),"driver"),400,"VALIDATION_FAILED");
    problem(request("POST",base()+"/shifts/on-duty",body(),null,"driver"),400,"VALIDATION_FAILED");
    assertThat(request("POST",base().replace(tenant.toString(),foreign.toString())+"/shifts/on-duty",body(),UUID.randomUUID(),"driver").statusCode()).isEqualTo(403);
    db.execute("UPDATE drivers SET active=false WHERE id=?",driver);problem(start(UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
  }
  @Test void concurrentOnDutyUsesDriverLockAndCreatesOneShift() throws Exception {
    var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<HttpResponse<String>> call=()-> {entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError();return start(UUID.randomUUID());};
      var a=pool.submit(call);var b=pool.submit(call);assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();
      assertThat(List.of(a.get(15,TimeUnit.SECONDS).statusCode(),b.get(15,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    } finally {release.countDown();}
    assertThat(db.value("SELECT count(*) FROM shifts WHERE tenant_id=? AND off_duty_at IS NULL",tenant)).isEqualTo(1L);
  }
  @Test void persistenceFailureRollsBackShiftAndLedgerThenOriginalKeyRecovers() throws Exception {
    var key=UUID.randomUUID();db.execute("CREATE FUNCTION fail_shift_create() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only failure'; END $$");db.execute("CREATE TRIGGER fail_shift_create BEFORE INSERT ON shifts FOR EACH ROW EXECUTE FUNCTION fail_shift_create()");
    try {assertThat(start(key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT count(*) FROM shifts WHERE tenant_id=?",tenant)).isEqualTo(0L);assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);}
    finally {db.execute("DROP TRIGGER fail_shift_create ON shifts");db.execute("DROP FUNCTION fail_shift_create()");}
    assertThat(start(key).statusCode()).isEqualTo(200);
  }
  @Test void concurrentOffDutyCreatesOneClosureAndDeterministicLoser() throws Exception {
    var shift=opened();var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<HttpResponse<String>> call=()->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return end(shift,UUID.randomUUID());};
      var a=pool.submit(call);var b=pool.submit(call);assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();
      assertThat(List.of(a.get(20,TimeUnit.SECONDS).statusCode(),b.get(20,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    } finally {release.countDown();}
    assertThat(db.value("SELECT count(*) FROM shifts WHERE tenant_id=? AND off_duty_at IS NOT NULL",tenant)).isEqualTo(1L);
  }
  @Test void closeFailureRollsBackAndDoesNotConsumeTheOperationIdentity() throws Exception {
    var shift=opened();var key=UUID.randomUUID();db.execute("CREATE FUNCTION fail_shift_close() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only'; END $$");db.execute("CREATE TRIGGER fail_shift_close BEFORE UPDATE ON shifts FOR EACH ROW EXECUTE FUNCTION fail_shift_close()");
    try {assertThat(end(shift,key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT off_duty_at IS NULL FROM shifts WHERE id=?",shift)).isEqualTo(true);assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);}
    finally {db.execute("DROP TRIGGER fail_shift_close ON shifts");db.execute("DROP FUNCTION fail_shift_close()");}
    assertThat(end(shift,key).statusCode()).isEqualTo(200);
  }
}
