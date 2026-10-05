package com.trailersaas;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgresIntegrationTest {

    static final String RUNTIME_USERNAME = "trailersaas_runtime";
    private static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("trailersaas_test")
            .withUsername("trailersaas_migration")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            // This test-only password is a generated UUID, never external SQL input.
            statement.execute("CREATE ROLE " + RUNTIME_USERNAME
                    + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '"
                    + RUNTIME_PASSWORD + "'");
            statement.execute("GRANT CONNECT ON DATABASE trailersaas_test TO " + RUNTIME_USERNAME);
            statement.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USERNAME);
        }
        // Exercise the real environment-variable mapping, not alternate test YAML.
        registry.add("DB_RUNTIME_JDBC_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_RUNTIME_USERNAME", () -> RUNTIME_USERNAME);
        registry.add("DB_RUNTIME_PASSWORD", () -> RUNTIME_PASSWORD);
        registry.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
    }
}
