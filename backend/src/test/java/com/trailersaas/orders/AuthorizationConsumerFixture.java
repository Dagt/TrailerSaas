package com.trailersaas.orders;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantAuthorization;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Synthetic application consumer: no RoleCode, persistence or Firebase dependency. */
@TestConfiguration(proxyBeanMethods = false)
@Import(AuthorizationConsumerFixture.Controller.class)
public class AuthorizationConsumerFixture {
    @Bean
    public ConsumerService consumerService(TenantAuthorization authorization) { return new ConsumerService(authorization); }

    public static class ConsumerService {
        private final TenantAuthorization authorization;
        private final Map<WorkKey, UUID> assignedUsers = new ConcurrentHashMap<>();

        ConsumerService(TenantAuthorization authorization) { this.authorization = authorization; }

        public void assign(UUID tenant, UUID work, UUID user) { assignedUsers.put(new WorkKey(tenant, work), user); }

        void invite(AuthenticatedUserPrincipal user, UUID tenant) {
            authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        }

        void edit(AuthenticatedUserPrincipal user, UUID tenant, UUID customer) {
            authorization.requireCustomerCapability(user, tenant, customer, Capability.ORDER_EDIT);
        }

        void execute(AuthenticatedUserPrincipal user, UUID tenant, UUID work) {
            boolean assigned = user.userId().equals(assignedUsers.get(new WorkKey(tenant, work)));
            authorization.requireAssignedWorkCapability(user, tenant, Capability.TRIP_EXECUTE_ASSIGNED, assigned);
        }

        record WorkKey(UUID tenant, UUID work) {}
    }

    @RestController
    @TestComponent
    static class Controller {
        private final ConsumerService service;

        Controller(ConsumerService service) { this.service = service; }

        @GetMapping("/api/v1/test-only/tenants/{tenant}/invite")
        String invite(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenant) {
            service.invite(user, tenant); return "allowed";
        }

        @GetMapping("/api/v1/test-only/tenants/{tenant}/customers/{customer}/edit")
        String edit(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenant,
                @PathVariable UUID customer) {
            service.edit(user, tenant, customer); return "allowed";
        }

        @GetMapping("/api/v1/test-only/tenants/{tenant}/work/{work}/execute")
        String execute(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenant,
                @PathVariable UUID work) {
            service.execute(user, tenant, work); return "allowed";
        }
    }
}
