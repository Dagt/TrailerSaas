package com.trailersaas.orders.internal;

import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class OrderRequestRevisionPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DataSource runtime;
    @Autowired OrderMutationService mutations;
    @Autowired ManifestService manifests;
    OrderFixtures f;
    UUID tenant,other,customer,user,order;
    AuthenticatedUserPrincipal actor;
    @BeforeEach void seed() throws Exception {
        f=new OrderFixtures(flyway);tenant=f.db.tenant();other=f.db.tenant();customer=f.customer(tenant);user=f.db.user();
        f.db.execute("update tenants set status='ACTIVE' where id=?",tenant);f.db.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
        actor=new AuthenticatedUserPrincipal(user,"fixture-"+user,null,null);order=f.order(tenant,customer,user);f.stop(tenant,order,1,null,null,"{\"name\":\"Original request\"}");
    }
    void setting(Connection c,UUID tenant) throws Exception {
        try(var q=c.prepareStatement("select set_config('app.current_tenant_id',?,true)")){q.setString(1,tenant.toString());try(var r=q.executeQuery()){assertThat(r.next()).isTrue();}}
    }
    void revise(){mutations.patch(actor,tenant,order,Map.of("expectedVersion",0,"cargoLines",List.of(Map.of("description","Corrected cargo"))));}
    long count(Connection c) throws Exception {try(var q=c.prepareStatement("select count(*) from order_request_revisions where order_id=?")){q.setObject(1,order);try(var r=q.executeQuery()){r.next();return r.getLong(1);}}}
    @Test void realRuntimeRlsFencesRowsAcrossTenantAndConnectionReuse() throws Exception {
        revise();flyway.validate();
        try(var c=runtime.getConnection()) {
            c.setAutoCommit(false);assertThat(count(c)).isZero();
            setting(c,other);assertThat(count(c)).isZero();c.rollback();
            setting(c,tenant);assertThat(count(c)).isEqualTo(1);c.commit();
            assertThat(count(c)).isZero();c.rollback();
            try(var q=c.createStatement();var r=q.executeQuery("select rolsuper,rolbypassrls from pg_roles where rolname=current_user")){r.next();assertThat(r.getBoolean(1)).isFalse();assertThat(r.getBoolean(2)).isFalse();}
        }
    }
    @Test void revisionHistoryIsAppendOnlyForRuntime() throws Exception {
        revise();
        for(var sql:List.of("update order_request_revisions set order_version=2 where order_id=?","delete from order_request_revisions where order_id=?")) {
            try(var c=runtime.getConnection()){c.setAutoCommit(false);setting(c,tenant);assertThatThrownBy(()->f.db.execute(c,sql,order)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("42501"));c.rollback();}
        }
        assertThat(f.db.value("select count(*) from order_request_revisions where order_id=?",order)).isEqualTo(1L);
    }
    @Test void sqlRejectsMalformedHistoryAndCrossTenantParents() throws Exception {
        var otherOrder=f.order(other,f.customer(other),user);
        var sql="insert into order_request_revisions values(?,?,?,?,?,?::jsonb,'{}'::jsonb,now())";
        for(var mode:List.of("payload","version","parent","tenant")) try(var c=runtime.getConnection()) {
            c.setAutoCommit(false);setting(c,tenant);
            assertThatThrownBy(()->f.db.execute(c,sql,UUID.randomUUID(),mode.equals("tenant")?other:tenant,mode.equals("parent")?otherOrder:order,user,mode.equals("version")?0:1,mode.equals("payload")?"[]":"{}"))
                .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo(mode.equals("parent")?"23503":mode.equals("tenant")?"42501":"23514"));c.rollback();
        }
    }
    @Test void sqlGuardDoesNotPermitDeletingCommittedRequestedChildren() throws Exception {
        f.db.execute("update orders set status='PLANNED' where id=?",order);
        try(var c=runtime.getConnection()){c.setAutoCommit(false);setting(c,tenant);assertThatThrownBy(()->f.db.execute(c,"delete from order_stops where order_id=?",order)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));c.rollback();}
        assertThat(f.db.value("select count(*) from order_stops where order_id=?",order)).isEqualTo(1L);
    }
    @Test void temporaryNamesCannotBypassCommittedChildGuard() throws Exception {
        f.db.execute("update orders set status='PLANNED' where id=?",order);
        try(var c=runtime.getConnection()) {
            c.setAutoCommit(false);setting(c,tenant);
            try {
                f.db.execute(c,"create temporary table orders as select * from public.orders where id=?",order);
                f.db.execute(c,"update pg_temp.orders set status='OPEN'");
                assertThatThrownBy(()->f.db.execute(c,"delete from public.order_stops where order_id=?",order))
                    .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {c.rollback();}
        }
        assertThat(f.db.value("select count(*) from public.order_stops where order_id=?",order)).isEqualTo(1L);
    }
    @Test void isolatedSchemaGuardUsesItsOwnParentRatherThanThePublicOrder() throws Exception {
        var schema="revision_"+UUID.randomUUID().toString().replace("-","");
        var source=flyway.getConfiguration().getDataSource();
        var isolated=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .placeholders(Map.of("runtimeRole",RUNTIME_USERNAME)).load();
        try {
            isolated.migrate();isolated.validate();
            f.db.execute("grant usage on schema "+schema+" to "+RUNTIME_USERNAME);
            for(var table:List.of("tenants","users","customers","orders")) {
                var id=table.equals("tenants")?tenant:table.equals("users")?user:table.equals("customers")?customer:order;
                f.db.execute("insert into "+schema+"."+table+" select * from public."+table+" where id=?",id);
            }
            f.db.execute("insert into "+schema+".order_stops select * from public.order_stops where order_id=?",order);
            f.db.execute("update "+schema+".orders set status='PLANNED' where id=?",order);
            try(var c=runtime.getConnection()) {
                c.setAutoCommit(false);setting(c,tenant);
                try {assertThatThrownBy(()->f.db.execute(c,"delete from "+schema+".order_stops where order_id=?",order))
                    .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));}
                finally {c.rollback();}
            }
            assertThat(f.db.value("select count(*) from "+schema+".order_stops where order_id=?",order)).isEqualTo(1L);
            assertThat(f.db.value("select status from public.orders where id=?",order)).isEqualTo("OPEN");
        } finally {f.db.execute("drop schema if exists "+schema+" cascade");}
    }
    @Test void requiredHistoryFailureRollsBackChildrenReferencesAndVersion() throws Exception {
        var before=f.db.value("select jsonb_agg(to_jsonb(s))::text from order_stops s where order_id=?",order);
        f.db.execute("create function issue59_fail_revision() returns trigger language plpgsql as $$ begin if NEW.order_id='"+order+"'::uuid then raise exception 'Controlled revision persistence failure'; end if; return NEW; end $$");
        f.db.execute("create trigger issue59_revision_failure before insert on order_request_revisions for each row execute function issue59_fail_revision()");
        try {assertThatThrownBy(()->mutations.patch(actor,tenant,order,Map.of("expectedVersion",0,"poNumber","Must roll back","cargoLines",List.of(Map.of("description","Must roll back")),"requestedStops",List.of(Map.of("sequenceNumber",1,"stopType","DELIVERY","siteSnapshot",Map.of("name","Must roll back")))))).isInstanceOf(RuntimeException.class);}
        finally {f.db.execute("drop trigger issue59_revision_failure on order_request_revisions");f.db.execute("drop function issue59_fail_revision()");}
        assertThat(f.db.value("select version from orders where id=?",order)).isEqualTo(0L);assertThat(f.db.value("select po_number from orders where id=?",order)).isNull();
        assertThat(f.db.value("select count(*) from order_cargo_lines where order_id=?",order)).isEqualTo(0L);assertThat(f.db.value("select count(*) from order_request_revisions where order_id=?",order)).isEqualTo(0L);
        assertThat(f.db.value("select jsonb_agg(to_jsonb(s))::text from order_stops s where order_id=?",order)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void openOrderWithOpenOrHistoricalManifestCannotRewriteItsRequest(boolean closed) throws Exception {
        var manifest=manifests.create(actor,tenant,null);
        manifests.allocate(actor,tenant,manifest.id(),0,order,null,null,null);
        if(closed)manifests.close(actor,tenant,manifest.id(),1);
        assertThatThrownBy(()->mutations.patch(actor,tenant,order,Map.of("expectedVersion",0,"cargoLines",List.of())))
            .isInstanceOfSatisfying(OrderFailure.class,e->assertThat(e.code).isEqualTo("INVALID_STATE_TRANSITION"));
        try(var c=runtime.getConnection()){c.setAutoCommit(false);setting(c,tenant);assertThatThrownBy(()->f.db.execute(c,"delete from order_stops where order_id=?",order)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));c.rollback();}
        assertThat(f.db.value("select count(*) from manifest_orders where order_id=?",order)).isEqualTo(1L);
        assertThat(f.db.value("select version from orders where id=?",order)).isEqualTo(0L);
    }
}
