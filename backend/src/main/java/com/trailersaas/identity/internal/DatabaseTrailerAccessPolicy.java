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
@Transactional(readOnly=true)
class DatabaseTrailerAccessPolicy implements TrailerAccessPolicy {
    private final ActiveMembershipRepository memberships;
    private final TenantContext context;
    DatabaseTrailerAccessPolicy(ActiveMembershipRepository memberships, TenantContext context) {
        this.memberships=memberships; this.context=context;
    }
    @Override public CustomerAccessScope readScope(AuthenticatedUserPrincipal principal, UUID tenant) {
        var roles=roles(principal,tenant);
        if(tenantGrant(roles,TRAILER_VIEW_ALL)) { return new CustomerAccessScope(true,Set.of()); }
        var customers=roles.stream().filter(r->r.scopeType()==RoleScopeType.CUSTOMER && r.customerId()!=null
                && RoleCapabilities.grants(r.roleCode(),TRAILER_VIEW_CUSTOMER_SCOPED))
                .map(AuthorizationRoleRow::customerId).collect(Collectors.toSet());
        if(customers.isEmpty()) { throw new AuthorizationDeniedException(CAPABILITY_DENIED); }
        return new CustomerAccessScope(false,customers);
    }
    @Override public boolean mayResolveQr(AuthenticatedUserPrincipal principal, UUID tenant, UUID customer) {
        try {
            var roles=roles(principal,tenant);
            if(tenantGrant(roles,TRAILER_VIEW_ALL) || tenantGrant(roles,YARD_MOVE_EXECUTE)
                    || tenantGrant(roles,YARD_CHECK_EXECUTE) || tenantGrant(roles,TRAILER_HOOK)
                    || tenantGrant(roles,TRAILER_DROP)) { return true; }
            return customer!=null && roles.stream().anyMatch(r->r.scopeType()==RoleScopeType.CUSTOMER
                    && customer.equals(r.customerId()) && RoleCapabilities.grants(r.roleCode(),TRAILER_VIEW_CUSTOMER_SCOPED));
        } catch(AuthorizationDeniedException denied) { return false; }
    }
    private java.util.List<AuthorizationRoleRow> roles(AuthenticatedUserPrincipal principal,UUID tenant) {
        if(principal==null || principal.userId()==null || tenant==null) { throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED); }
        context.establishTenant(tenant);
        var roles=memberships.forActiveUserInTenant(principal.userId(),tenant);
        if(roles.isEmpty()) { throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED); }
        return roles;
    }
    private boolean tenantGrant(java.util.List<AuthorizationRoleRow> roles,Capability capability) {
        return roles.stream().anyMatch(r->r.scopeType()==RoleScopeType.TENANT && RoleCapabilities.grants(r.roleCode(),capability));
    }
}
