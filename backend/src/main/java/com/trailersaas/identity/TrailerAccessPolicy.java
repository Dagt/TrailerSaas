package com.trailersaas.identity;

import java.util.UUID;

/** Fresh Trailer scope, with no role names exposed to the consuming module. */
public interface TrailerAccessPolicy {
    CustomerAccessScope readScope(AuthenticatedUserPrincipal principal, UUID tenantId);

    /** Identification only: never authorizes a physical-state command. Denial returns false. */
    boolean mayResolveQr(AuthenticatedUserPrincipal principal, UUID tenantId, UUID designatedCustomerId);
}
