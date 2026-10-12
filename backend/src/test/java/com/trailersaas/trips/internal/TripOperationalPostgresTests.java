package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.event.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripOperationalPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired TripService service;
  @Autowired TripAssignmentService assignments;
  @Autowired TripOperationalService operations;
  @Autowired com.trailersaas.trips.TripCancellationResolutionPort finish;
  @Autowired com.trailersaas.trips.TripTrailerExpectationPolicy expectation;
  @Autowired TenantTransactionExecutor transactions;
  @MockitoBean FirebaseTokenVerifier verifier;

  @Value("${local.server.port}")
  int port;

  OrderFixtures f;
  UUID tenant,
      other,
      customer,
      customerB,
      user,
      order,
      orderB,
      site,
      neutral,
      driver,
      tractor,
      trailer,
      membership;
  AuthenticatedUserPrincipal admin;

  @BeforeEach
  void seed() throws Exception {
    f = new OrderFixtures(flyway);
    tenant = f.db.tenant();
    other = f.db.tenant();
    customer = f.customer(tenant);
    customerB = f.customer(tenant);
    user = actor("admin", "TENANT_ADMIN");
    membership =
        (UUID)
            f.db.value(
                "SELECT id FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, user);
    admin = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
    actor("ops", "TRANSPORT_OPERATIONS");
    actor("yard", "YARD_SPOTTER");
    actor("driver", "DRIVER");
    actor("dispatcher", "CUSTOMER_DISPATCHER");
    order = f.order(tenant, customer, user);
    orderB = f.order(tenant, customerB, user);
    f.stop(tenant, order, 1, null, null, "{\"name\":\"Requested frozen\"}");
    f.stop(tenant, orderB, 1, null, null, "{\"name\":\"Other requested\"}");
    site = f.site(tenant, customer);
    f.db.execute(
        "INSERT INTO customer_sites VALUES(?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        customerB,
        site);
    neutral = f.location(tenant, site, null);
    driver = f.db.driver(tenant, null);
    tractor = f.db.tractor(tenant, "T-" + UUID.randomUUID());
    trailer = new TrailerFixtures(flyway).trailer(tenant, "R-" + UUID.randomUUID(), customer);
  }

  @AfterEach
  void resetGuard() {}

  UUID actor(String token, String role) throws Exception {
    var id = f.db.user();
    f.db.membership(tenant, id, "ACTIVE", role);
    when(verifier.verify(token))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null));
    return id;
  }

  Map<String, Object> stop(int sequence, List<UUID> orders) {
    return new HashMap<>(
        Map.of(
            "sequenceNumber",
            sequence,
            "stopType",
            "DELIVERY",
            "orderIds",
            orders.stream().map(UUID::toString).toList(),
            "siteSnapshot",
            Map.of("name", "Frozen executable", "latitude", 32.5)));
  }

  Map<String, Object> plan() {
    return new HashMap<>(
        Map.of("orderIds", List.of(order.toString()), "stops", List.of(stop(2, List.of(order)))));
  }

  String json(Map<String, Object> body) throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
  }

  HttpResponse<String> http(String method, String path, String token, String body)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("X-Request-Id", "trip-regression");
    if (token != null) {
      b.header("Authorization", "Bearer " + token);
    }
    return HttpClient.newHttpClient()
        .send(
            b.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
  }

  String base() {
    return "/api/v1/tenants/" + tenant;
  }

  HttpResponse<String> post(Map<String, Object> b) throws Exception {
    return http("POST", base() + "/trips", "admin", json(b));
  }

  UUID id(HttpResponse<String> r) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(201);
    return UUID.fromString(JsonPath.read(r.body(), "$.id"));
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);
    if (status < 500) assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
    else
      assertThat(r.body())
          .contains("The server could not complete the request.")
          .doesNotContain("controlled infrastructure", "P0001");
    assertThat(r.body())
        .contains("traceId")
        .doesNotContain("constraint", "SQL", "fixture-", other.toString());
    // instance may echo IDs supplied in the request URI, never hidden resource metadata.
    var publicFields = new HashMap<>(JsonPath.<Map<String, Object>>read(r.body(), "$"));
    var instance = publicFields.remove("instance");
    if (instance != null) {
      assertThat(instance).isEqualTo(r.uri().getPath());
    }
    assertThat(publicFields.toString())
        .doesNotContain(customer.toString(), customerB.toString(), other.toString());
    assertThat(r.headers().firstValue("X-Request-Id")).contains("trip-regression");
  }

  HttpResponse<String> cancel(long version) throws Exception {
    return http(
        "POST",
        base() + "/orders/" + order + "/cancellations",
        "admin",
        "{\"expectedVersion\":" + version + ",\"reason\":\"Before execution\"}");
  }

  long count(String table) throws Exception {
    return (Long) f.db.value("SELECT count(*) FROM " + table + " WHERE tenant_id=?", tenant);
  }

  void orderState(String state) throws Exception {
    f.db.execute(
        "UPDATE orders SET status=?,delivered_at=CASE WHEN ? IN ('DELIVERED','COMPLETED') THEN"
            + " now() ELSE NULL END,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL"
            + " END,cancelled_at=CASE WHEN ?='CANCELLED' THEN now() ELSE NULL END WHERE id=?",
        state,
        state,
        state,
        state,
        order);
  }

  void waitFor(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test transaction timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  void lockWait(String table) {
    await()
        .atMost(Duration.ofSeconds(12))
        .untilAsserted(
            () ->
                assertThat(
                        (Long)
                            f.db.value(
                                "SELECT count(*) FROM pg_stat_activity WHERE usename=? AND"
                                    + " wait_event_type='Lock' AND query LIKE ?",
                                RUNTIME_USERNAME,
                                "%" + table + "%"))
                    .isGreaterThan(0));
  }

  void context(Connection c, UUID tenantId, UUID userId) throws Exception {
    try (var q =
        c.prepareStatement(
            "SELECT"
                + " set_config('app.current_tenant_id',?,true),set_config('app.current_user_id',?,true)")) {
      q.setString(1, tenantId == null ? "" : tenantId.toString());
      q.setString(2, userId == null ? "" : userId.toString());
      q.executeQuery().close();
    }
  }

  long runtimeCount(Connection c, String table) throws Exception {
    try (var q = c.createStatement();
        var r = q.executeQuery("SELECT count(*) FROM " + table)) {
      r.next();
      return r.getLong(1);
    }
  }

  UUID create(boolean assigned, boolean plannedTrailer) throws Exception {
    var b = plan();
    if (assigned) {
      b.put("driverId", driver.toString());
      b.put("tractorId", tractor.toString());
    }
    if (plannedTrailer) {
      b.put("plannedInitialTrailerId", trailer.toString());
    }
    return id(post(b));
  }

  UUID target(String kind) throws Exception {
    return switch (kind) {
      case "driver" -> f.db.driver(tenant, null);
      case "tractor" -> f.db.tractor(tenant, "New-" + UUID.randomUUID());
      default -> new TrailerFixtures(flyway).trailer(tenant, "New-" + UUID.randomUUID(), customer);
    };
  }

  String path(UUID trip, String kind) {
    return base()
        + "/trips/"
        + trip
        + "/"
        + (kind.equals("trailer") ? "planned-trailer" : kind)
        + "-assignments";
  }

  Map<String, Object> assignment(String kind, UUID resource, long version) {
    return Map.of(
        "expectedVersion", version, kind + "Id", resource.toString(), "reason", "Dispatch change");
  }

  HttpResponse<String> assign(UUID trip, String kind, UUID resource, long version, String actor)
      throws Exception {
    return http("POST", path(trip, kind), actor, json(assignment(kind, resource, version)));
  }

  HttpResponse<String> decide(
      UUID trip, UUID expected, UUID requested, String decision, String actor) throws Exception {
    return http(
        "POST",
        base() + "/trips/" + trip + "/trailer-substitution-decisions",
        actor,
        json(
            Map.of(
                "expectedTrailerId",
                expected.toString(),
                "requestedTrailerId",
                requested.toString(),
                "decision",
                decision,
                "reason",
                "Scanned alternate")));
  }

  Object field(UUID trip, String column) throws Exception {
    return f.db.value("SELECT " + column + " FROM trips WHERE id=?", trip);
  }

  String projection(String kind) {
    return kind.equals("trailer") ? "planned_initial_trailer_id" : "current_" + kind + "_id";
  }

  List<Map<String, Object>> history(HttpResponse<String> r) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    return JsonPath.read(r.body(), "$.assignmentHistory");
  }

  long historyCount(UUID trip) throws Exception {
    return (Long) f.db.value("SELECT count(*) FROM trip_assignment_events WHERE trip_id=?", trip);
  }

  UUID stopId(UUID trip) throws Exception {
    return (UUID) f.db.value("SELECT id FROM trip_stops WHERE trip_id=?", trip);
  }

  void state(UUID trip, String status) throws Exception {
    f.db.execute(
        "UPDATE trips SET status=?,started_at=CASE WHEN ? IN"
            + " ('IN_TRANSIT','AT_STOP','CANCELLING','COMPLETED') THEN now() ELSE NULL"
            + " END,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END,cancelled_at=CASE"
            + " WHEN ?='CANCELLED' THEN now() ELSE NULL END WHERE id=?",
        status,
        status,
        status,
        status,
        trip);
  }

  Map<String, Object> route(UUID trip, UUID siteId, long version) throws Exception {
    return new HashMap<>(
        Map.of(
            "expectedVersion",
            version,
            "affectedStopId",
            stopId(trip).toString(),
            "newSiteId",
            siteId.toString(),
            "reason",
            "Receiving changed",
            "instructions",
            "Use dock 4"));
  }

  Map<String, Object> cancellation(long version, boolean active) {
    var r =
        new HashMap<String, Object>(
            Map.of("expectedVersion", version, "reason", "Remaining work cancelled"));
    if (active) {
      r.put("resolutionType", "HOLD_CONTACT_DISPATCH");
      r.put("instructions", "Hold safely and call dispatch");
    }
    return r;
  }

  HttpResponse<String> reroute(UUID trip, Map<String, Object> r, String actor) throws Exception {
    return http("POST", base() + "/trips/" + trip + "/reroutes", actor, json(r));
  }

  HttpResponse<String> cancellation(UUID trip, Map<String, Object> r, String actor)
      throws Exception {
    return http("POST", base() + "/trips/" + trip + "/cancellations", actor, json(r));
  }

  long rows(String table, UUID trip) throws Exception {
    return (Long) f.db.value("SELECT count(*) FROM " + table + " WHERE trip_id=?", trip);
  }

  String row(String table, UUID id) throws Exception {
    return (String) f.db.value("SELECT to_jsonb(t)::text FROM " + table + " t WHERE id=?", id);
  }

  HttpResponse<String> updates(String actor, String query) throws Exception {
    return http("GET", base() + "/driver/me/updates" + query, actor, "");
  }

  UUID link(String token, UUID d) throws Exception {
    UUID u = actor(token, "DRIVER");
    f.db.execute("UPDATE drivers SET user_id=? WHERE id=?", u, d);
    return u;
  }

  void scopeDispatcher() throws Exception {
    f.db.execute(
        "UPDATE role_assignments SET customer_id=? WHERE tenant_id=? AND"
            + " role_code='CUSTOMER_DISPATCHER'",
        customer,
        tenant);
  }

  void denial(String state, String sql, Object... args) {
    assertThatThrownBy(() -> f.db.execute(sql, args))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(state));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PLANNED",
        "ASSIGNED",
        "IN_TRANSIT",
        "AT_STOP",
        "CANCELLING",
        "COMPLETED",
        "CANCELLED"
      })
  void rerouteTripStatesPreservePhysicalProjectionAndOldDestination(String status)
      throws Exception {
    var trip = create(true, true);
    state(trip, status);
    var stop = stopId(trip);
    var before = row("trip_stops", stop);
    var physical = row("trailers", trailer);
    var orderBefore = row("orders", order);
    var beforeUpdates = rows("trip_driver_updates", trip);
    var r = reroute(trip, route(trip, site, 0), "ops");
    if (Set.of("IN_TRANSIT", "AT_STOP").contains(status)) {
      assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
      assertThat(field(trip, "status")).isEqualTo(status);
      assertThat(field(trip, "version")).isEqualTo(1L);
      assertThat(f.db.value("SELECT site_id FROM trip_stops WHERE id=?", stop)).isEqualTo(site);
      assertThat(f.db.value("SELECT version FROM trip_stops WHERE id=?", stop)).isEqualTo(1L);
      var e = JsonPath.<Map<String, Object>>read(r.body(), "$.operationalHistory[0]");
      assertThat(e)
          .containsEntry("eventType", "TRIP_REROUTED")
          .containsEntry(
              "actorUserId",
              f.db.value(
                  "SELECT user_id::text FROM tenant_memberships m JOIN role_assignments r ON"
                      + " r.membership_id=m.id WHERE m.tenant_id=? AND"
                      + " r.role_code='TRANSPORT_OPERATIONS'",
                  tenant));
      assertThat(e.get("occurredAt")).isNotNull();
      assertThat(
              JsonPath.<String>read(
                  r.body(), "$.operationalHistory[0].metadata.previous.siteSnapshot.name"))
          .isEqualTo("Frozen executable");
      assertThat(
              JsonPath.<String>read(
                  r.body(), "$.operationalHistory[0].metadata.replacement.siteId"))
          .isEqualTo(site.toString());
      assertThat(rows("trip_driver_updates", trip)).isEqualTo(beforeUpdates + 1);
    } else {
      problem(r, 409, "INVALID_STATE_TRANSITION");
      assertThat(row("trip_stops", stop)).isEqualTo(before);
      assertThat(rows("trip_operational_events", trip)).isZero();
      assertThat(rows("trip_driver_updates", trip)).isEqualTo(beforeUpdates);
    }
    assertThat(row("trailers", trailer)).isEqualTo(physical);
    assertThat(row("orders", order)).isEqualTo(orderBefore);
    assertThat(f.db.value("SELECT count(*) FROM trip_stop_orders WHERE trip_stop_id=?", stop))
        .isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PENDING",
        "EN_ROUTE",
        "ARRIVED",
        "IN_SERVICE",
        "EXCEPTION",
        "COMPLETED",
        "CANCELLED"
      })
  void onlyFutureStopsCanRerouteAndOthersRemainByteForByte(String status) throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var stop = stopId(trip);
    f.db.execute(
        "UPDATE trip_stops SET status=?,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL"
            + " END WHERE id=?",
        status,
        status,
        stop);
    var before = row("trip_stops", stop);
    var r = reroute(trip, route(trip, site, 0), "admin");
    if (Set.of("PENDING", "EN_ROUTE").contains(status)) {
      assertThat(r.statusCode()).isEqualTo(200);
      assertThat(JsonPath.<String>read(r.body(), "$.stops[0].status")).isEqualTo(status);
    } else {
      problem(r, 409, "INVALID_STATE_TRANSITION");
      assertThat(row("trip_stops", stop)).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PLANNED",
        "ASSIGNED",
        "IN_TRANSIT",
        "AT_STOP",
        "CANCELLING",
        "COMPLETED",
        "CANCELLED"
      })
  void cancellationStateMachinePreservesOrdersMappingsAndTrailer(String status) throws Exception {
    var trip = create(true, true);
    state(trip, status);
    var stop = stopId(trip);
    var before = row("trip_stops", stop);
    var physical = row("trailers", trailer);
    var orderBefore = row("orders", order);
    var history =
        f.db.value(
            "SELECT jsonb_agg(to_jsonb(e) ORDER BY id)::text FROM trip_assignment_events e WHERE"
                + " trip_id=?",
            trip);
    var r = cancellation(trip, cancellation(0, true), "admin");
    if (Set.of("PLANNED", "ASSIGNED", "IN_TRANSIT", "AT_STOP").contains(status)) {
      boolean active = Set.of("IN_TRANSIT", "AT_STOP").contains(status);
      assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
      assertThat(field(trip, "status")).isEqualTo(active ? "CANCELLING" : "CANCELLED");
      assertThat(field(trip, "version")).isEqualTo(1L);
      assertThat(field(trip, "completed_at")).isNull();
      if (active) {
        assertThat(field(trip, "cancelled_at")).isNull();
        assertThat(row("trip_stops", stop)).isEqualTo(before);
      } else {
        assertThat(field(trip, "cancelled_at")).isNotNull();
        assertThat(field(trip, "started_at")).isNull();
        assertThat(f.db.value("SELECT status FROM trip_stops WHERE id=?", stop))
            .isEqualTo("CANCELLED");
      }
      assertThat(rows("trip_operational_events", trip)).isEqualTo(1);
      assertThat(rows("trip_driver_updates", trip)).isEqualTo(2);
    } else {
      problem(r, 409, "INVALID_STATE_TRANSITION");
      assertThat(row("trip_stops", stop)).isEqualTo(before);
      assertThat(rows("trip_operational_events", trip)).isZero();
    }
    assertThat(row("orders", order)).isEqualTo(orderBefore);
    assertThat(row("trailers", trailer)).isEqualTo(physical);
    assertThat(
            f.db.value(
                "SELECT jsonb_agg(to_jsonb(e) ORDER BY id)::text FROM trip_assignment_events e"
                    + " WHERE trip_id=?",
                trip))
        .isEqualTo(history);
    assertThat(f.db.value("SELECT count(*) FROM trip_stop_orders WHERE trip_stop_id=?", stop))
        .isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PENDING",
        "EN_ROUTE",
        "EXCEPTION",
        "COMPLETED",
        "CANCELLED",
        "ARRIVED",
        "IN_SERVICE"
      })
  void preMovementCancelsOnlyEligibleNonterminalStopProjection(String status) throws Exception {
    var trip = create(true, false);
    var stop = stopId(trip);
    f.db.execute("UPDATE trip_stops SET status=? WHERE id=?", status, stop);
    var before = row("trip_stops", stop);
    var r = cancellation(trip, cancellation(0, false), "admin");
    if (Set.of("ARRIVED", "IN_SERVICE").contains(status)) {
      problem(r, 409, "RESOURCE_CONFLICT");
      assertThat(field(trip, "status")).isEqualTo("ASSIGNED");
      assertThat(row("trip_stops", stop)).isEqualTo(before);
    } else {
      assertThat(r.statusCode()).isEqualTo(200);
      if (Set.of("COMPLETED", "CANCELLED").contains(status))
        assertThat(row("trip_stops", stop)).isEqualTo(before);
      else {
        assertThat(f.db.value("SELECT status FROM trip_stops WHERE id=?", stop))
            .isEqualTo("CANCELLED");
        assertThat(f.db.value("SELECT version FROM trip_stops WHERE id=?", stop)).isEqualTo(1L);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PENDING",
        "EN_ROUTE",
        "ARRIVED",
        "IN_SERVICE",
        "EXCEPTION",
        "COMPLETED",
        "CANCELLED"
      })
  void finishCancellationPreservesTerminalStopsExactlyAndRecordsNoFakeMovement(String status)
      throws Exception {
    var trip = create(true, false);
    state(trip, "CANCELLING");
    var stop = stopId(trip);
    f.db.execute("UPDATE trip_stops SET status=? WHERE id=?", status, stop);
    var before = row("trip_stops", stop);
    var started = field(trip, "started_at");
    var r = transactions.execute(tenant, () -> finish.finishCancellation(tenant, trip));
    assertThat(r.status()).isEqualTo("CANCELLED");
    assertThat(r.version()).isEqualTo(1);
    assertThat(r.cancelledAt()).isNotNull();
    assertThat(field(trip, "started_at")).isEqualTo(started);
    assertThat(field(trip, "completed_at")).isNull();
    if (Set.of("COMPLETED", "CANCELLED").contains(status))
      assertThat(row("trip_stops", stop)).isEqualTo(before);
    else {
      assertThat(f.db.value("SELECT status FROM trip_stops WHERE id=?", stop))
          .isEqualTo("CANCELLED");
      assertThat(f.db.value("SELECT version FROM trip_stops WHERE id=?", stop)).isEqualTo(1L);
    }
    assertThat(
            f.db.value("SELECT metadata::text FROM trip_operational_events WHERE trip_id=?", trip))
        .isEqualTo("{\"previousState\": \"CANCELLING\"}");
    assertThat(rows("trip_driver_updates", trip)).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "ASSIGNED", "IN_TRANSIT", "AT_STOP", "COMPLETED", "CANCELLED"})
  void finalPortRejectsOtherSourceStates(String status) throws Exception {
    var trip = create(false, false);
    state(trip, status);
    assertThatThrownBy(
            () -> transactions.execute(tenant, () -> finish.finishCancellation(tenant, trip)))
        .isInstanceOfSatisfying(
            TripFailure.class, e -> assertThat(e.code).isEqualTo("INVALID_STATE_TRANSITION"));
    assertThat(rows("trip_operational_events", trip)).isZero();
  }

  @Test
  void rerouteFreezesEveryDestinationAndTimelineMergesWithUuidTieBreaker() throws Exception {
    var trip = create(true, true);
    state(trip, "AT_STOP");
    var r1 = reroute(trip, route(trip, site, 0), "admin");
    assertThat(r1.statusCode()).isEqualTo(200);
    var frozen =
        JsonPath.<Object>read(
            r1.body(), "$.operationalHistory[0].metadata.replacement.siteSnapshot");
    f.db.execute(
        "UPDATE physical_sites SET name='Changed master',city='Different city' WHERE id=?", site);
    var c = f.site(tenant, customer);
    var r2 = reroute(trip, route(trip, c, 1), "admin");
    assertThat(r2.statusCode()).isEqualTo(200);
    assertThat(
            JsonPath.<Object>read(
                r2.body(), "$.operationalHistory[0].metadata.replacement.siteSnapshot"))
        .isEqualTo(frozen);
    assertThat(
            JsonPath.<List<String>>read(
                r2.body(), "$.operationalHistory[*].metadata.replacement.siteId"))
        .containsExactly(site.toString(), c.toString());
    assertThat(JsonPath.<String>read(r2.body(), "$.operationalHistory[1].metadata.previous.siteId"))
        .isEqualTo(site.toString());
    var timeline = http("GET", base() + "/trips/" + trip + "/timeline", "admin", "");
    assertThat(JsonPath.<List<?>>read(timeline.body(), "$.items")).hasSize(5);
    assertThat(timeline.body()).doesNotContain("tenantId", "updateType", "createdAt");
    var at = Timestamp.from(java.time.Instant.parse("2026-10-01T10:00:00Z"));
    var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    f.db.execute(
        "INSERT INTO trip_operational_events(id,tenant_id,trip_id,event_type,occurred_at)"
            + " VALUES(?,?,?,'TRIP_CANCELLED',?)",
        low,
        tenant,
        trip,
        at);
    f.db.execute(
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,driver_id,occurred_at)"
            + " VALUES(?,?,?,'DRIVER_ASSIGNED',?,?)",
        high,
        tenant,
        trip,
        driver,
        at);
    timeline = http("GET", base() + "/trips/" + trip + "/timeline", "admin", "");
    assertThat(JsonPath.<List<String>>read(timeline.body(), "$.items[*].id").subList(0, 2))
        .containsExactly(low.toString(), high.toString());
  }

  @Test
  void driversRetrieveOnlyExplicitlyTargetedUpdatesIncludingReassignmentAway() throws Exception {
    link("d1", driver);
    var d2 = target("driver");
    link("d2", d2);
    var trip = create(true, true);
    assertThat(JsonPath.<List<?>>read(updates("d1", "").body(), "$.items")).hasSize(1);
    assertThat(JsonPath.<List<?>>read(updates("d2", "").body(), "$.items")).isEmpty();
    var r = assign(trip, "driver", d2, 0, "admin");
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(field(trip, "current_driver_id")).isEqualTo(d2);
    var old = updates("d1", "");
    assertThat(old.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<List<Boolean>>read(old.body(), "$.items[*].payload.currentAssignment"))
        .containsExactly(true, false);
    assertThat(old.body()).doesNotContain(d2.toString(), "actorUserId", "customerId", "tenantId");
    assertThat(JsonPath.<List<?>>read(updates("d2", "").body(), "$.items")).hasSize(1);
    problem(http("GET", base() + "/trips/" + trip, "d1", ""), 403, "CAPABILITY_DENIED");
    f.db.execute("UPDATE drivers SET active=false WHERE id=?", driver);
    problem(updates("d1", ""), 403, "CAPABILITY_DENIED");
  }

  @Test
  void substitutionsAndTractorChangesProduceDurableCurrentExpectation() throws Exception {
    link("d1", driver);
    var trip = create(true, true);
    var t2 = target("trailer");
    assertThat(decide(trip, trailer, t2, "AUTHORIZE", "admin").statusCode()).isEqualTo(200);
    var t3 = target("trailer");
    assertThat(decide(trip, t2, t3, "REJECT", "admin").statusCode()).isEqualTo(200);
    assertThat(assign(trip, "tractor", target("tractor"), 2, "admin").statusCode()).isEqualTo(200);
    var r = updates("d1", "");
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(
            JsonPath.<List<String>>read(
                r.body(),
                "$.items[?(@.updateType == 'TRAILER_EXPECTATION_CHANGED')].payload.decision"))
        .containsExactly("AUTHORIZE", "REJECT");
    assertThat(
            JsonPath.<List<String>>read(
                r.body(),
                "$.items[?(@.updateType =="
                    + " 'TRAILER_EXPECTATION_CHANGED')].payload.expectedTrailerId"))
        .containsExactly(t2.toString(), t2.toString());
    assertThat(JsonPath.<List<?>>read(r.body(), "$.items")).hasSize(4);
  }

  @Test
  void driverUpdateCursorIsIncrementalStrictAndScopedOnEveryPage() throws Exception {
    link("d1", driver);
    var trip = create(true, false);
    var at = Timestamp.from(java.time.Instant.parse("2026-01-01T10:00:00Z"));
    var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    for (var id : List.of(high, low))
      f.db.execute(
          "INSERT INTO"
              + " trip_driver_updates(id,tenant_id,trip_id,driver_id,update_type,occurred_at,payload,created_at)"
              + " VALUES(?,?,?,?,'TRIP_ASSIGNMENT_CHANGED',?,'{}',?)",
          id,
          tenant,
          trip,
          driver,
          at,
          at);
    var r = updates("d1", "?limit=1");
    assertThat(JsonPath.<String>read(r.body(), "$.items[0].id")).isEqualTo(low.toString());
    var cursor = JsonPath.<String>read(r.body(), "$.nextCursor");
    r = updates("d1", "?limit=1&cursor=" + cursor);
    assertThat(JsonPath.<String>read(r.body(), "$.items[0].id")).isEqualTo(high.toString());
    cursor = JsonPath.read(r.body(), "$.nextCursor");
    r = updates("d1", "?limit=1&cursor=" + cursor);
    assertThat(JsonPath.<Object>read(r.body(), "$.nextCursor")).isNull();
    for (var q :
        List.of("?driverId=" + driver, "?cursor=bad", "?limit=201", "?limit=0", "?active=true"))
      problem(updates("d1", q), 400, "VALIDATION_FAILED");
    problem(updates(null, ""), 401, "AUTHENTICATION_REQUIRED");
    problem(updates("admin", ""), 403, "CAPABILITY_DENIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"RETURN_TO_BASE", "REROUTE", "HOLD_CONTACT_DISPATCH", "OTHER_AUTHORIZED"})
  void activeResolutionCatalogAndDestinationPolicy(String type) throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var neutralBase = f.site(tenant, null);
    var r = cancellation(0, true);
    r.put("resolutionType", type);
    if (!type.equals("HOLD_CONTACT_DISPATCH"))
      r.put("destinationSiteId", (type.equals("REROUTE") ? site : neutralBase).toString());
    var response = cancellation(trip, r, "admin");
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    assertThat(
            JsonPath.<String>read(
                response.body(), "$.operationalHistory[0].metadata.resolutionType"))
        .isEqualTo(type);
  }

  @ParameterizedTest
  @ValueSource(strings = {"instructions", "destination", "both"})
  void preMovementOptionalResolutionFieldsRemainOptionalAndArePreserved(String mode)
      throws Exception {
    var trip = create(true, false);
    var request = cancellation(0, false);
    if (!mode.equals("destination")) request.put("instructions", "Contact dispatch before work");
    if (!mode.equals("instructions")) request.put("destinationSiteId", site.toString());
    var response = cancellation(trip, request, "admin");
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    assertThat(field(trip, "status")).isEqualTo("CANCELLED");
    var metadata =
        JsonPath.<Map<String, Object>>read(response.body(), "$.operationalHistory[0].metadata");
    assertThat(metadata)
        .containsEntry("previousState", "ASSIGNED")
        .doesNotContainKey("resolutionType");
    if (!mode.equals("destination"))
      assertThat(metadata).containsEntry("instructions", "Contact dispatch before work");
    if (!mode.equals("instructions"))
      assertThat(metadata).containsEntry("destinationSiteId", site.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missingType",
        "missingInstructions",
        "blankInstructions",
        "missingBase",
        "missingReroute",
        "holdHasSite",
        "unknownType",
        "unknownField",
        "negativeVersion",
        "fractionVersion"
      })
  void activeCancellationStrictValidation(String mode) throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var r = cancellation(0, true);
    switch (mode) {
      case "missingType" -> r.remove("resolutionType");
      case "missingInstructions" -> r.remove("instructions");
      case "blankInstructions" -> r.put("instructions", " ");
      case "missingBase" -> r.put("resolutionType", "RETURN_TO_BASE");
      case "missingReroute" -> r.put("resolutionType", "REROUTE");
      case "holdHasSite" -> r.put("destinationSiteId", site.toString());
      case "unknownType" -> r.put("resolutionType", "FAKE");
      case "unknownField" -> r.put("actorUserId", user.toString());
      case "negativeVersion" -> r.put("expectedVersion", -1);
      default -> r.put("expectedVersion", 0.5);
    }
    problem(cancellation(trip, r, "admin"), 400, "VALIDATION_FAILED");
    assertThat(field(trip, "status")).isEqualTo("IN_TRANSIT");
    assertThat(rows("trip_operational_events", trip)).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "driver",
        "yard",
        "foreign",
        "mixed",
        "suspended",
        "roleRemoved",
        "customerRemoved",
        "claims"
      })
  void freshScopeCannotWidenThroughTripOrFirebase(String mode) throws Exception {
    scopeDispatcher();
    var body = plan();
    if (mode.equals("mixed")) {
      body.put("orderIds", List.of(order.toString(), orderB.toString()));
      body.put("stops", List.of(stop(2, List.of(order, orderB))));
    }
    var trip = id(post(body));
    state(trip, "AT_STOP");
    String token = "dispatcher";
    String code = "RESOURCE_NOT_FOUND";
    int status = 404;
    if (mode.equals("driver") || mode.equals("yard")) {
      token = mode;
      status = 403;
      code = "CAPABILITY_DENIED";
    } else if (mode.equals("foreign")) {
      f.db.execute(
          "UPDATE role_assignments SET customer_id=? WHERE tenant_id=? AND"
              + " role_code='CUSTOMER_DISPATCHER'",
          customerB,
          tenant);
    } else if (mode.equals("suspended")) {
      f.db.execute(
          "UPDATE tenant_memberships SET status='SUSPENDED' WHERE id IN(SELECT membership_id FROM"
              + " role_assignments WHERE tenant_id=? AND role_code='CUSTOMER_DISPATCHER')",
          tenant);
      status = 403;
      code = "TENANT_ACCESS_DENIED";
    } else if (mode.equals("roleRemoved") || mode.equals("customerRemoved")) {
      f.db.execute(
          "DELETE FROM role_assignments WHERE tenant_id=? AND role_code='CUSTOMER_DISPATCHER'",
          tenant);
      status = 403;
      code = "CAPABILITY_DENIED";
    } else if (mode.equals("claims")) {
      token = "fakeClaim";
      var u = actor(token, "DRIVER");
      when(verifier.verify(token))
          .thenReturn(
              new VerifiedFirebaseIdentity(
                  "fixture-" + u, "not-authority@example.com", "TENANT_ADMIN"));
      status = 403;
      code = "CAPABILITY_DENIED";
    }
    problem(reroute(trip, route(trip, site, 0), token), status, code);
    problem(cancellation(trip, cancellation(0, true), token), status, code);
    assertThat(rows("trip_operational_events", trip)).isZero();
  }

  @Test
  void scopedCommandOwnCustomersWorksButCannotBrowseTrip() throws Exception {
    scopeDispatcher();
    var trip = create(true, false);
    state(trip, "AT_STOP");
    assertThat(reroute(trip, route(trip, site, 0), "dispatcher").statusCode()).isEqualTo(200);
    assertThat(cancellation(trip, cancellation(1, true), "dispatcher").statusCode()).isEqualTo(200);
    problem(http("GET", base() + "/trips/" + trip, "dispatcher", ""), 403, "CAPABILITY_DENIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"reroute", "cancel", "mixed"})
  void sameVersionConcurrentCommandsHaveOneWinnerNoPartialEvents(String kind) throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                waitFor(start);
                return kind.equals("cancel")
                    ? cancellation(trip, cancellation(0, true), "admin")
                    : reroute(trip, route(trip, site, 0), "admin");
              });
      var b =
          pool.submit(
              () -> {
                waitFor(start);
                return kind.equals("reroute")
                    ? reroute(trip, route(trip, site, 0), "admin")
                    : cancellation(trip, cancellation(0, true), "admin");
              });
      start.countDown();
      var results = List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
      assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
      problem(
          results.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow(),
          409,
          "STALE_VERSION");
    }
    assertThat(field(trip, "version")).isEqualTo(1L);
    assertThat(rows("trip_operational_events", trip)).isEqualTo(1);
    assertThat(rows("trip_driver_updates", trip)).isEqualTo(2);
  }

  @ParameterizedTest
  @CsvSource({
    "reroute,trip_operational_events,INSERT",
    "reroute,trip_driver_updates,INSERT",
    "reroute,trip_stops,UPDATE",
    "reroute,trips,UPDATE",
    "cancel,trip_operational_events,INSERT",
    "cancel,trip_driver_updates,INSERT",
    "cancel,trip_stops,UPDATE",
    "cancel,trips,UPDATE"
  })
  void infrastructureFailureRollsBackEveryProjectionAndDurableWrite(
      String command, String table, String operation) throws Exception {
    var trip = create(true, false);
    if (command.equals("reroute")) state(trip, "IN_TRANSIT");
    var stop = stopId(trip);
    var t = row("trips", trip);
    var s = row("trip_stops", stop);
    var updates = rows("trip_driver_updates", trip);
    f.db.execute(
        "CREATE FUNCTION issue36_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE"
            + " EXCEPTION 'controlled infrastructure failure' USING ERRCODE='P0001'; END $$");
    f.db.execute(
        "CREATE TRIGGER issue36_fail BEFORE "
            + operation
            + " ON "
            + table
            + " FOR EACH ROW EXECUTE FUNCTION issue36_fail()");
    try {
      problem(
          command.equals("reroute")
              ? reroute(trip, route(trip, site, 0), "admin")
              : cancellation(trip, cancellation(0, false), "admin"),
          500,
          "INTERNAL_ERROR");
      assertThat(row("trips", trip)).isEqualTo(t);
      assertThat(row("trip_stops", stop)).isEqualTo(s);
      assertThat(rows("trip_operational_events", trip)).isZero();
      assertThat(rows("trip_driver_updates", trip)).isEqualTo(updates);
    } finally {
      f.db.execute("DROP TRIGGER issue36_fail ON " + table);
      f.db.execute("DROP FUNCTION issue36_fail()");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "trailer", "authorize", "reject", "create"})
  void updateSinkFailureRollsBackAssignmentsIncludingInitialCreate(String kind) throws Exception {
    var trip = create(true, true);
    var before = row("trips", trip);
    var history = historyCount(trip);
    var updates = rows("trip_driver_updates", trip);
    f.db.execute(
        "CREATE FUNCTION issue36_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE"
            + " EXCEPTION 'controlled update failure' USING ERRCODE='P0001'; END $$");
    f.db.execute(
        "CREATE TRIGGER issue36_fail BEFORE INSERT ON trip_driver_updates FOR EACH ROW EXECUTE"
            + " FUNCTION issue36_fail()");
    try {
      HttpResponse<String> r;
      if (kind.equals("create")) {
        var b = plan();
        b.put("driverId", driver.toString());
        r = post(b);
      } else if (kind.equals("authorize") || kind.equals("reject"))
        r =
            decide(
                trip,
                trailer,
                target("trailer"),
                kind.equals("authorize") ? "AUTHORIZE" : "REJECT",
                "admin");
      else r = assign(trip, kind, target(kind), 0, "admin");
      problem(r, 500, "INTERNAL_ERROR");
      assertThat(row("trips", trip)).isEqualTo(before);
      assertThat(historyCount(trip)).isEqualTo(history);
      assertThat(rows("trip_driver_updates", trip)).isEqualTo(updates);
      assertThat(count("trips")).isEqualTo(1);
    } finally {
      f.db.execute("DROP TRIGGER issue36_fail ON trip_driver_updates");
      f.db.execute("DROP FUNCTION issue36_fail()");
    }
  }

  @Test
  void exactV15ColumnsGrantsRlsAndNoFutureTables() throws Exception {
    flyway.validate();
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
    var columns =
        Map.of(
            "trip_operational_events",
            List.of(
                "id",
                "tenant_id",
                "trip_id",
                "trip_stop_id",
                "event_type",
                "actor_user_id",
                "target_driver_id",
                "occurred_at",
                "reason",
                "metadata"),
            "trip_driver_updates",
            List.of(
                "id",
                "tenant_id",
                "trip_id",
                "driver_id",
                "update_type",
                "occurred_at",
                "payload",
                "created_at"));
    for (var table : columns.keySet()) {
      try (var c = runtime.getConnection();
          var q =
              c.prepareStatement(
                  "SELECT column_name FROM information_schema.columns WHERE table_name=? ORDER BY"
                      + " ordinal_position")) {
        q.setString(1, table);
        var names = new ArrayList<String>();
        try (var r = q.executeQuery()) {
          while (r.next()) names.add(r.getString(1));
        }
        assertThat(names).isEqualTo(columns.get(table));
      }
      assertThat(f.db.value("SELECT relrowsecurity FROM pg_class WHERE oid=?::regclass", table))
          .isEqualTo(true);
      assertThat(
              f.db.value(
                  "SELECT pg_get_userbyid(relowner)=? FROM pg_class WHERE oid=?::regclass",
                  RUNTIME_USERNAME,
                  table))
          .isEqualTo(false);
      for (var privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE"))
        assertThat(
                f.db.value("SELECT has_table_privilege(?,?,?)", RUNTIME_USERNAME, table, privilege))
            .isEqualTo(Set.of("SELECT", "INSERT").contains(privilege));
      assertThat(
              f.db.value(
                  "SELECT count(*) FROM pg_constraint WHERE conrelid=?::regclass AND contype='f'"
                      + " AND confdeltype='c'",
                  table))
          .isEqualTo(0L);
      assertThat(
              f.db.value(
                  "SELECT count(*) FROM information_schema.table_privileges WHERE table_name=? AND"
                      + " grantee='PUBLIC'",
                  table))
          .isEqualTo(0L);
    }
    assertThat(
            f.db.value(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=?", RUNTIME_USERNAME))
        .isEqualTo(false);
    assertThat(f.db.value("SELECT has_schema_privilege(?,'public','CREATE')", RUNTIME_USERNAME))
        .isEqualTo(false);
    for (var table :
        List.of(
            "outbox_jobs",
            "audit_events"))
      assertThat(f.db.value("SELECT to_regclass(?)", table)).isNull();
    assertThat(f.db.value("SELECT to_regclass('trip_stop_events')::text")).isEqualTo("trip_stop_events");
    assertThat(f.db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(f.db.value("SELECT count(*) FROM trailer_couplings WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(f.db.value("SELECT count(*) FROM trailer_movement_events WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }

  @Test
  void operationalRowsRequireTenantContextAndAreAppendOnly() throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    assertThat(reroute(trip, route(trip, site, 0), "admin").statusCode()).isEqualTo(200);
    for (var table : List.of("trip_operational_events", "trip_driver_updates"))
      try (var c = runtime.getConnection()) {
        assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        c.setAutoCommit(false);
        assertThat(runtimeCount(c, table)).isZero();
        context(c, null, user);
        assertThat(runtimeCount(c, table)).isZero();
        c.rollback();
        context(c, tenant, user);
        assertThat(runtimeCount(c, table)).isZero();
        c.rollback();
        context(c, other, null);
        assertThat(runtimeCount(c, table)).isZero();
        c.rollback();
        context(c, tenant, null);
        assertThat(runtimeCount(c, table))
            .isEqualTo(table.equals("trip_operational_events") ? 1 : 2);
        for (var sql : List.of("UPDATE " + table + " SET id=id", "DELETE FROM " + table)) {
          assertThatThrownBy(() -> f.db.execute(c, sql))
              .isInstanceOfSatisfying(
                  SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
          c.rollback();
          context(c, tenant, null);
        }
        assertThatThrownBy(
                () ->
                    f.db.execute(
                        c,
                        "INSERT INTO"
                            + " trip_operational_events(id,tenant_id,trip_id,event_type,occurred_at)"
                            + " VALUES(?,?,?,'TRIP_CANCELLED',now())",
                        UUID.randomUUID(),
                        other,
                        trip))
            .isInstanceOfSatisfying(
                SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
        c.rollback();
      }
    assertThatThrownBy(
            () -> transactions.execute(other, () -> finish.finishCancellation(tenant, trip)))
        .isInstanceOf(TripFailure.class);
    assertThatThrownBy(() -> finish.finishCancellation(tenant, trip))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "trip",
        "stopOtherTrip",
        "driver",
        "actor",
        "missingStop",
        "cancellationStop",
        "unknownEvent",
        "updateDriver",
        "updateTrip",
        "unknownUpdate"
      })
  void sqlFinalBarrierRejectsBadOperationalReferencesAndCatalog(String kind) throws Exception {
    var trip = create(true, false);
    var stop = stopId(trip);
    var foreignTrip = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO trips(id,tenant_id,status,created_at,updated_at)"
            + " VALUES(?,?,'PLANNED',now(),now())",
        foreignTrip,
        other);
    var trip2 = create(false, false);
    var secondStop = stopId(trip2);
    var target = driver;
    var actorId = user;
    var event = "TRIP_REROUTED";
    UUID stopRef = stop;
    String sqlState = "23503";
    switch (kind) {
      case "trip" -> trip = foreignTrip;
      case "stopOtherTrip" -> stopRef = secondStop;
      case "driver" -> target = f.db.driver(other, null);
      case "actor" -> actorId = UUID.randomUUID();
      case "missingStop" -> {
        stopRef = null;
        sqlState = "23514";
      }
      case "cancellationStop" -> {
        event = "TRIP_CANCELLED";
        sqlState = "23514";
      }
      case "unknownEvent" -> {
        event = "FAKE";
        sqlState = "23514";
      }
      default -> {}
    }
    if (kind.startsWith("update") || kind.equals("unknownUpdate")) {
      denial(
          kind.equals("unknownUpdate") ? "23514" : "23503",
          "INSERT INTO"
              + " trip_driver_updates(id,tenant_id,trip_id,driver_id,update_type,occurred_at,payload,created_at)"
              + " VALUES(?,?,?,?,?,now(),'{}',now())",
          UUID.randomUUID(),
          tenant,
          kind.equals("updateTrip") ? foreignTrip : trip,
          kind.equals("updateDriver") ? f.db.driver(other, null) : driver,
          kind.equals("unknownUpdate") ? "FAKE" : "TRIP_REROUTED");
    } else
      denial(
          sqlState,
          "INSERT INTO"
              + " trip_operational_events(id,tenant_id,trip_id,trip_stop_id,event_type,actor_user_id,target_driver_id,occurred_at)"
              + " VALUES(?,?,?,?,?,?,?,now())",
          UUID.randomUUID(),
          tenant,
          trip,
          stopRef,
          event,
          actorId,
          target);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rerouteAndAssociationRemovalUseSharedExclusiveSiteParentProtocol(boolean routeFirst)
      throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var destination = f.site(tenant, customer);
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var request = route(trip, destination, 0);
    try (var pool = Executors.newFixedThreadPool(2)) {
      if (routeFirst) {
        var first =
            pool.submit(
                () ->
                    transactions.execute(
                        tenant,
                        () -> {
                          var r = operations.reroute(admin, tenant, trip, request);
                          held.countDown();
                          waitFor(release);
                          return r;
                        }));
        waitFor(held);
        var second =
            pool.submit(
                () ->
                    http(
                        "DELETE",
                        base() + "/customers/" + customer + "/sites/" + destination,
                        "admin",
                        ""));
        try {
          lockWait("physical_sites");
        } finally {
          release.countDown();
        }
        assertThat(first.get(25, TimeUnit.SECONDS).version()).isEqualTo(1);
        problem(second.get(25, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
      } else {
        // Only migrator fixture removal supplies the existing exclusive parent lock; production has
        // no bypass.
        var remove =
            pool.submit(
                () -> {
                  try (var c = flyway.getConfiguration().getDataSource().getConnection()) {
                    c.setAutoCommit(false);
                    try (var q =
                        c.prepareStatement("SELECT id FROM physical_sites WHERE id=? FOR UPDATE")) {
                      q.setObject(1, destination);
                      q.executeQuery().close();
                    }
                    f.db.execute(
                        c,
                        "DELETE FROM customer_sites WHERE tenant_id=? AND customer_id=? AND"
                            + " site_id=?",
                        tenant,
                        customer,
                        destination);
                    held.countDown();
                    waitFor(release);
                    c.commit();
                    return true;
                  }
                });
        waitFor(held);
        var second = pool.submit(() -> reroute(trip, request, "admin"));
        try {
          lockWait("physical_sites");
        } finally {
          release.countDown();
        }
        assertThat(remove.get(25, TimeUnit.SECONDS)).isTrue();
        problem(second.get(25, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
        assertThat(field(trip, "version")).isEqualTo(0L);
        assertThat(rows("trip_operational_events", trip)).isZero();
        assertThat(rows("trip_driver_updates", trip)).isEqualTo(1);
      }
    } finally {
      release.countDown();
    }
  }

  @Test
  void orderCancellationWaitsOnTripWithoutReverseOrderLockOrHistoryLoss() throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var request = route(trip, site, 0);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var r = operations.reroute(admin, tenant, trip, request);
                        held.countDown();
                        waitFor(release);
                        return r;
                      }));
      waitFor(held);
      var second = pool.submit(() -> cancel(1));
      try {
        lockWait("trips");
      } finally {
        release.countDown();
      }
      assertThat(first.get(25, TimeUnit.SECONDS).version()).isEqualTo(1);
      problem(second.get(25, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
      assertThat(field(trip, "status")).isEqualTo("IN_TRANSIT");
      assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("PLANNED");
      assertThat(
              f.db.value(
                  "SELECT count(*) FROM trip_stop_orders WHERE trip_stop_id=?", stopId(trip)))
          .isEqualTo(1L);
    } finally {
      release.countDown();
    }
  }

  @Test
  void rerouteDestinationRejectsForeignAndMixedCustomerSpecificLocation() throws Exception {
    var request = plan();
    request.put("orderIds", List.of(order.toString(), orderB.toString()));
    request.put("stops", List.of(stop(2, List.of(order, orderB))));
    var trip = id(post(request));
    state(trip, "IN_TRANSIT");
    var route = route(trip, site, 0);
    route.put("newLocationId", f.location(tenant, site, customer).toString());
    problem(reroute(trip, route, "admin"), 409, "RESOURCE_CONFLICT");
    route.put("newLocationId", neutral.toString());
    assertThat(reroute(trip, route, "admin").statusCode()).isEqualTo(200);
    route = route(trip, f.site(other, null), 1);
    problem(reroute(trip, route, "admin"), 404, "RESOURCE_NOT_FOUND");
  }

  @Test
  void completedStopSurvivesActiveCancellationStartAndFinishExactly() throws Exception {
    var trip = create(true, false);
    state(trip, "AT_STOP");
    var stop = stopId(trip);
    f.db.execute(
        "UPDATE trip_stops SET status='COMPLETED',completed_at=now(),departed_at=now() WHERE id=?",
        stop);
    var before = row("trip_stops", stop);
    assertThat(cancellation(trip, cancellation(0, true), "admin").statusCode()).isEqualTo(200);
    assertThat(row("trip_stops", stop)).isEqualTo(before);
    transactions.execute(tenant, () -> finish.finishCancellation(tenant, trip));
    assertThat(row("trip_stops", stop)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "version",
        "fraction",
        "actor",
        "missingStop",
        "foreignStop",
        "missingSite",
        "blankReason",
        "blankInstructions",
        "snapshot",
        "unknownQuery"
      })
  void rerouteStrictShapeAndInvisibleStopPairs(String mode) throws Exception {
    var trip = create(true, false);
    state(trip, "IN_TRANSIT");
    var r = route(trip, site, 0);
    int status = 400;
    String code = "VALIDATION_FAILED";
    switch (mode) {
      case "version" -> r.remove("expectedVersion");
      case "fraction" -> r.put("expectedVersion", 0.1);
      case "actor" -> r.put("actorUserId", user.toString());
      case "missingStop" -> {
        r.put("affectedStopId", UUID.randomUUID().toString());
        status = 404;
        code = "RESOURCE_NOT_FOUND";
      }
      case "foreignStop" -> {
        var otherTrip = create(false, false);
        r.put("affectedStopId", stopId(otherTrip).toString());
        status = 404;
        code = "RESOURCE_NOT_FOUND";
      }
      case "missingSite" -> r.remove("newSiteId");
      case "blankReason" -> r.put("reason", " ");
      case "blankInstructions" -> r.put("instructions", " ");
      case "snapshot" -> r.put("siteSnapshot", Map.of("name", "Unapproved"));
      default -> {}
    }
    var response =
        mode.equals("unknownQuery")
            ? http("POST", base() + "/trips/" + trip + "/reroutes?force=true", "admin", json(r))
            : reroute(trip, r, "admin");
    problem(response, status, code);
    assertThat(rows("trip_operational_events", trip)).isZero();
    assertThat(field(trip, "version")).isEqualTo(0L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"neutral", "specific", "wrongSite", "foreignLocation", "unassociated"})
  void tripOnlyRerouteNeverBorrowsCustomerCompatibility(String mode) throws Exception {
    var b = plan();
    b.put("stops", List.of(stop(2, List.of(order)), stop(3, List.of())));
    var trip = id(post(b));
    state(trip, "IN_TRANSIT");
    var operational =
        (UUID) f.db.value("SELECT id FROM trip_stops WHERE trip_id=? AND sequence_number=3", trip);
    var destination = f.site(tenant, null);
    var r =
        new HashMap<String, Object>(
            Map.of(
                "expectedVersion",
                0,
                "affectedStopId",
                operational.toString(),
                "newSiteId",
                destination.toString(),
                "reason",
                "Operational visit",
                "instructions",
                "Proceed safely"));
    if (mode.equals("specific")) {
      r.put("newSiteId", site.toString());
      r.put("newLocationId", f.location(tenant, site, customer).toString());
    } else if (mode.equals("wrongSite")) {
      r.put("newLocationId", neutral.toString());
    } else if (mode.equals("foreignLocation")) {
      r.put("newLocationId", f.location(other, f.site(other, null), null).toString());
    } else if (mode.equals("neutral")) {
      r.put("newLocationId", f.location(tenant, destination, null).toString());
    }
    var response = reroute(trip, r, "admin");
    if (Set.of("neutral", "unassociated").contains(mode))
      assertThat(response.statusCode()).isEqualTo(200);
    else problem(response, 409, "RESOURCE_CONFLICT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspended", "roleRevoked", "unlinked"})
  void driverUpdatesRequireFreshActiveAccessAndUserLink(String mode) throws Exception {
    var u = link("d1", driver);
    create(true, false);
    assertThat(updates("d1", "").statusCode()).isEqualTo(200);
    if (mode.equals("suspended"))
      f.db.execute(
          "UPDATE tenant_memberships SET status='SUSPENDED' WHERE tenant_id=? AND user_id=?",
          tenant,
          u);
    else if (mode.equals("roleRevoked"))
      f.db.execute(
          "DELETE FROM role_assignments WHERE membership_id IN(SELECT id FROM tenant_memberships"
              + " WHERE tenant_id=? AND user_id=?)",
          tenant,
          u);
    else f.db.execute("UPDATE drivers SET user_id=NULL WHERE id=?", driver);
    var r = updates("d1", "");
    assertThat(r.statusCode()).isEqualTo(403);
    assertThat(r.body()).doesNotContain("updateType", driver.toString());
  }

  @Test
  void assignmentAndCancellationSerializeAndKeepBothHistories() throws Exception {
    var trip = create(true, false);
    var next = target("driver");
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var r = assignment("driver", next, 0);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var result =
                            assignments.assign(
                                admin, tenant, trip, TripAssignmentService.Kind.DRIVER, r);
                        held.countDown();
                        waitFor(release);
                        return result;
                      }));
      waitFor(held);
      var second = pool.submit(() -> cancellation(trip, cancellation(0, false), "admin"));
      try {
        lockWait("trips");
      } finally {
        release.countDown();
      }
      assertThat(first.get(25, TimeUnit.SECONDS).version()).isEqualTo(1);
      problem(second.get(25, TimeUnit.SECONDS), 409, "STALE_VERSION");
      assertThat(rows("trip_operational_events", trip)).isZero();
      assertThat(cancellation(trip, cancellation(1, false), "admin").statusCode()).isEqualTo(200);
      assertThat(historyCount(trip)).isEqualTo(3);
      assertThat(rows("trip_operational_events", trip)).isEqualTo(1);
    } finally {
      release.countDown();
    }
  }
}
