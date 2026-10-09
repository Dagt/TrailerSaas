package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripContracts.*;

import com.trailersaas.drivers.DriverPlanningPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.tractors.TractorPlanningPolicy;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TripAssignmentService {
  enum Kind {
    DRIVER(Capability.TRIP_ASSIGN_DRIVER, "driverId"),
    TRACTOR(Capability.TRIP_ASSIGN_TRACTOR, "tractorId"),
    TRAILER(Capability.TRIP_ASSIGN_TRAILER, "trailerId");
    final Capability capability;
    final String field;

    Kind(Capability c, String f) {
      capability = c;
      field = f;
    }
  }

  private final TenantAuthorization auth;
  private final TripRepository repository;
  private final TripService reads;
  private final DriverPlanningPolicy drivers;
  private final TractorPlanningPolicy tractors;
  private final TrailerPlanningPolicy trailers;
  private final TripDriverUpdateSink updates;

  TripAssignmentService(
      TenantAuthorization auth,
      TripRepository repository,
      TripService reads,
      DriverPlanningPolicy drivers,
      TractorPlanningPolicy tractors,
      TrailerPlanningPolicy trailers,
      TripDriverUpdateSink updates) {
    this.auth = auth;
    this.repository = repository;
    this.reads = reads;
    this.drivers = drivers;
    this.tractors = tractors;
    this.trailers = trailers;
    this.updates = updates;
  }

  Detail assign(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID id,
      Kind kind,
      Map<String, Object> input) {
    var request = TripValidation.assignment(input, kind.field);
    var t = authorizedLock(actor, tenant, id, kind.capability);
    if (t.version != request.expectedVersion()) {
      throw TripFailure.stale();
    }
    if (Set.of("COMPLETED", "CANCELLED").contains(t.status)
        || (kind == Kind.TRAILER && "CANCELLING".equals(t.status))) {
      throw TripFailure.tripState();
    }
    var previous =
        switch (kind) {
          case DRIVER -> t.currentDriverId;
          case TRACTOR -> t.currentTractorId;
          case TRAILER -> t.plannedInitialTrailerId;
        };
    if (request.resourceId().equals(previous)) {
      return reads.detail(t);
    }
    if (kind == Kind.TRAILER
        && previous != null
        && !Set.of("PLANNED", "ASSIGNED").contains(t.status)) {
      throw TripFailure.tripState();
    }
    switch (kind) {
      case DRIVER -> {
        driver(tenant, request.resourceId());
        t.currentDriverId = request.resourceId();
      }
      case TRACTOR -> {
        tractor(tenant, request.resourceId());
        t.currentTractorId = request.resourceId();
      }
      case TRAILER -> {
        trailer(actor, tenant, request.resourceId(), true);
        t.plannedInitialTrailerId = request.resourceId();
      }
    }
    if (kind != Kind.TRAILER
        && "PLANNED".equals(t.status)
        && t.currentDriverId != null
        && t.currentTractorId != null
        && !repository.stops(tenant, id).isEmpty()) {
      // Both required current resources must still be active, not merely non-null.
      driver(tenant, t.currentDriverId);
      tractor(tenant, t.currentTractorId);
      t.status = "ASSIGNED";
    }
    var type =
        kind == Kind.TRAILER
            ? "TRAILER_PLANNED"
            : kind.name() + (previous == null ? "_ASSIGNED" : "_REASSIGNED");
    var key =
        switch (kind) {
          case DRIVER -> "previousDriverId";
          case TRACTOR -> "previousTractorId";
          case TRAILER -> "previousTrailerId";
        };
    var at = advance(t);
    TripAssignmentHistory.append(
        repository,
        t,
        actor,
        type,
        request.resourceId(),
        request.reason(),
        previous == null ? null : Map.of(key, previous.toString()),
        at);
    updates.assignment(t, t.currentDriverId, true, kind.name(), null, at, t.version + 1);
    if (kind == Kind.DRIVER && previous != null)
      updates.assignment(t, previous, false, "DRIVER", null, at, t.version + 1);
    flush();
    return reads.detail(t);
  }

  Detail decide(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Map<String, Object> input) {
    var r = TripValidation.substitution(input);
    var t = authorizedLock(actor, tenant, id, Capability.TRAILER_SUBSTITUTE_AUTHORIZE);
    if (!Set.of("ASSIGNED", "IN_TRANSIT", "AT_STOP").contains(t.status)) {
      throw TripFailure.tripState();
    }
    if (t.plannedInitialTrailerId == null
        || !r.expectedTrailerId().equals(t.plannedInitialTrailerId)) {
      throw TripFailure.conflict();
    }
    boolean authorize = "AUTHORIZE".equals(r.decision());
    trailer(actor, tenant, r.requestedTrailerId(), authorize);
    if (authorize) {
      t.plannedInitialTrailerId = r.requestedTrailerId();
    }
    var at = advance(t); // REJECT adds durable history even though the projection stays unchanged.
    TripAssignmentHistory.append(
        repository,
        t,
        actor,
        "TRAILER_SUBSTITUTION_" + (authorize ? "AUTHORIZED" : "REJECTED"),
        r.requestedTrailerId(),
        r.reason(),
        Map.of("expectedTrailerId", r.expectedTrailerId().toString()),
        at);
    updates.assignment(t, t.currentDriverId, true, "TRAILER", r.decision(), at, t.version + 1);
    flush();
    return reads.detail(t);
  }

  private TripEntity authorizedLock(
      AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Capability capability) {
    var scope = auth.resolveCustomerScope(actor, tenant, capability);
    var t = repository.resource(tenant, id, true);
    if (!scope.tenantWide()) {
      var customers = repository.mappedCustomers(tenant, id);
      if (customers.isEmpty() || !scope.customerIds().containsAll(customers)) {
        throw TripFailure.missing();
      }
    }
    return t;
  }

  private void driver(UUID tenant, UUID id) {
    switch (drivers.check(tenant, id)) {
      case NOT_FOUND -> throw TripFailure.missing();
      case INACTIVE -> throw TripFailure.conflict();
      default -> {}
    }
  }

  private void tractor(UUID tenant, UUID id) {
    switch (tractors.check(tenant, id)) {
      case NOT_FOUND -> throw TripFailure.missing();
      case INACTIVE -> throw TripFailure.conflict();
      default -> {}
    }
  }

  private void trailer(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, boolean usable) {
    switch (usable ? trailers.check(actor, tenant, id) : trailers.visible(actor, tenant, id)) {
      case NOT_FOUND, CUSTOMER_SCOPE_DENIED -> throw TripFailure.missing();
      case UNAVAILABLE -> throw TripFailure.conflict();
      default -> {}
    }
  }

  private Instant advance(TripEntity t) {
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    t.updatedAt = now.isAfter(t.updatedAt) ? now : t.updatedAt.plus(1, ChronoUnit.MICROS);
    return t.updatedAt;
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
