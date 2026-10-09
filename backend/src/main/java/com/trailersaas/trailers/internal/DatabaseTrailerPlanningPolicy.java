package com.trailersaas.trailers.internal;

import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.TrailerAccessPolicy;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTrailerPlanningPolicy implements TrailerPlanningPolicy {
    private final EntityManager em;
    private final TrailerAccessPolicy access;
    DatabaseTrailerPlanningPolicy(EntityManager em, TrailerAccessPolicy access) { this.em = em; this.access = access; }
    @Override public Eligibility check(AuthenticatedUserPrincipal actor, UUID tenant, UUID id) {
        var scope = access.readScope(actor, tenant);
        var row = em.createQuery("select t from TrailerEntity t where t.tenantId=:tenant and t.id=:id", TrailerEntity.class)
                .setParameter("tenant", tenant).setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_READ)
                .getResultStream().findFirst();
        if (row.isEmpty()) { return Eligibility.NOT_FOUND; }
        var trailer = row.get();
        if (!scope.tenantWide() && (trailer.designatedCustomerId == null || !scope.customerIds().contains(trailer.designatedCustomerId))) { return Eligibility.CUSTOMER_SCOPE_DENIED; }
        return trailer.active && "IN_SERVICE".equals(trailer.serviceabilityState) ? Eligibility.ELIGIBLE : Eligibility.UNAVAILABLE;
    }
}
