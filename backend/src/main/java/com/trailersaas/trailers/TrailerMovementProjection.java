package com.trailersaas.trailers;

import java.time.Instant;
import java.util.UUID;

/**
 * Transaction-joining domain boundary, not an authorization API. The caller establishes one Tenant,
 * authorizes the full workflow and locks Trip/equipment parents before invoking this boundary. No
 * reverse Trip/equipment lock, independent commit or idempotency claim occurs here. Expected
 * Trailer version is mandatory for every new operation, including offline occurrences.
 */
public interface TrailerMovementProjection {
  enum LocationState {
    AT_LOCATION,
    IN_TRANSIT,
    LOCATION_UNKNOWN
  }

  enum LoadState {
    EMPTY,
    LOADED,
    UNKNOWN
  }

  enum ActorType {
    HUMAN_USER,
    SYSTEM,
    INTEGRATION
  }

  enum SourceType {
    USER_ACTION,
    MOBILE_AUTOMATION,
    SYSTEM_PROCESS,
    EXTERNAL_INTEGRATION
  }

  enum ArrivalSource {
    MANUAL,
    GEOFENCE_CONFIRMED
  }

  record Provenance(ActorType actorType, SourceType sourceType, UUID actorUserId) {}

  /** IDs and provenance must come from the authorized server orchestration, never client hints. */
  record Context(
      Instant occurredAt,
      Provenance provenance,
      UUID driverId,
      UUID tripId,
      UUID tripStopId,
      UUID tractorId,
      UUID clientOperationId) {}

  record Target(UUID trailerId, long expectedVersion) {}

  record Departure(Target target, Context context) {}

  record Arrival(Target target, Context context, UUID locationId, ArrivalSource arrivalSource) {}

  record YardMove(Target target, Context context, UUID fromLocationId, UUID toLocationId) {}

  record Correction(
      Target target,
      Context context,
      UUID locationId,
      String reason,
      boolean physicalObservation) {}

  record MarkUnknown(Target target, Context context, String reason) {}

  record Recovery(Target target, Context context, UUID locationId) {}

  record LoadChange(Target target, Context context, LoadState loadState, String reason) {}

  record Movement(UUID id, String eventType, Instant occurredAt, Instant recordedAt) {}

  record Result(
      UUID trailerId,
      long version,
      LocationState locationState,
      UUID currentLocationId,
      UUID lastKnownLocationId,
      Instant lastSeenAt,
      UUID lastDriverId,
      LoadState loadState,
      String serviceabilityState,
      Movement movementEvent) {}

  Result depart(UUID tenantId, Departure command);

  Result arrive(UUID tenantId, Arrival command);

  Result yardMove(UUID tenantId, YardMove command);

  Result correctLocation(UUID tenantId, Correction command);

  Result markLocationUnknown(UUID tenantId, MarkUnknown command);

  Result recoverLocation(UUID tenantId, Recovery command);

  Result changeLoadState(UUID tenantId, LoadChange command);
}
