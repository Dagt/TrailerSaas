package com.trailersaas;

import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.InvalidIdentityTokenException;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Explicit test-only boundary: no Firebase app, ADC, certificate or network access. */
@TestConfiguration(proxyBeanMethods = false)
public class TestFirebaseConfiguration {
    @Bean
    FirebaseTokenVerifier testFirebaseTokenVerifier() {
        return token -> {
            if ("synthetic-secret-token".equals(token)) {
                return new VerifiedFirebaseIdentity("observability-fixture", null, null);
            }
            throw new InvalidIdentityTokenException();
        };
    }
}
