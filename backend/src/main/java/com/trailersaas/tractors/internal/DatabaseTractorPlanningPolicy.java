package com.trailersaas.tractors.internal;

import com.trailersaas.tractors.TractorPlanningPolicy;
import jakarta.persistence.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTractorPlanningPolicy implements TractorPlanningPolicy {
  private final EntityManager em;

  DatabaseTractorPlanningPolicy(EntityManager em) {
    this.em = em;
  }

  public Eligibility check(UUID tenant, UUID id) {
    var row =
        em.createQuery(
                "select e from TractorEntity e where e.tenantId=:tenant and e.id=:id",
                TractorEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("id", id)
            .setLockMode(LockModeType.PESSIMISTIC_READ)
            .getResultStream()
            .findFirst();
    return row.isEmpty()
        ? Eligibility.NOT_FOUND
        : row.get().active ? Eligibility.ELIGIBLE : Eligibility.INACTIVE;
  }
}
