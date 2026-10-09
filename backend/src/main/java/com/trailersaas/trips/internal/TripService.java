package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripContracts.*;

import com.trailersaas.customers.TripDestinationPolicy;
import com.trailersaas.drivers.DriverPlanningPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import com.trailersaas.tractors.TractorPlanningPolicy;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TripService {
  private final TenantAuthorization auth;
  private final OrderPlanningPolicy orders;
  private final OrderDestinationSnapshots snapshots;
  private final TripDestinationPolicy destinations;
  private final DriverPlanningPolicy drivers;
  private final TractorPlanningPolicy tractors;
  private final TrailerPlanningPolicy trailers;
  private final TripRepository repository;
  private final TripDriverUpdateSink updates;

  TripService(
      TenantAuthorization auth,
      OrderPlanningPolicy orders,
      OrderDestinationSnapshots snapshots,
      TripDestinationPolicy destinations,
      DriverPlanningPolicy drivers,
      TractorPlanningPolicy tractors,
      TrailerPlanningPolicy trailers,
      TripRepository repository,
      TripDriverUpdateSink updates) {
    this.auth = auth;
    this.orders = orders;
    this.snapshots = snapshots;
    this.destinations = destinations;
    this.drivers = drivers;
    this.tractors = tractors;
    this.trailers = trailers;
    this.repository = repository;
    this.updates = updates;
  }

  Detail create(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, Object> input) {
    var request = TripValidation.create(input, snapshots);
    auth.requireTenantCapability(actor, tenant, Capability.TRIP_PLAN);
    if (request.driverId() != null) {
      auth.requireTenantCapability(actor, tenant, Capability.TRIP_ASSIGN_DRIVER);
    }
    if (request.tractorId() != null) {
      auth.requireTenantCapability(actor, tenant, Capability.TRIP_ASSIGN_TRACTOR);
    }
    if (request.plannedInitialTrailerId() != null) {
      auth.requireTenantCapability(actor, tenant, Capability.TRIP_ASSIGN_TRAILER);
    }
    var locked = orders.lockEligible(actor, tenant, request.orderIds());
    var customers =
        locked.stream()
            .collect(
                Collectors.toMap(
                    OrderPlanningPolicy.PlanningOrder::orderId,
                    OrderPlanningPolicy.PlanningOrder::customerId));
    if (request.driverId() != null) {
      switch (drivers.check(tenant, request.driverId())) {
        case NOT_FOUND -> throw TripFailure.missing();
        case INACTIVE -> throw TripFailure.conflict();
        default -> {}
      }
    }
    if (request.tractorId() != null) {
      switch (tractors.check(tenant, request.tractorId())) {
        case NOT_FOUND -> throw TripFailure.missing();
        case INACTIVE -> throw TripFailure.conflict();
        default -> {}
      }
    }
    if (request.plannedInitialTrailerId() != null) {
      switch (trailers.check(actor, tenant, request.plannedInitialTrailerId())) {
        case ELIGIBLE -> {}
        case NOT_FOUND -> throw TripFailure.missing();
        default -> throw TripFailure.conflict();
      }
    }
    // Shared Site locks follow all deterministic Order locks; no historical FK to mutable
    // associations.
    for (var stop :
        request.stops().stream()
            .filter(s -> s.siteId() != null)
            .sorted(Comparator.comparing(StopInput::siteId))
            .toList()) {
      var served = stop.orderIds().stream().map(customers::get).collect(Collectors.toSet());
      if (!destinations.compatible(tenant, served, stop.siteId(), stop.locationId())) {
        throw TripFailure.conflict();
      }
    }
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var trip = new TripEntity();
    trip.id = UUID.randomUUID();
    trip.tenantId = tenant;
    trip.status =
        request.driverId() != null && request.tractorId() != null ? "ASSIGNED" : "PLANNED";
    trip.currentDriverId = request.driverId();
    trip.currentTractorId = request.tractorId();
    trip.plannedInitialTrailerId = request.plannedInitialTrailerId();
    trip.createdAt = trip.updatedAt = now;
    repository.insert(trip);
    for (var inputStop : request.stops()) {
      var stop = new TripStopEntity();
      stop.id = UUID.randomUUID();
      stop.tenantId = tenant;
      stop.tripId = trip.id;
      stop.sequenceNumber = inputStop.sequenceNumber();
      stop.stopType = inputStop.stopType();
      stop.siteId = inputStop.siteId();
      stop.locationId = inputStop.locationId();
      stop.siteSnapshot = inputStop.siteSnapshot();
      stop.scheduledAt = inputStop.scheduledAt();
      stop.instructions = inputStop.instructions();
      stop.status = "PENDING";
      stop.createdAt = stop.updatedAt = now;
      repository.insert(stop);
      for (var order : inputStop.orderIds()) {
        var mapping = new TripStopOrderEntity();
        mapping.id = UUID.randomUUID();
        mapping.tenantId = tenant;
        mapping.tripStopId = stop.id;
        mapping.orderId = order;
        repository.insert(mapping);
      }
    }
    if (trip.currentDriverId != null) {
      TripAssignmentHistory.append(
          repository, trip, actor, "DRIVER_ASSIGNED", trip.currentDriverId, null, null, now);
    }
    if (trip.currentTractorId != null) {
      TripAssignmentHistory.append(
          repository, trip, actor, "TRACTOR_ASSIGNED", trip.currentTractorId, null, null, now);
    }
    if (trip.plannedInitialTrailerId != null) {
      TripAssignmentHistory.append(
          repository,
          trip,
          actor,
          "TRAILER_PLANNED",
          trip.plannedInitialTrailerId,
          null,
          null,
          now);
    }
    updates.assignment(trip, trip.currentDriverId, true, "DRIVER", null, now, 0);
    flush();
    for (var order : locked) {
      orders.planned(actor, tenant, order);
    }
    return detail(trip);
  }

  @Transactional(readOnly = true)
  Page list(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, String> parameters) {
    auth.requireTenantCapability(actor, tenant, Capability.TRIP_VIEW_ALL);
    var f = TripValidation.filters(parameters);
    var rows = repository.list(tenant, f);
    boolean more = rows.size() > f.limit();
    var page = more ? rows.subList(0, f.limit()) : rows;
    var last = page.isEmpty() ? null : page.getLast();
    return new Page(
        page.stream().map(Summary::of).toList(),
        more ? new TripValidation.Position(last.createdAt, last.id).encode() : null);
  }

  @Transactional(readOnly = true)
  Detail get(AuthenticatedUserPrincipal actor, UUID tenant, UUID id) {
    auth.requireTenantCapability(actor, tenant, Capability.TRIP_VIEW_ALL);
    return detail(repository.resource(tenant, id, false));
  }

  Detail detail(TripEntity trip) {
    var mappings = repository.mappings(trip.tenantId, trip.id);
    return Detail.of(
        trip,
        repository.stops(trip.tenantId, trip.id).stream()
            .map(
                s ->
                    Stop.of(
                        s,
                        mappings.stream()
                            .filter(a -> a.tripStopId.equals(s.id))
                            .map(a -> a.orderId)
                            .distinct()
                            .sorted()
                            .toList()))
            .toList(),
        repository.history(trip.tenantId, trip.id).stream().map(Assignment::of).toList(),
        repository.operationalHistory(trip.tenantId, trip.id).stream()
            .map(Operational::of)
            .toList());
  }

  @Transactional(readOnly = true)
  Timeline timeline(AuthenticatedUserPrincipal actor, UUID tenant, UUID id) {
    auth.requireTenantCapability(actor, tenant, Capability.TRIP_VIEW_ALL);
    repository.resource(tenant, id, false);
    var items = new ArrayList<HistoryItem>();
    items.addAll(repository.history(tenant, id).stream().map(Assignment::of).toList());
    items.addAll(repository.operationalHistory(tenant, id).stream().map(Operational::of).toList());
    // PostgreSQL UUID ordering is unsigned; Java UUID natural order is signed.
    items.sort(Comparator.comparing(HistoryItem::occurredAt).thenComparing(e -> e.id().toString()));
    return new Timeline(List.copyOf(items));
  }

  private void flush() {
    try {
      repository.flush();
    } catch (DataIntegrityViolationException | ConstraintViolationException failure) {
      if (!TripFailure.integrity(failure)) {
        throw failure;
      }
      throw TripFailure.conflict();
    }
  }
}
