package com.trailersaas.drivers.internal;

import com.trailersaas.drivers.DriverPlanningPolicy;
import jakarta.persistence.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseDriverPlanningPolicy implements DriverPlanningPolicy {
  private final EntityManager em;

  DatabaseDriverPlanningPolicy(EntityManager em) {
    this.em = em;
  }

  public Eligibility check(UUID tenant, UUID id) {
    var row =
        em.createQuery(
                "select e from DriverEntity e where e.tenantId=:tenant and e.id=:id",
                DriverEntity.class)
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
