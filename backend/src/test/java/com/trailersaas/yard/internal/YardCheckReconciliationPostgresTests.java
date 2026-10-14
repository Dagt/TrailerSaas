package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YardCheckReconciliationPostgresTests extends YardCheckFixture {
  UUID item(UUID id) throws Exception {return UUID.fromString(JsonPath.read(get("/yard-checks/"+id).body(),"$.items[0].id"));}
  UUID completed(boolean mismatch) throws Exception {
    var id=check(start());if(mismatch)assertThat(scan(id,UUID.randomUUID().toString(),scanBody(trailer,b)).statusCode()).isEqualTo(200);
    var r=command("/yard-checks/"+id+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",mismatch?1:0));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return id;
  }
  Map<String,Object> reconcileBody(boolean unknown,int version) {
    var r=new LinkedHashMap<String,Object>();r.put("expectedTrailerVersion",version);r.put("reason","Confirmed physical finding");if(!unknown)r.put("newLocationId",b.toString());return r;
  }
  HttpResponse<String> reconcile(UUID id,UUID item,String key,boolean unknown,Map<String,Object> body) throws Exception {
    return command("/yard-checks/"+id+"/items/"+item+(unknown?"/mark-location-unknown":"/location-correction"),key,body);
  }
  @Test void missingConfirmationAtomicallyCreatesRealAlertEventAndAssociationPreservingLastKnown() throws Exception {
    var id=completed(false);var item=item(id);var before=f.db.value("select row_to_json(i)::text from yard_check_items i where id=?",item);
    var lastSeen=f.db.value("select last_seen_at from trailers where id=?",trailer);
    var key=UUID.randomUUID().toString();var r=reconcile(id,item,key,true,reconcileBody(true,0));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.trailer.locationState")).isEqualTo("LOCATION_UNKNOWN");
    assertThat(JsonPath.<Object>read(r.body(),"$.trailer.currentLocation")).isNull();
    assertThat(f.db.value("select last_known_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select last_seen_at from trailers where id=?",trailer)).isEqualTo(lastSeen);
    assertThat(f.db.value("select load_state from trailers where id=?",trailer)).isEqualTo("UNKNOWN");
    assertThat(f.db.value("select designated_customer_id from trailers where id=?",trailer)).isEqualTo(customer);
    assertThat(f.db.value("select row_to_json(i)::text from yard_check_items i where id=?",item)).isEqualTo(before);
    assertThat(f.db.value("select version from yard_checks where id=?",id)).isEqualTo(1L);
    var alert=UUID.fromString(JsonPath.read(r.body(),"$.alertId"));var event=UUID.fromString(JsonPath.read(r.body(),"$.movementEvent.id"));
    assertThat(f.db.value("select alert_type||'/'||severity||'/'||status from alerts where id=?",alert)).isEqualTo("TRAILER_MISSING/WARNING/OPEN");
    assertThat(f.db.value("select related_entity_id from alerts where id=?",alert)).isEqualTo(trailer);
    for(var entry:Map.of("yardCheckId",id,"itemId",item,"movementEventId",event,"actorUserId",user).entrySet())
      assertThat(f.db.value("select message_data->>? from alerts where id=?",entry.getKey(),alert)).isEqualTo(entry.getValue().toString());
    assertThat(f.db.value("select actor_type||'/'||source_type from trailer_movement_events where id=?",event)).isEqualTo("HUMAN_USER/USER_ACTION");
    assertThat(f.db.value("select actor_user_id from trailer_movement_events where id=?",event)).isEqualTo(user);
    assertThat(f.db.value("select trip_id from trailer_movement_events where id=?",event)).isNull();
    assertThat(f.db.value("select tractor_id from trailer_movement_events where id=?",event)).isNull();
    assertThat(f.db.value("select client_operation_id from trailer_movement_events where id=?",event)).isEqualTo(UUID.fromString(key));
    assertThat(f.db.value("select alert_id from yard_check_reconciliations where item_id=?",item)).isEqualTo(alert);
  }
  @Test void correctionUsesSharedPhysicalEngineAndLeavesHistoricalMismatch() throws Exception {
    var id=completed(true);var item=item(id);var r=reconcile(id,item,UUID.randomUUID().toString(),false,reconcileBody(false,0));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.trailer.currentLocation.id")).isEqualTo(b.toString());
    assertThat(JsonPath.<String>read(r.body(),"$.movementEvent.eventType")).isEqualTo("LOCATION_CORRECTED");assertThat(JsonPath.<Object>read(r.body(),"$.alertId")).isNull();
    assertThat(f.db.value("select result from yard_check_items where id=?",item)).isEqualTo("LOCATION_MISMATCH");
    assertThat(f.db.value("select expected_location_id from yard_check_items where id=?",item)).isEqualTo(a);
  }
  @ParameterizedTest @ValueSource(booleans={true,false}) void lostAckReturnsSameReceiptAndNoDuplicatePhysicalFact(boolean unknown) throws Exception {
    var id=completed(!unknown);var item=item(id);var key=UUID.randomUUID().toString();var body=reconcileBody(unknown,0);var first=reconcile(id,item,key,unknown,body);
    assertThat(first.statusCode()).as(first.body()).isEqualTo(200);assertThat(reconcile(id,item,key,unknown,body).body()).isEqualTo(first.body());
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from yard_check_reconciliations where item_id=?",item)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(unknown?1L:0L);
    problem(reconcile(id,item,UUID.randomUUID().toString(),unknown,reconcileBody(unknown,1)),409,"RESOURCE_CONFLICT");
  }
  @Test void sameKeyDifferentReconciliationBodyRejects() throws Exception {
    var id=completed(false);var item=item(id);var key=UUID.randomUUID().toString();var body=reconcileBody(true,0);assertThat(reconcile(id,item,key,true,body).statusCode()).isEqualTo(200);
    body.put("reason","Changed request");problem(reconcile(id,item,key,true,body),409,"IDEMPOTENCY_KEY_REUSED");
  }
  @ParameterizedTest @ValueSource(booleans={true,false}) void staleTrailerVersionRejectsAllEffects(boolean unknown) throws Exception {
    var id=completed(!unknown);var item=item(id);problem(reconcile(id,item,UUID.randomUUID().toString(),unknown,reconcileBody(unknown,1)),409,"STALE_VERSION");
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select count(*) from yard_check_reconciliations where item_id=?",item)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void movedTrailerCannotBeMarkedUnknownFromOldExpectedInventory() throws Exception {
    var id=completed(false);var item=item(id);assertThat(post(UUID.randomUUID().toString(),moveBody()).statusCode()).isEqualTo(200);
    problem(reconcile(id,item,UUID.randomUUID().toString(),true,reconcileBody(true,1)),409,"RESOURCE_CONFLICT");
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(b);
  }
  @Test void arbitraryDestinationNotAuthorizedByMismatchFinding() throws Exception {
    var id=completed(true);var body=reconcileBody(false,0);body.put("newLocationId",a.toString());
    problem(reconcile(id,item(id),UUID.randomUUID().toString(),false,body),409,"RESOURCE_CONFLICT");
  }
  @ParameterizedTest @ValueSource(booleans={true,false}) void wrongFindingCannotBeReconciled(boolean unknown) throws Exception {
    var id=completed(unknown);problem(reconcile(id,item(id),UUID.randomUUID().toString(),unknown,reconcileBody(unknown,0)),409,"INVALID_STATE_TRANSITION");
  }
  @Test void unfinishedCheckCannotReconcile() throws Exception {
    var id=check(start());problem(reconcile(id,item(id),UUID.randomUUID().toString(),true,reconcileBody(true,0)),409,"INVALID_STATE_TRANSITION");
  }
  @Test void foreignItemCannotReconcileAnotherCheck() throws Exception {
    var first=completed(false);var old=item(first);var second=completed(false);
    problem(reconcile(second,old,UUID.randomUUID().toString(),true,reconcileBody(true,0)),404,"RESOURCE_NOT_FOUND");
  }
  @Test void revokedMembershipPreventsSavedReconciliationReceipt() throws Exception {
    var id=completed(false);var item=item(id);var key=UUID.randomUUID().toString();assertThat(reconcile(id,item,key,true,reconcileBody(true,0)).statusCode()).isEqualTo(200);
    f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);
    assertThat(reconcile(id,item,key,true,reconcileBody(true,0)).statusCode()).isEqualTo(403);
  }
  @ParameterizedTest @ValueSource(strings={"alerts","yard_check_reconciliations","processed_client_operations"}) void lateFailureRollsBackTrailerEventAlertReconciliationAndLedger(String table) throws Exception {
    var id=completed(false);var item=item(id);var key=UUID.randomUUID().toString();
    var trigger="yard_check_controlled_failure";
    f.db.execute("create function "+trigger+"() returns trigger language plpgsql as $$ begin raise exception 'controlled rollback'; end $$");
    f.db.execute("create trigger "+trigger+" before "+(table.equals("processed_client_operations")?"update":"insert")+" on "+table+" for each row execute function "+trigger+"()");
    try {assertThat(reconcile(id,item,key,true,reconcileBody(true,0)).statusCode()).isEqualTo(500);}
    finally {f.db.execute("drop trigger "+trigger+" on "+table);f.db.execute("drop function "+trigger+"()");}
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from yard_check_reconciliations where item_id=?",item)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from processed_client_operations where client_operation_id=?",UUID.fromString(key))).isEqualTo(0L);
  }
  @Test void concurrentConfirmationsCreateExactlyOneEventAndAlert() throws Exception {
    var id=completed(false);var item=item(id);try(var workers=Executors.newFixedThreadPool(2)){
      var gate=new CountDownLatch(1);var futures=new ArrayList<Future<HttpResponse<String>>>();for(int n=0;n<2;n++)futures.add(workers.submit(()->{gate.await();return reconcile(id,item,UUID.randomUUID().toString(),true,reconcileBody(true,0));}));gate.countDown();
      var codes=new ArrayList<Integer>();for(var task:futures)codes.add(task.get(30,TimeUnit.SECONDS).statusCode());assertThat(codes).containsExactlyInAnyOrder(200,409);
    }
    assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
  }
  @Test void unknownPreservesRealLastDriverLoadServiceabilityAndOpenCoupling() throws Exception {
    var driver=f.db.driver(tenant,user);var tractor=f.db.tractor(tenant,"Coupled-"+UUID.randomUUID());var hook=UUID.randomUUID();
    f.db.execute("insert into trailer_movement_events(id,tenant_id,trailer_id,driver_id,tractor_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at) values(?,?,?,?,?,'HOOKED','HUMAN_USER','USER_ACTION',?,now()-interval '1 hour',now())",hook,tenant,trailer,driver,tractor,user);
    f.db.execute("insert into trailer_couplings(id,tenant_id,trailer_id,driver_id,tractor_id,hooked_at,hook_event_id,created_at) values(?,?,?,?,?,now()-interval '1 hour',?,now())",UUID.randomUUID(),tenant,trailer,driver,tractor,hook);
    f.db.execute("update trailers set last_driver_id=?,load_state='LOADED',serviceability_state='OUT_OF_SERVICE' where id=?",driver,trailer);
    var id=completed(false);var r=reconcile(id,item(id),UUID.randomUUID().toString(),true,reconcileBody(true,0));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(f.db.value("select last_driver_id from trailers where id=?",trailer)).isEqualTo(driver);
    assertThat(f.db.value("select load_state||'/'||serviceability_state from trailers where id=?",trailer)).isEqualTo("LOADED/OUT_OF_SERVICE");
    assertThat(f.db.value("select count(*) from trailer_couplings where trailer_id=? and dropped_at is null",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='DROPPED'",trailer)).isEqualTo(0L);
  }
  @Test void differentChecksCompetingForSameTrailerHaveOnePhysicalWinner() throws Exception {
    var one=completed(false);var two=completed(false);var firstItem=item(one);var secondItem=item(two);
    try(var workers=Executors.newFixedThreadPool(2)){
      var gate=new CountDownLatch(1);var first=workers.submit(()->{gate.await();return reconcile(one,firstItem,UUID.randomUUID().toString(),true,reconcileBody(true,0));});
      var second=workers.submit(()->{gate.await();return reconcile(two,secondItem,UUID.randomUUID().toString(),true,reconcileBody(true,0));});gate.countDown();
      assertThat(List.of(first.get(30,TimeUnit.SECONDS).statusCode(),second.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    }
    assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from yard_check_items where tenant_id=? and result='MISSING'",tenant)).isEqualTo(2L);
  }
}
