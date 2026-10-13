package com.trailersaas.yard.internal;
import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YardCorrectionsPostgresTests extends YardPostgresFixture {
  Map<String,Object> action(String type) {
    var body=new LinkedHashMap<String,Object>();body.put("trailerId",trailer.toString());body.put("siteId",site.toString());
    body.put("expectedTrailerVersion",0);body.put("observedAt",Instant.now().minusSeconds(5).toString());body.put("reason","Physically confirmed");
    body.put(type.equals("location-corrections")?"newLocationId":"loadState",type.equals("location-corrections")?b.toString():"EMPTY");return body;
  }
  HttpResponse<String> send(String type,String key,Map<String,Object> body) throws Exception {
    var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/yard/"+type)).header("Authorization","Bearer yard").header("Content-Type","application/json");
    if(key!=null)req.header("Idempotency-Key",key);
    return HttpClient.newHttpClient().send(req.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  @Test void correctionKeepsBeforeAfterAndHumanProvenance() throws Exception {
    var body=action("location-corrections");var key=UUID.randomUUID().toString();var r=send("location-corrections",key,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(r.body(),"$.movementEvent.eventType")).isEqualTo("LOCATION_CORRECTED");
    assertThat(JsonPath.<Number>read(r.body(),"$.trailer.version").longValue()).isEqualTo(1);
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(b);
    assertThat(f.db.value("select last_known_location_id from trailers where id=?",trailer)).isEqualTo(b);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='LOCATION_CORRECTED' and actor_type='HUMAN_USER' and source_type='USER_ACTION' and actor_user_id=? and client_operation_id=? and metadata->>'previousLocationId'=? and metadata->>'newLocationId'=? and driver_id is null and tractor_id is null and trip_id is null",trailer,user,UUID.fromString(key),a.toString(),b.toString())).isEqualTo(1L);
    assertThat(f.db.value("select load_state from trailers where id=?",trailer)).isEqualTo("UNKNOWN");
    assertThat(f.db.value("select designated_customer_id from trailers where id=?",trailer)).isEqualTo(customer);
    assertThat(f.db.value("select serviceability_state from trailers where id=?",trailer)).isEqualTo("IN_SERVICE");
  }
  @ParameterizedTest @ValueSource(strings={"EMPTY:LOADED","LOADED:EMPTY","UNKNOWN:LOADED","UNKNOWN:EMPTY","EMPTY:UNKNOWN","LOADED:UNKNOWN"})
  void loadChangesOnlyLoad(String change) throws Exception {
    var values=change.split(":");f.db.execute("update trailers set load_state=? where id=?",values[0],trailer);var seen=f.db.value("select last_seen_at from trailers where id=?",trailer);
    var body=action("load-state-changes");body.put("loadState",values[1]);var r=send("load-state-changes",UUID.randomUUID().toString(),body);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(f.db.value("select load_state from trailers where id=?",trailer)).isEqualTo(values[1]);
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);assertThat(f.db.value("select last_seen_at from trailers where id=?",trailer)).isEqualTo(seen);
    assertThat(f.db.value("select designated_customer_id from trailers where id=?",trailer)).isEqualTo(customer);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='LOAD_STATE_CHANGED' and metadata->>'previousLoadState'=? and metadata->>'newLoadState'=?",trailer,values[0],values[1])).isEqualTo(1L);
  }
  @ParameterizedTest @ValueSource(strings={"sameLocation","sameLoad","stale","blank","unknownField","badVersion","foreignLocation","wrongSite","inactiveSite","oldObservation","unknownLoad","future","expandedYear"})
  void invalidCorrectionsHaveNoEffects(String mode) throws Exception {
    String type=Set.of("sameLoad","unknownLoad").contains(mode)?"load-state-changes":"location-corrections";var body=action(type);int status=409;String code="RESOURCE_CONFLICT";
    switch(mode) {
      case "sameLocation" -> body.put("newLocationId",a.toString());
      case "sameLoad" -> body.put("loadState","UNKNOWN");
      case "stale" -> {body.put("expectedTrailerVersion",2);code="STALE_VERSION";}
      case "blank" -> {body.put("reason","\u00a0\u2003");status=400;code="VALIDATION_FAILED";}
      case "unknownField" -> {body.put("actor_type","SYSTEM");status=400;code="VALIDATION_FAILED";}
      case "badVersion" -> {body.put("expectedTrailerVersion",0.5);status=400;code="VALIDATION_FAILED";}
      case "foreignLocation" -> {body.put("newLocationId",f.location(other).toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "wrongSite" -> {var loc=f.location(tenant);body.put("siteId",f.db.value("select site_id from operational_locations where id=?",loc).toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "inactiveSite" -> f.db.execute("update physical_sites set active=false where id=?",site);
      case "oldObservation" -> body.put("observedAt",Instant.now().minusSeconds(7200).toString());
      case "unknownLoad" -> {body.put("loadState","LOADING");status=400;code="VALIDATION_FAILED";}
      case "future" -> {body.put("observedAt",Instant.now().plusSeconds(60).toString());status=400;code="VALIDATION_FAILED";}
      case "expandedYear" -> {body.put("observedAt","-1000000000-01-01T00:00:00Z");status=400;code="VALIDATION_FAILED";}
    }
    problem(send(type,UUID.randomUUID().toString(),body),status,code);unchanged();
  }
  @ParameterizedTest @ValueSource(strings={"location-corrections","load-state-changes"})
  void lostAckAndRevocation(String type) throws Exception {
    var key=UUID.randomUUID().toString();var body=action(type);var first=send(type,key,body);assertThat(first.statusCode()).as(first.body()).isEqualTo(200);assertThat(send(type,key,body).body()).isEqualTo(first.body());
    var changed=new LinkedHashMap<>(body);changed.put("reason","Different intent");problem(send(type,key,changed),409,"IDEMPOTENCY_KEY_REUSED");
    f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);assertThat(send(type,key,body).statusCode()).isEqualTo(403);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
  }
  @Test void concurrentCommandsHaveOneVersionWinner() throws Exception {
    try(var pool=Executors.newFixedThreadPool(2)) {
      var first=pool.submit(()->send("location-corrections",UUID.randomUUID().toString(),action("location-corrections")));var second=pool.submit(()->send("load-state-changes",UUID.randomUUID().toString(),action("load-state-changes")));
      assertThat(List.of(first.get(30,TimeUnit.SECONDS).statusCode(),second.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    }
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
  }
}
