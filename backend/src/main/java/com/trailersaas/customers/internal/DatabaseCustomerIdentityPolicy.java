package com.trailersaas.customers.internal;

import com.trailersaas.customers.CustomerIdentityPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseCustomerIdentityPolicy implements CustomerIdentityPolicy {
  private final EntityManager em;

  DatabaseCustomerIdentityPolicy(EntityManager em) {
    this.em = em;
  }

  public boolean exists(UUID tenant, UUID customer) {
    return !em.createNativeQuery(
            "select id from customers where tenant_id=:tenant and id=:customer for share",
            UUID.class)
        .setParameter("tenant", tenant)
        .setParameter("customer", customer)
        .setFlushMode(FlushModeType.COMMIT)
        .getResultList()
        .isEmpty();
  }
}
