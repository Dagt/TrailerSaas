package com.trailersaas;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class CorsConfiguration {
    @Bean CorsConfigurationSource corsConfigurationSource(Environment environment) {
        boolean local = Arrays.asList(environment.getActiveProfiles().length == 0
                ? environment.getDefaultProfiles() : environment.getActiveProfiles()).contains("local");
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(origins(environment.getProperty("trailersaas.cors.allowed-origins", ""), local));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
    static List<String> origins(String configured, boolean local) {
        if (configured.isBlank()) { return List.of(); }
        return Arrays.stream(configured.split(",", -1)).map(String::trim).map(value -> {
            try {
                var uri = URI.create(value);
                boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost() == null ? "" : uri.getHost());
                if (uri.getHost() == null || value.contains("*") || uri.getUserInfo() != null || uri.getRawQuery() != null
                        || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || !("https".equals(uri.getScheme()) || (local && loopback && "http".equals(uri.getScheme())))
                        || (!local && loopback) || uri.getPort() > 65535) { throw new IllegalArgumentException(); }
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("WEB_ALLOWED_ORIGINS must contain explicit HTTPS origins; loopback HTTP is local-only.");
            }
            return value;
        }).distinct().toList();
    }
}
