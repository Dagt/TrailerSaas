package com.trailersaas.customers.internal;

import com.trailersaas.customers.TripDestinationPolicy;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTripDestinationPolicy implements TripDestinationPolicy {
  private final EntityManager em;
  private final DatabaseOrderDestinationPolicy orders;

  DatabaseTripDestinationPolicy(EntityManager em, DatabaseOrderDestinationPolicy orders) {
    this.em = em;
    this.orders = orders;
  }

  public boolean compatible(UUID tenant, Set<UUID> customers, UUID site, UUID location) {
    if (!customers.isEmpty()) {
      return customers.stream()
          .sorted()
          .allMatch(c -> orders.compatible(tenant, c, site, location));
    }
    var found =
        em.createNativeQuery(
                "select id from physical_sites where tenant_id=:tenant and id=:site for share",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("site", site)
            .setFlushMode(FlushModeType.COMMIT)
            .getResultList();
    if (found.isEmpty()) {
      return false;
    }
    return location == null
        || !em.createQuery(
                "select l.id from OperationalLocationEntity l where l.tenantId=:tenant and"
                    + " l.siteId=:site and l.id=:location and l.customerId is null",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("site", site)
            .setParameter("location", location)
            .getResultList()
            .isEmpty();
  }
}
