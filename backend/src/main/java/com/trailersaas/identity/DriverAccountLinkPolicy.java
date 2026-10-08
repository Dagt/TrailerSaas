package com.trailersaas.identity;

import java.util.UUID;

/** Target eligibility only, not actor authorization. Requires an already authorized Tenant transaction. */
public interface DriverAccountLinkPolicy {
    /** Fresh ACTIVE membership + TENANT DRIVER role; never creates access or accepts token claims. */
    void requireEligible(UUID tenantId, UUID targetUserId);
}
