package com.trailersaas.identity.internal;

import java.util.UUID;
import com.trailersaas.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseTenantActorMembership implements TenantActorMembership {
    private final ActiveMembershipRepository memberships;
    private final TenantContext context;
    DatabaseTenantActorMembership(ActiveMembershipRepository memberships, TenantContext context) {
        this.memberships = memberships; this.context = context;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public UUID forActor(AuthenticatedUserPrincipal actor, UUID tenant) {
        context.establishTenant(tenant);
        return memberships.forActiveUserInTenant(actor.userId(), tenant).stream().findFirst()
                .orElseThrow(() -> new AuthorizationDeniedException(AuthorizationDeniedException.Reason.TENANT_ACCESS_DENIED)).membershipId();
    }
}
