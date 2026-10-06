package com.trailersaas;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration(proxyBeanMethods = false)
class EnvironmentConfiguration {

    @Bean
    static BeanFactoryPostProcessor validateEnvironment(ConfigurableEnvironment environment) {
        // Validate after Spring resolves configuration, before any datasource opens.
        return beanFactory -> validate(environment);
    }

    static void validate(ConfigurableEnvironment environment) {
        var active = environment.getActiveProfiles();
        var profiles = active.length == 0 ? environment.getDefaultProfiles() : active;
        if (profiles.length != 1 || !Set.of("local", "staging", "production").contains(profiles[0])
                || (active.length == 0 && !"local".equals(profiles[0]))) {
            throw new IllegalStateException(
                    "Select exactly one SPRING_PROFILES_ACTIVE: local, staging or production; default must be local.");
        }
        boolean local = "local".equals(profiles[0]);
        validateJdbc(required(environment, "spring.datasource.url", "DB_RUNTIME_JDBC_URL"),
                "DB_RUNTIME_JDBC_URL", local);
        String runtimeRole = required(environment, "spring.datasource.username", "DB_RUNTIME_USERNAME");
        String grantRole = required(environment, "spring.flyway.placeholders.runtimeRole", "DB_RUNTIME_USERNAME");
        if (!runtimeRole.matches("[A-Za-z_][A-Za-z0-9_]{0,62}") || !runtimeRole.equals(grantRole)) {
            throw new IllegalStateException("DB_RUNTIME_USERNAME must be a 1-63 character ASCII role identifier; Flyway grant role must match it.");
        }
        required(environment, "spring.datasource.password", "DB_RUNTIME_PASSWORD");
        validateJdbc(required(environment, "spring.flyway.url", "DB_MIGRATION_JDBC_URL / DB_RUNTIME_JDBC_URL"),
                "DB_MIGRATION_JDBC_URL", local);
        required(environment, "spring.flyway.user", "DB_MIGRATION_USERNAME");
        required(environment, "spring.flyway.password", "DB_MIGRATION_PASSWORD");
        String project = required(environment, "trailersaas.firebase.project-id", "FIREBASE_PROJECT_ID");
        if (!project.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]")) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID must be a valid project identifier.");
        }
    }

    private static String required(ConfigurableEnvironment environment, String property, String variable) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException exception) {
            // Never attach a resolver exception that could disclose a supplied value.
            throw new IllegalStateException(variable + " is required; supply it securely.");
        }
        if (value == null || value.isBlank() || value.contains("<set-locally>")) {
            throw new IllegalStateException(variable + " is required; supply it securely.");
        }
        return value;
    }

    private static void validateJdbc(String value, String variable, boolean local) {
        URI uri;
        boolean embeddedCredentials;
        try {
            if (!value.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }
            uri = URI.create(value.substring("jdbc:".length()));
            embeddedCredentials = uri.getRawQuery() != null
                    && Arrays.stream(uri.getRawQuery().split("&"))
                            .map(parameter -> parameter.split("=", 2)[0])
                            .map(name -> URLDecoder.decode(name, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))
                            .anyMatch(Set.of("user", "password", "sslpassword")::contains);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(variable + " must be an absolute PostgreSQL JDBC URL.");
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2
                || uri.getUserInfo() != null || uri.getFragment() != null || embeddedCredentials) {
            throw new IllegalStateException(variable + " must identify a database without embedded credentials.");
        }
        if (local && !Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(variable + " must use loopback in the local profile.");
        }
    }
}
