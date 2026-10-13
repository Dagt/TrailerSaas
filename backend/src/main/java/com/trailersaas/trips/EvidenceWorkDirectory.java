package com.trailersaas.trips;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Fresh persisted resource facts, with Trip parent locks acquired before Driver/Document locks. */
public interface EvidenceWorkDirectory {
  Optional<Trip> trip(UUID tenant, UUID trip, boolean exclusive);
  Optional<Trip> readTrip(UUID tenant, UUID trip);
  /** Initial visibility only; callers must revalidate under the normal mutation locks. */
  Optional<Trip> readStopTrip(UUID tenant, UUID stop);
  /** Read-only parent identity check; never acquires a different Trip's lock. */
  boolean belongs(UUID tenant,UUID stop,UUID trip);
  Optional<Stop> stop(UUID tenant, UUID stop, boolean exclusive);
  record Trip(UUID id, UUID driverId, UUID tractorId, String status) {}
  record Stop(UUID id, Trip trip, String type, String status, long version, Instant completedAt,
      UUID completionActorId, boolean completionInCurrentTransaction) {}
}
