package com.trailersaas.inspections.internal;

import static org.assertj.core.api.Assertions.*;
import com.trailersaas.EquipmentFixtures;
import com.trailersaas.PostgresIntegrationTest;
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
class ShiftSchemaPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  EquipmentFixtures db;
  UUID tenant,other,user,driver,foreignDriver,tractor,shift;
  @BeforeEach void seed() throws Exception {
    db=new EquipmentFixtures(flyway);tenant=db.tenant();other=db.tenant();user=db.user();
    driver=db.driver(tenant,user);foreignDriver=db.driver(other,null);
    tractor=db.tractor(tenant,"Inspection-"+UUID.randomUUID());shift=open(tenant,driver);
  }
  UUID open(UUID t,UUID d) throws Exception {
    var id=UUID.randomUUID();db.execute("INSERT INTO shifts VALUES(?,?,?,now(),NULL,now(),now())",id,t,d);return id;
  }
  @Test void exactSchemaAndMigrationHistoryRetainAcceptedColumns() throws Exception {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");flyway.validate();
    try(var c=flyway.getConfiguration().getDataSource().getConnection();var q=c.createStatement()) {
      var columns=new ArrayList<String>();
      try(var r=q.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='inspections' ORDER BY ordinal_position")) {while(r.next())columns.add(r.getString(1));}
      assertThat(columns).containsExactly("id","tenant_id","driver_id","tractor_id","shift_id","trip_id","inspection_type","odometer","damages","failures","comments","checklist_data","occurred_at","created_at");
      try(var r=q.executeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('shifts'::regclass,'shift_corrections'::regclass,'inspections'::regclass) AND confdeltype='c'")) {r.next();assertThat(r.getLong(1)).isZero();}
    }
  }
  @Test void onlyOneOpenShiftAndCrossTenantDriverNeverAllowed() throws Exception {
    assertThatThrownBy(()->open(tenant,driver)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->open(tenant,foreignDriver)).isInstanceOf(SQLException.class);
    db.execute("UPDATE shifts SET off_duty_at=now() WHERE id=?",shift);
    assertThat(open(tenant,driver)).isNotEqualTo(shift);
    assertThatThrownBy(()->db.execute("UPDATE shifts SET off_duty_at=on_duty_at-interval '1 second' WHERE id=?",shift)).isInstanceOf(SQLException.class);
  }
  @ParameterizedTest @ValueSource(strings={"shifts","shift_corrections","inspections"})
  void exactRuntimeGrantsAndTenantPinnedRls(String table) throws Exception {
    UUID foreignShift=open(other,foreignDriver),ownRow=shift,foreignRow=foreignShift;
    String crossInsert;
    if(table.equals("shift_corrections")) {
      ownRow=UUID.randomUUID();foreignRow=UUID.randomUUID();
      for(var values:List.of(new Object[]{ownRow,tenant,shift,user},new Object[]{foreignRow,other,foreignShift,user}))
        db.execute("INSERT INTO shift_corrections VALUES(?,?,?,'on_duty_at',now(),now()+interval '1 second',?,'Fixture correction',now())",values);
      crossInsert="INSERT INTO shift_corrections VALUES('"+UUID.randomUUID()+"','"+other+"','"+foreignShift+"','on_duty_at',now(),now()+interval '1 second','"+user+"','Cross-Tenant fixture',now())";
    } else if(table.equals("inspections")) {
      ownRow=UUID.randomUUID();foreignRow=UUID.randomUUID();UUID foreignTractor=db.tractor(other,"Foreign-"+UUID.randomUUID());
      for(var values:List.of(new Object[]{ownRow,tenant,driver,tractor,shift},new Object[]{foreignRow,other,foreignDriver,foreignTractor,foreignShift}))
        db.execute("INSERT INTO inspections(id,tenant_id,driver_id,tractor_id,shift_id,inspection_type,odometer,occurred_at,created_at) VALUES(?,?,?,?,?,'POST_TRIP',1,now(),now())",values);
      crossInsert="INSERT INTO inspections(id,tenant_id,driver_id,tractor_id,shift_id,inspection_type,odometer,occurred_at,created_at) VALUES('"+UUID.randomUUID()+"','"+other+"','"+foreignDriver+"','"+foreignTractor+"','"+foreignShift+"','POST_TRIP',1,now(),now())";
    } else {
      UUID newForeignDriver=db.driver(other,null);
      crossInsert="INSERT INTO shifts VALUES('"+UUID.randomUUID()+"','"+other+"','"+newForeignDriver+"',now(),NULL,now(),now())";
    }
    try(var c=runtime.getConnection();var q=c.createStatement()) {
      try(var r=q.executeQuery("SELECT has_table_privilege(current_user,'"+table+"','SELECT'),has_table_privilege(current_user,'"+table+"','INSERT'),has_table_privilege(current_user,'"+table+"','UPDATE'),has_table_privilege(current_user,'"+table+"','DELETE'),has_schema_privilege(current_user,'public','CREATE'),rolsuper,rolbypassrls FROM pg_roles WHERE rolname=current_user")) {
        r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();assertThat(r.getBoolean(3)).isEqualTo(table.equals("shifts"));for(int i=4;i<=7;i++)assertThat(r.getBoolean(i)).isFalse();
      }
      c.setAutoCommit(false);
      try(var r=q.executeQuery("SELECT count(*) FROM "+table)) {r.next();assertThat(r.getLong(1)).isZero();}
      q.execute("SELECT set_config('app.current_tenant_id','"+tenant+"',true)");
      try(var r=q.executeQuery("SELECT id FROM "+table)) {assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(ownRow);assertThat(r.next()).isFalse();}
      q.execute("SELECT set_config('app.current_tenant_id','"+other+"',true)");
      try(var r=q.executeQuery("SELECT id FROM "+table)) {assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(foreignRow);assertThat(r.next()).isFalse();}
      q.execute("SELECT set_config('app.current_user_id','"+user+"',true)");
      try(var r=q.executeQuery("SELECT count(*) FROM "+table)) {r.next();assertThat(r.getLong(1)).isZero();}
      c.rollback();
      q.execute("SELECT set_config('app.current_tenant_id','"+tenant+"',true)");
      assertThatThrownBy(()->q.executeUpdate(crossInsert)).isInstanceOfSatisfying(SQLException.class,error->assertThat(error.getSQLState()).isEqualTo("42501"));
      c.rollback();
      try(var r=q.executeQuery("SELECT cmd,roles::text,qual,with_check FROM pg_policies WHERE schemaname='public' AND tablename='"+table+"'")) {
        assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("ALL");assertThat(r.getString(2)).isEqualTo("{"+RUNTIME_USERNAME+"}");
        assertThat(r.getString(3)).isEqualTo(r.getString(4)).contains("tenant_id", "app.current_tenant_id", "app.current_user_id", "IS NULL");assertThat(r.next()).isFalse();
      }
      try(var r=q.executeQuery("SELECT relrowsecurity,relforcerowsecurity,pg_get_userbyid(relowner)=current_user FROM pg_class WHERE oid='"+table+"'::regclass")) {
        assertThat(r.next()).isTrue();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isFalse();assertThat(r.getBoolean(3)).isFalse();
      }
    }
    assertThat(db.value("SELECT count(*) FROM pg_class c,LATERAL aclexplode(c.relacl) a WHERE c.oid=?::regclass AND a.grantee=0",table)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"PRE_TRIP","POST_TRIP"})
  void inspectionTenantAndDriverShiftIntegrity(String type) throws Exception {
    String sql="INSERT INTO inspections(id,tenant_id,driver_id,tractor_id,shift_id,inspection_type,odometer,occurred_at,created_at) VALUES(?,?,?,?,?,?,1,now(),now())";
    db.execute(sql,UUID.randomUUID(),tenant,driver,tractor,shift,type);
    assertThatThrownBy(()->db.execute(sql,UUID.randomUUID(),other,foreignDriver,tractor,shift,type)).isInstanceOf(SQLException.class);
    var second=db.driver(tenant,null);
    assertThatThrownBy(()->db.execute(sql,UUID.randomUUID(),tenant,second,tractor,shift,type)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->db.execute(sql,UUID.randomUUID(),tenant,driver,tractor,shift,"OTHER")).isInstanceOf(SQLException.class);
  }
}
