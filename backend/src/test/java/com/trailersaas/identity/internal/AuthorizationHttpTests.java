package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import com.trailersaas.InfrastructureExceptionHandler;
import com.trailersaas.ProblemDetailsConfiguration;
import com.trailersaas.RequestCorrelationFilter;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.FirebaseTokenVerifier;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import com.trailersaas.orders.AuthorizationConsumerFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AuthorizationHttpTests.Configuration.class)
@WebAppConfiguration
class AuthorizationHttpTests {
    @Autowired WebApplicationContext context;
    @Autowired ActiveMembershipRepository repository;
    @Autowired FirebaseTokenVerifier verifier;
    @Autowired InternalUserResolver resolver;
    final UUID user = UUID.randomUUID();
    final UUID tenant = UUID.randomUUID();
    final UUID customer = UUID.randomUUID();
    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(repository, verifier, resolver);
        var identity = new VerifiedFirebaseIdentity("uid", null, null);
        when(verifier.verify("synthetic-secret-firebase-token")).thenReturn(identity);
        when(resolver.resolve(identity)).thenReturn(new AuthenticatedUserPrincipal(user, "uid", null, null));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestCorrelationFilter())
                .apply(springSecurity()).build();
    }

    @ParameterizedTest
    @EnumSource(AuthorizationDeniedException.Reason.class)
    void specificAuthorizationAdvicePreserves403CodeAndAddsCorrelation(AuthorizationDeniedException.Reason reason)
            throws Exception {
        var roles = switch (reason) {
            case TENANT_ACCESS_DENIED -> List.<AuthorizationRoleRow>of();
            case CAPABILITY_DENIED -> List.of(new AuthorizationRoleRow(UUID.randomUUID(), null, null, null));
            case CUSTOMER_SCOPE_DENIED -> List.of(new AuthorizationRoleRow(UUID.randomUUID(),
                    RoleCode.CUSTOMER_DISPATCHER, RoleScopeType.CUSTOMER, UUID.randomUUID()));
        };
        when(repository.forActiveUserInTenant(user, tenant)).thenReturn(roles);
        mvc.perform(get("/api/v1/test-only/tenants/" + tenant + "/customers/" + customer + "/edit")
                        .header("Authorization", "Bearer synthetic-secret-firebase-token")
                        .header("X-Request-Id", "authorization-denied"))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value(reason.name()))
                .andExpect(jsonPath("$.detail").value(reason.detail()))
                .andExpect(jsonPath("$.traceId").value("authorization-denied"))
                .andExpect(header().string("X-Request-Id", "authorization-denied"));
        verify(repository).forActiveUserInTenant(user, tenant);
    }

    @Test
    void noAuthenticationNeverConsultsAuthorizationRepository() throws Exception {
        mvc.perform(get("/api/v1/test-only/tenants/" + tenant + "/invite"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(repository);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({IdentitySecurityConfiguration.class, DatabaseTenantAuthorization.class, AuthorizationExceptionHandler.class,
            ProblemDetailsConfiguration.class, InfrastructureExceptionHandler.class, AuthorizationConsumerFixture.class})
    static class Configuration {
        @Bean ActiveMembershipRepository repository() { return mock(ActiveMembershipRepository.class); }
        @Bean FirebaseTokenVerifier verifier() { return mock(FirebaseTokenVerifier.class); }
        @Bean InternalUserResolver resolver() { return mock(InternalUserResolver.class); }
        @Bean JsonMapper mapper() {
            return JsonMapper.builder().addMixIn(org.springframework.http.ProblemDetail.class,
                    org.springframework.http.converter.json.ProblemDetailJacksonMixin.class).build();
        }
    }
}
