package com.trailersaas.trailers;

import java.time.Instant;
import java.util.UUID;

/**
 * Physical persistence boundary. The caller must hold its assigned Trip lock and fresh actor
 * authorization in the same runtime Tenant transaction. No planning or lifecycle transitions.
 */
public interface TrailerCouplingTransactions {
  record TripContext(
      UUID id,
      String status,
      long version,
      UUID currentDriverId,
      UUID currentTractorId,
      UUID plannedInitialTrailerId) {}

  record Hook(
      UUID trailerId,
      UUID tractorId,
      UUID driverId,
      Instant occurredAt,
      long expectedTrailerVersion) {}

  record Drop(UUID trailerId, UUID stopId, UUID locationId, Instant occurredAt) {}

  record Coupling(
      UUID id,
      UUID trailerId,
      UUID tractorId,
      UUID driverId,
      UUID tripId,
      Instant hookedAt,
      Instant droppedAt) {}

  record Trailer(
      UUID id,
      long version,
      String locationState,
      UUID currentLocationId,
      UUID lastKnownLocationId,
      Instant lastSeenAt,
      UUID lastDriverId,
      String loadState,
      String serviceabilityState) {}

  record Movement(UUID id, String eventType, Instant occurredAt, Instant recordedAt) {}

  record Result(Coupling coupling, Trailer trailer, TripContext trip, Movement movementEvent) {}

  /** Run after actor/assigned Driver revalidation; same-key replay precedes physical checks. */
  Result hook(
      UUID tenant, UUID user, UUID operation, TripContext trip, UUID actualDriver, Hook request);

  /**
   * Destination validation runs only for first execution, after the successful replay lookup.
   * Caller uses its locked Stop/Customer context, never request-supplied Customer hints.
   */
  Result drop(
      UUID tenant,
      UUID user,
      UUID operation,
      TripContext trip,
      UUID actualDriver,
      Drop request,
      Runnable validateDestination);
}
