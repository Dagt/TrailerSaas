package com.trailersaas.customers;

import java.util.Set;
import java.util.UUID;

/** Each serviced Customer must be compatible; no Orders means only a neutral Location is legal. */
public interface TripDestinationPolicy {
  /** Frozen same-Tenant Site under shared lock; never an authorization decision. */
  java.util.Optional<java.util.Map<String, Object>> snapshot(UUID tenant, UUID site);

  boolean compatible(UUID tenant, Set<UUID> customers, UUID site, UUID location);
}
