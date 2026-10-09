package com.trailersaas.trips;

import com.trailersaas.trailers.TrailerCouplingTransactions.TripContext;
import java.util.UUID;

/**
 * Trips-owned physical execution context, with an exclusive Trip parent lock until commit. Consumed
 * inside Trips orchestration; Trailers never depends back on Trips.
 */
public interface TripCouplingExecutionPolicy {
  TripContext lock(UUID tenant, UUID trip, UUID requestedTrailer);

  void validateDropDestination(UUID tenant, UUID trip, UUID stop, UUID location);
}
