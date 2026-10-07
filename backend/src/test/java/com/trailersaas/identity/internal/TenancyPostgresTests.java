package com.trailersaas.identity.internal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.trailersaas.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class TenancyPostgresTests extends PostgresIntegrationTest {
    @Autowired DataSource database;
    @Autowired Flyway flyway;
    IdentityFixtures fixtures;

    @BeforeEach
    void fixtures() { fixtures = new IdentityFixtures(flyway); }

    @Test
    void flywayV5PreservesAcceptedIdentityColumnsAndAddsCustomerForeignKey() throws Exception {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
        // Flyway history is migrator-owned; runtime deliberately has no grant.
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5");
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            var tables = new ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")) {
                while (rows.next()) { tables.add(rows.getString(1)); }
            }
            assertThat(tables).containsExactlyInAnyOrder("users", "tenants", "tenant_memberships",
                    "role_assignments", "flyway_schema_history", "customers", "physical_sites", "customer_sites", "operational_locations");
            for (var entry : java.util.Map.of(
                    "users", List.of("id", "firebase_uid", "email", "display_name", "created_at", "updated_at"),
                    "tenants", List.of("id", "name", "status", "created_at", "updated_at"),
                    "tenant_memberships", List.of("id", "tenant_id", "user_id", "status", "created_at", "updated_at"),
                    "role_assignments", List.of("id", "tenant_id", "membership_id", "role_code", "scope_type", "customer_id", "created_at")).entrySet()) {
                var columns = new ArrayList<String>();
                try (var query = connection.prepareStatement(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position")) {
                    query.setString(1, entry.getKey());
                    try (var rows = query.executeQuery()) { while (rows.next()) { columns.add(rows.getString(1)); } }
                }
                assertThat(columns).containsExactlyElementsOf(entry.getValue());
            }
            try (var rows = statement.executeQuery(
                    "SELECT count(*) FROM pg_constraint WHERE conrelid='role_assignments'::regclass AND contype='f'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2); // Membership and tenant-safe Customer FKs.
            }
        }
    }

    @Test
    void sameUserHasMultipleTenantsButCannotDuplicateMembership() throws Exception {
        var user = fixtures.user();
        var tenant = fixtures.tenant("First");
        fixtures.membership(tenant, user, "ACTIVE");
        fixtures.membership(fixtures.tenant("Second"), user, "ACTIVE");
        rejects("23505", () -> fixtures.membership(tenant, user, "ACTIVE"));
        try (var connection = database.getConnection(); var query = connection.prepareStatement(
                "SELECT count(*) FROM tenant_memberships WHERE user_id=?")) {
            connection.setAutoCommit(false);
            try (var context = connection.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
                context.setString(1, user.toString());
                context.executeQuery().close();
            }
            query.setObject(1, user);
            try (var rows = query.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(2); }
            connection.rollback();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED", "DISABLED"})
    void acceptedMembershipStatuses(String status) throws Exception {
        fixtures.membership(fixtures.tenant("Status"), fixtures.user(), status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVITED", "UNKNOWN", "active"})
    void unacceptedMembershipStatusesAreRejected(String status) throws Exception {
        var tenant = fixtures.tenant("Status");
        var user = fixtures.user();
        rejects("23514", () -> fixtures.membership(tenant, user, status));
    }

    @Test
    void compositeMembershipForeignKeyRejectsCrossTenantRoles() throws Exception {
        var tenant = fixtures.tenant("Owner");
        var membership = fixtures.membership(tenant, fixtures.user(), "ACTIVE");
        var other = fixtures.tenant("Other");
        rejects("23503", () -> fixtures.role(other, membership, "DRIVER", "TENANT", null));
    }

    @Test
    void membershipParentForeignKeysRejectMissingUsersAndTenants() throws Exception {
        var user = fixtures.user();
        var tenant = fixtures.tenant("Parent");
        rejects("23503", () -> fixtures.membership(UUID.randomUUID(), user, "ACTIVE"));
        rejects("23503", () -> fixtures.membership(tenant, UUID.randomUUID(), "ACTIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_ADMIN", "TRANSPORT_OPERATIONS", "YARD_SPOTTER", "DRIVER"})
    void tenantRolesRequireTenantScopeAndNullCustomerAndCannotDuplicate(String role) throws Exception {
        var tenant = fixtures.tenant("Role matrix");
        var membership = fixtures.membership(tenant, fixtures.user(), "ACTIVE");
        fixtures.role(tenant, membership, role, "TENANT", null);
        rejects("23505", () -> fixtures.role(tenant, membership, role, "TENANT", null));
        rejects("23514", () -> fixtures.role(tenant, membership, role, "TENANT", UUID.randomUUID()));
        rejects("23514", () -> fixtures.role(tenant, membership, role, "CUSTOMER", UUID.randomUUID()));
        rejects("23514", () -> fixtures.role(tenant, membership, role, "CUSTOMER", null));
    }

    @Test
    void dispatcherRequiresCustomerScopeAndSupportsDifferentRealCustomers() throws Exception {
        var tenant = fixtures.tenant("Dispatcher");
        var membership = fixtures.membership(tenant, fixtures.user(), "ACTIVE");
        var customer = fixtures.customer(tenant);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", customer);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", fixtures.customer(tenant));
        rejects("23505", () -> fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", customer));
        rejects("23514", () -> fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", null));
        rejects("23514", () -> fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "TENANT", null));
        rejects("23514", () -> fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "TENANT", customer));
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN,TENANT", "DRIVER,LOCATION", "TENANT_ADMIN,UNKNOWN"})
    void closedRoleAndScopeSetsRejectUnknownValues(String role, String scope) throws Exception {
        var tenant = fixtures.tenant("Closed sets");
        var membership = fixtures.membership(tenant, fixtures.user(), "ACTIVE");
        rejects("23514", () -> fixtures.role(tenant, membership, role, scope, null));
    }

    @Test
    void differentTenantRolesCanCoexistAndForeignKeysPreserveHistory() throws Exception {
        var tenant = fixtures.tenant("Multiple roles");
        var user = fixtures.user();
        var membership = fixtures.membership(tenant, user, "ACTIVE");
        for (var role : List.of("TENANT_ADMIN", "TRANSPORT_OPERATIONS", "YARD_SPOTTER", "DRIVER")) {
            fixtures.role(tenant, membership, role, "TENANT", null);
        }
        rejects("23503", () -> fixtures.execute("DELETE FROM tenant_memberships WHERE id=?", membership));
        rejects("23503", () -> fixtures.execute("DELETE FROM users WHERE id=?", user));
        rejects("23503", () -> fixtures.execute("DELETE FROM tenants WHERE id=?", tenant));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenants", "tenant_memberships", "role_assignments"})
    void runtimeHasOnlySelectIsNotOwnerAndIsProtectedByRlsWithoutAdministrativePrivileges(String table) throws Exception {
        // table comes exclusively from the fixed test annotation, not configuration/input.
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            try (var query = connection.prepareStatement("""
                    SELECT pg_get_userbyid(c.relowner), current_user,
                           has_table_privilege(current_user,c.oid,'SELECT'),
                           has_table_privilege(current_user,c.oid,'INSERT'),
                           has_table_privilege(current_user,c.oid,'UPDATE'),
                           has_table_privilege(current_user,c.oid,'DELETE'),
                           has_schema_privilege(current_user,'public','CREATE'),
                           r.rolsuper, r.rolbypassrls, c.relrowsecurity, c.relforcerowsecurity
                    FROM pg_class c JOIN pg_roles r ON r.rolname=current_user
                    WHERE c.oid=to_regclass(?)
                    """)) {
                query.setString(1, table);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNotEqualTo(RUNTIME_USERNAME);
                    assertThat(rows.getString(2)).isEqualTo(RUNTIME_USERNAME);
                    assertThat(rows.getBoolean(3)).isTrue();
                    for (int column = 4; column <= 9; column++) { assertThat(rows.getBoolean(column)).isFalse(); }
                    assertThat(rows.getBoolean(10)).isTrue();
                    assertThat(rows.getBoolean(11)).isFalse();
                }
            }
            statement.executeQuery("SELECT * FROM " + table).close();
            rejects("42501", () -> statement.executeUpdate("INSERT INTO " + table + " (id) VALUES (NULL)"));
            rejects("42501", () -> statement.executeUpdate("UPDATE " + table + " SET id=id WHERE false"));
            rejects("42501", () -> statement.executeUpdate("DELETE FROM " + table + " WHERE false"));
            rejects("42501", () -> statement.executeUpdate("CREATE TABLE forbidden_identity_table (id uuid)"));
            try (var query = connection.prepareStatement("""
                    SELECT count(*) FROM pg_class c, LATERAL aclexplode(c.relacl) a
                    WHERE c.oid=to_regclass(?) AND a.grantee=0
                    """)) {
                query.setString(1, table);
                try (var rows = query.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
            }
        }
    }

    private void rejects(String state, SqlAction action) {
        assertThatThrownBy(action::run).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(state));
    }

    @FunctionalInterface
    interface SqlAction { void run() throws SQLException; }
}
