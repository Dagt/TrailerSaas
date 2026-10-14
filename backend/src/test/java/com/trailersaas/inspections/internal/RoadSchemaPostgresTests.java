package com.trailersaas.inspections.internal;

import static org.assertj.core.api.Assertions.*;
import com.trailersaas.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RoadSchemaPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  EquipmentFixtures db;
  UUID tenant,other,user,driver,tractor,trip,stop,document;
  @BeforeEach void seed() throws Exception {
    db=new EquipmentFixtures(flyway);tenant=db.tenant();other=db.tenant();user=db.user();
    driver=db.driver(tenant,user);tractor=db.tractor(tenant,"Fuel-"+UUID.randomUUID());trip=UUID.randomUUID();stop=UUID.randomUUID();document=UUID.randomUUID();
    db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,?,now(),now())",trip,tenant,driver,tractor);
    db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'OTHER','PENDING','{\"name\":\"Fixture\"}',now(),now())",stop,tenant,trip);
    db.execute("INSERT INTO documents(id,tenant_id,object_key,document_type,upload_status,source_type,created_at) VALUES(?,?,?,'FUEL_RECEIPT','PENDING','SYSTEM_PROCESS',now())",document,tenant,document.toString());
  }
  @Test void exactFuelColumnsAndHistory() throws Exception {
    flyway.validate();assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("25");
    try(var c=flyway.getConfiguration().getDataSource().getConnection();var q=c.createStatement()) {
      var columns=new ArrayList<String>();try(var r=q.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='fuel_events' ORDER BY ordinal_position")){while(r.next())columns.add(r.getString(1));}
      assertThat(columns).containsExactly("id","tenant_id","driver_id","tractor_id","trip_id","odometer","receipt_document_id","occurred_at","created_at");
      try(var r=q.executeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('fuel_events'::regclass,'operational_exceptions'::regclass,'operational_exception_documents'::regclass,'order_fulfillment_events'::regclass) AND confdeltype='c'")){r.next();assertThat(r.getLong(1)).isZero();}
    }
  }
  @ParameterizedTest @ValueSource(strings={"fuel_events","operational_exceptions","operational_exception_documents","order_fulfillment_events"})
  void exactRuntimePolicyAndGrants(String table) throws Exception {
    try(var c=runtime.getConnection();var q=c.createStatement()) {
      try(var r=q.executeQuery("SELECT has_table_privilege(current_user,'"+table+"','SELECT'),has_table_privilege(current_user,'"+table+"','INSERT'),has_table_privilege(current_user,'"+table+"','UPDATE'),has_table_privilege(current_user,'"+table+"','DELETE'),has_schema_privilege(current_user,'public','CREATE'),rolsuper,rolbypassrls FROM pg_roles WHERE rolname=current_user")) {
        r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();for(int i=3;i<=7;i++)assertThat(r.getBoolean(i)).isFalse();
      }
      try(var r=q.executeQuery("SELECT cmd,roles::text,qual,with_check FROM pg_policies WHERE schemaname='public' AND tablename='"+table+"'")) {
        assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("ALL");assertThat(r.getString(2)).isEqualTo("{"+RUNTIME_USERNAME+"}");assertThat(r.getString(3)).isEqualTo(r.getString(4)).contains("tenant_id","app.current_tenant_id","app.current_user_id","IS NULL");assertThat(r.next()).isFalse();
      }
      try(var r=q.executeQuery("SELECT relrowsecurity,relforcerowsecurity,pg_get_userbyid(relowner)=current_user FROM pg_class WHERE oid='"+table+"'::regclass")) {
        r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isFalse();assertThat(r.getBoolean(3)).isFalse();
      }
    }
    assertThat(db.value("SELECT count(*) FROM pg_class c,LATERAL aclexplode(c.relacl) a WHERE c.oid=?::regclass AND a.grantee=0",table)).isEqualTo(0L);
  }
  void fuel(UUID id,UUID t,UUID receipt,String odometer) throws Exception {
    db.execute("INSERT INTO fuel_events VALUES(?,?,?,?,?,?::numeric,?,now(),now())",id,t,driver,tractor,trip,odometer,receipt);
  }
  @Test void fuelTenantIntegrityUniquenessAndOdometer() throws Exception {
    fuel(UUID.randomUUID(),tenant,document,"12.123");
    assertThatThrownBy(()->fuel(UUID.randomUUID(),tenant,document,"12")).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->fuel(UUID.randomUUID(),other,document,"12")).isInstanceOf(SQLException.class);
    for(String value:List.of("-1","1.1234","1000000000001"))assertThatThrownBy(()->fuel(UUID.randomUUID(),tenant,document,value)).isInstanceOf(SQLException.class);
  }
  @Test void exceptionStopMustBelongToItsExactTrip() throws Exception {
    var second=UUID.randomUUID();db.execute("INSERT INTO trips(id,tenant_id,status,created_at,updated_at) VALUES(?,?,'PLANNED',now(),now())",second,tenant);
    String sql="INSERT INTO operational_exceptions VALUES(?,?,?,?,?,?,'OTHER','Fixture problem',now(),now(),?)";
    db.execute(sql,UUID.randomUUID(),tenant,trip,stop,driver,user,UUID.randomUUID());
    assertThatThrownBy(()->db.execute(sql,UUID.randomUUID(),tenant,second,stop,driver,user,UUID.randomUUID())).isInstanceOf(SQLException.class);
  }
  @Test void sameConnectionCannotReadFuelAcrossTenantOrBootstrap() throws Exception {
    fuel(UUID.randomUUID(),tenant,document,"10");
    try(var c=runtime.getConnection();var q=c.createStatement()) {
      c.setAutoCommit(false);
      for(var scope:List.of(other,tenant,other)) {
        q.execute("SELECT set_config('app.current_tenant_id','"+scope+"',true)");
        try(var r=q.executeQuery("SELECT count(*) FROM fuel_events")){r.next();assertThat(r.getInt(1)).isEqualTo(scope.equals(tenant)?1:0);}
      }
      q.execute("SELECT set_config('app.current_tenant_id','"+tenant+"',true),set_config('app.current_user_id','"+user+"',true)");
      try(var r=q.executeQuery("SELECT count(*) FROM fuel_events")){r.next();assertThat(r.getInt(1)).isZero();}c.rollback();
    }
  }
}
