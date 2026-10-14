package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YardCheckLifecyclePostgresTests extends YardCheckFixture {
  HttpResponse<String> finish(UUID id,String key,int version,boolean cancel) throws Exception {
    return command("/yard-checks/"+id+(cancel?"/cancellation":"/completion"),key,cancel?Map.of("expectedVersion",version,"reason","Interrupted"):Map.of("expectedVersion",version));
  }
  @Test void completionMarksMissingOnlyAndPreservesLocationWithoutAlert() throws Exception {
    var id=check(start());var r=finish(id,UUID.randomUUID().toString(),0,false);
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(JsonPath.<String>read(r.body(),"$.status")).isEqualTo("COMPLETED");
    assertThat(JsonPath.<Integer>read(r.body(),"$.missingCount")).isEqualTo(1);assertThat(JsonPath.<String>read(r.body(),"$.completedByUserId")).isEqualTo(user.toString());
    assertThat(f.db.value("select result from yard_check_items where yard_check_id=?",id)).isEqualTo("MISSING");
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
  }
  @Test void cancellationKeepsPartialObservationsAndPendingNotMissing() throws Exception {
    var extra=f.trailer(tenant,"Pending-"+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",b,extra);
    var id=check(start());assertThat(scan(id,UUID.randomUUID().toString(),scanBody(trailer,a)).statusCode()).isEqualTo(200);
    var r=finish(id,UUID.randomUUID().toString(),1,true);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.status")).isEqualTo("CANCELLED");assertThat(JsonPath.<Integer>read(r.body(),"$.missingCount")).isZero();
    assertThat(JsonPath.<Integer>read(r.body(),"$.remainingCount")).isEqualTo(1);assertThat(JsonPath.<Integer>read(r.body(),"$.matchedCount")).isEqualTo(1);
  }
  @Test void completionKeepsMatchedMismatchUnexpectedAndMarksOnlyUnobserved() throws Exception {
    var missing=f.trailer(tenant,"Missing-"+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",a,missing);
    var mismatch=f.trailer(tenant,"Mismatch-"+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",a,mismatch);
    var unexpected=f.trailer(tenant,"Unexpected-"+UUID.randomUUID(),customer);
    var id=check(start());for(var pair:List.of(new UUID[]{trailer,a},new UUID[]{mismatch,b},new UUID[]{unexpected,a}))assertThat(scan(id,UUID.randomUUID().toString(),scanBody(pair[0],pair[1])).statusCode()).isEqualTo(200);
    var r=finish(id,UUID.randomUUID().toString(),3,false);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    for(var field:List.of("matchedCount","missingCount","mismatchCount","unexpectedCount"))assertThat(JsonPath.<Integer>read(r.body(),"$."+field)).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(r.body(),"$.remainingCount")).isZero();
  }
  @ParameterizedTest @ValueSource(booleans={false,true}) void terminalReplayOriginalButNewCompletionAndScanRejected(boolean cancel) throws Exception {
    var id=check(start());var key=UUID.randomUUID().toString();var first=finish(id,key,0,cancel);assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
    assertThat(finish(id,key,0,cancel).body()).isEqualTo(first.body());
    problem(finish(id,UUID.randomUUID().toString(),1,false),409,"INVALID_STATE_TRANSITION");
    problem(finish(id,UUID.randomUUID().toString(),1,true),409,"INVALID_STATE_TRANSITION");
    problem(scan(id,UUID.randomUUID().toString(),scanBody(trailer,a)),409,"INVALID_STATE_TRANSITION");
  }
  @Test void staleCompletionRollsBackMissingAndLedgerClaim() throws Exception {
    var id=check(start());var key=UUID.randomUUID().toString();problem(finish(id,key,1,false),409,"STALE_VERSION");
    assertThat(f.db.value("select result from yard_check_items where yard_check_id=?",id)).isNull();
    assertThat(f.db.value("select count(*) from processed_client_operations where client_operation_id=?",UUID.fromString(key))).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={" ","","\n"}) void cancellationRequiresNonblankReason(String reason) throws Exception {
    var id=check(start());problem(command("/yard-checks/"+id+"/cancellation",UUID.randomUUID().toString(),Map.of("expectedVersion",0,"reason",reason)),400,"VALIDATION_FAILED");
  }
  @Test void clientCannotSupplyTerminalTimeOrProvenance() throws Exception {
    var id=check(start());problem(command("/yard-checks/"+id+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",0,"completedAt","2026-10-13T00:00:00Z")),400,"VALIDATION_FAILED");
  }
  @Test void concurrentScanAndCompletionHaveConsistentSingleWinner() throws Exception {
    var id=check(start());var body=scanBody(trailer,a);
    try(var workers=Executors.newFixedThreadPool(2)){
      var gate=new CountDownLatch(1);var scan=workers.submit(()->{gate.await();return scan(id,UUID.randomUUID().toString(),body);});
      var complete=workers.submit(()->{gate.await();return finish(id,UUID.randomUUID().toString(),0,false);});gate.countDown();
      var s=scan.get(30,TimeUnit.SECONDS);var c=complete.get(30,TimeUnit.SECONDS);
      assertThat(List.of(s.statusCode(),c.statusCode())).containsExactlyInAnyOrder(200,409);
      var detail=get("/yard-checks/"+id);var state=JsonPath.<String>read(detail.body(),"$.check.status");
      if(state.equals("COMPLETED")){assertThat(JsonPath.<String>read(detail.body(),"$.items[0].result")).isEqualTo("MISSING");assertThat(JsonPath.<Boolean>read(detail.body(),"$.items[0].found")).isFalse();}
      else {assertThat(state).isEqualTo("IN_PROGRESS");assertThat(JsonPath.<String>read(detail.body(),"$.items[0].result")).isEqualTo("MATCHED");}
    }
  }
  @Test void concurrentCompleteAndCancelHaveOneTerminalTransition() throws Exception {
    var id=check(start());try(var workers=Executors.newFixedThreadPool(2)){
      var gate=new CountDownLatch(1);var a=workers.submit(()->{gate.await();return finish(id,UUID.randomUUID().toString(),0,false);});
      var b=workers.submit(()->{gate.await();return finish(id,UUID.randomUUID().toString(),0,true);});gate.countDown();
      assertThat(List.of(a.get(30,TimeUnit.SECONDS).statusCode(),b.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    }
    assertThat(f.db.value("select version from yard_checks where id=?",id)).isEqualTo(1L);
  }
}
