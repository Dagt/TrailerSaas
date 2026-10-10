package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerMovementRejectedException.Reason.*;
import static com.trailersaas.trailers.internal.MovementRules.fail;

import com.trailersaas.trailers.TrailerProjectionReconciliation;
import jakarta.persistence.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTrailerProjectionReconciliation implements TrailerProjectionReconciliation {
  private final EntityManager em;
  private final DatabaseTrailerMovementProjection movements;

  DatabaseTrailerProjectionReconciliation(
      EntityManager em, DatabaseTrailerMovementProjection movements) {
    this.em = em;
    this.movements = movements;
  }

  public Comparison compare(UUID tenant, UUID trailer) {
    movements.pinned(tenant);
    // Shared lock prevents append/update between the current row and history reads.
    var t =
        em.createQuery(
                "select t from TrailerEntity t where t.tenantId=:tenant and t.id=:id",
                TrailerEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("id", trailer)
            .setLockMode(LockModeType.PESSIMISTIC_READ)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> fail(RESOURCE_NOT_FOUND));
    em.refresh(t, LockModeType.PESSIMISTIC_READ);
    var events =
        em.createQuery(
                "select e from TrailerMovementEntity e where e.tenantId=:tenant and"
                    + " e.trailerId=:trailer order by e.recordedAt,e.id",
                TrailerMovementEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("trailer", trailer)
            .getResultList();
    return MovementReconciliation.compare(events, DatabaseTrailerMovementProjection.state(t));
  }
}
