package com.trailersaas.identity.internal;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.trailersaas.EquipmentFixtures;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="trailersaas.cors.allowed-origins=https://web.example.test")
class TenantSettingsHttpPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway; @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    EquipmentFixtures f; UUID tenant, user, membership;
    @BeforeEach void seed() throws Exception {
        f=new EquipmentFixtures(flyway);tenant=f.tenant();user=f.user();membership=f.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
        f.execute("INSERT INTO tenant_settings VALUES(?,72,NULL,now(),now(),0)",tenant);
        when(verifier.verify("settings-test-token")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
    }
    @Test void getPatchNoopNullableTimezoneStaleAndCorrelation() throws Exception {
        var get=request("GET",null,true);assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).contains("\"staleTrailerThresholdHours\":72","\"defaultTimezone\":null","\"version\":0");
        assertThat(request("PATCH","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":72}",true).body()).isEqualTo(get.body());
        var patch=request("PATCH","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":1,\"defaultTimezone\":\"America/Chicago\"}",true);
        assertThat(patch.statusCode()).isEqualTo(200);assertThat(patch.body()).contains("\"version\":1","America/Chicago");
        problem(request("PATCH","{\"expectedVersion\":0}",true),409,"STALE_VERSION");
        assertThat(request("PATCH","{\"expectedVersion\":1,\"defaultTimezone\":null}",true).body()).contains("\"defaultTimezone\":null","\"version\":2");
        problem(request("GET",null,false),401,"AUTHENTICATION_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":0}","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":1.5}","{\"expectedVersion\":0,\"defaultTimezone\":\"Unknown/Zone\"}","{\"expectedVersion\":0,\"retention\":3}"})
    void invalidFields(String body) throws Exception { problem(request("PATCH",body,true),400,"VALIDATION_FAILED"); }
    @ParameterizedTest @ValueSource(strings={"TRANSPORT_OPERATIONS","CUSTOMER_DISPATCHER","YARD_SPOTTER","DRIVER","SUSPENDED"})
    void otherRolesOrSuspensionCannotManageSettings(String role) throws Exception {
        f.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        if(role.equals("SUSPENDED")) { f.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership); }
        else {
            UUID customer = null;
            if (role.equals("CUSTOMER_DISPATCHER")) {
                customer = UUID.randomUUID();
                f.execute("INSERT INTO customers VALUES(?,?,'Settings fixture','ACTIVE',now(),now(),0)",customer,tenant);
            }
            f.execute("INSERT INTO role_assignments VALUES(?,?,?,?,?,?,now())",UUID.randomUUID(),tenant,membership,role,
                    customer == null ? "TENANT" : "CUSTOMER",customer);
        }
        problem(request("GET",null,true),403,role.equals("SUSPENDED")?"TENANT_ACCESS_DENIED":"CAPABILITY_DENIED");
        problem(request("PATCH","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":99}",true),403,role.equals("SUSPENDED")?"TENANT_ACCESS_DENIED":"CAPABILITY_DENIED");
    }
    @Test void concurrentPatchesProduceOneWinner() throws Exception {
        var start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->{start.await();return request("PATCH","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":50}",true);});
            var second=executor.submit(()->{start.await();return request("PATCH","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":60}",true);});
            start.countDown();var results=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200,409);
            problem(results.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"STALE_VERSION");
            assertThat(f.value("SELECT version FROM tenant_settings WHERE tenant_id=?",tenant)).isEqualTo(1L);
        }
    }
    @Test void firebaseCustomClaimsCannotGrantSettingsAccess() throws Exception {
        f.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        var sdk=mock(com.google.firebase.auth.FirebaseToken.class);
        when(sdk.getUid()).thenReturn("fixture-"+user);
        when(sdk.getClaims()).thenReturn(java.util.Map.of("role","TENANT_ADMIN","tenantId",tenant.toString(),"isAdmin",true));
        when(verifier.verify("settings-test-token")).thenReturn(FirebaseAdminTokenVerifier.identity(sdk));
        problem(request("GET",null,true),403,"CAPABILITY_DENIED");
        problem(request("PATCH","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":99}",true),403,"CAPABILITY_DENIED");
        verify(sdk,never()).getClaims();
        assertThat(f.value("SELECT stale_trailer_threshold_hours FROM tenant_settings WHERE tenant_id=?",tenant)).isEqualTo(72);
    }
    @Test void corsExplicitOriginsHeadersAndAuthentication() throws Exception {
        var allowed=preflight("https://web.example.test");assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("https://web.example.test");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase()).contains("authorization","content-type","x-request-id");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(preflight("https://foreign.example.test").headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        var actual=client.send(HttpRequest.newBuilder(uri()).header("Origin","https://web.example.test").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(actual.statusCode()).isEqualTo(401);
        assertThat(actual.headers().firstValue("Access-Control-Expose-Headers")).contains("X-Request-Id");
    }
    HttpResponse<String> preflight(String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(uri()).header("Origin",origin).header("Access-Control-Request-Method","PATCH")
                .header("Access-Control-Request-Headers","Authorization,Content-Type,X-Request-Id")
                .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    URI uri() { return URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/settings"); }
    HttpResponse<String> request(String method,String body,boolean bearer) throws Exception {
        var builder=HttpRequest.newBuilder(uri()).header("X-Request-Id","settings-correlation");
        if(bearer) { builder.header("Authorization","Bearer settings-test-token"); }
        if(body!=null) { builder.header("Content-Type","application/json"); }
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void problem(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).isEqualTo(status);assertThat(response.body()).contains(code,"traceId").doesNotContain("settings-test-token","SQL","constraint");
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    }
}
