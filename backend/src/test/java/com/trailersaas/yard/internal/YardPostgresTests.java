package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.awaitility.Awaitility.await;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class YardPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  TrailerFixtures f;
  UUID tenant,other,user,membership,site,a,b,trailer,customer;
  final ObjectMapper json=new ObjectMapper();
  @BeforeEach void seed() throws Exception {
    f=new TrailerFixtures(flyway);tenant=f.db.tenant();other=f.db.tenant();user=f.db.user();
    f.db.execute("update tenants set status='ACTIVE' where id=?",tenant);
    membership=f.db.membership(tenant,user,"ACTIVE","YARD_SPOTTER");
    a=f.location(tenant);site=(UUID)f.db.value("select site_id from operational_locations where id=?",a);
    b=location(site,"DOCK");customer=f.customer(tenant);
    trailer=f.trailer(tenant,"Yard-"+UUID.randomUUID(),customer);
    f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=?,last_known_location_id=?,last_seen_at=? where id=?",a,a,java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),trailer);
    when(verifier.verify("yard")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
  }
  UUID location(UUID parent,String type) throws Exception {
    UUID id=UUID.randomUUID();f.db.execute("insert into operational_locations(id,tenant_id,site_id,type,name,active,created_at,updated_at) values(?,?,?,?,?,true,now(),now())",id,tenant,parent,type,type+"-"+id);return id;
  }
  HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+path)).header("Authorization","Bearer yard").GET().build(),HttpResponse.BodyHandlers.ofString());
  }
  void problem(HttpResponse<String> r,int status,String code){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(JsonPath.<String>read(r.body(),"$.code")).isEqualTo(code);}
  Map<String,Object> moveBody() {return new LinkedHashMap<>(Map.of("trailerId",trailer.toString(),"fromLocationId",a.toString(),"toLocationId",b.toString(),"observedAt",Instant.now().minusSeconds(5).toString(),"expectedTrailerVersion",0));}
  HttpResponse<String> post(String key,Map<String,Object> body) throws Exception {
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/yard-moves"))
        .header("Authorization","Bearer yard").header("Content-Type","application/json");
    if(key!=null)request.header("Idempotency-Key",key);
    return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  void unchanged() throws Exception {
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from processed_client_operations where tenant_id=?",tenant)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"YARD:DOCK","DOCK:YARD","YARD:YARD"})
  void moveUsesOriginalEngineAndPreservesOtherDimensions(String route) throws Exception {
    var types=route.split(":");f.db.execute("update operational_locations set type=? where id=?",types[0],a);f.db.execute("update operational_locations set type=? where id=?",types[1],b);
    var body=moveBody();var key=UUID.randomUUID().toString();var r=post(key,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.trailer.currentLocation.id")).isEqualTo(b.toString());
    assertThat(JsonPath.<Number>read(r.body(),"$.trailer.version").longValue()).isEqualTo(1L);
    assertThat(f.db.value("select last_known_location_id from trailers where id=?",trailer)).isEqualTo(b);
    assertThat(f.db.value("select load_state from trailers where id=?",trailer)).isEqualTo("UNKNOWN");
    assertThat(f.db.value("select designated_customer_id from trailers where id=?",trailer)).isEqualTo(customer);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='YARD_MOVED' and actor_type='HUMAN_USER' and source_type='USER_ACTION' and actor_user_id=? and client_operation_id=? and trip_id is null and driver_id is null and tractor_id is null and metadata->>'previousLocationId'=? and metadata->>'newLocationId'=?",trailer,user,UUID.fromString(key),a.toString(),b.toString())).isEqualTo(1L);
    assertThat(f.db.value("select last_seen_at from trailers where id=?",trailer)).isEqualTo(java.sql.Timestamp.from(Instant.parse(body.get("observedAt").toString()).truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
  }
  @Test void mismatchHasAuthorizedProjectionAndNoSilentCorrection() throws Exception {
    var body=moveBody();body.put("fromLocationId",b.toString());body.put("toLocationId",a.toString());
    var r=post(UUID.randomUUID().toString(),body);problem(r,409,"LOCATION_MISMATCH");
    assertThat(JsonPath.<String>read(r.body(),"$.currentTrailer.currentLocation.id")).isEqualTo(a.toString());unchanged();
  }
  @Test void staleVersionIsDifferentFromMismatch() throws Exception {
    var body=moveBody();body.put("expectedTrailerVersion",12);problem(post(UUID.randomUUID().toString(),body),409,"STALE_VERSION");unchanged();
  }
  @ParameterizedTest @ValueSource(strings={"oos","unknown","transit","same","site","inactiveSite","inactiveLocation","foreignLocation","foreignTrailer","oldObservation"})
  void incompatibleMovesRollbackEverySideEffect(String mode) throws Exception {
    var body=moveBody();String code="RESOURCE_CONFLICT";int status=409;
    switch(mode) {
      case "oos" -> {f.db.execute("update trailers set serviceability_state='OUT_OF_SERVICE' where id=?",trailer);code="INVALID_STATE_TRANSITION";}
      case "unknown","transit" -> {f.db.execute("update trailers set location_state=?,current_location_id=null where id=?",mode.equals("unknown")?"LOCATION_UNKNOWN":"IN_TRANSIT",trailer);code="INVALID_STATE_TRANSITION";}
      case "same" -> body.put("toLocationId",a.toString());
      case "site" -> body.put("toLocationId",f.location(tenant).toString());
      case "inactiveSite" -> f.db.execute("update physical_sites set active=false where id=?",site);
      case "inactiveLocation" -> f.db.execute("update operational_locations set active=false where id=?",b);
      case "foreignLocation" -> {body.put("toLocationId",f.location(other).toString());code="RESOURCE_NOT_FOUND";status=404;}
      case "foreignTrailer" -> {body.put("trailerId",f.trailer(other,"Foreign-"+UUID.randomUUID(),null).toString());code="RESOURCE_NOT_FOUND";status=404;}
      case "oldObservation" -> body.put("observedAt",Instant.now().minusSeconds(7200).toString());
    }
    problem(post(UUID.randomUUID().toString(),body),status,code);
    if(mode.equals("unknown")||mode.equals("transit"))f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",a,trailer);
    unchanged();
  }
  @Test void lostAckReplayKeepsOriginalResponseAndBodyReuseIsRejected() throws Exception {
    var body=moveBody();var key=UUID.randomUUID().toString();var first=post(key,body);assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
    var replay=post(key,body);assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(first.body()));
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
    body.put("expectedTrailerVersion",1);problem(post(key,body),409,"IDEMPOTENCY_KEY_REUSED");
  }
  @ParameterizedTest @ValueSource(strings={"same-key","different-key"})
  void concurrentConfirmationsHaveOnlyOnePhysicalWinner(String mode) throws Exception {
    var body=moveBody();var key=UUID.randomUUID().toString();var start=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var first=pool.submit(()->{start.await();return post(key,body);});
      var second=pool.submit(()->{start.await();return post(mode.equals("same-key")?key:UUID.randomUUID().toString(),body);});
      start.countDown();var responses=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
      if(mode.equals("same-key")){assertThat(responses).allMatch(r->r.statusCode()==200);assertThat(json.readTree(responses.get(0).body())).isEqualTo(json.readTree(responses.get(1).body()));}
      else assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,409);
    }
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from processed_client_operations where tenant_id=?",tenant)).isEqualTo(1L);
  }
  @ParameterizedTest @ValueSource(strings={"membership","capability","tenant"})
  void revokedAuthorityCannotReplayAnEarlierSuccessfulResult(String mode) throws Exception {
    var body=moveBody();var key=UUID.randomUUID().toString();assertThat(post(key,body).statusCode()).isEqualTo(200);
    switch(mode){
      case "membership" -> f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);
      case "capability" -> f.db.execute("delete from role_assignments where membership_id=?",membership);
      case "tenant" -> f.db.execute("update tenants set status='CANCELLED' where id=?",tenant);
    }
    assertThat(post(key,body).statusCode()).isEqualTo(403);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
  }
  @ParameterizedTest @ValueSource(strings={"actor_type","source_type","negativeVersion","fractionalVersion","missingTime","future","badId","key","missingKey"})
  void commandDtoAndIdentityAreClosed(String mode) throws Exception {
    var body=moveBody();String key=UUID.randomUUID().toString();
    switch(mode){
      case "actor_type","source_type" -> body.put(mode,"SYSTEM");
      case "negativeVersion" -> body.put("expectedTrailerVersion",-1);
      case "fractionalVersion" -> body.put("expectedTrailerVersion",0.5);
      case "missingTime" -> body.remove("observedAt");
      case "future" -> body.put("observedAt",Instant.now().plusSeconds(3600).toString());
      case "badId" -> body.put("trailerId","1-1-1-1-1");
      case "key" -> key="1-1-1-1-1";
      case "missingKey" -> key=null;
    }
    problem(post(key,body),400,"VALIDATION_FAILED");unchanged();
  }
  @Test void coupledTrailerCannotBeMovedThroughYard() throws Exception {
    var driver=f.db.driver(tenant,user);var tractor=f.db.tractor(tenant,"Spotter-"+UUID.randomUUID());var event=UUID.randomUUID();
    f.db.execute("insert into trailer_movement_events(id,tenant_id,trailer_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at) values(?,?,?,'HOOKED','HUMAN_USER','USER_ACTION',?,now(),now())",event,tenant,trailer,user);
    f.db.execute("insert into trailer_couplings(id,tenant_id,trailer_id,tractor_id,driver_id,hooked_at,hook_event_id,created_at) values(?,?,?,?,?,now(),?,now())",UUID.randomUUID(),tenant,trailer,tractor,driver,event);
    problem(post(UUID.randomUUID().toString(),moveBody()),409,"INVALID_STATE_TRANSITION");
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='YARD_MOVED'",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
  }
  @Test void inactiveTrailerAndForeignTenantExposeNoProjection() throws Exception {
    f.db.execute("update trailers set active=false where id=?",trailer);var r=post(UUID.randomUUID().toString(),moveBody());problem(r,404,"RESOURCE_NOT_FOUND");assertThat(r.body()).doesNotContain("currentTrailer",trailer.toString());unchanged();
    tenant=other;r=post(UUID.randomUUID().toString(),moveBody());assertThat(r.statusCode()).isEqualTo(403);assertThat(r.body()).doesNotContain("currentTrailer",site.toString(),a.toString());
  }
  @Test void qrIdentificationIsReadOnlyAndRevocationIsReal() throws Exception {
    var bytes=new byte[16];new java.security.SecureRandom().nextBytes(bytes);var token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);var qr=f.qr(tenant,trailer,token);
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/trailer-qr/"+token)).header("Authorization","Bearer yard").GET().build();
    var r=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);unchanged();
    f.db.execute("update trailer_qr_identities set active=false,revoked_at=now() where id=?",qr);
    r=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());problem(r,404,"RESOURCE_NOT_FOUND");assertThat(r.body()).doesNotContain(token,trailer.toString());unchanged();
  }
  @Test void failureSavingLedgerResultRollsBackPhysicalEventAndProjection() throws Exception {
    f.db.execute("create function reject_yard_test_result() returns trigger language plpgsql as $$ begin if NEW.operation_type='YARD_MOVE' and NEW.status='SUCCEEDED' then raise exception 'test ledger failure'; end if; return NEW; end $$");
    f.db.execute("create trigger reject_yard_result before update on processed_client_operations for each row execute function reject_yard_test_result()");
    try {var r=post(UUID.randomUUID().toString(),moveBody());assertThat(r.statusCode()).as(r.body()).isEqualTo(500);unchanged();}
    finally {f.db.execute("drop trigger reject_yard_result on processed_client_operations");f.db.execute("drop function reject_yard_test_result()");}
  }
  @Test void currentSiteUpdateIsLockedAndRechecked() throws Exception {
    var held=new CountDownLatch(1);var release=new CountDownLatch(1);var body=moveBody();
    try(var pool=Executors.newFixedThreadPool(2)){
      var update=pool.submit(()->transactions.execute(tenant,()->{jdbc.update("update physical_sites set active=false,version=version+1 where tenant_id=? and id=?",tenant,site);held.countDown();
        try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Lock timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return true;}));
      assertThat(held.await(20,TimeUnit.SECONDS)).isTrue();var confirm=pool.submit(()->post(UUID.randomUUID().toString(),body));
      try{await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat((Long)f.db.value("select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%for share of l,s%'")).isPositive());}
      finally{release.countDown();}
      assertThat(update.get(30,TimeUnit.SECONDS)).isTrue();problem(confirm.get(30,TimeUnit.SECONDS),409,"RESOURCE_CONFLICT");unchanged();
    }finally{release.countDown();}
  }
  @ParameterizedTest @ValueSource(strings={"CANDIDATE","SUSPENDED","CANCELLED"})
  void tenantMustBeActiveForReadsAndCommands(String status) throws Exception {
    f.db.execute("update tenants set status=? where id=?",status,tenant);
    problem(get("/yard/sites"),403,"TENANT_ACCESS_DENIED");
    problem(get("/yard/trailers/"+trailer),403,"TENANT_ACCESS_DENIED");
    problem(post(UUID.randomUUID().toString(),moveBody()),403,"TENANT_ACCESS_DENIED");unchanged();
  }
  @Test void changedOriginHasSpecificMismatchEvenWhenVersionAlsoChanged() throws Exception {
    f.db.execute("update trailers set current_location_id=?,last_known_location_id=?,version=1 where id=?",b,b,trailer);
    var body=moveBody();body.put("toLocationId",location(site,"YARD").toString());
    var r=post(UUID.randomUUID().toString(),body);problem(r,409,"LOCATION_MISMATCH");
    assertThat(JsonPath.<String>read(r.body(),"$.currentTrailer.currentLocation.id")).isEqualTo(b.toString());
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
  }
  @Test void invalidRfc3339ObservationIsRejectedBeforeStorage() throws Exception {
    f.db.execute("update trailers set last_seen_at=null where id=?",trailer);
    var body=moveBody();body.put("observedAt","-1000000000-01-01T00:00:00Z");
    problem(post(UUID.randomUUID().toString(),body),400,"VALIDATION_FAILED");unchanged();
  }
  @Test void authorityRevokedWhileWaitingForSiteCannotCommit() throws Exception {
    var held=new CountDownLatch(1);var release=new CountDownLatch(1);var body=moveBody();
    try(var pool=Executors.newFixedThreadPool(2)){
      var update=pool.submit(()->transactions.execute(tenant,()->{jdbc.update("update physical_sites set version=version+1 where tenant_id=? and id=?",tenant,site);held.countDown();
        try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Lock timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return true;}));
      assertThat(held.await(20,TimeUnit.SECONDS)).isTrue();var confirm=pool.submit(()->post(UUID.randomUUID().toString(),body));
      try{await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat((Long)f.db.value("select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%for share of l,s%'")).isPositive());
        f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);
      }finally{release.countDown();}
      assertThat(update.get(30,TimeUnit.SECONDS)).isTrue();assertThat(confirm.get(30,TimeUnit.SECONDS).statusCode()).isEqualTo(403);unchanged();
    }finally{release.countDown();}
  }
  @Test void activeContextHasOnlyTenantSitesAndNormalizedLocations() throws Exception {
    var foreign=f.location(other);var inactive=f.location(tenant);
    f.db.execute("update physical_sites set active=false where id=(select site_id from operational_locations where id=?)",inactive);
    var r=get("/yard/sites");assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(r.body()).contains(site.toString(),a.toString(),b.toString()).doesNotContain(foreign.toString(),inactive.toString(),"latitude","longitude");
    assertThat(JsonPath.<List<?>>read(r.body(),"$.items")).hasSize(1);
  }
  @Test void contextLocationsMustHaveLiveCustomerAssociation() throws Exception {
    var owned=location(site,"CUSTOMER_AREA");f.db.execute("update operational_locations set customer_id=? where id=?",customer,owned);
    assertThat(get("/yard/sites").body()).doesNotContain(owned.toString());
    f.db.execute("insert into customer_sites values(?,?,?,?,now())",UUID.randomUUID(),tenant,customer,site);
    assertThat(get("/yard/sites").body()).contains(owned.toString());
    f.db.execute("update customers set status='INACTIVE' where id=?",customer);
    assertThat(get("/yard/sites").body()).doesNotContain(owned.toString());
  }
  @Test void pagesAreBoundedAndCursorNeverGrantsAccess() throws Exception {
    f.location(tenant);var r=get("/yard/sites?limit=1");assertThat(r.statusCode()).isEqualTo(200);
    var cursor=JsonPath.<String>read(r.body(),"$.nextCursor");assertThat(cursor).isNotNull();
    var next=get("/yard/sites?limit=1&cursor="+cursor);assertThat(JsonPath.<List<?>>read(next.body(),"$.items")).hasSize(1);
    var locations=get("/yard/sites/"+site+"/locations?limit=1");assertThat(JsonPath.<List<?>>read(locations.body(),"$.items")).hasSize(1);
    assertThat(JsonPath.<String>read(locations.body(),"$.nextCursor")).isNotNull();
    problem(get("/yard/sites/"+UUID.randomUUID()+"/locations"),404,"RESOURCE_NOT_FOUND");
  }
  @ParameterizedTest @ValueSource(strings={"?limit=0","?limit=51","?cursor=invalid","?customerId=x"})
  void contextIsStrict(String query) throws Exception {problem(get("/yard/sites"+query),400,"VALIDATION_FAILED");}
  @ParameterizedTest @ValueSource(strings={"membership","role","foreignTenant"})
  void freshAuthorityPrecedesEveryContextRead(String mode) throws Exception {
    if(mode.equals("membership"))f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);
    if(mode.equals("role"))f.db.execute("delete from role_assignments where membership_id=?",membership);
    if(mode.equals("foreignTenant"))tenant=other;
    assertThat(get("/yard/sites").statusCode()).isEqualTo(403);
  }
  @Test void yardReadIsMinimalAndNeverMutatesPhysicalState() throws Exception {
    var r=get("/yard/trailers/"+trailer);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.currentLocation.id")).isEqualTo(a.toString());
    assertThat(JsonPath.<String>read(r.body(),"$.designatedCustomer.id")).isEqualTo(customer.toString());
    assertThat(JsonPath.<String>read(r.body(),"$.couplingState")).isEqualTo("UNCOUPLED");
    assertThat(r.body()).doesNotContain("tenantId","currentDriverId","tractorId","orders");
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
    var foreign=f.trailer(other,"Foreign",null);problem(get("/yard/trailers/"+foreign),404,"RESOURCE_NOT_FOUND");
    f.db.execute("update trailers set active=false where id=?",trailer);problem(get("/yard/trailers/"+trailer),404,"RESOURCE_NOT_FOUND");
  }
}
