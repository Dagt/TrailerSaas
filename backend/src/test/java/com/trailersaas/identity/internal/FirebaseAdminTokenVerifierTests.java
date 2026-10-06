package com.trailersaas.identity.internal;

import java.util.Map;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.trailersaas.identity.InvalidIdentityTokenException;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FirebaseAdminTokenVerifierTests {
    @Test
    void testBoundarySuppressesProductionAppAndCredentialsInitialization() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(com.trailersaas.TestFirebaseConfiguration.class, FirebaseVerifierConfiguration.class)
                .withPropertyValues("trailersaas.firebase.project-id=test-only-project")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(com.trailersaas.identity.FirebaseTokenVerifier.class);
                    assertThat(context).doesNotHaveBean(com.google.firebase.FirebaseApp.class);
                });
    }

    @Test
    void mapsOnlyVerifiedIdentityNeverBusinessClaimsOrEmailVerificationPolicy() throws Exception {
        var auth = mock(FirebaseAuth.class);
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("external-uid");
        when(token.getEmail()).thenReturn("identity@example.test");
        when(token.getName()).thenReturn("Identity Name");
        when(token.getClaims()).thenReturn(Map.of("role", "TENANT_ADMIN", "tenantId", "evil-tenant",
                "customerId", "evil-customer", "isAdmin", true, "email_verified", false));
        when(auth.verifyIdToken("synthetic-secret-firebase-token")).thenReturn(token);
        var identity = new FirebaseAdminTokenVerifier(auth).verify("synthetic-secret-firebase-token");
        assertThat(identity).isEqualTo(new VerifiedFirebaseIdentity("external-uid", "identity@example.test", "Identity Name"));
        verify(token, never()).getClaims();
        verify(token, never()).isEmailVerified();
        assertThat(identity.toString()).doesNotContain("external-uid", "identity@example.test");
        verify(auth).verifyIdToken("synthetic-secret-firebase-token");
    }

    @Test
    void sdkFailureDoesNotEscapeWithMessageOrCause() throws Exception {
        var auth = mock(FirebaseAuth.class);
        var failure = mock(FirebaseAuthException.class);
        when(failure.getMessage()).thenReturn("synthetic-secret-firebase-token issuer certificate password");
        when(auth.verifyIdToken(anyString())).thenThrow(failure);
        assertThatThrownBy(() -> new FirebaseAdminTokenVerifier(auth).verify("synthetic-secret-firebase-token"))
                .isInstanceOf(InvalidIdentityTokenException.class).hasNoCause()
                .hasMessage("A valid authentication token is required.");
    }

    @Test
    void malformedSdkInputIsAlsoSanitized() throws Exception {
        var auth = mock(FirebaseAuth.class);
        when(auth.verifyIdToken(anyString())).thenThrow(new IllegalArgumentException("synthetic-secret-firebase-token"));
        assertThatThrownBy(() -> new FirebaseAdminTokenVerifier(auth).verify("synthetic-secret-firebase-token"))
                .isInstanceOf(InvalidIdentityTokenException.class).hasNoCause()
                .hasMessageNotContaining("synthetic-secret-firebase-token");
    }
}
