package com.trailersaas.trailers.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrailerAdministrativeHttpPostgresTests extends PostgresIntegrationTest {
  static final String TOKEN = "synthetic-trailer-command-bearer";
  static final List<String> COMMANDS =
      List.of(
          "location-corrections",
          "mark-location-unknown",
          "load-state-changes",
          "out-of-service",
          "return-to-service",
          "customer-designation-changes");
  static final Instant SEEN = Instant.parse("2026-10-01T12:00:00Z");
  @Autowired Flyway flyway;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired TrailerPlanningPolicy planning;
  @Autowired TrailerProjectionReconciliation reconciliation;
  @Autowired TrailerMovementProjection movement;
  @MockitoBean FirebaseTokenVerifier verifier;

  @Value("${local.server.port}")
  int port;

  final JsonMapper json = JsonMapper.builder().build();
  TrailerFixtures f;
  UUID tenant, user, membership, trailer, a, b, customerA, customerB, driver;

  @BeforeEach
  void seed() throws Exception {
    f = new TrailerFixtures(flyway);
    tenant = f.db.tenant();
    user = f.db.user();
    membership = f.db.membership(tenant, user, "ACTIVE", "TENANT_ADMIN");
    customerA = f.customer(tenant);
    customerB = f.customer(tenant);
    driver = f.db.driver(tenant, user);
    a = f.location(tenant);
    b = f.location(tenant);
    trailer = f.trailer(tenant, "Commands-" + UUID.randomUUID(), customerA);
    f.db.execute(
        "update trailers set"
            + " location_state='AT_LOCATION',current_location_id=?,last_known_location_id=?,last_seen_at=?,last_driver_id=?"
            + " where id=?",
        a,
        a,
        java.sql.Timestamp.from(SEEN),
        driver,
        trailer);
    when(verifier.verify(TOKEN))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
  }

  Map<String, Object> input(String command, long version) {
    return switch (command) {
      case "location-corrections" ->
          Map.of("expectedVersion", version, "newLocationId", b, "reason", "Confirmed location");
      case "mark-location-unknown" ->
          Map.of("expectedVersion", version, "reason", "Unable to locate");
      case "load-state-changes" ->
          Map.of("expectedVersion", version, "loadState", "LOADED", "reason", "Confirmed load");
      case "out-of-service" -> Map.of("expectedVersion", version, "comment", "Rear door damage");
      case "return-to-service" -> Map.of("expectedVersion", version, "reason", "Repairs inspected");
      default ->
          Map.of(
              "expectedVersion",
              version,
              "newCustomerId",
              customerB,
              "reason",
              "Administrative reassignment");
    };
  }

  static Stream<Arguments> gates() {
    return COMMANDS.stream()
        .flatMap(
            command ->
                Stream.of(
                        "TENANT_ADMIN",
                        "TRANSPORT_OPERATIONS",
                        "CUSTOMER_DISPATCHER",
                        "YARD_SPOTTER",
                        "DRIVER")
                    .map(role -> Arguments.of(command, role)));
  }

  void roles(String role) throws Exception {
    f.db.execute("delete from role_assignments where membership_id=?", membership);
    f.role(tenant, membership, role, "CUSTOMER_DISPATCHER".equals(role) ? customerA : null);
  }

  void prepare(String command) throws Exception {
    if (command.equals("return-to-service"))
      f.db.execute("update trailers set serviceability_state='OUT_OF_SERVICE' where id=?", trailer);
  }

  @ParameterizedTest
  @MethodSource("gates")
  void freshCapabilityGatesPreserveTheExistingConservativeMapping(String command, String role)
      throws Exception {
    prepare(command);
    roles(role);
    boolean allowed =
        role.equals("TENANT_ADMIN")
            || (role.equals("YARD_SPOTTER")
                && List.of("location-corrections", "mark-location-unknown", "load-state-changes")
                    .contains(command));
    var response = command(command, input(command, 0));
    if (allowed) {
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      assertThat(body(response))
          .containsEntry("version", 1)
          .doesNotContainKeys("tenantId", "operationalNumberNormalized");
      assertThat(count()).isEqualTo(1);
    } else {
      problem(response, 403, "CAPABILITY_DENIED");
      assertThat(count()).isZero();
      assertThat(version()).isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "location-corrections",
        "mark-location-unknown",
        "load-state-changes",
        "out-of-service",
        "return-to-service",
        "customer-designation-changes"
      })
  void strictHttpContractsRejectActorSourceTimeAliasesAndEvidence(String command) throws Exception {
    prepare(command);
    for (String key :
        List.of(
            "actorType",
            "sourceType",
            "actorUserId",
            "occurredAt",
            "driverId",
            "tenantId",
            "documentIds",
            "observedLocationId")) {
      var payload = new HashMap<>(input(command, 0));
      payload.put(key, "untrusted");
      problem(command(command, payload), 400, "VALIDATION_FAILED");
    }
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "location-corrections",
        "mark-location-unknown",
        "load-state-changes",
        "out-of-service",
        "return-to-service",
        "customer-designation-changes"
      })
  void allCommandsAuthenticateRequireActiveMembershipAndRejectStale(String command)
      throws Exception {
    prepare(command);
    problem(
        http("POST", tenant, trailer, command, input(command, 0), null),
        401,
        "AUTHENTICATION_REQUIRED");
    problem(command(command, input(command, 1)), 409, "STALE_VERSION");
    f.db.execute("update tenant_memberships set status='SUSPENDED' where id=?", membership);
    problem(command(command, input(command, 0)), 403, "TENANT_ACCESS_DENIED");
    f.db.execute("update tenant_memberships set status='ACTIVE' where id=?", membership);
    f.db.execute("delete from role_assignments where membership_id=?", membership);
    problem(command(command, input(command, 0)), 403, "CAPABILITY_DENIED");
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "location-corrections",
        "mark-location-unknown",
        "load-state-changes",
        "out-of-service",
        "return-to-service",
        "customer-designation-changes"
      })
  void missingBlankAndCoercedValuesAreValidationFailuresOverHttp(String command) throws Exception {
    prepare(command);
    var valid = input(command, 0);
    for (String field : valid.keySet()) {
      var missing = new HashMap<>(valid);
      missing.remove(field);
      problem(command(command, missing), 400, "VALIDATION_FAILED");
      var blank = new HashMap<>(valid);
      blank.put(field, " ");
      problem(command(command, blank), 400, "VALIDATION_FAILED");
    }
    for (Object version : List.of(-1, 0.5, "0", true)) {
      var badVersion = new HashMap<>(valid);
      badVersion.put("expectedVersion", version);
      problem(command(command, badVersion), 400, "VALIDATION_FAILED");
    }
    if (command.equals("load-state-changes")) {
      var invalid = new HashMap<>(valid);
      invalid.put("loadState", "LOADING");
      problem(command(command, invalid), 400, "VALIDATION_FAILED");
    }
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @Test
  void correctionAndUnknownPreserveHistoryAndLastPhysicalObservation() throws Exception {
    var from = Instant.now();
    var correction = body(success("location-corrections", input("location-corrections", 0)));
    assertThat(correction)
        .containsEntry("locationState", "AT_LOCATION")
        .containsEntry("currentLocationId", b.toString())
        .containsEntry("lastKnownLocationId", b.toString())
        .containsEntry("lastDriverId", driver.toString())
        .containsEntry("version", 1);
    assertThat(Instant.parse((String) correction.get("lastSeenAt")))
        .isBetween(from.minusSeconds(1), Instant.now().plusSeconds(1));
    var originalEvent = event(0);
    var unknown = body(success("mark-location-unknown", input("mark-location-unknown", 1)));
    assertThat(unknown)
        .containsEntry("locationState", "LOCATION_UNKNOWN")
        .containsEntry("currentLocationId", null)
        .containsEntry("lastKnownLocationId", b.toString())
        .containsEntry("lastSeenAt", correction.get("lastSeenAt"))
        .containsEntry("lastDriverId", driver.toString())
        .containsEntry("version", 2);
    assertThat(event(0)).isEqualTo(originalEvent);
    assertFact(event(0), "LOCATION_CORRECTED", "Confirmed location");
    assertFact(event(1), "LOCATION_MARKED_UNKNOWN", "Unable to locate");
    problem(
        command("mark-location-unknown", input("mark-location-unknown", 2)),
        409,
        "INVALID_STATE_TRANSITION");
    assertThat(count()).isEqualTo(2);
  }

  @Test
  void markUnknownFromTransitPreservesAllLastKnownFields() throws Exception {
    f.db.execute(
        "update trailers set location_state='IN_TRANSIT',current_location_id=null where id=?",
        trailer);
    var after = body(success("mark-location-unknown", input("mark-location-unknown", 0)));
    assertThat(after)
        .containsEntry("currentLocationId", null)
        .containsEntry("lastKnownLocationId", a.toString())
        .containsEntry("lastSeenAt", SEEN.toString())
        .containsEntry("lastDriverId", driver.toString())
        .containsEntry("loadState", "UNKNOWN")
        .containsEntry("serviceabilityState", "IN_SERVICE")
        .containsEntry("designatedCustomerId", customerA.toString());
  }

  static Stream<Arguments> loads() {
    return Stream.of("EMPTY", "LOADED", "UNKNOWN")
        .flatMap(
            from ->
                Stream.of("EMPTY", "LOADED", "UNKNOWN")
                    .filter(to -> !from.equals(to))
                    .map(to -> Arguments.of(from, to)));
  }

  @ParameterizedTest
  @MethodSource("loads")
  void sixDistinctLoadTransitionsAreIndependentAndDurable(String from, String to) throws Exception {
    f.db.execute("update trailers set load_state=? where id=?", from, trailer);
    var before = body(get());
    var payload = Map.of("expectedVersion", 0, "loadState", to, "reason", "Confirmed contents");
    var after = body(success("load-state-changes", payload));
    assertIndependent(before, after, Set.of("loadState", "version", "updatedAt"));
    assertThat(after).containsEntry("loadState", to).containsEntry("version", 1);
    assertThat(metadata(event(0)))
        .containsEntry("previousLoadState", from)
        .containsEntry("newLoadState", to)
        .hasSize(2);
    assertFact(event(0), "LOAD_STATE_CHANGED", "Confirmed contents");
    problem(
        command(
            "load-state-changes",
            Map.of("expectedVersion", 1, "loadState", to, "reason", "Same state")),
        409,
        "RESOURCE_CONFLICT");
    assertThat(count()).isEqualTo(1);
    assertThat(version()).isEqualTo(1);
  }

  @Test
  void oosAndReturnUseIndependentServiceabilityFactsAndPlanningEligibility() throws Exception {
    var before = body(get());
    var actor = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
    assertThat(transactions.execute(tenant, () -> planning.check(actor, tenant, trailer)))
        .isEqualTo(TrailerPlanningPolicy.Eligibility.ELIGIBLE);
    var out = body(success("out-of-service", input("out-of-service", 0)));
    assertIndependent(before, out, Set.of("serviceabilityState", "version", "updatedAt"));
    assertThat(out)
        .containsEntry("serviceabilityState", "OUT_OF_SERVICE")
        .containsEntry("version", 1);
    assertFact(event(0), "MARKED_OOS", "Rear door damage");
    assertThat(metadata(event(0)))
        .containsEntry("previousServiceabilityState", "IN_SERVICE")
        .containsEntry("newServiceabilityState", "OUT_OF_SERVICE")
        .hasSize(2);
    assertThat(transactions.execute(tenant, () -> planning.check(actor, tenant, trailer)))
        .isEqualTo(TrailerPlanningPolicy.Eligibility.UNAVAILABLE);
    problem(command("out-of-service", input("out-of-service", 1)), 409, "INVALID_STATE_TRANSITION");
    var back = body(success("return-to-service", input("return-to-service", 1)));
    assertIndependent(before, back, Set.of("version", "updatedAt"));
    assertFact(event(1), "RETURNED_TO_SERVICE", "Repairs inspected");
    assertThat(metadata(event(1)))
        .containsEntry("previousServiceabilityState", "OUT_OF_SERVICE")
        .containsEntry("newServiceabilityState", "IN_SERVICE")
        .hasSize(2);
    assertThat(transactions.execute(tenant, () -> planning.check(actor, tenant, trailer)))
        .isEqualTo(TrailerPlanningPolicy.Eligibility.ELIGIBLE);
    problem(
        command("return-to-service", input("return-to-service", 2)),
        409,
        "INVALID_STATE_TRANSITION");
    f.db.execute("update trailers set active=false where id=?", trailer);
    assertThat(transactions.execute(tenant, () -> planning.check(actor, tenant, trailer)))
        .isEqualTo(TrailerPlanningPolicy.Eligibility.UNAVAILABLE);
  }

  @Test
  void customerDesignationIsTenantSafeIndependentAndImmediatelyChangesReadScope() throws Exception {
    var before = body(get());
    f.db.execute("update customers set status='INACTIVE' where id=?", customerB);
    var after =
        body(success("customer-designation-changes", input("customer-designation-changes", 0)));
    assertIndependent(before, after, Set.of("designatedCustomerId", "version", "updatedAt"));
    assertThat(after)
        .containsEntry("designatedCustomerId", customerB.toString())
        .containsEntry("version", 1);
    assertFact(event(0), "DESIGNATED_CUSTOMER_CHANGED", "Administrative reassignment");
    assertThat(metadata(event(0)))
        .containsEntry("previousDesignatedCustomerId", customerA.toString())
        .containsEntry("newDesignatedCustomerId", customerB.toString())
        .hasSize(2);
    problem(
        command("customer-designation-changes", input("customer-designation-changes", 1)),
        409,
        "RESOURCE_CONFLICT");
    roles("CUSTOMER_DISPATCHER");
    problem(get(), 404, "RESOURCE_NOT_FOUND");
    f.db.execute(
        "update role_assignments set customer_id=? where membership_id=?", customerB, membership);
    assertThat(get().statusCode()).isEqualTo(200);
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void initiallyUnassignedDesignationRecordsKnownNull() throws Exception {
    f.db.execute("update trailers set designated_customer_id=null where id=?", trailer);
    success("customer-designation-changes", input("customer-designation-changes", 0));
    assertThat(metadata(event(0))).containsEntry("previousDesignatedCustomerId", null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"location-corrections", "customer-designation-changes"})
  void missingOrForeignParentsAreGenericMissingAndNeverWrite(String command) throws Exception {
    String key = command.equals("location-corrections") ? "newLocationId" : "newCustomerId";
    var other = f.db.tenant();
    var foreign = command.equals("location-corrections") ? f.location(other) : f.customer(other);
    for (UUID target : List.of(foreign, UUID.randomUUID())) {
      var payload = new HashMap<>(input(command, 0));
      payload.put(key, target);
      problem(command(command, payload), 404, "RESOURCE_NOT_FOUND");
    }
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @Test
  void genericPatchCannotBypassExplicitCommands() throws Exception {
    for (String field :
        List.of(
            "designatedCustomerId",
            "serviceabilityState",
            "loadState",
            "locationState",
            "currentLocationId")) {
      problem(
          http(
              "PATCH",
              tenant,
              trailer,
              "",
              Map.of("expectedVersion", 0, field, "untrusted"),
              TOKEN),
          400,
          "VALIDATION_FAILED");
    }
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @Test
  void actualAdministrativeFactsDoNotDestroyMovementReconciliation() throws Exception {
    // Establish a complete physical baseline, including the observed Driver, through #39.
    f.db.execute(
        "update trailers set location_state='IN_TRANSIT',current_location_id=null where id=?",
        trailer);
    transactions.execute(
        tenant,
        () ->
            movement.arrive(
                tenant,
                new TrailerMovementProjection.Arrival(
                    new TrailerMovementProjection.Target(trailer, 0),
                    new TrailerMovementProjection.Context(
                        SEEN,
                        new TrailerMovementProjection.Provenance(
                            TrailerMovementProjection.ActorType.HUMAN_USER,
                            TrailerMovementProjection.SourceType.USER_ACTION,
                            user),
                        driver,
                        null,
                        null,
                        null,
                        null),
                    a,
                    TrailerMovementProjection.ArrivalSource.MANUAL)));
    success("location-corrections", input("location-corrections", 1));
    success("load-state-changes", input("load-state-changes", 2));
    assertThat(transactions.execute(tenant, () -> reconciliation.compare(tenant, trailer)).status())
        .isEqualTo(TrailerProjectionReconciliation.Status.MATCH);
    success("out-of-service", input("out-of-service", 3));
    success("return-to-service", input("return-to-service", 4));
    success("customer-designation-changes", input("customer-designation-changes", 5));
    var comparison = transactions.execute(tenant, () -> reconciliation.compare(tenant, trailer));
    assertThat(comparison.status()).isEqualTo(TrailerProjectionReconciliation.Status.MATCH);
    assertThat(comparison.derivedFields())
        .containsExactlyInAnyOrder(TrailerProjectionReconciliation.Field.values());
    assertThat(count()).isEqualTo(6);
    assertThat(version()).isEqualTo(6);
  }

  @ParameterizedTest
  @CsvSource({
    "location-corrections,trailer_movement_events,INSERT,P0001",
    "out-of-service,trailer_movement_events,INSERT,P0001",
    "out-of-service,trailers,UPDATE,P0001",
    "customer-designation-changes,trailer_movement_events,INSERT,P0001",
    "customer-designation-changes,trailers,UPDATE,P0001",
    "location-corrections,trailer_movement_events,INSERT,23514",
    "out-of-service,trailer_movement_events,INSERT,23514",
    "out-of-service,trailers,UPDATE,23514",
    "customer-designation-changes,trailer_movement_events,INSERT,23514",
    "customer-designation-changes,trailers,UPDATE,23514"
  })
  void eventAndProjectionFailuresRollBackTogether(
      String command, String table, String operation, String sqlState) throws Exception {
    var before = f.db.value("select to_jsonb(t)::text from trailers t where id=?", trailer);
    var name = "test_command_" + UUID.randomUUID().toString().replace("-", "");
    f.db.execute(
        "create function "
            + name
            + "() returns trigger language plpgsql as $$ begin raise exception using"
            + " errcode='"
            + sqlState
            + "',message='Controlled command failure'; end; $$");
    var column = table.equals("trailers") ? "id" : "trailer_id";
    try {
      f.db.execute(
          "create trigger "
              + name
              + " before "
              + operation
              + " on "
              + table
              + " for each row when (new."
              + column
              + "='"
              + trailer
              + "'::uuid) execute function "
              + name
              + "()");
      var response = command(command, input(command, 0));
      if (sqlState.equals("23514")) {
        problem(response, 409, "RESOURCE_CONFLICT");
      } else {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(500);
        assertThat(body(response))
            .containsEntry("detail", "The server could not complete the request.")
            .containsKey("traceId");
      }
      assertThat(response.body())
          .doesNotContain("Controlled command failure", "P0001", name, TOKEN);
      assertThat(f.db.value("select to_jsonb(t)::text from trailers t where id=?", trailer))
          .isEqualTo(before);
      assertThat(count()).isZero();
      assertThat(version()).isZero();
    } finally {
      f.db.execute("drop trigger if exists " + name + " on " + table);
      f.db.execute("drop function " + name + "()");
    }
  }

  @Test
  void tenantIsolationAndUnknownParametersKeepExistingRlsBoundaries() throws Exception {
    var other = f.db.tenant();
    var foreign = f.trailer(other, "foreign", null);
    for (String command : COMMANDS) {
      problem(
          http("POST", tenant, foreign, command, input(command, 0), TOKEN),
          404,
          "RESOURCE_NOT_FOUND");
      problem(
          http("POST", tenant, trailer, command + "?unexpected=true", input(command, 0), TOKEN),
          400,
          "VALIDATION_FAILED");
    }
    assertThat(f.db.value("select version from trailers where id=?", foreign)).isEqualTo(0L);
  }

  HttpResponse<String> command(String name, Object payload) throws Exception {
    return http("POST", tenant, trailer, name, payload, TOKEN);
  }

  HttpResponse<String> success(String name, Object payload) throws Exception {
    var r = command(name, payload);
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    return r;
  }

  HttpResponse<String> get() throws Exception {
    return http("GET", tenant, trailer, "", null, TOKEN);
  }

  HttpResponse<String> http(
      String method, UUID tenantId, UUID id, String suffix, Object payload, String token)
      throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:"
                          + port
                          + "/api/v1/tenants/"
                          + tenantId
                          + "/trailers/"
                          + id
                          + (suffix.isEmpty() ? "" : "/" + suffix)))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .header("X-Request-Id", "trailer-command-test")
              .method(
                  method,
                  payload == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
      if (token != null) request.header("Authorization", "Bearer " + token);
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  Map<String, Object> body(HttpResponse<String> response) {
    return JsonPath.read(response.body(), "$");
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
    assertThat(body(r)).containsEntry("code", code).containsKey("traceId");
    assertThat(r.headers().firstValue("X-Request-Id")).contains("trailer-command-test");
    assertThat(r.body()).doesNotContain(TOKEN, "constraint", "SQL");
  }

  void assertIndependent(
      Map<String, Object> before, Map<String, Object> after, Set<String> changed) {
    var commandFields =
        Arrays.stream(TrailerDtos.Trailer.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());
    // GET now adds read-only summaries. Every original command/projection field remains checked.
    before.forEach(
        (key, value) -> {
          if (commandFields.contains(key) && !changed.contains(key))
            assertThat(after).containsEntry(key, value);
        });
  }

  long count() throws Exception {
    return ((Number)
            f.db.value("select count(*) from trailer_movement_events where trailer_id=?", trailer))
        .longValue();
  }

  long version() throws Exception {
    return ((Number) f.db.value("select version from trailers where id=?", trailer)).longValue();
  }

  Map<String, Object> event(int offset) throws Exception {
    return JsonPath.read(
        (String)
            f.db.value(
                "select to_jsonb(e)::text from trailer_movement_events e where trailer_id=? order"
                    + " by recorded_at,id offset ? limit 1",
                trailer,
                offset),
        "$");
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> metadata(Map<String, Object> e) {
    return (Map<String, Object>) e.get("metadata");
  }

  void assertFact(Map<String, Object> e, String type, String reason) {
    assertThat(e)
        .containsEntry("event_type", type)
        .containsEntry("actor_type", "HUMAN_USER")
        .containsEntry("source_type", "USER_ACTION")
        .containsEntry("actor_user_id", user.toString())
        .containsEntry("reason", reason)
        .containsEntry("client_operation_id", null)
        .containsEntry("trip_id", null)
        .containsEntry("tractor_id", null)
        .containsEntry("driver_id", null);
    assertThat(OffsetDateTime.parse((String) e.get("occurred_at")).toInstant()).isAfter(SEEN);
    assertThat(OffsetDateTime.parse((String) e.get("recorded_at")).toInstant()).isAfter(SEEN);
  }
}
