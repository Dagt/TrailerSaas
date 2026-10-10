package com.trailersaas.trailers.internal;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TrailerAdministrativeConcurrencyPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired TrailerAdministrativeCommandService commands;
  @Autowired TrailerService metadata;
  @Autowired TrailerCouplingTransactions coupling;
  @Autowired EntityManager em;
  TrailerFixtures f;
  UUID tenant, user, driver, tractor, trailer, location, customer;
  AuthenticatedUserPrincipal actor;
  TrailerCouplingTransactions.TripContext trip;
  static final Instant AT = Instant.parse("2026-10-01T12:00:00Z");

  @BeforeEach
  void seed() throws Exception {
    f = new TrailerFixtures(flyway);
    tenant = f.db.tenant();
    user = f.db.user();
    f.db.membership(tenant, user, "ACTIVE", "TENANT_ADMIN");
    driver = f.db.driver(tenant, user);
    tractor = f.db.tractor(tenant, "Command tractor-" + UUID.randomUUID());
    trailer = f.trailer(tenant, "Command race-" + UUID.randomUUID(), null);
    location = f.location(tenant);
    customer = f.customer(tenant);
    f.db.execute(
        "update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",
        location,
        trailer);
    var id = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trips(id,tenant_id,status,current_driver_id,current_tractor_id,planned_initial_trailer_id,created_at,updated_at)"
            + " values(?,?,'ASSIGNED',?,?,?,now(),now())",
        id,
        tenant,
        driver,
        tractor,
        trailer);
    trip = new TrailerCouplingTransactions.TripContext(id, "ASSIGNED", 0, driver, tractor, trailer);
    actor = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
  }

  <T> T tx(Supplier<T> action) {
    return transactions.execute(tenant, action);
  }

  Object command(String type, long version) {
    return switch (type) {
      case "CORRECT" ->
          commands.correct(
              actor,
              tenant,
              trailer,
              new TrailerCommandRequests.LocationCorrection(version, location, "Confirmed"));
      case "UNKNOWN" ->
          commands.unknown(
              actor,
              tenant,
              trailer,
              new TrailerCommandRequests.MarkUnknown(version, "Unable to locate"));
      case "LOAD" ->
          commands.load(
              actor,
              tenant,
              trailer,
              new TrailerCommandRequests.LoadChange(
                  version, TrailerMovementProjection.LoadState.LOADED, "Confirmed contents"));
      case "OOS" ->
          commands.oos(
              actor,
              tenant,
              trailer,
              new TrailerCommandRequests.OutOfService(version, "Damage found"));
      case "RETURN" ->
          commands.restore(
              actor,
              tenant,
              trailer,
              new TrailerCommandRequests.ReturnToService(version, "Inspected"));
      case "DESIGNATE" ->
          commands.designate(
              actor,
              tenant,
              trailer,
              new TrailerCommandRequests.CustomerDesignation(version, customer, "Reassigned"));
      default ->
          metadata.patch(
              actor,
              tenant,
              trailer,
              Map.of("expectedVersion", version, "trailerType", "Updated type"));
    };
  }

  @ParameterizedTest
  @CsvSource({
    "CORRECT,CORRECT",
    "CORRECT,UNKNOWN",
    "UNKNOWN,CORRECT",
    "LOAD,LOAD",
    "OOS,OOS",
    "OOS,RETURN",
    "RETURN,OOS",
    "DESIGNATE,DESIGNATE",
    "DESIGNATE,PATCH",
    "PATCH,DESIGNATE"
  })
  void sameVersionCommandsSerializeAndTheLoserIsStale(String first, String second)
      throws Exception {
    if (first.equals("RETURN"))
      f.db.execute("update trailers set serviceability_state='OUT_OF_SERVICE' where id=?", trailer);
    var failure = race(() -> command(first, 0), () -> command(second, 0));
    assertThat(failure).isNotNull();
    assertStale(failure);
    assertThat(version()).isEqualTo(1);
    assertThat(count()).isEqualTo(first.equals("PATCH") ? 0 : 1);
  }

  @Test
  void oosFirstBlocksNewHookEvenWithTheCurrentVersion() throws Exception {
    var operation = UUID.randomUUID();
    var failure = race(() -> command("OOS", 0), () -> hook(operation, 1));
    assertThat(failure)
        .isInstanceOf(CouplingCommandRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", CouplingCommandRejectedException.Reason.CONFLICT);
    assertThat(version()).isEqualTo(1);
    assertThat(count()).isEqualTo(1);
    assertThat(openCouplings()).isZero();
    assertThat(
            f.db.value(
                "select count(*) from processed_client_operations where client_operation_id=?",
                operation))
        .isEqualTo(0L);
  }

  @Test
  void hookFirstAllowsOosWithoutLosingThePhysicalCoupling() throws Exception {
    var failure = race(() -> hook(UUID.randomUUID(), 0), () -> command("OOS", 1));
    assertThat(failure).isNull();
    assertThat(version()).isEqualTo(2);
    assertThat(count()).isEqualTo(2);
    assertThat(openCouplings()).isEqualTo(1);
    assertThat(f.db.value("select serviceability_state from trailers where id=?", trailer))
        .isEqualTo("OUT_OF_SERVICE");
  }

  @Test
  void oosFirstStillAllowsTheExistingCouplingToDropAndReplay() throws Exception {
    tx(() -> hook(UUID.randomUUID(), 0));
    var operation = UUID.randomUUID();
    var failure = race(() -> command("OOS", 1), () -> drop(operation));
    assertThat(failure).isNull();
    assertThat(version()).isEqualTo(3);
    assertThat(count()).isEqualTo(3);
    assertThat(openCouplings()).isZero();
    assertThat(f.db.value("select serviceability_state from trailers where id=?", trailer))
        .isEqualTo("OUT_OF_SERVICE");
    var first = tx(() -> drop(operation));
    var replay = tx(() -> drop(operation));
    assertThat(replay).isEqualTo(first);
    assertThat(version()).isEqualTo(3);
    assertThat(count()).isEqualTo(3);
  }

  @Test
  void dropFirstAllowsOosWithoutReopeningOrRewritingTheInterval() throws Exception {
    tx(() -> hook(UUID.randomUUID(), 0));
    var failure = race(() -> drop(UUID.randomUUID()), () -> command("OOS", 2));
    assertThat(failure).isNull();
    assertThat(version()).isEqualTo(3);
    assertThat(count()).isEqualTo(3);
    assertThat(openCouplings()).isZero();
    assertThat(
            f.db.value(
                "select count(*) from trailer_couplings where trailer_id=? and dropped_at is not"
                    + " null",
                trailer))
        .isEqualTo(1L);
  }

  TrailerCouplingTransactions.Result hook(UUID operation, long version) {
    lockTrip();
    return coupling.hook(
        tenant,
        user,
        operation,
        trip,
        driver,
        new TrailerCouplingTransactions.Hook(trailer, tractor, driver, AT, version));
  }

  TrailerCouplingTransactions.Result drop(UUID operation) {
    lockTrip();
    return coupling.drop(
        tenant,
        user,
        operation,
        trip,
        driver,
        new TrailerCouplingTransactions.Drop(trailer, null, location, AT.plusSeconds(60)),
        () -> {});
  }

  void lockTrip() {
    em.createNativeQuery(
            "select id from trips where tenant_id=:tenant and id=:id for update", UUID.class)
        .setParameter("tenant", tenant)
        .setParameter("id", trip.id())
        .getSingleResult();
  }

  Throwable race(Supplier<?> first, Supplier<?> second) throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pid = new CompletableFuture<Integer>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () ->
                  tx(
                      () -> {
                        var result = first.get();
                        held.countDown();
                        waitFor(release);
                        return result;
                      }));
      try {
        assertThat(held.await(20, TimeUnit.SECONDS)).isTrue();
        var waiter =
            pool.submit(
                () -> {
                  try {
                    tx(
                        () -> {
                          pid.complete(
                              (Integer)
                                  em.createNativeQuery("select pg_backend_pid()", Integer.class)
                                      .getSingleResult());
                          return second.get();
                        });
                    return (Throwable) null;
                  } catch (RuntimeException failure) {
                    return failure;
                  }
                });
        var backend = pid.get(20, TimeUnit.SECONDS);
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        f.db.value(
                            "select wait_event_type='Lock' from pg_stat_activity where pid=?",
                            backend)));
        release.countDown();
        winner.get(20, TimeUnit.SECONDS);
        return waiter.get(20, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
  }

  void waitFor(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) throw new AssertionError("Lock coordination timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  void assertStale(Throwable failure) {
    if (failure instanceof TrailerFailure f) assertThat(f.code).isEqualTo("STALE_VERSION");
    else
      assertThat(failure)
          .isInstanceOf(TrailerMovementRejectedException.class)
          .hasFieldOrPropertyWithValue(
              "reason", TrailerMovementRejectedException.Reason.STALE_VERSION);
  }

  long version() throws Exception {
    return ((Number) f.db.value("select version from trailers where id=?", trailer)).longValue();
  }

  long count() throws Exception {
    return ((Number)
            f.db.value("select count(*) from trailer_movement_events where trailer_id=?", trailer))
        .longValue();
  }

  long openCouplings() throws Exception {
    return ((Number)
            f.db.value(
                "select count(*) from trailer_couplings where trailer_id=? and dropped_at is null",
                trailer))
        .longValue();
  }
}
