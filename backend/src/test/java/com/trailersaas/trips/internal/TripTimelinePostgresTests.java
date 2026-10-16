package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TripTimelinePostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired jakarta.persistence.EntityManagerFactory emf;
  @Autowired TripService service;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  EquipmentFixtures f;
  UUID tenant, other, trip, foreignTrip, driver, user, membership;
  final Instant at = Instant.parse("2026-10-10T12:00:00Z");

  @BeforeEach void seed() throws Exception {
    f = new EquipmentFixtures(flyway);
    tenant = f.tenant(); other = f.tenant();
    f.execute("update tenants set status='ACTIVE' where id in (?,?)", tenant, other);
    user = actor("admin", "TENANT_ADMIN"); actor("ops", "TRANSPORT_OPERATIONS");
    actor("customer", "CUSTOMER_DISPATCHER"); actor("driver", "DRIVER"); actor("yard", "YARD_SPOTTER");
    membership = (UUID) f.value("select id from tenant_memberships where tenant_id=? and user_id=?", tenant, user);
    driver = f.driver(tenant, null); trip = trip(tenant); foreignTrip = trip(other);
  }
  UUID actor(String token, String role) throws Exception {
    var id = f.user(); f.membership(tenant, id, "ACTIVE", role);
    when(verifier.verify(token)).thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null)); return id;
  }
  UUID trip(UUID owner) throws Exception {
    var id = UUID.randomUUID();
    f.execute("insert into trips(id,tenant_id,status,created_at,updated_at) values(?,?,'PLANNED',now(),now())", id, owner); return id;
  }
  void event(UUID id, boolean assignment, Instant time) throws Exception {
    if (assignment) f.execute("insert into trip_assignment_events(id,tenant_id,trip_id,event_type,driver_id,actor_user_id,occurred_at,reason) values(?,?,?,'DRIVER_ASSIGNED',?,?,?,'Original assignment')", id, tenant, trip, driver, user, Timestamp.from(time));
    else f.execute("insert into trip_operational_events(id,tenant_id,trip_id,event_type,actor_user_id,occurred_at,reason) values(?,?,?,'TRIP_CANCELLED',?,?,'Recorded operational instruction')", id, tenant, trip, user, Timestamp.from(time));
  }
  HttpResponse<String> get(String suffix, String token) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/tenants/" + tenant + "/trips/" + trip + suffix))
        .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
  }
  @Test void legacyResponseAndDetailRemainCompatibleWhileOptInOmitsEmbeddedHistory() throws Exception {
    event(UUID.randomUUID(), true, at); event(UUID.randomUUID(), false, at.plusSeconds(1));
    var legacy = get("/timeline", "admin"); assertThat(legacy.statusCode()).isEqualTo(200);
    var legacyBody = JsonPath.parse(legacy.body()); assertThat(legacyBody.<List<?>>read("$.items")).hasSize(2);
    assertThat(legacyBody.<Map<String,Object>>read("$")).containsOnlyKeys("items");
    var detail = JsonPath.parse(get("", "admin").body()); assertThat(detail.<List<?>>read("$.assignmentHistory")).hasSize(1); assertThat(detail.<List<?>>read("$.operationalHistory")).hasSize(1);
    var bounded = JsonPath.parse(get("?includeHistory=false", "admin").body()); assertThat(bounded.<List<?>>read("$.assignmentHistory")).isEmpty(); assertThat(bounded.<List<?>>read("$.operationalHistory")).isEmpty();
    assertThat(get("?includeHistory=invalid", "admin").statusCode()).isEqualTo(400);
    assertThat(get("?includeHistory=false&includeHistory=true", "admin").statusCode()).isEqualTo(400);
  }
  @Test void equalTimestampAndEqualUuidAcrossBothTablesDoNotLoseAnEvent() throws Exception {
    var same = new UUID(0x8000000000004000L, trip.getLeastSignificantBits());
    var lower = new UUID(0x1000000000004000L, trip.getLeastSignificantBits());
    event(same, true, at); event(same, false, at); event(lower, false, at);
    var types = new ArrayList<String>(); String cursor = null;
    for (int i = 0; i < 3; i++) {
      var response = get("/timeline?limit=1" + (cursor == null ? "" : "&cursor=" + cursor), "admin"); assertThat(response.statusCode()).isEqualTo(200);
      var page = JsonPath.parse(response.body()); types.add(page.read("$.items[0].eventType")); cursor = page.read("$.nextCursor");
    }
    assertThat(types).containsExactly("TRIP_CANCELLED", "DRIVER_ASSIGNED", "TRIP_CANCELLED"); assertThat(cursor).isNull();
  }
  @Test void multipleSqlBoundedPagesRetainDeterministicUnionOrder() throws Exception {
    var expected = new ArrayList<String>();
    for (int i = 1; i <= 206; i++) { var id = new UUID(trip.getMostSignificantBits(), i); expected.add(id.toString()); event(id, i % 2 == 0, at.plusSeconds(i / 5)); }
    var actual = new ArrayList<String>(); String cursor = null; int pages = 0;
    do {
      var response = get("/timeline?limit=100" + (cursor == null ? "" : "&cursor=" + cursor), "ops"); assertThat(response.statusCode()).isEqualTo(200);
      var page = JsonPath.parse(response.body()); var ids = page.<List<String>>read("$.items[*].id"); assertThat(ids).hasSizeLessThanOrEqualTo(100);
      actual.addAll(ids); cursor = page.read("$.nextCursor"); pages++; assertThat(pages).isLessThan(5);
    } while (cursor != null);
    assertThat(pages).isEqualTo(3); assertThat(actual).containsExactlyElementsOf(expected);
  }
  @Test void cursorIsBoundToUserTenantTripAndReauthorizesEveryPage() throws Exception {
    event(UUID.randomUUID(), true, at); event(UUID.randomUUID(), false, at.plusSeconds(1));
    String cursor = JsonPath.read(get("/timeline?limit=1", "admin").body(), "$.nextCursor");
    assertThat(get("/timeline?cursor=" + cursor, "ops").statusCode()).isEqualTo(400);
    var originalTrip = trip; trip = trip(tenant); assertThat(get("/timeline?cursor=" + cursor, "admin").statusCode()).isEqualTo(400); trip = originalTrip;
    var originalTenant = tenant; f.membership(other, user, "ACTIVE", "TENANT_ADMIN"); tenant = other; trip = foreignTrip;
    assertThat(get("/timeline?cursor=" + cursor, "admin").statusCode()).isEqualTo(400); tenant = originalTenant; trip = originalTrip;
    f.execute("delete from role_assignments where membership_id=?", membership);
    assertThat(get("/timeline?cursor=" + cursor, "admin").statusCode()).isEqualTo(403);
  }
  @ParameterizedTest @ValueSource(strings = {"customer", "driver", "yard"})
  void restrictedRolesCannotReadEitherModeOrOmittedHistoryDetail(String token) throws Exception {
    assertThat(get("/timeline", token).statusCode()).isEqualTo(403);
    assertThat(get("/timeline?limit=10", token).statusCode()).isEqualTo(403);
    assertThat(get("?includeHistory=false", token).statusCode()).isEqualTo(403);
  }
  @Test void suspendedMembershipAndForeignTripDoNotRevealHistory() throws Exception {
    var original = trip; trip = foreignTrip; assertThat(get("/timeline?limit=1", "admin").statusCode()).isEqualTo(404); trip = original;
    f.execute("update tenant_memberships set status='SUSPENDED' where id=?", membership);
    assertThat(get("/timeline?limit=1", "admin").statusCode()).isEqualTo(403);
  }
  @ParameterizedTest @ValueSource(strings = {"limit=0", "limit=101", "limit=99999999999999", "limit=bad", "cursor=bad", "cursor=", "limit=1&limit=2", "unknown=true"})
  void invalidQueryNeverBecomesUnbounded(String query) throws Exception { assertThat(get("/timeline?" + query, "admin").statusCode()).isEqualTo(400); }
  @Test void emptyPagedHistoryIsACompleteEmptyPage() throws Exception {
    var response = get("/timeline?limit=100", "admin"); assertThat(response.statusCode()).isEqualTo(200);
    var body = JsonPath.parse(response.body()); assertThat(body.<List<?>>read("$.items")).isEmpty(); assertThat(body.<String>read("$.nextCursor")).isNull();
  }
  @Test void queryCountStaysBoundedAndDetailOptOutNeverLoadsHistory() throws Exception {
    for (int i = 1; i <= 60; i++) event(new UUID(trip.getMostSignificantBits(), i), i % 2 == 0, at.plusSeconds(i));
    var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    var actor = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
    stats.clear(); service.timelinePage(actor, tenant, trip, Map.of("limit", "2")); long small = stats.getPrepareStatementCount();
    stats.clear(); service.timelinePage(actor, tenant, trip, Map.of("limit", "50"));
    assertThat(stats.getPrepareStatementCount()).isEqualTo(small).isLessThan(10);
    stats.clear(); service.get(actor, tenant, trip, false); long bounded = stats.getPrepareStatementCount();
    stats.clear(); service.get(actor, tenant, trip, true);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(bounded + 2);
  }
  @Test void runtimeRlsAndExistingChronologicalIndexesSupportBothBoundedBranches() throws Exception {
    event(UUID.randomUUID(), true, at); event(UUID.randomUUID(), false, at);
    try (var c = runtime.getConnection()) {
      c.setAutoCommit(false);
      try (var q = c.createStatement()) {
        try (var r = q.executeQuery("select count(*) from trip_assignment_events")) { r.next(); assertThat(r.getInt(1)).isZero(); }
        q.execute("set local app.current_tenant_id='" + other + "'");
        try (var r = q.executeQuery("select count(*) from trip_assignment_events where tenant_id='" + tenant + "'")) { r.next(); assertThat(r.getInt(1)).isZero(); }
        q.execute("set local app.current_tenant_id='" + tenant + "'"); q.execute("set local enable_seqscan=off");
        for (String table : List.of("trip_assignment_events", "trip_operational_events")) {
          var plan = new StringBuilder();
          try (var r = q.executeQuery("explain select id,occurred_at from " + table + " where tenant_id='" + tenant + "' and trip_id='" + trip + "' order by occurred_at,id limit 10")) { while (r.next()) plan.append(r.getString(1)); }
          assertThat(plan.toString()).contains(table.equals("trip_assignment_events") ? "trip_assignments_chronological" : "trip_operations_chronological");
        }
        try (var r = q.executeQuery("select rolsuper or rolbypassrls from pg_roles where rolname=current_user")) { r.next(); assertThat(r.getBoolean(1)).isFalse(); }
      } finally { c.rollback(); }
    }
  }
}
