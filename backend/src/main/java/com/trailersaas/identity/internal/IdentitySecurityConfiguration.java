package com.trailersaas.identity.internal;

import java.net.URI;

import com.trailersaas.ProblemDetailsConfiguration;
import com.trailersaas.identity.FirebaseTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class IdentitySecurityConfiguration {
    @Bean
    AuthenticationManager identityAuthenticationManager(FirebaseTokenVerifier verifier, InternalUserResolver users) {
        return new ProviderManager(new FirebaseAuthenticationProvider(verifier, users));
    }

    @Bean
    AuthenticationEntryPoint identityAuthenticationEntryPoint(JsonMapper mapper) {
        return (request, response, failure) -> {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid authentication token is required.");
            problem.setType(URI.create("https://api.trailersaas.example/problems/authentication-required"));
            problem.setTitle("Authentication required");
            problem.setProperty("code", "AUTHENTICATION_REQUIRED");
            ProblemDetailsConfiguration.addCorrelation(problem);
            response.setStatus(401);
            response.setContentType("application/problem+json");
            response.setHeader("WWW-Authenticate", "Bearer");
            mapper.writeValue(response.getOutputStream(), problem);
        };
    }

    @Bean
    SecurityFilterChain identitySecurity(HttpSecurity http, AuthenticationManager identityAuthenticationManager,
            AuthenticationEntryPoint identityAuthenticationEntryPoint) throws Exception {
        return http.cors(cors -> {}).csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(identityAuthenticationEntryPoint))
                .oauth2ResourceServer(resource -> resource
                        .authenticationManagerResolver(request -> identityAuthenticationManager)
                        .authenticationEntryPoint(identityAuthenticationEntryPoint))
                .build();
    }
}
