package com.trailersaas.identity.internal;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.TenantContext;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1"})
class InvitationPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired EntityManager manager;
    @Autowired TenantContext tenants;
    @Autowired InvitationOnboardingContext onboarding;
    @Autowired PlatformTransactionManager transactionManager;
    IdentityFixtures fixtures;
    UUID a, b, user, invitationA, invitationB;
    String rawA, rawB, hashA;

    @BeforeEach void seed() throws Exception {
        fixtures = new IdentityFixtures(flyway);
        a = fixtures.tenant("A"); b = fixtures.tenant("B"); user = fixtures.user();
        rawA = new InvitationToken().generate(); rawB = new InvitationToken().generate(); hashA = InvitationToken.hash(rawA);
        invitationA = invitation(a, "a@example.com", hashA, "PENDING");
        invitationB = invitation(b, "b@example.com", InvitationToken.hash(rawB), "PENDING");
        role(a, invitationA, "DRIVER", "TENANT", null);
        role(b, invitationB, "DRIVER", "TENANT", null);
    }
    @Test void v6CreatesOnlyAcceptedInvitationTablesWithNoRawTokenColumnAndPreservesChecksums() throws Exception {
        flyway.validate();
        assertThat(flyway.info().applied()).extracting(i -> i.getVersion().getVersion()).containsExactly("1","2","3","4","5","6","7","8","9","10");
        var names = tx(() -> manager.createNativeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'", String.class).getResultList());
        assertThat(names).containsExactlyInAnyOrder("flyway_schema_history", "users", "tenants", "tenant_memberships", "role_assignments",
                "customers", "physical_sites", "customer_sites", "operational_locations", "membership_invitations", "invitation_role_assignments", "drivers", "tractors", "trailers", "trailer_qr_identities", "tenant_settings", "orders", "order_cargo_lines", "order_stops");
        assertThat(tx(() -> manager.createNativeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='membership_invitations' ORDER BY ordinal_position", String.class).getResultList()))
                .containsExactly("id","tenant_id","email","email_normalized","display_name","token_hash","status","expires_at","accepted_by_user_id","created_by_user_id","created_at","accepted_at");
        assertThat(tx(() -> manager.createNativeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='invitation_role_assignments' ORDER BY ordinal_position", String.class).getResultList()))
                .containsExactly("id","tenant_id","invitation_id","role_code","scope_type","customer_id");
        try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.prepareStatement("SELECT row_to_json(i)::text FROM membership_invitations i WHERE id=?")) {
            q.setObject(1, invitationA); try (var r = q.executeQuery()) { r.next(); assertThat(r.getString(1)).contains(hashA).doesNotContain(rawA); }
        }
        assertThat(tx(() -> number("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('membership_invitations'::regclass,'invitation_role_assignments'::regclass) AND confdeltype='c'"))).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "ACCEPTED", "EXPIRED", "REVOKED"})
    void acceptedStatusCatalog(String status) throws Exception { invitation(a, UUID.randomUUID() + "@example.com", UUID.randomUUID().toString(), status); }
    @ParameterizedTest @ValueSource(strings = {"UNKNOWN", "INVITED", "pending"})
    void closedStatusRejectsUnknown(String status) { rejects("23514", () -> invitation(a, "closed@example.com", UUID.randomUUID().toString(), status)); }
    @Test void tokenHashAndPendingEmailUniquenessAllowHistoricalRows() throws Exception {
        rejects("23505", () -> invitation(b, "other@example.com", hashA, "PENDING"));
        rejects("23505", () -> invitation(a, "a@example.com", UUID.randomUUID().toString(), "PENDING"));
        for (var state : List.of("EXPIRED", "REVOKED", "ACCEPTED")) { invitation(a, "a@example.com", UUID.randomUUID().toString(), state); }
        invitation(b, "a@example.com", UUID.randomUUID().toString(), "PENDING");
        rejects("23514", () -> invitation(a, "", UUID.randomUUID().toString(), "PENDING"));
        var index = tx(() -> manager.createNativeQuery("SELECT indexdef FROM pg_indexes WHERE indexname='membership_invitations_pending_email_unique'", String.class).getSingleResult());
        assertThat(index.toString()).contains("status", "PENDING").doesNotContain("expires_at", "now()");
    }
    @ParameterizedTest @ValueSource(strings = {"TENANT_ADMIN", "TRANSPORT_OPERATIONS", "YARD_SPOTTER", "DRIVER"})
    void exactTenantRoleScopeAndUniqueness(String role) throws Exception {
        var invitation = invitation(a, UUID.randomUUID() + "@example.com", UUID.randomUUID().toString(), "PENDING");
        role(a, invitation, role, "TENANT", null);
        rejects("23505", () -> role(a, invitation, role, "TENANT", null));
        rejects("23514", () -> role(a, invitation, role, "TENANT", UUID.randomUUID()));
        rejects("23514", () -> role(a, invitation, role, "CUSTOMER", UUID.randomUUID()));
    }
    @Test void customerRolesAreTenantSafeSupportDifferentCustomersAndNeverDuplicate() throws Exception {
        var customerA = fixtures.customer(a); var customerA2 = fixtures.customer(a); var customerB = fixtures.customer(b);
        role(a, invitationA, "CUSTOMER_DISPATCHER", "CUSTOMER", customerA);
        role(a, invitationA, "CUSTOMER_DISPATCHER", "CUSTOMER", customerA2);
        rejects("23505", () -> role(a, invitationA, "CUSTOMER_DISPATCHER", "CUSTOMER", customerA));
        rejects("23514", () -> role(a, invitationA, "CUSTOMER_DISPATCHER", "CUSTOMER", null));
        rejects("23514", () -> role(a, invitationA, "CUSTOMER_DISPATCHER", "TENANT", null));
        rejects("23503", () -> role(a, invitationA, "CUSTOMER_DISPATCHER", "CUSTOMER", UUID.randomUUID()));
        rejects("23503", () -> role(a, invitationA, "CUSTOMER_DISPATCHER", "CUSTOMER", customerB));
        rejects("23503", () -> role(b, invitationA, "TENANT_ADMIN", "TENANT", null));
        rejects("23514", () -> role(a, invitationA, "UNKNOWN", "TENANT", null));
        rejects("23514", () -> role(a, invitationA, "DRIVER", "LOCATION", null));
        rejects("23503", () -> fixtures.execute("DELETE FROM membership_invitations WHERE id=?", invitationA));
    }
    @Test void tenantHashBootstrapAndNoContextIsolationWithoutRepositoryPredicates() {
        tx(() -> { noRows(); return null; });
        tx(() -> { tenants.establishTenant(a); assertThat(ids("SELECT id FROM membership_invitations")).containsExactly(invitationA);
            assertThat(ids("SELECT invitation_id FROM invitation_role_assignments")).containsExactly(invitationA); return null; });
        tx(() -> { onboarding.establish(hashA); assertThat(ids("SELECT id FROM membership_invitations FOR UPDATE")).containsExactly(invitationA);
            assertThat(ids("SELECT invitation_id FROM invitation_role_assignments")).containsExactly(invitationA);
            assertThat(ids("SELECT id FROM tenants")).isEmpty();
            assertThat(ids("SELECT id FROM tenant_memberships")).isEmpty(); return null; });
        tx(() -> { tenants.establishBootstrapUser(new AuthenticatedUserPrincipal(user, "fixture", null, null)); noRows(); return null; });
        tx(() -> { tenants.establishBootstrapUser(new AuthenticatedUserPrincipal(user, "fixture", null, null));
            assertThatIllegalStateException().isThrownBy(() -> onboarding.establish(hashA)); return null; });
        tx(() -> { onboarding.establish(hashA);
            assertThatIllegalStateException().isThrownBy(() -> onboarding.establish(InvitationToken.hash(rawB)));
            // The acceptance service may pin only the Tenant derived from its locked invitation.
            tenants.establishTenant(a); assertThat(ids("SELECT id FROM membership_invitations")).containsExactly(invitationA); return null; });
        assertThatIllegalStateException().isThrownBy(() -> onboarding.establish(hashA));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void samePhysicalPooledConnectionClearsHashAndDerivedTenantAfterCommitAndRollback(boolean rollback) {
        int pid = tx(() -> number("SELECT pg_backend_pid()"));
        var template = new TransactionTemplate(transactionManager);
        template.execute(status -> {
            onboarding.establish(hashA); tenants.establishTenant(a);
            assertThat(number("SELECT pg_backend_pid()")).isEqualTo(pid);
            if (rollback) { status.setRollbackOnly(); }
            return null;
        });
        tx(() -> {
            assertThat(number("SELECT pg_backend_pid()")).isEqualTo(pid);
            assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_invitation_hash',true),'')").getSingleResult()).isNull();
            assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_tenant_id',true),'')").getSingleResult()).isNull();
            noRows(); return null;
        });
    }
    @Test void hashOnlyCanReadAndLockExactlyItsInvitationAndIntendedRoles() {
        tx(() -> {
            onboarding.establish(hashA);
            assertThat(ids("SELECT id FROM membership_invitations")).containsExactly(invitationA);
            assertThat(ids("SELECT id FROM membership_invitations FOR UPDATE")).containsExactly(invitationA);
            assertThat(ids("SELECT invitation_id FROM invitation_role_assignments")).containsExactly(invitationA);
            return null;
        });
    }
    @ParameterizedTest @ValueSource(strings = {"status='REVOKED'", "email='changed@example.com'", "display_name='changed'"})
    void hashOnlyCannotUpdateEvenItsLockedInvitation(String assignment) {
        assertThatThrownBy(() -> tx(() -> {
            onboarding.establish(hashA);
            assertThat(ids("SELECT id FROM membership_invitations FOR UPDATE")).containsExactly(invitationA);
            updateInvitation(assignment);
            return null;
        })).rootCause().isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
        tx(() -> {
            assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_invitation_hash',true),'')").getSingleResult()).isNull();
            assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_tenant_id',true),'')").getSingleResult()).isNull();
            tenants.establishTenant(a); assertThat(invitationStatus()).isEqualTo("PENDING"); return null;
        });
    }
    @Test void hashAndMatchingLockedInvitationTenantCanUpdate() {
        tx(() -> {
            onboarding.establish(hashA);
            UUID derivedTenant = (UUID) manager.createNativeQuery("SELECT tenant_id FROM membership_invitations WHERE id=:id FOR UPDATE", UUID.class)
                    .setParameter("id", invitationA).getSingleResult();
            tenants.establishTenant(derivedTenant);
            assertThat(updateInvitation("status='REVOKED'")).isEqualTo(1);
            assertThat(invitationStatus()).isEqualTo("REVOKED");
            return null;
        });
    }
    @Test void hashAndWrongTenantCannotUpdateExactInvitation() {
        tx(() -> {
            onboarding.establish(hashA);
            assertThat(ids("SELECT id FROM membership_invitations FOR UPDATE")).containsExactly(invitationA);
            tenants.establishTenant(b);
            // USING now hides the target; no row can be modified under the wrong Tenant.
            assertThat(updateInvitation("status='REVOKED'")).isZero();
            return null;
        });
        tx(() -> { tenants.establishTenant(a); assertThat(invitationStatus()).isEqualTo("PENDING"); return null; });
    }
    @ParameterizedTest @ValueSource(strings = {"membership_invitations", "invitation_role_assignments", "tenant_memberships", "role_assignments"})
    void runtimeHasExactApprovedGrantsAndNoAdministrativePrivileges(String table) {
        tx(() -> {
            var values = (Object[]) manager.createNativeQuery("""
                    SELECT has_table_privilege(current_user,:table,'SELECT'), has_table_privilege(current_user,:table,'INSERT'),
                        has_table_privilege(current_user,:table,'UPDATE'), has_table_privilege(current_user,:table,'DELETE'),
                        pg_get_userbyid(c.relowner)=current_user, r.rolsuper, r.rolbypassrls,
                        has_schema_privilege(current_user,'public','CREATE'), c.relrowsecurity,
                        (SELECT count(*) FROM aclexplode(c.relacl) acl WHERE acl.grantee=0)
                    FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid=to_regclass(:table)
                    """).setParameter("table", table).getSingleResult();
            assertThat(values[0]).isEqualTo(true); assertThat(values[1]).isEqualTo(true);
            assertThat(values[2]).isEqualTo(table.equals("membership_invitations"));
            for (int i = 3; i <= 7; i++) { assertThat(values[i]).isEqualTo(false); }
            assertThat(values[8]).isEqualTo(true); assertThat(((Number) values[9]).intValue()).isZero();
            return null;
        });
    }
    private UUID invitation(UUID tenant, String email, String hash, String status) throws SQLException {
        var id = UUID.randomUUID();
        fixtures.execute("""
                INSERT INTO membership_invitations
                (id,tenant_id,email,email_normalized,token_hash,status,expires_at,created_by_user_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,now())
                """, id, tenant, email, email, hash, status, Timestamp.from(Instant.now().plusSeconds(604800)), user);
        return id;
    }
    private void role(UUID tenant, UUID invitation, String role, String scope, UUID customer) throws SQLException {
        fixtures.execute("INSERT INTO invitation_role_assignments VALUES(?,?,?,?,?,?)", UUID.randomUUID(), tenant, invitation, role, scope, customer);
    }
    private void noRows() {
        assertThat(ids("SELECT id FROM membership_invitations")).isEmpty();
        assertThat(ids("SELECT id FROM invitation_role_assignments")).isEmpty();
    }
    private int updateInvitation(String assignment) {
        return manager.createNativeQuery("UPDATE membership_invitations SET " + assignment + " WHERE id=:id")
                .setParameter("id", invitationA).executeUpdate();
    }
    private Object invitationStatus() {
        return manager.createNativeQuery("SELECT status FROM membership_invitations WHERE id=:id")
                .setParameter("id", invitationA).getSingleResult();
    }
    private List<UUID> ids(String sql) { return manager.createNativeQuery(sql, UUID.class).getResultList(); }
    private int number(String sql) { return ((Number) manager.createNativeQuery(sql).getSingleResult()).intValue(); }
    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactionManager).execute(status -> work.get()); }
    private void rejects(String state, SqlAction action) {
        assertThatThrownBy(action::run).isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo(state));
    }
    @FunctionalInterface interface SqlAction { void run() throws SQLException; }
}
