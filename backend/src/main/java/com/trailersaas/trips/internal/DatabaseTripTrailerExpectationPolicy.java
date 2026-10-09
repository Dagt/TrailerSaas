package com.trailersaas.trips.internal;

import com.trailersaas.trips.TripTrailerExpectationPolicy;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTripTrailerExpectationPolicy implements TripTrailerExpectationPolicy {
  private final EntityManager em;

  DatabaseTripTrailerExpectationPolicy(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<Expectation> inspect(UUID tenant, UUID trip, UUID scanned) {
    Objects.requireNonNull(scanned);
    return em.createQuery(
            "select t from TripEntity t where t.tenantId=:tenant and t.id=:trip", TripEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .setLockMode(LockModeType.PESSIMISTIC_READ)
        .getResultStream()
        .findFirst()
        .map(
            t ->
                new Expectation(
                    t.plannedInitialTrailerId, scanned.equals(t.plannedInitialTrailerId)));
  }
}
