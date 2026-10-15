package com.trailersaas.customers.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class MasterDataHttpPostgresTests extends PostgresIntegrationTest {
    static final String TOKEN = "synthetic-secret-master-data-bearer";
    @Value("${local.server.port}") int port;
    @Autowired Flyway flyway;
    @Autowired TenantAuthorization authorization;
    @MockitoBean FirebaseTokenVerifier verifier;
    UUID tenant, user, membership;

    @BeforeEach
    void fixture() throws Exception {
        user = UUID.randomUUID(); tenant = UUID.randomUUID(); membership = UUID.randomUUID();
        sql("INSERT INTO users VALUES (?, ?, NULL, NULL, now(), now())", user, "fixture-" + user);
        sql("INSERT INTO tenants VALUES (?, 'Tenant', 'ACTIVE', now(), now())", tenant);
        sql("INSERT INTO tenant_memberships VALUES (?, ?, ?, 'ACTIVE', now(), now())", membership, tenant, user);
        role("TENANT_ADMIN", null);
        when(verifier.verify(TOKEN)).thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
    }

    @Test
    void customerCrudDefaultsOrderingDuplicateAndVersionConflict(CapturedOutput output) throws Exception {
        var z = create("/customers", "{\"name\":\"Z Customer\"}");
        var a = create("/customers", "{\"name\":\"A Customer\"}");
        assertThat(z).containsEntry("status", "ACTIVE").containsEntry("version", 0)
                .containsKeys("createdAt", "updatedAt");
        assertThat(items(get("/customers"))).extracting(x -> x.get("id")).containsExactly(a.get("id"), z.get("id"));
        assertThat(get("/customers/" + a.get("id")).statusCode()).isEqualTo(200);
        problem(request("POST", "/customers", "{\"name\":\"A Customer\"}"), 409, "RESOURCE_CONFLICT");
        var updated = request("PATCH", "/customers/" + a.get("id"),
                "{\"expectedVersion\":0,\"name\":\"Changed\",\"status\":\"INACTIVE\"}");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated)).containsEntry("status", "INACTIVE").containsEntry("version", 1);
        problem(request("PATCH", "/customers/" + a.get("id"), "{\"expectedVersion\":0,\"name\":\"Stale\"}"),
                409, "STALE_VERSION");
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void siteCrudSupportsPartialAddressNullableFieldsAndImmutableLocationSite() throws Exception {
        var site = create("/sites", """
                {"name":"Shared","timezone":"America/Chicago","address":{"line1":"Street","city":"City","countryCode":"US"},
                 "latitude":41.0,"geofenceRadiusM":250}
                """);
        assertThat(site).containsEntry("active", true).containsEntry("version", 0);
        var id = site.get("id");
        var updated = request("PATCH", "/sites/" + id, """
                {"expectedVersion":0,"address":{"line1":null},"latitude":null,"active":false}
                """);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(updated.body(), "$.address.city")).isEqualTo("City");
        assertThat(JsonPath.<Object>read(updated.body(), "$.address.line1")).isNull();
        assertThat(body(updated)).containsEntry("latitude", null).containsEntry("version", 1);
        problem(request("PATCH", "/sites/" + id, "{\"expectedVersion\":0,\"active\":true}"), 409, "STALE_VERSION");
        var loc = create("/sites/" + id + "/locations", "{\"name\":\"Neutral\",\"type\":\"YARD\"}");
        assertThat(loc).containsEntry("customerId", null).containsEntry("version", 0);
        problem(request("PATCH", "/locations/" + loc.get("id"),
                "{\"expectedVersion\":0,\"siteId\":\"" + UUID.randomUUID() + "\"}"), 400, "VALIDATION_FAILED");
    }

    @Test
    void sharedSiteCustomerScopeExcludesOtherAndNeutralLocations(CapturedOutput output) throws Exception {
        sql("UPDATE tenants SET status='ACTIVE' WHERE id=?", tenant);
        var hood = create("/customers", "{\"name\":\"Hood\"}");
        var stanfast = create("/customers", "{\"name\":\"Stanfast\"}");
        var site = create("/sites", "{\"name\":\"710 Kimberly Dr\",\"timezone\":\"America/Chicago\"}");
        var unassociated = create("/sites", "{\"name\":\"Unassociated\",\"timezone\":\"UTC\"}");
        associate(hood, site); associate(stanfast, site);
        create("/sites/" + site.get("id") + "/locations", "{\"name\":\"Dock H\",\"type\":\"DOCK\",\"customerId\":\"" + hood.get("id") + "\"}");
        create("/sites/" + site.get("id") + "/locations", "{\"name\":\"Dock S\",\"type\":\"DOCK\",\"customerId\":\"" + stanfast.get("id") + "\"}");
        create("/sites/" + site.get("id") + "/locations", "{\"name\":\"Yard Neutral\",\"type\":\"YARD\"}");
        assertThat(items(get("/sites/" + site.get("id") + "/locations"))).hasSize(3);
        assertThat(items(get("/sites"))).hasSize(2);
        onlyRole("TRANSPORT_OPERATIONS", null);
        assertThat(items(get("/customers"))).hasSize(2);
        assertThat(items(get("/sites/" + site.get("id") + "/locations"))).hasSize(3);
        for (var customer : List.of(hood, stanfast)) {
            onlyRole("CUSTOMER_DISPATCHER", UUID.fromString((String) customer.get("id")));
            assertThat(items(get("/customers"))).extracting(x -> x.get("id")).containsExactly(customer.get("id"));
            assertThat(items(get("/sites"))).extracting(x -> x.get("id")).containsExactly(site.get("id"));
            var locations = items(get("/sites/" + site.get("id") + "/locations"));
            assertThat(locations).hasSize(1).allSatisfy(x -> assertThat(x.get("customerId")).isEqualTo(customer.get("id")));
            var other = customer == hood ? stanfast : hood;
            problem(get("/customers/" + other.get("id")), 403, "CUSTOMER_SCOPE_DENIED");
            problem(get("/customers/" + UUID.randomUUID()), 403, "CUSTOMER_SCOPE_DENIED");
            problem(get("/sites/" + UUID.randomUUID()), 404, "RESOURCE_NOT_FOUND");
            problem(get("/sites/" + unassociated.get("id")), 404, "RESOURCE_NOT_FOUND");
            problem(get("/sites/" + unassociated.get("id") + "/locations"), 404, "RESOURCE_NOT_FOUND");
            problem(request("POST", "/customers", "{\"name\":\"No write\"}"), 403, "CAPABILITY_DENIED");
        }
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void associationInvariantRemovalSafetyReactivationAndNullablePatch() throws Exception {
        var customer = create("/customers", "{\"name\":\"Customer\"}");
        var site = create("/sites", "{\"name\":\"Site\",\"timezone\":\"UTC\"}");
        String locationPath = "/sites/" + site.get("id") + "/locations";
        String createBody = "{\"name\":\"Dock\",\"type\":\"DOCK\",\"code\":\"14\",\"customerId\":\"" + customer.get("id") + "\"}";
        problem(request("POST", locationPath, createBody), 409, "RESOURCE_CONFLICT");
        associate(customer, site);
        problem(request("POST", "/customers/" + customer.get("id") + "/sites",
                "{\"siteId\":\"" + site.get("id") + "\"}"), 409, "RESOURCE_CONFLICT");
        var location = create(locationPath, createBody);
        String associationPath = "/customers/" + customer.get("id") + "/sites/" + site.get("id");
        problem(request("DELETE", associationPath, null), 409, "RESOURCE_CONFLICT");
        assertThat(request("PATCH", "/locations/" + location.get("id"), "{\"expectedVersion\":0,\"active\":false}").statusCode()).isEqualTo(200);
        assertThat(request("DELETE", associationPath, null).statusCode()).isEqualTo(204);
        problem(request("DELETE", associationPath, null), 404, "RESOURCE_NOT_FOUND");
        assertThat(items(get(locationPath))).hasSize(1); // No cascading history deletion.
        problem(request("PATCH", "/locations/" + location.get("id"), "{\"expectedVersion\":1,\"active\":true}"), 409, "RESOURCE_CONFLICT");
        var neutral = request("PATCH", "/locations/" + location.get("id"),
                "{\"expectedVersion\":1,\"active\":true,\"customerId\":null,\"code\":null}");
        assertThat(neutral.statusCode()).isEqualTo(200);
        assertThat(body(neutral)).containsEntry("customerId", null).containsEntry("code", null).containsEntry("version", 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRIVER", "YARD_SPOTTER"})
    void operationalRolesHaveNoGenericMasterDataAccess(String role) throws Exception {
        onlyRole(role, null);
        problem(get("/customers"), 403, "CAPABILITY_DENIED");
        problem(get("/sites"), 403, "CAPABILITY_DENIED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSPORT_OPERATIONS", "CUSTOMER_DISPATCHER"})
    void readCapabilitiesDoNotGrantWrites(String role) throws Exception {
        var c = create("/customers", "{\"name\":\"Customer\"}");
        var s = create("/sites", "{\"name\":\"Site\",\"timezone\":\"UTC\"}");
        onlyRole(role, role.equals("CUSTOMER_DISPATCHER") ? UUID.fromString((String) c.get("id")) : null);
        problem(request("PATCH", "/customers/" + c.get("id"), "{\"expectedVersion\":0,\"name\":\"Denied\"}"), 403, "CAPABILITY_DENIED");
        problem(request("POST", "/sites", "{\"name\":\"Denied\",\"timezone\":\"UTC\"}"), 403, "CAPABILITY_DENIED");
        problem(request("PATCH", "/sites/" + s.get("id"), "{\"expectedVersion\":0,\"active\":false}"), 403, "CAPABILITY_DENIED");
        problem(request("POST", "/sites/" + s.get("id") + "/locations", "{\"name\":\"Denied\",\"type\":\"YARD\"}"), 403, "CAPABILITY_DENIED");
        problem(request("POST", "/customers/" + c.get("id") + "/sites", "{\"siteId\":\"" + s.get("id") + "\"}"), 403, "CAPABILITY_DENIED");
    }

    @Test
    void scopeBoundaryAggregatesRealCustomersAndRevocationAndSuspensionAreFresh() throws Exception {
        var a = create("/customers", "{\"name\":\"A\"}");
        var b = create("/customers", "{\"name\":\"B\"}");
        onlyRole("CUSTOMER_DISPATCHER", UUID.fromString((String) a.get("id")));
        role("CUSTOMER_DISPATCHER", UUID.fromString((String) b.get("id")));
        var principal = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
        assertThat(authorization.resolveCustomerScope(principal, tenant, Capability.CUSTOMER_VIEW).customerIds())
                .containsExactlyInAnyOrder(UUID.fromString((String) a.get("id")), UUID.fromString((String) b.get("id")));
        assertThat(items(get("/customers"))).hasSize(2);
        sql("DELETE FROM role_assignments WHERE membership_id=?", membership);
        problem(get("/customers"), 403, "CAPABILITY_DENIED");
        role("TENANT_ADMIN", null);
        assertThat(items(get("/customers"))).hasSize(2);
        sql("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?", membership);
        problem(get("/customers"), 403, "TENANT_ACCESS_DENIED");
        assertThat(JsonPath.<String>read(request("GET", "/api/v1/me", null, TOKEN, true).body(), "$.memberships[0].status")).isEqualTo("SUSPENDED");
    }

    @Test
    void authCrossTenantIdsAndUntrustedHintsNeverExposeMetadata(CapturedOutput output) throws Exception {
        problem(request("GET", "/customers", null, null, false), 401, "AUTHENTICATION_REQUIRED");
        problem(request("GET", "/api/v1/tenants/" + UUID.randomUUID() + "/customers", null, TOKEN, true), 403, "TENANT_ACCESS_DENIED");
        UUID foreignTenant = UUID.randomUUID(), foreignCustomer = UUID.randomUUID(), foreignSite = UUID.randomUUID();
        sql("INSERT INTO tenants VALUES (?, 'foreign-secret-tenant', 'CANDIDATE', now(), now())", foreignTenant);
        sql("INSERT INTO customers VALUES (?, ?, 'foreign-secret-customer', 'ACTIVE', now(), now(), 0)", foreignCustomer, foreignTenant);
        sql("INSERT INTO physical_sites(id,tenant_id,name,timezone,active,created_at,updated_at) VALUES(?,?,'foreign-secret-site','UTC',true,now(),now())", foreignSite, foreignTenant);
        problem(get("/customers/" + foreignCustomer), 404, "RESOURCE_NOT_FOUND");
        problem(get("/sites/" + foreignSite), 404, "RESOURCE_NOT_FOUND");
        var own = create("/customers", "{\"name\":\"Own\"}");
        problem(request("POST", "/customers/" + own.get("id") + "/sites", "{\"siteId\":\"" + foreignSite + "\"}"), 404, "RESOURCE_NOT_FOUND");
        onlyRole("CUSTOMER_DISPATCHER", UUID.fromString((String) own.get("id")));
        // Verification returns only identity; spoofed URL claims cannot widen database grants.
        problem(get("/customers?tenantId=" + foreignTenant + "&role=TENANT_ADMIN&isAdmin=true"), 400, "VALIDATION_FAILED");
        assertThat(items(get("/customers"))).extracting(x -> x.get("id")).containsExactly(own.get("id"));
        var denied = get("/customers/" + foreignCustomer);
        problem(denied, 403, "CUSTOMER_SCOPE_DENIED");
        assertThat(denied.body()).doesNotContain("foreign-secret", "CUSTOMER_DISPATCHER", (String) own.get("id"));
        // RFC Problem instance repeats the caller's own request URI, not discovered metadata.
        assertThat(body(denied)).containsEntry("instance", "/api/v1/tenants/" + tenant + "/customers/" + foreignCustomer);
        var publicFields = new java.util.HashMap<>(body(denied));
        publicFields.remove("instance");
        assertThat(publicFields.toString()).doesNotContain(foreignCustomer.toString(), (String) own.get("id"), foreignTenant.toString());
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"name\":\"\",\"timezone\":\"UTC\"}", "{\"name\":\"Site\",\"timezone\":\"Invalid\"}",
        "{\"name\":\"Site\",\"timezone\":\"UTC\",\"latitude\":91}", "{\"name\":\"Site\",\"timezone\":\"UTC\",\"longitude\":-181}",
        "{\"name\":\"Site\",\"timezone\":\"UTC\",\"geofenceRadiusM\":0}", "{\"name\":\"Site\",\"timezone\":\"UTC\",\"address\":{\"countryCode\":\"USA\"}}"
    })
    void invalidSiteFieldsReturnSafeValidationProblem(String json) throws Exception {
        problem(request("POST", "/sites", json), 400, "VALIDATION_FAILED");
    }

    @Test
    void malformedRequestAndMissingVersionHaveStableSafeErrors() throws Exception {
        problem(request("POST", "/customers", "{invalid-json"), 400, "VALIDATION_FAILED");
        problem(get("/customers/not-a-uuid"), 400, "VALIDATION_FAILED");
        var c = create("/customers", "{\"name\":\"Customer\"}");
        problem(request("PATCH", "/customers/" + c.get("id"), "{\"name\":\"Missing version\"}"), 400, "VALIDATION_FAILED");
        problem(request("PATCH", "/customers/" + c.get("id"), "{\"expectedVersion\":0,\"status\":\"UNKNOWN\"}"), 400, "VALIDATION_FAILED");
    }

    @Test void inactiveTenantCannotServeCustomerScopedPortalReads() throws Exception {
        var customer = create("/customers", "{\"name\":\"Own Customer\"}");
        onlyRole("CUSTOMER_DISPATCHER", UUID.fromString(customer.get("id").toString()));
        sql("UPDATE tenants SET status='DISABLED' WHERE id=?", tenant);
        assertThat(get("/customers").statusCode()).isEqualTo(403);
        assertThat(get("/customers/"+customer.get("id")).statusCode()).isEqualTo(403);
    }

    @Test void portalMembershipPublishesTenantStateAndCustomerRolesWithoutTenantCapabilities() throws Exception {
        var c = create("/customers", "{\"name\":\"Portal Customer\"}"); onlyRole("CUSTOMER_DISPATCHER", UUID.fromString(c.get("id").toString()));
        var me = request("GET", "/api/v1/me", null, TOKEN, true);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(me.body(), "$.memberships[0].tenantStatus")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<List<?>>read(me.body(), "$.memberships[0].capabilities")).isEmpty();
        assertThat(JsonPath.<String>read(me.body(), "$.memberships[0].roles[0].customerId")).isEqualTo(c.get("id"));
        assertThat(get("/customers/"+c.get("id")).statusCode()).isEqualTo(200);
        sql("UPDATE tenants SET status='DISABLED' WHERE id=?", tenant);
        var inactive = request("GET", "/api/v1/me", null, TOKEN, true);
        assertThat(JsonPath.<String>read(inactive.body(), "$.memberships[0].tenantStatus")).isEqualTo("DISABLED");
        problem(get("/customers/"+c.get("id")),403,"TENANT_ACCESS_DENIED");
    }
    @ParameterizedTest @ValueSource(strings={"/drivers","/tractors","/settings","/alerts","/dispatch/board"})
    void customerRoleCannotOpenInternalDirectoriesOrAlerts(String path) throws Exception {
        var c = create("/customers", "{\"name\":\"Portal Customer\"}"); onlyRole("CUSTOMER_DISPATCHER", UUID.fromString(c.get("id").toString()));
        assertThat(get(path).statusCode()).as(path).isEqualTo(403);
    }
    @Test void knownForeignAndInventedCustomerHaveTheSameGenericDeniedDetail() throws Exception {
        var a = create("/customers", "{\"name\":\"Assigned\"}");var b = create("/customers", "{\"name\":\"Secret other business\"}");
        onlyRole("CUSTOMER_DISPATCHER", UUID.fromString(a.get("id").toString()));
        var known=get("/customers/"+b.get("id"));var invented=get("/customers/"+UUID.randomUUID());
        problem(known,403,"CUSTOMER_SCOPE_DENIED");problem(invented,403,"CUSTOMER_SCOPE_DENIED");
        assertThat(JsonPath.<String>read(known.body(),"$.detail")).isEqualTo(JsonPath.read(invented.body(),"$.detail"));
        assertThat(known.body()+invented.body()).doesNotContain("Secret other business","Assigned");
    }

    private void associate(Map<String, Object> c, Map<String, Object> s) throws Exception {
        create("/customers/" + c.get("id") + "/sites", "{\"siteId\":\"" + s.get("id") + "\"}");
    }
    private void onlyRole(String role, UUID customer) throws SQLException {
        sql("DELETE FROM role_assignments WHERE membership_id=?", membership); role(role, customer);
    }
    private void role(String role, UUID customer) throws SQLException {
        sql("INSERT INTO role_assignments VALUES(?,?,?,?,?,?,now())", UUID.randomUUID(), tenant, membership,
                role, customer == null ? "TENANT" : "CUSTOMER", customer);
    }
    private void sql(String query, Object... values) throws SQLException {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection(); var statement = connection.prepareStatement(query)) {
            for (int i = 0; i < values.length; i++) { statement.setObject(i + 1, values[i]); }
            statement.executeUpdate();
        }
    }
    private Map<String, Object> create(String path, String json) throws Exception {
        var response = request("POST", path, json);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return body(response);
    }
    private Map<String, Object> body(HttpResponse<String> response) { return JsonPath.read(response.body(), "$"); }
    private List<Map<String, Object>> items(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.items");
    }
    private void problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(body(response)).containsEntry("code", code).containsEntry("status", status).containsKey("traceId");
        assertThat(JsonPath.<String>read(response.body(), "$.traceId")).isNotBlank();
        assertThat(response.headers().firstValue("X-Request-Id")).contains("master-data-test");
        assertThat(response.body()).doesNotContain(TOKEN, "SQL", "constraint", "roleCode", "password", "stackTrace");
    }
    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> request(String method, String path, String json) throws Exception {
        return request(method, path, json, TOKEN, false);
    }
    private HttpResponse<String> request(String method, String path, String json, String token, boolean absolute) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + (absolute ? path : "/api/v1/tenants/" + tenant + path)))
                    .header("X-Request-Id", "master-data-test").timeout(Duration.ofSeconds(10));
            if (token != null) { request.header("Authorization", "Bearer " + token); }
            if (json != null) { request.header("Content-Type", "application/json"); }
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
