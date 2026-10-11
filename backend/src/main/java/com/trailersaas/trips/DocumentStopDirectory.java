package com.trailersaas.trips;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fresh, Tenant-pinned resource facts for attachment authorization, never JPA entities. */
public interface DocumentStopDirectory {
  Optional<Stop> find(UUID tenant, UUID stopId, boolean lockParent);

  record Stop(UUID id, UUID tripId, UUID driverId, Set<UUID> customerIds) {
    public Stop { customerIds = Set.copyOf(customerIds); }
  }
}
