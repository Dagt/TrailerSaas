package com.trailersaas.identity.internal;

import java.util.UUID;

import javax.sql.DataSource;

import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantAuthorization;
import com.trailersaas.identity.TenantTransactionExecutor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.trailersaas.identity.internal.AuthorizationDeniedException.Reason.*;
import static com.trailersaas.identity.internal.TenantAuthorizationTests.denied;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AuthorizationPostgresTests extends PostgresIntegrationTest {
    @Autowired TenantAuthorization authorization;
    @Autowired TenantTransactionExecutor transactions;
    @Autowired ActiveMembershipRepository repository;
    @Autowired DataSource database;
    @Autowired Flyway flyway;
    IdentityFixtures fixtures;

    @BeforeEach
    void fixtures() { fixtures = new IdentityFixtures(flyway); }

    @ParameterizedTest
    @CsvSource({"TENANT_ADMIN, USER_INVITE, true", "YARD_SPOTTER, USER_INVITE, false",
            "TRANSPORT_OPERATIONS, TRIP_PLAN, true", "DRIVER, TRIP_PLAN, false"})
    void representativeTenantRoles(String role, Capability capability, boolean allowed) throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Candidate Tenant status does not authorize");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        fixtures.role(tenant, membership, role, "TENANT", null);
        if (allowed) { authorization.requireTenantCapability(user, tenant, capability); }
        else { denied(() -> authorization.requireTenantCapability(user, tenant, capability), CAPABILITY_DENIED); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "DISABLED"})
    void nonActiveMembershipWithAdminRoleNeverGrants(String status) throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Non-active");
        var membership = fixtures.membership(tenant, user.userId(), status);
        fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
        assertThat(transactions.execute(tenant, () -> repository.forActiveUserInTenant(user.userId(), tenant))).isEmpty();
        denied(() -> authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
        denied(() -> authorization.requireCustomerCapability(user, tenant, UUID.randomUUID(), Capability.ORDER_EDIT),
                TENANT_ACCESS_DENIED);
        denied(() -> authorization.requireAssignedWorkCapability(user, tenant, Capability.TRIP_EXECUTE_ASSIGNED, true),
                TENANT_ACCESS_DENIED);
    }

    @Test
    void lookupIsScopedToBothInternalUserAndRequestedTenant() throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Authorized");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
        authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        var foreignTenant = fixtures.tenant("Foreign");
        var foreignUser = principal(fixtures.user());
        denied(() -> authorization.requireTenantCapability(user, foreignTenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
        denied(() -> authorization.requireTenantCapability(foreignUser, tenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
        denied(() -> authorization.requireTenantCapability(user, UUID.randomUUID(), Capability.USER_INVITE), TENANT_ACCESS_DENIED);
    }

    @Test
    void rolelessActiveMembershipIsDistinctFromMissingMembershipAndRolesAggregate() throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Aggregate");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        assertThat(transactions.execute(tenant, () -> repository.forActiveUserInTenant(user.userId(), tenant))).containsExactly(
                new AuthorizationRoleRow(membership, null, null, null));
        denied(() -> authorization.requireTenantCapability(user, tenant, Capability.TRIP_PLAN), CAPABILITY_DENIED);
        fixtures.role(tenant, membership, "TRANSPORT_OPERATIONS", "TENANT", null);
        fixtures.role(tenant, membership, "YARD_SPOTTER", "TENANT", null);
        authorization.requireTenantCapability(user, tenant, Capability.TRIP_PLAN);
        authorization.requireTenantCapability(user, tenant, Capability.YARD_CHECK_EXECUTE);
        denied(() -> authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE), CAPABILITY_DENIED);
    }

    @Test
    void customerAssignmentsOnlyGrantTheirOwnScopeAndNeverTenantWide() throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Customer scopes");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        var a = fixtures.customer(tenant);
        var b = fixtures.customer(tenant);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", a);
        authorization.requireCustomerCapability(user, tenant, a, Capability.ORDER_EDIT);
        denied(() -> authorization.requireCustomerCapability(user, tenant, b, Capability.ORDER_EDIT), CUSTOMER_SCOPE_DENIED);
        denied(() -> authorization.requireCustomerCapability(user, tenant, null, Capability.ORDER_EDIT), CUSTOMER_SCOPE_DENIED);
        denied(() -> authorization.requireTenantCapability(user, tenant, Capability.ORDER_EDIT), CAPABILITY_DENIED);
        fixtures.role(tenant, membership, "CUSTOMER_DISPATCHER", "CUSTOMER", b);
        authorization.requireCustomerCapability(user, tenant, b, Capability.ORDER_EDIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_ADMIN", "TRANSPORT_OPERATIONS"})
    void tenantRoleCanSatisfySameCapabilityForAnyCustomer(String role) throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Tenant scope");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        fixtures.role(tenant, membership, role, "TENANT", null);
        authorization.requireCustomerCapability(user, tenant, UUID.randomUUID(), Capability.ORDER_EDIT);
        authorization.requireCustomerCapability(user, tenant, UUID.randomUUID(), Capability.ORDER_EDIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRIP_VIEW_ASSIGNED", "TRIP_EXECUTE_ASSIGNED"})
    void driverAssignmentIsMandatoryAndCannotBeBypassed(String name) throws Exception {
        var capability = Capability.valueOf(name);
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Assigned work");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        fixtures.role(tenant, membership, "DRIVER", "TENANT", null);
        authorization.requireAssignedWorkCapability(user, tenant, capability, true);
        denied(() -> authorization.requireAssignedWorkCapability(user, tenant, capability, false), CAPABILITY_DENIED);
        denied(() -> authorization.requireTenantCapability(user, tenant, capability), CAPABILITY_DENIED);
        denied(() -> authorization.requireCustomerCapability(user, tenant, UUID.randomUUID(), capability), CAPABILITY_DENIED);
    }

    @Test
    void samePrincipalSeesRoleRemovalAndMembershipSuspensionImmediately() throws Exception {
        var user = principal(fixtures.user());
        var tenant = fixtures.tenant("Fresh authorization");
        var membership = fixtures.membership(tenant, user.userId(), "ACTIVE");
        fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
        authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        fixtures.execute("DELETE FROM role_assignments WHERE tenant_id=? AND membership_id=?", tenant, membership);
        denied(() -> authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE), CAPABILITY_DENIED);
        fixtures.role(tenant, membership, "TENANT_ADMIN", "TENANT", null);
        authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        fixtures.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE tenant_id=? AND id=?", tenant, membership);
        denied(() -> authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
    }

    @Test
    void resolverRunsWithV4RlsAndUnchangedV3SchemaAndRuntimeGrants() throws Exception {
        flyway.validate();
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28");
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            try (var rows = statement.executeQuery("SELECT current_user")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo(RUNTIME_USERNAME);
            }
            for (var table : java.util.List.of("tenants", "tenant_memberships", "role_assignments")) {
                try (var query = connection.prepareStatement("""
                        SELECT has_table_privilege(current_user, ?, 'SELECT'),
                            has_table_privilege(current_user, ?, 'UPDATE,DELETE'),
                            relrowsecurity, relforcerowsecurity
                        FROM pg_class WHERE oid=?::regclass
                        """)) {
                    query.setString(1, table); query.setString(2, table); query.setString(3, table);
                    try (var rows = query.executeQuery()) {
                        rows.next();
                        assertThat(rows.getBoolean(1)).isTrue();
                        assertThat(rows.getBoolean(2)).isFalse();
                        assertThat(rows.getBoolean(3)).isTrue();
                        assertThat(rows.getBoolean(4)).isFalse();
                    }
                }
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname='public'")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(53);
            }
            try (var rows = statement.executeQuery("""
                    SELECT relrowsecurity, relforcerowsecurity,
                      has_table_privilege(current_user,'order_request_revisions','SELECT'),
                      has_table_privilege(current_user,'order_request_revisions','INSERT'),
                      has_table_privilege(current_user,'order_request_revisions','UPDATE,DELETE')
                    FROM pg_class WHERE oid='order_request_revisions'::regclass
                    """)) {
                assertThat(rows.next()).isTrue();
                for (int column=1;column<=4;column++) assertThat(rows.getBoolean(column)).isTrue();
                assertThat(rows.getBoolean(5)).isFalse();
            }
        }
    }

    private AuthenticatedUserPrincipal principal(UUID id) {
        return new AuthenticatedUserPrincipal(id, "fixture-" + id, null, null);
    }
}
