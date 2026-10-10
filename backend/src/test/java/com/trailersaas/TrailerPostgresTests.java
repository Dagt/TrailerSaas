package com.trailersaas;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import com.trailersaas.identity.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=1","spring.datasource.hikari.minimum-idle=1"})
class TrailerPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway; @Autowired DataSource runtime; @Autowired EntityManager manager;
    @Autowired TenantContext context; @Autowired TrailerQrLookupContext lookup;
    @Autowired TenantTransactionExecutor tenants; @Autowired PlatformTransactionManager transactions;
    TrailerFixtures f; UUID a,b,ta,tb,qa,qb,user;
    final String tokenA="AAAAAAAAAAAAAAAAAAAAAA",tokenB="BBBBBBBBBBBBBBBBBBBBBA";
    @BeforeEach void seed() throws Exception {
        f=new TrailerFixtures(flyway); a=f.db.tenant();b=f.db.tenant();user=f.db.user();
        ta=f.trailer(a,"a",null);tb=f.trailer(b,"b",null);
        qa=f.qr(a,ta,randomToken());qb=f.qr(b,tb,randomToken());
    }
    String randomToken() { byte[] bytes=new byte[16];new java.security.SecureRandom().nextBytes(bytes);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    String token(UUID id) throws Exception { return (String)f.db.value("SELECT public_token FROM trailer_qr_identities WHERE id=?",id); }
    @Test void v8AddsExactlyTwoTablesAndExactColumnsWithPreservedChecksums() {
        flyway.validate();
        assertThat(flyway.info().applied()).extracting(i->i.getVersion().getVersion()).containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15", "16", "17");
        assertThat(manager.createNativeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'",String.class).getResultList())
            .containsExactlyInAnyOrder("flyway_schema_history","users","tenants","tenant_memberships","role_assignments","customers","physical_sites",
                    "customer_sites","operational_locations","membership_invitations","invitation_role_assignments","drivers","tractors","trailers","trailer_qr_identities","tenant_settings", "orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders", "trips", "trip_stops", "trip_stop_orders", "trip_assignment_events", "trip_operational_events", "trip_driver_updates", "trailer_movement_events", "trailer_couplings", "processed_client_operations");
        assertThat(manager.createNativeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='trailers' ORDER BY ordinal_position",String.class).getResultList())
            .containsExactly("id","tenant_id","operational_number","operational_number_normalized","designated_customer_id","trailer_type","capability_notes",
                    "location_state","current_location_id","last_known_location_id","last_seen_at","last_driver_id","load_state","serviceability_state","usage_purpose","active","created_at","updated_at","version");
        assertThat(manager.createNativeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='trailer_qr_identities' ORDER BY ordinal_position",String.class).getResultList())
            .containsExactly("id","tenant_id","trailer_id","public_token","active","issued_at","revoked_at");
        assertThat(manager.createNativeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('trailers'::regclass,'trailer_qr_identities'::regclass) AND confdeltype='c'").getSingleResult()).isEqualTo(0L);
    }
    @ParameterizedTest @ValueSource(strings={"trailers","trailer_qr_identities"})
    void exactRuntimeGrantsRlsAndNonAdministrativePrivileges(String table) throws Exception {
        try(var c=runtime.getConnection();var q=c.prepareStatement("""
                SELECT r.rolsuper,r.rolbypassrls,pg_get_userbyid(c.relowner)=current_user,
                       has_schema_privilege(current_user,'public','CREATE'),
                       has_table_privilege(current_user,c.oid,'SELECT'),has_table_privilege(current_user,c.oid,'INSERT'),
                       has_table_privilege(current_user,c.oid,'UPDATE'),has_table_privilege(current_user,c.oid,'DELETE'),
                       c.relrowsecurity,(SELECT count(*) FROM aclexplode(c.relacl) WHERE grantee=0)
                FROM pg_class c JOIN pg_roles r ON r.rolname=current_user WHERE c.oid=?::regclass
                """)) {
            q.setString(1,table);try(var r=q.executeQuery()) {
                r.next();for(int i=1;i<=4;i++) { assertThat(r.getBoolean(i)).isFalse(); }
                for(int i=5;i<=7;i++) { assertThat(r.getBoolean(i)).isTrue(); }
                assertThat(r.getBoolean(8)).isFalse();assertThat(r.getBoolean(9)).isTrue();assertThat(r.getInt(10)).isZero();
                assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"trailers","trailer_qr_identities"})
    void tenantRlsGuardsReadsWritesAndExcludesBootstrap(String table) throws Exception {
        for(var tenant:List.of(a,b)) { tenants.execute(tenant,()->{assertThat(ids("SELECT DISTINCT tenant_id FROM "+table)).containsExactly(tenant);return null;}); }
        new TransactionTemplate(transactions).execute(s->{
            assertThat(ids("SELECT id FROM "+table)).isEmpty();
            context.establishBootstrapUser(new AuthenticatedUserPrincipal(user,"fixture-"+user,null,null));
            assertThat(ids("SELECT id FROM "+table)).isEmpty();return null;
        });
        try(var c=runtime.getConnection()) {
            pin(c,a);
            assertThat(f.db.execute(c,"UPDATE "+table+" SET active=active WHERE tenant_id=?",b)).isZero();
            reject("42501",()->f.db.execute(c,"UPDATE "+table+" SET tenant_id=? WHERE tenant_id=?",b,a));
            c.rollback();pin(c,a);
            if(table.equals("trailers")) {
                reject("42501",()->f.db.execute(c,"INSERT INTO trailers(id,tenant_id,operational_number,location_state,load_state,serviceability_state,created_at,updated_at) VALUES(?,?,'cross','LOCATION_UNKNOWN','UNKNOWN','IN_SERVICE',now(),now())",UUID.randomUUID(),b));
            } else {
                reject("42501",()->f.db.execute(c,"INSERT INTO trailer_qr_identities VALUES(?,?,?,?,true,now(),NULL)",UUID.randomUUID(),b,tb,randomToken()));
            }
            c.rollback();pin(c,a);reject("42501",()->f.db.execute(c,"DELETE FROM "+table+" WHERE tenant_id=?",a));c.rollback();
        }
    }
    @Test void generatedNumberPreservesDisplayNonAsciiNbspAndTenantScopedIdentity() throws Exception {
        var id=f.trailer(a," t114 ",null);
        assertThat(f.db.value("SELECT operational_number FROM trailers WHERE id=?",id)).isEqualTo(" t114 ");
        assertThat(f.db.value("SELECT operational_number_normalized FROM trailers WHERE id=?",id)).isEqualTo("T114");
        reject("23505",()->f.trailer(a,"T114",null));
        f.trailer(a,"bh  t114",null);reject("23505",()->f.trailer(a,"BH T114",null));
        f.trailer(a,"T-114",null);f.trailer(a,"T0114",null);f.trailer(b,"T114",null);
        var unicode=f.trailer(a," äéßı café ",null);
        assertThat(f.db.value("SELECT operational_number_normalized FROM trailers WHERE id=?",unicode)).isEqualTo("äéßı CAFé");
        var nbsp=f.trailer(a,"\u00a0t114\u00a0",null);
        assertThat(f.db.value("SELECT operational_number_normalized FROM trailers WHERE id=?",nbsp)).isEqualTo("\u00a0T114\u00a0");
        assertThat(f.db.value("SELECT attgenerated FROM pg_attribute WHERE attrelid='trailers'::regclass AND attname='operational_number_normalized'")).isEqualTo("s");
        assertThat(f.db.value("SELECT c.collname FROM pg_attribute a JOIN pg_collation c ON c.oid=a.attcollation WHERE a.attrelid='trailers'::regclass AND a.attname='operational_number_normalized'")).isEqualTo("C");
        reject("428C9",()->f.db.execute("UPDATE trailers SET operational_number_normalized='override' WHERE id=?",id));
        reject("23505",()->f.db.execute("UPDATE trailers SET operational_number='T-114' WHERE id=?",id));
    }
    @ParameterizedTest @ValueSource(strings={"T\t114","T\n114","T\r114","T\u0001114","T\u007f114","T\u0085114","T\u009f114","","   "})
    void sqlRejectsControlsAndEmptyKeyWithoutAppValidation(String number) { reject("23514",()->f.trailer(a,number,null)); }
    @Test void allNullableReferencesAreTenantSafe() throws Exception {
        for(var column:List.of("designated_customer_id","current_location_id","last_known_location_id","last_driver_id")) {
            UUID own=column.contains("customer")?f.customer(a):column.contains("driver")?f.db.driver(a,null):f.location(a);
            UUID foreign=column.contains("customer")?f.customer(b):column.contains("driver")?f.db.driver(b,null):f.location(b);
            String extra=column.equals("current_location_id")?",location_state='AT_LOCATION'":"";
            f.db.execute("UPDATE trailers SET "+column+"=?"+extra+" WHERE id=?",own,ta);
            reject("23503",()->f.db.execute("UPDATE trailers SET "+column+"=? WHERE id=?",foreign,ta));
            reject("23503",()->f.db.execute("UPDATE trailers SET "+column+"=? WHERE id=?",UUID.randomUUID(),ta));
            String clear=column.equals("current_location_id")?"":column+"=NULL,";
            f.db.execute("UPDATE trailers SET "+clear+"location_state='LOCATION_UNKNOWN',current_location_id=NULL WHERE id=?",ta);
        }
    }
    @Test void stateChecksEnforceOnlyAcceptedLocationInvariants() throws Exception {
        reject("23514",()->f.db.execute("UPDATE trailers SET location_state='AT_LOCATION' WHERE id=?",ta));
        var location=f.location(a);
        reject("23514",()->f.db.execute("UPDATE trailers SET current_location_id=? WHERE id=?",location,ta));
        f.db.execute("UPDATE trailers SET location_state='IN_TRANSIT',current_location_id=? WHERE id=?",location,ta);
        f.db.execute("UPDATE trailers SET current_location_id=NULL WHERE id=?",ta);
        for(var column:List.of("location_state","load_state","serviceability_state","usage_purpose")) {
            reject("23514",()->f.db.execute("UPDATE trailers SET "+column+"='INVALID' WHERE id=?",ta));
        }
        for(var state:List.of("EMPTY","LOADED","UNKNOWN")) { f.db.execute("UPDATE trailers SET load_state=? WHERE id=?",state,ta); }
        for(var state:List.of("IN_SERVICE","OUT_OF_SERVICE")) { f.db.execute("UPDATE trailers SET serviceability_state=? WHERE id=?",state,ta); }
        for(var state:List.of("DELIVERY","SHUTTLE","WIP_STORAGE","TOOLING_STORAGE","OTHER")) { f.db.execute("UPDATE trailers SET usage_purpose=? WHERE id=?",state,ta); }
    }
    @Test void qrConstraintsPreserveRevokedHistoryAndTrailerIdentity() throws Exception {
        reject("23505",()->f.qr(a,ta,randomToken()));
        reject("23505",()->f.qr(b,f.trailer(b,"extra",null),token(qa)));
        reject("23503",()->f.qr(b,ta,randomToken()));
        reject("23514",()->f.db.execute("UPDATE trailer_qr_identities SET active=false WHERE id=?",qa));
        reject("23514",()->f.db.execute("UPDATE trailer_qr_identities SET revoked_at=now() WHERE id=?",qa));
        reject("23514",()->f.db.execute("UPDATE trailer_qr_identities SET active=false,revoked_at=issued_at-interval '1 second' WHERE id=?",qa));
        f.db.execute("UPDATE trailer_qr_identities SET active=false,revoked_at=now() WHERE id=?",qa);
        f.qr(a,ta,randomToken());
        assertThat(f.db.value("SELECT count(*) FROM trailer_qr_identities WHERE trailer_id=?",ta)).isEqualTo(2L);
        assertThat(f.db.value("SELECT id FROM trailers WHERE id=?",ta)).isEqualTo(ta);
    }
    @Test void exactTokenContextIsSelectOnlyAndCannotSeeTrailerBeforeDerivedTenant() throws Exception {
        String token=token(qa);
        new TransactionTemplate(transactions).execute(s->{
            assertThat(ids("SELECT id FROM trailer_qr_identities")).isEmpty();
            lookup.establish(token);
            assertThat(ids("SELECT id FROM trailer_qr_identities")).containsExactly(qa);
            assertThat(ids("SELECT id FROM trailer_qr_identities WHERE id='"+qb+"'")).isEmpty();
            assertThat(ids("SELECT id FROM trailers")).isEmpty();
            context.establishTenant(a);
            assertThat(ids("SELECT id FROM trailer_qr_identities")).containsExactly(qa);
            assertThat(ids("SELECT id FROM trailers")).containsExactly(ta);
            return null;
        });
        try(var c=runtime.getConnection()) {
            c.setAutoCommit(false);setting(c,"app.current_trailer_qr_token",token);
            assertThat(f.db.execute(c,"UPDATE trailer_qr_identities SET active=false,revoked_at=now() WHERE id=?",qa)).isZero();
            reject("42501",()->f.db.execute(c,"INSERT INTO trailer_qr_identities VALUES(?,?,?,?,true,now(),NULL)",UUID.randomUUID(),a,ta,randomToken()));
            c.rollback();
            c.setAutoCommit(false);setting(c,"app.current_trailer_qr_token",token);
            reject("42501",()->f.db.execute(c,"DELETE FROM trailer_qr_identities WHERE id=?",qa));
            c.rollback();
        }
        f.db.execute("UPDATE trailer_qr_identities SET active=false,revoked_at=now() WHERE id=?",qa);
        new TransactionTemplate(transactions).execute(s->{lookup.establish(token);assertThat(ids("SELECT id FROM trailer_qr_identities")).isEmpty();return null;});
        new TransactionTemplate(transactions).execute(s->{lookup.establish(randomToken());assertThat(ids("SELECT id FROM trailer_qr_identities")).isEmpty();return null;});
    }
    @Test void tenantAPlusTenantBTokenCannotUseCrossTenantQrLookupPolicy() throws Exception {
        String foreign=token(qb);
        new TransactionTemplate(transactions).execute(s->{
            context.establishTenant(a);
            // Deliberately bypass the Java guard to exercise PostgreSQL defense-in-depth.
            rawSetting("app.current_trailer_qr_token",foreign);
            assertThat(ids("SELECT id FROM trailer_qr_identities")).containsExactly(qa);
            assertThat(ids("SELECT id FROM trailer_qr_identities WHERE id='"+qb+"'")).isEmpty();
            assertThat(ids("SELECT id FROM trailers")).containsExactly(ta);
            return null;
        });
    }
    @ParameterizedTest @ValueSource(strings={"app.current_invitation_hash","app.current_user_id"})
    void invitationOrBootstrapUserPlusQrTokenCannotUseSpecialLookup(String mode) throws Exception {
        String exact=token(qb);
        new TransactionTemplate(transactions).execute(s->{
            rawSetting(mode,mode.equals("app.current_user_id")?user.toString():"a".repeat(64));
            rawSetting("app.current_trailer_qr_token",exact);
            assertThat(ids("SELECT id FROM trailer_qr_identities")).isEmpty();
            assertThat(ids("SELECT id FROM trailers")).isEmpty();
            return null;
        });
    }
    @Test void derivedTenantDisablesSpecialPolicyEvenIfTokenIsSubsequentlyChangedBySql() throws Exception {
        String first=token(qa),foreign=token(qb);
        new TransactionTemplate(transactions).execute(s->{
            lookup.establish(first);
            assertThat(ids("SELECT tenant_id FROM trailer_qr_identities")).containsExactly(a);
            context.establishTenant(a);
            rawSetting("app.current_trailer_qr_token",foreign);
            // Only the normal Tenant policy can apply now; B never becomes visible.
            assertThat(ids("SELECT id FROM trailer_qr_identities")).containsExactly(qa);
            assertThat(ids("SELECT id FROM trailers")).containsExactly(ta);
            return null;
        });
    }
    @Test void pooledPhysicalConnectionClearsQrAndTenantAfterCommitAndRollback() throws Exception {
        String token=token(qa);long pid=pid();
        for(boolean rollback:List.of(false,true)) {
            new TransactionTemplate(transactions).execute(s->{
                lookup.establish(token);context.establishTenant(a);assertThat(pid()).isEqualTo(pid);
                if(rollback) { s.setRollbackOnly(); } return null;
            });
            new TransactionTemplate(transactions).execute(s->{
                assertThat(pid()).isEqualTo(pid);
                for(var key:List.of("app.current_trailer_qr_token","app.current_tenant_id")) {
                    assertThat(manager.createNativeQuery("SELECT NULLIF(current_setting('"+key+"',true),'')").getSingleResult()).isNull();
                }
                assertThat(ids("SELECT id FROM trailer_qr_identities")).isEmpty();assertThat(ids("SELECT id FROM trailers")).isEmpty();return null;
            });
        }
    }
    @Test void qrContextRejectsMixedOrChangingModes() throws Exception {
        String first=token(qa),second=token(qb);
        new TransactionTemplate(transactions).execute(s->{
            lookup.establish(first);lookup.establish(first);
            assertThatThrownBy(()->lookup.establish(second)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->context.establishBootstrapUser(new AuthenticatedUserPrincipal(user,"u",null,null))).isInstanceOf(IllegalStateException.class);
            return null;
        });
        tenants.execute(a,()->{assertThatThrownBy(()->lookup.establish(first)).isInstanceOf(IllegalStateException.class);return null;});
        tenants.execute(a,()->{assertThatThrownBy(()->context.establishTenant(b)).isInstanceOf(IllegalStateException.class);return null;});
        new TransactionTemplate(transactions).execute(s->{
            context.establishBootstrapUser(new AuthenticatedUserPrincipal(user,"u",null,null));
            assertThatThrownBy(()->lookup.establish(first)).isInstanceOf(IllegalStateException.class);
            return null;
        });
        new TransactionTemplate(transactions).execute(s->{
            rawSetting("app.current_invitation_hash","a".repeat(64));
            assertThatThrownBy(()->lookup.establish(first)).isInstanceOf(IllegalStateException.class);
            return null;
        });
        new TransactionTemplate(transactions).execute(s->{
            lookup.establish(first);context.establishTenant(a);
            assertThatThrownBy(()->context.establishTenant(b)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->lookup.establish(second)).isInstanceOf(IllegalStateException.class);
            return null;
        });
    }
    void rawSetting(String key,String value) {
        manager.unwrap(org.hibernate.Session.class).doWork(c->setting(c,key,value));
    }
    long pid() { return ((Number)manager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).longValue(); }
    List<UUID> ids(String sql) { return manager.createNativeQuery(sql,UUID.class).getResultList(); }
    void pin(Connection c,UUID tenant) throws Exception { c.setAutoCommit(false);setting(c,"app.current_tenant_id",tenant.toString()); }
    void setting(Connection c,String key,String value) throws SQLException {
        try(var q=c.prepareStatement("SELECT set_config(?,?,true)")) {q.setString(1,key);q.setString(2,value);q.executeQuery().close();}
    }
    interface SqlAction {void run() throws Exception;}
    void reject(String state,SqlAction action) {
        assertThatThrownBy(action::run).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo(state));
    }
}
