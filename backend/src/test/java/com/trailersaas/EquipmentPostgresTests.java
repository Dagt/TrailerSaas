package com.trailersaas;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
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

@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=1","spring.datasource.hikari.minimum-idle=1"})
class EquipmentPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DataSource runtime;
    @Autowired EntityManager manager;
    @Autowired TenantContext context;
    @Autowired TenantTransactionExecutor tenants;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DriverAccountLinkPolicy linkPolicy;
    EquipmentFixtures fixtures;
    UUID a,b,user,driverA,driverB,tractorA,tractorB;
    @BeforeEach void seed() throws Exception {
        fixtures=new EquipmentFixtures(flyway); a=fixtures.tenant(); b=fixtures.tenant(); user=fixtures.user();
        driverA=fixtures.driver(a,null); driverB=fixtures.driver(b,null);
        tractorA=fixtures.tractor(a,"a1"); tractorB=fixtures.tractor(b,"b1");
    }
    @Test void v7CreatesExactlyDriversAndTractorsAndValidatesPriorChecksums() {
        flyway.validate();
        assertThat(flyway.info().applied()).extracting(i->i.getVersion().getVersion()).containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27");
        assertThat(manager.createNativeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'",String.class).getResultList())
                .containsExactlyInAnyOrder("flyway_schema_history","users","tenants","tenant_memberships","role_assignments",
                        "customers","physical_sites","customer_sites","operational_locations","membership_invitations",
                        "invitation_role_assignments","drivers","tractors","trailers","trailer_qr_identities","tenant_settings", "orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders", "trips", "trip_stops", "trip_stop_orders", "trip_assignment_events", "trip_operational_events", "trip_driver_updates", "trailer_movement_events", "trailer_couplings", "processed_client_operations", "trip_stop_events", "documents", "document_links", "delivery_evidence", "shifts", "shift_corrections", "inspections", "fuel_events", "operational_exceptions", "operational_exception_documents", "order_fulfillment_events", "yard_discrepancy_reports", "trailer_oos_evidence", "yard_checks", "yard_check_items", "yard_check_reconciliations", "alerts", "outbox_jobs", "alert_recipients", "user_devices", "alert_deliveries", "stale_trailer_scans");
        assertThat(manager.createNativeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='drivers' ORDER BY ordinal_position",String.class).getResultList())
                .containsExactly("id","tenant_id","user_id","name","license_number","medical_certificate_expires","active","created_at","updated_at","version");
        assertThat(manager.createNativeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='tractors' ORDER BY ordinal_position",String.class).getResultList())
                .containsExactly("id","tenant_id","operational_number","operational_number_normalized","active","created_at","updated_at","version");
        assertThat(manager.createNativeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('drivers'::regclass,'tractors'::regclass) AND confdeltype='c'").getSingleResult())
                .isEqualTo(0L);
    }
    @ParameterizedTest @ValueSource(strings={"drivers","tractors"})
    void grantsAreExactAndRuntimeCannotOwnBypassCreateDeleteOrGrantPublic(String table) throws Exception {
        try(var c=runtime.getConnection();var q=c.prepareStatement("""
                SELECT r.rolsuper,r.rolbypassrls,pg_get_userbyid(c.relowner)=current_user,
                    has_schema_privilege(current_user,'public','CREATE'),
                    has_table_privilege(current_user,c.oid,'SELECT'),has_table_privilege(current_user,c.oid,'INSERT'),
                    has_table_privilege(current_user,c.oid,'UPDATE'),has_table_privilege(current_user,c.oid,'DELETE'),
                    c.relrowsecurity,(SELECT count(*) FROM aclexplode(c.relacl) WHERE grantee=0)
                FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid=?::regclass
                """)) {
            q.setString(1,table); try(var r=q.executeQuery()) {
                r.next(); for(int i=1;i<=4;i++) { assertThat(r.getBoolean(i)).isFalse(); }
                for(int i=5;i<=7;i++) { assertThat(r.getBoolean(i)).isTrue(); }
                assertThat(r.getBoolean(8)).isFalse(); assertThat(r.getBoolean(9)).isTrue(); assertThat(r.getInt(10)).isZero();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"drivers","tractors"})
    void rlsGuardsUnfilteredReadsAndExcludesBootstrapAndMissingContext(String table) {
        for(var tenant:List.of(a,b)) {
            tenants.execute(tenant,()-> {
                assertThat(manager.createNativeQuery("SELECT DISTINCT tenant_id FROM "+table,UUID.class).getResultList()).containsExactly(tenant); return null;
            });
        }
        new TransactionTemplate(transactionManager).execute(status-> {
            assertThat(manager.createNativeQuery("SELECT id FROM "+table).getResultList()).isEmpty();
            context.establishBootstrapUser(new AuthenticatedUserPrincipal(user,"fixture-"+user,null,null));
            assertThat(manager.createNativeQuery("SELECT id FROM "+table).getResultList()).isEmpty(); return null;
        });
    }
    @ParameterizedTest @ValueSource(strings={"drivers","tractors"})
    void crossTenantUpdatesInsertsAndHardDeletesFailEvenWithoutApplicationPredicates(String table) throws Exception {
        try(var c=runtime.getConnection()) {
            pin(c,a);
            assertThat(fixtures.execute(c,"UPDATE "+table+" SET active=false WHERE tenant_id=?",b)).isZero();
            rejects("42501",()->fixtures.execute(c,"UPDATE "+table+" SET tenant_id=? WHERE tenant_id=?",b,a));
            c.rollback(); pin(c,a);
            String column=table.equals("drivers")?"name":"operational_number";
            rejects("42501",()->fixtures.execute(c,"INSERT INTO "+table+"(id,tenant_id,"+column+",created_at,updated_at) VALUES(?,?,'cross',now(),now())",UUID.randomUUID(),b));
            c.rollback(); pin(c,a);
            rejects("42501",()->fixtures.execute(c,"DELETE FROM "+table+" WHERE tenant_id=?",a));
            c.rollback();
        }
    }
    @Test void optionalUserAndTenantScopedLinkUniquenessPreserveGlobalIdentity() throws Exception {
        fixtures.driver(a,null); fixtures.driver(a,null);
        fixtures.driver(a,user); fixtures.driver(b,user);
        rejects("23505",()->fixtures.driver(a,user));
        rejects("23503",()->fixtures.driver(a,UUID.randomUUID()));
        rejects("23503",()->fixtures.driver(UUID.randomUUID(),null));
        assertThat(fixtures.value("SELECT active FROM drivers WHERE id=?",driverA)).isEqualTo(true);
        assertThat(fixtures.value("SELECT version FROM drivers WHERE id=?",driverA)).isEqualTo(0L);
    }
    @Test void generatedNormalizationPreservesDisplayAndOnlyAsciiEquivalences() throws Exception {
        var id=fixtures.tractor(a," t114 ");
        assertThat(fixtures.value("SELECT operational_number FROM tractors WHERE id=?",id)).isEqualTo(" t114 ");
        assertThat(fixtures.value("SELECT operational_number_normalized FROM tractors WHERE id=?",id)).isEqualTo("T114");
        rejects("23505",()->fixtures.tractor(a,"T114"));
        fixtures.tractor(a,"bh  t114"); rejects("23505",()->fixtures.tractor(a,"BH T114"));
        fixtures.tractor(a,"T-114"); fixtures.tractor(a,"T0114");
        fixtures.tractor(b,"T114"); // Tenant-scoped uniqueness.
        var unicode=fixtures.tractor(a," äéßı café ");
        assertThat(fixtures.value("SELECT operational_number_normalized FROM tractors WHERE id=?",unicode)).isEqualTo("äéßı CAFé");
        fixtures.tractor(a,"ÄÉSSI CAFÉ");
        var nbsp=fixtures.tractor(a,"\u00a0t114\u00a0");
        assertThat(fixtures.value("SELECT operational_number_normalized FROM tractors WHERE id=?",nbsp)).isEqualTo("\u00a0T114\u00a0");
        fixtures.execute("UPDATE tractors SET operational_number=' x  02 ' WHERE id=?",id);
        assertThat(fixtures.value("SELECT operational_number_normalized FROM tractors WHERE id=?",id)).isEqualTo("X 02");
        rejects("23505",()->fixtures.execute("UPDATE tractors SET operational_number='T-114' WHERE id=?",id));
        assertThat(fixtures.value("SELECT attgenerated FROM pg_attribute WHERE attrelid='tractors'::regclass AND attname='operational_number_normalized'")).isEqualTo("s");
        assertThat(fixtures.value("SELECT c.collname FROM pg_attribute a JOIN pg_collation c ON c.oid=a.attcollation WHERE a.attrelid='tractors'::regclass AND a.attname='operational_number_normalized'")).isEqualTo("C");
        rejects("428C9",()->fixtures.execute("UPDATE tractors SET operational_number_normalized='override' WHERE id=?",id));
    }
    @ParameterizedTest @ValueSource(strings={"T\t114","T\n114","T\r114","T\u0001114","T\u007f114","T\u0085114","T\u009f114","","   "})
    void sqlRejectsControlsAndEmptyNormalizedKeysWithoutAppValidation(String value) {
        rejects("23514",()->fixtures.tractor(a,value));
    }
    @Test void accountLinkPolicyRequiresExistingMatchingTenantContextAndFreshEligibility() throws Exception {
        var membership=fixtures.membership(a,user,"ACTIVE","DRIVER");
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).execute(status->{linkPolicy.requireEligible(a,user);return null;}))
                .isInstanceOf(DriverAccountLinkRejectedException.class);
        tenants.execute(a,()->{linkPolicy.requireEligible(a,user);return null;});
        assertThatThrownBy(()->tenants.execute(b,()->{linkPolicy.requireEligible(a,user);return null;}))
                .isInstanceOf(DriverAccountLinkRejectedException.class);
        fixtures.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
        assertThatThrownBy(()->tenants.execute(a,()->{linkPolicy.requireEligible(a,user);return null;}))
                .isInstanceOf(DriverAccountLinkRejectedException.class);
        fixtures.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?",membership);
        fixtures.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        assertThatThrownBy(()->tenants.execute(a,()->{linkPolicy.requireEligible(a,user);return null;}))
                .isInstanceOf(DriverAccountLinkRejectedException.class);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void oneConnectionPoolClearsTenantAfterCommitOrRollback(boolean rollback) {
        var template=new TransactionTemplate(transactionManager);
        int pid=template.execute(s->((Number)manager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue());
        template.execute(s->{context.establishTenant(a); if(rollback){s.setRollbackOnly();} return null;});
        template.execute(s->{
            assertThat(((Number)manager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue()).isEqualTo(pid);
            assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('app.current_tenant_id',true),'')").getSingleResult()).isNull();
            assertThat(manager.createNativeQuery("SELECT id FROM drivers").getResultList()).isEmpty();
            assertThat(manager.createNativeQuery("SELECT id FROM tractors").getResultList()).isEmpty(); return null;
        });
    }
    private void pin(Connection c,UUID tenant) throws SQLException {
        c.setAutoCommit(false);try(var q=c.prepareStatement("SELECT set_config('app.current_tenant_id',?,true)")){q.setString(1,tenant.toString());q.executeQuery().close();}
    }
    private void rejects(String state,SqlAction action) {
        assertThatThrownBy(action::run).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo(state));
    }
    @FunctionalInterface interface SqlAction { void run() throws Exception; }
}
