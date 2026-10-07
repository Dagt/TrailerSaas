package com.trailersaas.identity.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.EquipmentFixtures;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class EquipmentHttpPostgresTests extends PostgresIntegrationTest {
    static final String TOKEN="synthetic-secret-equipment-token";
    @Autowired Flyway flyway;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    final JsonMapper mapper=JsonMapper.builder().build();
    EquipmentFixtures fixtures;
    UUID tenant,actor,membership;
    @BeforeEach void seed() throws Exception {
        fixtures=new EquipmentFixtures(flyway);tenant=fixtures.tenant();actor=fixtures.user();
        membership=fixtures.membership(tenant,actor,"ACTIVE","TENANT_ADMIN");
        when(verifier.verify(TOKEN)).thenReturn(new VerifiedFirebaseIdentity("fixture-"+actor,null,null));
    }
    @Test void driverCrudNullableFieldsPastCertificateNoopDeactivateReactivateAndStaleVersion() throws Exception {
        var first=created("/drivers","{\"name\":\"Zed\",\"licenseNumber\":\"same\",\"medicalCertificateExpires\":\"2000-01-01\"}");
        var second=created("/drivers","{\"name\":\"Amy\",\"licenseNumber\":\"same\"}");
        assertThat(first).containsEntry("userId",null).containsEntry("active",true).containsEntry("version",0).containsEntry("medicalCertificateExpires","2000-01-01");
        assertThat(items(request("GET","/drivers",null,TOKEN))).extracting(i->i.get("id")).containsExactly(second.get("id"),first.get("id"));
        String path="/drivers/"+first.get("id");
        assertThat(body(request("GET",path,null,TOKEN))).isEqualTo(first);
        assertThat(body(request("PATCH",path,"{\"expectedVersion\":0}",TOKEN))).isEqualTo(first);
        var updated=request("PATCH",path,"{\"expectedVersion\":0,\"name\":\"Changed\",\"licenseNumber\":null,\"medicalCertificateExpires\":null,\"active\":false}",TOKEN);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated)).containsEntry("name","Changed").containsEntry("licenseNumber",null).containsEntry("medicalCertificateExpires",null).containsEntry("active",false).containsEntry("version",1);
        problem(request("PATCH",path,"{\"expectedVersion\":0,\"name\":\"stale\"}",TOKEN),409,"STALE_VERSION");
        assertThat(body(request("PATCH",path,"{\"expectedVersion\":1,\"active\":true}",TOKEN))).containsEntry("version",2).containsEntry("active",true);
        assertThat(request("DELETE",path,null,TOKEN).statusCode()).isEqualTo(405);
    }
    @Test void tractorCrudPreservesDisplayGeneratedKeyRemainsPrivateAndPatchesRecheckDuplicates() throws Exception {
        var z=created("/tractors","{\"operationalNumber\":\" z02 \"}");
        var a=created("/tractors","{\"operationalNumber\":\"a  001\"}");
        assertThat(z).containsEntry("operationalNumber"," z02 ").containsEntry("active",true).containsEntry("version",0).doesNotContainKey("operationalNumberNormalized");
        assertThat(items(request("GET","/tractors",null,TOKEN))).extracting(i->i.get("id")).containsExactly(a.get("id"),z.get("id"));
        problem(request("POST","/tractors","{\"operationalNumber\":\"Z02\"}",TOKEN),409,"RESOURCE_CONFLICT");
        String path="/tractors/"+z.get("id");
        assertThat(body(request("GET",path,null,TOKEN))).isEqualTo(z);
        assertThat(body(request("PATCH",path,"{\"expectedVersion\":0}",TOKEN))).isEqualTo(z);
        problem(request("PATCH",path,"{\"expectedVersion\":0,\"operationalNumber\":\"A 001\"}",TOKEN),409,"RESOURCE_CONFLICT");
        var updated=request("PATCH",path,"{\"expectedVersion\":0,\"operationalNumber\":\" x  03 \",\"active\":false}",TOKEN);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated)).containsEntry("operationalNumber"," x  03 ").containsEntry("version",1).containsEntry("active",false);
        problem(request("PATCH",path,"{\"expectedVersion\":0,\"active\":true}",TOKEN),409,"STALE_VERSION");
        assertThat(body(request("PATCH",path,"{\"expectedVersion\":1,\"active\":true}",TOKEN))).containsEntry("version",2).containsEntry("active",true);
        assertThat(request("DELETE",path,null,TOKEN).statusCode()).isEqualTo(405);
    }
    @Test void linkUnlinkDoesNotMutateIdentityAccessAndLinkNeverSilentlyReplaces() throws Exception {
        UUID target=fixtures.user();UUID member=fixtures.membership(tenant,target,"ACTIVE","DRIVER");
        var driver=created("/drivers","{\"name\":\"Linked\"}");String path="/drivers/"+driver.get("id")+"/user-link";
        var linked=request("POST",path,linkBody(target,0),TOKEN);
        assertThat(linked.statusCode()).isEqualTo(200);
        assertThat(body(linked)).containsEntry("userId",target.toString()).containsEntry("version",1);
        problem(request("POST",path,linkBody(target,1),TOKEN),409,"RESOURCE_CONFLICT");
        UUID replacement=fixtures.user();fixtures.membership(tenant,replacement,"ACTIVE","DRIVER");
        problem(request("POST",path,linkBody(replacement,1),TOKEN),409,"RESOURCE_CONFLICT");
        problem(request("DELETE",path,"{\"expectedVersion\":0}",TOKEN),409,"STALE_VERSION");
        assertThat(request("DELETE",path,"{\"expectedVersion\":1}",TOKEN).statusCode()).isEqualTo(204);
        assertThat(request("DELETE",path,"{\"expectedVersion\":2}",TOKEN).statusCode()).isEqualTo(204);
        var refreshed=body(request("GET","/drivers/"+driver.get("id"),null,TOKEN));
        assertThat(refreshed).containsEntry("version",2).containsEntry("userId",null);
        assertThat(fixtures.value("SELECT status FROM tenant_memberships WHERE id=?",member)).isEqualTo("ACTIVE");
        assertThat(fixtures.value("SELECT count(*) FROM role_assignments WHERE membership_id=?",member)).isEqualTo(1L);
        assertThat(fixtures.value("SELECT count(*) FROM users WHERE id=?",target)).isEqualTo(1L);
        assertThat(body(request("POST",path,linkBody(replacement,2),TOKEN))).containsEntry("version",3).containsEntry("userId",replacement.toString());
    }
    @ParameterizedTest @ValueSource(strings={"TENANT_ADMIN","TRANSPORT_OPERATIONS","CUSTOMER_DISPATCHER","YARD_SPOTTER","SUSPENDED","DISABLED","no-membership","nonexistent","foreign-tenant"})
    void linkTargetNeedsFreshActiveDriverMembershipInSameTenant(String condition) throws Exception {
        UUID target=condition.equals("nonexistent")?UUID.randomUUID():fixtures.user();
        if(!List.of("no-membership","nonexistent").contains(condition)) {
            String status=List.of("SUSPENDED","DISABLED").contains(condition)?condition:"ACTIVE";
            String role=List.of("SUSPENDED","DISABLED","foreign-tenant").contains(condition)?"DRIVER":condition;
            fixtures.membership(condition.equals("foreign-tenant")?fixtures.tenant():tenant,target,status,role);
        }
        var driver=created("/drivers","{\"name\":\"Eligibility\"}");
        var response=request("POST","/drivers/"+driver.get("id")+"/user-link",linkBody(target,0),TOKEN);
        problem(response,409,"RESOURCE_CONFLICT");
        assertThat(response.body()).doesNotContain(condition,target.toString(),"DRIVER","membership","fixture-");
        assertThat(fixtures.value("SELECT user_id FROM drivers WHERE id=?",UUID.fromString((String)driver.get("id")))).isNull();
    }
    @Test void sameUserLinksAcrossTenantsButDuplicateLinkInOneTenantIsDbRejected() throws Exception {
        UUID target=fixtures.user();fixtures.membership(tenant,target,"ACTIVE","DRIVER");
        var first=created("/drivers","{\"name\":\"One\"}");var second=created("/drivers","{\"name\":\"Two\"}");
        assertThat(request("POST","/drivers/"+first.get("id")+"/user-link",linkBody(target,0),TOKEN).statusCode()).isEqualTo(200);
        problem(request("POST","/drivers/"+second.get("id")+"/user-link",linkBody(target,0),TOKEN),409,"RESOURCE_CONFLICT");
        UUID originalTenant=tenant;tenant=fixtures.tenant();fixtures.membership(tenant,actor,"ACTIVE","TENANT_ADMIN");
        fixtures.membership(tenant,target,"ACTIVE","DRIVER");
        var third=created("/drivers","{\"name\":\"Other Tenant\"}");
        assertThat(request("POST","/drivers/"+third.get("id")+"/user-link",linkBody(target,0),TOKEN).statusCode()).isEqualTo(200);
        assertThat(fixtures.value("SELECT count(*) FROM drivers WHERE user_id=?",target)).isEqualTo(2L);
        tenant=originalTenant;
    }
    @Test void linkEligibilityChangesAreFreshAndFirebaseRoleHintsCannotSubstituteDatabaseRole() throws Exception {
        UUID target=fixtures.user();var member=fixtures.membership(tenant,target,"ACTIVE","DRIVER");
        fixtures.execute("DELETE FROM role_assignments WHERE membership_id=?",member);
        var sdk=mock(com.google.firebase.auth.FirebaseToken.class);
        when(sdk.getUid()).thenReturn("fixture-"+target);
        when(sdk.getClaims()).thenReturn(Map.of("role","DRIVER","tenantId",tenant.toString(),"isAdmin",true));
        var identity=FirebaseAdminTokenVerifier.identity(sdk);
        when(verifier.verify("forged")).thenReturn(identity);
        assertThat(request("GET","/me",null,"forged").statusCode()).isEqualTo(404); // Tenant path cannot become a bootstrap bypass.
        var driver=created("/drivers","{\"name\":\"Fresh\"}");
        problem(request("POST","/drivers/"+driver.get("id")+"/user-link",linkBody(target,0),TOKEN),409,"RESOURCE_CONFLICT");
        problem(request("POST","/drivers","{\"name\":\"Forged\"}","forged"),403,"CAPABILITY_DENIED");
        verify(sdk,never()).getClaims();
    }
    @ParameterizedTest @ValueSource(strings={"TRANSPORT_OPERATIONS","CUSTOMER_DISPATCHER","YARD_SPOTTER","DRIVER"})
    void operationsReadsOnlyAndOtherRolesCannotBrowseOrWrite(String role) throws Exception {
        var driver=created("/drivers","{\"name\":\"Protected\"}");var tractor=created("/tractors","{\"operationalNumber\":\"R1\"}");
        UUID reader=fixtures.user();fixtures.membership(tenant,reader,"ACTIVE",role);
        when(verifier.verify("reader")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+reader,null,null));
        for(String path:List.of("/drivers","/drivers/"+driver.get("id"),"/tractors","/tractors/"+tractor.get("id"))) {
            var response=request("GET",path,null,"reader");
            if(role.equals("TRANSPORT_OPERATIONS")) { assertThat(response.statusCode()).isEqualTo(200); }
            else { problem(response,403,"CAPABILITY_DENIED"); }
        }
        problem(request("POST","/drivers","{\"name\":\"Forbidden\"}","reader"),403,"CAPABILITY_DENIED");
        problem(request("POST","/tractors","{\"operationalNumber\":\"Forbidden\"}","reader"),403,"CAPABILITY_DENIED");
        problem(request("PATCH","/drivers/"+driver.get("id"),"{\"expectedVersion\":0,\"active\":false}","reader"),403,"CAPABILITY_DENIED");
        problem(request("PATCH","/tractors/"+tractor.get("id"),"{\"expectedVersion\":0,\"active\":false}","reader"),403,"CAPABILITY_DENIED");
        problem(request("POST","/drivers/"+driver.get("id")+"/user-link",linkBody(reader,0),"reader"),403,"CAPABILITY_DENIED");
        problem(request("DELETE","/drivers/"+driver.get("id")+"/user-link","{\"expectedVersion\":0}","reader"),403,"CAPABILITY_DENIED");
    }
    @Test void authenticationTenantAccessCrossTenantIdsAndCorrelationRemainPrivate(CapturedOutput output) throws Exception {
        problem(request("GET","/drivers",null,null),401,"AUTHENTICATION_REQUIRED");
        problem(request("GET","/tractors",null,null),401,"AUTHENTICATION_REQUIRED");
        UUID foreign=fixtures.tenant();UUID otherDriver=fixtures.driver(foreign,null);UUID otherTractor=fixtures.tractor(foreign,"foreign-secret");
        for(String path:List.of("/drivers/"+otherDriver,"/tractors/"+otherTractor)) {
            var read=request("GET",path,null,TOKEN);problem(read,404,"RESOURCE_NOT_FOUND");
            assertThat(read.body()).doesNotContain("foreign-secret",foreign.toString());
            problem(request("PATCH",path,"{\"expectedVersion\":0,\"active\":false}",TOKEN),404,"RESOURCE_NOT_FOUND");
        }
        problem(request("POST","/drivers/"+otherDriver+"/user-link",linkBody(actor,0),TOKEN),404,"RESOURCE_NOT_FOUND");
        problem(request("DELETE","/drivers/"+otherDriver+"/user-link","{\"expectedVersion\":0}",TOKEN),404,"RESOURCE_NOT_FOUND");
        fixtures.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
        problem(request("GET","/drivers",null,TOKEN),403,"TENANT_ACCESS_DENIED");
        fixtures.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?",membership);
        tenant=foreign;problem(request("GET","/tractors",null,TOKEN),403,"TENANT_ACCESS_DENIED");
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }
    @Test void strictRequestValidationProtectsImmutableAndDedicatedLinkFields() throws Exception {
        var driver=created("/drivers","{\"name\":\"Validation\"}");String path="/drivers/"+driver.get("id");
        for(String json:List.of("{}","{\"name\":\" \"}","{\"name\":\"X\",\"userId\":null}","{\"name\":\"X\",\"active\":false}","{\"name\":\"X\",\"version\":0}","{\"name\":\"X\",\"medicalCertificateExpires\":\"bad\"}")) {
            problem(request("POST","/drivers",json,TOKEN),400,"VALIDATION_FAILED");
        }
        for(String json:List.of("{}","{\"expectedVersion\":null}","{\"expectedVersion\":-1}","{\"expectedVersion\":0.5}","{\"expectedVersion\":0,\"userId\":null}","{\"expectedVersion\":0,\"active\":null}","{\"expectedVersion\":0,\"name\":null}")) {
            problem(request("PATCH",path,json,TOKEN),400,"VALIDATION_FAILED");
        }
        problem(request("POST",path+"/user-link","{\"expectedVersion\":0,\"userId\":\"1-1-1-1-1\"}",TOKEN),400,"VALIDATION_FAILED");
        problem(request("DELETE",path+"/user-link","{\"expectedVersion\":0,\"userId\":null}",TOKEN),400,"VALIDATION_FAILED");
        for(String number:List.of("","   ","T\t1","T\n1","T\r1","T\u00001","T\u00851")) {
            problem(request("POST","/tractors",mapper.writeValueAsString(Map.of("operationalNumber",number)),TOKEN),400,"VALIDATION_FAILED");
        }
        var tractor=created("/tractors","{\"operationalNumber\":\"Valid\"}");
        problem(request("PATCH","/tractors/"+tractor.get("id"),"{\"expectedVersion\":0,\"operationalNumber\":\"T\\t1\"}",TOKEN),400,"VALIDATION_FAILED");
        problem(request("POST","/tractors","{\"operationalNumber\":\"V\",\"active\":false}",TOKEN),400,"VALIDATION_FAILED");
        for(String collection:List.of("/drivers","/tractors")) { problem(request("GET",collection+"?unknown=true",null,TOKEN),400,"VALIDATION_FAILED"); }
    }
    @ParameterizedTest @ValueSource(strings={"08006","57014","P0001"})
    void databaseInfrastructureFailuresAreSafe500ForBothCreateAndUpdate(String state) throws Exception {
        var driver=created("/drivers","{\"name\":\"Fault\"}");var tractor=created("/tractors","{\"operationalNumber\":\"Fault\"}");
        fixtures.execute("""
                CREATE FUNCTION test_equipment_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic private DB detail' USING ERRCODE='%s'; END $$
                """.formatted(state));
        for(String table:List.of("drivers","tractors")) { fixtures.execute("CREATE TRIGGER test_equipment_failure BEFORE INSERT OR UPDATE ON "+table+" FOR EACH ROW EXECUTE FUNCTION test_equipment_failure()"); }
        try {
            serverError(request("POST","/drivers","{\"name\":\"Server fault\"}",TOKEN));
            serverError(request("POST","/tractors","{\"operationalNumber\":\"Server fault\"}",TOKEN));
            serverError(request("PATCH","/drivers/"+driver.get("id"),"{\"expectedVersion\":0,\"active\":false}",TOKEN));
            serverError(request("PATCH","/tractors/"+tractor.get("id"),"{\"expectedVersion\":0,\"active\":false}",TOKEN));
            assertThat(fixtures.value("SELECT version FROM drivers WHERE id=?",UUID.fromString((String)driver.get("id")))).isEqualTo(0L);
            assertThat(fixtures.value("SELECT version FROM tractors WHERE id=?",UUID.fromString((String)tractor.get("id")))).isEqualTo(0L);
        } finally {
            for(String table:List.of("drivers","tractors")) { fixtures.execute("DROP TRIGGER test_equipment_failure ON "+table); }
            fixtures.execute("DROP FUNCTION test_equipment_failure()");
        }
    }
    @Test void concurrentSameTenantLinksArbitrateAtDatabaseUniqueIndex() throws Exception {
        UUID target=fixtures.user();fixtures.membership(tenant,target,"ACTIVE","DRIVER");
        var a=created("/drivers","{\"name\":\"Race A\"}");var b=created("/drivers","{\"name\":\"Race B\"}");
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return request("POST","/drivers/"+a.get("id")+"/user-link",linkBody(target,0),TOKEN);});
            var second=pool.submit(()->{start.await();return request("POST","/drivers/"+b.get("id")+"/user-link",linkBody(target,0),TOKEN);});
            start.countDown();var results=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200,409);
            results.stream().filter(r->r.statusCode()==409).forEach(r->problem(r,409,"RESOURCE_CONFLICT"));
        }
        assertThat(fixtures.value("SELECT count(*) FROM drivers WHERE tenant_id=? AND user_id=?",tenant,target)).isEqualTo(1L);
    }
    private String linkBody(UUID user,long version) { return mapper.writeValueAsString(Map.of("userId",user.toString(),"expectedVersion",version)); }
    private Map<String,Object> created(String path,String json) throws Exception {
        var response=request("POST",path,json,TOKEN);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return body(response);
    }
    private HttpResponse<String> request(String method,String path,String json,String bearer) throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+path)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type","application/json").header("X-Request-Id","equipment-test")
                    .method(method,json==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json));
            if(bearer!=null) { builder.header("Authorization","Bearer "+bearer); }
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private Map<String,Object> body(HttpResponse<String> response) { return JsonPath.read(response.body(),"$"); }
    private List<Map<String,Object>> items(HttpResponse<String> response) { assertThat(response.statusCode()).isEqualTo(200);return JsonPath.read(response.body(),"$.items"); }
    private void problem(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("equipment-test");
        assertThat(body(response)).containsEntry("code",code).containsKey("traceId");
    }
    private void serverError(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("equipment-test");
        assertThat(body(response)).containsEntry("detail","The server could not complete the request.").containsKey("traceId");
        assertThat(response.body()).doesNotContain("synthetic private DB detail","test_equipment","SQL","constraint","RESOURCE_CONFLICT","normalized");
    }
}
