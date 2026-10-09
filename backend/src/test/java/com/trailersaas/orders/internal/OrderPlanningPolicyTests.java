package com.trailersaas.orders.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrderPlanningPolicyTests {
  final TenantAuthorization auth = mock(TenantAuthorization.class);
  final TenantActorMembership attribution = mock(TenantActorMembership.class);
  final OrderRepository repo = mock(OrderRepository.class);
  final OrderMutationService mutations = mock(OrderMutationService.class);
  final DatabaseOrderPlanningPolicy policy =
      new DatabaseOrderPlanningPolicy(auth, attribution, repo, mutations);
  final UUID tenant = UUID.randomUUID();
  final AuthenticatedUserPrincipal actor =
      new AuthenticatedUserPrincipal(UUID.randomUUID(), "fixture", null, null);

  OrderEntity order(UUID id, OrderStatus status) {
    var o = new OrderEntity();
    o.id = id;
    o.tenantId = tenant;
    o.customerId = UUID.randomUUID();
    o.status = status;
    when(repo.lockForMutation(eq(tenant), eq(id), any())).thenReturn(o);
    return o;
  }

  @Test
  void locksEveryOrderInOneDeterministicUuidOrderBeforeReturningProjections() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    ids.forEach(id -> order(id, OrderStatus.OPEN));
    var result = policy.lockEligible(actor, tenant, ids.reversed());
    assertThat(result)
        .extracting(OrderPlanningPolicy.PlanningOrder::orderId)
        .isEqualTo(ids.stream().sorted().toList());
    var ordered = inOrder(repo);
    for (var id : ids.stream().sorted().toList()) {
      ordered
          .verify(repo)
          .lockForMutation(eq(tenant), eq(id), eq(new CustomerAccessScope(true, Set.of())));
    }
    verify(auth).requireTenantCapability(actor, tenant, Capability.TRIP_PLAN);
    verifyNoInteractions(mutations);
  }

  @Test
  void plannedOrderDoesNotPublishFalseTransitionOrAdvanceVersion() {
    var id = UUID.randomUUID();
    var row = order(id, OrderStatus.PLANNED);
    row.version = 3;
    policy.planned(
        actor, tenant, new OrderPlanningPolicy.PlanningOrder(id, row.customerId, "PLANNED", 3));
    verifyNoInteractions(mutations);
    assertThat(row.version).isEqualTo(3);
  }

  @Test
  void openOrderUsesExistingMachineAndServerDerivedHumanAttribution() {
    var id = UUID.randomUUID();
    var row = order(id, OrderStatus.OPEN);
    var membership = UUID.randomUUID();
    when(attribution.forActor(actor, tenant)).thenReturn(membership);
    policy.planned(
        actor, tenant, new OrderPlanningPolicy.PlanningOrder(id, row.customerId, "OPEN", 0));
    verify(mutations)
        .transition(
            row,
            OrderLifecycleEvent.Action.ORDER_PLANNED,
            new OrderLifecycleEvent.Actor("HUMAN_USER", "USER_ACTION", actor.userId(), membership),
            null,
            null);
  }
}
