package com.trailersaas.orders.internal;

import java.util.Set;
import java.util.UUID;
import com.trailersaas.identity.CustomerAccessScope;
import com.trailersaas.orders.*;
import com.trailersaas.orders.OrderLifecycleEvent.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(propagation = Propagation.MANDATORY)
class DatabaseOrderLifecyclePort implements OrderLifecyclePort {
    private final OrderRepository repository;
    private final OrderMutationService mutations;
    private final EntityManager em;
    DatabaseOrderLifecyclePort(OrderRepository repository, OrderMutationService mutations, EntityManager em) {
        this.repository = repository; this.mutations = mutations; this.em = em;
    }
    public Revision plan(UUID tenant, UUID order, long version) { return change(tenant, order, version, Action.ORDER_PLANNED); }
    public Revision startFulfillment(UUID tenant, UUID order, long version) { return change(tenant, order, version, Action.ORDER_FULFILLMENT_STARTED); }
    public Revision recordPartialDelivery(UUID tenant, UUID order, long version) { return change(tenant, order, version, Action.ORDER_PARTIALLY_DELIVERED); }
    public Revision resumeFulfillment(UUID tenant, UUID order, long version) { return change(tenant, order, version, Action.ORDER_FULFILLMENT_RESUMED); }
    public Revision deliver(UUID tenant, UUID order, long version) { return change(tenant, order, version, Action.ORDER_DELIVERED); }
    public Revision finishCancellation(UUID tenant, UUID order, long version) { return change(tenant, order, version, Action.ORDER_CANCELLATION_FINISHED); }
    private Revision change(UUID tenant, UUID id, long version, Action action) {
        // An integration port cannot establish/switch Tenant or borrow /me bootstrap visibility.
        boolean pinned = Boolean.TRUE.equals(em.createNativeQuery("""
                SELECT COALESCE(NULLIF(current_setting('app.current_tenant_id',true),'')::uuid = :tenant
                    AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL, false)
                """, Boolean.class).setParameter("tenant", tenant).getSingleResult());
        if (!pinned) { throw OrderFailure.missing(); }
        var order = repository.lockForMutation(tenant, id, new CustomerAccessScope(true, Set.of()));
        OrderMutationService.expected(order, version);
        mutations.transition(order, action, Actor.system(), null, null);
        return new Revision(order.id, order.status.name(), order.version, order.updatedAt);
    }
}
