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
import com.trailersaas.orders.AuthorizationConsumerFixture;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthorizationConsumerFixture.class)
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
class AuthorizationHttpPostgresTests extends PostgresIntegrationTest {
    private static final String TOKEN = "synthetic-secret-firebase-token";
    @Value("${local.server.port}") int port;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Autowired Flyway flyway;
    @Autowired InternalUserResolver users;
    @Autowired AuthorizationConsumerFixture.ConsumerService consumer;
    IdentityFixtures fixtures;
    UUID user;

    @BeforeEach
    void fixtures() throws Exception {
        fixtures = new IdentityFixtures(flyway);
        user = fixtures.user();
        when(verifier.verify(TOKEN)).thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
    }

    @Test
    void missingAuthenticationIsStillSafe401(CapturedOutput output) throws Exception {
        var response = request(invite(UUID.randomUUID()), null);
        problem(response, 401, "AUTHENTICATION_REQUIRED", output);
    }

    @Test
    void missingAndNonActiveMembershipsAllDenyButRemainVisibleInMe(CapturedOutput output) throws Exception {
        problem(request(invite(UUID.randomUUID()), TOKEN), 403, "TENANT_ACCESS_DENIED", output);
        for (var status : List.of("SUSPENDED", "DISABLED")) {
            var tenant = fixtures.tenant(status);
            var membership = fixtures.membership(tenant, user, status);
            fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
            var response = request(invite(tenant), TOKEN);
            problem(response, 403, "TENANT_ACCESS_DENIED", output);
            assertThat(response.body()).doesNotContain(membership.toString(), "TENANT_ADMIN", status);
        }
        var me = request("/api/v1/me", TOKEN);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(me.body(), "$.memberships[*].status"))
                .containsExactly("DISABLED", "SUSPENDED");
    }

    @Test
    void activeRolelessAndYardRoleCannotInvite(CapturedOutput output) throws Exception {
        var tenant = fixtures.tenant("Roleless");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        problem(request(invite(tenant), TOKEN), 403, "CAPABILITY_DENIED", output);
        fixtures.role(tenant, membership, "YARD_SPOTTER", "TENANT", null);
        var response = request(invite(tenant), TOKEN);
        problem(response, 403, "CAPABILITY_DENIED", output);
        assertThat(response.body()).doesNotContain("YARD_SPOTTER", membership.toString());
    }

    @Test
    void customerConsumerUsesCapabilityWithoutRoleChecksAndKeepsOtherScopesPrivate(CapturedOutput output) throws Exception {
        var tenant = fixtures.tenant("Scoped");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", a);
        assertThat(request(edit(tenant, a), TOKEN).statusCode()).isEqualTo(200);
        var response = request(edit(tenant, b), TOKEN);
        problem(response, 403, "CUSTOMER_SCOPE_DENIED", output);
        assertThat(response.body()).doesNotContain(a.toString(), "CUSTOMER_DISPATCHER", "roles", "customerId");
        problem(request(invite(tenant), TOKEN), 403, "CAPABILITY_DENIED", output);
        fixtures.role(tenant, membership, "TRANSPORT_OPERATIONS", "TENANT", null);
        assertThat(request(edit(tenant, b), TOKEN).statusCode()).isEqualTo(200);
    }

    @Test
    void sameBearerSessionSeesRoleRemovalAndMembershipSuspension(CapturedOutput output) throws Exception {
        var tenant = fixtures.tenant("Fresh");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
        assertThat(request(invite(tenant), TOKEN).statusCode()).isEqualTo(200);
        fixtures.execute("DELETE FROM role_assignments WHERE tenant_id=? AND membership_id=?", tenant, membership);
        problem(request(invite(tenant), TOKEN), 403, "CAPABILITY_DENIED", output);
        fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
        assertThat(request(invite(tenant), TOKEN).statusCode()).isEqualTo(200);
        fixtures.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE tenant_id=? AND id=?", tenant, membership);
        problem(request(invite(tenant), TOKEN), 403, "TENANT_ACCESS_DENIED", output);
        assertThat(JsonPath.<String>read(request("/api/v1/me", TOKEN).body(), "$.memberships[0].status"))
                .isEqualTo("SUSPENDED");
    }

    @Test
    void assignedWorkConsumerDerivesConditionServerSideNotFromRequest(CapturedOutput output) throws Exception {
        var tenant = fixtures.tenant("Driver");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        fixtures.role(tenant, membership, "DRIVER", "TENANT", null);
        var work = UUID.randomUUID();
        var path = "/api/v1/test-only/tenants/" + tenant + "/work/" + work + "/execute";
        // Client hints cannot create an assignment in this synthetic service.
        problem(request(path + "?assigned=true", TOKEN), 403, "CAPABILITY_DENIED", output);
        consumer.assign(tenant, work, user);
        assertThat(request(path, TOKEN).statusCode()).isEqualTo(200);
        consumer.assign(tenant, work, fixtures.user());
        problem(request(path, TOKEN), 403, "CAPABILITY_DENIED", output);
    }

    @Test
    void maliciousFirebaseBusinessClaimsCannotChangeDatabaseAuthorization(CapturedOutput output) throws Exception {
        var tenant = fixtures.tenant("Actual scope");
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var foreignTenant = fixtures.tenant("Other Tenant");
        var foreignMembership = fixtures.membership(foreignTenant, fixtures.user(), "ACTIVE");
        fixtures.role(foreignTenant, foreignMembership, "TENANT_ADMIN", "TENANT", null);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", a);
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("fixture-" + user);
        when(token.getClaims()).thenReturn(Map.of("role", "TENANT_ADMIN", "tenantId", foreignTenant.toString(),
                "customerId", b.toString(), "isAdmin", true, "capabilities", List.of("USER_INVITE", "ORDER_EDIT")));
        when(verifier.verify(TOKEN)).thenReturn(FirebaseAdminTokenVerifier.identity(token));
        assertThat(request(edit(tenant, a), TOKEN).statusCode()).isEqualTo(200);
        problem(request(edit(tenant, b), TOKEN), 403, "CUSTOMER_SCOPE_DENIED", output);
        problem(request(invite(tenant), TOKEN), 403, "CAPABILITY_DENIED", output);
        problem(request(invite(foreignTenant), TOKEN), 403, "TENANT_ACCESS_DENIED", output);
        verify(token, never()).getClaims();
        var authentication = new FirebaseAuthenticationProvider(verifier, users).authenticate(
                new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken(TOKEN));
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(AuthenticatedUserPrincipal.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("userId", "firebaseUid", "email", "displayName");
    }

    private void problem(HttpResponse<String> response, int status, String code, CapturedOutput output) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(requestId).startsWith("authorization-");
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertThat(body).containsEntry("code", code).containsEntry("status", status);
        String traceId = (String) body.get("traceId");
        assertThat(traceId).matches("[a-f0-9]{32}");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(output.getOut().lines().filter(line -> line.startsWith("{") && line.contains("HTTP request completed"))
                        .map(line -> JsonPath.<Map<String, Object>>read(line, "$")))
                        .anySatisfy(log -> assertThat(log).containsEntry("requestId", requestId)
                                .containsEntry("traceId", traceId).containsEntry("http.status", status)));
        assertThat(response.body()).doesNotContain(TOKEN, "SQL", "password", "firebase", "roleCode", "membershipId");
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    private String invite(UUID tenant) { return "/api/v1/test-only/tenants/" + tenant + "/invite"; }
    private String edit(UUID tenant, UUID customer) {
        return "/api/v1/test-only/tenants/" + tenant + "/customers/" + customer + "/edit";
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("X-Request-Id", "authorization-" + UUID.randomUUID())
                    .timeout(Duration.ofSeconds(10)).GET();
            if (token != null) { builder.header("Authorization", "Bearer " + token); }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
