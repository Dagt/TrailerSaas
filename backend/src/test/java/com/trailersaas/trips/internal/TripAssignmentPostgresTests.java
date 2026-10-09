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
class TripAssignmentPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired TripService service;
  @Autowired TripAssignmentService assignments;
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
    assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
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

  @Test
  void initialAndReassignmentHistoryPreservesResourcesActorReasonAndPreviousEvents()
      throws Exception {
    var trip = create(true, true);
    var initial = http("GET", base() + "/trips/" + trip, "admin", "");
    var original = history(initial);
    assertThat(original).hasSize(3);
    assertThat(JsonPath.<Number>read(initial.body(), "$.version").longValue()).isZero();
    assertThat(original)
        .allSatisfy(
            e -> {
              assertThat(e.get("actorUserId")).isEqualTo(user.toString());
              assertThat(e.get("reason")).isNull();
              assertThat(e.get("tenantId")).isNull();
              assertThat(e.get("occurredAt")).isNotNull();
            });
    long version = 0;
    for (var kind : List.of("driver", "tractor", "trailer")) {
      var next = target(kind);
      var previous = field(trip, projection(kind));
      var r = assign(trip, kind, next, version++, "ops");
      assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
      assertThat(field(trip, projection(kind))).isEqualTo(next);
      var newEvent = history(r).getLast();
      assertThat(newEvent.get("eventType"))
          .isEqualTo(
              kind.equals("trailer")
                  ? "TRAILER_PLANNED"
                  : kind.toUpperCase(Locale.ROOT) + "_REASSIGNED");
      assertThat(newEvent.get("reason")).isEqualTo("Dispatch change");
      assertThat(newEvent.get("metadata"))
          .isEqualTo(
              Map.of(
                  "previous" + Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + "Id",
                  previous.toString()));
      assertThat(history(r)).containsAll(original);
    }
    var timeline = http("GET", base() + "/trips/" + trip + "/timeline", "admin", "");
    assertThat(timeline.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<List<?>>read(timeline.body(), "$.items")).hasSize(6);
    assertThat(field(trip, "version")).isEqualTo(3L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "trailer"})
  void sameCurrentResourceIsNoOpAndStaleVersionIsStillRejected(String kind) throws Exception {
    var trip = create(true, true);
    var before = field(trip, "updated_at");
    var current = (UUID) field(trip, projection(kind));
    assertThat(assign(trip, kind, current, 0, "admin").statusCode()).isEqualTo(200);
    assertThat(field(trip, "version")).isEqualTo(0L);
    assertThat(field(trip, "updated_at")).isEqualTo(before);
    assertThat(historyCount(trip)).isEqualTo(3);
    problem(assign(trip, kind, current, 1, "admin"), 409, "STALE_VERSION");
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor"})
  void completingRequiredAssignmentsPromotesPlannedExactlyOnce(String first) throws Exception {
    var trip = create(false, false);
    var second = first.equals("driver") ? "tractor" : "driver";
    var r = assign(trip, first, target(first), 0, "admin");
    assertThat(JsonPath.<String>read(r.body(), "$.status")).isEqualTo("PLANNED");
    r = assign(trip, second, target(second), 1, "admin");
    assertThat(JsonPath.<String>read(r.body(), "$.status")).isEqualTo("ASSIGNED");
    assertThat(field(trip, "version")).isEqualTo(2L);
    assertThat(historyCount(trip)).isEqualTo(2);
  }

  @Test
  void trailerAloneCannotPromotePlannedAndExistingInactiveRequiredResourceCannotPromote()
      throws Exception {
    var trip = create(false, false);
    assertThat(assign(trip, "trailer", trailer, 0, "admin").statusCode()).isEqualTo(200);
    assertThat(field(trip, "status")).isEqualTo("PLANNED");
    assertThat(assign(trip, "driver", driver, 1, "admin").statusCode()).isEqualTo(200);
    f.db.execute("UPDATE drivers SET active=false WHERE id=?", driver);
    problem(assign(trip, "tractor", tractor, 2, "admin"), 409, "RESOURCE_CONFLICT");
    assertThat(field(trip, "current_tractor_id")).isNull();
    assertThat(historyCount(trip)).isEqualTo(2);
  }

  @ParameterizedTest
  @CsvSource({
    "IN_TRANSIT,driver",
    "AT_STOP,driver",
    "CANCELLING,driver",
    "IN_TRANSIT,tractor",
    "AT_STOP,tractor",
    "CANCELLING,tractor"
  })
  void activeDriverAndTractorReassignmentPreservesLifecycle(String state, String kind)
      throws Exception {
    var trip = create(true, true);
    f.db.execute("UPDATE trips SET status=? WHERE id=?", state, trip);
    assertThat(assign(trip, kind, target(kind), 0, "admin").statusCode()).isEqualTo(200);
    assertThat(field(trip, "status")).isEqualTo(state);
    assertThat(field(trip, "started_at")).isNull();
    assertThat(field(trip, "version")).isEqualTo(1L);
  }

  @ParameterizedTest
  @CsvSource({
    "COMPLETED,driver",
    "CANCELLED,driver",
    "COMPLETED,tractor",
    "CANCELLED,tractor",
    "COMPLETED,trailer",
    "CANCELLED,trailer",
    "CANCELLING,trailer"
  })
  void invalidAssignmentLifecycleHasNoWrites(String state, String kind) throws Exception {
    var trip = create(true, true);
    f.db.execute("UPDATE trips SET status=? WHERE id=?", state, trip);
    problem(assign(trip, kind, target(kind), 0, "admin"), 409, "INVALID_STATE_TRANSITION");
    assertThat(historyCount(trip)).isEqualTo(3);
    assertThat(field(trip, "version")).isEqualTo(0L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"IN_TRANSIT", "AT_STOP"})
  void activeOrdinaryTrailerCanFillUnknownButCannotReplaceExpected(String state) throws Exception {
    var trip = create(true, false);
    f.db.execute("UPDATE trips SET status=? WHERE id=?", state, trip);
    assertThat(assign(trip, "trailer", trailer, 0, "admin").statusCode()).isEqualTo(200);
    assertThat(assign(trip, "trailer", trailer, 1, "admin").statusCode()).isEqualTo(200);
    problem(
        assign(trip, "trailer", target("trailer"), 1, "admin"), 409, "INVALID_STATE_TRANSITION");
    assertThat(field(trip, "version")).isEqualTo(1L);
    assertThat(field(trip, "status")).isEqualTo(state);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASSIGNED", "IN_TRANSIT", "AT_STOP"})
  void authorizeChangesExpectedOnlyAndRejectPreservesItWithDurableEvents(String state)
      throws Exception {
    var trip = create(true, true);
    f.db.execute("UPDATE trips SET status=? WHERE id=?", state, trip);
    var requested = target("trailer");
    var original = f.db.value("SELECT row_to_json(t)::text FROM trailers t WHERE id=?", requested);
    var mismatch =
        transactions.execute(
            tenant, () -> expectation.inspect(tenant, trip, requested).orElseThrow());
    assertThat(mismatch.matches()).isFalse();
    assertThat(mismatch.expectedTrailerId()).isEqualTo(trailer);
    var rejected = decide(trip, trailer, requested, "REJECT", "ops");
    var e = history(rejected).getLast();
    assertThat(e.get("eventType")).isEqualTo("TRAILER_SUBSTITUTION_REJECTED");
    assertThat(field(trip, "planned_initial_trailer_id")).isEqualTo(trailer);
    assertThat(field(trip, "version")).isEqualTo(1L);
    assertThat(
            transactions.execute(
                tenant, () -> expectation.inspect(tenant, trip, requested).orElseThrow().matches()))
        .isFalse();
    var authorized = decide(trip, trailer, requested, "AUTHORIZE", "admin");
    e = history(authorized).getLast();
    assertThat(e.get("eventType")).isEqualTo("TRAILER_SUBSTITUTION_AUTHORIZED");
    assertThat(e.get("trailerId")).isEqualTo(requested.toString());
    assertThat(e.get("metadata")).isEqualTo(Map.of("expectedTrailerId", trailer.toString()));
    assertThat(field(trip, "planned_initial_trailer_id")).isEqualTo(requested);
    assertThat(field(trip, "status")).isEqualTo(state);
    assertThat(field(trip, "version")).isEqualTo(2L);
    assertThat(
            transactions.execute(
                tenant, () -> expectation.inspect(tenant, trip, requested).orElseThrow().matches()))
        .isTrue();
    assertThat(f.db.value("SELECT row_to_json(t)::text FROM trailers t WHERE id=?", requested))
        .isEqualTo(original);
    assertThat(f.db.value("SELECT to_regclass('trailer_couplings')")).isNull();
    problem(
        decide(trip, trailer, target("trailer"), "AUTHORIZE", "admin"), 409, "RESOURCE_CONFLICT");
    assertThat(historyCount(trip)).isEqualTo(5);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "CANCELLING", "COMPLETED", "CANCELLED"})
  void substitutionStateGuardPreservesProjectionAndHistory(String state) throws Exception {
    var trip = create(true, true);
    f.db.execute("UPDATE trips SET status=? WHERE id=?", state, trip);
    problem(
        decide(trip, trailer, target("trailer"), "AUTHORIZE", "admin"),
        409,
        "INVALID_STATE_TRANSITION");
    assertThat(historyCount(trip)).isEqualTo(3);
    assertThat(field(trip, "version")).isEqualTo(0L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"inactive", "oos"})
  void rejectingVisibleUnusableTrailerIsPermittedButAuthorizingItIsNot(String mode)
      throws Exception {
    var trip = create(true, true);
    var requested = target("trailer");
    f.db.execute(
        "UPDATE trailers SET "
            + (mode.equals("inactive") ? "active=false" : "serviceability_state='OUT_OF_SERVICE'")
            + " WHERE id=?",
        requested);
    problem(decide(trip, trailer, requested, "AUTHORIZE", "admin"), 409, "RESOURCE_CONFLICT");
    assertThat(decide(trip, trailer, requested, "REJECT", "admin").statusCode()).isEqualTo(200);
    assertThat(field(trip, "planned_initial_trailer_id")).isEqualTo(trailer);
  }

  @Test
  void missingExpectedAndEqualRequestedHaveDistinctStableGuardErrors() throws Exception {
    var trip = create(true, false);
    problem(
        decide(trip, trailer, target("trailer"), "AUTHORIZE", "admin"), 409, "RESOURCE_CONFLICT");
    problem(decide(trip, trailer, trailer, "AUTHORIZE", "admin"), 400, "VALIDATION_FAILED");
    assertThat(historyCount(trip)).isEqualTo(2);
  }

  void dispatcherScope(UUID allowed) throws Exception {
    var uid = verifier.verify("dispatcher").uid();
    f.db.execute(
        "UPDATE role_assignments SET customer_id=? WHERE tenant_id=? AND membership_id IN (SELECT"
            + " id FROM tenant_memberships WHERE user_id=(SELECT id FROM users WHERE"
            + " firebase_uid=?))",
        allowed,
        tenant,
        uid);
  }

  @Test
  void scopedDispatcherCommandsRequireAllTripCustomersAndNeverGrantGeneralTripReads()
      throws Exception {
    dispatcherScope(customer);
    var trip = create(true, true);
    assertThat(assign(trip, "driver", target("driver"), 0, "dispatcher").statusCode())
        .isEqualTo(200);
    var replacement = target("trailer");
    assertThat(assign(trip, "trailer", replacement, 1, "dispatcher").statusCode()).isEqualTo(200);
    assertThat(decide(trip, replacement, trailer, "REJECT", "dispatcher").statusCode())
        .isEqualTo(200);
    problem(assign(trip, "tractor", target("tractor"), 3, "dispatcher"), 403, "CAPABILITY_DENIED");
    problem(http("GET", base() + "/trips/" + trip, "dispatcher", ""), 403, "CAPABILITY_DENIED");
    problem(
        http("GET", base() + "/trips/" + trip + "/timeline", "dispatcher", ""),
        403,
        "CAPABILITY_DENIED");
    var b = plan();
    b.put("orderIds", List.of(order.toString(), orderB.toString()));
    b.put("stops", List.of(stop(1, List.of(order, orderB))));
    b.put("driverId", driver.toString());
    b.put("tractorId", tractor.toString());
    b.put("plannedInitialTrailerId", trailer.toString());
    var mixed = id(post(b));
    problem(assign(mixed, "driver", target("driver"), 0, "dispatcher"), 404, "RESOURCE_NOT_FOUND");
    problem(assign(mixed, "trailer", trailer, 0, "dispatcher"), 404, "RESOURCE_NOT_FOUND");
    problem(
        decide(mixed, trailer, target("trailer"), "AUTHORIZE", "dispatcher"),
        404,
        "RESOURCE_NOT_FOUND");
    assertThat(historyCount(mixed)).isEqualTo(3);
    assertThat(assign(mixed, "driver", target("driver"), 0, "ops").statusCode()).isEqualTo(200);
  }

  @Test
  void emptyTripCustomerSetCannotSatisfyScopedAuthority() throws Exception {
    dispatcherScope(customer);
    var trip = create(false, false);
    f.db.execute("DELETE FROM trip_stop_orders WHERE tenant_id=?", tenant);
    problem(assign(trip, "driver", driver, 0, "dispatcher"), 404, "RESOURCE_NOT_FOUND");
    assertThat(assign(trip, "driver", driver, 0, "admin").statusCode()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AUTHORIZE", "REJECT"})
  void hiddenAndForeignRequestedTrailersAreSafeMissingEvenWhenDecisionIsReject(String decision)
      throws Exception {
    dispatcherScope(customer);
    var trip = create(true, true);
    var hidden =
        new TrailerFixtures(flyway).trailer(tenant, "Hidden-" + UUID.randomUUID(), customerB);
    problem(decide(trip, trailer, hidden, decision, "dispatcher"), 404, "RESOURCE_NOT_FOUND");
    var foreign = new TrailerFixtures(flyway).trailer(other, "Foreign", null);
    problem(decide(trip, trailer, foreign, decision, "admin"), 404, "RESOURCE_NOT_FOUND");
    problem(assign(trip, "trailer", hidden, 0, "dispatcher"), 404, "RESOURCE_NOT_FOUND");
    assertThat(field(trip, "version")).isEqualTo(0L);
    assertThat(historyCount(trip)).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspend", "revoke", "customerGrant"})
  void sameTokenLosesFreshCommandScopeImmediately(String change) throws Exception {
    dispatcherScope(customer);
    var trip = create(true, true);
    var uid = verifier.verify("dispatcher").uid();
    if (change.equals("suspend")) {
      f.db.execute(
          "UPDATE tenant_memberships SET status='SUSPENDED' WHERE user_id=(SELECT id FROM users"
              + " WHERE firebase_uid=?)",
          uid);
    } else if (change.equals("revoke")) {
      f.db.execute(
          "DELETE FROM role_assignments WHERE membership_id IN (SELECT id FROM tenant_memberships"
              + " WHERE user_id=(SELECT id FROM users WHERE firebase_uid=?))",
          uid);
    } else {
      dispatcherScope(customerB);
    }
    var r = assign(trip, "driver", target("driver"), 0, "dispatcher");
    problem(
        r,
        change.equals("customerGrant") ? 404 : 403,
        change.equals("customerGrant")
            ? "RESOURCE_NOT_FOUND"
            : change.equals("suspend") ? "TENANT_ACCESS_DENIED" : "CAPABILITY_DENIED");
    var d = decide(trip, trailer, target("trailer"), "AUTHORIZE", "dispatcher");
    assertThat(d.statusCode()).isEqualTo(r.statusCode());
    assertThat(historyCount(trip)).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"yard", "driver"})
  void executionRolesAndFirebaseClaimsCannotBecomeDispatchAuthority(String token) throws Exception {
    var trip = create(true, true);
    for (var kind : List.of("driver", "tractor", "trailer")) {
      problem(assign(trip, kind, target(kind), 0, token), 403, "CAPABILITY_DENIED");
    }
    problem(decide(trip, trailer, target("trailer"), "AUTHORIZE", token), 403, "CAPABILITY_DENIED");
    problem(
        http("POST", path(trip, "driver"), null, json(assignment("driver", driver, 0))),
        401,
        "AUTHENTICATION_REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "trailer"})
  void strictAssignmentBodyAndQueryRejectUnknownNullBlankAndFractionalVersion(String kind)
      throws Exception {
    var trip = create(true, true);
    for (var bad :
        List.of(
            Map.of("status", "ASSIGNED"),
            Map.of("expectedVersion", -1),
            Map.of("expectedVersion", 0.5),
            Map.of("reason", " "),
            Map.of(kind + "Id", "bad"))) {
      var b = new HashMap<String, Object>(assignment(kind, target(kind), 0));
      b.putAll(bad);
      problem(http("POST", path(trip, kind), "admin", json(b)), 400, "VALIDATION_FAILED");
    }
    var b = new HashMap<String, Object>(assignment(kind, target(kind), 0));
    b.remove("expectedVersion");
    problem(http("POST", path(trip, kind), "admin", json(b)), 400, "VALIDATION_FAILED");
    b.put("expectedVersion", 0);
    b.put(kind + "Id", null);
    problem(http("POST", path(trip, kind), "admin", json(b)), 400, "VALIDATION_FAILED");
    problem(
        http(
            "POST",
            path(trip, kind) + "?customerId=" + customer,
            "admin",
            json(assignment(kind, target(kind), 0))),
        400,
        "VALIDATION_FAILED");
    assertThat(historyCount(trip)).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "trailer"})
  void inactiveAndForeignAssignmentTargetsCannotChangeProjection(String kind) throws Exception {
    var trip = create(true, true);
    var next = target(kind);
    var table = kind + "s";
    f.db.execute("UPDATE " + table + " SET active=false WHERE id=?", next);
    problem(assign(trip, kind, next, 0, "admin"), 409, "RESOURCE_CONFLICT");
    var foreign =
        switch (kind) {
          case "driver" -> f.db.driver(other, null);
          case "tractor" -> f.db.tractor(other, "Foreign");
          default -> new TrailerFixtures(flyway).trailer(other, "Foreign", null);
        };
    problem(assign(trip, kind, foreign, 0, "admin"), 404, "RESOURCE_NOT_FOUND");
    assertThat(historyCount(trip)).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "trailer"})
  void simultaneousSameVersionCommandsHaveOneWinnerAndOneEvent(String kind) throws Exception {
    var trip = create(true, true);
    var a = target(kind);
    var b = target(kind);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                waitFor(start);
                return assign(trip, kind, a, 0, "admin");
              });
      var two =
          pool.submit(
              () -> {
                waitFor(start);
                return assign(trip, kind, b, 0, "admin");
              });
      start.countDown();
      var r1 = one.get(20, TimeUnit.SECONDS);
      var r2 = two.get(20, TimeUnit.SECONDS);
      assertThat(List.of(r1.statusCode(), r2.statusCode())).containsExactlyInAnyOrder(200, 409);
      problem(r1.statusCode() == 409 ? r1 : r2, 409, "STALE_VERSION");
      assertThat(field(trip, projection(kind))).isEqualTo(r1.statusCode() == 200 ? a : b);
      assertThat(field(trip, "version")).isEqualTo(1L);
      assertThat(historyCount(trip)).isEqualTo(4);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "driver,true",
    "driver,false",
    "tractor,true",
    "tractor,false",
    "trailer,true",
    "trailer,false"
  })
  void sharedTargetEligibilityLockSerializesDeactivationBothDirections(
      String kind, boolean assignmentFirst) throws Exception {
    var trip = create(true, true);
    var next = target(kind);
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      if (assignmentFirst) {
        var first =
            pool.submit(
                () ->
                    transactions.execute(
                        tenant,
                        () -> {
                          var d =
                              assignments.assign(
                                  admin,
                                  tenant,
                                  trip,
                                  TripAssignmentService.Kind.valueOf(kind.toUpperCase(Locale.ROOT)),
                                  assignment(kind, next, 0));
                          held.countDown();
                          waitFor(release);
                          return d;
                        }));
        waitFor(held);
        var second =
            pool.submit(
                () -> {
                  try (var c = runtime.getConnection()) {
                    c.setAutoCommit(false);
                    context(c, tenant, null);
                    f.db.execute(c, "UPDATE " + kind + "s SET active=false WHERE id=?", next);
                    c.commit();
                    return true;
                  }
                });
        try {
          lockWait(kind + "s");
        } finally {
          release.countDown();
        }
        assertThat(first.get(20, TimeUnit.SECONDS).version()).isEqualTo(1);
        assertThat(second.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(field(trip, projection(kind))).isEqualTo(next);
      } else {
        var first =
            pool.submit(
                () -> {
                  try (var c = runtime.getConnection()) {
                    c.setAutoCommit(false);
                    context(c, tenant, null);
                    f.db.execute(c, "UPDATE " + kind + "s SET active=false WHERE id=?", next);
                    held.countDown();
                    waitFor(release);
                    c.commit();
                    return true;
                  }
                });
        waitFor(held);
        var second = pool.submit(() -> assign(trip, kind, next, 0, "admin"));
        try {
          lockWait(kind + "s");
        } finally {
          release.countDown();
        }
        assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
        problem(second.get(20, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
        assertThat(field(trip, "version")).isEqualTo(0L);
        assertThat(historyCount(trip)).isEqualTo(3);
      }
    } finally {
      release.countDown();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"assignmentEvent", "tripUpdate", "authorize", "reject", "initial"})
  void databaseFailuresRollbackProjectionHistoryAndInitialOrderPlanning(String mode)
      throws Exception {
    UUID trip = mode.equals("initial") ? null : create(true, true);
    var next = target("trailer");
    var before =
        trip == null
            ? null
            : f.db.value("SELECT row_to_json(t)::text FROM trips t WHERE id=?", trip);
    String name = "fail_assignment_" + UUID.randomUUID().toString().replace("-", "");
    var table = mode.equals("tripUpdate") ? "trips" : "trip_assignment_events";
    var operation = mode.equals("tripUpdate") ? "UPDATE" : "INSERT";
    f.db.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id='"
            + tenant
            + "' THEN RAISE EXCEPTION 'controlled infrastructure failure' USING ERRCODE='08006';"
            + " END IF; RETURN NEW; END $$");
    f.db.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE "
            + operation
            + " ON "
            + table
            + " FOR EACH ROW EXECUTE FUNCTION "
            + name
            + "()");
    try {
      HttpResponse<String> r;
      if (mode.equals("initial")) {
        var b = plan();
        b.put("driverId", driver.toString());
        r = post(b);
      } else if (mode.equals("authorize") || mode.equals("reject")) {
        r = decide(trip, trailer, next, mode.toUpperCase(Locale.ROOT), "admin");
      } else {
        r = assign(trip, "driver", target("driver"), 0, "admin");
      }
      assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(500);
      assertThat(r.body())
          .contains("traceId")
          .doesNotContain("controlled infrastructure", "08006", name, "SQL");
      if (trip == null) {
        for (var t : List.of("trips", "trip_stops", "trip_stop_orders", "trip_assignment_events")) {
          assertThat(count(t)).isZero();
        }
        assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("OPEN");
        assertThat(f.db.value("SELECT version FROM orders WHERE id=?", order)).isEqualTo(0L);
      } else {
        assertThat(f.db.value("SELECT row_to_json(t)::text FROM trips t WHERE id=?", trip))
            .isEqualTo(before);
        assertThat(historyCount(trip)).isEqualTo(3);
      }
    } finally {
      f.db.execute("DROP TRIGGER " + name + " ON " + table);
      f.db.execute("DROP FUNCTION " + name + "()");
    }
  }

  void denied(String state, String sql, Object... values) {
    assertThatThrownBy(() -> f.db.execute(sql, values))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(state));
  }

  @Test
  void migrationExactShapeChronologicalIndexNoBackfillAndNoCascade() throws Exception {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
    flyway.validate();
    try (var c = flyway.getConfiguration().getDataSource().getConnection();
        var q = c.createStatement()) {
      var columns = new HashSet<String>();
      try (var r =
          q.executeQuery(
              "SELECT column_name FROM information_schema.columns WHERE"
                  + " table_name='trip_assignment_events'")) {
        while (r.next()) {
          columns.add(r.getString(1));
        }
      }
      assertThat(columns)
          .containsExactlyInAnyOrder(
              "id",
              "tenant_id",
              "trip_id",
              "event_type",
              "driver_id",
              "tractor_id",
              "trailer_id",
              "actor_user_id",
              "occurred_at",
              "reason",
              "metadata");
      try (var r =
          q.executeQuery(
              "SELECT pg_get_indexdef(indexrelid) FROM pg_index WHERE"
                  + " indexrelid='trip_assignments_chronological'::regclass")) {
        r.next();
        assertThat(r.getString(1)).contains("(tenant_id, trip_id, occurred_at, id)");
      }
      try (var r =
          q.executeQuery(
              "SELECT count(*) FROM pg_constraint WHERE conrelid='trip_assignment_events'::regclass"
                  + " AND contype='f' AND confdeltype='c'")) {
        r.next();
        assertThat(r.getInt(1)).isZero();
      }
    }
    var old = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO trips(id,tenant_id,status,current_driver_id,created_at,updated_at)"
            + " VALUES(?,?,'PLANNED',?,now(),now())",
        old,
        tenant,
        driver);
    assertThat(historyCount(old))
        .isZero(); // Pre-existing projections never synthesize attribution.
    assertThat(
            JsonPath.<List<?>>read(
                http("GET", base() + "/trips/" + old, "admin", "").body(), "$.assignmentHistory"))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"trip", "driver", "tractor", "trailer", "actor"})
  void eventForeignKeysAreTenantSafeAndActorMustExist(String kind) throws Exception {
    var trip = create(false, false);
    var resource = driver;
    String type = "DRIVER_ASSIGNED", column = "driver_id";
    if (kind.equals("trip")) {
      trip = UUID.randomUUID();
      f.db.execute(
          "INSERT INTO trips(id,tenant_id,status,created_at,updated_at)"
              + " VALUES(?,?,'PLANNED',now(),now())",
          trip,
          other);
    }
    if (kind.equals("driver")) {
      resource = f.db.driver(other, null);
    }
    if (kind.equals("tractor")) {
      resource = f.db.tractor(other, "Foreign");
      type = "TRACTOR_ASSIGNED";
      column = "tractor_id";
    }
    if (kind.equals("trailer")) {
      resource = new TrailerFixtures(flyway).trailer(other, "Foreign", null);
      type = "TRAILER_PLANNED";
      column = "trailer_id";
    }
    denied(
        "23503",
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,"
            + column
            + ",actor_user_id,occurred_at) VALUES(?,?,?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        trip,
        type,
        resource,
        kind.equals("actor") ? UUID.randomUUID() : user);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DRIVER_ASSIGNED",
        "DRIVER_REASSIGNED",
        "TRACTOR_ASSIGNED",
        "TRACTOR_REASSIGNED",
        "TRAILER_PLANNED",
        "TRAILER_SUBSTITUTION_AUTHORIZED",
        "TRAILER_SUBSTITUTION_REJECTED"
      })
  void closedCatalogRequiresExactlyItsMatchingResource(String type) throws Exception {
    var trip = create(false, false);
    String column =
        type.startsWith("DRIVER_")
            ? "driver_id"
            : type.startsWith("TRACTOR_") ? "tractor_id" : "trailer_id";
    UUID resource =
        column.equals("driver_id") ? driver : column.equals("tractor_id") ? tractor : trailer;
    f.db.execute(
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,"
            + column
            + ",occurred_at) VALUES(?,?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        trip,
        type,
        resource);
    denied(
        "23514",
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,occurred_at)"
            + " VALUES(?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        trip,
        type);
    var wrong = column.equals("driver_id") ? "tractor_id" : "driver_id";
    var extra = wrong.equals("driver_id") ? driver : tractor;
    denied(
        "23514",
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,"
            + column
            + ","
            + wrong
            + ",occurred_at) VALUES(?,?,?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        trip,
        type,
        resource,
        extra);
    denied(
        "23514",
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,driver_id,occurred_at)"
            + " VALUES(?,?,?,'TRAILER_REASSIGNED',?,now())",
        UUID.randomUUID(),
        tenant,
        trip,
        driver);
  }

  @Test
  void runtimeIsAppendOnlyRestrictedNonOwnerAndRlsRequiresPureTenantContext() throws Exception {
    var trip = create(true, true);
    UUID foreign = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO trips(id,tenant_id,status,created_at,updated_at)"
            + " VALUES(?,?,'PLANNED',now(),now())",
        foreign,
        other);
    f.db.execute(
        "INSERT INTO trip_assignment_events(id,tenant_id,trip_id,event_type,driver_id,occurred_at)"
            + " VALUES(?,?,?,'DRIVER_ASSIGNED',?,now())",
        UUID.randomUUID(),
        other,
        foreign,
        f.db.driver(other, null));
    try (var c = runtime.getConnection();
        var q = c.createStatement()) {
      assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
      try (var r =
          q.executeQuery(
              "SELECT"
                  + " has_table_privilege(current_user,'trip_assignment_events','SELECT'),has_table_privilege(current_user,'trip_assignment_events','INSERT'),has_table_privilege(current_user,'trip_assignment_events','UPDATE'),has_table_privilege(current_user,'trip_assignment_events','DELETE'),rolsuper,rolbypassrls,has_schema_privilege(current_user,'public','CREATE')"
                  + " FROM pg_roles WHERE rolname=current_user")) {
        r.next();
        assertThat(r.getBoolean(1)).isTrue();
        assertThat(r.getBoolean(2)).isTrue();
        for (int i = 3; i <= 7; i++) {
          assertThat(r.getBoolean(i)).isFalse();
        }
      }
      try (var r =
          q.executeQuery(
              "SELECT relowner=(SELECT oid FROM pg_roles WHERE rolname=current_user),relrowsecurity"
                  + " FROM pg_class WHERE oid='trip_assignment_events'::regclass")) {
        r.next();
        assertThat(r.getBoolean(1)).isFalse();
        assertThat(r.getBoolean(2)).isTrue();
      }
      try (var r =
          q.executeQuery(
              "SELECT count(*) FROM information_schema.table_privileges WHERE"
                  + " table_name='trip_assignment_events' AND grantee='PUBLIC'")) {
        r.next();
        assertThat(r.getLong(1)).isZero();
      }
      c.setAutoCommit(false);
      assertThat(runtimeCount(c, "trip_assignment_events")).isZero();
      context(c, null, user);
      assertThat(runtimeCount(c, "trip_assignment_events")).isZero();
      c.rollback();
      context(c, tenant, null);
      assertThat(runtimeCount(c, "trip_assignment_events")).isEqualTo(3);
      for (var operation :
          List.of(
              "UPDATE trip_assignment_events SET reason='changed'",
              "DELETE FROM trip_assignment_events")) {
        assertThatThrownBy(() -> f.db.execute(c, operation))
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
                          + " trip_assignment_events(id,tenant_id,trip_id,event_type,driver_id,occurred_at)"
                          + " VALUES(?,?,?,'DRIVER_ASSIGNED',?,now())",
                      UUID.randomUUID(),
                      other,
                      foreign,
                      f.db.driver(other, null)))
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
      c.rollback();
      context(c, tenant, user);
      assertThat(runtimeCount(c, "trip_assignment_events")).isZero();
      c.rollback();
    }
    assertThat(transactions.execute(tenant, () -> expectation.inspect(tenant, foreign, trailer)))
        .isEmpty();
  }

  @Test
  void identicalTimestampsUseUuidTieBreakerInDetailAndTimeline() throws Exception {
    var trip = create(false, false);
    var at = java.time.Instant.parse("2026-10-09T10:00:00Z");
    var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    for (var id : List.of(high, low)) {
      f.db.execute(
          "INSERT INTO"
              + " trip_assignment_events(id,tenant_id,trip_id,event_type,driver_id,occurred_at)"
              + " VALUES(?,?,?,'DRIVER_ASSIGNED',?,?)",
          id,
          tenant,
          trip,
          driver,
          java.sql.Timestamp.from(at));
    }
    var r = http("GET", base() + "/trips/" + trip, "admin", "");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.assignmentHistory[*].id"))
        .containsExactly(low.toString(), high.toString());
    r = http("GET", base() + "/trips/" + trip + "/timeline", "admin", "");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.items[*].id"))
        .containsExactly(low.toString(), high.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "expectedVersion",
        "tenantId",
        "actorUserId",
        "customerId",
        "approvedAlternateTrailerId"
      })
  void substitutionRejectsInventedRequestFields(String unknown) throws Exception {
    var trip = create(true, true);
    var input =
        new HashMap<String, Object>(
            Map.of(
                "expectedTrailerId",
                trailer.toString(),
                "requestedTrailerId",
                target("trailer").toString(),
                "decision",
                "AUTHORIZE",
                "reason",
                "Dispatch"));
    input.put(unknown, 0);
    problem(
        http(
            "POST",
            base() + "/trips/" + trip + "/trailer-substitution-decisions",
            "admin",
            json(input)),
        400,
        "VALIDATION_FAILED");
    assertThat(historyCount(trip)).isEqualTo(3);
  }

  @Test
  void ordinaryTrailerPlanningRejectsOutOfServiceWithoutHistory() throws Exception {
    var trip = create(true, true);
    var next = target("trailer");
    f.db.execute("UPDATE trailers SET serviceability_state='OUT_OF_SERVICE' WHERE id=?", next);
    problem(assign(trip, "trailer", next, 0, "admin"), 409, "RESOURCE_CONFLICT");
    assertThat(field(trip, "planned_initial_trailer_id")).isEqualTo(trailer);
    assertThat(historyCount(trip)).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void substitutionSharedTargetLockCoordinatesOutOfServiceMutation(boolean authorizationFirst)
      throws Exception {
    var trip = create(true, true);
    var next = target("trailer");
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var request =
        Map.<String, Object>of(
            "expectedTrailerId",
            trailer.toString(),
            "requestedTrailerId",
            next.toString(),
            "decision",
            "AUTHORIZE",
            "reason",
            "Dispatch");
    try (var pool = Executors.newFixedThreadPool(2)) {
      if (authorizationFirst) {
        var one =
            pool.submit(
                () ->
                    transactions.execute(
                        tenant,
                        () -> {
                          var result = assignments.decide(admin, tenant, trip, request);
                          held.countDown();
                          waitFor(release);
                          return result;
                        }));
        waitFor(held);
        var two =
            pool.submit(
                () -> {
                  try (var c = runtime.getConnection()) {
                    c.setAutoCommit(false);
                    context(c, tenant, null);
                    f.db.execute(
                        c,
                        "UPDATE trailers SET serviceability_state='OUT_OF_SERVICE' WHERE id=?",
                        next);
                    c.commit();
                    return true;
                  }
                });
        try {
          lockWait("trailers");
        } finally {
          release.countDown();
        }
        assertThat(one.get(20, TimeUnit.SECONDS).plannedInitialTrailerId()).isEqualTo(next);
        assertThat(two.get(20, TimeUnit.SECONDS)).isTrue();
      } else {
        var one =
            pool.submit(
                () -> {
                  try (var c = runtime.getConnection()) {
                    c.setAutoCommit(false);
                    context(c, tenant, null);
                    f.db.execute(
                        c,
                        "UPDATE trailers SET serviceability_state='OUT_OF_SERVICE' WHERE id=?",
                        next);
                    held.countDown();
                    waitFor(release);
                    c.commit();
                    return true;
                  }
                });
        waitFor(held);
        var two = pool.submit(() -> decide(trip, trailer, next, "AUTHORIZE", "admin"));
        try {
          lockWait("trailers");
        } finally {
          release.countDown();
        }
        assertThat(one.get(20, TimeUnit.SECONDS)).isTrue();
        problem(two.get(20, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
        assertThat(field(trip, "version")).isEqualTo(0L);
      }
    } finally {
      release.countDown();
    }
  }
}
