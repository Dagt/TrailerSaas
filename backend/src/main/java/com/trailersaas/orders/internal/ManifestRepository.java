package com.trailersaas.orders.internal;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
class ManifestRepository {
    private final EntityManager em;
    ManifestRepository(EntityManager em) { this.em = em; }
    LoadManifestEntity lock(UUID tenant, UUID id) {
        return em.createQuery("select m from LoadManifestEntity m where m.tenantId=:tenant and m.id=:id", LoadManifestEntity.class)
                .setParameter("tenant", tenant).setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream().findFirst().orElseThrow(OrderFailure::missing);
    }
    ManifestOrderEntity allocation(UUID tenant, UUID manifest, UUID order) {
        return em.createQuery("select a from ManifestOrderEntity a where a.tenantId=:tenant and a.manifestId=:manifest and a.orderId=:order", ManifestOrderEntity.class)
                .setParameter("tenant", tenant).setParameter("manifest", manifest).setParameter("order", order)
                .getResultStream().findFirst().orElse(null);
    }
    void insert(Object entity) { em.persist(entity); }
    void remove(ManifestOrderEntity allocation) { em.remove(allocation); }
    void flush() { em.flush(); }
}
