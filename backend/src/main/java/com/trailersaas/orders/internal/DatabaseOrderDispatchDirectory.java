package com.trailersaas.orders.internal;

import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
class DatabaseOrderDispatchDirectory implements OrderDispatchDirectory {
  private final TenantAuthorization authorization;
  private final OrderPlanningDirectory planning;
  private final OrderRepository repository;

  DatabaseOrderDispatchDirectory(
      TenantAuthorization authorization,
      OrderPlanningDirectory planning,
      OrderRepository repository) {
    this.authorization = authorization;
    this.planning = planning;
    this.repository = repository;
  }

  public List<Pending> pending(AuthenticatedUserPrincipal actor, UUID tenant, UUID customer) {
    authorization.requireTenantCapability(actor, tenant, Capability.ORDER_VIEW_ALL);
    var scope = new CustomerAccessScope(true, Set.of());
    var ids =
        planning.page(
            new OrderPlanningDirectory.Query(tenant, scope, customer, null, null, null, 20, true));
    if (ids.isEmpty()) return List.of();
    return repository.byPlanningIds(tenant, scope, ids).stream()
        .map(
            o ->
                new Pending(
                    o.id,
                    o.customerId,
                    o.orderNumber,
                    o.status.name(),
                    o.version,
                    o.createdAt,
                    o.updatedAt))
        .toList();
  }
}
