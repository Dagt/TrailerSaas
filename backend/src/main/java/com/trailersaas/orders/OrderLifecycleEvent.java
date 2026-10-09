package com.trailersaas.orders;

import java.time.Instant;
import java.util.UUID;

/** In-process, transaction-bound integration event. Not a durable audit/history record.
 * Future audit listeners must participate in the transaction; publication is not a replay guarantee.
 */
public record OrderLifecycleEvent(UUID tenantId, UUID orderId, UUID customerId, Action action,
        String previousState, String newState, long version, Actor actor, String reason,
        Resolution resolution, Instant occurredAt) {
    public enum Action { ORDER_EDITED, ORDER_PLANNED, ORDER_FULFILLMENT_STARTED,
        ORDER_PARTIALLY_DELIVERED, ORDER_FULFILLMENT_RESUMED, ORDER_DELIVERED,
        ORDER_CANCELLED, ORDER_CANCELLATION_REQUESTED, ORDER_CANCELLATION_FINISHED, ORDER_COMPLETED }
    public record Actor(String actorType, String sourceType, UUID userId, UUID membershipId) {
        public static Actor system() { return new Actor("SYSTEM", "SYSTEM_PROCESS", null, null); }
    }
    public record Resolution(String type, UUID destinationSiteId, String instructions) {}
}
