package com.trailersaas.orders.internal;

import java.util.List;
import java.util.UUID;
import com.trailersaas.identity.CustomerAccessScope;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepository {
    private final EntityManager em;
    OrderRepository(EntityManager em) { this.em = em; }

    OrderEntity resource(UUID tenant, UUID id, CustomerAccessScope scope) {
        var q = em.createQuery("select o from OrderEntity o where o.tenantId=:tenant and o.id=:id"
                + (scope.tenantWide() ? "" : " and o.customerId in :customers"), OrderEntity.class)
                .setParameter("tenant", tenant).setParameter("id", id);
        if (!scope.tenantWide()) { q.setParameter("customers", scope.customerIds()); }
        return q.getResultStream().findFirst().orElseThrow(OrderFailure::missing);
    }

    List<OrderEntity> list(UUID tenant, CustomerAccessScope scope, OrderValidation.Filters filters) {
        var q = em.createQuery("select o from OrderEntity o where o.tenantId=:tenant"
                + (scope.tenantWide() ? "" : " and o.customerId in :customers")
                + (filters.customerId() == null ? "" : " and o.customerId=:customer")
                + (filters.status() == null ? "" : " and o.status=:status")
                + (filters.cursor() == null ? "" : " and (o.createdAt<:at or (o.createdAt=:at and o.id<:id))")
                + " order by o.createdAt desc, o.id desc", OrderEntity.class).setParameter("tenant", tenant);
        if (!scope.tenantWide()) { q.setParameter("customers", scope.customerIds()); }
        if (filters.customerId() != null) { q.setParameter("customer", filters.customerId()); }
        if (filters.status() != null) { q.setParameter("status", filters.status()); }
        if (filters.cursor() != null) { q.setParameter("at", filters.cursor().createdAt()).setParameter("id", filters.cursor().id()); }
        return q.setMaxResults(filters.limit() + 1).getResultList();
    }

    List<OrderCargoEntity> cargo(UUID tenant, UUID order) {
        return em.createQuery("select c from OrderCargoEntity c where c.tenantId=:tenant and c.orderId=:order order by c.createdAt, c.id", OrderCargoEntity.class)
                .setParameter("tenant", tenant).setParameter("order", order).getResultList();
    }
    List<OrderStopEntity> stops(UUID tenant, UUID order) {
        return em.createQuery("select s from OrderStopEntity s where s.tenantId=:tenant and s.orderId=:order order by s.sequenceNumber, s.id", OrderStopEntity.class)
                .setParameter("tenant", tenant).setParameter("order", order).getResultList();
    }
    boolean hasActiveSiteUse(UUID tenant, UUID customer, UUID site) {
        // Fresh statement after the caller acquires the exclusive Site lock.
        // Every accepted state except COMPLETED/CANCELLED keeps the association in active use.
        return !em.createQuery("""
                select o.id from OrderEntity o where o.tenantId=:tenant and o.customerId=:customer
                    and o.status not in :terminal
                    and exists (select s.id from OrderStopEntity s where s.tenantId=o.tenantId
                        and s.orderId=o.id and s.siteId=:site)
                """, UUID.class).setParameter("tenant", tenant).setParameter("customer", customer)
                .setParameter("site", site).setParameter("terminal", java.util.Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED))
                .setMaxResults(1).getResultList().isEmpty();
    }

    void insert(Object entity) { em.persist(entity); }
    void flush() { em.flush(); }
}
