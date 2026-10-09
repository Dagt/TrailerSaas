package com.trailersaas.customers;

import java.util.Set;
import java.util.UUID;

/** Each serviced Customer must be compatible; no Orders means only a neutral Location is legal. */
public interface TripDestinationPolicy {
  boolean compatible(UUID tenant, Set<UUID> customers, UUID site, UUID location);
}
