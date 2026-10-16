package com.trailersaas.identity.internal;

import com.trailersaas.identity.*;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
class DatabaseDocumentAccessPolicy implements DocumentAccessPolicy {
  private final ActiveMembershipRepository memberships;
  private final TenantContext context;
  private final CustomerActionAccess actions;
  DatabaseDocumentAccessPolicy(ActiveMembershipRepository memberships,TenantContext context,CustomerActionAccess actions) {
    this.memberships=memberships;this.context=context;this.actions=actions;
  }
  public ReadScope readScope(AuthenticatedUserPrincipal actor,UUID tenant) {
    if(actor==null || actor.userId()==null || tenant==null) throw new AuthorizationDeniedException(AuthorizationDeniedException.Reason.TENANT_ACCESS_DENIED);
    context.establishTenant(tenant);
    var roles=memberships.forActiveUserInTenant(actor.userId(),tenant);
    if(roles.isEmpty()) throw new AuthorizationDeniedException(AuthorizationDeniedException.Reason.TENANT_ACCESS_DENIED);
    if(roles.stream().anyMatch(r->r.scopeType()==RoleScopeType.TENANT && RoleCapabilities.grants(r.roleCode(),Capability.DOCUMENT_VIEW_ALL)))
      return new ReadScope(true,Set.of(),false);
    var customers=roles.stream().filter(r->r.scopeType()==RoleScopeType.CUSTOMER && r.customerId()!=null && RoleCapabilities.grants(r.roleCode(),Capability.DOCUMENT_VIEW_CUSTOMER_SCOPED))
        .map(AuthorizationRoleRow::customerId).collect(Collectors.toSet());
    boolean assigned=roles.stream().anyMatch(r->r.scopeType()==RoleScopeType.TENANT && RoleCapabilities.grants(r.roleCode(),Capability.TRIP_VIEW_ASSIGNED));
    for(var customer:customers) actions.capabilities(actor,tenant,customer);
    if(!customers.isEmpty() || assigned) return new ReadScope(false,customers,assigned);
    throw new AuthorizationDeniedException(AuthorizationDeniedException.Reason.CAPABILITY_DENIED);
  }
}
