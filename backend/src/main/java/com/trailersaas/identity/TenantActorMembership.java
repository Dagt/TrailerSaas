package com.trailersaas.identity;

import java.util.UUID;

/** Fresh attribution after capability authorization; does not grant any capability. */
public interface TenantActorMembership {
    UUID forActor(AuthenticatedUserPrincipal actor, UUID tenantId);
}
