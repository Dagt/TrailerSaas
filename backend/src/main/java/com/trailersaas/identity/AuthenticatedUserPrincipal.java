package com.trailersaas.identity;

import java.security.Principal;
import java.util.UUID;

/** Internal identity only. No Tenant, roles, scopes or capabilities. */
public record AuthenticatedUserPrincipal(UUID userId, String firebaseUid, String email, String displayName,
        String authenticatedEmail, boolean authenticatedEmailVerified)
        implements Principal {
    // Stored email/displayName remain /me metadata. These extra fields describe
    // the current verified token only; never infer verification from stored email.
    public AuthenticatedUserPrincipal(UUID userId, String firebaseUid, String email, String displayName) {
        this(userId, firebaseUid, email, displayName, null, false);
    }
    @Override
    public String getName() { return userId.toString(); }

    @Override
    public String toString() { return "AuthenticatedUserPrincipal[userId=" + userId + "]"; }
}
