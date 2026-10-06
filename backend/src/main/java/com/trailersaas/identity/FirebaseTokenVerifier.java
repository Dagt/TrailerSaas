package com.trailersaas.identity;

/** Production verifies with Firebase Admin; tests replace only this external boundary. */
public interface FirebaseTokenVerifier {
    VerifiedFirebaseIdentity verify(String idToken);
}
