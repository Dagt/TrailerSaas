package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerMovementProjection.*;
import static com.trailersaas.trailers.TrailerProjectionReconciliation.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.trailersaas.*;
import com.trailersaas.identity.TenantTransactionExecutor;
import com.trailersaas.trailers.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class TrailerMovementPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired TrailerMovementProjection movements;
  @Autowired TrailerProjectionReconciliation reconciliation;
  @Autowired TrailerCouplingTransactions coupling;
  @Autowired EntityManager em;
  @Autowired JsonMapper json;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  TrailerFixtures f;
  UUID tenant, other, user, driver, trailer, a, b, c;
  static final Instant AT = Instant.parse("2026-10-01T12:00:00Z"), BEFORE = AT.minusSeconds(600);

  @BeforeEach
  void seed() throws Exception {
    f = new TrailerFixtures(flyway);
    tenant = f.db.tenant();
    other = f.db.tenant();
    user = f.db.user();
    driver = f.db.driver(tenant, user);
    a = f.location(tenant);
    b = f.location(tenant);
    c = f.location(tenant);
    trailer = f.trailer(tenant, "Movement-" + UUID.randomUUID(), null);
  }

  Context context() {
    return context(AT, driver, new Provenance(ActorType.HUMAN_USER, SourceType.USER_ACTION, user));
  }

  Context context(Instant at, UUID d, Provenance p) {
    return new Context(at, p, d, null, null, null, null);
  }

  Target target(long v) {
    return new Target(trailer, v);
  }

  <T> T tx(Supplier<T> work) {
    return transactions.execute(tenant, work);
  }

  void baseline(LocationState state, LoadState load) throws Exception {
    f.db.execute(
        "update trailers set"
            + " location_state=?,current_location_id=?,last_known_location_id=?,last_seen_at=?,last_driver_id=?,load_state=?"
            + " where id=?",
        state.name(),
        state == LocationState.AT_LOCATION ? a : null,
        a,
        java.sql.Timestamp.from(BEFORE),
        driver,
        load.name(),
        trailer);
  }

  long count() throws Exception {
    return ((Number)
            f.db.value("select count(*) from trailer_movement_events where trailer_id=?", trailer))
        .longValue();
  }

  long version() throws Exception {
    return ((Number) f.db.value("select version from trailers where id=?", trailer)).longValue();
  }

  static Stream<Arguments> matrix() {
    return MovementRulesTests.matrix();
  }

  Result apply(String op, long v) {
    return switch (op) {
      case "DEPART" -> movements.depart(tenant, new Departure(target(v), context()));
      case "ARRIVE" ->
          movements.arrive(tenant, new Arrival(target(v), context(), b, ArrivalSource.MANUAL));
      case "YARD" -> movements.yardMove(tenant, new YardMove(target(v), context(), a, b));
      case "CORRECT" ->
          movements.correctLocation(
              tenant, new Correction(target(v), context(), b, "Confirmed correction", false));
      case "UNKNOWN" ->
          movements.markLocationUnknown(
              tenant, new MarkUnknown(target(v), context(), "Unable to locate"));
      default -> movements.recoverLocation(tenant, new Recovery(target(v), context(), b));
    };
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void persistedLocationMatrixIsAtomicAndPreservesIndependentFields(LocationState source, String op)
      throws Exception {
    baseline(source, LoadState.LOADED);
    boolean valid =
        switch (op) {
          case "DEPART", "YARD" -> source == LocationState.AT_LOCATION;
          case "ARRIVE" -> source == LocationState.IN_TRANSIT;
          case "UNKNOWN" -> source != LocationState.LOCATION_UNKNOWN;
          case "RECOVER" -> source == LocationState.LOCATION_UNKNOWN;
          default -> true;
        };
    if (!valid) {
      assertThatThrownBy(() -> tx(() -> apply(op, 0)))
          .isInstanceOf(TrailerMovementRejectedException.class)
          .hasFieldOrPropertyWithValue(
              "reason", TrailerMovementRejectedException.Reason.INVALID_STATE_TRANSITION);
      assertThat(count()).isZero();
      assertThat(version()).isZero();
      return;
    }
    var before = f.db.value("select updated_at from trailers where id=?", trailer);
    var r = tx(() -> apply(op, 0));
    assertThat(r.version()).isEqualTo(1);
    assertThat(count()).isEqualTo(1);
    assertThat(r.loadState()).isEqualTo(LoadState.LOADED);
    assertThat(r.serviceabilityState()).isEqualTo("IN_SERVICE");
    assertThat(r.lastDriverId()).isEqualTo(driver);
    assertThat(r.lastKnownLocationId()).isEqualTo(Set.of("DEPART", "UNKNOWN").contains(op) ? a : b);
    assertThat(r.lastSeenAt()).isEqualTo(Set.of("CORRECT", "UNKNOWN").contains(op) ? BEFORE : AT);
    assertThat(r.currentLocationId())
        .isEqualTo(Set.of("DEPART", "UNKNOWN").contains(op) ? null : b);
    assertThat(f.db.value("select updated_at from trailers where id=?", trailer))
        .isNotEqualTo(before);
    assertThat(
            f.db.value(
                "select actor_type from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo("HUMAN_USER");
    assertThat(
            f.db.value(
                "select source_type from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo("USER_ACTION");
    assertThat(
            f.db.value(
                "select actor_user_id from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo(user);
    assertThat(
            f.db.value(
                "select location_id from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo(op.equals("DEPART") ? a : op.equals("UNKNOWN") ? null : b);
    assertThat(r.movementEvent().occurredAt()).isEqualTo(AT);
    assertThat(r.movementEvent().recordedAt()).isAfter(AT);
  }

  static Stream<Arguments> loads() {
    return MovementRulesTests.loads();
  }

  @ParameterizedTest
  @MethodSource("loads")
  void allLoadTransitionsAndSameStateConflict(LoadState from, LoadState to) throws Exception {
    baseline(LocationState.AT_LOCATION, from);
    if (from == to) {
      assertThatThrownBy(
              () ->
                  tx(
                      () ->
                          movements.changeLoadState(
                              tenant, new LoadChange(target(0), context(), to, "Load verified"))))
          .isInstanceOf(TrailerMovementRejectedException.class);
      assertThat(count()).isZero();
      return;
    }
    var r =
        tx(
            () ->
                movements.changeLoadState(
                    tenant, new LoadChange(target(0), context(), to, "Load verified")));
    assertThat(r.version()).isEqualTo(1);
    assertThat(r.loadState()).isEqualTo(to);
    assertThat(r.locationState()).isEqualTo(LocationState.AT_LOCATION);
    assertThat(r.currentLocationId()).isEqualTo(a);
    assertThat(r.lastSeenAt()).isEqualTo(BEFORE);
    assertThat(r.lastDriverId()).isEqualTo(driver);
    var m =
        json.readValue(
            f.db
                .value(
                    "select metadata::text from trailer_movement_events where trailer_id=?",
                    trailer)
                .toString(),
            Map.class);
    assertThat(m)
        .containsEntry("previousLoadState", from.name())
        .containsEntry("newLoadState", to.name())
        .hasSize(2);
  }

  @Test
  void sequenceReconcilesAndReportsDriftWithoutAutoRepair() throws Exception {
    baseline(LocationState.AT_LOCATION, LoadState.UNKNOWN);
    tx(() -> movements.depart(tenant, new Departure(target(0), context())));
    tx(
        () ->
            movements.arrive(
                tenant,
                new Arrival(
                    target(1),
                    context(AT.minusSeconds(300), driver, context().provenance()),
                    b,
                    ArrivalSource.MANUAL)));
    tx(() -> movements.yardMove(tenant, new YardMove(target(2), context(), b, c)));
    tx(
        () ->
            movements.changeLoadState(
                tenant, new LoadChange(target(3), context(), LoadState.LOADED, "Load confirmed")));
    assertThat(tx(() -> reconciliation.compare(tenant, trailer)).status()).isEqualTo(Status.MATCH);
    f.db.execute("update trailers set current_location_id=? where id=?", a, trailer);
    var r = tx(() -> reconciliation.compare(tenant, trailer));
    assertThat(r.status()).isEqualTo(Status.DRIFT);
    assertThat(r.driftFields()).containsExactly(Field.CURRENT_LOCATION);
    assertThat(f.db.value("select current_location_id from trailers where id=?", trailer))
        .isEqualTo(a);
    assertThat(count()).isEqualTo(4);
  }

  @Test
  void noEventsCannotRebuildBaseline() {
    assertThat(tx(() -> reconciliation.compare(tenant, trailer)).status())
        .isEqualTo(Status.INSUFFICIENT_HISTORY);
  }

  @Test
  void comparisonRefreshesAPreviouslyManagedProjectionUnderSharedLock() throws Exception {
    tx(() -> apply("RECOVER", 0));
    tx(
        () ->
            movements.changeLoadState(
                tenant, new LoadChange(target(1), context(), LoadState.LOADED, "Confirmed")));
    var result =
        tx(
            () -> {
              em.find(
                  TrailerEntity.class,
                  trailer); // An earlier unprotected caller read is not authoritative.
              try {
                f.db.execute("update trailers set current_location_id=? where id=?", a, trailer);
              } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
              }
              return reconciliation.compare(tenant, trailer);
            });
    assertThat(result.status()).isEqualTo(Status.DRIFT);
    assertThat(result.driftFields()).containsExactly(Field.CURRENT_LOCATION);
  }

  @Test
  void staleOfflineOccurrenceCannotAppendOrOverwrite() throws Exception {
    baseline(LocationState.IN_TRANSIT, LoadState.UNKNOWN);
    tx(() -> apply("ARRIVE", 0));
    var old = context(AT.minusSeconds(1000), driver, context().provenance());
    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        movements.correctLocation(
                            tenant, new Correction(target(0), old, a, "Late observation", true))))
        .isInstanceOf(TrailerMovementRejectedException.class)
        .hasFieldOrPropertyWithValue(
            "reason", TrailerMovementRejectedException.Reason.STALE_VERSION);
    assertThat(version()).isEqualTo(1);
    assertThat(count()).isEqualTo(1);
    assertThat(f.db.value("select current_location_id from trailers where id=?", trailer))
        .isEqualTo(b);
  }

  @Test
  void markUnknownPreservesObservationAndRecoveryWithoutDriverKeepsPriorDriver() throws Exception {
    baseline(LocationState.AT_LOCATION, LoadState.EMPTY);
    tx(() -> apply("UNKNOWN", 0));
    var none = context(AT, null, context().provenance());
    var r = tx(() -> movements.recoverLocation(tenant, new Recovery(target(1), none, b)));
    assertThat(r.lastDriverId()).isEqualTo(driver);
    assertThat(r.lastSeenAt()).isEqualTo(AT);
    assertThat(r.lastKnownLocationId()).isEqualTo(b);
    assertThat(r.loadState()).isEqualTo(LoadState.EMPTY);
    var m =
        json.readValue(
            f.db
                .value(
                    "select metadata::text from trailer_movement_events where trailer_id=? and"
                        + " event_type='LOCATION_MARKED_UNKNOWN'",
                    trailer)
                .toString(),
            Map.class);
    assertThat(m)
        .containsEntry("preservedLastKnownLocationId", a.toString())
        .containsEntry("preservedLastDriverId", driver.toString())
        .containsEntry("preservedLastSeenAt", BEFORE.toString());
  }

  @Test
  void correctionObservationFlagAndReasonAreDurable() throws Exception {
    baseline(LocationState.IN_TRANSIT, LoadState.UNKNOWN);
    var r =
        tx(
            () ->
                movements.correctLocation(
                    tenant,
                    new Correction(target(0), context(), b, "Physical confirmation", true)));
    assertThat(r.lastSeenAt()).isEqualTo(AT);
    assertThat(
            f.db.value(
                "select reason from trailer_movement_events where id=?", r.movementEvent().id()))
        .isEqualTo("Physical confirmation");
    var m =
        json.readValue(
            f.db
                .value(
                    "select metadata::text from trailer_movement_events where id=?",
                    r.movementEvent().id())
                .toString(),
            Map.class);
    assertThat(m)
        .containsEntry("previousLocationState", "IN_TRANSIT")
        .containsEntry("previousLocationId", null)
        .containsEntry("previousLastKnownLocationId", a.toString())
        .containsEntry("physicalObservation", true);
  }

  static Stream<Arguments> actors() {
    return Stream.of(
        Arguments.of(ActorType.HUMAN_USER, SourceType.MOBILE_AUTOMATION),
        Arguments.of(ActorType.SYSTEM, SourceType.SYSTEM_PROCESS),
        Arguments.of(ActorType.INTEGRATION, SourceType.EXTERNAL_INTEGRATION));
  }

  @ParameterizedTest
  @MethodSource("actors")
  void provenanceUsesRealHumanUserOrNoFabricatedUser(ActorType actor, SourceType source)
      throws Exception {
    baseline(LocationState.IN_TRANSIT, LoadState.UNKNOWN);
    var key = UUID.randomUUID();
    var p = new Provenance(actor, source, actor == ActorType.HUMAN_USER ? user : null);
    var context = new Context(AT, p, null, null, null, null, key);
    var r =
        tx(
            () ->
                movements.arrive(
                    tenant,
                    new Arrival(
                        target(0),
                        context,
                        b,
                        actor == ActorType.HUMAN_USER ? ArrivalSource.GEOFENCE_CONFIRMED : null)));
    assertThat(
            f.db.value(
                "select actor_type from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo(actor.name());
    assertThat(
            f.db.value(
                "select source_type from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo(source.name());
    assertThat(
            f.db.value(
                "select actor_user_id from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo(p.actorUserId());
    assertThat(
            f.db.value(
                "select client_operation_id from trailer_movement_events where id=?",
                r.movementEvent().id()))
        .isEqualTo(key);
    assertThat(
            f.db.value(
                "select count(*) from processed_client_operations where client_operation_id=?",
                key))
        .isEqualTo(0L);
  }

  @Test
  void nonexistentHumanActorRollsBackRatherThanFabricatingIdentity() throws Exception {
    var ctx =
        context(
            AT,
            driver,
            new Provenance(ActorType.HUMAN_USER, SourceType.USER_ACTION, UUID.randomUUID()));
    assertThatThrownBy(
            () -> tx(() -> movements.recoverLocation(tenant, new Recovery(target(0), ctx, b))))
        .isInstanceOf(TrailerMovementRejectedException.class)
        .hasFieldOrPropertyWithValue(
            "reason", TrailerMovementRejectedException.Reason.RESOURCE_CONFLICT);
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @Test
  void foreignAndMissingLocationsNeverBecomePhysicalProjection() throws Exception {
    var foreign = f.location(other);
    for (var location : List.of(foreign, UUID.randomUUID()))
      assertThatThrownBy(
              () ->
                  tx(
                      () ->
                          movements.recoverLocation(
                              tenant, new Recovery(target(0), context(), location))))
          .isInstanceOf(TrailerMovementRejectedException.class)
          .hasFieldOrPropertyWithValue(
              "reason", TrailerMovementRejectedException.Reason.RESOURCE_NOT_FOUND);
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @Test
  void customerSpecificLocationStillRequiresRealParentButCallerOwnsCompatibility()
      throws Exception {
    var customer = f.customer(tenant);
    f.db.execute(
        "insert into customer_sites values(?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        customer,
        f.db.value("select site_id from operational_locations where id=?", b));
    f.db.execute("update operational_locations set customer_id=? where id=?", customer, b);
    assertThat(
            tx(() -> movements.recoverLocation(tenant, new Recovery(target(0), context(), b)))
                .currentLocationId())
        .isEqualTo(b);
  }

  @Test
  void runtimeCannotRewriteFactsAndNoContextCannotReadThem() throws Exception {
    tx(() -> apply("RECOVER", 0));
    try (var conn = runtime.getConnection()) {
      assertThatThrownBy(
              () ->
                  f.db.execute(
                      conn,
                      "update trailer_movement_events set event_type='DROPPED' where trailer_id=?",
                      trailer))
          .isInstanceOf(java.sql.SQLException.class);
      assertThatThrownBy(
              () ->
                  f.db.execute(
                      conn, "delete from trailer_movement_events where trailer_id=?", trailer))
          .isInstanceOf(java.sql.SQLException.class);
      try (var q = conn.createStatement();
          var rows = q.executeQuery("select count(*) from trailer_movement_events")) {
        rows.next();
        assertThat(rows.getLong(1)).isZero();
      }
    }
    assertThatThrownBy(
            () ->
                transactions.execute(
                    other,
                    () ->
                        movements.recoverLocation(
                            other, new Recovery(target(1), context(), failingLocation()))))
        .isInstanceOf(TrailerMovementRejectedException.class)
        .hasFieldOrPropertyWithValue(
            "reason", TrailerMovementRejectedException.Reason.RESOURCE_NOT_FOUND);
  }

  UUID failingLocation() {
    return b;
  }

  @Test
  void absentAndBootstrapContextsCannotInvokeMovementOrReconciliation() throws Exception {
    var template =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                template.execute(
                    status ->
                        movements.recoverLocation(tenant, new Recovery(target(0), context(), b))))
        .isInstanceOf(TrailerMovementRejectedException.class)
        .hasFieldOrPropertyWithValue(
            "reason", TrailerMovementRejectedException.Reason.RESOURCE_NOT_FOUND);
    assertThatThrownBy(
            () ->
                template.execute(
                    status -> {
                      em.createNativeQuery("select set_config('app.current_user_id',:user,true)")
                          .setParameter("user", user.toString())
                          .getSingleResult();
                      return reconciliation.compare(tenant, trailer);
                    }))
        .isInstanceOf(TrailerMovementRejectedException.class)
        .hasFieldOrPropertyWithValue(
            "reason", TrailerMovementRejectedException.Reason.RESOURCE_NOT_FOUND);
    assertThat(count()).isZero();
    assertThat(version()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"trailer_movement_events", "trailers"})
  void appendAndProjectionBothRollBackOnEitherWriteFailure(String table) throws Exception {
    var name = "failure_" + UUID.randomUUID().toString().replace("-", "");
    f.db.execute(
        "create function "
            + name
            + "() returns trigger language plpgsql as $$ begin if "
            + (table.equals("trailers") ? "NEW.id" : "NEW.trailer_id")
            + "='"
            + trailer
            + "'::uuid then raise exception using errcode='08006',message='Controlled write"
            + " failure'; end if; return NEW; end $$");
    f.db.execute(
        "create trigger "
            + name
            + " before "
            + (table.equals("trailers") ? "update" : "insert")
            + " on "
            + table
            + " for each row execute function "
            + name
            + "()");
    try {
      assertThatThrownBy(() -> tx(() -> apply("RECOVER", 0)))
          .isNotInstanceOf(TrailerMovementRejectedException.class);
      assertThat(count()).isZero();
      assertThat(version()).isZero();
      assertThat(f.db.value("select location_state from trailers where id=?", trailer))
          .isEqualTo("LOCATION_UNKNOWN");
    } finally {
      f.db.execute("drop trigger " + name + " on " + table);
      f.db.execute("drop function " + name + "()");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "AT_LOCATION,DEPART,DEPART",
    "IN_TRANSIT,ARRIVE,ARRIVE",
    "AT_LOCATION,YARD,CORRECT",
    "AT_LOCATION,UNKNOWN,RECOVER"
  })
  void competingMovesWaitForTrailerLockAndLoseWithStaleVersion(
      LocationState initial, String first, String second) throws Exception {
    baseline(initial, LoadState.UNKNOWN);
    race(() -> apply(first, 0), () -> apply(second, 0));
    assertThat(count()).isEqualTo(1);
    assertThat(version()).isEqualTo(1);
  }

  @Test
  void competingLoadChangesDoNotLoseUpdates() throws Exception {
    race(
        () ->
            movements.changeLoadState(
                tenant, new LoadChange(target(0), context(), LoadState.EMPTY, "Confirmed empty")),
        () ->
            movements.changeLoadState(
                tenant,
                new LoadChange(target(0), context(), LoadState.LOADED, "Confirmed loaded")));
    assertThat(count()).isEqualTo(1);
    assertThat(version()).isEqualTo(1);
    assertThat(f.db.value("select load_state from trailers where id=?", trailer))
        .isEqualTo("EMPTY");
  }

  void race(Supplier<?> first, Supplier<?> second) throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var secondPid = new CompletableFuture<Integer>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () ->
                  tx(
                      () -> {
                        var value = first.get();
                        held.countDown();
                        awaitLatch(release);
                        return value;
                      }));
      try {
        assertThat(held.await(15, TimeUnit.SECONDS)).isTrue();
        var loser =
            pool.submit(
                () ->
                    tx(
                        () -> {
                          secondPid.complete(
                              (Integer)
                                  em.createNativeQuery("select pg_backend_pid()", Integer.class)
                                      .getSingleResult());
                          return second.get();
                        }));
        var pid = secondPid.get(15, TimeUnit.SECONDS);
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        f.db.value(
                            "select wait_event_type='Lock' from pg_stat_activity where pid=?",
                            pid)));
        release.countDown();
        winner.get(15, TimeUnit.SECONDS);
        assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS))
            .hasRootCauseInstanceOf(TrailerMovementRejectedException.class)
            .rootCause()
            .hasFieldOrPropertyWithValue(
                "reason", TrailerMovementRejectedException.Reason.STALE_VERSION);
      } finally {
        release.countDown();
      }
    }
  }

  static void awaitLatch(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS))
        throw new IllegalStateException("Lock coordination timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  TrailerCouplingTransactions.TripContext physicalTrip() throws Exception {
    var tractor = f.db.tractor(tenant, "Coupling-" + UUID.randomUUID());
    var trip = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trips(id,tenant_id,status,current_driver_id,current_tractor_id,planned_initial_trailer_id,created_at,updated_at)"
            + " values(?,?,'ASSIGNED',?,?,?,now(),now())",
        trip,
        tenant,
        driver,
        tractor,
        trailer);
    return new TrailerCouplingTransactions.TripContext(
        trip, "ASSIGNED", 0, driver, tractor, trailer);
  }

  @Test
  void hookAndMovementUseSameTrailerSerializationAndHookStillPreservesLocation() throws Exception {
    baseline(LocationState.AT_LOCATION, LoadState.UNKNOWN);
    var trip = physicalTrip();
    var key = UUID.randomUUID();
    race(
        () ->
            coupling.hook(
                tenant,
                user,
                key,
                trip,
                driver,
                new TrailerCouplingTransactions.Hook(
                    trailer, trip.currentTractorId(), driver, AT, 0)),
        () -> movements.depart(tenant, new Departure(target(0), context())));
    assertThat(count()).isEqualTo(1);
    assertThat(version()).isEqualTo(1);
    assertThat(f.db.value("select location_state from trailers where id=?", trailer))
        .isEqualTo("AT_LOCATION");
    assertThat(f.db.value("select current_location_id from trailers where id=?", trailer))
        .isEqualTo(a);
    var replay =
        tx(
            () ->
                coupling.hook(
                    tenant,
                    user,
                    key,
                    trip,
                    driver,
                    new TrailerCouplingTransactions.Hook(
                        trailer, trip.currentTractorId(), driver, AT, 0)));
    assertThat(replay.trailer().version()).isEqualTo(1);
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void movementFirstMakesConcurrentHookStaleWithoutCouplingOrClaimCommit() throws Exception {
    baseline(LocationState.AT_LOCATION, LoadState.UNKNOWN);
    var trip = physicalTrip();
    var key = UUID.randomUUID();
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pid = new CompletableFuture<Integer>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  tx(
                      () -> {
                        var r = apply("DEPART", 0);
                        held.countDown();
                        awaitLatch(release);
                        return r;
                      }));
      try {
        assertThat(held.await(15, TimeUnit.SECONDS)).isTrue();
        var hook =
            pool.submit(
                () ->
                    tx(
                        () -> {
                          pid.complete(
                              (Integer)
                                  em.createNativeQuery("select pg_backend_pid()", Integer.class)
                                      .getSingleResult());
                          return coupling.hook(
                              tenant,
                              user,
                              key,
                              trip,
                              driver,
                              new TrailerCouplingTransactions.Hook(
                                  trailer, trip.currentTractorId(), driver, AT, 0));
                        }));
        var waiting = pid.get(15, TimeUnit.SECONDS);
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        f.db.value(
                            "select wait_event_type='Lock' from pg_stat_activity where pid=?",
                            waiting)));
        release.countDown();
        first.get(15, TimeUnit.SECONDS);
        assertThatThrownBy(() -> hook.get(15, TimeUnit.SECONDS))
            .hasRootCauseInstanceOf(CouplingCommandRejectedException.class)
            .rootCause()
            .hasFieldOrPropertyWithValue("reason", CouplingCommandRejectedException.Reason.STALE);
      } finally {
        release.countDown();
      }
    }
    assertThat(count()).isEqualTo(1);
    assertThat(version()).isEqualTo(1);
    assertThat(f.db.value("select count(*) from trailer_couplings where trailer_id=?", trailer))
        .isEqualTo(0L);
    assertThat(
            f.db.value(
                "select count(*) from processed_client_operations where client_operation_id=?",
                key))
        .isEqualTo(0L);
  }

  @Test
  void dropSerializesAfterMovementAndPreservesExplicitLoadChange() throws Exception {
    baseline(LocationState.AT_LOCATION, LoadState.UNKNOWN);
    var trip = physicalTrip();
    tx(
        () ->
            coupling.hook(
                tenant,
                user,
                UUID.randomUUID(),
                trip,
                driver,
                new TrailerCouplingTransactions.Hook(
                    trailer, trip.currentTractorId(), driver, AT, 0)));
    var stop = UUID.randomUUID();
    var site = f.db.value("select site_id from operational_locations where id=?", b);
    f.db.execute(
        "insert into"
            + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,site_id,status,created_at,updated_at)"
            + " values(?,?,?,1,'DROP_HOOK',?,'PENDING',now(),now())",
        stop,
        tenant,
        trip.id(),
        site);
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pid = new CompletableFuture<Integer>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  tx(
                      () -> {
                        var r =
                            movements.changeLoadState(
                                tenant,
                                new LoadChange(
                                    target(1), context(), LoadState.LOADED, "Load verified"));
                        held.countDown();
                        awaitLatch(release);
                        return r;
                      }));
      try {
        assertThat(held.await(15, TimeUnit.SECONDS)).isTrue();
        var drop =
            pool.submit(
                () ->
                    tx(
                        () -> {
                          pid.complete(
                              (Integer)
                                  em.createNativeQuery("select pg_backend_pid()", Integer.class)
                                      .getSingleResult());
                          return coupling.drop(
                              tenant,
                              user,
                              UUID.randomUUID(),
                              trip,
                              driver,
                              new TrailerCouplingTransactions.Drop(
                                  trailer, stop, b, AT.plusSeconds(60)),
                              () -> {});
                        }));
        var waiting = pid.get(15, TimeUnit.SECONDS);
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        f.db.value(
                            "select wait_event_type='Lock' from pg_stat_activity where pid=?",
                            waiting)));
        release.countDown();
        first.get(15, TimeUnit.SECONDS);
        var r = drop.get(15, TimeUnit.SECONDS);
        assertThat(r.trailer().version()).isEqualTo(3);
        assertThat(r.trailer().loadState()).isEqualTo("LOADED");
        assertThat(r.trailer().currentLocationId()).isEqualTo(b);
      } finally {
        release.countDown();
      }
    }
    assertThat(count()).isEqualTo(3);
    assertThat(tx(() -> reconciliation.compare(tenant, trailer)).status()).isEqualTo(Status.MATCH);
  }
}
