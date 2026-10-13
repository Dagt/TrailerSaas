package com.trailersaas.identity.internal;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;

import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantAuthorization;
import com.trailersaas.identity.TenantContext;
import com.trailersaas.identity.TenantTransactionExecutor;
import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1"})
@Import(TenantRlsPostgresTests.Configuration.class)
class TenantRlsPostgresTests extends PostgresIntegrationTest {
    @Autowired EntityManager manager;
    @Autowired DataSource database;
    @Autowired Flyway flyway;
    @Autowired TenantContext context;
    @Autowired TenantTransactionExecutor executor;
    @Autowired TenantAuthorization authorization;
    @Autowired MembershipDirectory directory;
    @Autowired OuterCommand command;
    @Autowired PlatformTransactionManager transactionManager;
    IdentityFixtures fixtures;
    UUID a, b, userA, userB, membershipAA, membershipAB, membershipBA, membershipBB;

    @BeforeEach
    void seedUsingMigratorOnly() throws Exception {
        fixtures = new IdentityFixtures(flyway);
        a = fixtures.tenant("Tenant A");
        b = fixtures.tenant("Tenant B");
        userA = fixtures.user();
        userB = fixtures.user();
        membershipAA = fixtures.membership(a, userA, "ACTIVE");
        membershipAB = fixtures.membership(b, userA, "SUSPENDED");
        membershipBA = fixtures.membership(a, userB, "DISABLED");
        membershipBB = fixtures.membership(b, userB, "ACTIVE");
        fixtures.role(a, membershipAA, "TENANT_ADMIN", "TENANT", null);
        fixtures.role(b, membershipAB, "DRIVER", "TENANT", null);
        fixtures.role(a, membershipBA, "YARD_SPOTTER", "TENANT", null);
        fixtures.role(b, membershipBB, "DRIVER", "TENANT", null);
    }

    @Test
    void noContextReturnsNoTenantRowsAndUsersRemainGlobal() {
        tx(() -> {
            assertNoContextOrRows();
            assertThat(ids("SELECT id FROM users")).contains(userA, userB);
            return null;
        });
    }

    @Test
    void missingTenantPredicateIsStillIsolatedByPostgres() {
        executor.execute(a, () -> {
            assertTenantRows(a, membershipAA, membershipBA);
            return null;
        });
        executor.execute(b, () -> {
            assertTenantRows(b, membershipAB, membershipBB);
            return null;
        });
    }

    @Test
    void bootstrapLimitsUnscopedQueriesToOwnMembershipsRolesAndTenantsAcrossStatuses() throws Exception {
        var foreignTenant = fixtures.tenant("Only User B");
        var foreignMembership = fixtures.membership(foreignTenant, userB, "ACTIVE");
        fixtures.role(foreignTenant, foreignMembership, "DRIVER", "TENANT", null);
        tx(() -> {
            context.establishBootstrapUser(principal(userA));
            assertThat(ids("SELECT id FROM tenant_memberships")).containsExactlyInAnyOrder(membershipAA, membershipAB);
            assertThat(ids("SELECT membership_id FROM role_assignments")).containsExactlyInAnyOrder(membershipAA, membershipAB);
            assertThat(ids("SELECT id FROM tenants")).containsExactlyInAnyOrder(a, b);
            return null;
        });
        tx(() -> {
            context.establishBootstrapUser(principal(userB));
            assertThat(ids("SELECT id FROM tenant_memberships"))
                    .containsExactlyInAnyOrder(membershipBA, membershipBB, foreignMembership);
            assertThat(ids("SELECT membership_id FROM role_assignments"))
                    .containsExactlyInAnyOrder(membershipBA, membershipBB, foreignMembership);
            assertThat(ids("SELECT id FROM tenants")).containsExactlyInAnyOrder(a, b, foreignTenant);
            return null;
        });
    }

    @Test
    void bootstrapWithoutMembershipCannotEnumerateAnyTenantOrRole() throws Exception {
        var user = fixtures.user();
        assertThat(directory.forUser(principal(user))).isEmpty();
        tx(() -> {
            context.establishBootstrapUser(principal(user));
            assertNoRows();
            return null;
        });
    }

    @Test
    void directoryPreservesAllStatusesButDoesNotAuthorizeSuspendedOrDisabledMemberships() {
        assertThat(directory.forUser(principal(userA))).extracting(MembershipDirectory.MembershipSummary::status)
                .containsExactlyInAnyOrder(MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED);
        assertThat(directory.forUser(principal(userB))).extracting(MembershipDirectory.MembershipSummary::status)
                .containsExactlyInAnyOrder(MembershipStatus.ACTIVE, MembershipStatus.DISABLED);
        TenantAuthorizationTests.denied(() -> authorization.requireTenantCapability(principal(userA), b,
                Capability.TRIP_VIEW_ASSIGNED), AuthorizationDeniedException.Reason.TENANT_ACCESS_DENIED);
    }

    @Test
    void tenantSettingIsNotAuthorization() {
        TenantAuthorizationTests.denied(() -> executor.execute(a, () -> {
            assertTenantRows(a, membershipAA, membershipBA);
            authorization.requireTenantCapability(principal(userB), a, Capability.USER_INVITE);
            return null;
        }), AuthorizationDeniedException.Reason.TENANT_ACCESS_DENIED);
    }

    @Test
    void contextRequiresTransactionAndCannotSwitchTenantOrMode() {
        assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(a));
        executor.execute(a, () -> {
            context.establishTenant(a);
            assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(b));
            assertThatIllegalStateException().isThrownBy(() -> context.establishBootstrapUser(principal(userA)));
            assertTenantRows(a, membershipAA, membershipBA);
            return null;
        });
        tx(() -> {
            context.establishBootstrapUser(principal(userA));
            context.establishBootstrapUser(principal(userA));
            assertThatIllegalStateException().isThrownBy(() -> context.establishBootstrapUser(principal(userB)));
            assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(a));
            return null;
        });
    }

    @Test
    void malformedMixedModesDenyRowsEvenWhenSetDirectlyInSql() {
        tx(() -> {
            setBothDirectly(a, userA);
            assertNoRows();
            assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(a));
            return null;
        });
    }

    @Test
    void authorizationJoinsOuterDomainTransactionAndKeepsContextForLaterRepositoryCalls() {
        var result = command.execute(principal(userA), a);
        assertThat(result.beforePid()).isEqualTo(result.afterPid());
        assertThat(result.visibleTenants()).containsExactly(a);
        assertThat(result.visibleMemberships()).containsExactlyInAnyOrder(membershipAA, membershipBA);
        tx(() -> { assertNoContextOrRows(); return null; });
    }

    @Test
    void backgroundExecutorUsesOneTenantPerTransactionAndDoesNotPermitNestedSwitching() {
        int pid = executor.execute(a, () -> {
            assertTenantRows(a, membershipAA, membershipBA);
            assertThatIllegalStateException().isThrownBy(() -> executor.execute(b, () -> null));
            assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(b));
            return pid();
        });
        tx(() -> {
            assertThat(pid()).isEqualTo(pid);
            assertNoContextOrRows();
            return null;
        });
        executor.execute(b, () -> {
            assertThat(pid()).isEqualTo(pid);
            assertTenantRows(b, membershipAB, membershipBB);
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void samePhysicalPooledConnectionDoesNotLeakTenantAfterCommitOrRollback(boolean rollback) {
        var firstPid = new AtomicInteger();
        tx(() -> {
            context.establishTenant(a);
            firstPid.set(pid());
            assertTenantRows(a, membershipAA, membershipBA);
            if (rollback) { throw new SyntheticRollback(); }
            return null;
        }, rollback);
        tx(() -> {
            assertThat(pid()).isEqualTo(firstPid.get());
            assertNoContextOrRows();
            return null;
        });
        executor.execute(b, () -> {
            assertThat(pid()).isEqualTo(firstPid.get());
            assertTenantRows(b, membershipAB, membershipBB);
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void samePhysicalPooledConnectionDoesNotLeakBootstrapAfterCommitOrRollback(boolean rollback) {
        var firstPid = new AtomicInteger();
        tx(() -> {
            context.establishBootstrapUser(principal(userA));
            firstPid.set(pid());
            assertThat(ids("SELECT id FROM tenant_memberships")).containsExactlyInAnyOrder(membershipAA, membershipAB);
            if (rollback) { throw new SyntheticRollback(); }
            return null;
        }, rollback);
        tx(() -> {
            assertThat(pid()).isEqualTo(firstPid.get());
            assertNoContextOrRows();
            return null;
        });
        tx(() -> {
            context.establishBootstrapUser(principal(userB));
            assertThat(pid()).isEqualTo(firstPid.get());
            assertThat(ids("SELECT id FROM tenant_memberships")).containsExactlyInAnyOrder(membershipBA, membershipBB);
            assertThat(ids("SELECT membership_id FROM role_assignments")).containsExactlyInAnyOrder(membershipBA, membershipBB);
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant_memberships", "role_assignments"})
    void temporaryWritesProveWithCheckAndBootstrapSelectOnlyWithoutChangingProductionGrants(String table)
            throws Exception {
        fixtures.execute("GRANT UPDATE, DELETE ON " + table + " TO \"" + RUNTIME_USERNAME + "\"");
        try {
            var newUser = fixtures.user();
            executor.execute(a, () -> {
                assertThat(write("UPDATE " + table + " SET tenant_id=tenant_id")).isEqualTo(2);
                return null;
            });
            if (table.equals("tenant_memberships")) {
                executor.execute(a, () -> {
                    assertThat(write("INSERT INTO tenant_memberships VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                            UUID.randomUUID(), a, newUser)).isEqualTo(1);
                    return null;
                });
                deniedWrite(() -> executor.execute(a, () -> write(
                        "INSERT INTO tenant_memberships VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                        UUID.randomUUID(), b, newUser)));
                deniedWrite(() -> executor.execute(a, () -> write(
                        "UPDATE tenant_memberships SET tenant_id=? WHERE user_id=?", b, newUser)));
                deniedWrite(() -> tx(() -> {
                    context.establishBootstrapUser(principal(newUser));
                    return write("INSERT INTO tenant_memberships VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                            UUID.randomUUID(), b, newUser);
                }));
            } else {
                executor.execute(a, () -> {
                    assertThat(write("INSERT INTO role_assignments VALUES (?, ?, ?, 'DRIVER', 'TENANT', NULL, now())",
                            UUID.randomUUID(), a, membershipAA)).isEqualTo(1);
                    return null;
                });
                deniedWrite(() -> executor.execute(a, () -> write(
                        "INSERT INTO role_assignments VALUES (?, ?, ?, 'TRANSPORT_OPERATIONS', 'TENANT', NULL, now())",
                        UUID.randomUUID(), b, membershipBB)));
                deniedWrite(() -> executor.execute(a, () -> write(
                        "UPDATE role_assignments SET tenant_id=? WHERE membership_id=?", b, membershipAA)));
                deniedWrite(() -> tx(() -> {
                    context.establishBootstrapUser(principal(userA));
                    return write("INSERT INTO role_assignments VALUES (?, ?, ?, 'YARD_SPOTTER', 'TENANT', NULL, now())",
                            UUID.randomUUID(), a, membershipAA);
                }));
            }
            tx(() -> {
                context.establishBootstrapUser(principal(userA));
                assertThat(write("UPDATE " + table + " SET tenant_id=tenant_id")).isZero();
                assertThat(write("DELETE FROM " + table)).isZero();
                return null;
            });
        } finally {
            fixtures.execute("REVOKE UPDATE, DELETE ON " + table + " FROM \"" + RUNTIME_USERNAME + "\"");
        }
        tx(() -> {
            assertThat(manager.createNativeQuery("SELECT has_table_privilege(current_user, :table, 'INSERT')",
                    Boolean.class).setParameter("table", table).getSingleResult()).isEqualTo(true);
            assertThat(manager.createNativeQuery("SELECT has_table_privilege(current_user, :table, 'UPDATE,DELETE')",
                    Boolean.class).setParameter("table", table).getSingleResult()).isEqualTo(false);
            return null;
        });
    }

    @Test
    void policiesTargetOnlyRuntimeWithSelectOnlyBootstrapAndNoForce() {
        tx(() -> {
            assertThat(number("SELECT count(*) FROM pg_policies WHERE schemaname='public'")).isEqualTo(46);
            assertThat(number("SELECT count(*) FROM pg_policies WHERE schemaname='public' AND cmd='ALL' AND with_check IS NOT NULL"))
                    .isEqualTo(38);
            assertThat(number("SELECT count(*) FROM pg_policies WHERE schemaname='public' AND cmd='SELECT' AND with_check IS NULL"))
                    .isEqualTo(7);
            assertThat(number("SELECT count(*) FROM pg_policies WHERE schemaname='public' AND roles::text='{"
                    + RUNTIME_USERNAME + "}'")).isEqualTo(46);
            assertThat(number("SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relrowsecurity"))
                    .isEqualTo(39);
            assertThat(number("SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relforcerowsecurity"))
                    .isZero();
            assertThat(number("SELECT count(*) FROM pg_tables WHERE schemaname='public'")).isEqualTo(41);
            return null;
        });
    }

    private void assertTenantRows(UUID tenant, UUID... memberships) {
        assertThat(ids("SELECT id FROM tenants")).containsExactly(tenant);
        assertThat(ids("SELECT id FROM tenant_memberships")).containsExactlyInAnyOrder(memberships);
        assertThat(ids("SELECT DISTINCT tenant_id FROM role_assignments")).containsExactly(tenant);
        assertThat(ids("SELECT membership_id FROM role_assignments")).containsExactlyInAnyOrder(memberships);
        assertThat(manager.createNativeQuery("SELECT current_user", String.class).getSingleResult()).isEqualTo(RUNTIME_USERNAME);
    }

    private void assertNoContextOrRows() {
        assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_tenant_id', true), '')")
                .getSingleResult()).isNull();
        assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_user_id', true), '')")
                .getSingleResult()).isNull();
        assertNoRows();
    }

    private void assertNoRows() {
        for (var table : List.of("tenants", "tenant_memberships", "role_assignments")) {
            assertThat(ids("SELECT id FROM " + table)).isEmpty();
        }
    }

    private List<UUID> ids(String sql) { return manager.createNativeQuery(sql, UUID.class).getResultList(); }
    private int number(String sql) { return ((Number) manager.createNativeQuery(sql).getSingleResult()).intValue(); }
    private int pid() { return number("SELECT pg_backend_pid()"); }

    private int write(String sql, Object... values) {
        return manager.unwrap(Session.class).doReturningWork(connection -> {
            try (var query = connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) { query.setObject(i + 1, values[i]); }
                return query.executeUpdate();
            }
        });
    }

    private void setBothDirectly(UUID tenant, UUID user) {
        manager.unwrap(Session.class).doWork(connection -> {
            try (var query = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, true), set_config('app.current_user_id', ?, true)")) {
                query.setString(1, tenant.toString());
                query.setString(2, user.toString());
                query.executeQuery().close();
            }
        });
    }

    private <T> T tx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private void tx(Supplier<?> work, boolean rollback) {
        if (rollback) { assertThatThrownBy(() -> tx(work)).isInstanceOf(SyntheticRollback.class); }
        else { tx(work); }
    }

    private void deniedWrite(Supplier<?> work) {
        assertThatThrownBy(work::get).satisfies(error -> {
            Throwable cause = error;
            while (!(cause instanceof SQLException) && cause.getCause() != null) { cause = cause.getCause(); }
            assertThat(cause).isInstanceOfSatisfying(SQLException.class,
                    sql -> assertThat(sql.getSQLState()).isEqualTo("42501"));
        });
    }

    private AuthenticatedUserPrincipal principal(UUID id) {
        return new AuthenticatedUserPrincipal(id, "fixture-" + id, null, null);
    }

    static class SyntheticRollback extends RuntimeException {}

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean OuterCommand outerCommand(TenantAuthorization authorization, EntityManager manager) {
            return new OuterCommand(authorization, manager);
        }
    }

    static class OuterCommand {
        private final TenantAuthorization authorization;
        private final EntityManager manager;
        OuterCommand(TenantAuthorization authorization, EntityManager manager) {
            this.authorization = authorization;
            this.manager = manager;
        }

        @Transactional
        public Result execute(AuthenticatedUserPrincipal principal, UUID tenant) {
            int before = ((Number) manager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue();
            authorization.requireTenantCapability(principal, tenant, Capability.USER_INVITE);
            var tenants = manager.createNativeQuery("SELECT id FROM tenants", UUID.class).getResultList();
            var memberships = manager.createNativeQuery("SELECT id FROM tenant_memberships", UUID.class).getResultList();
            int after = ((Number) manager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue();
            return new Result(before, after, tenants, memberships);
        }
    }

    record Result(int beforePid, int afterPid, List<UUID> visibleTenants, List<UUID> visibleMemberships) {}
}
