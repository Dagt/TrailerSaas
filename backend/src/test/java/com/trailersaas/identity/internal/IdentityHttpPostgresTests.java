package com.trailersaas.identity.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseToken;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.InvalidIdentityTokenException;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
class IdentityHttpPostgresTests extends PostgresIntegrationTest {
    @Value("${local.server.port}") int port;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Autowired DataSource database;
    @Autowired ApplicationContext context;

    @Test
    void authenticationCreatesThenReusesOnlyGlobalUser(CapturedOutput output) throws Exception {
        String uid = "http-" + UUID.randomUUID();
        when(verifier.verify("synthetic-secret-firebase-token"))
                .thenReturn(new VerifiedFirebaseIdentity(uid, "user@example.test", "Jane Smith"));
        assertThat(context.getBeansOfType(FirebaseApp.class)).isEmpty(); // No ADC/network in CI.
        var first = request("/api/v1/me", "synthetic-secret-firebase-token", "first-login");
        var second = request("/api/v1/me", "synthetic-secret-firebase-token", "second-login");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(first.body(), "$.userId")).isEqualTo(JsonPath.read(second.body(), "$.userId"));
        assertThat(JsonPath.<String>read(first.body(), "$.email")).isEqualTo("user@example.test");
        assertThat(JsonPath.<String>read(first.body(), "$.displayName")).isEqualTo("Jane Smith");
        assertThat(JsonPath.<java.util.List<?>>read(first.body(), "$.memberships")).isEmpty();
        assertThat(first.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(request("/api/v1/me", null, "no-session").statusCode()).isEqualTo(401);
        try (var connection = database.getConnection(); var query = connection.prepareStatement("SELECT count(*) FROM users WHERE firebase_uid=?")) {
            query.setString(1, uid);
            try (var rows = query.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(1); }
        }
        assertThat(output.getAll()).doesNotContain("synthetic-secret-firebase-token");
    }

    @Test
    void failuresRemainPrivateAndCorrelationMatchesRealHttpLog(CapturedOutput output) throws Exception {
        when(verifier.verify(anyString())).thenThrow(new InvalidIdentityTokenException());
        var response = request("/api/v1/me", "synthetic-secret-firebase-token", "unauthorized-correlation");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("unauthorized-correlation");
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("AUTHENTICATION_REQUIRED");
        String trace = JsonPath.read(response.body(), "$.traceId");
        assertThat(trace).matches("[a-f0-9]{32}");
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(output.getOut().lines().filter(line -> line.startsWith("{") && line.contains("HTTP request completed"))
                        .map(line -> JsonPath.<Map<String, Object>>read(line, "$")))
                        .anySatisfy(log -> assertThat(log).containsEntry("requestId", "unauthorized-correlation")
                                .containsEntry("traceId", trace).containsEntry("http.status", 401)));
        assertThat(response.body()).doesNotContain("synthetic-secret-firebase-token", "Exception", "issuer", "certificate");
        assertThat(output.getAll()).doesNotContain("synthetic-secret-firebase-token");
        for (String probe : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            assertThat(request(probe, null, "public-probe").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void customBusinessClaimsCannotCreateMembershipsOrAuthorities() throws Exception {
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("claims-attack-" + UUID.randomUUID());
        when(token.getClaims()).thenReturn(Map.of("role", "TENANT_ADMIN", "roles", java.util.List.of("DRIVER"),
                "tenantId", UUID.randomUUID().toString(), "customerId", UUID.randomUUID().toString(), "isAdmin", true));
        var identity = FirebaseAdminTokenVerifier.identity(token);
        when(verifier.verify("claims-attack")).thenReturn(identity);
        var response = request("/api/v1/me", "claims-attack", "claims-test");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.memberships")).isEmpty();
        assertThat(response.body()).doesNotContain("TENANT_ADMIN", "tenantId", "customerId", "isAdmin");
        verify(token, never()).getClaims();
        var provider = new FirebaseAuthenticationProvider(verifier, context.getBean(InternalUserResolver.class));
        var authentication = provider.authenticate(new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken("claims-attack"));
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(com.trailersaas.identity.AuthenticatedUserPrincipal.class);
        assertThat(com.trailersaas.identity.AuthenticatedUserPrincipal.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("userId", "firebaseUid", "email", "displayName", "authenticatedEmail", "authenticatedEmailVerified");
    }

    private HttpResponse<String> request(String path, String token, String requestId) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("X-Request-Id", requestId).GET();
            if (token != null) { builder.header("Authorization", "Bearer " + token); }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
