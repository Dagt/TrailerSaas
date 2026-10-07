package com.trailersaas.identity.internal;

import java.util.UUID;

import com.trailersaas.RequestCorrelationFilter;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.InvalidIdentityTokenException;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(IdentitySecurityTests.Configuration.class)
@WebAppConfiguration
@ExtendWith(OutputCaptureExtension.class)
class IdentitySecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired FirebaseTokenVerifier verifier;
    @Autowired InternalUserResolver resolver;
    @Autowired MembershipDirectory directory;
    MockMvc mvc;
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        reset(verifier, resolver, directory);
        when(directory.forUser(any())).thenReturn(java.util.List.of());
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestCorrelationFilter()).apply(springSecurity()).build();
    }

    @Test
    void missingTokenIsCorrelatedSafeProblem(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/v1/me").header("X-Request-Id", "missing-token"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-Id", "missing-token"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value("missing-token"))
                .andExpect(jsonPath("$.detail").value("A valid authentication token is required."));
        verifyNoInteractions(verifier, resolver, directory);
        assertThat(output.getAll()).doesNotContain("synthetic-secret-firebase-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "expired", "wrong-issuer", "wrong-audience", "synthetic-secret-firebase-token"})
    void invalidTokenReasonsAllReturnSameSafe401(String token, CapturedOutput output) throws Exception {
        when(verifier.verify(token)).thenThrow(new InvalidIdentityTokenException());
        var response = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "failed-auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value("failed-auth"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("synthetic-secret-firebase-token", "Exception", "certificate");
        assertThat(output.getAll()).doesNotContain("synthetic-secret-firebase-token");
        verifyNoInteractions(resolver, directory);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedBearerHeaderDoesNotReachVerifier() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer token with spaces"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(verifier, resolver);
    }

    @Test
    void validIdentityReturnsTypedInternalUserWithoutSessionOrLeakedContext(CapturedOutput output) throws Exception {
        var identity = new VerifiedFirebaseIdentity("verified-uid", "name@example.test", "Name");
        when(verifier.verify("synthetic-secret-firebase-token")).thenReturn(identity);
        when(resolver.resolve(identity)).thenReturn(new AuthenticatedUserPrincipal(userId, identity.uid(), identity.email(), identity.displayName()));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer synthetic-secret-firebase-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("name@example.test"))
                .andExpect(jsonPath("$.displayName").value("Name"))
                .andExpect(jsonPath("$.memberships").isEmpty()).andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(directory).forUser(new AuthenticatedUserPrincipal(userId, identity.uid(), identity.email(), identity.displayName()));
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        assertThat(output.getAll()).doesNotContain("synthetic-secret-firebase-token");
    }

    @Test
    void apiIsProtectedByDefaultAndNonApiNotFoundStays404() throws Exception {
        mvc.perform(get("/api/v1/not-implemented")).andExpect(status().isUnauthorized());
        mvc.perform(get("/not-found")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound()); // No Actuator in this unit context; not 401.
    }

    @Test
    void verifiedCustomClaimsAttackStillProducesNoAuthorityOrTenantContext() throws Exception {
        var verified = mock(com.google.firebase.auth.FirebaseToken.class);
        when(verified.getUid()).thenReturn("claims-attack-user");
        when(verified.getClaims()).thenReturn(java.util.Map.of("role", "TENANT_ADMIN", "tenantId", "evil-tenant",
                "customerId", "evil-customer", "isAdmin", true, "capabilities", java.util.List.of("USER_MANAGE")));
        var identity = FirebaseAdminTokenVerifier.identity(verified);
        when(verifier.verify("claims-attack")).thenReturn(identity);
        when(resolver.resolve(identity)).thenReturn(new AuthenticatedUserPrincipal(userId, identity.uid(), null, null));
        var authentication = new FirebaseAuthenticationProvider(verifier, resolver).authenticate(
                new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken("claims-attack"));
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(AuthenticatedUserPrincipal.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("userId", "firebaseUid", "email", "displayName", "authenticatedEmail", "authenticatedEmailVerified");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer claims-attack"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberships").isEmpty());
        verify(verified, never()).getClaims();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({IdentitySecurityConfiguration.class, CurrentUserController.class})
    static class Configuration {
        @Bean FirebaseTokenVerifier verifier() { return mock(FirebaseTokenVerifier.class); }
        @Bean InternalUserResolver resolver() { return mock(InternalUserResolver.class); }
        @Bean MembershipDirectory directory() { return mock(MembershipDirectory.class); }
        @Bean JsonMapper mapper() {
            return JsonMapper.builder().addMixIn(org.springframework.http.ProblemDetail.class,
                    org.springframework.http.converter.json.ProblemDetailJacksonMixin.class).build();
        }
    }
}
