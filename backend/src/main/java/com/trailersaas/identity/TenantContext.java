package com.trailersaas.identity;

import java.util.UUID;

/**
 * Pins PostgreSQL RLS context on the current transaction, not on a thread/session.
 * This limits rows; it never grants capabilities or proves Tenant access.
 * A transaction cannot switch Tenant/User or mix the two modes.
 */
public interface TenantContext {
    void establishTenant(UUID tenantId);

    /** Bootstrap directory only: the principal must come from server authentication. */
    void establishBootstrapUser(AuthenticatedUserPrincipal principal);
}
