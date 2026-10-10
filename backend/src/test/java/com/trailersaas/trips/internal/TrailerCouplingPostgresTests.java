package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerCouplingTransactions;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrailerCouplingPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired TripCouplingService service;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired TrailerCouplingTransactions physical;
  @Autowired jakarta.persistence.EntityManager em;
  @MockitoBean FirebaseTokenVerifier verifier;

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
  AuthenticatedUserPrincipal actor;
  static final String HOOK_AT = "2026-10-09T20:00:00Z", DROP_AT = "2026-10-09T21:00:00Z";
  final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void seed() throws Exception {
    f = new OrderFixtures(flyway);
    tenant = f.db.tenant();
    other = f.db.tenant();
    user = identity("driver", "DRIVER");
    actor = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
    membership =
        (UUID)
            f.db.value(
                "select id from tenant_memberships where tenant_id=? and user_id=?", tenant, user);
    identity("admin", "TENANT_ADMIN");
    identity("ops", "TRANSPORT_OPERATIONS");
    identity("yard", "YARD_SPOTTER");
    identity("dispatcher", "CUSTOMER_DISPATCHER");
    driver = f.db.driver(tenant, user);
    tractor = f.db.tractor(tenant, "Tractor-" + UUID.randomUUID());
    customer = f.customer(tenant);
    site = f.site(tenant, customer);
    location = f.location(tenant, site, null);
    trailer = new TrailerFixtures(flyway).trailer(tenant, "Trailer-" + UUID.randomUUID(), customer);
    trip = trip(tenant, driver, tractor, trailer);
    stop = stop(trip, site);
    var order = f.order(tenant, customer, user);
    f.db.execute(
        "insert into trip_stop_orders values(?,?,?,?,null)",
        UUID.randomUUID(),
        tenant,
        stop,
        order);
  }

  UUID identity(String token, String role) throws Exception {
    var id = f.db.user();
    f.db.membership(tenant, id, "ACTIVE", role);
    when(verifier.verify(token))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null));
    return id;
  }

  UUID trip(UUID t, UUID d, UUID c, UUID r) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trips(id,tenant_id,status,current_driver_id,current_tractor_id,planned_initial_trailer_id,created_at,updated_at)"
            + " values(?,?,'ASSIGNED',?,?,?,now(),now())",
        id,
        t,
        d,
        c,
        r);
    return id;
  }

  UUID stop(UUID t, UUID s) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,site_id,status,site_snapshot,created_at,updated_at)"
            + " values(?,?,?,1,'DROP_HOOK',?,'PENDING','{\"name\":\"Frozen\"}',now(),now())",
        id,
        tenant,
        t,
        s);
    return id;
  }

  Map<String, Object> hookBody() {
    return new LinkedHashMap<>(
        Map.of(
            "trailerId",
            trailer.toString(),
            "tractorId",
            tractor.toString(),
            "driverId",
            driver.toString(),
            "occurredAt",
            HOOK_AT,
            "expectedTrailerVersion",
            0));
  }

  Map<String, Object> dropBody() {
    return new LinkedHashMap<>(
        Map.of(
            "trailerId",
            trailer.toString(),
            "stopId",
            stop.toString(),
            "locationId",
            location.toString(),
            "occurredAt",
            DROP_AT));
  }

  String path(UUID t, String command) {
    return "/api/v1/tenants/" + tenant + "/trips/" + t + "/" + command;
  }

  HttpResponse<String> post(
      UUID t, String command, String key, String token, Map<String, Object> body) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path(t, command)))
            .timeout(Duration.ofSeconds(40))
            .header("Content-Type", "application/json")
            .header("X-Request-Id", "physical-regression");
    if (key != null) b.header("Idempotency-Key", key);
    if (token != null) b.header("Authorization", "Bearer " + token);
    return HttpClient.newHttpClient()
        .send(
            b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> hook(UUID key) throws Exception {
    return post(trip, "hooks", key.toString(), "driver", hookBody());
  }

  HttpResponse<String> drop(UUID key) throws Exception {
    return post(trip, "drops", key.toString(), "driver", dropBody());
  }

  void ok(HttpResponse<String> r) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);
    assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
    assertThat(r.body())
        .contains("traceId")
        .doesNotContain("constraint", "SQL", "fixture-", other.toString(), customer.toString());
    assertThat(r.headers().firstValue("X-Request-Id")).contains("physical-regression");
  }

  long count(String table) throws Exception {
    return (Long) f.db.value("select count(*) from " + table + " where tenant_id=?", tenant);
  }

  Object trailerField(String column) throws Exception {
    return f.db.value("select " + column + " from trailers where id=?", trailer);
  }

  String retained() throws Exception {
    return (String)
        f.db.value(
            "select"
                + " (to_jsonb(t)-'version'-'updated_at'-'last_seen_at'-'last_driver_id'-'location_state'-'current_location_id'-'last_known_location_id')::text"
                + " from trailers t where id=?",
            trailer);
  }

  void state(String state) throws Exception {
    f.db.execute("update trips set status=? where id=?", state, trip);
  }

  void noWrites() throws Exception {
    assertThat(count("trailer_couplings")).isZero();
    assertThat(count("trailer_movement_events")).isZero();
    assertThat(count("processed_client_operations")).isZero();
    assertThat(trailerField("version")).isEqualTo(0L);
  }

  @Test
  void hookWritesOnePhysicalFactAndMinimalProjectionWithoutDeparture() throws Exception {
    var beforeCommand = Instant.now().minusSeconds(1);
    var before = retained();
    var tripBefore = f.db.value("select row_to_json(t)::text from trips t where id=?", trip);
    var stopBefore = f.db.value("select row_to_json(s)::text from trip_stops s where id=?", stop);
    var response = hook(UUID.randomUUID());
    ok(response);
    assertThat(retained()).isEqualTo(before);
    assertThat(trailerField("version")).isEqualTo(1L);
    assertThat(trailerField("last_seen_at")).isEqualTo(Timestamp.from(Instant.parse(HOOK_AT)));
    assertThat(trailerField("last_driver_id")).isEqualTo(driver);
    assertThat(trailerField("location_state")).isEqualTo("LOCATION_UNKNOWN");
    assertThat(trailerField("current_location_id")).isNull();
    assertThat(f.db.value("select row_to_json(t)::text from trips t where id=?", trip))
        .isEqualTo(tripBefore);
    assertThat(f.db.value("select row_to_json(s)::text from trip_stops s where id=?", stop))
        .isEqualTo(stopBefore);
    assertThat(count("trailer_couplings")).isEqualTo(1L);
    assertThat(count("trailer_movement_events")).isEqualTo(1L);
    assertThat(
            f.db.value(
                "select actor_user_id from trailer_movement_events where tenant_id=?", tenant))
        .isEqualTo(user);
    assertThat(
            f.db.value("select driver_id from trailer_movement_events where tenant_id=?", tenant))
        .isEqualTo(driver);
    assertThat(
            f.db.value("select location_id from trailer_movement_events where tenant_id=?", tenant))
        .isNull();
    assertThat(
            f.db.value("select occurred_at from trailer_movement_events where tenant_id=?", tenant))
        .isEqualTo(Timestamp.from(Instant.parse(HOOK_AT)));
    assertThat(
            ((Timestamp)
                    f.db.value(
                        "select recorded_at from trailer_movement_events where tenant_id=?",
                        tenant))
                .toInstant())
        .isBetween(beforeCommand, Instant.now().plusSeconds(1));
    assertThat(JsonPath.<Integer>read(response.body(), "$.trip.version")).isZero();
    assertThat(response.body())
        .doesNotContain("tenantId", "responseData", "requestHash", "deviceId");
    assertThat(
            f.db.value(
                "select response_data::text from processed_client_operations where tenant_id=?",
                tenant))
        .asString()
        .doesNotContain("Bearer", "driver-token", "firebase", "requestHash", "customerId");
  }

  @Test
  void knownHookLocationRetainedInMovementAndProjection() throws Exception {
    f.db.execute(
        "update trailers set"
            + " location_state='AT_LOCATION',current_location_id=?,last_known_location_id=? where"
            + " id=?",
        location,
        location,
        trailer);
    ok(hook(UUID.randomUUID()));
    assertThat(trailerField("current_location_id")).isEqualTo(location);
    assertThat(
            f.db.value("select location_id from trailer_movement_events where tenant_id=?", tenant))
        .isEqualTo(location);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASSIGNED", "AT_STOP", "CANCELLING"})
  void allowedTripStatesDoNotTransition(String status) throws Exception {
    state(status);
    ok(hook(UUID.randomUUID()));
    ok(drop(UUID.randomUUID()));
    assertThat(f.db.value("select status from trips where id=?", trip)).isEqualTo(status);
    assertThat(f.db.value("select status from trip_stops where id=?", stop)).isEqualTo("PENDING");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "IN_TRANSIT", "COMPLETED", "CANCELLED"})
  void hookRejectsInvalidTripStates(String status) throws Exception {
    state(status);
    problem(hook(UUID.randomUUID()), 409, "INVALID_STATE_TRANSITION");
    noWrites();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "IN_TRANSIT", "COMPLETED", "CANCELLED"})
  void dropRejectsInvalidTripStates(String status) throws Exception {
    ok(hook(UUID.randomUUID()));
    state(status);
    problem(drop(UUID.randomUUID()), 409, "INVALID_STATE_TRANSITION");
    assertThat(count("trailer_movement_events")).isEqualTo(1L);
    assertThat(f.db.value("select dropped_at from trailer_couplings where tenant_id=?", tenant))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin", "ops", "yard", "dispatcher"})
  void noOverridesOrUnrelatedRoles(String token) throws Exception {
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), token, hookBody()),
        403,
        "CAPABILITY_DENIED");
    noWrites();
  }

  @Test
  void missingBearerRemains401() throws Exception {
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), null, hookBody()),
        401,
        "AUTHENTICATION_REQUIRED");
    noWrites();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"invalid", "1-1-1-1-1"})
  void invalidOrMissingIdempotencyKey(String key) throws Exception {
    problem(post(trip, "hooks", key, "driver", hookBody()), 400, "VALIDATION_FAILED");
    noWrites();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"trailerId", "tractorId", "driverId", "occurredAt", "expectedTrailerVersion"})
  void hookRequiredFields(String field) throws Exception {
    var body = hookBody();
    body.remove(field);
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        400,
        "VALIDATION_FAILED");
    noWrites();
  }

  @ParameterizedTest
  @ValueSource(strings = {"trailerId", "stopId", "locationId", "occurredAt"})
  void dropRequiredFields(String field) throws Exception {
    var body = dropBody();
    body.remove(field);
    problem(
        post(trip, "drops", UUID.randomUUID().toString(), "driver", body),
        400,
        "VALIDATION_FAILED");
    noWrites();
  }

  @ParameterizedTest
  @ValueSource(strings = {"status", "tenantId", "sourceType", "actorUserId", "version", "driverId"})
  void strictDropRejectsExtraFields(String field) throws Exception {
    var body = dropBody();
    body.put(field, "client hint");
    problem(
        post(trip, "drops", UUID.randomUUID().toString(), "driver", body),
        400,
        "VALIDATION_FAILED");
    noWrites();
  }

  @Test
  void strictHookAndNumericVersionValidation() throws Exception {
    for (var invalid : List.of(-1, 0.5, "0")) {
      var body = hookBody();
      body.put("expectedTrailerVersion", invalid);
      problem(
          post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
          400,
          "VALIDATION_FAILED");
    }
    var body = hookBody();
    body.put("role", "DRIVER");
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        400,
        "VALIDATION_FAILED");
    noWrites();
  }

  @Test
  void driverSpoofAndUnassignedIdentityNeverGrantAccess() throws Exception {
    var body = hookBody();
    body.put("driverId", UUID.randomUUID().toString());
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        404,
        "RESOURCE_NOT_FOUND");
    var otherUser = identity("other-driver", "DRIVER");
    f.db.driver(tenant, otherUser);
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "other-driver", hookBody()),
        403,
        "CAPABILITY_DENIED");
    noWrites();
  }

  @Test
  void inactiveDriverAndTractorRejectWithoutPhysicalWrites() throws Exception {
    f.db.execute("update drivers set active=false where id=?", driver);
    problem(hook(UUID.randomUUID()), 403, "CAPABILITY_DENIED");
    f.db.execute("update drivers set active=true where id=?", driver);
    f.db.execute("update tractors set active=false where id=?", tractor);
    problem(hook(UUID.randomUUID()), 409, "RESOURCE_CONFLICT");
    noWrites();
  }

  @Test
  void wrongTractorRejected() throws Exception {
    var body = hookBody();
    body.put("tractorId", f.db.tractor(tenant, "Other").toString());
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        409,
        "RESOURCE_CONFLICT");
    noWrites();
  }

  @ParameterizedTest
  @ValueSource(strings = {"inactive", "oos", "missing", "foreign"})
  void unavailableTrailer(String kind) throws Exception {
    if (kind.equals("inactive"))
      f.db.execute("update trailers set active=false where id=?", trailer);
    if (kind.equals("oos"))
      f.db.execute("update trailers set serviceability_state='OUT_OF_SERVICE' where id=?", trailer);
    var body = hookBody();
    if (kind.equals("missing") || kind.equals("foreign")) {
      var target =
          kind.equals("foreign")
              ? new TrailerFixtures(flyway).trailer(other, "Foreign", null)
              : UUID.randomUUID();
      body.put("trailerId", target.toString());
      // Unknown/foreign targets are not the durable expectation, so denial must not reveal
      // existence.
    }
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        409,
        kind.equals("missing") || kind.equals("foreign")
            ? "TRAILER_SUBSTITUTION_NOT_AUTHORIZED"
            : "RESOURCE_CONFLICT");
    noWrites();
  }

  @Test
  void nullExpectationBlocksAndSubstitutionAuthorizationAloneDoesNotHook() throws Exception {
    f.db.execute("update trips set planned_initial_trailer_id=null where id=?", trip);
    problem(hook(UUID.randomUUID()), 409, "TRAILER_SUBSTITUTION_NOT_AUTHORIZED");
    noWrites();
  }

  @Test
  void rejectThenAuthorizeAlternateThenExplicitHook() throws Exception {
    var next = new TrailerFixtures(flyway).trailer(tenant, "Alternate", customer);
    var body = hookBody();
    body.put("trailerId", next.toString());
    var key = UUID.randomUUID();
    problem(
        post(trip, "hooks", key.toString(), "driver", body),
        409,
        "TRAILER_SUBSTITUTION_NOT_AUTHORIZED");
    var decision =
        new HashMap<String, Object>(
            Map.of(
                "expectedTrailerId",
                trailer.toString(),
                "requestedTrailerId",
                next.toString(),
                "decision",
                "REJECT",
                "reason",
                "Known discrepancy"));
    ok(post(trip, "trailer-substitution-decisions", null, "ops", decision));
    problem(
        post(trip, "hooks", key.toString(), "driver", body),
        409,
        "TRAILER_SUBSTITUTION_NOT_AUTHORIZED");
    noWrites();
    decision.put("decision", "AUTHORIZE");
    ok(post(trip, "trailer-substitution-decisions", null, "ops", decision));
    noWrites();
    ok(post(trip, "hooks", key.toString(), "driver", body));
    assertThat(f.db.value("select trailer_id from trailer_couplings where tenant_id=?", tenant))
        .isEqualTo(next);
  }

  @Test
  void semanticReplayPrecedesStaleAndCurrentPhysicalState() throws Exception {
    var key = UUID.randomUUID();
    var first = hook(key);
    ok(first);
    var replay = hook(key);
    ok(replay);
    assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(first.body()));
    assertThat(trailerField("version")).isEqualTo(1L);
    assertThat(count("processed_client_operations")).isEqualTo(1L);
    problem(hook(UUID.randomUUID()), 409, "STALE_VERSION");
    var current = hookBody();
    current.put("expectedTrailerVersion", 1);
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", current),
        409,
        "TRAILER_ALREADY_COUPLED");
    ok(drop(UUID.randomUUID()));
    ok(hook(key));
    assertThat(count("trailer_couplings")).isEqualTo(1L);
    assertThat(count("trailer_movement_events")).isEqualTo(2L);
    assertThat(trailerField("version")).isEqualTo(2L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"trailerId", "tractorId", "occurredAt", "expectedTrailerVersion", "path", "type"})
  void reusedKeyDifferentLogicalRequest(String field) throws Exception {
    var key = UUID.randomUUID();
    ok(hook(key));
    var body = hookBody();
    var t = trip;
    var command = "hooks";
    if (field.equals("path")) t = trip(tenant, driver, tractor, trailer);
    else if (field.equals("type")) {
      command = "drops";
      body = dropBody();
    } else if (field.equals("occurredAt")) body.put(field, DROP_AT);
    else if (field.equals("expectedTrailerVersion")) body.put(field, 1);
    else body.put(field, UUID.randomUUID().toString());
    problem(post(t, command, key.toString(), "driver", body), 409, "IDEMPOTENCY_KEY_REUSED");
    assertThat(count("trailer_movement_events")).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"SUSPENDED", "DISABLED", "role_removed", "identity_unlinked", "reassigned"})
  void replayRequiresFreshActorAssignmentAndMembership(String change) throws Exception {
    var key = UUID.randomUUID();
    ok(hook(key));
    if (change.equals("SUSPENDED") || change.equals("DISABLED"))
      f.db.execute("update tenant_memberships set status=? where id=?", change, membership);
    if (change.equals("role_removed"))
      f.db.execute("delete from role_assignments where membership_id=?", membership);
    if (change.equals("identity_unlinked"))
      f.db.execute("update drivers set user_id=null where id=?", driver);
    if (change.equals("reassigned"))
      f.db.execute(
          "update trips set current_driver_id=? where id=?", f.db.driver(tenant, null), trip);
    var r = hook(key);
    assertThat(r.statusCode()).isEqualTo(403);
    assertThat(r.body()).doesNotContain("coupling", "movementEvent");
    assertThat(count("processed_client_operations")).isEqualTo(1L);
    assertThat(trailerField("version")).isEqualTo(1L);
  }

  @Test
  void dropClosesIntervalPreservesHistoryAndCanReplayAfterReactivationOrProjectionChange()
      throws Exception {
    ok(hook(UUID.randomUUID()));
    var retained = retained();
    var key = UUID.randomUUID();
    var r = drop(key);
    ok(r);
    assertThat(retained()).isEqualTo(retained);
    assertThat(trailerField("location_state")).isEqualTo("AT_LOCATION");
    assertThat(trailerField("current_location_id")).isEqualTo(location);
    assertThat(trailerField("last_known_location_id")).isEqualTo(location);
    assertThat(trailerField("last_driver_id")).isEqualTo(driver);
    assertThat(trailerField("last_seen_at")).isEqualTo(Timestamp.from(Instant.parse(DROP_AT)));
    assertThat(trailerField("version")).isEqualTo(2L);
    assertThat(f.db.value("select hooked_at from trailer_couplings where tenant_id=?", tenant))
        .isEqualTo(Timestamp.from(Instant.parse(HOOK_AT)));
    assertThat(f.db.value("select dropped_at from trailer_couplings where tenant_id=?", tenant))
        .isEqualTo(Timestamp.from(Instant.parse(DROP_AT)));
    assertThat(f.db.value("select driver_id from trailer_couplings where tenant_id=?", tenant))
        .isEqualTo(driver);
    assertThat(JsonPath.<String>read(r.body(), "$.movementEvent.eventType")).isEqualTo("DROPPED");
    assertThat(json.readTree(drop(key).body())).isEqualTo(json.readTree(r.body()));
    assertThat(trailerField("version")).isEqualTo(2L);
    assertThat(count("trailer_movement_events")).isEqualTo(2L);
    problem(drop(UUID.randomUUID()), 409, "RESOURCE_CONFLICT");
  }

  @Test
  void reassignedDriverDropsWithOwnProvenanceAndCouplingTractorEvenWhenCurrentTractorChanged()
      throws Exception {
    ok(hook(UUID.randomUUID()));
    var next = identity("replacement", "DRIVER");
    var nextDriver = f.db.driver(tenant, next);
    f.db.execute(
        "update trips set current_driver_id=?,current_tractor_id=? where id=?",
        nextDriver,
        f.db.tractor(tenant, "Changed"),
        trip);
    ok(post(trip, "drops", UUID.randomUUID().toString(), "replacement", dropBody()));
    assertThat(f.db.value("select driver_id from trailer_couplings where tenant_id=?", tenant))
        .isEqualTo(driver);
    assertThat(
            f.db.value(
                "select driver_id from trailer_movement_events where tenant_id=? and"
                    + " event_type='DROPPED'",
                tenant))
        .isEqualTo(nextDriver);
    assertThat(
            f.db.value(
                "select tractor_id from trailer_movement_events where tenant_id=? and"
                    + " event_type='DROPPED'",
                tenant))
        .isEqualTo(tractor);
    assertThat(trailerField("last_driver_id")).isEqualTo(nextDriver);
  }

  @Test
  void inactiveOosCoupledEquipmentCanStillSafelyDrop() throws Exception {
    ok(hook(UUID.randomUUID()));
    f.db.execute(
        "update trailers set active=false,serviceability_state='OUT_OF_SERVICE' where id=?",
        trailer);
    f.db.execute("update tractors set active=false where id=?", tractor);
    ok(drop(UUID.randomUUID()));
    assertThat(trailerField("active")).isEqualTo(false);
    assertThat(trailerField("serviceability_state")).isEqualTo("OUT_OF_SERVICE");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "stop_foreign",
        "stop_missing",
        "wrong_site",
        "foreign_location",
        "adhoc",
        "customer_mismatch",
        "before_hook",
        "no_coupling",
        "other_trip"
      })
  void invalidDropDoesNotCloseOrInventFacts(String kind) throws Exception {
    if (!kind.equals("no_coupling")) ok(hook(UUID.randomUUID()));
    var body = dropBody();
    var t = trip;
    if (kind.equals("stop_foreign"))
      body.put("stopId", stop(trip(tenant, driver, tractor, trailer), site).toString());
    if (kind.equals("stop_missing")) body.put("stopId", UUID.randomUUID().toString());
    if (kind.equals("wrong_site"))
      body.put("locationId", f.location(tenant, f.site(tenant, customer), null).toString());
    if (kind.equals("foreign_location"))
      body.put("locationId", new TrailerFixtures(flyway).location(other).toString());
    if (kind.equals("adhoc"))
      f.db.execute("update trip_stops set site_id=null,location_id=null where id=?", stop);
    if (kind.equals("customer_mismatch"))
      body.put("locationId", f.location(tenant, site, f.customer(tenant)).toString());
    if (kind.equals("before_hook")) body.put("occurredAt", "2026-10-09T19:00:00Z");
    if (kind.equals("other_trip")) t = trip(tenant, driver, tractor, trailer);
    problem(
        post(t, "drops", UUID.randomUUID().toString(), "driver", body),
        kind.startsWith("stop_") ? 404 : 409,
        kind.startsWith("stop_") ? "RESOURCE_NOT_FOUND" : "RESOURCE_CONFLICT");
    assertThat(count("trailer_movement_events")).isEqualTo(kind.equals("no_coupling") ? 0L : 1L);
    if (!kind.equals("no_coupling"))
      assertThat(f.db.value("select dropped_at from trailer_couplings where tenant_id=?", tenant))
          .isNull();
  }

  @Test
  void explicitDropThenHookReplacementMaintainsOneActiveTractorAndSameTripInvariant()
      throws Exception {
    ok(hook(UUID.randomUUID()));
    var next = new TrailerFixtures(flyway).trailer(tenant, "Replacement", customer);
    f.db.execute("update trips set planned_initial_trailer_id=? where id=?", next, trip);
    var body = hookBody();
    body.put("trailerId", next.toString());
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        409,
        "TRACTOR_ALREADY_COUPLED");
    ok(drop(UUID.randomUUID()));
    ok(post(trip, "hooks", UUID.randomUUID().toString(), "driver", body));
    assertThat(count("trailer_couplings")).isEqualTo(2L);
    assertThat(
            f.db.value(
                "select count(*) from trailer_couplings where tenant_id=? and tractor_id=? and"
                    + " dropped_at is null",
                tenant,
                tractor))
        .isEqualTo(1L);
    assertThat(
            f.db.value(
                "select count(*) from trailer_movement_events where tenant_id=? and trailer_id=?"
                    + " and event_type='DROPPED'",
                tenant,
                trailer))
        .isEqualTo(1L);
    // A free different Tractor still cannot bypass the existing open Trip coupling.
    var free = f.db.tractor(tenant, "Third");
    var third = new TrailerFixtures(flyway).trailer(tenant, "Third", customer);
    f.db.execute(
        "update trips set current_tractor_id=?,planned_initial_trailer_id=? where id=?",
        free,
        third,
        trip);
    body.put("trailerId", third.toString());
    body.put("tractorId", free.toString());
    problem(
        post(trip, "hooks", UUID.randomUUID().toString(), "driver", body),
        409,
        "RESOURCE_CONFLICT");
  }

  @ParameterizedTest
  @CsvSource({
    "hook,trailer_movement_events,INSERT",
    "hook,trailer_couplings,INSERT",
    "hook,trailers,UPDATE",
    "hook,processed_client_operations,INSERT",
    "hook,processed_client_operations,UPDATE",
    "drop,trailer_movement_events,INSERT",
    "drop,trailer_couplings,UPDATE",
    "drop,trailers,UPDATE",
    "drop,processed_client_operations,INSERT",
    "drop,processed_client_operations,UPDATE"
  })
  void eachWriteFailureRollsBackEntireCommand(String command, String table, String mutation)
      throws Exception {
    if (command.equals("drop")) ok(hook(UUID.randomUUID()));
    var trailerBefore =
        f.db.value("select row_to_json(t)::text from trailers t where id=?", trailer);
    var couplings = count("trailer_couplings");
    var events = count("trailer_movement_events");
    var ledger = count("processed_client_operations");
    var couplingBefore =
        f.db.value(
            "select coalesce(jsonb_agg(to_jsonb(c) order by id),'[]'::jsonb)::text from"
                + " trailer_couplings c where tenant_id=?",
            tenant);
    f.db.execute(
        "create function issue38_fail() returns trigger language plpgsql as $$ begin raise"
            + " exception using errcode='P0001',message='Controlled infrastructure failure'; end;"
            + " $$");
    f.db.execute(
        "create trigger issue38_fail before "
            + mutation
            + " on "
            + table
            + " for each row execute function issue38_fail()");
    try {
      var r = command.equals("hook") ? hook(UUID.randomUUID()) : drop(UUID.randomUUID());
      assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(500);
      assertThat(r.body())
          .contains("traceId")
          .doesNotContain("P0001", "Controlled infrastructure", "SQL");
      assertThat(f.db.value("select row_to_json(t)::text from trailers t where id=?", trailer))
          .isEqualTo(trailerBefore);
      assertThat(count("trailer_couplings")).isEqualTo(couplings);
      assertThat(count("trailer_movement_events")).isEqualTo(events);
      assertThat(count("processed_client_operations")).isEqualTo(ledger);
      assertNoProcessing();
      assertThat(
              f.db.value(
                  "select coalesce(jsonb_agg(to_jsonb(c) order by id),'[]'::jsonb)::text from"
                      + " trailer_couplings c where tenant_id=?",
                  tenant))
          .isEqualTo(couplingBefore);
    } finally {
      f.db.execute("drop trigger issue38_fail on " + table);
      f.db.execute("drop function issue38_fail()");
    }
  }

  void waitFor(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Lock test timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  void lockWait(String table) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        (Long)
                            f.db.value(
                                "select count(*) from pg_stat_activity where usename=? and"
                                    + " wait_event_type='Lock' and query like ?",
                                RUNTIME_USERNAME,
                                "%" + table + "%"))
                    .isGreaterThan(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"hooks", "drops"})
  void eightConcurrentIdenticalRequestsExecuteExactlyOnce(String command) throws Exception {
    if (command.equals("drops")) ok(hook(UUID.randomUUID()));
    var key = UUID.randomUUID().toString();
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(8)) {
      var futures = new ArrayList<Future<HttpResponse<String>>>();
      for (int i = 0; i < 8; i++)
        futures.add(
            pool.submit(
                () -> {
                  waitFor(gate);
                  return post(
                      trip,
                      command,
                      key,
                      "driver",
                      command.equals("hooks") ? hookBody() : dropBody());
                }));
      gate.countDown();
      String result = null;
      for (var future : futures) {
        var r = future.get(45, TimeUnit.SECONDS);
        ok(r);
        if (result == null) result = r.body();
        else assertThat(json.readTree(r.body())).isEqualTo(json.readTree(result));
      }
    }
    assertThat(count("trailer_couplings")).isEqualTo(1L);
    assertThat(count("trailer_movement_events")).isEqualTo(command.equals("hooks") ? 1L : 2L);
    assertThat(count("processed_client_operations")).isEqualTo(command.equals("hooks") ? 1L : 2L);
    assertThat(trailerField("version")).isEqualTo(command.equals("hooks") ? 1L : 2L);
    assertNoProcessing();
  }

  void assertNoProcessing() throws Exception {
    assertThat(
            f.db.value(
                "select count(*) from processed_client_operations where tenant_id=? and"
                    + " status='PROCESSING'",
                tenant))
        .isEqualTo(0L);
  }

  // Isolate unique-index arbitration from the HTTP layer's earlier exclusive Trip lock.
  // Both calls use the same stable, already persisted assigned Trip fixture; no production
  // endpoint bypasses the Trip/Driver authorization and locking protocol.
  TrailerCouplingTransactions.Result physicalCommand(String command, UUID key) {
    var context =
        new TrailerCouplingTransactions.TripContext(trip, "ASSIGNED", 0, driver, tractor, trailer);
    if (command.equals("hooks"))
      return physical.hook(
          tenant,
          user,
          key,
          context,
          driver,
          new TrailerCouplingTransactions.Hook(
              trailer, tractor, driver, Instant.parse(HOOK_AT), 0));
    return physical.drop(
        tenant,
        user,
        key,
        context,
        driver,
        new TrailerCouplingTransactions.Drop(trailer, stop, location, Instant.parse(DROP_AT)),
        () -> {});
  }

  @ParameterizedTest
  @CsvSource({"hooks,false", "hooks,true", "drops,false", "drops,true"})
  void uniqueClaimWaitsForWinnerAndReplaysCommitOrContinuesAfterRollback(
      String command, boolean rollback) throws Exception {
    if (command.equals("drops")) ok(hook(UUID.randomUUID()));
    var key = UUID.randomUUID();
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var winnerClaim = new java.util.concurrent.atomic.AtomicReference<UUID>();
    var winnerResponse =
        new java.util.concurrent.atomic.AtomicReference<TrailerCouplingTransactions.Result>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var response = physicalCommand(command, key);
                        winnerResponse.set(response);
                        winnerClaim.set(
                            (UUID)
                                em.createNativeQuery(
                                        "select id from processed_client_operations where"
                                            + " tenant_id=:tenant and client_operation_id=:key",
                                        UUID.class)
                                    .setParameter("tenant", tenant)
                                    .setParameter("key", key)
                                    .getSingleResult());
                        held.countDown();
                        waitFor(release);
                        if (rollback) throw new IllegalStateException("Controlled winner rollback");
                        return response;
                      }));
      try {
        waitFor(held);
        // An uncommitted claim and physical writes are invisible to another connection.
        assertThat(
                f.db.value(
                    "select count(*) from processed_client_operations where tenant_id=? and"
                        + " client_operation_id=?",
                    tenant,
                    key))
            .isEqualTo(0L);
        var waiter =
            pool.submit(() -> transactions.execute(tenant, () -> physicalCommand(command, key)));
        lockWait("insert into processed_client_operations");
        assertThat(waiter.isDone()).isFalse();
        release.countDown();
        if (rollback) {
          assertThatThrownBy(() -> winner.get(30, TimeUnit.SECONDS))
              .hasRootCauseMessage("Controlled winner rollback");
        } else assertThat(winner.get(30, TimeUnit.SECONDS)).isEqualTo(winnerResponse.get());
        var result = waiter.get(30, TimeUnit.SECONDS);
        if (!rollback) assertThat(result).isEqualTo(winnerResponse.get());
        var committedClaim =
            (UUID)
                f.db.value(
                    "select id from processed_client_operations where tenant_id=? and"
                        + " client_operation_id=?",
                    tenant,
                    key);
        if (rollback) assertThat(committedClaim).isNotEqualTo(winnerClaim.get());
        else assertThat(committedClaim).isEqualTo(winnerClaim.get());
        assertThat(
                f.db.value(
                    "select status from processed_client_operations where id=?", committedClaim))
            .isEqualTo("SUCCEEDED");
        assertThat(count("trailer_couplings")).isEqualTo(1L);
        assertThat(count("trailer_movement_events")).isEqualTo(command.equals("hooks") ? 1L : 2L);
        assertThat(count("processed_client_operations"))
            .isEqualTo(command.equals("hooks") ? 1L : 2L);
        assertThat(trailerField("version")).isEqualTo(command.equals("hooks") ? 1L : 2L);
        assertNoProcessing();
      } finally {
        release.countDown();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"hooks", "drops"})
  void processingClaimExistsInsideBusinessTransactionBeforeAnyMovementWrite(String command)
      throws Exception {
    if (command.equals("drops")) ok(hook(UUID.randomUUID()));
    f.db.execute(
        "create function issue38_claim_required() returns trigger language plpgsql as $$ begin  if"
            + " not exists(select 1 from processed_client_operations p where"
            + " p.tenant_id=NEW.tenant_id and p.client_operation_id=NEW.client_operation_id and"
            + " p.status='PROCESSING' and p.processed_at is null and p.created_at is not null and"
            + " p.request_hash is not null) then raise exception using"
            + " errcode='23514',message='Missing transaction claim'; end if; return NEW; end; $$");
    f.db.execute(
        "create trigger issue38_claim_required before insert on trailer_movement_events"
            + " for each row execute function issue38_claim_required()");
    try {
      ok(command.equals("hooks") ? hook(UUID.randomUUID()) : drop(UUID.randomUUID()));
      assertNoProcessing();
      assertThat(
              f.db.value(
                  "select count(*) from processed_client_operations where tenant_id=? and"
                      + " status='SUCCEEDED' and processed_at>=created_at",
                  tenant))
          .isEqualTo(command.equals("hooks") ? 1L : 2L);
    } finally {
      f.db.execute("drop trigger issue38_claim_required on trailer_movement_events");
      f.db.execute("drop function issue38_claim_required()");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "null,now(),PROCESSING,null,23502",
    "hash,null,PROCESSING,null,23502",
    "hash,now(),PROCESSING,now(),23514",
    "hash,now(),SUCCEEDED,null,23514",
    "hash,now(),REJECTED,null,23514"
  })
  void ledgerRejectsMissingRequiredFieldsAndInconsistentMilestones(
      String hash, String created, String status, String processed, String sqlState) {
    rejectSql(
        sqlState,
        () ->
            f.db.execute(
                "insert into"
                    + " processed_client_operations(id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at,processed_at)"
                    + " values(?,?,?,?,'TRAILER_HOOK',"
                    + (hash.equals("null") ? "null" : "'hash'")
                    + ",?,"
                    + created
                    + ","
                    + processed
                    + ")",
                UUID.randomUUID(),
                tenant,
                UUID.randomUUID(),
                user,
                status));
  }

  @Test
  void ledgerSupportsTransactionLocalProcessingAndBothTerminalStatuses() throws Exception {
    try (var c = runtime.getConnection()) {
      c.setAutoCommit(false);
      context(c, tenant, null);
      for (var status : List.of("PROCESSING", "SUCCEEDED", "REJECTED")) {
        f.db.execute(
            c,
            "insert into"
                + " processed_client_operations(id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at,processed_at)"
                + " values(?,?,?,?,'TRAILER_HOOK','hash',?,now(),"
                + (status.equals("PROCESSING") ? "null" : "now()")
                + ")",
            UUID.randomUUID(),
            tenant,
            UUID.randomUUID(),
            user,
            status);
      }
      assertThat(rows(c, "processed_client_operations")).isEqualTo(3L);
      c.rollback(); // PROCESSING is a transaction-local fixture, never committed as a lease.
    }
    assertThat(
            f.db.value(
                "select is_nullable from information_schema.columns where"
                    + " table_name='processed_client_operations' and column_name='request_hash'"))
        .isEqualTo("NO");
    assertThat(
            f.db.value(
                "select is_nullable from information_schema.columns where"
                    + " table_name='processed_client_operations' and column_name='created_at'"))
        .isEqualTo("NO");
    assertThat(
            f.db.value(
                "select is_nullable from information_schema.columns where"
                    + " table_name='processed_client_operations' and column_name='processed_at'"))
        .isEqualTo("YES");
    assertNoProcessing();
  }

  @ParameterizedTest
  @ValueSource(strings = {"trailer", "tractor"})
  void concurrentDifferentTripsCannotDoubleCouple(String shared) throws Exception {
    var secondTrailer =
        shared.equals("trailer")
            ? trailer
            : new TrailerFixtures(flyway).trailer(tenant, "Second", customer);
    var secondTractor = shared.equals("tractor") ? tractor : f.db.tractor(tenant, "Second");
    var secondTrip = trip(tenant, driver, secondTractor, secondTrailer);
    var second = hookBody();
    second.put("trailerId", secondTrailer.toString());
    second.put("tractorId", secondTractor.toString());
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                waitFor(gate);
                return hook(UUID.randomUUID());
              });
      var b =
          pool.submit(
              () -> {
                waitFor(gate);
                return post(secondTrip, "hooks", UUID.randomUUID().toString(), "driver", second);
              });
      gate.countDown();
      var responses = List.of(a.get(40, TimeUnit.SECONDS), b.get(40, TimeUnit.SECONDS));
      assertThat(responses.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(1);
      problem(
          responses.stream().filter(r -> r.statusCode() != 200).findFirst().orElseThrow(),
          409,
          shared.equals("trailer") ? "TRAILER_ALREADY_COUPLED" : "TRACTOR_ALREADY_COUPLED");
    }
    assertThat(count("trailer_couplings")).isEqualTo(1L);
    assertThat(count("trailer_movement_events")).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "substitution"})
  void hookTripLockSerializesReassignmentAndSubstitution(String kind) throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var key = UUID.randomUUID().toString();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var result = service.hook(actor, tenant, trip, key, hookBody());
                        held.countDown();
                        waitFor(release);
                        return result;
                      }));
      waitFor(held);
      Map<String, Object> body;
      String endpoint;
      if (kind.equals("substitution")) {
        endpoint = "trailer-substitution-decisions";
        body =
            Map.of(
                "expectedTrailerId",
                trailer.toString(),
                "requestedTrailerId",
                new TrailerFixtures(flyway).trailer(tenant, "Alternate", customer).toString(),
                "decision",
                "AUTHORIZE",
                "reason",
                "Dispatcher change");
      } else {
        endpoint = kind + "-assignments";
        var target =
            kind.equals("driver") ? f.db.driver(tenant, null) : f.db.tractor(tenant, "Alternate");
        body =
            Map.of(
                "expectedVersion",
                0,
                kind + "Id",
                target.toString(),
                "reason",
                "Dispatcher change");
      }
      var second = pool.submit(() -> post(trip, endpoint, null, "ops", body));
      try {
        lockWait("trips");
      } finally {
        release.countDown();
      }
      assertThat(first.get(30, TimeUnit.SECONDS).coupling().trailerId()).isEqualTo(trailer);
      ok(second.get(30, TimeUnit.SECONDS));
      assertThat(f.db.value("select driver_id from trailer_couplings where tenant_id=?", tenant))
          .isEqualTo(driver);
      assertThat(f.db.value("select tractor_id from trailer_couplings where tenant_id=?", tenant))
          .isEqualTo(tractor);
    } finally {
      release.countDown();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "driver,current_driver_id",
    "tractor,current_tractor_id",
    "trailer,planned_initial_trailer_id"
  })
  void assignmentWinningLockIsRevalidatedBeforeHook(String kind, String column) throws Exception {
    var target =
        kind.equals("driver")
            ? f.db.driver(tenant, null)
            : kind.equals("tractor")
                ? f.db.tractor(tenant, "Alternate")
                : new TrailerFixtures(flyway).trailer(tenant, "Alternate", customer);
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                try (var c = runtime.getConnection()) {
                  c.setAutoCommit(false);
                  context(c, tenant, null);
                  f.db.execute(c, "update trips set " + column + "=? where id=?", target, trip);
                  held.countDown();
                  waitFor(release);
                  c.commit();
                  return true;
                }
              });
      waitFor(held);
      var second = pool.submit(() -> hook(UUID.randomUUID()));
      try {
        lockWait("trips");
      } finally {
        release.countDown();
      }
      assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
      var r = second.get(30, TimeUnit.SECONDS);
      problem(
          r,
          kind.equals("driver") ? 403 : 409,
          kind.equals("driver")
              ? "CAPABILITY_DENIED"
              : kind.equals("trailer")
                  ? "TRAILER_SUBSTITUTION_NOT_AUTHORIZED"
                  : "RESOURCE_CONFLICT");
      noWrites();
    } finally {
      release.countDown();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "true,active=false",
    "false,active=false",
    "true,serviceability_state='OUT_OF_SERVICE'",
    "false,serviceability_state='OUT_OF_SERVICE'"
  })
  void hookAndAdministrativeEligibilityMutationAreSerialized(boolean hookFirst, String change)
      throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      if (hookFirst) {
        var first =
            pool.submit(
                () ->
                    transactions.execute(
                        tenant,
                        () -> {
                          var r =
                              service.hook(
                                  actor, tenant, trip, UUID.randomUUID().toString(), hookBody());
                          held.countDown();
                          waitFor(release);
                          return r;
                        }));
        waitFor(held);
        var second =
            pool.submit(
                () -> {
                  try (var c = runtime.getConnection()) {
                    c.setAutoCommit(false);
                    context(c, tenant, null);
                    f.db.execute(c, "update trailers set " + change + " where id=?", trailer);
                    c.commit();
                    return true;
                  }
                });
        try {
          lockWait("trailers");
        } finally {
          release.countDown();
        }
        assertThat(first.get(30, TimeUnit.SECONDS).trailer().version()).isEqualTo(1);
        assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
      } else {
        var first =
            pool.submit(
                () -> {
                  try (var c = runtime.getConnection()) {
                    c.setAutoCommit(false);
                    context(c, tenant, null);
                    f.db.execute(c, "update trailers set " + change + " where id=?", trailer);
                    held.countDown();
                    waitFor(release);
                    c.commit();
                    return true;
                  }
                });
        waitFor(held);
        var second = pool.submit(() -> hook(UUID.randomUUID()));
        try {
          lockWait("trailers");
        } finally {
          release.countDown();
        }
        assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
        problem(second.get(30, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
        noWrites();
      }
    } finally {
      release.countDown();
    }
  }

  void context(Connection c, UUID t, UUID u) throws SQLException {
    try (var q =
        c.prepareStatement(
            "select"
                + " set_config('app.current_tenant_id',?,true),set_config('app.current_user_id',?,true)")) {
      q.setString(1, t == null ? "" : t.toString());
      q.setString(2, u == null ? "" : u.toString());
      q.executeQuery().close();
    }
  }

  @Test
  void anotherCurrentlyAssignedUserCannotReplayAnOldUsersOperation() throws Exception {
    var key = UUID.randomUUID();
    ok(hook(key));
    var next = identity("replacement", "DRIVER");
    var replacement = f.db.driver(tenant, next);
    f.db.execute("update trips set current_driver_id=? where id=?", replacement, trip);
    problem(
        post(trip, "hooks", key.toString(), "replacement", hookBody()),
        409,
        "IDEMPOTENCY_KEY_REUSED");
    var changed = hookBody();
    changed.put("driverId", replacement.toString());
    problem(
        post(trip, "hooks", key.toString(), "replacement", changed), 409, "IDEMPOTENCY_KEY_REUSED");
    assertThat(count("processed_client_operations")).isEqualTo(1L);
  }

  @Test
  void schemaAndClosedIntervalHistoryRemainProtectedAtDatabaseBoundary() throws Exception {
    flyway.validate();
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("18");
    assertThat(columns("trailer_couplings"))
        .containsExactly(
            "id",
            "tenant_id",
            "trailer_id",
            "tractor_id",
            "driver_id",
            "trip_id",
            "hooked_at",
            "dropped_at",
            "hook_event_id",
            "drop_event_id",
            "created_at");
    assertThat(columns("processed_client_operations"))
        .containsExactly(
            "id",
            "tenant_id",
            "client_operation_id",
            "user_id",
            "device_id",
            "operation_type",
            "request_hash",
            "status",
            "created_at",
            "processed_at",
            "result_entity_type",
            "result_entity_id",
            "response_data");
    assertThat(
            f.db.value(
                "select count(*) from pg_constraint where conrelid in"
                    + " ('trailer_couplings'::regclass,'trailer_movement_events'::regclass,'processed_client_operations'::regclass)"
                    + " and confdeltype='c'"))
        .isEqualTo(0L);
    ok(hook(UUID.randomUUID()));
    var id = f.db.value("select id from trailer_couplings where tenant_id=?", tenant);
    rejectSql(
        "23514",
        () -> f.db.execute("update trailer_couplings set dropped_at=now() where id=?", id));
    rejectSql(
        "23514",
        () -> f.db.execute("update trailer_couplings set hook_event_id=null where id=?", id));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_couplings set dropped_at=hooked_at-interval '1"
                    + " second',drop_event_id=hook_event_id where id=?",
                id));
    ok(drop(UUID.randomUUID()));
    var closed = f.db.value("select row_to_json(c)::text from trailer_couplings c where id=?", id);
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_couplings set dropped_at=null,drop_event_id=null where id=?", id));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_couplings set driver_id=? where id=?",
                f.db.driver(tenant, null),
                id));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_couplings set dropped_at=dropped_at+interval '1 second' where id=?",
                id));
    assertThat(f.db.value("select row_to_json(c)::text from trailer_couplings c where id=?", id))
        .isEqualTo(closed);
  }

  List<String> columns(String table) throws Exception {
    try (var c = flyway.getConfiguration().getDataSource().getConnection();
        var q =
            c.prepareStatement(
                "select column_name from information_schema.columns where table_name=? order by"
                    + " ordinal_position")) {
      q.setString(1, table);
      try (var r = q.executeQuery()) {
        var result = new ArrayList<String>();
        while (r.next()) result.add(r.getString(1));
        return result;
      }
    }
  }

  interface SqlAction {
    void run() throws Exception;
  }

  void rejectSql(String state, SqlAction run) {
    assertThatThrownBy(run::run)
        .isInstanceOf(SQLException.class)
        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(state));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"trailer_movement_events", "trailer_couplings", "processed_client_operations"})
  void exactRuntimeGrantsAndRlsExcludeUnscopedBootstrapAndForeignRows(String table)
      throws Exception {
    ok(hook(UUID.randomUUID()));
    try (var c = runtime.getConnection();
        var q =
            c.prepareStatement(
                """
                select r.rolsuper,r.rolbypassrls,pg_get_userbyid(t.relowner)=current_user,
                  has_schema_privilege(current_user,'public','CREATE'),t.relrowsecurity,
                  has_table_privilege(current_user,t.oid,'SELECT'),has_table_privilege(current_user,t.oid,'INSERT'),
                  has_table_privilege(current_user,t.oid,'UPDATE'),has_table_privilege(current_user,t.oid,'DELETE'),
                  (select count(*) from aclexplode(t.relacl) where grantee=0)
                from pg_class t join pg_roles r on r.rolname=current_user where t.oid=?::regclass
                """)) {
      q.setString(1, table);
      try (var r = q.executeQuery()) {
        r.next();
        for (int i = 1; i <= 4; i++) assertThat(r.getBoolean(i)).isFalse();
        for (int i = 5; i <= 7; i++) assertThat(r.getBoolean(i)).isTrue();
        assertThat(r.getBoolean(8)).isFalse();
        assertThat(r.getBoolean(9)).isFalse();
        assertThat(r.getInt(10)).isZero();
      }
      assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
      c.setAutoCommit(false);
      context(c, null, null);
      assertThat(rows(c, table)).isZero();
      context(c, tenant, user);
      assertThat(rows(c, table)).isZero();
      context(c, other, null);
      assertThat(rows(c, table)).isZero();
      context(c, tenant, null);
      assertThat(rows(c, table)).isEqualTo(1L);
      rejectSql(
          "42501", () -> f.db.execute(c, "delete from " + table + " where tenant_id=?", tenant));
      c.rollback();
      context(c, tenant, null);
      if (!table.equals("trailer_couplings")) {
        rejectSql(
            "42501",
            () ->
                f.db.execute(
                    c, "update " + table + " set tenant_id=tenant_id where tenant_id=?", tenant));
        c.rollback();
      } else {
        assertThat(
                value(
                    c,
                    "select"
                        + " has_column_privilege(current_user,'trailer_couplings','dropped_at','UPDATE')"))
            .isEqualTo(true);
        assertThat(
                value(
                    c,
                    "select"
                        + " has_column_privilege(current_user,'trailer_couplings','drop_event_id','UPDATE')"))
            .isEqualTo(true);
        for (var column : columns(table))
          if (!Set.of("dropped_at", "drop_event_id").contains(column)) {
            assertThat(
                    value(
                        c,
                        "select has_column_privilege(current_user,'trailer_couplings','"
                            + column
                            + "','UPDATE')"))
                .isEqualTo(false);
          }
        rejectSql(
            "42501",
            () ->
                f.db.execute(
                    c,
                    "update trailer_couplings set driver_id=driver_id where tenant_id=?",
                    tenant));
        c.rollback();
      }
      if (table.equals("processed_client_operations")) {
        for (var column : columns(table))
          assertThat(
                  value(
                      c,
                      "select has_column_privilege(current_user,'processed_client_operations','"
                          + column
                          + "','UPDATE')"))
              .isEqualTo(
                  Set.of(
                          "status",
                          "processed_at",
                          "result_entity_type",
                          "result_entity_id",
                          "response_data")
                      .contains(column));
      }
    }
  }

  long rows(Connection c, String table) throws Exception {
    return ((Number) value(c, "select count(*) from " + table)).longValue();
  }

  Object value(Connection c, String sql) throws Exception {
    try (var q = c.prepareStatement(sql);
        var r = q.executeQuery()) {
      r.next();
      return r.getObject(1);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"trailer_movement_events", "trailer_couplings", "processed_client_operations"})
  void runtimeCannotInsertAnotherTenantsPhysicalFact(String table) throws Exception {
    ok(hook(UUID.randomUUID()));
    // Fixture INSERT shape comes from the real catalog, so newly required provenance is included.
    var cols = columns(table);
    var projection = cols.stream().map(c -> c.equals("id") ? "?::uuid" : c).toList();
    try (var c = runtime.getConnection()) {
      c.setAutoCommit(false);
      context(c, other, null);
      // Copying a foreign Tenant row cannot enumerate it through RLS either.
      assertThat(
              f.db.execute(
                  c,
                  "insert into "
                      + table
                      + " select "
                      + String.join(",", projection)
                      + " from "
                      + table
                      + " where tenant_id=?",
                  UUID.randomUUID(),
                  tenant))
          .isZero();
      c.rollback();
      context(c, null, null);
      rejectSql(
          "42501",
          () ->
              f.db.execute(
                  c,
                  "insert into"
                      + " processed_client_operations(id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at,processed_at)"
                      + " values(?,?,?,?,'TRAILER_HOOK','hash','SUCCEEDED',now(),now())",
                  UUID.randomUUID(),
                  tenant,
                  UUID.randomUUID(),
                  user));
      c.rollback();
      context(c, other, null);
      // Copy fixture values through prepared parameters; the target Tenant is A while context is B.
      var values = new ArrayList<Object>();
      try (var admin = flyway.getConfiguration().getDataSource().getConnection();
          var q = admin.prepareStatement("select * from " + table + " where tenant_id=? limit 1")) {
        q.setObject(1, tenant);
        try (var r = q.executeQuery()) {
          assertThat(r.next()).isTrue();
          for (int i = 1; i <= cols.size(); i++)
            values.add(i == 1 ? UUID.randomUUID() : r.getObject(i));
        }
      }
      rejectSql(
          "42501",
          () ->
              f.db.execute(
                  c,
                  "insert into "
                      + table
                      + " values("
                      + String.join(",", Collections.nCopies(cols.size(), "?"))
                      + ")",
                  values.toArray()));
      c.rollback();
    }
  }

  @Test
  void partialUniqueIndexesAndTenantSafeParentsRemainFinalIntegrityBarrier() throws Exception {
    ok(hook(UUID.randomUUID()));
    var c = (UUID) f.db.value("select id from trailer_couplings where tenant_id=?", tenant);
    rejectSql(
        "23505",
        () ->
            f.db.execute(
                "insert into trailer_couplings select"
                    + " ?,tenant_id,trailer_id,tractor_id,driver_id,trip_id,hooked_at,dropped_at,hook_event_id,drop_event_id,created_at"
                    + " from trailer_couplings where id=?",
                UUID.randomUUID(),
                c));
    var t = new TrailerFixtures(flyway).trailer(tenant, "Another", customer);
    rejectSql(
        "23505",
        () ->
            f.db.execute(
                "insert into trailer_couplings select"
                    + " ?,tenant_id,?,tractor_id,driver_id,trip_id,hooked_at,dropped_at,hook_event_id,drop_event_id,created_at"
                    + " from trailer_couplings where id=?",
                UUID.randomUUID(),
                t,
                c));
    var foreignDriver = f.db.driver(other, null);
    var foreignTractor = f.db.tractor(other, "Foreign");
    var foreignTrailer = new TrailerFixtures(flyway).trailer(other, "Foreign", null);
    var foreignTrip = trip(other, foreignDriver, foreignTractor, foreignTrailer);
    for (var entry :
        Map.of(
                "driver_id",
                foreignDriver,
                "tractor_id",
                foreignTractor,
                "trailer_id",
                foreignTrailer,
                "trip_id",
                foreignTrip,
                "hook_event_id",
                UUID.randomUUID())
            .entrySet()) {
      var columns = columns("trailer_couplings");
      var selects =
          columns.stream()
              .map(x -> x.equals("id") || x.equals(entry.getKey()) ? "?::uuid" : x)
              .toList();
      // A closed copy avoids the independent active uniqueness checks.
      okIfNotDropped();
      rejectSql(
          "23503",
          () ->
              f.db.execute(
                  "insert into trailer_couplings select "
                      + String.join(",", selects)
                      + " from trailer_couplings where id=?",
                  UUID.randomUUID(),
                  entry.getValue(),
                  c));
    }
    rejectSql(
        "23505",
        () ->
            f.db.execute(
                "insert into processed_client_operations select"
                    + " ?,tenant_id,client_operation_id,user_id,device_id,operation_type,request_hash,status,created_at,processed_at,result_entity_type,result_entity_id,response_data"
                    + " from processed_client_operations where tenant_id=? limit 1",
                UUID.randomUUID(),
                tenant));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "insert into"
                    + " processed_client_operations(id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at,processed_at)"
                    + " values(?,?,?,?,'TRAILER_HOOK','hash','UNKNOWN',now(),now())",
                UUID.randomUUID(),
                tenant,
                UUID.randomUUID(),
                user));
  }

  void okIfNotDropped() throws Exception {
    if (f.db.value("select dropped_at from trailer_couplings where tenant_id=?", tenant) == null)
      ok(drop(UUID.randomUUID()));
  }

  @Test
  void manualHookAndDropPersistDistinctServerDerivedActorAndSource() throws Exception {
    ok(hook(UUID.randomUUID()));
    ok(drop(UUID.randomUUID()));
    assertThat(columns("trailer_movement_events"))
        .containsExactly(
            "id",
            "tenant_id",
            "trailer_id",
            "trip_id",
            "trip_stop_id",
            "driver_id",
            "tractor_id",
            "location_id",
            "event_type",
            "actor_type",
            "source_type",
            "actor_user_id",
            "occurred_at",
            "recorded_at",
            "client_operation_id",
            "reason",
            "metadata");
    for (var type : List.of("HOOKED", "DROPPED")) {
      assertThat(
              f.db.value(
                  "select actor_type from trailer_movement_events where tenant_id=? and"
                      + " event_type=?",
                  tenant,
                  type))
          .isEqualTo("HUMAN_USER");
      assertThat(
              f.db.value(
                  "select source_type from trailer_movement_events where tenant_id=? and"
                      + " event_type=?",
                  tenant,
                  type))
          .isEqualTo("USER_ACTION");
      assertThat(
              f.db.value(
                  "select actor_user_id from trailer_movement_events where tenant_id=? and"
                      + " event_type=?",
                  tenant,
                  type))
          .isEqualTo(user);
      assertThat(
              f.db.value(
                  "select reason from trailer_movement_events where tenant_id=? and event_type=?",
                  tenant,
                  type))
          .isNull();
      assertThat(
              f.db.value(
                  "select metadata from trailer_movement_events where tenant_id=? and event_type=?",
                  tenant,
                  type))
          .isNull();
    }
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_movement_events set actor_user_id=null where tenant_id=?", tenant));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_movement_events set actor_type='DRIVER' where tenant_id=?",
                tenant));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_movement_events set source_type='MOBILE' where tenant_id=?",
                tenant));
    // Future non-human facts have no fabricated User; this fixture does not add an execution flow.
    f.db.execute(
        "update trailer_movement_events set"
            + " actor_type='SYSTEM',source_type='SYSTEM_PROCESS',actor_user_id=null where"
            + " tenant_id=? and event_type='DROPPED'",
        tenant);
  }

  @ParameterizedTest
  @CsvSource({
    "hooks,actorType",
    "hooks,sourceType",
    "hooks,actorUserId",
    "drops,actorType",
    "drops,sourceType",
    "drops,actorUserId"
  })
  void clientCannotSupplyActorOrSourceProvenance(String command, String field) throws Exception {
    var body = command.equals("hooks") ? hookBody() : dropBody();
    body.put(field, user.toString());
    problem(
        post(trip, command, UUID.randomUUID().toString(), "driver", body),
        400,
        "VALIDATION_FAILED");
    noWrites();
  }

  @Test
  void movementCatalogParentsAndOperationUniquenessAreDatabaseEnforced() throws Exception {
    var key = UUID.randomUUID();
    ok(hook(key));
    var event =
        (UUID) f.db.value("select id from trailer_movement_events where tenant_id=?", tenant);
    rejectSql(
        "23505",
        () ->
            f.db.execute(
                "insert into trailer_movement_events select"
                    + " ?,tenant_id,trailer_id,trip_id,trip_stop_id,driver_id,tractor_id,location_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at,client_operation_id,reason,metadata"
                    + " from trailer_movement_events where id=?",
                UUID.randomUUID(),
                event));
    for (var type :
        List.of(
            "HOOKED",
            "DROPPED",
            "DEPARTED",
            "ARRIVED",
            "YARD_MOVED",
            "LOCATION_CORRECTED",
            "LOCATION_MARKED_UNKNOWN",
            "LOCATION_RECOVERED",
            "LOAD_STATE_CHANGED",
            "MARKED_OOS",
            "RETURNED_TO_SERVICE",
            "DESIGNATED_CUSTOMER_CHANGED")) {
      f.db.execute("update trailer_movement_events set event_type=? where id=?", type, event);
    }
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_movement_events set event_type='UNKNOWN' where id=?", event));
    for (var type :
        List.of("USER_ACTION", "MOBILE_AUTOMATION", "SYSTEM_PROCESS", "EXTERNAL_INTEGRATION"))
      f.db.execute("update trailer_movement_events set source_type=? where id=?", type, event);
    var fd = f.db.driver(other, null);
    var ft = f.db.tractor(other, "Foreign");
    var fr = new TrailerFixtures(flyway).trailer(other, "Foreign", null);
    var fp = trip(other, fd, ft, fr);
    var fl = new TrailerFixtures(flyway).location(other);
    for (var entry :
        Map.of(
                "trailer_id",
                fr,
                "trip_id",
                fp,
                "driver_id",
                fd,
                "tractor_id",
                ft,
                "location_id",
                fl,
                "actor_user_id",
                UUID.randomUUID())
            .entrySet()) {
      rejectSql(
          "23503",
          () ->
              f.db.execute(
                  "update trailer_movement_events set " + entry.getKey() + "=? where id=?",
                  entry.getValue(),
                  event));
    }
    var otherTrip = trip(tenant, driver, tractor, trailer);
    var wrongStop = stop(otherTrip, site);
    rejectSql(
        "23503",
        () ->
            f.db.execute(
                "update trailer_movement_events set trip_stop_id=? where id=?", wrongStop, event));
    rejectSql(
        "23514",
        () ->
            f.db.execute(
                "update trailer_movement_events set trip_id=null,trip_stop_id=? where id=?",
                stop,
                event));
    f.db.execute(
        "update trailer_movement_events set actor_type='INTEGRATION',actor_user_id=null where id=?",
        event);
  }

  @Test
  void concurrentDifferentKeyDropsCannotCloseTwice() throws Exception {
    ok(hook(UUID.randomUUID()));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                waitFor(gate);
                return drop(UUID.randomUUID());
              });
      var b =
          pool.submit(
              () -> {
                waitFor(gate);
                return drop(UUID.randomUUID());
              });
      gate.countDown();
      var results = List.of(a.get(40, TimeUnit.SECONDS), b.get(40, TimeUnit.SECONDS));
      assertThat(results.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(1);
      problem(
          results.stream().filter(r -> r.statusCode() != 200).findFirst().orElseThrow(),
          409,
          "RESOURCE_CONFLICT");
    }
    assertThat(count("trailer_movement_events")).isEqualTo(2L);
    assertThat(count("processed_client_operations")).isEqualTo(2L);
    assertThat(trailerField("version")).isEqualTo(2L);
  }

  @Test
  void dropLockMakesReplacementHookWaitAndObserveClosedInterval() throws Exception {
    ok(hook(UUID.randomUUID()));
    var next = new TrailerFixtures(flyway).trailer(tenant, "Replacement", customer);
    f.db.execute("update trips set planned_initial_trailer_id=? where id=?", next, trip);
    var body = hookBody();
    body.put("trailerId", next.toString());
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var r =
                            service.drop(
                                actor, tenant, trip, UUID.randomUUID().toString(), dropBody());
                        held.countDown();
                        waitFor(release);
                        return r;
                      }));
      waitFor(held);
      var second =
          pool.submit(() -> post(trip, "hooks", UUID.randomUUID().toString(), "driver", body));
      try {
        lockWait("trips");
      } finally {
        release.countDown();
      }
      assertThat(first.get(30, TimeUnit.SECONDS).coupling().droppedAt()).isNotNull();
      ok(second.get(30, TimeUnit.SECONDS));
      assertThat(
              f.db.value(
                  "select count(*) from trailer_couplings where tenant_id=? and dropped_at is null",
                  tenant))
          .isEqualTo(1L);
      assertThat(
              f.db.value(
                  "select trailer_id from trailer_couplings where tenant_id=? and dropped_at is"
                      + " null",
                  tenant))
          .isEqualTo(next);
    } finally {
      release.countDown();
    }
  }

  @Test
  void oneConnectionPoolClearsTenantContextAndTransactionLocksAfterBothOutcomes() throws Exception {
    var configured = (com.zaxxer.hikari.HikariDataSource) runtime;
    try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
      pool.setJdbcUrl(configured.getJdbcUrl());
      pool.setUsername(configured.getUsername());
      pool.setPassword(configured.getPassword());
      pool.setMaximumPoolSize(1);
      pool.setMinimumIdle(1);
      long pid;
      try (var c = pool.getConnection()) {
        pid = ((Number) value(c, "select pg_backend_pid()")).longValue();
      }
      for (boolean rollback : List.of(false, true)) {
        try (var c = pool.getConnection()) {
          c.setAutoCommit(false);
          context(c, tenant, null);
          var key = UUID.randomUUID();
          f.db.execute(
              c,
              "insert into"
                  + " processed_client_operations(id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at)"
                  + " values(?,?,?,?,'TRAILER_HOOK','hash','PROCESSING',now())",
              UUID.randomUUID(),
              tenant,
              key,
              user);
          assertThat(((Number) value(c, "select pg_backend_pid()")).longValue()).isEqualTo(pid);
          if (rollback) c.rollback();
          else {
            f.db.execute(
                c,
                "update processed_client_operations set status='SUCCEEDED',processed_at=now()"
                    + " where tenant_id=? and client_operation_id=?",
                tenant,
                key);
            c.commit();
          }
        }
        try (var c = pool.getConnection()) {
          assertThat(((Number) value(c, "select pg_backend_pid()")).longValue()).isEqualTo(pid);
          assertThat(value(c, "select nullif(current_setting('app.current_tenant_id',true),'')"))
              .isNull();
          assertThat(value(c, "select nullif(current_setting('app.current_user_id',true),'')"))
              .isNull();
          assertThat(
                  value(
                      c,
                      "select count(*) from pg_locks where pid=pg_backend_pid() and"
                          + " locktype in ('transactionid','tuple')"))
              .isEqualTo(0L);
          for (var table :
              List.of(
                  "trailer_couplings", "trailer_movement_events", "processed_client_operations"))
            assertThat(rows(c, table)).isZero();
        }
        assertNoProcessing();
      }
    }
  }
}
