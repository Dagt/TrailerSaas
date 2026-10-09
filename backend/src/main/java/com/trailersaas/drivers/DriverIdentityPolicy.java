package com.trailersaas.drivers;

import java.util.Optional;
import java.util.UUID;

/**
 * Fresh active operational identity; does not confer actor capability or arbitrary Driver browsing.
 */
public interface DriverIdentityPolicy {
  Optional<UUID> activeForUser(UUID tenantId, UUID authenticatedUserId);
}
