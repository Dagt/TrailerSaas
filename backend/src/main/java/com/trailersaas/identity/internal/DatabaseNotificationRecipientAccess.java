package com.trailersaas.identity.internal;

import com.trailersaas.identity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
class DatabaseNotificationRecipientAccess implements NotificationRecipientAccess {
 final ActiveMembershipRepository memberships;final TenantContext context;
 DatabaseNotificationRecipientAccess(ActiveMembershipRepository memberships,TenantContext context){this.memberships=memberships;this.context=context;}
 public Optional<CustomerAccessScope> scope(UUID tenant,UUID user){
  context.establishTenant(tenant);if(user==null)return Optional.empty();
  var roles=memberships.forActiveUserInTenant(user,tenant);
  if(roles.stream().anyMatch(r->r.scopeType()==RoleScopeType.TENANT&&RoleCapabilities.grants(r.roleCode(),Capability.ALERT_VIEW)))return Optional.of(new CustomerAccessScope(true,Set.of()));
  var customers=new HashSet<UUID>();for(var r:roles)if(r.scopeType()==RoleScopeType.CUSTOMER&&r.customerId()!=null&&RoleCapabilities.grants(r.roleCode(),Capability.ALERT_VIEW))customers.add(r.customerId());
  return customers.isEmpty()?Optional.empty():Optional.of(new CustomerAccessScope(false,customers));
 }
}
