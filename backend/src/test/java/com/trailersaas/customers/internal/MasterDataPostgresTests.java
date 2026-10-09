package com.trailersaas.customers.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.*;
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
import static org.awaitility.Awaitility.await;
import static com.trailersaas.customers.internal.MasterDataDtos.*;

@SpringBootTest
class MasterDataPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DataSource runtime;
    @Autowired TenantTransactionExecutor transactions;
    @Autowired TenantContext context;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager em;
    @Autowired MasterDataService service;
    @Autowired MasterDataRepository repository;
    UUID a, b, customerA, customerB, siteA, siteB, user;
    AuthenticatedUserPrincipal principal;

    @BeforeEach
    void seed() throws Exception {
        a = tenant(); b = tenant();
        customerA = customer(a); customerB = customer(b);
        siteA = site(a); siteB = site(b);
        user = UUID.randomUUID(); var membership = UUID.randomUUID();
        admin("INSERT INTO users VALUES(?, ?, NULL, NULL, now(), now())", user, "fixture-" + user);
        admin("INSERT INTO tenant_memberships VALUES(?,?,?,'ACTIVE',now(),now())", membership, a, user);
        admin("INSERT INTO role_assignments VALUES(?,?,?,'TENANT_ADMIN','TENANT',NULL,now())", UUID.randomUUID(), a, membership);
        principal = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
        association(a, customerA, siteA); association(b, customerB, siteB);
        location(a, siteA, customerA); location(b, siteB, customerB);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customers", "physical_sites", "customer_sites", "operational_locations"})
    void rlsProtectsMissingPredicateAndHasNoBootstrapVisibility(String table) {
        for (var tenant : List.of(a, b)) {
            transactions.execute(tenant, () -> {
                assertThat(em.createNativeQuery("SELECT DISTINCT tenant_id FROM " + table, UUID.class).getResultList())
                        .containsExactly(tenant);
                return null;
            });
        }
        new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(em.createNativeQuery("SELECT id FROM " + table).getResultList()).isEmpty();
            context.establishBootstrapUser(principal);
            assertThat(em.createNativeQuery("SELECT id FROM " + table).getResultList()).isEmpty();
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"customers", "physical_sites", "operational_locations"})
    void exactAggregateGrantsAndRestrictedRuntimeOwnership(String table) throws Exception {
        try (var connection = runtime.getConnection(); var query = connection.prepareStatement("""
                SELECT r.rolsuper,r.rolbypassrls,pg_get_userbyid(c.relowner)=current_user,
                    has_schema_privilege(current_user,'public','CREATE'),
                    has_table_privilege(current_user,c.oid,'SELECT'),
                    has_table_privilege(current_user,c.oid,'INSERT'),
                    has_table_privilege(current_user,c.oid,'UPDATE'),
                    has_table_privilege(current_user,c.oid,'DELETE'),c.relrowsecurity,
                    (SELECT count(*) FROM aclexplode(c.relacl) WHERE grantee=0)
                FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid=?::regclass
                """)) {
            query.setString(1, table);
            try (var rows = query.executeQuery()) {
                rows.next();
                for (int i = 1; i <= 4; i++) { assertThat(rows.getBoolean(i)).isFalse(); }
                for (int i = 5; i <= 7; i++) { assertThat(rows.getBoolean(i)).isTrue(); }
                assertThat(rows.getBoolean(8)).isFalse(); assertThat(rows.getBoolean(9)).isTrue();
                assertThat(rows.getInt(10)).isZero();
            }
        }
    }

    @Test
    void associationGrantsOnlySelectInsertDelete() throws Exception {
        try (var c = runtime.getConnection(); var q = c.createStatement(); var r = q.executeQuery("""
                SELECT has_table_privilege(current_user,'customer_sites','SELECT'),
                    has_table_privilege(current_user,'customer_sites','INSERT'),
                    has_table_privilege(current_user,'customer_sites','DELETE'),
                    has_table_privilege(current_user,'customer_sites','UPDATE')
                """)) {
            r.next(); assertThat(r.getBoolean(1)).isTrue(); assertThat(r.getBoolean(2)).isTrue();
            assertThat(r.getBoolean(3)).isTrue(); assertThat(r.getBoolean(4)).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"customers", "physical_sites", "operational_locations"})
    void withCheckRejectsMovingOwnRowToAnotherTenant(String table) throws Exception {
        try (var c = runtime.getConnection()) {
            pin(c, a);
            rejects("42501", () -> execute(c, "UPDATE " + table + " SET tenant_id=? WHERE tenant_id=?", b, a));
            c.rollback();
        }
    }

    @Test
    void insertsForOtherTenantAreRejectedForAllFourTables() throws Exception {
        var queries = List.of(
                "INSERT INTO customers VALUES (?,?,'foreign','ACTIVE',now(),now(),0)",
                "INSERT INTO physical_sites(id,tenant_id,name,timezone,active,created_at,updated_at) VALUES(?,?,'foreign','UTC',true,now(),now())",
                "INSERT INTO customer_sites VALUES(?,?, '" + customerB + "', '" + siteB + "', now())",
                "INSERT INTO operational_locations(id,tenant_id,site_id,type,name,active,created_at,updated_at) VALUES(?,?,'" + siteB + "','YARD','foreign',true,now(),now())");
        for (var query : queries) {
            try (var c = runtime.getConnection()) {
                pin(c, a);
                rejects("42501", () -> execute(c, query, UUID.randomUUID(), b));
                c.rollback();
            }
        }
    }

    @Test
    void compositeForeignKeysRejectCrossTenantAndMissingParents() throws Exception {
        rejects("23503", () -> admin("INSERT INTO customers VALUES(?,?,'orphan','ACTIVE',now(),now(),0)", UUID.randomUUID(), UUID.randomUUID()));
        rejects("23503", () -> association(a, customerA, siteB));
        rejects("23503", () -> association(a, customerB, siteA));
        rejects("23503", () -> location(a, siteB, customerA));
        rejects("23503", () -> location(a, siteA, customerB));
        var membership = UUID.randomUUID();
        admin("INSERT INTO tenant_memberships VALUES(?,?,?,'ACTIVE',now(),now())", membership, b, user);
        for (var customer : List.of(UUID.randomUUID(), customerA)) {
            rejects("23503", () -> admin("INSERT INTO role_assignments VALUES(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())",
                    UUID.randomUUID(), b, membership, customer));
        }
        admin("INSERT INTO role_assignments VALUES(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())",
                UUID.randomUUID(), b, membership, customerB);
        rejects("23503", () -> admin("DELETE FROM customers WHERE id=?", customerA));
        rejects("23503", () -> admin("DELETE FROM physical_sites WHERE id=?", siteA));
    }

    @Test
    void schemaAndClosedCatalogsMatchV5WithoutPrematureDomains() throws Exception {
        assertThat(flyway.info().applied()).extracting(i -> i.getVersion().getVersion()).containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15");
        try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.createStatement()) {
            try (var rows = q.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")) {
                var names = new java.util.ArrayList<String>();
                while (rows.next()) { names.add(rows.getString(1)); }
                assertThat(names).containsExactlyInAnyOrder("flyway_schema_history","users","tenants","tenant_memberships",
                        "role_assignments","customers","physical_sites","customer_sites","operational_locations",
                        "membership_invitations","invitation_role_assignments","drivers","tractors","trailers","trailer_qr_identities","tenant_settings", "orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders", "trips", "trip_stops", "trip_stop_orders", "trip_assignment_events", "trip_operational_events", "trip_driver_updates");
            }
            try (var rows = q.executeQuery("""
                    SELECT count(*) FROM pg_constraint WHERE conrelid='operational_locations'::regclass
                        AND contype='u' AND pg_get_constraintdef(oid)='UNIQUE (tenant_id, site_id, id)'
                    """)) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
        rejects("23514", () -> admin("UPDATE customers SET status='UNKNOWN' WHERE id=?", customerA));
        rejects("23514", () -> admin("UPDATE operational_locations SET type='LOCATION_UNKNOWN' WHERE tenant_id=?", a));
        rejects("23505", () -> admin("INSERT INTO customers VALUES(?,?,'Customer','ACTIVE',now(),now(),0)", UUID.randomUUID(), a));
        // Site names/addresses deliberately not unique.
        site(a);
    }

    @Test
    void jpaVersionDetectsRaceAfterBothTransactionsReadSameVersion() throws Exception {
        var loaded = new CountDownLatch(2); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> versionedEdit(loaded, release, "First"));
            var second = executor.submit(() -> versionedEdit(loaded, release, "Second"));
            try { assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { release.countDown(); }
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("updated", "stale");
        }
    }
    private String versionedEdit(CountDownLatch loaded, CountDownLatch release, String name) {
        try {
            transactions.execute(a, () -> {
                var customer = repository.resource(CustomerEntity.class, a, customerA);
                customer.requireVersion(0); loaded.countDown(); waitFor(release);
                customer.name = name; repository.flush(); return null;
            });
            return "updated";
        } catch (org.springframework.dao.OptimisticLockingFailureException error) { return "stale"; }
    }

    @Test
    void sharedParentSiteLockSerializesCreateAgainstAssociationRemoval() throws Exception {
        // Remove the initial location so only the concurrently created location prevents removal.
        admin("DELETE FROM operational_locations WHERE tenant_id=?", a);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var create = executor.submit(() -> transactions.execute(a, () -> {
                repository.lockAssociation(a, customerA, siteA, false); locked.countDown(); waitFor(release);
                return service.createLocation(principal, a, siteA, new CreateLocation("Concurrent", "DOCK", null, customerA));
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var remove = executor.submit(() -> {
                try { service.disassociate(principal, a, customerA, siteA); return "removed"; }
                catch (MasterDataFailure error) { return error.code; }
            });
            try { awaitBlockedAssociationQuery(); }
            finally { release.countDown(); }
            assertThat(create.get(15, TimeUnit.SECONDS).customerId()).isEqualTo(customerA);
            assertThat(remove.get(15, TimeUnit.SECONDS)).isEqualTo("RESOURCE_CONFLICT");
        } finally { release.countDown(); }
    }

    @Test
    void exclusiveParentSiteLockDuringAssociationRemovalBlocksAndRejectsReactivation() throws Exception {
        admin("UPDATE operational_locations SET active=false WHERE tenant_id=?", a);
        UUID location = transactions.execute(a, () -> repository.locations(a, siteA, new CustomerAccessScope(true, java.util.Set.of())).getFirst().id);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var remove = executor.submit(() -> transactions.execute(a, () -> {
                var id = repository.lockAssociation(a, customerA, siteA, true);
                repository.deleteAssociation(a, id); locked.countDown(); waitFor(release); return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var reactivate = executor.submit(() -> {
                try {
                    service.updateLocation(principal, a, location, java.util.Map.of("expectedVersion", 0, "active", true));
                    return "reactivated";
                } catch (MasterDataFailure error) { return error.code; }
            });
            try { awaitBlockedAssociationQuery(); }
            finally { release.countDown(); }
            remove.get(15, TimeUnit.SECONDS);
            assertThat(reactivate.get(15, TimeUnit.SECONDS)).isEqualTo("RESOURCE_CONFLICT");
            assertThat(service.locations(principal, a, siteA).items().getFirst().active()).isFalse();
        } finally { release.countDown(); }
    }
    private void awaitBlockedAssociationQuery() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.prepareStatement(
                    "SELECT count(*) FROM pg_stat_activity WHERE usename=? AND wait_event_type='Lock' AND query LIKE '%physical_sites%'")) {
                q.setString(1, RUNTIME_USERNAME);
                try (var r = q.executeQuery()) { r.next(); assertThat(r.getInt(1)).isGreaterThan(0); }
            }
        });
    }
    private static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out waiting for test transaction"); } }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
    private UUID tenant() throws SQLException {
        var id = UUID.randomUUID(); admin("INSERT INTO tenants VALUES(?,'Tenant','CANDIDATE',now(),now())", id); return id;
    }
    private UUID customer(UUID tenant) throws SQLException {
        var id = UUID.randomUUID(); admin("INSERT INTO customers VALUES(?,?,'Customer','ACTIVE',now(),now(),0)", id, tenant); return id;
    }
    private UUID site(UUID tenant) throws SQLException {
        var id = UUID.randomUUID();
        admin("INSERT INTO physical_sites(id,tenant_id,name,timezone,active,created_at,updated_at) VALUES(?,?,'Site','UTC',true,now(),now())", id, tenant);
        return id;
    }
    private void association(UUID tenant, UUID customer, UUID site) throws SQLException {
        admin("INSERT INTO customer_sites VALUES(?,?,?,?,now())", UUID.randomUUID(), tenant, customer, site);
    }
    private void location(UUID tenant, UUID site, UUID customer) throws SQLException {
        admin("INSERT INTO operational_locations(id,tenant_id,site_id,customer_id,type,name,active,created_at,updated_at) VALUES(?,?,?,?,'DOCK','Dock',true,now(),now())",
                UUID.randomUUID(), tenant, site, customer);
    }
    private void admin(String query, Object... values) throws SQLException {
        try (var c = flyway.getConfiguration().getDataSource().getConnection()) { execute(c, query, values); }
    }
    private void execute(Connection c, String query, Object... values) throws SQLException {
        try (var q = c.prepareStatement(query)) {
            for (int i = 0; i < values.length; i++) { q.setObject(i + 1, values[i]); } q.executeUpdate();
        }
    }
    private void pin(Connection c, UUID tenant) throws SQLException {
        c.setAutoCommit(false);
        try (var q = c.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true), set_config('app.current_user_id','',true)")) {
            q.setString(1, tenant.toString()); q.executeQuery().close();
        }
    }
    private void rejects(String state, SqlAction action) {
        assertThatThrownBy(action::run).isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(state));
    }
    @FunctionalInterface interface SqlAction { void run() throws SQLException; }
}
