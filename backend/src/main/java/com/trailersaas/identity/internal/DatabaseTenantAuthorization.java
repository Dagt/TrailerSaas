package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.trailersaas.identity.internal.AuthorizationDeniedException.Reason.*;

@Service
@Transactional(readOnly = true)
class DatabaseTenantAuthorization implements TenantAuthorization {
    private final ActiveMembershipRepository memberships;

    DatabaseTenantAuthorization(ActiveMembershipRepository memberships) { this.memberships = memberships; }

    @Override
    public void requireTenantCapability(AuthenticatedUserPrincipal principal, UUID tenantId, Capability capability) {
        var roles = activeRoles(principal, tenantId);
        if (RoleCapabilities.requiresAssignment(capability) || !hasTenantGrant(roles, capability)) {
            throw new AuthorizationDeniedException(CAPABILITY_DENIED);
        }
    }

    @Override
    public void requireCustomerCapability(AuthenticatedUserPrincipal principal, UUID tenantId,
            UUID customerId, Capability capability) {
        var roles = activeRoles(principal, tenantId);
        if (RoleCapabilities.requiresAssignment(capability)) {
            throw new AuthorizationDeniedException(CAPABILITY_DENIED);
        }
        if (customerId == null) { throw new AuthorizationDeniedException(CUSTOMER_SCOPE_DENIED); }
        if (hasTenantGrant(roles, capability)) { return; }
        var customerGrants = roles.stream()
                .filter(role -> role.scopeType() == RoleScopeType.CUSTOMER
                        && RoleCapabilities.grants(role.roleCode(), capability)).toList();
        if (customerGrants.stream().anyMatch(role -> customerId.equals(role.customerId()))) { return; }
        throw new AuthorizationDeniedException(customerGrants.isEmpty() ? CAPABILITY_DENIED : CUSTOMER_SCOPE_DENIED);
    }

    @Override
    public void requireAssignedWorkCapability(AuthenticatedUserPrincipal principal, UUID tenantId,
            Capability capability, boolean assignmentSatisfied) {
        var roles = activeRoles(principal, tenantId);
        if (!RoleCapabilities.requiresAssignment(capability) || !hasTenantGrant(roles, capability)
                || !assignmentSatisfied) {
            throw new AuthorizationDeniedException(CAPABILITY_DENIED);
        }
    }

    private List<AuthorizationRoleRow> activeRoles(AuthenticatedUserPrincipal principal, UUID tenantId) {
        if (principal == null || principal.userId() == null || tenantId == null) {
            throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED);
        }
        var roles = memberships.forActiveUserInTenant(principal.userId(), tenantId);
        if (roles.isEmpty()) { throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED); }
        return roles;
    }

    private boolean hasTenantGrant(List<AuthorizationRoleRow> roles, Capability capability) {
        return roles.stream().anyMatch(role -> role.scopeType() == RoleScopeType.TENANT
                && RoleCapabilities.grants(role.roleCode(), capability));
    }
}
