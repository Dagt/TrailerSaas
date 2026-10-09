package com.trailersaas.trips;

import java.util.Optional;
import java.util.UUID;

/**
 * Planning expectation only. The future Hook caller owns assignment, capability and physical-state
 * authorization. Requires its already-established runtime Tenant transaction; never creates
 * coupling.
 */
public interface TripTrailerExpectationPolicy {
  record Expectation(UUID expectedTrailerId, boolean matches) {}

  /**
   * A shared Trip lock pins this fresh expectation until the caller's transaction completes. An
   * unknown expected Trailer does not approve any scanned Trailer.
   */
  Optional<Expectation> inspect(UUID tenantId, UUID tripId, UUID scannedTrailerId);
}
