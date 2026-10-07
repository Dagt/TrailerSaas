package com.trailersaas.identity;

/** Allowlisted identity metadata from a verified ID token; never arbitrary claims. */
public record VerifiedFirebaseIdentity(String uid, String email, String displayName, boolean emailVerified) {
    public VerifiedFirebaseIdentity(String uid, String email, String displayName) {
        this(uid, email, displayName, false);
    }
    public VerifiedFirebaseIdentity {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("Verified identity requires a UID.");
        }
    }

    @Override
    public String toString() { return "VerifiedFirebaseIdentity[redacted]"; }
}
