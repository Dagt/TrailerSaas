package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerMovementRejectedException.Reason.*;
import static com.trailersaas.trailers.internal.MovementRules.*;

import com.trailersaas.customers.OperationalLocationDirectory;
import com.trailersaas.trailers.TrailerMovementProjection;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTrailerMovementProjection implements TrailerMovementProjection {
  private final EntityManager em;
  private final OperationalLocationDirectory locations;

  DatabaseTrailerMovementProjection(EntityManager em, OperationalLocationDirectory locations) {
    this.em = em;
    this.locations = locations;
  }

  public Result depart(UUID tenant, Departure c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.depart(s, c.context()));
  }

  public Result arrive(UUID tenant, Arrival c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.arrive(s, c));
  }

  public Result yardMove(UUID tenant, YardMove c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.yard(s, c));
  }

  public Result correctLocation(UUID tenant, Correction c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.correct(s, c));
  }

  public Result markLocationUnknown(UUID tenant, MarkUnknown c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.unknown(s, c));
  }

  public Result recoverLocation(UUID tenant, Recovery c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.recover(s, c));
  }

  public Result changeLoadState(UUID tenant, LoadChange c) {
    require(c != null);
    return apply(tenant, c.target(), c.context(), s -> MovementRules.load(s, c));
  }

  private Result apply(UUID tenant, Target target, Context context, Function<State, Change> rule) {
    validate(target, context);
    pinned(tenant);
    var t =
        em.createQuery(
                "select t from TrailerEntity t where t.tenantId=:tenant and t.id=:id",
                TrailerEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("id", target.trailerId())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> fail(RESOURCE_NOT_FOUND));
    // A caller may already have a managed projection; refresh while holding the row lock.
    em.refresh(t, LockModeType.PESSIMISTIC_WRITE);
    if (t.version != target.expectedVersion()) throw fail(STALE_VERSION);
    var change = rule.apply(state(t));
    if (change.locationId() != null && locations.lockSiteId(tenant, change.locationId()).isEmpty())
      throw fail(RESOURCE_NOT_FOUND);
    var e = write(t, context, change);
    try {
      em.flush();
    } catch (RuntimeException failure) {
      if (TrailerFailure.expectedIntegrity(failure)) throw fail(RESOURCE_CONFLICT);
      throw failure;
    }
    return new Result(
        t.id,
        t.version,
        LocationState.valueOf(t.locationState),
        t.currentLocationId,
        t.lastKnownLocationId,
        t.lastSeenAt,
        t.lastDriverId,
        LoadState.valueOf(t.loadState),
        t.serviceabilityState,
        new Movement(e.id, e.eventType, e.occurredAt, e.recordedAt));
  }

  /** #38 already holds the Trailer and all physical parent locks; it retains its ledger/guards. */
  TrailerMovementEntity coupling(
      TrailerEntity t,
      UUID user,
      UUID key,
      UUID trip,
      UUID driver,
      UUID tractor,
      UUID stop,
      UUID location,
      Instant occurred,
      boolean drop) {
    var context =
        new Context(
            occurred,
            new Provenance(ActorType.HUMAN_USER, SourceType.USER_ACTION, user),
            driver,
            trip,
            stop,
            tractor,
            key);
    validate(new Target(t.id, t.version), context);
    return write(t, context, MovementRules.coupling(state(t), context, location, drop));
  }

  private TrailerMovementEntity write(TrailerEntity t, Context c, Change change) {
    var e = new TrailerMovementEntity();
    e.id = UUID.randomUUID();
    e.tenantId = t.tenantId;
    e.trailerId = t.id;
    e.tripId = c.tripId();
    e.tripStopId = c.tripStopId();
    e.driverId = c.driverId();
    e.tractorId = c.tractorId();
    e.locationId = change.locationId();
    e.eventType = change.type();
    e.reason = change.reason();
    e.metadata = change.metadata();
    e.actorType = c.provenance().actorType().name();
    e.sourceType = c.provenance().sourceType().name();
    e.actorUserId = c.provenance().actorUserId();
    e.occurredAt = c.occurredAt();
    e.clientOperationId = c.clientOperationId();
    // Serialize event persistence chronology under the same Trailer lock. Microsecond precision
    // matches PostgreSQL; equal/backward server-clock readings cannot reorder accepted facts.
    var last =
        em.createQuery(
                "select max(e.recordedAt) from TrailerMovementEntity e where e.tenantId=:tenant and"
                    + " e.trailerId=:trailer",
                Instant.class)
            .setParameter("tenant", t.tenantId)
            .setParameter("trailer", t.id)
            .getSingleResult();
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    e.recordedAt = last != null && !now.isAfter(last) ? last.plus(1, ChronoUnit.MICROS) : now;
    em.persist(e);
    var after = change.state();
    t.locationState = after.locationState().name();
    t.currentLocationId = after.currentLocationId();
    t.lastKnownLocationId = after.lastKnownLocationId();
    t.lastSeenAt = after.lastSeenAt();
    t.lastDriverId = after.lastDriverId();
    t.loadState = after.loadState().name();
    // Dirty even for an observation with identical operational values; @Version remains sole owner.
    t.updatedAt = now.isAfter(t.updatedAt) ? now : t.updatedAt.plus(1, ChronoUnit.MICROS);
    return e;
  }

  static State state(TrailerEntity t) {
    return new State(
        LocationState.valueOf(t.locationState),
        t.currentLocationId,
        t.lastKnownLocationId,
        t.lastSeenAt,
        t.lastDriverId,
        LoadState.valueOf(t.loadState));
  }

  void pinned(UUID tenant) {
    if (tenant == null
        || !Boolean.TRUE.equals(
            em.createNativeQuery(
                    "select"
                        + " coalesce(NULLIF(current_setting('app.current_tenant_id',true),'')::uuid=:tenant"
                        + " and NULLIF(current_setting('app.current_user_id',true),'') is"
                        + " null,false)",
                    Boolean.class)
                .setParameter("tenant", tenant)
                .getSingleResult())) throw fail(RESOURCE_NOT_FOUND);
  }
}
