package com.trailersaas.identity.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import com.trailersaas.EquipmentFixtures;
import com.trailersaas.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class TenantSettingsPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway; @Autowired DataSource runtime;
    EquipmentFixtures f; UUID a, b;
    @BeforeEach void seed() throws Exception {
        f = new EquipmentFixtures(flyway); a = f.tenant(); b = f.tenant();
        f.execute("INSERT INTO tenant_settings VALUES (?,72,NULL,now(),now(),0),(?,72,NULL,now(),now(),0)", a,b);
    }
    @Test void v9ExactSchemaFkChecksAndBackfillsPreexistingTenants() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("18"); flyway.validate();
        try(var c = runtime.getConnection(); var q = c.createStatement(); var r = q.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='tenant_settings' ORDER BY ordinal_position")) {
            var columns = new java.util.ArrayList<String>(); while(r.next()) { columns.add(r.getString(1)); }
            assertThat(columns).containsExactly("tenant_id","stale_trailer_threshold_hours","default_timezone","created_at","updated_at","version");
            assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        }
        assertThat(f.value("SELECT count(*) FROM pg_constraint WHERE conrelid='tenant_settings'::regclass AND confdeltype='c'")).isEqualTo(0L);
        rejects("23514", () -> f.execute("UPDATE tenant_settings SET stale_trailer_threshold_hours=0 WHERE tenant_id=?", a));
        rejects("23503", () -> f.execute("INSERT INTO tenant_settings VALUES (?,72,NULL,now(),now(),0)", UUID.randomUUID()));
        String schema = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = flyway.getConfiguration().getDataSource();
        var older = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .placeholders(Map.of("runtimeRole", RUNTIME_USERNAME)).target("8").load();
        older.migrate();
        try(var c = dataSource.getConnection(); var q = c.createStatement()) {
            q.execute("SET search_path TO " + schema);
            q.execute("INSERT INTO tenants VALUES ('"+a+"','Preexisting A','CANDIDATE',now(),now()),('"+b+"','Preexisting B','CANDIDATE',now(),now())");
        }
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .placeholders(Map.of("runtimeRole", RUNTIME_USERNAME)).load().migrate();
        try(var c = dataSource.getConnection(); var q = c.createStatement(); var r = q.executeQuery("SELECT tenant_id,stale_trailer_threshold_hours,default_timezone,version FROM " + schema + ".tenant_settings ORDER BY tenant_id")) {
            int count = 0; while(r.next()) { count++; assertThat(r.getInt(2)).isEqualTo(72); assertThat(r.getString(3)).isNull(); assertThat(r.getLong(4)).isZero(); }
            assertThat(count).isEqualTo(2);
        }
    }
    @Test void exactRuntimeGrantsAndNonAdministrativeRole() throws Exception {
        try(var c = runtime.getConnection(); var q = c.createStatement(); var r = q.executeQuery("""
                SELECT c.relrowsecurity, r.rolsuper, r.rolbypassrls, pg_get_userbyid(c.relowner)=current_user,
                  has_schema_privilege(current_user,'public','CREATE'),
                  has_table_privilege(current_user,c.oid,'SELECT'),has_table_privilege(current_user,c.oid,'UPDATE'),
                  has_table_privilege(current_user,c.oid,'INSERT'),has_table_privilege(current_user,c.oid,'DELETE'),
                  (SELECT count(*) FROM aclexplode(c.relacl) WHERE grantee=0)
                FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid='tenant_settings'::regclass
                """)) {
            r.next(); assertThat(r.getBoolean(1)).isTrue();
            for(int i=2;i<=5;i++) { assertThat(r.getBoolean(i)).isFalse(); }
            assertThat(r.getBoolean(6)).isTrue(); assertThat(r.getBoolean(7)).isTrue();
            assertThat(r.getBoolean(8)).isFalse(); assertThat(r.getBoolean(9)).isFalse(); assertThat(r.getInt(10)).isZero();
        }
    }
    @Test void rlsNoContextBootstrapTenantIsolationAndCrossTenantUpdateRejected() throws Exception {
        try(var c = runtime.getConnection()) {
            c.setAutoCommit(false);
            assertThat(count(c)).isZero();
            setting(c,"app.current_user_id",f.user().toString()); assertThat(count(c)).isZero(); c.rollback();
            setting(c,"app.current_tenant_id",a.toString()); assertThat(count(c)).isEqualTo(1);
            assertThat(f.execute(c,"UPDATE tenant_settings SET stale_trailer_threshold_hours=50 WHERE tenant_id=?",b)).isZero();
            assertThat(f.execute(c,"UPDATE tenant_settings SET stale_trailer_threshold_hours=50 WHERE tenant_id=?",a)).isEqualTo(1);
            rejects("42501",()->f.execute(c,"UPDATE tenant_settings SET tenant_id=? WHERE tenant_id=?",UUID.randomUUID(),a));
            c.rollback(); assertThat(count(c)).isZero();
        }
    }
    long count(Connection c) throws Exception {
        try(var q=c.createStatement();var r=q.executeQuery("SELECT count(*) FROM tenant_settings")) { r.next();return r.getLong(1); }
    }
    void setting(Connection c,String key,String value) throws Exception {
        try(var q=c.prepareStatement("SELECT set_config(?,?,true)")) { q.setString(1,key);q.setString(2,value);q.executeQuery().close(); }
    }
    interface Sql { void run() throws Exception; }
    void rejects(String state,Sql sql) { assertThatThrownBy(sql::run).isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo(state); }
}
