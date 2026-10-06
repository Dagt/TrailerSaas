package com.trailersaas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EnvironmentConfiguration.class)
            .withPropertyValues(
                    "DB_RUNTIME_JDBC_URL=jdbc:postgresql://localhost:5432/test_only",
                    "DB_RUNTIME_USERNAME=test_runtime", "DB_RUNTIME_PASSWORD=test-only",
                    "DB_MIGRATION_USERNAME=test_migration", "DB_MIGRATION_PASSWORD=test-only",
                    "FIREBASE_PROJECT_ID=test-only-project");

    @Test
    void defaultsToLoopbackLocalWithProjectConfigurationWithoutReadingProviderSecrets() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getEnvironment().getDefaultProfiles()).containsExactly("local");
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad\"; GRANT ALL TO PUBLIC;--", "has space", "a.b", "a-b", "é", "runtime'"})
    void rejectsUnsafeRoleIdentifiersWithoutEchoingThem(String role) {
        runner.withPropertyValues("DB_RUNTIME_USERNAME=" + role).run(context -> {
            assertThat(context).hasFailed().getFailure().hasMessageContaining("DB_RUNTIME_USERNAME");
            if (!role.isEmpty()) {
                assertThat(context.getStartupFailure()).hasMessageNotContaining(role);
            }
        });
    }

    @Test
    void rejectsOverriddenFlywayGrantRole() {
        runner.withPropertyValues("spring.flyway.placeholders.runtimeRole=other_role").run(context ->
                assertThat(context).hasFailed().getFailure().hasMessageContaining("must match"));
    }

    @Test
    void requiresFirebaseProjectAndSanitizesInvalidConfiguration() {
        runner.withPropertyValues("FIREBASE_PROJECT_ID=").run(context ->
                assertThat(context).hasFailed().getFailure().hasMessageContaining("FIREBASE_PROJECT_ID"));
        runner.withPropertyValues("FIREBASE_PROJECT_ID=synthetic-secret-firebase-token!").run(context ->
                assertThat(context).hasFailed().getFailure().hasMessageContaining("FIREBASE_PROJECT_ID")
                        .hasMessageNotContaining("synthetic-secret-firebase-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "staging", "production"})
    void acceptsExplicitEnvironment(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly(profile);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "staging,production", "local,production"})
    void rejectsUnknownOrMixedEnvironments(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context ->
                assertThat(context).hasFailed().getFailure().hasMessageContaining("SPRING_PROFILES_ACTIVE"));
    }

    @Test
    void cannotSelectProductionImplicitlyThroughDefaultProfiles() {
        runner.withPropertyValues("spring.profiles.default=production").run(context ->
                assertThat(context).hasFailed().getFailure().hasMessageContaining("default must be local"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "staging", "production"})
    void rejectsMissingCredentialsWithoutLeakingValues(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile, "DB_RUNTIME_PASSWORD=")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("DB_RUNTIME_PASSWORD is required").hasMessageNotContaining("test-only"));
    }

    @Test
    void rejectsMissingMigrationCredentials() {
        runner.withPropertyValues("DB_MIGRATION_PASSWORD=").run(context ->
                assertThat(context).hasFailed().getFailure().hasMessageContaining("DB_MIGRATION_PASSWORD is required"));
    }

    @Test
    void rejectsUnresolvedRequiredVariable() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(EnvironmentConfiguration.class)
                .withPropertyValues("spring.datasource.url=${UNSET_TEST_DB_URL}")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("DB_RUNTIME_JDBC_URL is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_RUNTIME_JDBC_URL", "DB_MIGRATION_JDBC_URL"})
    void localRejectsRemoteDatabaseEvenWithExplicitUrl(String variable) {
        runner.withPropertyValues(variable + "=jdbc:postgresql://db.production.example.test:5432/example")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("must use loopback").hasMessageNotContaining("db.production"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-url", "jdbc:h2:mem:test", "jdbc:postgresql://localhost",
            "jdbc:postgresql://user:test-only@localhost/test", "jdbc:postgresql://localhost/test?password=test-only",
            "jdbc:postgresql://localhost/test?%70assword=test-only"})
    void rejectsInvalidJdbcUrlWithoutDisclosingIt(String value) {
        runner.withPropertyValues("DB_RUNTIME_JDBC_URL=" + value).run(context ->
                assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("DB_RUNTIME_JDBC_URL").hasMessageNotContaining("test-only"));
    }

    @Test
    void acceptsNonSecretJdbcOptionsAndCaseInsensitiveLoopbackHost() {
        runner.withPropertyValues("DB_RUNTIME_JDBC_URL=jdbc:postgresql://LOCALHOST:5432/test_only?sslmode=disable")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void nonLocalUsesOnlyExplicitConfigurationWithoutFallback(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile,
                "DB_RUNTIME_JDBC_URL=jdbc:postgresql://db.example.test:5432/test_only",
                "DB_MIGRATION_JDBC_URL=jdbc:postgresql://migration.example.test:5432/test_only")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://db.example.test:5432/test_only");
                    assertThat(context.getEnvironment().getProperty("spring.flyway.url"))
                            .isEqualTo("jdbc:postgresql://migration.example.test:5432/test_only");
                });
    }
}
