package com.trailersaas.customers.internal;

import com.trailersaas.customers.OperationalLocationDirectory;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseOperationalLocationDirectory implements OperationalLocationDirectory {
  private final EntityManager em;

  DatabaseOperationalLocationDirectory(EntityManager em) {
    this.em = em;
  }

  public Optional<UUID> lockSiteId(UUID tenant, UUID location) {
    return em.createNativeQuery(
            "select site_id from operational_locations where tenant_id=:tenant and id=:location for"
                + " share",
            UUID.class)
        .setParameter("tenant", tenant)
        .setParameter("location", location)
        .setFlushMode(FlushModeType.COMMIT)
        .getResultStream()
        .map(UUID.class::cast)
        .findFirst();
  }
}
