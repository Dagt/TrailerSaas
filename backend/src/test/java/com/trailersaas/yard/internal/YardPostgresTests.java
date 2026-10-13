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
import java.util.*;
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
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  TrailerFixtures f;
  UUID tenant,other,user,membership,site,a,b,trailer,customer;
  final ObjectMapper json=new ObjectMapper();
  @BeforeEach void seed() throws Exception {
    f=new TrailerFixtures(flyway);tenant=f.db.tenant();other=f.db.tenant();user=f.db.user();
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
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/yard-moves"))
        .header("Authorization","Bearer yard").header("Idempotency-Key",key).header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
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
