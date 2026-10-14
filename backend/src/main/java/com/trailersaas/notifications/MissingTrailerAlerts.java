package com.trailersaas.notifications;

import java.time.Instant;
import java.util.UUID;

/** Synchronous transaction-joining persistence; no delivery or recipient policy. */
public interface MissingTrailerAlerts {
  record Context(UUID tenantId,UUID trailerId,UUID yardCheckId,UUID itemId,
      UUID movementEventId,UUID actorUserId,UUID clientOperationId,Instant occurredAt) {}
  UUID open(Context context);
}
