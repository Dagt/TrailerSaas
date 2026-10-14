package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YardCheckScanPostgresTests extends YardCheckFixture {
  @Test void matchIsObservationNotMovement() throws Exception {
    var id=check(start());var r=scan(id,UUID.randomUUID().toString(),scanBody(trailer,a));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(JsonPath.<String>read(r.body(),"$.outcome")).isEqualTo("MATCHED");
    assertThat(JsonPath.<Integer>read(r.body(),"$.check.version")).isEqualTo(1);
    assertThat(JsonPath.<String>read(r.body(),"$.item.scannedByUserId")).isEqualTo(user.toString());
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
  }
  @Test void mismatchDoesNotRepairCurrentLocation() throws Exception {
    var id=check(start());var r=scan(id,UUID.randomUUID().toString(),scanBody(trailer,b));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(JsonPath.<String>read(r.body(),"$.outcome")).isEqualTo("LOCATION_MISMATCH");
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
  }
  @Test void unexpectedCreatesExactlyOneObservedItem() throws Exception {
    var id=check(start());var extra=f.trailer(tenant,"Unexpected-"+UUID.randomUUID(),customer);
    var r=scan(id,UUID.randomUUID().toString(),scanBody(extra,a));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(JsonPath.<String>read(r.body(),"$.outcome")).isEqualTo("UNEXPECTED");
    assertThat(JsonPath.<Boolean>read(r.body(),"$.item.expected")).isFalse();
    assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(r.body(),"$.check.totalItemCount")).isEqualTo(2);
  }
  @Test void sameKeyLostAckAndNewKeyDuplicatePreserveOriginalObservation() throws Exception {
    var id=check(start());var key=UUID.randomUUID().toString();var body=scanBody(trailer,a);var first=scan(id,key,body);
    assertThat(first.statusCode()).as(first.body()).isEqualTo(200);assertThat(scan(id,key,body).body()).isEqualTo(first.body());
    var second=scan(id,UUID.randomUUID().toString(),scanBody(trailer,b));
    assertThat(JsonPath.<String>read(second.body(),"$.outcome")).isEqualTo("ALREADY_SCANNED");
    assertThat(JsonPath.<String>read(second.body(),"$.item.observedLocationId")).isEqualTo(a.toString());
    assertThat(JsonPath.<String>read(second.body(),"$.item.scannedAt")).isEqualTo(JsonPath.<String>read(first.body(),"$.item.scannedAt"));
    assertThat(JsonPath.<Integer>read(second.body(),"$.check.version")).isEqualTo(1);
    assertThat(f.db.value("select count(*) from yard_check_items where yard_check_id=?",id)).isEqualTo(1L);
  }
  @Test void sameKeyDifferentBodyRejectsWithoutOverwrite() throws Exception {
    var id=check(start());var key=UUID.randomUUID().toString();var body=scanBody(trailer,a);
    assertThat(scan(id,key,body).statusCode()).isEqualTo(200);body.put("observedLocationId",b.toString());
    problem(scan(id,key,body),409,"IDEMPOTENCY_KEY_REUSED");
  }
  @ParameterizedTest @ValueSource(strings={"trailerId","observedLocationId"}) void foreignResourceSafe404(String field) throws Exception {
    var id=check(start());var body=scanBody(trailer,a);body.put(field,(field.equals("trailerId")?f.trailer(other,"Other",f.customer(other)):f.location(other)).toString());
    problem(scan(id,UUID.randomUUID().toString(),body),404,"RESOURCE_NOT_FOUND");
  }
  @Test void observedWrongSiteRejected() throws Exception {
    var id=check(start());problem(scan(id,UUID.randomUUID().toString(),scanBody(trailer,f.location(tenant))),404,"RESOURCE_NOT_FOUND");
  }
  @ParameterizedTest @ValueSource(strings={"future","past","malformed","expanded","unknown","null"}) void strictOriginalTimeAndClosedBody(String kind) throws Exception {
    var id=check(start());var body=scanBody(trailer,a);
    switch(kind){case "future"->body.put("scannedAt",Instant.now().plusSeconds(100).toString());case "past"->body.put("scannedAt",Instant.now().minusSeconds(3600).toString());case "malformed"->body.put("scannedAt","now");case "expanded"->body.put("scannedAt","-10000-01-01T00:00:00Z");case "unknown"->body.put("actorUserId",user.toString());case "null"->body.put("scannedAt",null);}
    problem(scan(id,UUID.randomUUID().toString(),body),400,"VALIDATION_FAILED");
    assertThat(f.db.value("select found from yard_check_items where yard_check_id=?",id)).isEqualTo(false);
  }
  @Test void concurrentDuplicateScansHaveOneOriginalObservation() throws Exception {
    var id=check(start());var body=scanBody(trailer,a);
    try(var workers=Executors.newFixedThreadPool(2)){
      var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<HttpResponse<String>>>();
      for(int n=0;n<2;n++)tasks.add(workers.submit(()->{gate.await();return scan(id,UUID.randomUUID().toString(),body);}));gate.countDown();
      var outcomes=new HashSet<String>();for(var task:tasks){var r=task.get(30,TimeUnit.SECONDS);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);outcomes.add(JsonPath.read(r.body(),"$.outcome"));}
      assertThat(outcomes).containsExactlyInAnyOrder("MATCHED","ALREADY_SCANNED");
    }
    assertThat(f.db.value("select version from yard_checks where id=?",id)).isEqualTo(1L);
  }
  @Test void changedVersionPageCannotMixObservationSnapshots() throws Exception {
    var id=check(start());assertThat(scan(id,UUID.randomUUID().toString(),scanBody(trailer,a)).statusCode()).isEqualTo(200);
    problem(get("/yard-checks/"+id+"?expectedVersion=0"),409,"STALE_VERSION");
  }
  @Test void acceptedScanReplaysOriginalReceiptAfterCompletionWithoutAnotherObservation() throws Exception {
    var id=check(start());var key=UUID.randomUUID().toString();var body=scanBody(trailer,a);var first=scan(id,key,body);assertThat(first.statusCode()).isEqualTo(200);
    assertThat(command("/yard-checks/"+id+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",1)).statusCode()).isEqualTo(200);
    assertThat(scan(id,key,body).body()).isEqualTo(first.body());assertThat(f.db.value("select version from yard_checks where id=?",id)).isEqualTo(2L);
  }
}
