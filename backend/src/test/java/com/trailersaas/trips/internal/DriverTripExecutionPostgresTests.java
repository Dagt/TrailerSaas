package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.trips.TripDeparturePrerequisites;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriverTripExecutionPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired DriverTripExecutionService execution;
  @MockitoBean FirebaseTokenVerifier verifier;
  // Domain integration seam only. Production denies departure until #43 supplies actual evidence.
  @MockitoBean TripDeparturePrerequisites prerequisites;

  @Value("${local.server.port}")
  int port;

  OrderFixtures f;
  UUID tenant,
      other,
      user,
      driver,
      tractor,
      trailer,
      trip,
      stop,
      site,
      location,
      customer,
      membership;
  final ObjectMapper json = new ObjectMapper();
  Instant at;

  @BeforeEach
  void seed() throws Exception {
    f = new OrderFixtures(flyway);
    tenant = f.db.tenant();
    other = f.db.tenant();
    user = f.db.user();
    membership = f.db.membership(tenant, user, "ACTIVE", "DRIVER");
    driver = f.db.driver(tenant, user);
    tractor = f.db.tractor(tenant, "Execution-" + UUID.randomUUID());
    customer = f.customer(tenant);
    site = f.site(tenant, customer);
    location = f.location(tenant, site, null);
    trailer =
        new TrailerFixtures(flyway).trailer(tenant, "Execution-" + UUID.randomUUID(), customer);
    f.db.execute(
        "update trailers set"
            + " location_state='AT_LOCATION',current_location_id=?,last_known_location_id=? where"
            + " id=?",
        location,
        location,
        trailer);
    trip = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trips(id,tenant_id,status,current_driver_id,current_tractor_id,planned_initial_trailer_id,created_at,updated_at)"
            + " values(?,?,'ASSIGNED',?,?,?,now(),now())",
        trip,
        tenant,
        driver,
        tractor,
        trailer);
    stop = stop(1, "OTHER");
    var order = f.order(tenant, customer, user);
    f.db.execute(
        "insert into trip_stop_orders values(?,?,?,?,null)",
        UUID.randomUUID(),
        tenant,
        stop,
        order);
    at = Instant.now().minusSeconds(3600);
    when(verifier.verify("driver"))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
    when(prerequisites.satisfied(any(), any(), any(), any())).thenReturn(true);
  }

  UUID stop(int sequence, String type) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,site_id,location_id,status,created_at,updated_at)"
            + " values(?,?,?,?,?,?,?,'PENDING',now(),now())",
        id,
        tenant,
        trip,
        sequence,
        type,
        site,
        location);
    return id;
  }

  String base() {
    return "/api/v1/tenants/" + tenant;
  }

  HttpResponse<String> post(String path, String key, Map<String, Object> body) throws Exception {
    return post(path, key, body, "driver");
  }

  HttpResponse<String> post(String path, String key, Map<String, Object> body, String token)
      throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + base() + path))
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> action(String path, Map<String, Object> fields) throws Exception {
    var body = new LinkedHashMap<>(fields);
    body.put("occurredAt", at.toString());
    at = at.plusSeconds(1);
    return post(path, UUID.randomUUID().toString(), body);
  }

  void ok(HttpResponse<String> r) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);
    assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
    assertThat(r.body()).doesNotContain("constraint", "SQL", "firebase-", "tenantId");
  }

  void hook() throws Exception {
    ok(
        action(
            "/trips/" + trip + "/hooks",
            Map.of(
                "trailerId",
                trailer.toString(),
                "tractorId",
                tractor.toString(),
                "driverId",
                driver.toString(),
                "expectedTrailerVersion",
                0)));
  }

  void depart() throws Exception {
    hook();
    ok(action("/trips/" + trip + "/departures", Map.of("nextStopId", stop.toString())));
  }

  void service(UUID id) throws Exception {
    ok(action("/stops/" + id + "/arrivals", Map.of("source", "MANUAL")));
    ok(action("/stops/" + id + "/service-starts", Map.of()));
  }

  Object value(String table, String column, UUID id) throws Exception {
    return f.db.value("select " + column + " from " + table + " where id=?", id);
  }

  @Test
  void firstDepartureArrivalServiceAndFinalCompletionAreAtomicPhysicalFacts() throws Exception {
    depart();
    assertThat(value("trips", "status", trip)).isEqualTo("IN_TRANSIT");
    assertThat(value("trip_stops", "status", stop)).isEqualTo("EN_ROUTE");
    assertThat(value("trailers", "location_state", trailer)).isEqualTo("IN_TRANSIT");
    service(stop);
    assertThat(value("trailers", "current_location_id", trailer)).isEqualTo(location);
    var completed = action("/stops/" + stop + "/completions", Map.of("notes", "Service confirmed"));
    ok(completed);
    assertThat(JsonPath.<String>read(completed.body(), "$.trip.status")).isEqualTo("COMPLETED");
    assertThat(value("trips", "completed_at", trip)).isNotNull();
    assertThat(value("trip_stops", "status", stop)).isEqualTo("COMPLETED");
    assertThat(f.db.value("select count(*) from trip_stop_events where trip_id=?", trip))
        .isEqualTo(4L);
    assertThat(
            f.db.value(
                "select count(*) from trip_stop_events where trip_id=? and actor_type='HUMAN_USER'"
                    + " and source_type='USER_ACTION' and actor_user_id=?",
                trip,
                user))
        .isEqualTo(4L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trip_id=?", trip))
        .isEqualTo(3L);
    assertThat(completed.body())
        .doesNotContain(
            "assignmentHistory", "actorUserId", "tenantId", "clientOperationId", "shift");
  }

  @Test
  void multipleStopsAndExplicitBaseReturnNeverSkipOrEndShift() throws Exception {
    var next = stop(7, "OTHER");
    var last = stop(11, "BASE_RETURN");
    depart();
    service(stop);
    ok(action("/stops/" + stop + "/completions", Map.of()));
    problem(
        action("/stops/" + stop + "/departures", Map.of("nextStopId", last.toString())),
        409,
        "INVALID_STATE_TRANSITION");
    ok(action("/stops/" + stop + "/departures", Map.of("nextStopId", next.toString())));
    service(next);
    ok(action("/stops/" + next + "/completions", Map.of()));
    assertThat(value("trips", "status", trip)).isEqualTo("AT_STOP");
    ok(action("/stops/" + next + "/departures", Map.of("nextStopId", last.toString())));
    service(last);
    ok(action("/stops/" + last + "/completions", Map.of()));
    assertThat(value("trips", "status", trip)).isEqualTo("COMPLETED");
    assertThat(f.db.value("select count(*) from trip_stops where trip_id=?", trip)).isEqualTo(3L);
  }

  @Test
  void missingDutyOrPreTripEvidenceBlocksFirstDepartureWithoutWrites() throws Exception {
    hook();
    when(prerequisites.satisfied(any(), any(), any(), any())).thenReturn(false);
    problem(
        action("/trips/" + trip + "/departures", Map.of("nextStopId", stop.toString())),
        409,
        "RESOURCE_CONFLICT");
    assertThat(value("trips", "status", trip)).isEqualTo("ASSIGNED");
    assertThat(value("trip_stops", "version", stop)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trip_stop_events where trip_id=?", trip))
        .isEqualTo(0L);
  }

  @Test
  void deliveryRequiresEvidenceAndPreservesAllRows() throws Exception {
    f.db.execute("update trip_stops set stop_type='DELIVERY' where id=?", stop);
    depart();
    service(stop);
    problem(action("/stops/" + stop + "/completions", Map.of()), 409, "EVIDENCE_REQUIRED");
    assertThat(value("trip_stops", "status", stop)).isEqualTo("IN_SERVICE");
    assertThat(value("trips", "status", trip)).isEqualTo("AT_STOP");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"PLANNED", "IN_TRANSIT", "AT_STOP", "CANCELLING", "COMPLETED", "CANCELLED"})
  void firstDepartureRejectsEveryOtherTripState(String state) throws Exception {
    f.db.execute("update trips set status=? where id=?", state, trip);
    problem(
        action("/trips/" + trip + "/departures", Map.of("nextStopId", stop.toString())),
        409,
        "INVALID_STATE_TRANSITION");
  }

  @ParameterizedTest
  @ValueSource(strings = {"arrivals", "service-starts", "completions", "departures"})
  void noExecutionCommandCanJumpFromPending(String command) throws Exception {
    problem(
        action(
            "/stops/" + stop + "/" + command,
            command.equals("arrivals")
                ? Map.of("source", "MANUAL")
                : command.equals("departures") ? Map.of("nextStopId", stop.toString()) : Map.of()),
        409,
        "INVALID_STATE_TRANSITION");
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspension", "role", "driver", "assignment"})
  void freshAuthorityLosesAccessWithSameToken(String mode) throws Exception {
    depart();
    switch (mode) {
      case "suspension" ->
          f.db.execute("update tenant_memberships set status='SUSPENDED' where id=?", membership);
      case "role" -> f.db.execute("delete from role_assignments where membership_id=?", membership);
      case "driver" -> f.db.execute("update drivers set active=false where id=?", driver);
      case "assignment" -> f.db.execute("update trips set current_driver_id=NULL where id=?", trip);
    }
    var r = action("/stops/" + stop + "/arrivals", Map.of("source", "MANUAL"));
    assertThat(r.statusCode()).isIn(403, 404);
    assertThat(value("trip_stops", "status", stop)).isEqualTo("EN_ROUTE");
  }

  @Test
  void replayIsSameLogicalResultAndChangedPayloadOrOperationConflicts() throws Exception {
    depart();
    var key = UUID.randomUUID().toString();
    var body = Map.<String, Object>of("occurredAt", at.toString(), "source", "MANUAL");
    var first = post("/stops/" + stop + "/arrivals", key, body);
    ok(first);
    var replay = post("/stops/" + stop + "/arrivals", key, body);
    ok(replay);
    assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(first.body()));
    problem(
        post(
            "/stops/" + stop + "/arrivals",
            key,
            Map.of("occurredAt", at.plusSeconds(1).toString(), "source", "MANUAL")),
        409,
        "IDEMPOTENCY_KEY_REUSED");
    problem(
        post("/stops/" + stop + "/service-starts", key, Map.of("occurredAt", at.toString())),
        409,
        "IDEMPOTENCY_KEY_REUSED");
    assertThat(
            f.db.value(
                "select count(*) from trip_stop_events where trip_id=? and"
                    + " event_type='ARRIVAL_CONFIRMED'",
                trip))
        .isEqualTo(1L);
  }

  @Test
  void concurrentDistinctArrivalKeysProduceOneAdvance() throws Exception {
    depart();
    try (var workers = Executors.newFixedThreadPool(2)) {
      var latch = new CountDownLatch(1);
      var a =
          workers.submit(
              () -> {
                latch.await();
                return post(
                    "/stops/" + stop + "/arrivals",
                    UUID.randomUUID().toString(),
                    Map.of("occurredAt", at.toString(), "source", "MANUAL"));
              });
      var b =
          workers.submit(
              () -> {
                latch.await();
                return post(
                    "/stops/" + stop + "/arrivals",
                    UUID.randomUUID().toString(),
                    Map.of("occurredAt", at.toString(), "source", "MANUAL"));
              });
      latch.countDown();
      assertThat(
              List.of(
                  a.get(30, TimeUnit.SECONDS).statusCode(),
                  b.get(30, TimeUnit.SECONDS).statusCode()))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(value("trip_stops", "version", stop)).isEqualTo(2L);
  }

  @Test
  void eventFailureRollsBackTripStopTrailerAndOperationClaim() throws Exception {
    depart();
    var oldVersion = value("trailers", "version", trailer);
    var key = UUID.randomUUID().toString();
    f.db.execute(
        "create function issue44_fail() returns trigger language plpgsql as $$ begin if"
            + " NEW.trip_id='"
            + trip
            + "' then raise exception using errcode='P0001',message='Controlled execution failure';"
            + " end if; return NEW; end; $$");
    f.db.execute(
        "create trigger issue44_fail before insert on trip_stop_events for each row execute"
            + " function issue44_fail()");
    try {
      var failed =
          post(
              "/stops/" + stop + "/arrivals",
              key,
              Map.of("occurredAt", at.toString(), "source", "MANUAL"));
      assertThat(failed.statusCode()).isEqualTo(500);
      assertThat(failed.body())
          .contains("The server could not complete the request.", "traceId")
          .doesNotContain("Controlled execution failure");
      assertThat(value("trips", "status", trip)).isEqualTo("IN_TRANSIT");
      assertThat(value("trip_stops", "status", stop)).isEqualTo("EN_ROUTE");
      assertThat(value("trailers", "version", trailer)).isEqualTo(oldVersion);
      assertThat(
              f.db.value(
                  "select count(*) from processed_client_operations where tenant_id=? and"
                      + " client_operation_id=?",
                  tenant,
                  UUID.fromString(key)))
          .isEqualTo(0L);
    } finally {
      f.db.execute("drop trigger issue44_fail on trip_stop_events");
      f.db.execute("drop function issue44_fail()");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"status", "actorUserId", "sourceType", "expectedVersion", "tripId"})
  void strictRequestsRejectClientAuthorityAndGenericStates(String field) throws Exception {
    problem(
        action("/stops/" + stop + "/arrivals", Map.of("source", "MANUAL", field, "spoof")),
        400,
        "VALIDATION_FAILED");
  }

  @Test
  void foreignOrDifferentTripStopIsInvisible() throws Exception {
    var id = UUID.randomUUID();
    problem(
        action("/stops/" + id + "/arrivals", Map.of("source", "MANUAL")),
        404,
        "RESOURCE_NOT_FOUND");
  }

  @Test
  void missingKeyMalformedSourceAndCoordinatesFailStrictly() throws Exception {
    problem(
        post(
            "/stops/" + stop + "/arrivals",
            "not-uuid",
            Map.of("occurredAt", at.toString(), "source", "MANUAL")),
        400,
        "VALIDATION_FAILED");
    problem(
        action("/stops/" + stop + "/arrivals", Map.of("source", "AUTO")), 400, "VALIDATION_FAILED");
    problem(
        action(
            "/stops/" + stop + "/arrivals",
            Map.of(
                "source",
                "MANUAL",
                "coordinates",
                Map.of("latitude", 100, "longitude", 0, "accuracyM", 1))),
        400,
        "VALIDATION_FAILED");
  }

  @Test
  void dropThenAuthorizedHookIsRequiredToCompleteDropHookService() throws Exception {
    f.db.execute("update trip_stops set stop_type='DROP_HOOK' where id=?", stop);
    depart();
    service(stop);
    problem(action("/stops/" + stop + "/completions", Map.of()), 409, "RESOURCE_CONFLICT");
    ok(
        action(
            "/trips/" + trip + "/drops",
            Map.of(
                "trailerId",
                trailer.toString(),
                "stopId",
                stop.toString(),
                "locationId",
                location.toString())));
    var replacement =
        new TrailerFixtures(flyway).trailer(tenant, "Replacement-" + UUID.randomUUID(), customer);
    var hook =
        Map.<String, Object>of(
            "occurredAt",
            at.toString(),
            "trailerId",
            replacement.toString(),
            "tractorId",
            tractor.toString(),
            "driverId",
            driver.toString(),
            "expectedTrailerVersion",
            0);
    problem(
        post("/trips/" + trip + "/hooks", UUID.randomUUID().toString(), hook),
        409,
        "TRAILER_SUBSTITUTION_NOT_AUTHORIZED");
    var ops = f.db.user();
    f.db.membership(tenant, ops, "ACTIVE", "TRANSPORT_OPERATIONS");
    when(verifier.verify("ops"))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + ops, null, null));
    ok(
        post(
            "/trips/" + trip + "/trailer-substitution-decisions",
            UUID.randomUUID().toString(),
            Map.of(
                "expectedTrailerId",
                trailer.toString(),
                "requestedTrailerId",
                replacement.toString(),
                "decision",
                "AUTHORIZE",
                "reason",
                "Operational replacement"),
            "ops"));
    ok(post("/trips/" + trip + "/hooks", UUID.randomUUID().toString(), hook));
    at = at.plusSeconds(1);
    ok(action("/stops/" + stop + "/completions", Map.of()));
    assertThat(f.db.value("select count(*) from trailer_couplings where trip_id=?", trip))
        .isEqualTo(2L);
    assertThat(
            f.db.value(
                "select trailer_id from trailer_couplings where trip_id=? and dropped_at is null",
                trip))
        .isEqualTo(replacement);
  }

  @Test
  void concurrentIdenticalKeyReturnsOneDurableResult() throws Exception {
    depart();
    var key = UUID.randomUUID().toString();
    var body = Map.<String, Object>of("occurredAt", at.toString(), "source", "MANUAL");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var latch = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                latch.await();
                return post("/stops/" + stop + "/arrivals", key, body);
              });
      var b =
          pool.submit(
              () -> {
                latch.await();
                return post("/stops/" + stop + "/arrivals", key, body);
              });
      latch.countDown();
      var first = a.get(30, TimeUnit.SECONDS);
      var second = b.get(30, TimeUnit.SECONDS);
      ok(first);
      ok(second);
      assertThat(json.readTree(first.body())).isEqualTo(json.readTree(second.body()));
    }
    assertThat(
            f.db.value(
                "select count(*) from trip_stop_events where trip_id=? and"
                    + " event_type='ARRIVAL_CONFIRMED'",
                trip))
        .isEqualTo(1L);
  }

  @Test
  void assignmentRevocationWaitsForExecutionTripLockAndThenDeniesOldDriver() throws Exception {
    depart();
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var actor = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var result =
                            execution.command(
                                actor,
                                tenant,
                                stop,
                                TripExecutionRequests.Action.ARRIVAL,
                                UUID.randomUUID().toString(),
                                Map.of("occurredAt", at.toString(), "source", "MANUAL"));
                        held.countDown();
                        try {
                          if (!release.await(20, TimeUnit.SECONDS))
                            throw new IllegalStateException("Lock test timeout");
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(e);
                        }
                        return result;
                      }));
      assertThat(held.await(20, TimeUnit.SECONDS)).isTrue();
      var second =
          pool.submit(
              () -> {
                f.db.execute("update trips set current_driver_id=NULL where id=?", trip);
                return true;
              });
      try {
        await()
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(
                () ->
                    assertThat(
                            (Long)
                                f.db.value(
                                    "select count(*) from pg_stat_activity where"
                                        + " wait_event_type='Lock' and query like '%update trips"
                                        + " set current_driver_id%'"))
                        .isPositive());
      } finally {
        release.countDown();
      }
      assertThat(first.get(30, TimeUnit.SECONDS).trip().status()).isEqualTo("AT_STOP");
      assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
    }
    problem(action("/stops/" + stop + "/service-starts", Map.of()), 404, "RESOURCE_NOT_FOUND");
  }

  @Test
  void currentOwnDetailHidesAdministrativeHistoryAndForeignTenant() throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + base() + "/driver/me/trips/" + trip))
            .header("Authorization", "Bearer driver")
            .GET()
            .build();
    var own = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    ok(own);
    assertThat(own.body())
        .contains("tractorOperationalNumber", "departurePrerequisitesSatisfied")
        .doesNotContain("assignmentHistory", "actorUserId", "tenantId", "clientOperationId");
    var foreign =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/tenants/"
                        + other
                        + "/driver/me/trips/"
                        + trip))
            .header("Authorization", "Bearer driver")
            .GET()
            .build();
    assertThat(
            HttpClient.newHttpClient()
                .send(foreign, HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isIn(403, 404);
  }

  @Test
  void executionFactsAreAppendOnlyTenantOwnedAndHaveStrictProvenance() throws Exception {
    depart();
    assertThat(
            f.db.value(
                "select has_table_privilege(?, 'trip_stop_events','SELECT')", RUNTIME_USERNAME))
        .isEqualTo(true);
    assertThat(
            f.db.value(
                "select has_table_privilege(?, 'trip_stop_events','INSERT')", RUNTIME_USERNAME))
        .isEqualTo(true);
    for (var grant : List.of("UPDATE", "DELETE"))
      assertThat(
              f.db.value(
                  "select has_table_privilege(?, 'trip_stop_events',?)", RUNTIME_USERNAME, grant))
          .isEqualTo(false);
    assertThat(
            f.db.value(
                "select relrowsecurity from pg_class where oid='trip_stop_events'::regclass"))
        .isEqualTo(true);
    assertThat(transactions.execute(other, () -> executionCount())).isZero();
    assertThat(transactions.execute(tenant, () -> executionCount())).isEqualTo(1L);
    assertThatThrownBy(
            () ->
                f.db.execute(
                    "insert into"
                        + " trip_stop_events(id,tenant_id,trip_stop_id,trip_id,event_type,actor_type,source_type,occurred_at,recorded_at)"
                        + " values(?,?,?,?,'ACTIVATED','HUMAN_USER','USER_ACTION',now(),now())",
                    UUID.randomUUID(),
                    tenant,
                    stop,
                    trip))
        .hasMessageContaining("check constraint");
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  long executionCount() {
    return jdbc.queryForObject(
        "select count(*) from trip_stop_events where trip_id=?", Long.class, trip);
  }
}
