package com.trailersaas.identity.internal;

import java.io.IOException;
import java.util.UUID;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.trailersaas.identity.FirebaseTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(FirebaseTokenVerifier.class)
class FirebaseVerifierConfiguration {
    @Bean(destroyMethod = "delete")
    FirebaseApp identityFirebaseApp(@Value("${trailersaas.firebase.project-id}") String projectId) {
        // Firebase's emulator bypasses signature verification. It is not a production fallback.
        if (System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null) {
            throw new IllegalStateException("Firebase Authentication emulator is unsupported by this backend.");
        }
        try {
            var options = FirebaseOptions.builder().setProjectId(projectId)
                    .setCredentials(GoogleCredentials.getApplicationDefault()).build();
            return FirebaseApp.initializeApp(options, "trailersaas-identity-" + UUID.randomUUID());
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Firebase administrative credentials could not be initialized securely.");
        }
    }

    @Bean
    FirebaseTokenVerifier firebaseTokenVerifier(FirebaseApp identityFirebaseApp) {
        return new FirebaseAdminTokenVerifier(FirebaseAuth.getInstance(identityFirebaseApp));
    }
}
