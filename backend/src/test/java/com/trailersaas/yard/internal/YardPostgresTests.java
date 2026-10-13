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
