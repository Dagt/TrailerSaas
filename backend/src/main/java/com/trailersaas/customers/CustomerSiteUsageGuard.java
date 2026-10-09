package com.trailersaas.customers;

import java.util.UUID;

/**
 * SPI for domains using a Customer-Site association. Called in the removal
 * transaction after Customers holds the Site's exclusive parent lock.
 * Implementations must read fresh, Tenant-scoped active use without changing history.
 */
public interface CustomerSiteUsageGuard {
    boolean hasActiveUse(UUID tenantId, UUID customerId, UUID siteId);
}
