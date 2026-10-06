package com.trailersaas.identity.internal;

import java.util.List;

import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.InvalidIdentityTokenException;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

final class FirebaseAuthenticationProvider implements AuthenticationProvider {
    private final FirebaseTokenVerifier verifier;
    private final InternalUserResolver users;

    FirebaseAuthenticationProvider(FirebaseTokenVerifier verifier, InternalUserResolver users) {
        this.verifier = verifier;
        this.users = users;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var bearer = (BearerTokenAuthenticationToken) authentication;
        VerifiedFirebaseIdentity identity;
        try {
            identity = verifier.verify(bearer.getToken());
        } catch (InvalidIdentityTokenException exception) {
            throw new BadCredentialsException("A valid authentication token is required.");
        }
        // Empty authorities by construction: claims never become business authorization.
        return UsernamePasswordAuthenticationToken.authenticated(users.resolve(identity), null, List.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
