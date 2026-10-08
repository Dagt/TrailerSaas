package com.trailersaas.identity.internal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class TrailerHttpPostgresTests extends PostgresIntegrationTest {
    static final String TOKEN="synthetic-secret-trailer-bearer-token";
    @Autowired Flyway flyway; @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    final JsonMapper mapper=JsonMapper.builder().build();
    TrailerFixtures f; UUID tenant,actor,membership,customerA,customerB;
    @BeforeEach void seed() throws Exception {
        f=new TrailerFixtures(flyway);tenant=f.db.tenant();actor=f.db.user();
        membership=f.db.membership(tenant,actor,"ACTIVE","TENANT_ADMIN");
        customerA=f.customer(tenant);customerB=f.customer(tenant);
        when(verifier.verify(TOKEN)).thenReturn(new VerifiedFirebaseIdentity("fixture-"+actor,null,null));
    }
    @Test void creationDefaultsMetadataOnlyPatchingNoopNullableClearsDeactivationAndStale() throws Exception {
        var t=create(Map.of("operationalNumber"," t114 ","designatedCustomerId",customerA,"trailerType","open-type","capabilityNotes","notes","usagePurpose","DELIVERY"));
        assertThat(t).containsEntry("locationState","LOCATION_UNKNOWN").containsEntry("loadState","UNKNOWN").containsEntry("serviceabilityState","IN_SERVICE")
                .containsEntry("active",true).containsEntry("version",0).containsEntry("currentLocationId",null).containsEntry("lastKnownLocationId",null)
                .containsEntry("lastSeenAt",null).containsEntry("lastDriverId",null).containsEntry("operationalNumber"," t114 ")
                .doesNotContainKeys("tenantId","operationalNumberNormalized");
        String p="/trailers/"+t.get("id");
        assertThat(body(req("GET",p,null,TOKEN))).isEqualTo(t);
        assertThat(body(req("PATCH",p,Map.of("expectedVersion",0),TOKEN))).isEqualTo(t);
        var patch=new HashMap<String,Object>();patch.put("expectedVersion",0);patch.put("trailerType",null);patch.put("capabilityNotes",null);patch.put("usagePurpose",null);patch.put("active",false);
        assertThat(body(req("PATCH",p,patch,TOKEN))).containsEntry("version",1).containsEntry("active",false).containsEntry("usagePurpose",null).containsEntry("trailerType",null).containsEntry("capabilityNotes",null);
        problem(req("PATCH",p,Map.of("expectedVersion",0,"active",true),TOKEN),409,"STALE_VERSION");
        assertThat(body(req("PATCH",p,Map.of("expectedVersion",1,"active",true),TOKEN))).containsEntry("version",2).containsEntry("active",true);
        problem(req("POST","/trailers",Map.of("operationalNumber","T114"),TOKEN),409,"RESOURCE_CONFLICT");
        problem(req("POST","/trailers",Map.of("operationalNumber","bad","designatedCustomerId",f.customer(f.db.tenant())),TOKEN),409,"RESOURCE_CONFLICT");
        assertThat(req("DELETE",p,null,TOKEN).statusCode()).isEqualTo(405);
    }
    @Test void genericRequestsRejectPhysicalStateAndDesignationMutation() throws Exception {
        var t=create(Map.of("operationalNumber","one"));
        for(String key:List.of("locationState","currentLocationId","lastKnownLocationId","lastSeenAt","lastDriverId","loadState","serviceabilityState","version","active")) {
            problem(req("POST","/trailers",Map.of("operationalNumber","two",key,"untrusted"),TOKEN),400,"VALIDATION_FAILED");
        }
        for(String key:List.of("designatedCustomerId","locationState","currentLocationId","lastKnownLocationId","lastSeenAt","lastDriverId","loadState","serviceabilityState")) {
            problem(req("PATCH","/trailers/"+t.get("id"),Map.of("expectedVersion",0,key,"untrusted"),TOKEN),400,"VALIDATION_FAILED");
        }
        problem(req("POST","/trailers",Map.of("operationalNumber","T\t1"),TOKEN),400,"VALIDATION_FAILED");
        problem(req("PATCH","/trailers/"+t.get("id"),Map.of("expectedVersion",0,"usagePurpose","BAD"),TOKEN),400,"VALIDATION_FAILED");
    }
    @Test void listUsesExactNormalizedFiltersAndAndCombinationWithStablePrivateOrdering() throws Exception {
        var z=create(Map.of("operationalNumber"," z  02 ","designatedCustomerId",customerA,"usagePurpose","SHUTTLE"));
        var a=create(Map.of("operationalNumber","a01","designatedCustomerId",customerB));
        assertThat(items(req("GET","/trailers",null,TOKEN))).extracting(t->t.get("id")).containsExactly(a.get("id"),z.get("id"));
        assertThat(items(req("GET","/trailers?operationalNumber=z%20%20%2002",null,TOKEN))).extracting(t->t.get("id")).containsExactly(z.get("id"));
        for(String query:List.of("designatedCustomerId="+customerA,"locationState=LOCATION_UNKNOWN&loadState=UNKNOWN&serviceabilityState=IN_SERVICE&active=true&designatedCustomerId="+customerA)) {
            assertThat(items(req("GET","/trailers?"+query,null,TOKEN))).extracting(t->t.get("id")).containsExactly(z.get("id"));
        }
        assertThat(items(req("GET","/trailers?active=false",null,TOKEN))).isEmpty();
        assertThat(items(req("GET","/trailers?operationalNumber=z",null,TOKEN))).isEmpty();
        UUID location=f.location(tenant);
        f.db.execute("UPDATE trailers SET location_state='AT_LOCATION',current_location_id=?,load_state='LOADED',serviceability_state='OUT_OF_SERVICE' WHERE id=?",location,UUID.fromString((String)z.get("id")));
        assertThat(items(req("GET","/trailers?currentLocationId="+location+"&locationState=AT_LOCATION&loadState=LOADED&serviceabilityState=OUT_OF_SERVICE",null,TOKEN))).hasSize(1);
        for(String query:List.of("unknown=true","active=yes","designatedCustomerId=bad","currentLocationId=bad","locationState=BAD","loadState=BAD","serviceabilityState=BAD")) {
            problem(req("GET","/trailers?"+query,null,TOKEN),400,"VALIDATION_FAILED");
        }
    }
    void replaceRoles(String role,UUID customer) throws Exception {
        f.db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        f.role(tenant,membership,role,customer);
    }
    @Test void dispatcherScopeAlwaysFiltersAndCannotSeeUnassignedOrForeignResources() throws Exception {
        var own=create(Map.of("operationalNumber","own","designatedCustomerId",customerA));
        var foreign=create(Map.of("operationalNumber","foreign","designatedCustomerId",customerB));
        var unassigned=create(Map.of("operationalNumber","none"));
        replaceRoles("CUSTOMER_DISPATCHER",customerA);
        assertThat(items(req("GET","/trailers",null,TOKEN))).extracting(t->t.get("id")).containsExactly(own.get("id"));
        assertThat(req("GET","/trailers/"+own.get("id"),null,TOKEN).statusCode()).isEqualTo(200);
        for(var t:List.of(foreign,unassigned)) { problem(req("GET","/trailers/"+t.get("id"),null,TOKEN),404,"RESOURCE_NOT_FOUND"); }
        problem(req("GET","/trailers?designatedCustomerId="+customerB,null,TOKEN),403,"CUSTOMER_SCOPE_DENIED");
        assertThat(items(req("GET","/trailers?operationalNumber=foreign",null,TOKEN))).isEmpty();
        problem(req("POST","/trailers",Map.of("operationalNumber","denied"),TOKEN),403,"CAPABILITY_DENIED");
        problem(req("PATCH","/trailers/"+own.get("id"),Map.of("expectedVersion",0),TOKEN),403,"CAPABILITY_DENIED");
    }
    @ParameterizedTest @ValueSource(strings={"TRANSPORT_OPERATIONS","YARD_SPOTTER","DRIVER"})
    void operationsReadOnlyAndPhysicalOperatorsCannotBrowseGenerically(String role) throws Exception {
        var t=create(Map.of("operationalNumber","roles"));replaceRoles(role,null);
        if(role.equals("TRANSPORT_OPERATIONS")) {
            assertThat(items(req("GET","/trailers",null,TOKEN))).hasSize(1);
            assertThat(req("GET","/trailers/"+t.get("id"),null,TOKEN).statusCode()).isEqualTo(200);
        } else {
            problem(req("GET","/trailers",null,TOKEN),403,"CAPABILITY_DENIED");
            problem(req("GET","/trailers/"+t.get("id"),null,TOKEN),403,"CAPABILITY_DENIED");
        }
        problem(req("POST","/trailers",Map.of("operationalNumber","denied"),TOKEN),403,"CAPABILITY_DENIED");
        problem(req("PATCH","/trailers/"+t.get("id"),Map.of("expectedVersion",0),TOKEN),403,"CAPABILITY_DENIED");
        for(String method:List.of("GET","POST")) { problem(req(method,"/trailers/"+t.get("id")+"/qr-identities",null,TOKEN),403,"CAPABILITY_DENIED"); }
    }
    @Test void crossTenantIdsMissingAuthenticationAndSuspensionAreSafeAndCorrelated() throws Exception {
        UUID foreign=f.trailer(f.db.tenant(),"foreign",null);
        var response=req("GET","/trailers/"+foreign,null,TOKEN);problem(response,404,"RESOURCE_NOT_FOUND");
        assertThat(response.body()).doesNotContain("foreign");
        problem(req("GET","/trailers",null,null),401,"AUTHENTICATION_REQUIRED");
        f.db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
        problem(req("GET","/trailers",null,TOKEN),403,"TENANT_ACCESS_DENIED");
    }
    @Test void customFirebaseClaimsCannotWidenDatabaseTrailerScope() throws Exception {
        var t=create(Map.of("operationalNumber","not visible","designatedCustomerId",customerB));
        replaceRoles("CUSTOMER_DISPATCHER",customerA);
        var firebase=mock(com.google.firebase.auth.FirebaseToken.class);
        when(firebase.getUid()).thenReturn("fixture-"+actor);
        when(firebase.getClaims()).thenReturn(Map.of("role","TENANT_ADMIN","tenantId",tenant.toString(),"customerId",customerB.toString(),"isAdmin",true));
        var identity=FirebaseAdminTokenVerifier.identity(firebase);when(verifier.verify(TOKEN)).thenReturn(identity);
        problem(req("GET","/trailers/"+t.get("id"),null,TOKEN),404,"RESOURCE_NOT_FOUND");
        problem(req("POST","/trailers",Map.of("operationalNumber","denied"),TOKEN),403,"CAPABILITY_DENIED");
        verify(firebase,never()).getClaims();
    }
    @Test void qrIssueRevokeReissueRetainsHistoryAndUuidAndUsesOneActiveConstraint() throws Exception {
        var t=create(Map.of("operationalNumber","qr"));String p="/trailers/"+t.get("id")+"/qr-identities";
        var issued=req("POST",p,null,TOKEN);assertThat(issued.statusCode()).isEqualTo(201);var qr=body(issued);
        String token=(String)qr.get("publicToken");
        assertThat(token).hasSize(22);assertThat(Base64.getUrlDecoder().decode(token)).hasSize(16);
        problem(req("POST",p,null,TOKEN),409,"RESOURCE_CONFLICT");
        assertThat(items(req("GET",p,null,TOKEN))).hasSize(1);
        assertThat(scan(token,TOKEN).statusCode()).isEqualTo(200);
        String revoke=p+"/"+qr.get("id")+"/revocation";
        assertThat(req("POST",revoke,null,TOKEN).statusCode()).isEqualTo(204);
        assertThat(req("POST",revoke,null,TOKEN).statusCode()).isEqualTo(204);
        problem(scan(token,TOKEN),404,"RESOURCE_NOT_FOUND");
        var reissued=body(req("POST",p,null,TOKEN));String newToken=(String)reissued.get("publicToken");
        assertThat(newToken).isNotEqualTo(token);assertThat(reissued).containsEntry("trailerId",t.get("id"));
        assertThat(items(req("GET",p,null,TOKEN))).hasSize(2).extracting(i->i.get("active")).containsExactly(true,false);
        assertThat(scan(newToken,TOKEN).statusCode()).isEqualTo(200);
        var other=create(Map.of("operationalNumber","other"));
        problem(req("POST","/trailers/"+other.get("id")+"/qr-identities/"+reissued.get("id")+"/revocation",null,TOKEN),404,"RESOURCE_NOT_FOUND");
    }
    @Test void simultaneousQrIssuesSerializeOnTrailerWithoutImplicitRevocation() throws Exception {
        var t=create(Map.of("operationalNumber","race"));String p="/trailers/"+t.get("id")+"/qr-identities";
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return req("POST",p,null,TOKEN);});
            var b=pool.submit(()->{start.await();return req("POST",p,null,TOKEN);});
            start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,409);
        }
        assertThat(f.db.value("SELECT count(*) FROM trailer_qr_identities WHERE trailer_id=?",UUID.fromString((String)t.get("id")))).isEqualTo(1L);
        assertThat(f.db.value("SELECT version FROM trailers WHERE id=?",UUID.fromString((String)t.get("id")))).isEqualTo(0L);
    }
    @ParameterizedTest @ValueSource(strings={"TENANT_ADMIN","TRANSPORT_OPERATIONS","CUSTOMER_DISPATCHER","YARD_SPOTTER","DRIVER"})
    void scanPermissionIdentifiesButDoesNotGiveGenericEditOrPhysicalMutation(String role) throws Exception {
        var t=create(Map.of("operationalNumber","scan","designatedCustomerId",customerA));String p="/trailers/"+t.get("id")+"/qr-identities";
        var qr=body(req("POST",p,null,TOKEN));String token=(String)qr.get("publicToken");
        replaceRoles(role,role.equals("CUSTOMER_DISPATCHER")?customerA:null);
        var response=scan(token,TOKEN);assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(body(response)).containsOnlyKeys("trailerId","operationalNumber","trailerType","locationState","loadState","serviceabilityState","active");
        if(!role.equals("TENANT_ADMIN")) {
            problem(req("POST",p,null,TOKEN),403,"CAPABILITY_DENIED");
            problem(req("POST",p+"/"+qr.get("id")+"/revocation",null,TOKEN),403,"CAPABILITY_DENIED");
        }
    }
    @Test void allQrAuthorizationDenialsLookLikeMissingResourcesAndTokensNeverLeak(CapturedOutput output) throws Exception {
        var t=create(Map.of("operationalNumber","private","designatedCustomerId",customerB));
        String token=(String)body(req("POST","/trailers/"+t.get("id")+"/qr-identities",null,TOKEN)).get("publicToken");
        replaceRoles("CUSTOMER_DISPATCHER",customerA);
        var denial=scan(token,TOKEN);problem(denial,404,"RESOURCE_NOT_FOUND");
        String detail=(String)body(denial).get("detail");
        f.db.execute("UPDATE trailers SET designated_customer_id=NULL WHERE id=?",UUID.fromString((String)t.get("id")));
        assertThat(body(scan(token,TOKEN))).containsEntry("detail",detail);
        for(String status:List.of("SUSPENDED","DISABLED")) {
            f.db.execute("UPDATE tenant_memberships SET status=? WHERE id=?",status,membership);
            problem(scan(token,TOKEN),404,"RESOURCE_NOT_FOUND");assertThat(body(scan(token,TOKEN))).containsEntry("detail",detail);
        }
        f.db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        f.db.execute("DELETE FROM tenant_memberships WHERE id=?",membership);
        problem(scan(token,TOKEN),404,"RESOURCE_NOT_FOUND");
        problem(scan("AAAAAAAAAAAAAAAAAAAAAA",TOKEN),404,"RESOURCE_NOT_FOUND");
        problem(scan("synthetic-secret-qr-token",TOKEN),400,"VALIDATION_FAILED");
        problem(scan(token,null),401,"AUTHENTICATION_REQUIRED");
        assertThat(denial.body()).doesNotContain(token,customerB.toString(),tenant.toString(),"roles","scope","private");
        assertThat(output.getAll()).doesNotContain(token,TOKEN,"synthetic-secret-qr-token");
    }
    @ParameterizedTest @ValueSource(strings={"08006","57014","P0001"})
    void infrastructureFailuresRemainSafe5xxAndRollbackMetadata(String state,CapturedOutput output) throws Exception {
        var t=create(Map.of("operationalNumber","fault"));
        f.db.execute("CREATE FUNCTION test_trailer_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private QR synthetic-secret-qr-token' USING ERRCODE='"+state+"'; END $$");
        f.db.execute("CREATE TRIGGER test_trailer_failure BEFORE INSERT OR UPDATE ON trailers FOR EACH ROW EXECUTE FUNCTION test_trailer_failure()");
        try {
            for(var response:List.of(req("POST","/trailers",Map.of("operationalNumber","fault2"),TOKEN),
                    req("PATCH","/trailers/"+t.get("id"),Map.of("expectedVersion",0,"active",false),TOKEN))) {
                assertThat(response.statusCode()).isEqualTo(500);assertThat(body(response)).containsEntry("detail","The server could not complete the request.");
                assertThat(response.body()).doesNotContain("RESOURCE_CONFLICT","SQL","constraint","private","synthetic-secret-qr-token");
            }
            assertThat(f.db.value("SELECT version FROM trailers WHERE id=?",UUID.fromString((String)t.get("id")))).isEqualTo(0L);
            assertThat(output.getAll()).doesNotContain("synthetic-secret-qr-token","private QR");
        } finally { f.db.execute("DROP TRIGGER test_trailer_failure ON trailers");f.db.execute("DROP FUNCTION test_trailer_failure()"); }
    }
    Map<String,Object> create(Map<String,Object> fields) throws Exception {
        var response=req("POST","/trailers",fields,TOKEN);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return body(response);
    }
    HttpResponse<String> req(String method,String path,Object json,String bearer) throws Exception {return http(method,"/api/v1/tenants/"+tenant+path,json,bearer);}
    HttpResponse<String> scan(String token,String bearer) throws Exception {return http("GET","/api/v1/trailer-qr/"+token,null,bearer);}
    HttpResponse<String> http(String method,String path,Object json,String bearer) throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type","application/json").header("X-Request-Id","trailer-test")
                .method(method,json==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(json)));
            if(bearer!=null) {builder.header("Authorization","Bearer "+bearer);}
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Map<String,Object> body(HttpResponse<String> response) {return JsonPath.read(response.body(),"$");}
    List<Map<String,Object>> items(HttpResponse<String> response) {assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return JsonPath.read(response.body(),"$.items");}
    void problem(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("trailer-test");
        assertThat(body(response)).containsEntry("code",code).containsKey("traceId");
    }
}
