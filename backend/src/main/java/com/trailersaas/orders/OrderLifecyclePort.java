package com.trailersaas.orders;

import java.time.Instant;
import java.util.UUID;

/** Internal module API, never a generic REST set-status surface.
 * Caller must own workflow authorization, establish a runtime Tenant transaction, and validate
 * operational planning/execution/Stops/POD obligations. No context switching or administrative DB.
 */
public interface OrderLifecyclePort {
    record Revision(UUID orderId, String status, long version, Instant updatedAt) {}
    Revision plan(UUID tenant, UUID order, long expectedVersion);
    Revision startFulfillment(UUID tenant, UUID order, long expectedVersion);
    Revision recordPartialDelivery(UUID tenant, UUID order, long expectedVersion);
    Revision resumeFulfillment(UUID tenant, UUID order, long expectedVersion);
    Revision deliver(UUID tenant, UUID order, long expectedVersion);
    Revision finishCancellation(UUID tenant, UUID order, long expectedVersion);
}
