package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriverMobilePostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  EquipmentFixtures f;
  UUID tenant,other,user,driver,membership,trip,trailer;
  @BeforeEach void seed() throws Exception {
    f=new EquipmentFixtures(flyway); tenant=f.tenant(); other=f.tenant(); user=f.user();
    membership=f.membership(tenant,user,"ACTIVE","DRIVER"); driver=f.driver(tenant,user);
    trailer=new TrailerFixtures(flyway).trailer(tenant,"Mobile-"+UUID.randomUUID(),null);
    trip=trip(tenant,driver,"ASSIGNED");
    f.execute("UPDATE trips SET planned_initial_trailer_id=? WHERE id=?",trailer,trip);
    f.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,4,'DELIVERY','PENDING','{\"name\":\"Frozen destination\"}',now(),now())",UUID.randomUUID(),tenant,trip);
    when(verifier.verify("driver")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
  }
  UUID trip(UUID t,UUID d,String state) throws Exception {
    var id=UUID.randomUUID(); f.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,created_at,updated_at) VALUES(?,?,?,?,now(),now())",id,t,state,d);return id;
  }
  HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer driver").GET().build(),HttpResponse.BodyHandlers.ofString());
  }
  String base(){return "/api/v1/tenants/"+tenant+"/driver/me";}
  @Test void ownProfileAndAssignedProjectionExcludeAdministrativeData() throws Exception {
    var profile=get(base());assertThat(profile.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<String>read(profile.body(),"$.id")).isEqualTo(driver.toString());
    assertThat(profile.body()).doesNotContain("userId","licenseNumber","firebase","tenantId");
    var otherTrip=trip(tenant,f.driver(tenant,null),"ASSIGNED");
    var foreignTrip=trip(other,f.driver(other,null),"ASSIGNED");
    var work=get(base()+"/trips");assertThat(work.statusCode()).withFailMessage(work.body()).isEqualTo(200);
    assertThat(work.body()).contains(trip.toString(),"Frozen destination",trailer.toString()).doesNotContain(otherTrip.toString(),foreignTrip.toString(),"tenantId","actorUserId","assignmentHistory","operationalHistory","clientOperationId");
    assertThat(JsonPath.<String>read(work.body(),"$.items[0].plannedTrailer.id")).isEqualTo(trailer.toString());
    assertThat(work.body()).doesNotContain("currentCoupling");
  }
  @ParameterizedTest @ValueSource(strings={"COMPLETED","CANCELLED"}) void terminalTripsAreNotDownloadedWork(String state) throws Exception {
    f.execute("UPDATE trips SET status=? WHERE id=?",state,trip);
    assertThat(JsonPath.<List<?>>read(get(base()+"/trips").body(),"$.items")).isEmpty();
  }
  @ParameterizedTest @ValueSource(strings={"/trips?active=true","/trips?date=2026-10-10","/trips?driverId=bad","/trips?limit=201","/trips?cursor=bad","?userId=bad"})
  void strictOwnReadQueries(String suffix) throws Exception {assertThat(get(base()+suffix).statusCode()).isEqualTo(400);}
  @Test void freshSuspensionRoleRemovalAndUnlinkDenySameBearer() throws Exception {
    assertThat(get(base()+"/trips").statusCode()).isEqualTo(200);
    f.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
    assertThat(get(base()+"/trips").statusCode()).isEqualTo(403);
    f.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?",membership);
    f.execute("UPDATE drivers SET user_id=NULL WHERE id=?",driver);
    assertThat(get(base()).statusCode()).isEqualTo(403);
    f.execute("UPDATE drivers SET user_id=? WHERE id=?",user,driver);
    f.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
    assertThat(get(base()).statusCode()).isEqualTo(403);
  }
  @Test void inactiveDriverAndForeignTenantCannotRead() throws Exception {
    assertThat(get("/api/v1/tenants/"+other+"/driver/me/trips").statusCode()).isEqualTo(403);
    f.execute("UPDATE drivers SET active=false WHERE id=?",driver);
    assertThat(get(base()+"/trips").statusCode()).isEqualTo(403);
  }
  @Test void keysetPaginationRechecksAssignmentEveryPage() throws Exception {
    var nextTrip=trip(tenant,driver,"PLANNED");
    var first=get(base()+"/trips?limit=1");
    assertThat(first.statusCode()).isEqualTo(200);
    var cursor=JsonPath.<String>read(first.body(),"$.nextCursor"); assertThat(cursor).isNotBlank();
    var second=get(base()+"/trips?limit=1&cursor="+cursor);
    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<String>read(first.body(),"$.items[0].id")).isNotEqualTo(JsonPath.<String>read(second.body(),"$.items[0].id"));
    f.execute("UPDATE trips SET current_driver_id=NULL WHERE id IN (?,?)",trip,nextTrip);
    assertThat(JsonPath.<List<?>>read(get(base()+"/trips").body(),"$.items")).isEmpty();
  }
  @Test void meCapabilitiesAreFreshHintsNotClaimsOrGrants() throws Exception {
    var me=get("/api/v1/me");assertThat(me.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<List<String>>read(me.body(),"$.memberships[0].capabilities")).contains("TRIP_VIEW_ASSIGNED").doesNotContain("TRIP_PLAN");
    f.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
    assertThat(JsonPath.<List<?>>read(get("/api/v1/me").body(),"$.memberships[0].capabilities")).isEmpty();
  }
  @ParameterizedTest @ValueSource(strings={"TENANT_ADMIN","TRANSPORT_OPERATIONS","YARD_SPOTTER","CUSTOMER_DISPATCHER"})
  void otherRolesCannotUseLinkedProfileAsAssignedWorkAuthority(String role) throws Exception {
    f.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
    var c=role.equals("CUSTOMER_DISPATCHER")?new TrailerFixtures(flyway).customer(tenant):null;
    f.execute("INSERT INTO role_assignments VALUES(?,?,?,?,?,?,now())",UUID.randomUUID(),tenant,membership,role,c==null?"TENANT":"CUSTOMER",c);
    assertThat(get(base()+"/trips").statusCode()).isEqualTo(403);
  }
}
