package com.trailersaas.identity.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.firebase.auth.FirebaseToken;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MembershipHttpPostgresTests extends PostgresIntegrationTest {
    @Value("${local.server.port}") int port;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Autowired Flyway flyway;
    @Autowired InternalUserResolver users;
    IdentityFixtures fixtures;

    @BeforeEach
    void fixtures() { fixtures = new IdentityFixtures(flyway); }

    @Test
    void userWithoutMembershipsGetsEmptyDirectoryAndNoSession() throws Exception {
        var user = user();
        var response = me(user);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.userId")).isEqualTo(user.toString());
        assertThat(memberships(response)).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        var unauthenticated = request(null);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(unauthenticated.headers().firstValue("X-Request-Id")).contains("membership-directory");
        assertThat(JsonPath.<String>read(unauthenticated.body(), "$.code")).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(JsonPath.<String>read(unauthenticated.body(), "$.traceId")).isNotBlank();
    }

    @Test
    void activeRolelessMembershipReturnsAcceptedFieldsOnly() throws Exception {
        var user = user();
        var tenant = fixtures.tenant("Active tenant");
        fixtures.membership(tenant, user, "ACTIVE");
        var entries = memberships(me(user));
        assertThat(entries).containsExactly(Map.of("tenantId", tenant.toString(), "tenantName", "Active tenant",
                "status", "ACTIVE", "roles", List.of()));
    }

    @Test
    void multipleRolesAndCustomerScopesHaveStableOrderAndNullableCustomer() throws Exception {
        var user = user();
        var tenant = fixtures.tenant("Roles");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        var firstCustomer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var secondCustomer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        fixtures.role(tenant, membership, "YARD_SPOTTER", "TENANT", null);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", secondCustomer);
        fixtures.role(tenant, membership, "TRANSPORT_OPERATIONS", "TENANT", null);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", firstCustomer);
        var response = me(user);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.memberships[0].roles[*].roleCode"))
                .containsExactly("CUSTOMER_DISPATCHER", "CUSTOMER_DISPATCHER", "TRANSPORT_OPERATIONS", "YARD_SPOTTER");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.memberships[0].roles[*].customerId"))
                .containsExactly(firstCustomer.toString(), secondCustomer.toString(), null, null);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.memberships[0].roles[*].scopeType"))
                .containsExactly("CUSTOMER", "CUSTOMER", "TENANT", "TENANT");
        assertThat(me(user).body()).isEqualTo(response.body());
    }

    @Test
    void userDirectoryCannotBeRedirectedByFirebaseClaimsOrHttpUserTenantHints() throws Exception {
        var userA = user();
        var userB = user();
        var tenant1 = fixtures.tenant("A first");
        var tenant2 = fixtures.tenant("A second");
        var tenant3 = fixtures.tenant("B private");
        fixtures.membership(tenant2, userA, "ACTIVE");
        fixtures.membership(tenant1, userA, "ACTIVE");
        var bMembership = fixtures.membership(tenant3, userB, "ACTIVE");
        fixtures.role(tenant3, bMembership, "TENANT_ADMIN", "TENANT", null);
        var firebase = mock(FirebaseToken.class);
        when(firebase.getUid()).thenReturn("fixture-" + userA);
        when(firebase.getClaims()).thenReturn(Map.of("tenantId", tenant3.toString(), "role", "TENANT_ADMIN",
                "customerId", UUID.randomUUID().toString(), "isAdmin", true));
        var verifiedIdentity = FirebaseAdminTokenVerifier.identity(firebase);
        when(verifier.verify("claims-attack")).thenReturn(verifiedIdentity);
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + "/api/v1/me?userId=" + userB + "&tenantId=" + tenant3))
                    .header("Authorization", "Bearer claims-attack").header("X-Tenant-Id", tenant3.toString())
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<List<String>>read(response.body(), "$.memberships[*].tenantId"))
                    .containsExactly(tenant1.toString(), tenant2.toString());
            assertThat(response.body()).doesNotContain(tenant3.toString(), "B private", "TENANT_ADMIN");
        }
        verify(firebase, never()).getClaims();
        assertThat(JsonPath.<List<String>>read(me(userB).body(), "$.memberships[*].tenantId"))
                .containsExactly(tenant3.toString());
        var authentication = new FirebaseAuthenticationProvider(verifier, users).authenticate(
                new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken("claims-attack"));
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUserPrincipal.class);
        assertThat(AuthenticatedUserPrincipal.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("userId", "firebaseUid", "email", "displayName");
    }

    @Test
    void sameTokenReadsNewDatabaseMembershipStatusesAndRoleSetWithoutCachedAuthorization() throws Exception {
        var user = user();
        var tenant = fixtures.tenant("Fresh data");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        fixtures.role(tenant, membership, "DRIVER", "TENANT", null);
        assertThat(JsonPath.<String>read(me(user).body(), "$.memberships[0].status")).isEqualTo("ACTIVE");
        for (var status : List.of("SUSPENDED", "DISABLED")) {
            fixtures.execute("UPDATE tenant_memberships SET status=?, updated_at=now() WHERE id=?", status, membership);
            assertThat(JsonPath.<String>read(me(user).body(), "$.memberships[0].status")).isEqualTo(status);
        }
        fixtures.execute("DELETE FROM role_assignments WHERE tenant_id=? AND membership_id=?", tenant, membership);
        assertThat(JsonPath.<List<?>>read(me(user).body(), "$.memberships[0].roles")).isEmpty();
        var authentication = new FirebaseAuthenticationProvider(verifier, users).authenticate(
                new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken("user-" + user));
        assertThat(authentication.getAuthorities()).isEmpty(); // Directory/status is not authorization.
    }

    @Test
    void allPersistedMembershipStatusesRemainVisibleInNameOrder() throws Exception {
        var user = user();
        fixtures.membership(fixtures.tenant("Z disabled"), user, "DISABLED");
        fixtures.membership(fixtures.tenant("M suspended"), user, "SUSPENDED");
        fixtures.membership(fixtures.tenant("A active"), user, "ACTIVE");
        var response = me(user);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.memberships[*].status"))
                .containsExactly("ACTIVE", "SUSPENDED", "DISABLED");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.memberships[*].tenantName"))
                .containsExactly("A active", "M suspended", "Z disabled");
        assertThat(response.body()).doesNotContain("CANDIDATE_TEST_STATUS"); // Tenant status is not part of /me.
    }

    private UUID user() throws Exception {
        var id = fixtures.user();
        when(verifier.verify("user-" + id)).thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null));
        return id;
    }

    private HttpResponse<String> me(UUID user) throws Exception { return request("user-" + user); }

    private HttpResponse<String> request(String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/me"))
                    .header("X-Request-Id", "membership-directory").timeout(Duration.ofSeconds(10)).GET();
            if (token != null) { request.header("Authorization", "Bearer " + token); }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private List<Map<String, Object>> memberships(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.memberships");
    }
}
