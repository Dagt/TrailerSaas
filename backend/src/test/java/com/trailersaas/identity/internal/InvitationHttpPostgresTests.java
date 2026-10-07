package com.trailersaas.identity.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
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
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class InvitationHttpPostgresTests extends PostgresIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired Flyway flyway;
    @Autowired InternalUserResolver users;
    @MockitoBean FirebaseTokenVerifier verifier;
    IdentityFixtures fixtures;
    UUID tenant, adminUser, adminMembership;
    final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach void seedAdminWithMigrator() throws Exception {
        fixtures = new IdentityFixtures(flyway);
        tenant = fixtures.tenant("Invitation Tenant");
        adminUser = fixtures.user();
        adminMembership = fixtures.membership(tenant, adminUser, "ACTIVE");
        fixtures.role(tenant, adminMembership, "TENANT_ADMIN", "TENANT", null);
        when(verifier.verify("admin-token")).thenReturn(new VerifiedFirebaseIdentity("fixture-" + adminUser, "admin@example.com", "Admin", true));
    }
    @Test void createReturnsTokenOnceAndSevenDaysServerExpiryListAndDbNeverContainRawToken(CapturedOutput output) throws Exception {
        var customer = fixtures.customer(tenant);
        var response = create("  Maria.Example+Ops@Example.COM  ", List.of(role("CUSTOMER_DISPATCHER", customer), role("DRIVER", null)));
        assertThat(response.statusCode()).isEqualTo(201);
        var created = body(response);
        String raw = (String) created.get("acceptanceToken");
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(created).containsEntry("status", "PENDING").doesNotContainKey("tokenHash");
        try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.prepareStatement(
                "SELECT token_hash, email_normalized, created_at, expires_at, created_by_user_id, row_to_json(i)::text FROM membership_invitations i WHERE id=?")) {
            q.setObject(1, UUID.fromString((String) created.get("id")));
            try (var r = q.executeQuery()) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo(InvitationToken.hash(raw));
                assertThat(r.getString(2)).isEqualTo("maria.example+ops@example.com");
                assertThat(Duration.between(r.getTimestamp(3).toInstant(), r.getTimestamp(4).toInstant())).isEqualTo(Duration.ofDays(7));
                assertThat(r.getObject(5, UUID.class)).isEqualTo(adminUser);
                assertThat(r.getString(6)).doesNotContain(raw);
            }
        }
        var list = request("GET", tenantPath() + "?status=PENDING", "admin-token", null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).doesNotContain(raw, "acceptanceToken", "tokenHash").contains("CUSTOMER_DISPATCHER", customer.toString());
        assertThat(output.getAll()).doesNotContain(raw);
    }
    @Test void normalizationCollidesOnlyExactTrimLowercaseAndReinvitesExpiredPending() throws Exception {
        var first = body(create("USER@EXAMPLE.COM", List.of(role("DRIVER", null))));
        problem(create("  user@example.com  ", List.of(role("DRIVER", null))), 409, "RESOURCE_CONFLICT");
        fixtures.execute("UPDATE membership_invitations SET expires_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(1)), uuid(first, "id"));
        var next = create("user@example.com", List.of(role("DRIVER", null)));
        assertThat(next.statusCode()).isEqualTo(201);
        assertThat(body(next).get("acceptanceToken")).isNotEqualTo(first.get("acceptanceToken"));
        assertThat(adminValue("SELECT status FROM membership_invitations WHERE id=?", uuid(first, "id"))).isEqualTo("EXPIRED");
        for (var email : List.of("user+one@example.com", "user+two@example.com", "first.last@example.com", "firstlast@example.com")) {
            assertThat(create(email, List.of(role("DRIVER", null))).statusCode()).isEqualTo(201);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"TRANSPORT_OPERATIONS", "CUSTOMER_DISPATCHER", "DRIVER", "YARD_SPOTTER"})
    void onlyTenantAdminCanInviteListOrRevoke(String role) throws Exception {
        var user = fixtures.user();
        var member = fixtures.membership(tenant, user, "ACTIVE");
        var customer = role.equals("CUSTOMER_DISPATCHER") ? fixtures.customer(tenant) : null;
        fixtures.role(tenant, member, role, customer == null ? "TENANT" : "CUSTOMER", customer);
        when(verifier.verify("other-token")).thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null, true));
        var existing = body(create("invite@example.com", List.of(role("DRIVER", null))));
        for (var response : List.of(request("POST", tenantPath(), "other-token", jsonCreate("new@example.com", List.of(role("DRIVER", null)))),
                request("GET", tenantPath(), "other-token", null),
                request("POST", tenantPath() + "/" + existing.get("id") + "/revoke", "other-token", null))) {
            problem(response, 403, "CAPABILITY_DENIED");
        }
    }
    @Test void suspendedAdminAndForgedFirebaseClaimsCannotInvite() throws Exception {
        fixtures.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?", adminMembership);
        problem(create("a@example.com", List.of(role("DRIVER", null))), 403, "TENANT_ACCESS_DENIED");
        var token = mock(com.google.firebase.auth.FirebaseToken.class);
        when(token.getUid()).thenReturn("forged-" + UUID.randomUUID());
        when(token.getClaims()).thenReturn(Map.of("role", "TENANT_ADMIN", "tenantId", tenant.toString(), "isAdmin", true, "email_verified", true));
        when(verifier.verify("forged-token")).thenReturn(FirebaseAdminTokenVerifier.identity(token));
        problem(request("POST", tenantPath(), "forged-token", jsonCreate("a@example.com", List.of(role("DRIVER", null)))), 403, "TENANT_ACCESS_DENIED");
        verify(token, never()).getClaims();
    }
    @Test void currentVerifiedEmailWinsOverStoredEmailAndMeReflectsExactMaterializedRoles() throws Exception {
        var customerA = fixtures.customer(tenant);
        var customerB = fixtures.customer(tenant);
        fixtures.execute("UPDATE customers SET status='INACTIVE' WHERE id=?", customerB); // No invented ACTIVE requirement.
        var roles = List.of(role("CUSTOMER_DISPATCHER", customerA), role("CUSTOMER_DISPATCHER", customerB), role("DRIVER", null));
        var invitation = body(create("new@example.com", roles));
        String uid = "changing-email-" + UUID.randomUUID();
        var stored = users.resolve(new VerifiedFirebaseIdentity(uid, "old@example.com", "Snapshot", false));
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity(uid, "  NEW@EXAMPLE.COM ", "Current", true));
        var accepted = accept((String) invitation.get("acceptanceToken"), "invitee-token");
        assertThat(accepted.statusCode()).isEqualTo(200);
        var result = body(accepted);
        assertThat(result).containsEntry("tenantId", tenant.toString()).containsEntry("accepted", true);
        assertThat(adminValue("SELECT status FROM tenant_memberships WHERE id=?", uuid(result, "membershipId"))).isEqualTo("ACTIVE");
        assertThat(number("SELECT count(*) FROM role_assignments WHERE membership_id=?", uuid(result, "membershipId"))).isEqualTo(3);
        assertThat(adminValue("SELECT accepted_by_user_id FROM membership_invitations WHERE id=?", uuid(invitation, "id"))).isEqualTo(stored.userId());
        assertThat(adminValue("SELECT accepted_at FROM membership_invitations WHERE id=?", uuid(invitation, "id"))).isNotNull();
        var me = request("GET", "/api/v1/me", "invitee-token", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(body(me)).containsEntry("email", "old@example.com").containsEntry("displayName", "Snapshot");
        assertThat(JsonPath.<List<?>>read(me.body(), "$.memberships")).hasSize(1);
        assertThat(JsonPath.<List<?>>read(me.body(), "$.memberships[0].roles")).hasSize(3);
        assertThat(me.body()).contains(customerA.toString(), customerB.toString(), "DRIVER");
        var auth = new FirebaseAuthenticationProvider(verifier, users).authenticate(new BearerTokenAuthenticationToken("invitee-token"));
        assertThat(auth.getAuthorities()).isEmpty();
        // Same-user replay ignores later metadata/status changes and never reactivates/replaces roles.
        fixtures.execute("UPDATE tenant_memberships SET status='DISABLED' WHERE id=?", uuid(result, "membershipId"));
        fixtures.execute("DELETE FROM role_assignments WHERE membership_id=? AND role_code='DRIVER'", uuid(result, "membershipId"));
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity(uid, null, null, false));
        assertThat(body(accept((String) invitation.get("acceptanceToken"), "invitee-token"))).isEqualTo(result);
        assertThat(adminValue("SELECT status FROM tenant_memberships WHERE id=?", uuid(result, "membershipId"))).isEqualTo("DISABLED");
        assertThat(number("SELECT count(*) FROM role_assignments WHERE membership_id=?", uuid(result, "membershipId"))).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings = {"mismatch", "unverified", "missing", "expired", "revoked", "other-user", "existing-membership", "unknown"})
    void everyUnacceptableConditionHasSameGenericDetailWithoutChangingState(String condition) throws Exception {
        String uid = "ineligible-" + UUID.randomUUID();
        var stored = users.resolve(new VerifiedFirebaseIdentity(uid, "invited@example.com", null, true));
        String email = condition.equals("mismatch") ? "different@example.com" : condition.equals("missing") ? null : "invited@example.com";
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity(uid, email, null, !condition.equals("unverified")));
        var invitation = body(create("invited@example.com", List.of(role("DRIVER", null))));
        var id = uuid(invitation, "id");
        if (condition.equals("expired")) { fixtures.execute("UPDATE membership_invitations SET expires_at=now() WHERE id=?", id); }
        if (condition.equals("revoked")) { fixtures.execute("UPDATE membership_invitations SET status='REVOKED' WHERE id=?", id); }
        if (condition.equals("other-user")) { fixtures.execute("UPDATE membership_invitations SET status='ACCEPTED', accepted_by_user_id=? WHERE id=?", fixtures.user(), id); }
        if (condition.equals("existing-membership")) { fixtures.membership(tenant, stored.userId(), "SUSPENDED"); }
        String raw = condition.equals("unknown") ? new InvitationToken().generate() : (String) invitation.get("acceptanceToken");
        var response = accept(raw, "invitee-token");
        problem(response, 409, "INVITATION_NOT_ACCEPTABLE");
        assertThat(body(response).get("detail")).isEqualTo("The invitation cannot be accepted.");
        assertThat(response.body()).doesNotContain(raw, InvitationToken.hash(raw), "invited@example.com", "different@example.com", uid, id.toString());
        assertThat(number("SELECT count(*) FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, stored.userId()))
                .isEqualTo(condition.equals("existing-membership") ? 1 : 0);
    }
    @Test void eightConcurrentAcceptancesAndCreationsAreDbArbitrated() throws Exception {
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity("concurrent-" + UUID.randomUUID(), "race@example.com", null, true));
        var invitation = body(create("race@example.com", List.of(role("DRIVER", null), role("YARD_SPOTTER", null))));
        var accepted = concurrent(() -> accept((String) invitation.get("acceptanceToken"), "invitee-token"));
        assertThat(accepted).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));
        var ids = accepted.stream().map(r -> body(r).get("membershipId")).distinct().toList();
        assertThat(ids).hasSize(1);
        UUID membership = UUID.fromString((String) ids.getFirst());
        assertThat(number("SELECT count(*) FROM tenant_memberships WHERE id=?", membership)).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM role_assignments WHERE membership_id=?", membership)).isEqualTo(2);
        assertThat(adminValue("SELECT status FROM membership_invitations WHERE id=?", uuid(invitation, "id"))).isEqualTo("ACCEPTED");
        var creations = concurrent(() -> create("create-race@example.com", List.of(role("DRIVER", null))));
        assertThat(creations.stream().filter(r -> r.statusCode() == 201)).hasSize(1);
        assertThat(creations.stream().filter(r -> r.statusCode() == 409)).hasSize(7);
        assertThat(number("SELECT count(*) FROM membership_invitations WHERE tenant_id=? AND email_normalized='create-race@example.com'", tenant)).isEqualTo(1);
    }
    @Test void failureOnSecondMaterializedRoleRollsBackMembershipRolesAndAcceptance() throws Exception {
        var uid = "rollback-" + UUID.randomUUID();
        var user = users.resolve(new VerifiedFirebaseIdentity(uid, "rollback@example.com", null, true));
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity(uid, "rollback@example.com", null, true));
        var invitation = body(create("rollback@example.com", List.of(role("DRIVER", null), role("YARD_SPOTTER", null))));
        // Test-only migrator trigger forces a DB failure after membership/first role INSERT.
        fixtures.execute("""
                CREATE FUNCTION test_invitation_role_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.role_code='YARD_SPOTTER' THEN RAISE EXCEPTION 'controlled fixture failure'; END IF; RETURN NEW; END $$
                """);
        fixtures.execute("CREATE TRIGGER test_invitation_role_failure BEFORE INSERT ON role_assignments FOR EACH ROW EXECUTE FUNCTION test_invitation_role_failure()");
        try {
            problem(accept((String) invitation.get("acceptanceToken"), "invitee-token"), 409, "INVITATION_NOT_ACCEPTABLE");
            assertThat(adminValue("SELECT status FROM membership_invitations WHERE id=?", uuid(invitation, "id"))).isEqualTo("PENDING");
            assertThat(adminValue("SELECT accepted_at FROM membership_invitations WHERE id=?", uuid(invitation, "id"))).isNull();
            assertThat(number("SELECT count(*) FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, user.userId())).isZero();
            assertThat(number("SELECT count(*) FROM role_assignments WHERE tenant_id=? AND membership_id<>?", tenant, adminMembership)).isZero();
        } finally {
            fixtures.execute("DROP TRIGGER test_invitation_role_failure ON role_assignments");
            fixtures.execute("DROP FUNCTION test_invitation_role_failure()");
        }
    }
    @Test void revokeIsIdempotentPreservesRolesAndCannotRevokeAccepted() throws Exception {
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity("revocation-" + UUID.randomUUID(), "revoke@example.com", null, true));
        var invitation = body(create("revoke@example.com", List.of(role("DRIVER", null))));
        var path = tenantPath() + "/" + invitation.get("id") + "/revoke";
        assertThat(body(request("POST", path, "admin-token", null))).containsEntry("status", "REVOKED");
        assertThat(body(request("POST", path, "admin-token", null))).containsEntry("status", "REVOKED");
        assertThat(number("SELECT count(*) FROM invitation_role_assignments WHERE invitation_id=?", uuid(invitation, "id"))).isEqualTo(1);
        problem(accept((String) invitation.get("acceptanceToken"), "invitee-token"), 409, "INVITATION_NOT_ACCEPTABLE");
        var next = body(create("revoke@example.com", List.of(role("DRIVER", null))));
        assertThat(accept((String) next.get("acceptanceToken"), "invitee-token").statusCode()).isEqualTo(200);
        problem(request("POST", tenantPath() + "/" + next.get("id") + "/revoke", "admin-token", null), 409, "RESOURCE_CONFLICT");
        var stale = body(create("stale@example.com", List.of(role("DRIVER", null))));
        fixtures.execute("UPDATE membership_invitations SET expires_at=now() WHERE id=?", uuid(stale, "id"));
        assertThat(body(request("POST", tenantPath() + "/" + stale.get("id") + "/revoke", "admin-token", null))).containsEntry("status", "EXPIRED");
    }
    @Test void strictValidationSafeCustomerFkErrorsAndTokenPrivacy(CapturedOutput output) throws Exception {
        when(verifier.verify("invitee-token")).thenReturn(new VerifiedFirebaseIdentity("privacy-" + UUID.randomUUID(), "test@example.com", null, true));
        var raw = "synthetic-secret-invitation-token";
        problem(accept(raw, "invitee-token"), 400, "VALIDATION_FAILED");
        // Valid canonical representation of a 32-byte synthetic secret exercises DB lookup.
        raw = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf("synthetic-secret-invitation-token".getBytes(java.nio.charset.StandardCharsets.UTF_8), 32));
        var invalid = accept(raw, "invitee-token");
        problem(invalid, 409, "INVITATION_NOT_ACCEPTABLE");
        assertThat(invalid.body()).doesNotContain(raw);
        problem(request("POST", "/api/v1/invitations/accept", "invitee-token", mapper.writeValueAsString(Map.of("token", raw, "tenantId", tenant.toString()))), 400, "VALIDATION_FAILED");
        for (var query : List.of("?unknown=true", "?status=UNKNOWN", "?status=PENDING&status=REVOKED")) {
            problem(request("GET", tenantPath() + query, "admin-token", null), 400, "VALIDATION_FAILED");
        }
        problem(create("bad-customer@example.com", List.of(role("CUSTOMER_DISPATCHER", UUID.randomUUID()))), 409, "RESOURCE_CONFLICT");
        var foreign = fixtures.customer(fixtures.tenant("Foreign"));
        var response = create("cross@example.com", List.of(role("CUSTOMER_DISPATCHER", foreign)));
        problem(response, 409, "RESOURCE_CONFLICT");
        assertThat(response.body()).doesNotContain(foreign.toString(), "constraint", "SQL", "cross@example.com");
        assertThat(number("SELECT count(*) FROM membership_invitations WHERE tenant_id=?", tenant)).isZero();
        assertThat(output.getAll()).doesNotContain(raw, "synthetic-secret-invitation-token");
        assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
        problem(request("POST", "/api/v1/invitations/accept", null, "{}"), 401, "AUTHENTICATION_REQUIRED");
        assertThat(request("GET", "/api/v1/me", null, null).statusCode()).isEqualTo(401);
    }
    @Test void adminTenantIsolationAndDeterministicOptionalStatusFilter() throws Exception {
        var first = body(create("first@example.com", List.of(role("DRIVER", null))));
        request("POST", tenantPath() + "/" + first.get("id") + "/revoke", "admin-token", null);
        var second = body(create("second@example.com", List.of(role("DRIVER", null))));
        var list = request("GET", tenantPath(), "admin-token", null);
        assertThat(JsonPath.<List<String>>read(list.body(), "$.items[*].id")).containsExactly((String) second.get("id"), (String) first.get("id"));
        assertThat(JsonPath.<List<?>>read(request("GET", tenantPath() + "?status=REVOKED", "admin-token", null).body(), "$.items")).hasSize(1);
        var foreignTenant = fixtures.tenant("Foreign");
        problem(request("GET", "/api/v1/tenants/" + foreignTenant + "/invitations", "admin-token", null), 403, "TENANT_ACCESS_DENIED");
    }
    private List<HttpResponse<String>> concurrent(CheckedRequest operation) throws Exception {
        var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for (int i = 0; i < 8; i++) { futures.add(pool.submit(() -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); return operation.run(); })); }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var responses = new ArrayList<HttpResponse<String>>();
            for (var future : futures) { responses.add(future.get(30, TimeUnit.SECONDS)); }
            return responses;
        }
    }
    private Map<String, Object> role(String role, UUID customer) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("roleCode", role); result.put("scopeType", customer == null ? "TENANT" : "CUSTOMER"); result.put("customerId", customer);
        return result;
    }
    private String tenantPath() { return "/api/v1/tenants/" + tenant + "/invitations"; }
    private String jsonCreate(String email, List<Map<String, Object>> roles) { return mapper.writeValueAsString(Map.of("email", email, "displayName", "Maria", "roles", roles)); }
    private HttpResponse<String> create(String email, List<Map<String, Object>> roles) throws Exception { return request("POST", tenantPath(), "admin-token", jsonCreate(email, roles)); }
    private HttpResponse<String> accept(String token, String bearer) throws Exception { return request("POST", "/api/v1/invitations/accept", bearer, mapper.writeValueAsString(Map.of("token", token))); }
    private HttpResponse<String> request(String method, String path, String bearer, String json) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30))
                    .header("X-Request-Id", "invitation-test").header("Content-Type", "application/json")
                    .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (bearer != null) { builder.header("Authorization", "Bearer " + bearer); }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private Map<String, Object> body(HttpResponse<String> response) { return JsonPath.read(response.body(), "$"); }
    private UUID uuid(Map<String, Object> response, String field) { return UUID.fromString((String) response.get(field)); }
    private void problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as("safe response %s", response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("invitation-test");
        assertThat(body(response)).containsEntry("code", code).containsKey("traceId");
    }
    private Object adminValue(String sql, Object... parameters) throws Exception {
        try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) { q.setObject(i + 1, parameters[i]); }
            try (var r = q.executeQuery()) { assertThat(r.next()).isTrue(); return r.getObject(1); }
        }
    }
    private int number(String sql, Object... parameters) throws Exception { return ((Number) adminValue(sql, parameters)).intValue(); }
    @FunctionalInterface interface CheckedRequest { HttpResponse<String> run() throws Exception; }
}
