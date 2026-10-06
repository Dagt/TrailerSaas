package com.trailersaas.identity;

import java.security.Principal;
import java.util.UUID;

/** Internal identity only. No Tenant, roles, scopes or capabilities. */
public record AuthenticatedUserPrincipal(UUID userId, String firebaseUid, String email, String displayName)
        implements Principal {
    @Override
    public String getName() { return userId.toString(); }

    @Override
    public String toString() { return "AuthenticatedUserPrincipal[userId=" + userId + "]"; }
}
