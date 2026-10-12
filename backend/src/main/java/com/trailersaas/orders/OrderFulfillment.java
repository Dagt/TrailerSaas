package com.trailersaas.orders;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tenant-pinned synchronous fulfillment. Acquire deterministic Order locks before Trip locks. */
public interface OrderFulfillment {
  List<UUID> lockTripOrders(UUID tenant,UUID trip);
  List<UUID> lockDeliveryOrders(UUID tenant,UUID stop);
  void started(UUID tenant,List<UUID> lockedOrders);
  void delivered(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,UUID operation,
      Instant occurredAt,List<UUID> lockedOrders);
}
