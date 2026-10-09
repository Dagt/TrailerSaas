package com.trailersaas.identity;

import java.util.UUID;

/** Fresh Order read scope, exposing Customer IDs without internal role data. */
public interface OrderAccessPolicy {
    CustomerAccessScope readScope(AuthenticatedUserPrincipal principal, UUID tenantId);
}
