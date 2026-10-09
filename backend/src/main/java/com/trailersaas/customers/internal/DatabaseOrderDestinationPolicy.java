package com.trailersaas.customers.internal;

import java.util.UUID;
import com.trailersaas.customers.OrderDestinationPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseOrderDestinationPolicy implements OrderDestinationPolicy {
    private final EntityManager em;
    DatabaseOrderDestinationPolicy(EntityManager em) { this.em = em; }

    @Override public boolean customerExists(UUID tenantId, UUID customerId) {
        return !em.createQuery("select c.id from CustomerEntity c where c.tenantId=:tenant and c.id=:customer", UUID.class)
                .setParameter("tenant", tenantId).setParameter("customer", customerId).getResultList().isEmpty();
    }

    @Override public boolean compatible(UUID tenantId, UUID customerId, UUID siteId, UUID locationId) {
        // Same parent lock protocol as Customer-Site association removal; no permanent historical association FK.
        var sites = em.createNativeQuery("select id from physical_sites where tenant_id=:tenant and id=:site for share", UUID.class)
                .setParameter("tenant", tenantId).setParameter("site", siteId)
                .setFlushMode(FlushModeType.COMMIT).getResultList();
        if (sites.isEmpty()) { return false; }
        var association = em.createQuery("""
                select a.id from CustomerSiteEntity a where a.tenantId=:tenant and a.customerId=:customer and a.siteId=:site
                """, UUID.class).setParameter("tenant", tenantId).setParameter("customer", customerId)
                .setParameter("site", siteId).getResultList();
        if (association.isEmpty()) { return false; }
        if (locationId == null) { return true; }
        return !em.createQuery("""
                select l.id from OperationalLocationEntity l where l.tenantId=:tenant and l.siteId=:site and l.id=:location
                    and (l.customerId is null or l.customerId=:customer)
                """, UUID.class).setParameter("tenant", tenantId).setParameter("site", siteId)
                .setParameter("location", locationId).setParameter("customer", customerId).getResultList().isEmpty();
    }
}
