package com.trailersaas.customers;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-safe physical parent identity only; the enclosing workflow owns Customer authorization.
 */
public interface OperationalLocationDirectory {
  Optional<UUID> lockSiteId(UUID tenantId, UUID locationId);
}
