package com.trailersaas.identity.internal;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.InvalidIdentityTokenException;
import com.trailersaas.identity.VerifiedFirebaseIdentity;

final class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {
    private final FirebaseAuth auth;

    FirebaseAdminTokenVerifier(FirebaseAuth auth) { this.auth = auth; }

    @Override
    public VerifiedFirebaseIdentity verify(String idToken) {
        try {
            // Signature, issuer, audience/project, expiry and ID-token structure are SDK-owned.
            // No revocation lookup or acceptance of Google access/custom tokens.
            return identity(auth.verifyIdToken(idToken));
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            throw new InvalidIdentityTokenException();
        }
    }

    static VerifiedFirebaseIdentity identity(FirebaseToken token) {
        return new VerifiedFirebaseIdentity(token.getUid(), token.getEmail(), token.getName());
    }
}
