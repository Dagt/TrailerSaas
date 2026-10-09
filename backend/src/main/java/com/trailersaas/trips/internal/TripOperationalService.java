package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripContracts.*;

import com.trailersaas.customers.TripDestinationPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.trips.TripCancellationResolutionPort;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional
class TripOperationalService implements TripCancellationResolutionPort {
  private final TenantAuthorization auth;
  private final TripRepository repository;
  private final TripDestinationPolicy destinations;
  private final TripDriverUpdateSink updates;
  private final TripService reads;

  TripOperationalService(
      TenantAuthorization auth,
      TripRepository repository,
      TripDestinationPolicy destinations,
      TripDriverUpdateSink updates,
      TripService reads) {
    this.auth = auth;
    this.repository = repository;
    this.destinations = destinations;
    this.updates = updates;
    this.reads = reads;
  }

  Detail reroute(
      AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Map<String, Object> input) {
    var r = TripValidation.reroute(input);
    var scope = auth.resolveCustomerScope(actor, tenant, Capability.TRIP_REROUTE);
    var trip = repository.resource(tenant, id, true);
    scoped(trip, scope);
    expected(trip, r.expectedVersion());
    if (!Set.of("IN_TRANSIT", "AT_STOP").contains(trip.status)) throw TripFailure.tripState();
    var customers = repository.stopCustomers(tenant, id, r.stopId());
    var stop = repository.lockStop(tenant, id, r.stopId());
    if (!Set.of("PENDING", "EN_ROUTE").contains(stop.status)) throw TripFailure.tripState();
    var replacement = destinations.snapshot(tenant, r.siteId()).orElseThrow(TripFailure::missing);
    if (!destinations.compatible(tenant, customers, r.siteId(), r.locationId()))
      throw TripFailure.conflict();
    var previous = destination(stop);
    previous.put("status", stop.status);
    previous.put("version", stop.version);
    var next = new LinkedHashMap<String, Object>();
    next.put("siteId", r.siteId());
    next.put("locationId", r.locationId());
    next.put("siteSnapshot", replacement);
    next.put("scheduledAt", r.scheduledAt());
    next.put("instructions", r.instructions());
    var at = advance(trip);
    event(
        trip,
        stop.id,
        actor,
        "TRIP_REROUTED",
        r.reason(),
        Map.of("previous", previous, "replacement", next),
        at);
    var payload = new LinkedHashMap<>(next);
    payload.put("tripId", trip.id);
    payload.put("affectedStopId", stop.id);
    payload.put("reason", r.reason());
    payload.put("tripVersion", trip.version + 1);
    payload.put("stopVersion", stop.version + 1);
    updates.append(trip, trip.currentDriverId, "TRIP_REROUTED", at, payload);
    stop.siteId = r.siteId();
    stop.locationId = r.locationId();
    stop.siteSnapshot = replacement;
    stop.scheduledAt = r.scheduledAt();
    stop.instructions = r.instructions();
    stop.updatedAt = later(stop.updatedAt, at);
    flush();
    return reads.detail(trip);
  }

  Detail cancel(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Map<String, Object> input) {
    var r = TripValidation.cancellation(input);
    var scope = auth.resolveCustomerScope(actor, tenant, Capability.TRIP_REROUTE);
    var cancelScope = auth.resolveCustomerScope(actor, tenant, Capability.ORDER_CANCEL);
    var trip = repository.resource(tenant, id, true);
    scoped(trip, scope);
    scoped(trip, cancelScope);
    expected(trip, r.expectedVersion());
    boolean before = Set.of("PLANNED", "ASSIGNED").contains(trip.status);
    if (!before && !Set.of("IN_TRANSIT", "AT_STOP").contains(trip.status))
      throw TripFailure.tripState();
    var stops = before ? repository.lockedStops(tenant, id) : List.<TripStopEntity>of();
    if (before
        && (trip.startedAt != null
            || stops.stream().anyMatch(s -> Set.of("ARRIVED", "IN_SERVICE").contains(s.status))))
      throw TripFailure.conflict();
    resolution(trip, r, !before);
    var previous = trip.status;
    var at = advance(trip);
    trip.status = before ? "CANCELLED" : "CANCELLING";
    trip.completedAt = null;
    trip.cancelledAt = before ? at : null;
    if (before) cancelStops(stops, at);
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("previousState", previous);
    if (r.resolutionType() != null) {
      metadata.put("resolutionType", r.resolutionType());
      metadata.put("destinationSiteId", r.destinationSiteId());
      metadata.put("instructions", r.instructions());
    }
    var type = before ? "TRIP_CANCELLED" : "TRIP_CANCELLATION_STARTED";
    event(trip, null, actor, type, r.reason(), metadata, at);
    var payload = new LinkedHashMap<String, Object>();
    payload.put("tripId", trip.id);
    payload.put("status", trip.status);
    payload.put("reason", r.reason());
    payload.put("tripVersion", trip.version + 1);
    if (!before) {
      payload.put("resolutionType", r.resolutionType());
      payload.put("destinationSiteId", r.destinationSiteId());
      payload.put("instructions", r.instructions());
    }
    updates.append(trip, trip.currentDriverId, type, at, payload);
    flush();
    return reads.detail(trip);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Revision finishCancellation(UUID tenant, UUID id) {
    repository.requirePinnedTenant(tenant);
    var trip = repository.resource(tenant, id, true);
    if (!"CANCELLING".equals(trip.status)) throw TripFailure.tripState();
    var stops = repository.lockedStops(tenant, id);
    var at = advance(trip);
    trip.status = "CANCELLED";
    trip.cancelledAt = at;
    trip.completedAt = null;
    cancelStops(stops, at);
    event(
        trip,
        null,
        null,
        "TRIP_CANCELLATION_FINISHED",
        null,
        Map.of("previousState", "CANCELLING"),
        at);
    updates.append(
        trip,
        trip.currentDriverId,
        "TRIP_CANCELLATION_FINISHED",
        at,
        Map.of(
            "tripId",
            trip.id,
            "status",
            "CANCELLED",
            "safeResolutionFinished",
            true,
            "tripVersion",
            trip.version + 1));
    flush();
    return new Revision(trip.id, trip.status, trip.version, trip.cancelledAt);
  }

  private void resolution(TripEntity trip, Cancellation r, boolean active) {
    if (active && (r.resolutionType() == null || r.instructions() == null))
      throw TripFailure.invalid("resolutionType");
    if (active
        && Set.of("RETURN_TO_BASE", "REROUTE").contains(r.resolutionType())
        && r.destinationSiteId() == null) throw TripFailure.invalid("destinationSiteId");
    if (r.destinationSiteId() != null) {
      destinations.snapshot(trip.tenantId, r.destinationSiteId()).orElseThrow(TripFailure::missing);
      if ("REROUTE".equals(r.resolutionType())
          && !destinations.compatible(
              trip.tenantId,
              repository.mappedCustomers(trip.tenantId, trip.id),
              r.destinationSiteId(),
              null)) throw TripFailure.conflict();
    }
  }

  private void scoped(TripEntity trip, CustomerAccessScope scope) {
    if (!scope.tenantWide()) {
      var customers = repository.mappedCustomers(trip.tenantId, trip.id);
      if (customers.isEmpty() || !scope.customerIds().containsAll(customers))
        throw TripFailure.missing();
    }
  }

  private static void expected(TripEntity trip, long version) {
    if (trip.version != version) throw TripFailure.stale();
  }

  private static void cancelStops(List<TripStopEntity> stops, Instant at) {
    for (var stop : stops) {
      if (!Set.of("COMPLETED", "CANCELLED").contains(stop.status)) {
        stop.status = "CANCELLED";
        stop.updatedAt = later(stop.updatedAt, at);
      }
    }
  }

  private static Map<String, Object> destination(TripStopEntity s) {
    var m = new LinkedHashMap<String, Object>();
    m.put("siteId", s.siteId);
    m.put("locationId", s.locationId);
    m.put("siteSnapshot", s.siteSnapshot);
    m.put("scheduledAt", s.scheduledAt);
    m.put("instructions", s.instructions);
    return m;
  }

  private static Instant later(Instant previous, Instant at) {
    return at.isAfter(previous) ? at : previous.plus(1, ChronoUnit.MICROS);
  }

  private static Instant advance(TripEntity trip) {
    var at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    trip.updatedAt = later(trip.updatedAt, at);
    return trip.updatedAt;
  }

  private void event(
      TripEntity trip,
      UUID stop,
      AuthenticatedUserPrincipal actor,
      String type,
      String reason,
      Map<String, Object> metadata,
      Instant at) {
    var e = new TripOperationalEventEntity();
    e.id = UUID.randomUUID();
    e.tenantId = trip.tenantId;
    e.tripId = trip.id;
    e.tripStopId = stop;
    e.eventType = type;
    e.actorUserId = actor == null ? null : actor.userId();
    e.targetDriverId = trip.currentDriverId;
    e.occurredAt = at;
    e.reason = reason;
    e.metadata = metadata;
    repository.insert(e);
  }

  private void flush() {
    try {
      repository.flush();
    } catch (DataIntegrityViolationException | ConstraintViolationException e) {
      if (!TripFailure.integrity(e)) throw e;
      throw TripFailure.conflict();
    }
  }
}
