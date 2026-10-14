package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YardCheckSecurityPostgresTests extends YardCheckFixture {
  UUID completed() throws Exception {var id=check(start());assertThat(command("/yard-checks/"+id+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",0)).statusCode()).isEqualTo(200);return id;}
  UUID item(UUID id) throws Exception {return UUID.fromString(JsonPath.read(get("/yard-checks/"+id).body(),"$.items[0].id"));}
  void state(String sql,Runnable work) {assertThatThrownBy(work::run).hasRootCauseInstanceOf(SQLException.class).satisfies(e->{Throwable root=e;while(root.getCause()!=null)root=root.getCause();assertThat(((SQLException)root).getSQLState()).isEqualTo(sql);});}
  @Test void realRuntimeRlsHidesEveryForeignTableWithoutTenantPredicate() throws Exception {
    var id=completed();var item=item(id);var r=command("/yard-checks/"+id+"/items/"+item+"/mark-location-unknown",UUID.randomUUID().toString(),Map.of("expectedTrailerVersion",0,"reason","Confirmed missing"));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    transactions.execute(other,()->{
      for(var table:List.of("yard_checks","yard_check_items","yard_check_reconciliations","alerts"))assertThat(jdbc.queryForObject("select count(*) from "+table,Long.class)).isZero();return null;
    });
    transactions.execute(tenant,()->{
      for(var table:List.of("yard_checks","yard_check_items","yard_check_reconciliations","alerts"))assertThat(jdbc.queryForObject("select count(*) from "+table,Long.class)).isEqualTo(1L);return null;
    });
  }
  @Test void everyNewTableHasOnlyNecessaryRuntimeGrantsAndNoPublicBypass() {
    transactions.execute(tenant,()->{
      for(var table:List.of("yard_checks","yard_check_items","yard_check_reconciliations","alerts")){
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,'SELECT') and has_table_privilege(current_user,?,'INSERT')",Boolean.class,table,table)).isTrue();
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,'DELETE')",Boolean.class,table)).isFalse();
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,'UPDATE')",Boolean.class,table)).isEqualTo(table.equals("yard_checks")||table.equals("yard_check_items"));
        assertThat(jdbc.queryForObject("select relrowsecurity and relowner<>(select oid from pg_roles where rolname=current_user) from pg_class where oid=?::regclass",Boolean.class,table)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.role_table_grants where grantee='PUBLIC' and table_name=?",Long.class,table)).isZero();
      }
      assertThat(jdbc.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user",Boolean.class)).isFalse();
      return null;
    });
  }
  @ParameterizedTest @ValueSource(strings={"guard_yard_check_lifecycle","guard_yard_check_item","guard_yard_missing_completion","guard_yard_reconciliation_context","guard_yard_missing_alert","guard_yard_alert_association"}) void guardFunctionsGrantNoPublicExecution(String name) {
    transactions.execute(tenant,()->{assertThat(jdbc.queryForObject("select count(*) from pg_proc p cross join lateral aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a where p.oid=?::regprocedure and a.grantee=0 and a.privilege_type='EXECUTE'",Long.class,name+"()")).isZero();return null;});
  }
  @ParameterizedTest @ValueSource(strings={"yard_checks","yard_check_items"}) void completedHistoryCannotBeRewrittenOrDeletedEvenByOwner(String table) throws Exception {
    var id=completed();var item=item(id);var row=table.equals("yard_checks")?id:item;
    state("42501",()->transactions.execute(tenant,()->jdbc.update("delete from "+table+" where id=?",row)));
    try{f.db.execute("delete from "+table+" where id=?",row);fail("Owner must not delete accepted history");}catch(SQLException e){assertThat(e.getSQLState()).isEqualTo("42501");}
    state("42501",()->transactions.execute(tenant,()->jdbc.update("update "+table+" set "+(table.equals("yard_checks")?"version=version+1":"recorded_snapshot='{}'::jsonb")+" where id=?",row)));
  }
  @Test void originalExpectedMembershipAndLocationCannotChangeBeforeCompletion() throws Exception {
    var id=check(start());var item=item(id);
    state("42501",()->transactions.execute(tenant,()->jdbc.update("update yard_check_items set expected_location_id=? where id=?",b,item)));
    state("42501",()->transactions.execute(tenant,()->jdbc.update("update yard_check_items set recorded_snapshot='{}' where id=?",item)));
  }
  @Test void missingCannotBeCommittedDuringActiveCheck() throws Exception {
    var id=check(start());state("23514",()->transactions.execute(tenant,()->jdbc.update("update yard_check_items set result='MISSING' where yard_check_id=?",id)));
    assertThat(f.db.value("select result from yard_check_items where yard_check_id=?",id)).isNull();
  }
  @Test void ownerCannotCompleteWithUnclassifiedPendingRows() throws Exception {
    var id=check(start());try{f.db.execute("update yard_checks set status='COMPLETED',completed_at=now(),completed_by_user_id=?,version=version+1 where id=?",user,id);fail("Pending inventory cannot be declared complete");}catch(SQLException e){assertThat(e.getSQLState()).isEqualTo("23514");}
  }
  @Test void historicalStartLedgerCannotCreateAnotherCheck() throws Exception {
    var id=check(start());var op=f.db.value("select client_operation_id from yard_checks where id=?",id);
    state("23514",()->transactions.execute(tenant,()->jdbc.update("insert into yard_checks(id,tenant_id,site_id,status,started_by_user_id,started_at,created_at,updated_at,client_operation_id) values(?,?,?,'IN_PROGRESS',?,statement_timestamp(),statement_timestamp(),statement_timestamp(),?)",UUID.randomUUID(),tenant,site,user,op)));
  }
  @Test void startLedgerCannotForgeScanObservation() throws Exception {
    var id=check(start());var op=f.db.value("select client_operation_id from yard_checks where id=?",id);
    state("23514",()->transactions.execute(tenant,()->jdbc.update("update yard_check_items set found=true,result='MATCHED',observed_location_id=?,scanned_at=clock_timestamp(),scanned_by_user_id=?,recorded_at=clock_timestamp(),client_operation_id=? where yard_check_id=?",a,user,op,id)));
  }
  @Test void expectedRowsCannotBeAppendedAfterStartEvenAfterCheckVersionUpdate() throws Exception {
    var id=check(start());state("42501",()->transactions.execute(tenant,()->{
      jdbc.update("update yard_checks set version=version+1 where id=?",id);
      return jdbc.update("insert into yard_check_items(id,tenant_id,yard_check_id,site_id,trailer_id,expected,expected_location_id,recorded_snapshot) values(?,?,?,?,?,true,?,'{}')",UUID.randomUUID(),tenant,id,site,trailer,a);
    }));
  }
  @ParameterizedTest @ValueSource(strings={"SUSPENDED","CANCELLED","CANDIDATE"}) void inactiveTenantBlocksStartAndHistoricalRead(String status) throws Exception {
    var id=check(start());f.db.execute("update tenants set status=? where id=?",status,tenant);
    assertThat(start().statusCode()).isEqualTo(403);assertThat(get("/yard-checks/"+id).statusCode()).isEqualTo(403);
  }
  @Test void sameTenantDifferentUserCannotReplayAnotherActorsUuid() throws Exception {
    var key=UUID.randomUUID().toString();check(command("/yard-checks",key,startBody(null)));
    var old=user;user=f.db.user();f.db.membership(tenant,user,"ACTIVE","YARD_SPOTTER");org.mockito.Mockito.when(verifier.verify("yard")).thenReturn(new com.trailersaas.identity.VerifiedFirebaseIdentity("fixture-"+user,null,null));
    problem(command("/yard-checks",key,startBody(null)),409,"IDEMPOTENCY_KEY_REUSED");user=old;
  }
  @Test void lateRevocationAfterSiteLockWaitRejectsStartAndLedger() throws Exception {
    try(var c=flyway.getConfiguration().getDataSource().getConnection()){
      c.setAutoCommit(false);try(var q=c.prepareStatement("select id from physical_sites where id=? for update")){q.setObject(1,site);q.executeQuery();}
      try(var worker=Executors.newSingleThreadExecutor()){
        var future=worker.submit(this::start);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->(Long)f.db.value("select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%physical_sites%'")>0);
        f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);c.commit();assertThat(future.get(20,TimeUnit.SECONDS).statusCode()).isEqualTo(403);
      }
    }
    assertThat(f.db.value("select count(*) from yard_checks where tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from processed_client_operations where tenant_id=?",tenant)).isEqualTo(0L);
  }
  void processing(UUID op,String type) {
    jdbc.update("insert into processed_client_operations(id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at) values(?,?,?,?,?,'controlled-test','PROCESSING',clock_timestamp())",UUID.randomUUID(),tenant,op,user,type);
  }
  @Test void compositeForeignKeyRejectsForeignSiteUnderRealRuntime() throws Exception {
    var loc=f.location(other);var foreignSite=f.db.value("select site_id from operational_locations where id=?",loc);var op=UUID.randomUUID();
    state("23503",()->transactions.execute(tenant,()->{
      processing(op,"YARD_CHECK_START");return jdbc.update("insert into yard_checks(id,tenant_id,site_id,status,started_by_user_id,started_at,created_at,updated_at,client_operation_id) values(?,?,?,'IN_PROGRESS',?,statement_timestamp(),statement_timestamp(),statement_timestamp(),?)",UUID.randomUUID(),tenant,foreignSite,user,op);
    }));
  }
  @Test void compositeForeignKeyRejectsObservationInAnotherSite() throws Exception {
    var id=check(start());var loc=f.location(tenant);var op=UUID.randomUUID();
    state("23503",()->transactions.execute(tenant,()->{
      processing(op,"YARD_CHECK_SCAN");return jdbc.update("update yard_check_items set found=true,result='LOCATION_MISMATCH',observed_location_id=?,scanned_at=clock_timestamp(),scanned_by_user_id=?,recorded_at=clock_timestamp(),client_operation_id=? where yard_check_id=?",loc,user,op,id);
    }));
  }
  @Test void newObservationCannotUseForeignTenantTrailer() throws Exception {
    var id=check(start());var asset=f.trailer(other,"Foreign-"+UUID.randomUUID(),f.customer(other));var op=UUID.randomUUID();
    state("23503",()->transactions.execute(tenant,()->{
      processing(op,"YARD_CHECK_SCAN");return jdbc.update("insert into yard_check_items(id,tenant_id,yard_check_id,site_id,trailer_id,expected,recorded_snapshot,found,result,observed_location_id,scanned_at,scanned_by_user_id,recorded_at,client_operation_id) values(?,?,?,?,?,false,'{}',true,'UNEXPECTED',?,clock_timestamp(),?,clock_timestamp(),?)",UUID.randomUUID(),tenant,id,site,asset,a,user,op);
    }));
  }
}
