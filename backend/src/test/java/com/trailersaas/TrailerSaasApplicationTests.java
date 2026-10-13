package com.trailersaas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrailerSaasApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Environment environment;

    @Value("${local.server.port}")
    private int port;

    @Test
    void defaultsToLocalWithTestOnlyFirebaseBoundary() {
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("local");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void connectsToRealPostgres18WithRestrictedRuntimeCredentials() throws Exception {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName("org.h2.Driver"));
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            var metadata = connection.getMetaData();
            assertThat(metadata.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(metadata.getDatabaseMajorVersion()).isEqualTo(18);
            assertThat(metadata.getUserName()).isEqualTo(RUNTIME_USERNAME);
            try (var roles = statement.executeQuery(
                    "SELECT rolsuper, rolbypassrls, has_schema_privilege(current_user, 'public', 'CREATE') "
                            + "FROM pg_roles WHERE rolname = current_user")) {
                assertThat(roles.next()).isTrue();
                assertThat(roles.getBoolean(1)).isFalse();
                assertThat(roles.getBoolean(2)).isFalse();
                assertThat(roles.getBoolean(3)).isFalse();
            }
        }
    }

    @Test
    void flywayMigratesAndValidatesUsingSeparateCredentials() throws Exception {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("23");
        assertThat(flyway.info().current().getDescription()).isEqualTo("yard reports oos evidence");
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getUserName()).isEqualTo(POSTGRES.getUsername())
                    .isNotEqualTo(RUNTIME_USERNAME);
            assertThat(connection.getMetaData().getURL())
                    .isEqualTo(environment.getProperty("spring.datasource.url"));
            try (var history = statement.executeQuery(
                    "SELECT installed_by, success FROM flyway_schema_history WHERE version = '1'")) {
                assertThat(history.next()).isTrue();
                assertThat(history.getString(1)).isEqualTo(POSTGRES.getUsername());
                assertThat(history.getBoolean(2)).isTrue();
                assertThat(history.next()).isFalse();
            }
        }
    }

    @Test
    void jpaValidatesOnlyTheFlywayCreatedIdentityEntities() throws Exception {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(27)
                .extracting(jakarta.persistence.metamodel.EntityType::getName)
                .contains("TrailerMovementEntity", "TrailerCouplingEntity", "ProcessedClientOperationEntity");
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        try (var entityManager = entityManagerFactory.createEntityManager()) {
            assertThat(entityManager.createNativeQuery("SELECT current_user").getSingleResult())
                    .isEqualTo(RUNTIME_USERNAME);
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var tables = statement.executeQuery(
                        "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = 'public'")) {
            var names = new java.util.ArrayList<String>();
            while (tables.next()) { names.add(tables.getString(1)); }
            assertThat(names).containsExactlyInAnyOrder("flyway_schema_history", "users",
                    "tenants", "tenant_memberships", "role_assignments", "customers", "physical_sites", "customer_sites", "operational_locations",
                    "membership_invitations", "invitation_role_assignments", "drivers", "tractors", "trailers", "trailer_qr_identities", "tenant_settings", "orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders", "trips", "trip_stops", "trip_stop_orders", "trip_assignment_events", "trip_operational_events", "trip_driver_updates", "trailer_movement_events", "trailer_couplings", "processed_client_operations", "trip_stop_events", "documents", "document_links", "delivery_evidence", "shifts", "shift_corrections", "inspections", "fuel_events", "operational_exceptions", "operational_exception_documents", "order_fulfillment_events", "yard_discrepancy_reports", "trailer_oos_evidence");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void startsWithLocalProfileAndServesHealth(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
            assertThat(response.body()).doesNotContain("\"components\"", "\"details\"");
        }
    }
}
