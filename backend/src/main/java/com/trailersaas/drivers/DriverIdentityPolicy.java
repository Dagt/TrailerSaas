package com.trailersaas.drivers;

import java.util.Optional;
import java.util.UUID;

/**
 * Fresh active operational identity; does not confer actor capability or arbitrary Driver browsing.
 */
public interface DriverIdentityPolicy {
  Optional<UUID> activeForUser(UUID tenantId, UUID authenticatedUserId);

  /** Pins the authenticated operational identity during a physical command transaction. */
  Optional<UUID> lockActiveForUser(UUID tenantId, UUID authenticatedUserId);

  /** Shift mutations serialize against execution's shared Driver lock. Trip parents precede this lock. */
  Optional<UUID> exclusiveActiveForUser(UUID tenantId, UUID authenticatedUserId);
}
