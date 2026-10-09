package com.trailersaas.identity.internal;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.trailersaas.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.identity.Capability.*;
import static com.trailersaas.identity.internal.AuthorizationDeniedException.Reason.*;

@Service
@Transactional(readOnly = true)
class DatabaseOrderAccessPolicy implements OrderAccessPolicy {
    private final ActiveMembershipRepository memberships;
    private final TenantContext context;

    DatabaseOrderAccessPolicy(ActiveMembershipRepository memberships, TenantContext context) {
        this.memberships = memberships;
        this.context = context;
    }

    @Override
    public CustomerAccessScope readScope(AuthenticatedUserPrincipal principal, UUID tenantId) {
        if (principal == null || principal.userId() == null || tenantId == null) {
            throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED);
        }
        context.establishTenant(tenantId);
        var roles = memberships.forActiveUserInTenant(principal.userId(), tenantId);
        if (roles.isEmpty()) { throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED); }
        if (roles.stream().anyMatch(r -> r.scopeType() == RoleScopeType.TENANT
                && RoleCapabilities.grants(r.roleCode(), ORDER_VIEW_ALL))) {
            return new CustomerAccessScope(true, Set.of());
        }
        var customers = roles.stream().filter(r -> r.scopeType() == RoleScopeType.CUSTOMER
                && r.customerId() != null && RoleCapabilities.grants(r.roleCode(), ORDER_VIEW_CUSTOMER_SCOPED))
                .map(AuthorizationRoleRow::customerId).collect(Collectors.toSet());
        if (customers.isEmpty()) { throw new AuthorizationDeniedException(CAPABILITY_DENIED); }
        return new CustomerAccessScope(false, customers);
    }
}
