package com.trailersaas.orders;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;

/** Transaction-bound Trip planning, not arbitrary Order status mutation. */
public interface OrderPlanningPolicy {
  record PlanningOrder(UUID orderId, UUID customerId, String status, long version) {}

  List<PlanningOrder> lockEligible(
      AuthenticatedUserPrincipal actor, UUID tenant, Collection<UUID> ids);

  void planned(AuthenticatedUserPrincipal actor, UUID tenant, PlanningOrder order);
}
