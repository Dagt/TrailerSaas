package com.trailersaas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.hikari.validation-timeout=500",
        "spring.datasource.hikari.minimum-idle=0"})
@Testcontainers
@Import(TestFirebaseConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresReadinessTests {
    // Dedicated lifecycle: stopping this fixture cannot destroy another class's database.
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("readiness_test")
            .withUsername("readiness_fixture")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_RUNTIME_JDBC_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_RUNTIME_USERNAME", POSTGRES::getUsername);
        registry.add("DB_RUNTIME_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
        registry.add("FIREBASE_PROJECT_ID", () -> "test-only-project");
    }

    @Value("${local.server.port}")
    private int port;

    @Test
    void databaseLossChangesReadinessButNotLivenessAndNeverExposesDetails() throws Exception {
        assertHealth("/actuator/health/readiness", 200, "UP");
        assertHealth("/actuator/health/liveness", 200, "UP");
        POSTGRES.stop();
        assertHealth("/actuator/health/readiness", 503, "DOWN");
        assertHealth("/actuator/health/liveness", 200, "UP");
    }

    private void assertHealth(String path, int code, String status) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(code);
            assertThat(response.body()).isEqualTo("{\"status\":\"" + status + "\"}");
            assertThat(response.headers().firstValue(RequestCorrelationFilter.HEADER)).isPresent();
        }
    }
}
