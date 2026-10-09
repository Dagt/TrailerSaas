package com.trailersaas.customers;

import java.util.UUID;

/** Master-data integrity for an already authorized Order Customer, in the caller's Tenant transaction. */
public interface OrderDestinationPolicy {
    boolean customerExists(UUID tenantId, UUID customerId);
    /** Requires Customer/Site association; optional Location is neutral or matches that Customer. */
    boolean compatible(UUID tenantId, UUID customerId, UUID siteId, UUID locationId);
}
