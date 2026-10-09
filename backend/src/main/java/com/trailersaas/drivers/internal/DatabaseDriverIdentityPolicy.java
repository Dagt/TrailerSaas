package com.trailersaas.drivers.internal;

import com.trailersaas.drivers.DriverIdentityPolicy;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseDriverIdentityPolicy implements DriverIdentityPolicy {
  private final EntityManager em;

  DatabaseDriverIdentityPolicy(EntityManager em) {
    this.em = em;
  }

  public Optional<UUID> activeForUser(UUID tenant, UUID user) {
    return em.createQuery(
            "select d from DriverEntity d where d.tenantId=:tenant and d.userId=:user and"
                + " d.active=true",
            DriverEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("user", user)
        .getResultStream()
        .findFirst()
        .map(d -> d.id);
  }
}
