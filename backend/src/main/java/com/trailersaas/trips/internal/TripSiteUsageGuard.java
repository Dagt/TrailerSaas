package com.trailersaas.trips.internal;

import com.trailersaas.customers.CustomerSiteUsageGuard;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
class TripSiteUsageGuard implements CustomerSiteUsageGuard {
  private final EntityManager em;

  TripSiteUsageGuard(EntityManager em) {
    this.em = em;
  }

  public boolean hasActiveUse(UUID tenant, UUID customer, UUID site) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM trips t JOIN trip_stops s ON s.tenant_id=t.tenant_id AND s.trip_id=t.id
            JOIN trip_stop_orders a ON a.tenant_id=s.tenant_id AND a.trip_stop_id=s.id
            JOIN orders o ON o.tenant_id=a.tenant_id AND o.id=a.order_id
            WHERE t.tenant_id=:tenant AND s.site_id=:site AND o.customer_id=:customer
                AND t.status IN ('PLANNED','ASSIGNED','IN_TRANSIT','AT_STOP','CANCELLING') LIMIT 1
            """,
            Integer.class)
        .setParameter("tenant", tenant)
        .setParameter("customer", customer)
        .setParameter("site", site)
        .getResultList()
        .isEmpty();
  }
}
