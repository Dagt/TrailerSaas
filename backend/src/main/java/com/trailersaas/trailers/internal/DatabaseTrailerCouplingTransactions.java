package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.CouplingCommandRejectedException.Reason.*;

import com.trailersaas.tractors.TractorCouplingPolicy;
import com.trailersaas.trailers.*;
import jakarta.persistence.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTrailerCouplingTransactions implements TrailerCouplingTransactions {
  private final EntityManager em;
  private final TractorCouplingPolicy tractors;
  private final JsonMapper json;

  DatabaseTrailerCouplingTransactions(
      EntityManager em, TractorCouplingPolicy tractors, JsonMapper json) {
    this.em = em;
    this.tractors = tractors;
    this.json = json;
  }

  public Result hook(
      UUID tenant, UUID user, UUID operation, TripContext trip, UUID actualDriver, Hook r) {
    var hash = hookHash(trip.id(), r);
    var replay = replay(tenant, user, operation, "TRAILER_HOOK", hash);
    if (replay != null) return replay;
    if (!actualDriver.equals(r.driverId())) throw fail(MISSING);
    state(trip);
    if (!r.trailerId().equals(trip.plannedInitialTrailerId())) throw fail(SUBSTITUTION);
    if (!r.tractorId().equals(trip.currentTractorId())) throw fail(CONFLICT);
    if (!tractors.lock(tenant, r.tractorId(), true)) throw fail(CONFLICT);
    var trailer = trailer(tenant, r.trailerId());
    var coupled = activeTrailer(tenant, r.trailerId(), false);
    if (coupled != null && !trip.id().equals(coupled.tripId)) throw fail(TRAILER_COUPLED);
    if (trailer.version != r.expectedTrailerVersion()) throw fail(STALE);
    if (!trailer.active || !"IN_SERVICE".equals(trailer.serviceabilityState)) throw fail(CONFLICT);
    if (coupled != null) throw fail(TRAILER_COUPLED);
    if (activeTractor(tenant, r.tractorId())) throw fail(TRACTOR_COUPLED);
    if (activeTrip(tenant, trip.id())) throw fail(CONFLICT);
    var at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var event =
        event(
            tenant,
            user,
            operation,
            trip.id(),
            r.trailerId(),
            r.driverId(),
            r.tractorId(),
            null,
            "AT_LOCATION".equals(trailer.locationState) ? trailer.currentLocationId : null,
            "HOOKED",
            r.occurredAt(),
            at);
    var c = new TrailerCouplingEntity();
    c.id = UUID.randomUUID();
    c.tenantId = tenant;
    c.trailerId = r.trailerId();
    c.tractorId = r.tractorId();
    c.driverId = r.driverId();
    c.tripId = trip.id();
    c.hookedAt = r.occurredAt();
    c.hookEventId = event.id;
    c.createdAt = at;
    em.persist(c);
    observed(trailer, r.driverId(), r.occurredAt(), at);
    flush();
    return remember(
        tenant, user, operation, "TRAILER_HOOK", hash, result(c, trailer, trip, event), at);
  }

  public Result drop(
      UUID tenant,
      UUID user,
      UUID operation,
      TripContext trip,
      UUID driver,
      Drop r,
      Runnable destination) {
    var hash = dropHash(trip.id(), r);
    var replay = replay(tenant, user, operation, "TRAILER_DROP", hash);
    if (replay != null) return replay;
    state(trip);
    // Read only the key first. Both commands lock Tractor -> Trailer -> Coupling.
    var initial = activeTrailer(tenant, r.trailerId(), false);
    if (initial == null || !trip.id().equals(initial.tripId)) throw fail(CONFLICT);
    if (!tractors.lock(tenant, initial.tractorId, false)) throw fail(CONFLICT);
    var trailer = trailer(tenant, r.trailerId());
    var c = activeTrailer(tenant, r.trailerId(), true);
    if (c == null || !trip.id().equals(c.tripId)) throw fail(CONFLICT);
    if (r.occurredAt().isBefore(c.hookedAt)) throw fail(CONFLICT);
    destination.run();
    var at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var event =
        event(
            tenant,
            user,
            operation,
            trip.id(),
            r.trailerId(),
            driver,
            c.tractorId,
            r.stopId(),
            r.locationId(),
            "DROPPED",
            r.occurredAt(),
            at);
    c.droppedAt = r.occurredAt();
    c.dropEventId = event.id;
    trailer.locationState = "AT_LOCATION";
    trailer.currentLocationId = r.locationId();
    trailer.lastKnownLocationId = r.locationId();
    observed(trailer, driver, r.occurredAt(), at);
    flush();
    return remember(
        tenant, user, operation, "TRAILER_DROP", hash, result(c, trailer, trip, event), at);
  }

  private Result replay(UUID tenant, UUID user, UUID key, String type, String hash) {
    pinned(tenant);
    // SHA-256 prefix is only a lock key: collisions serialize extra work, never identify
    // operations.
    long lock = ByteBuffer.wrap(digest(tenant + "|" + key)).getLong();
    em.createNativeQuery("select pg_advisory_xact_lock(:key)")
        .setParameter("key", lock)
        .getSingleResult();
    var row =
        em.createQuery(
                "select p from ProcessedClientOperationEntity p where p.tenantId=:tenant and"
                    + " p.clientOperationId=:key",
                ProcessedClientOperationEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("key", key)
            .getResultStream()
            .findFirst();
    if (row.isEmpty()) return null;
    var p = row.get();
    if (!user.equals(p.userId)
        || !type.equals(p.operationType)
        || !hash.equals(p.requestHash)
        || !"SUCCEEDED".equals(p.status)
        || p.responseData == null) throw fail(KEY_REUSED);
    return json.convertValue(p.responseData, Result.class);
  }

  private Result remember(
      UUID tenant, UUID user, UUID key, String type, String hash, Result response, Instant at) {
    var p = new ProcessedClientOperationEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenant;
    p.clientOperationId = key;
    p.userId = user;
    p.operationType = type;
    p.requestHash = hash;
    p.status = "SUCCEEDED";
    p.processedAt = at;
    p.resultEntityType = "TRAILER_COUPLING";
    p.resultEntityId = response.coupling().id();
    p.responseData = json.convertValue(response, new TypeReference<Map<String, Object>>() {});
    em.persist(p);
    flush();
    return response;
  }

  private TrailerMovementEntity event(
      UUID tenant,
      UUID user,
      UUID key,
      UUID trip,
      UUID trailer,
      UUID driver,
      UUID tractor,
      UUID stop,
      UUID location,
      String type,
      Instant occurred,
      Instant at) {
    var e = new TrailerMovementEntity();
    e.id = UUID.randomUUID();
    e.tenantId = tenant;
    e.trailerId = trailer;
    e.tripId = trip;
    e.tripStopId = stop;
    e.driverId = driver;
    e.tractorId = tractor;
    e.locationId = location;
    e.eventType = type;
    e.actorType = "HUMAN_USER";
    e.sourceType = "USER_ACTION";
    e.actorUserId = user;
    e.occurredAt = occurred;
    e.recordedAt = at;
    e.clientOperationId = key;
    em.persist(e);
    return e;
  }

  private TrailerEntity trailer(UUID tenant, UUID id) {
    return em.createQuery(
            "select t from TrailerEntity t where t.tenantId=:tenant and t.id=:id",
            TrailerEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> fail(MISSING));
  }

  private TrailerCouplingEntity activeTrailer(UUID tenant, UUID trailer, boolean lock) {
    var q =
        em.createQuery(
                "select c from TrailerCouplingEntity c where c.tenantId=:tenant and"
                    + " c.trailerId=:trailer and c.droppedAt is null",
                TrailerCouplingEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("trailer", trailer);
    if (lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    return q.getResultStream().findFirst().orElse(null);
  }

  private boolean activeTractor(UUID tenant, UUID tractor) {
    return !em.createQuery(
            "select c.id from TrailerCouplingEntity c where c.tenantId=:tenant and"
                + " c.tractorId=:tractor and c.droppedAt is null",
            UUID.class)
        .setParameter("tenant", tenant)
        .setParameter("tractor", tractor)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  private boolean activeTrip(UUID tenant, UUID trip) {
    return !em.createQuery(
            "select c.id from TrailerCouplingEntity c where c.tenantId=:tenant and c.tripId=:trip"
                + " and c.droppedAt is null",
            UUID.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  private static void observed(TrailerEntity t, UUID driver, Instant occurred, Instant at) {
    t.lastDriverId = driver;
    t.lastSeenAt = occurred;
    // Always dirty, even when two physical facts share device time/Driver.
    t.updatedAt = at.isAfter(t.updatedAt) ? at : t.updatedAt.plus(1, ChronoUnit.MICROS);
  }

  private static Result result(
      TrailerCouplingEntity c, TrailerEntity t, TripContext trip, TrailerMovementEntity e) {
    return new Result(
        new Coupling(c.id, c.trailerId, c.tractorId, c.driverId, c.tripId, c.hookedAt, c.droppedAt),
        new Trailer(
            t.id,
            t.version,
            t.locationState,
            t.currentLocationId,
            t.lastKnownLocationId,
            t.lastSeenAt,
            t.lastDriverId,
            t.loadState,
            t.serviceabilityState),
        trip,
        new Movement(e.id, e.eventType, e.occurredAt, e.recordedAt));
  }

  private static void state(TripContext trip) {
    if (!Set.of("ASSIGNED", "AT_STOP", "CANCELLING").contains(trip.status())) throw fail(STATE);
  }

  private void pinned(UUID tenant) {
    if (!Boolean.TRUE.equals(
        em.createNativeQuery(
                "select"
                    + " coalesce(NULLIF(current_setting('app.current_tenant_id',true),'')::uuid=:tenant"
                    + " and NULLIF(current_setting('app.current_user_id',true),'') is null,false)",
                Boolean.class)
            .setParameter("tenant", tenant)
            .getSingleResult())) throw fail(MISSING);
  }

  static String hookHash(UUID trip, Hook r) {
    return hex(
        "TRAILER_HOOK|"
            + trip
            + "|"
            + r.trailerId()
            + "|"
            + r.tractorId()
            + "|"
            + r.driverId()
            + "|"
            + r.occurredAt()
            + "|"
            + r.expectedTrailerVersion());
  }

  static String dropHash(UUID trip, Drop r) {
    return hex(
        "TRAILER_DROP|"
            + trip
            + "|"
            + r.trailerId()
            + "|"
            + r.stopId()
            + "|"
            + r.locationId()
            + "|"
            + r.occurredAt());
  }

  private static String hex(String s) {
    return HexFormat.of().formatHex(digest(s));
  }

  private static byte[] digest(String s) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Required JDK digest unavailable.");
    }
  }

  private static CouplingCommandRejectedException fail(CouplingCommandRejectedException.Reason r) {
    return new CouplingCommandRejectedException(r);
  }

  private void flush() {
    try {
      em.flush();
    } catch (RuntimeException e) {
      if (!TrailerFailure.expectedIntegrity(e)) throw e;
      throw fail(CONFLICT);
    }
  }
}
