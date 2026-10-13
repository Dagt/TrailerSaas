package com.trailersaas.yard.internal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.identity.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class YardReportsPostgresTests extends YardPostgresFixture {
  @MockitoSpyBean TenantAuthorization authorization;
  Map<String,Object> report() {
    var body=new LinkedHashMap<String,Object>();body.put("trailerId",trailer.toString());body.put("siteId",site.toString());body.put("recordedLocationId",a.toString());body.put("reportedLocationId",b.toString());
    body.put("observedTrailerVersion",0);body.put("observedAt",Instant.now().minusSeconds(5).toString());body.put("reason","Physically found at dock");return body;
  }
  HttpResponse<String> send(String key,Map<String,Object> body) throws Exception {
    var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/yard/discrepancy-reports")).header("Authorization","Bearer yard").header("Content-Type","application/json");
    if(key!=null)req.header("Idempotency-Key",key);
    return HttpClient.newHttpClient().send(req.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  void noMutation() throws Exception {
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select load_state from trailers where id=?",trailer)).isEqualTo("UNKNOWN");
    assertThat(f.db.value("select serviceability_state from trailers where id=?",trailer)).isEqualTo("IN_SERVICE");
  }
  @Test void reportNeedsNoCorrectionCapabilityAndNeverMutates() throws Exception {
    doThrow(new AccessDeniedException("No correction capability")).when(authorization).requireTenantCapability(any(),eq(tenant),eq(Capability.TRAILER_LOCATION_CORRECT));
    var key=UUID.randomUUID().toString();var body=report();var first=send(key,body);assertThat(first.statusCode()).as(first.body()).isEqualTo(200);noMutation();
    verify(authorization,never()).requireTenantCapability(any(),eq(tenant),eq(Capability.TRAILER_LOCATION_CORRECT));
    var id=UUID.fromString(JsonPath.<String>read(first.body(),"$.report.id"));
    assertThat(f.db.value("select count(*) from yard_discrepancy_reports where id=? and trailer_id=? and site_id=? and recorded_location_id=? and reported_location_id=? and observed_trailer_version=0 and actor_user_id=? and client_operation_id=?",id,trailer,site,a,b,user,UUID.fromString(key))).isEqualTo(1L);
    assertThat(send(key,body).body()).isEqualTo(first.body());
    body.put("reason","Another observation");problem(send(key,body),409,"IDEMPOTENCY_KEY_REUSED");
    assertThat(f.db.value("select count(*) from yard_discrepancy_reports where tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void historicalObservationDoesNotOverwriteNewerRecordedState() throws Exception {
    f.db.execute("update trailers set version=3 where id=?",trailer);var body=report();
    var r=send(UUID.randomUUID().toString(),body);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<Number>read(r.body(),"$.report.observedTrailerVersion").longValue()).isEqualTo(0L);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(3L);
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
  }
  @Test void unknownRecordedLocationIsAValidHistoricalObservationNotAFictitiousLocation() throws Exception {
    f.db.execute("update trailers set location_state='LOCATION_UNKNOWN',current_location_id=null where id=?",trailer);
    var body=report();body.put("recordedLocationId",null);var r=send(UUID.randomUUID().toString(),body);
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    assertThat(JsonPath.<Object>read(r.body(),"$.report.recordedLocationId")).isNull();
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isNull();
    assertThat(f.db.value("select location_state from trailers where id=?",trailer)).isEqualTo("LOCATION_UNKNOWN");
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
  }
  @Test void offlineReportAfterRealCorrectionCannotOverwriteOrRepeatItsPhysicalEvent() throws Exception {
    var body=report();var correction=new LinkedHashMap<String,Object>();correction.put("trailerId",trailer);correction.put("siteId",site);correction.put("newLocationId",b);correction.put("expectedTrailerVersion",0);correction.put("reason","Observed at dock");correction.put("observedAt",java.time.Instant.now().minusSeconds(2).toString());
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/yard/location-corrections")).header("Authorization","Bearer yard").header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(correction))).build();
    var corrected=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertThat(corrected.statusCode()).as(corrected.body()).isEqualTo(200);
    var reported=send(UUID.randomUUID().toString(),body);assertThat(reported.statusCode()).as(reported.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(reported.body(),"$.report.recordedLocationId")).isEqualTo(a.toString());
    assertThat(JsonPath.<Number>read(reported.body(),"$.report.observedTrailerVersion").longValue()).isZero();
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(b);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);
  }
  @ParameterizedTest @ValueSource(strings={"reason","same","foreignRecorded","foreignReported","foreignTrailer","wrongSite","inactiveSite","badVersion","futureVersion","unknown","future","noKey","revoked"})
  void invalidReportsAreSafe(String mode) throws Exception {
    var body=report();String key=UUID.randomUUID().toString();int status=400;String code="VALIDATION_FAILED";
    switch(mode) {
      case "reason" -> body.put("reason"," ");
      case "same" -> {body.put("reportedLocationId",a.toString());status=409;code="RESOURCE_CONFLICT";}
      case "foreignRecorded" -> {body.put("recordedLocationId",f.location(other).toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "foreignReported" -> {body.put("reportedLocationId",f.location(other).toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "foreignTrailer" -> {body.put("trailerId",f.trailer(other,"Foreign-"+UUID.randomUUID(),null).toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "wrongSite" -> {body.put("siteId",UUID.randomUUID().toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "inactiveSite" -> {f.db.execute("update physical_sites set active=false where id=?",site);status=409;code="RESOURCE_CONFLICT";}
      case "badVersion" -> body.put("observedTrailerVersion",1.5);
      case "futureVersion" -> body.put("observedTrailerVersion",1);
      case "unknown" -> body.put("source_type","SYSTEM_PROCESS");
      case "future" -> body.put("observedAt",Instant.now().plusSeconds(300).toString());
      case "noKey" -> key=null;
      case "revoked" -> {f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);status=403;code="TENANT_ACCESS_DENIED";}
    }
    problem(send(key,body),status,code);noMutation();assertThat(f.db.value("select count(*) from yard_discrepancy_reports where tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from processed_client_operations where tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void concurrentSameKeyCreatesExactlyOneReport() throws Exception {
    var body=report();var key=UUID.randomUUID().toString();
    try(var pool=Executors.newFixedThreadPool(2)) {
      var one=pool.submit(()->send(key,body));var two=pool.submit(()->send(key,body));var first=one.get(30,TimeUnit.SECONDS);var second=two.get(30,TimeUnit.SECONDS);
      assertThat(first.statusCode()).as(first.body()).isEqualTo(200);assertThat(second.body()).isEqualTo(first.body());
    }
    assertThat(f.db.value("select count(*) from yard_discrepancy_reports where tenant_id=?",tenant)).isEqualTo(1L);noMutation();
  }
  @Test void immutableRuntimeHistoryAndTenantRls() throws Exception {
    var response=send(UUID.randomUUID().toString(),report());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(transactions.execute(other,()->jdbc.queryForObject("select count(*) from yard_discrepancy_reports",Long.class))).isEqualTo(0L);
    assertThatThrownBy(()->transactions.execute(tenant,()->jdbc.update("update yard_discrepancy_reports set reason='rewrite'"))).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(()->f.db.execute("delete from yard_discrepancy_reports where tenant_id=?",tenant)).hasMessageContaining("append-only");
    assertThat(f.db.value("select count(*) from yard_discrepancy_reports where tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void reviewIsBoundedAndSeparatelyAuthorized() throws Exception {
    var response=send(UUID.randomUUID().toString(),report());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(get("/yard/discrepancy-reports").statusCode()).isEqualTo(403);
    f.db.execute("insert into role_assignments values(?,?,?,?,?,?,now())",UUID.randomUUID(),tenant,membership,"TENANT_ADMIN","TENANT",null);
    var page=get("/yard/discrepancy-reports?limit=1");assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
    assertThat(JsonPath.<List<Object>>read(page.body(),"$.items")).hasSize(1);
    problem(get("/yard/discrepancy-reports?limit=51"),400,"VALIDATION_FAILED");problem(get("/yard/discrepancy-reports?unexpected=1"),400,"VALIDATION_FAILED");
  }
}
