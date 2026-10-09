package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ManifestPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DataSource runtime;
    @Autowired ManifestService service;
    OrderFixtures f;
    UUID a, b, customer, otherCustomer, user, dispatcherMembership, order, secondOrder, foreignOrder, trailer;
    AuthenticatedUserPrincipal admin, dispatcher;
    @BeforeEach void seed() throws Exception {
        f = new OrderFixtures(flyway); a = f.db.tenant(); b = f.db.tenant(); customer = f.customer(a); otherCustomer = f.customer(a);
        user = f.db.user(); f.db.membership(a, user, "ACTIVE", "TENANT_ADMIN"); admin = principal(user);
        var scopedUser = f.db.user(); dispatcherMembership = f.db.membership(a, scopedUser, "ACTIVE", null);
        f.db.execute("INSERT INTO role_assignments VALUES(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())", UUID.randomUUID(), a, dispatcherMembership, customer);
        dispatcher = principal(scopedUser);
        order = f.order(a, customer, user); secondOrder = f.order(a, otherCustomer, user); foreignOrder = f.order(b, f.customer(b), user);
        trailer = new TrailerFixtures(flyway).trailer(a, "M-" + UUID.randomUUID(), customer);
    }
    AuthenticatedUserPrincipal principal(UUID id) { return new AuthenticatedUserPrincipal(id, "fixture-" + id, null, null); }
    @Test void exactV11SchemaAndNoOrderToTrailerForeignKey() throws Exception {
        flyway.validate(); assertThat(flyway.info().applied()).extracting(i -> i.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        assertThat(columns("load_manifests")).containsExactly("id", "tenant_id", "trailer_id", "status", "created_at", "updated_at", "version", "closed_at");
        assertThat(columns("manifest_orders")).containsExactly("id", "tenant_id", "manifest_id", "order_id", "allocated_quantity", "allocated_pallet_count", "allocated_weight", "created_at");
        assertThat(f.db.value("SELECT count(*) FROM pg_constraint WHERE conrelid='orders'::regclass AND confrelid='trailers'::regclass")).isEqualTo(0L);
        assertThat(f.db.value("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('load_manifests'::regclass,'manifest_orders'::regclass) AND confdeltype='c'")).isEqualTo(0L);
        assertThat(columns("orders")).doesNotContain("trailer_id", "manifest_id");
    }
    @Test void manyToManyAndOptionalTrailerHaveNoFalseUniqueness() throws Exception {
        var one = manifest(a, trailer); var two = manifest(a, trailer); manifest(a, null);
        allocation(a, one, order, null, null, null); allocation(a, one, secondOrder, new BigDecimal("1.25"), 0, new BigDecimal("0.75"));
        allocation(a, two, order, BigDecimal.ZERO, 2, null);
        assertThat((BigDecimal) f.db.value("SELECT allocated_quantity FROM manifest_orders WHERE manifest_id=? AND order_id=?", one, secondOrder)).isEqualByComparingTo("1.25");
        assertThat((BigDecimal) f.db.value("SELECT allocated_weight FROM manifest_orders WHERE manifest_id=? AND order_id=?", one, secondOrder)).isEqualByComparingTo("0.75");
        assertThat(f.db.value("SELECT allocated_pallet_count FROM manifest_orders WHERE manifest_id=? AND order_id=?", one, secondOrder)).isEqualTo(0);
        rejects("23505", () -> allocation(a, one, order, null, null, null));
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE order_id=?", order)).isEqualTo(2L);
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE manifest_id=?", one)).isEqualTo(2L);
        assertThat(f.db.value("SELECT count(*) FROM load_manifests WHERE trailer_id=?", trailer)).isEqualTo(2L);
    }
    @Test void crossTenantParentsAndInvalidAmountsFailAtDatabase() throws Exception {
        var m = manifest(a, trailer); var foreign = manifest(b, null);
        rejects("23503", () -> manifest(b, trailer));
        rejects("23503", () -> allocation(a, foreign, order, null, null, null));
        rejects("23503", () -> allocation(a, m, foreignOrder, null, null, null));
        rejects("23514", () -> allocation(a, m, order, new BigDecimal("-0.25"), null, null));
        rejects("23514", () -> allocation(a, m, order, null, -1, null));
        rejects("23514", () -> allocation(a, m, order, null, null, new BigDecimal("-1")));
        rejects("22P02", () -> f.db.execute("INSERT INTO manifest_orders(id,tenant_id,manifest_id,order_id,allocated_pallet_count,created_at) VALUES(?,?,?,?,'1.5',now())", UUID.randomUUID(), a, m, order));
    }
    @Test void stateChecksAreClosedAndTimestampConsistent() throws Exception {
        var m = manifest(a, null);
        rejects("23514", () -> f.db.execute("UPDATE load_manifests SET status='OTHER' WHERE id=?", m));
        rejects("23514", () -> f.db.execute("UPDATE load_manifests SET closed_at=now() WHERE id=?", m));
        rejects("23514", () -> f.db.execute("UPDATE load_manifests SET status='CLOSED' WHERE id=?", m));
        f.db.execute("UPDATE load_manifests SET status='CLOSED',closed_at=now() WHERE id=?", m);
    }
    @ParameterizedTest @ValueSource(strings = {"load_manifests", "manifest_orders"})
    void runtimePrivilegesAndRlsExactlyMatchPlanningNeeds(String table) throws Exception {
        try (var c = runtime.getConnection(); var q = c.prepareStatement("""
                SELECT c.relrowsecurity,r.rolsuper,r.rolbypassrls,pg_get_userbyid(c.relowner)=current_user,
                    has_schema_privilege(current_user,'public','CREATE'),has_table_privilege(current_user,c.oid,'SELECT'),
                    has_table_privilege(current_user,c.oid,'INSERT'),has_table_privilege(current_user,c.oid,'UPDATE'),
                    has_table_privilege(current_user,c.oid,'DELETE'),(SELECT count(*) FROM aclexplode(c.relacl) WHERE grantee=0)
                FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid=?::regclass
                """)) {
            assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            q.setString(1, table); try (var r = q.executeQuery()) {
                r.next(); assertThat(r.getBoolean(1)).isTrue(); for (int i = 2; i <= 5; i++) { assertThat(r.getBoolean(i)).isFalse(); }
                assertThat(r.getBoolean(6)).isTrue(); assertThat(r.getBoolean(7)).isTrue();
                assertThat(r.getBoolean(8)).isEqualTo(table.equals("load_manifests"));
                assertThat(r.getBoolean(9)).isEqualTo(table.equals("manifest_orders")); assertThat(r.getInt(10)).isZero();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings = {"load_manifests", "manifest_orders"})
    void rlsDeniesNoContextBootstrapAndCrossTenantReadsAndWrites(String table) throws Exception {
        var ma = manifest(a, null); var mb = manifest(b, null);
        allocation(a, ma, order, null, null, null); allocation(b, mb, foreignOrder, null, null, null);
        try (var c = runtime.getConnection()) {
            c.setAutoCommit(false); assertThat(count(c, table)).isZero(); setting(c, "app.current_user_id", user.toString()); assertThat(count(c, table)).isZero(); c.rollback();
            setting(c, "app.current_tenant_id", a.toString()); assertThat(count(c, table)).isEqualTo(1);
            assertThat(f.db.execute(c, table.equals("load_manifests") ? "UPDATE load_manifests SET updated_at=now() WHERE tenant_id=?" : "DELETE FROM manifest_orders WHERE tenant_id=?", b)).isZero();
            if (table.equals("load_manifests")) { rejects("42501", () -> f.db.execute(c, "UPDATE load_manifests SET tenant_id=? WHERE id=?", b, ma)); }
            else { rejects("42501", () -> f.db.execute(c, "INSERT INTO manifest_orders(id,tenant_id,manifest_id,order_id,created_at) VALUES(?,?,?,?,now())", UUID.randomUUID(), b, mb, foreignOrder)); }
            c.rollback(); setting(c, "app.current_tenant_id", a.toString()); setting(c, "app.current_user_id", user.toString()); assertThat(count(c, table)).isZero(); c.rollback();
        }
    }
    @Test void serviceLifecycleVersionsAndClosedHistoryArePreserved() throws Exception {
        var m = service.create(admin, a, trailer);
        assertThat(m.status()).isEqualTo(ManifestStatus.OPEN); assertThat(m.version()).isZero(); assertThat(m.closedAt()).isNull();
        var added = service.allocate(admin, a, m.id(), 0, order, new BigDecimal("0.25"), 0, null);
        assertThat(added.version()).isEqualTo(1); assertThat(added.updatedAt()).isAfter(m.updatedAt());
        failure("STALE_VERSION", () -> service.remove(admin, a, m.id(), 0, order));
        var removed = service.remove(admin, a, m.id(), 1, order); assertThat(removed.version()).isEqualTo(2);
        assertThat(f.db.value("SELECT count(*) FROM orders WHERE id=?", order)).isEqualTo(1L);
        service.allocate(admin, a, m.id(), 2, order, null, null, null);
        var closed = service.close(admin, a, m.id(), 3); assertThat(closed.version()).isEqualTo(4);
        assertThat(closed.closedAt()).isNotNull(); assertThat(closed.status()).isEqualTo(ManifestStatus.CLOSED);
        failure("RESOURCE_CONFLICT", () -> service.allocate(admin, a, m.id(), 4, secondOrder, null, null, null));
        failure("RESOURCE_CONFLICT", () -> service.remove(admin, a, m.id(), 4, order));
        failure("RESOURCE_CONFLICT", () -> service.close(admin, a, m.id(), 4));
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE manifest_id=?", m.id())).isEqualTo(1L);
        assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("OPEN");
        assertThat(f.db.value("SELECT load_state FROM trailers WHERE id=?", trailer)).isEqualTo("UNKNOWN");
        assertThat(f.db.value("SELECT version FROM trailers WHERE id=?", trailer)).isEqualTo(0L);
    }
    @ParameterizedTest @ValueSource(strings = {"PLANNED", "IN_PROGRESS", "PARTIALLY_DELIVERED", "DELIVERED", "COMPLETED", "CANCELLING", "CANCELLED"})
    void everyNonOpenOrderRejectsAddAndRemoveWithoutMutatingIt(String state) throws Exception {
        var m = service.create(admin, a, null); service.allocate(admin, a, m.id(), 0, order, null, null, null);
        f.db.execute("UPDATE orders SET status=?,delivered_at=CASE WHEN ? IN ('DELIVERED','COMPLETED') THEN now() END,completed_at=CASE WHEN ?='COMPLETED' THEN now() END,cancelled_at=CASE WHEN ?='CANCELLED' THEN now() END WHERE id=?", state, state, state, state, order);
        failure("RESOURCE_CONFLICT", () -> service.remove(admin, a, m.id(), 1, order));
        var other = service.create(admin, a, null);
        failure("RESOURCE_CONFLICT", () -> service.allocate(admin, a, other.id(), 0, order, null, null, null));
        assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo(state);
    }
    @Test void multiCustomerManifestScopedActorCanOnlyModifyOwnAllocationsAndCannotClose() throws Exception {
        var m = service.create(admin, a, null); service.allocate(admin, a, m.id(), 0, secondOrder, null, null, null);
        failure("RESOURCE_NOT_FOUND", () -> service.remove(dispatcher, a, m.id(), 1, secondOrder));
        service.allocate(dispatcher, a, m.id(), 1, order, null, null, null);
        assertThatThrownBy(() -> service.close(dispatcher, a, m.id(), 2)).isInstanceOf(RuntimeException.class).hasMessage("The requested action is not permitted.");
        service.remove(dispatcher, a, m.id(), 2, order);
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE manifest_id=? AND order_id=?", m.id(), secondOrder)).isEqualTo(1L);
        f.db.execute("DELETE FROM role_assignments WHERE membership_id=?", dispatcherMembership);
        assertThatThrownBy(() -> service.allocate(dispatcher, a, m.id(), 3, order, null, null, null)).isInstanceOf(RuntimeException.class);
    }
    @Test void duplicateAllocationAndInvisibleManifestNeverChangeVersion() throws Exception {
        var m = service.create(admin, a, null); service.allocate(admin, a, m.id(), 0, order, null, null, null);
        failure("RESOURCE_CONFLICT", () -> service.allocate(admin, a, m.id(), 1, order, null, null, null));
        var foreign = manifest(b, null); failure("RESOURCE_NOT_FOUND", () -> service.allocate(admin, a, foreign, 0, order, null, null, null));
        assertThat(f.db.value("SELECT version FROM load_manifests WHERE id=?", m.id())).isEqualTo(1L);
    }
    @Test void simultaneousAllocationCommandsWithSameVersionHaveOneWinnerAndNoPartialWrites() throws Exception {
        var m = service.create(admin, a, null); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var attempts = new ArrayList<Future<String>>();
            for (var target : List.of(order, secondOrder)) {
                attempts.add(pool.submit(() -> { ready.countDown(); if (!start.await(20, TimeUnit.SECONDS)) { throw new AssertionError("start gate"); }
                    try { service.allocate(admin, a, m.id(), 0, target, null, null, null); return "OK"; }
                    catch (OrderFailure e) { return e.code; }
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(attempts.get(0).get(30, TimeUnit.SECONDS), attempts.get(1).get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK", "STALE_VERSION");
        }
        assertThat(f.db.value("SELECT version FROM load_manifests WHERE id=?", m.id())).isEqualTo(1L);
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE manifest_id=?", m.id())).isEqualTo(1L);
    }
    UUID manifest(UUID tenant, UUID trailerId) throws SQLException {
        var id = UUID.randomUUID(); f.db.execute("INSERT INTO load_manifests(id,tenant_id,trailer_id,status,created_at,updated_at) VALUES(?,?,?,'OPEN',now(),now())", id, tenant, trailerId); return id;
    }
    void allocation(UUID tenant, UUID manifest, UUID orderId, BigDecimal quantity, Integer pallets, BigDecimal weight) throws SQLException {
        f.db.execute("INSERT INTO manifest_orders VALUES(?,?,?,?,?,?,?,now())", UUID.randomUUID(), tenant, manifest, orderId, quantity, pallets, weight);
    }
    List<String> columns(String table) throws Exception {
        try (var c = runtime.getConnection(); var q = c.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position")) {
            q.setString(1, table); try (var r = q.executeQuery()) { var list = new ArrayList<String>(); while (r.next()) { list.add(r.getString(1)); } return list; }
        }
    }
    long count(Connection c, String table) throws Exception { try (var q = c.createStatement(); var r = q.executeQuery("SELECT count(*) FROM " + table)) { r.next(); return r.getLong(1); } }
    void setting(Connection c, String key, String value) throws Exception { try (var q = c.prepareStatement("SELECT set_config(?,?,true)")) { q.setString(1, key); q.setString(2, value); q.executeQuery().close(); } }
    interface Sql { void run() throws Exception; }
    void rejects(String state, Sql sql) { assertThatThrownBy(sql::run).isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo(state); }
    void failure(String code, Runnable action) { assertThatThrownBy(action::run).isInstanceOf(OrderFailure.class).extracting(e -> ((OrderFailure)e).code).isEqualTo(code); }
}
