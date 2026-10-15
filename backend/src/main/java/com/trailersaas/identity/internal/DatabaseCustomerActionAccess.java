package com.trailersaas.identity.internal;

import com.trailersaas.identity.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.trailersaas.identity.internal.AuthorizationDeniedException.Reason.*;

@Component @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
class DatabaseCustomerActionAccess implements CustomerActionAccess {
    private final ActiveMembershipRepository memberships;
    private final TenantContext context;
    private final EntityManager em;
    DatabaseCustomerActionAccess(ActiveMembershipRepository memberships, TenantContext context, EntityManager em) {
        this.memberships=memberships; this.context=context; this.em=em;
    }
    public Set<Capability> capabilities(AuthenticatedUserPrincipal actor, UUID tenant, UUID customer) {
        if(actor==null || customer==null) throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED);
        context.establishTenant(tenant);
        if(((Number)em.createNativeQuery("select count(*) from tenants where id=:tenant and status='ACTIVE'")
                .setParameter("tenant",tenant).getSingleResult()).longValue()!=1) throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED);
        var roles=memberships.forActiveUserInTenant(actor.userId(),tenant);
        if(roles.isEmpty()) throw new AuthorizationDeniedException(TENANT_ACCESS_DENIED);
        var result=EnumSet.noneOf(Capability.class);
        for(var role:roles) if(role.scopeType()==RoleScopeType.TENANT || customer.equals(role.customerId()))
            for(var capability:Capability.values()) if(RoleCapabilities.grants(role.roleCode(),capability) && !RoleCapabilities.requiresAssignment(capability)) result.add(capability);
        return Collections.unmodifiableSet(result);
    }
    public boolean covers(AuthenticatedUserPrincipal actor, UUID tenant, Collection<UUID> customers, Capability capability) {
        return !customers.isEmpty() && customers.stream().allMatch(c->capabilities(actor,tenant,c).contains(capability));
    }
}
