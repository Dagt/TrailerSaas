package com.trailersaas.trips;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.UUID;

/** Synchronous owning-domain transition. The caller owns evidence and ledger orchestration. */
public interface DriverDeliveryExecution {
  record Assignment(UUID tripId, UUID driverId, UUID tractorId) {}
  record Result(UUID stopId, UUID tripId, String stopStatus, String tripStatus,
      long stopVersion, long tripVersion, Instant completedAt) {}
  Assignment prepare(AuthenticatedUserPrincipal actor, UUID tenant, UUID stop);
  Result complete(AuthenticatedUserPrincipal actor, UUID tenant, UUID stop, UUID operation, Instant occurredAt);
}
