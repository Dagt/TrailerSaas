package com.trailersaas.orders.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import com.trailersaas.OrderFixtures;
import com.trailersaas.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1"})
class OrderPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DataSource runtime;
    OrderFixtures f;
    UUID a, b, customerA, customerB, user, orderA, orderB, siteA, siteB, locationA, locationB;
    @BeforeEach void seed() throws Exception {
        f = new OrderFixtures(flyway); a = f.db.tenant(); b = f.db.tenant(); user = f.db.user();
        customerA = f.customer(a); customerB = f.customer(b); orderA = f.order(a, customerA, user); orderB = f.order(b, customerB, user);
        siteA = f.site(a, customerA); siteB = f.site(b, customerB); locationA = f.location(a, siteA, customerA); locationB = f.location(b, siteB, customerB);
        f.stop(a, orderA, 1, siteA, locationA, null); f.stop(b, orderB, 1, siteB, locationB, null);
        f.db.execute("INSERT INTO order_cargo_lines VALUES(?,?,?,'Cargo',NULL,NULL,NULL,NULL,now()),(?,?,?,'Cargo',NULL,NULL,NULL,NULL,now())", UUID.randomUUID(), a, orderA, UUID.randomUUID(), b, orderB);
    }
    @Test void exactV10TablesColumnsMilestonesAndNoExecutionTables() throws Exception {
        flyway.validate();
        assertThat(flyway.info().applied()).extracting(i -> i.getVersion().getVersion()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
        try (var c = runtime.getConnection(); var q = c.createStatement(); var r = q.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")) {
            var names = new ArrayList<String>(); while (r.next()) { names.add(r.getString(1)); }
            assertThat(names).containsExactlyInAnyOrder("flyway_schema_history", "users", "tenants", "tenant_memberships", "role_assignments", "customers", "physical_sites", "customer_sites", "operational_locations", "membership_invitations", "invitation_role_assignments", "drivers", "tractors", "trailers", "trailer_qr_identities", "tenant_settings", "orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders", "trips", "trip_stops", "trip_stop_orders", "trip_assignment_events");
            assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        }
        assertThat(columns("orders")).containsExactly("id", "tenant_id", "customer_id", "order_number", "po_number", "shipment_number", "bol_reference", "status", "created_by_user_id", "created_at", "updated_at", "version", "delivered_at", "completed_at", "cancelled_at");
        assertThat(columns("order_cargo_lines")).containsExactly("id", "tenant_id", "order_id", "description", "quantity", "pallet_count", "weight", "weight_unit", "created_at");
        assertThat(columns("order_stops")).containsExactly("id", "tenant_id", "order_id", "sequence_number", "site_id", "location_id", "stop_type", "scheduled_at", "instructions", "site_snapshot", "created_at", "updated_at", "version");
        assertThat(f.db.value("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('orders'::regclass,'order_cargo_lines'::regclass,'order_stops'::regclass) AND confdeltype='c'")).isEqualTo(0L);
        assertThat(f.db.value("SELECT count(*) FROM pg_constraint WHERE conrelid='order_stops'::regclass AND contype='u'")).isEqualTo(3L);
        assertThat(f.db.value("SELECT count(*) FROM pg_constraint WHERE conrelid='order_stops'::regclass AND contype='f'")).isEqualTo(3L);
    }
    @ParameterizedTest @ValueSource(strings = {"orders", "order_cargo_lines", "order_stops"})
    void exactOrderMutationAndImmutableChildGrantsWithRlsAndNoAdministrativePrivileges(String table) throws Exception {
        try (var c = runtime.getConnection(); var q = c.prepareStatement("""
                SELECT c.relrowsecurity,r.rolsuper,r.rolbypassrls,pg_get_userbyid(c.relowner)=current_user,
                    has_schema_privilege(current_user,'public','CREATE'),has_table_privilege(current_user,c.oid,'SELECT'),
                    has_table_privilege(current_user,c.oid,'INSERT'),has_table_privilege(current_user,c.oid,'UPDATE'),
                    has_table_privilege(current_user,c.oid,'DELETE'),(SELECT count(*) FROM aclexplode(c.relacl) WHERE grantee=0)
                FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid=?::regclass
                """)) {
            q.setString(1, table); try (var r = q.executeQuery()) {
                r.next(); assertThat(r.getBoolean(1)).isTrue();
                for (int i = 2; i <= 5; i++) { assertThat(r.getBoolean(i)).isFalse(); }
                assertThat(r.getBoolean(6)).isTrue(); assertThat(r.getBoolean(7)).isTrue();
                assertThat(r.getBoolean(8)).isEqualTo(table.equals("orders")); assertThat(r.getBoolean(9)).isFalse(); assertThat(r.getInt(10)).isZero();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings = {"orders", "order_cargo_lines", "order_stops"})
    void rlsWorksWithoutPredicatesAndDeniesNoContextOrBootstrap(String table) throws Exception {
        try (var c = runtime.getConnection()) {
            c.setAutoCommit(false); assertThat(count(c, table)).isZero();
            setting(c, "app.current_user_id", user.toString()); assertThat(count(c, table)).isZero(); c.rollback();
            setting(c, "app.current_tenant_id", a.toString()); assertThat(ids(c, table)).containsExactly(a);
            setting(c, "app.current_user_id", user.toString()); assertThat(count(c, table)).isZero(); c.rollback();
            setting(c, "app.current_tenant_id", b.toString()); assertThat(ids(c, table)).containsExactly(b); c.rollback();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"orders", "order_cargo_lines", "order_stops"})
    void crossTenantInsertFailsAtRlsEvenWithValidForeignKeys(String table) throws Exception {
        try (var c = runtime.getConnection()) {
            c.setAutoCommit(false); setting(c, "app.current_tenant_id", a.toString());
            rejects("42501", () -> {
                if (table.equals("orders")) { f.db.execute(c, "INSERT INTO orders(id,tenant_id,customer_id,order_number,status,created_by_user_id,created_at,updated_at) VALUES(?,?,?,'Other','OPEN',?,now(),now())", UUID.randomUUID(), b, customerB, user); }
                else if (table.equals("order_cargo_lines")) { f.db.execute(c, "INSERT INTO order_cargo_lines VALUES(?,?,?,'Cargo',NULL,NULL,NULL,NULL,now())", UUID.randomUUID(), b, orderB); }
                else { f.db.execute(c, "INSERT INTO order_stops(id,tenant_id,order_id,sequence_number,stop_type,site_snapshot,created_at,updated_at) VALUES(?,?,?,2,'DELIVERY','{\"name\":\"Site\"}',now(),now())", UUID.randomUUID(), b, orderB); }
            }); c.rollback();
        }
    }
    @Test void compositeParentsSiteLocationAndSequenceAreFinalBarriers() throws Exception {
        rejects("23503", () -> f.db.execute("INSERT INTO order_cargo_lines VALUES(?,?,?,'Cargo',NULL,NULL,NULL,NULL,now())", UUID.randomUUID(), a, orderB));
        rejects("23503", () -> f.stop(a, orderB, 2, siteA, locationA, null));
        rejects("23503", () -> f.stop(a, orderA, 2, siteB, null, null));
        rejects("23503", () -> f.stop(a, orderA, 2, siteA, locationB, null));
        var otherSite = f.site(a, customerA); var otherLocation = f.location(a, otherSite, customerA);
        rejects("23503", () -> f.stop(a, orderA, 2, siteA, otherLocation, null));
        rejects("23503", () -> f.stop(a, orderA, 2, UUID.randomUUID(), null, null));
        rejects("23505", () -> f.stop(a, orderA, 1, siteA, locationA, null));
        var valid = f.stop(a, orderA, 2, siteA, null, "{\"name\":\"Frozen alongside reference\"}");
        assertThat(f.db.value("SELECT site_snapshot->>'name' FROM order_stops WHERE id=?", valid)).isEqualTo("Frozen alongside reference");
    }
    @ParameterizedTest @ValueSource(strings = {"null", "{}", "[]", "\"string\""})
    void adhocSnapshotMustBeNonemptyObjectInSql(String snapshot) {
        rejects("23514", () -> f.stop(a, orderA, 2, null, null, snapshot));
    }
    @Test void adhocAndNormalizedStopsPermitNullableOptionalDataButNoLocationWithoutSite() throws Exception {
        f.stop(a, orderA, 2, null, null, "{\"name\":\"Ad hoc\"}");
        rejects("23514", () -> f.stop(a, orderA, 3, null, locationA, "{\"name\":\"Not an escape\"}"));
        assertThat(f.db.value("SELECT count(*) FROM order_stops WHERE order_id=?", orderA)).isEqualTo(2L);
    }
    @Test void orderNumberNotUniqueAndMilestonesRespectAcceptedLifecycle() throws Exception {
        f.order(a, customerA, user);
        assertThat(f.db.value("SELECT count(*) FROM orders WHERE tenant_id=? AND order_number='Repeated number'", a)).isEqualTo(2L);
        rejects("23503", () -> f.db.execute("UPDATE orders SET customer_id=? WHERE id=?", customerB, orderA));
        rejects("23503", () -> f.db.execute("UPDATE orders SET created_by_user_id=? WHERE id=?", UUID.randomUUID(), orderA));
        rejects("23514", () -> f.db.execute("UPDATE orders SET status='UNKNOWN' WHERE id=?", orderA));
        for (var state : List.of("PLANNED", "IN_PROGRESS", "PARTIALLY_DELIVERED", "CANCELLING")) { f.db.execute("UPDATE orders SET status=? WHERE id=?", state, orderA); }
        rejects("23514", () -> f.db.execute("UPDATE orders SET status='DELIVERED' WHERE id=?", orderA));
        f.db.execute("UPDATE orders SET status='DELIVERED',delivered_at=now() WHERE id=?", orderA);
        rejects("23514", () -> f.db.execute("UPDATE orders SET status='COMPLETED' WHERE id=?", orderA));
        f.db.execute("UPDATE orders SET status='COMPLETED',completed_at=now() WHERE id=?", orderA);
        rejects("23514", () -> f.db.execute("UPDATE orders SET cancelled_at=now() WHERE id=?", orderA));
        f.db.execute("UPDATE orders SET status='CANCELLED',delivered_at=NULL,completed_at=NULL,cancelled_at=now() WHERE id=?", orderA);
        rejects("23503", () -> f.db.execute("DELETE FROM orders WHERE id=?", orderA));
    }
    @ParameterizedTest @ValueSource(strings = {"commit", "rollback"})
    void oneConnectionPoolClearsTenantContextAndPreservesSameBackendPid(String outcome) throws Exception {
        long pid;
        try (var c = runtime.getConnection()) {
            c.setAutoCommit(false); pid = scalar(c, "SELECT pg_backend_pid()"); setting(c, "app.current_tenant_id", a.toString());
            assertThat(ids(c, "orders")).containsExactly(a); if (outcome.equals("commit")) { c.commit(); } else { c.rollback(); }
        }
        try (var c = runtime.getConnection()) {
            assertThat(scalar(c, "SELECT pg_backend_pid()")).isEqualTo(pid);
            assertThat(scalar(c, "SELECT count(*) FROM orders")).isZero();
            try (var q = c.createStatement(); var r = q.executeQuery("SELECT NULLIF(current_setting('app.current_tenant_id',true),'')")) { r.next(); assertThat(r.getString(1)).isNull(); }
        }
    }
    List<String> columns(String table) throws Exception {
        try (var c = runtime.getConnection(); var q = c.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position")) {
            q.setString(1, table); try (var r = q.executeQuery()) { var list = new ArrayList<String>(); while (r.next()) { list.add(r.getString(1)); } return list; }
        }
    }
    List<UUID> ids(Connection c, String table) throws Exception {
        try (var q = c.createStatement(); var r = q.executeQuery("SELECT tenant_id FROM " + table)) { var result = new ArrayList<UUID>(); while (r.next()) { result.add(r.getObject(1, UUID.class)); } return result; }
    }
    long count(Connection c, String table) throws Exception { return scalar(c, "SELECT count(*) FROM " + table); }
    long scalar(Connection c, String sql) throws Exception { try (var q = c.createStatement(); var r = q.executeQuery(sql)) { r.next(); return r.getLong(1); } }
    void setting(Connection c, String key, String value) throws Exception { try (var q = c.prepareStatement("SELECT set_config(?,?,true)")) { q.setString(1, key); q.setString(2, value); q.executeQuery().close(); } }
    interface Sql { void run() throws Exception; }
    void rejects(String state, Sql sql) { assertThatThrownBy(sql::run).isInstanceOf(SQLException.class).extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(state); }
}
