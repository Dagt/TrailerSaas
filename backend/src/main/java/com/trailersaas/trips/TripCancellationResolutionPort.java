package com.trailersaas.trips;

import java.time.Instant;
import java.util.UUID;

/**
 * Future execution caller owns physical safe-resolution facts and authorization. Requires an
 * already pinned runtime Tenant transaction; no REST shortcut or Tenant switching.
 */
public interface TripCancellationResolutionPort {
  Revision finishCancellation(UUID tenantId, UUID tripId);

  record Revision(UUID tripId, String status, long version, Instant cancelledAt) {}
}
