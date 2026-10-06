package com.trailersaas.identity;

import java.util.UUID;

/**
 * Fresh database-backed authorization. A successful check is not a domain-state
 * decision or a substitute for tenant-safe resource lookup.
 */
public interface TenantAuthorization {
    /** Customer-scoped grants cannot satisfy a Tenant-wide check. */
    void requireTenantCapability(AuthenticatedUserPrincipal principal, UUID tenantId, Capability capability);

    /**
     * The consumer must derive the Customer target from tenant-scoped resource
     * data, not trust a client-supplied Customer hint. A target is mandatory.
     */
    void requireCustomerCapability(AuthenticatedUserPrincipal principal, UUID tenantId,
            UUID customerId, Capability capability);

    /**
     * Only TRIP_VIEW_ASSIGNED and TRIP_EXECUTE_ASSIGNED are supported here.
     * The domain service must derive assignmentSatisfied server-side. These
     * capabilities cannot be authorized through the other two methods.
     */
    void requireAssignedWorkCapability(AuthenticatedUserPrincipal principal, UUID tenantId,
            Capability capability, boolean assignmentSatisfied);
}
