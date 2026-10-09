package com.trailersaas.orders.internal;

import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseOrderPlanningPolicy implements OrderPlanningPolicy {
  private final TenantAuthorization authorization;
  private final TenantActorMembership attribution;
  private final OrderRepository repository;
  private final OrderMutationService mutations;

  DatabaseOrderPlanningPolicy(
      TenantAuthorization authorization,
      TenantActorMembership attribution,
      OrderRepository repository,
      OrderMutationService mutations) {
    this.authorization = authorization;
    this.attribution = attribution;
    this.repository = repository;
    this.mutations = mutations;
  }

  public List<PlanningOrder> lockEligible(
      AuthenticatedUserPrincipal actor, UUID tenant, Collection<UUID> ids) {
    authorization.requireTenantCapability(actor, tenant, Capability.TRIP_PLAN);
    var result = new ArrayList<PlanningOrder>();
    for (var id : ids.stream().distinct().sorted().toList()) {
      OrderEntity order;
      try {
        order = repository.lockForMutation(tenant, id, new CustomerAccessScope(true, Set.of()));
      } catch (OrderFailure failure) {
        throw new OrderPlanningRejectedException(OrderPlanningRejectedException.Reason.MISSING);
      }
      if (order.status != OrderStatus.OPEN && order.status != OrderStatus.PLANNED) {
        throw new OrderPlanningRejectedException(OrderPlanningRejectedException.Reason.STATE);
      }
      result.add(new PlanningOrder(order.id, order.customerId, order.status.name(), order.version));
    }
    return List.copyOf(result);
  }

  public void planned(AuthenticatedUserPrincipal actor, UUID tenant, PlanningOrder projection) {
    authorization.requireTenantCapability(actor, tenant, Capability.TRIP_PLAN);
    var order =
        repository.lockForMutation(
            tenant, projection.orderId(), new CustomerAccessScope(true, Set.of()));
    OrderMutationService.expected(order, projection.version());
    if (order.status == OrderStatus.PLANNED) {
      return;
    }
    if (order.status != OrderStatus.OPEN) {
      throw new OrderPlanningRejectedException(OrderPlanningRejectedException.Reason.STATE);
    }
    mutations.transition(
        order,
        OrderLifecycleEvent.Action.ORDER_PLANNED,
        new OrderLifecycleEvent.Actor(
            "HUMAN_USER", "USER_ACTION", actor.userId(), attribution.forActor(actor, tenant)),
        null,
        null);
  }
}
