package com.trailersaas.trips.internal;

import com.trailersaas.customers.TripDestinationPolicy;
import com.trailersaas.trailers.TrailerCouplingTransactions.TripContext;
import com.trailersaas.trips.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTripCouplingExecutionPolicy implements TripCouplingExecutionPolicy {
  private final TripRepository repository;
  private final TripTrailerExpectationPolicy expectations;
  private final TripDestinationPolicy destinations;

  DatabaseTripCouplingExecutionPolicy(
      TripRepository repository,
      TripTrailerExpectationPolicy expectations,
      TripDestinationPolicy destinations) {
    this.repository = repository;
    this.expectations = expectations;
    this.destinations = destinations;
  }

  public TripContext lock(UUID tenant, UUID trip, UUID requestedTrailer) {
    repository.requirePinnedTenant(tenant);
    var t = repository.resource(tenant, trip, true);
    var expectation =
        expectations.inspect(tenant, trip, requestedTrailer).orElseThrow(TripFailure::missing);
    return new TripContext(
        t.id,
        t.status,
        t.version,
        t.currentDriverId,
        t.currentTractorId,
        expectation.expectedTrailerId());
  }

  public void validateDropDestination(UUID tenant, UUID trip, UUID stop, UUID location) {
    repository.requirePinnedTenant(tenant);
    // This API is internal orchestration, not arbitrary caller-supplied Trip authorization.
    repository.resource(tenant, trip, true);
    var s = repository.lockStop(tenant, trip, stop);
    if (s.siteId == null
        || !destinations.compatible(
            tenant, repository.stopCustomers(tenant, trip, stop), s.siteId, location))
      throw TripFailure.conflict();
  }
}
