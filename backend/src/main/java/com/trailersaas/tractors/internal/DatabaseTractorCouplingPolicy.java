package com.trailersaas.tractors.internal;

import com.trailersaas.tractors.TractorCouplingPolicy;
import jakarta.persistence.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTractorCouplingPolicy implements TractorCouplingPolicy {
  private final EntityManager em;

  DatabaseTractorCouplingPolicy(EntityManager em) {
    this.em = em;
  }

  public boolean lock(UUID tenant, UUID id, boolean requireActive) {
    return em.createQuery(
            "select e from TractorEntity e where e.tenantId=:tenant and e.id=:id",
            TractorEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst()
        .filter(e -> !requireActive || e.active)
        .isPresent();
  }
}
