package com.trailersaas.identity;

import java.util.Set;
import java.util.UUID;

/** Fresh document read grants; consumers must independently validate linked resources. */
public interface DocumentAccessPolicy {
  ReadScope readScope(AuthenticatedUserPrincipal actor,UUID tenant);
  record ReadScope(boolean tenantWide,Set<UUID> customerIds,boolean assignedWork) {
    public ReadScope { customerIds=Set.copyOf(customerIds); }
  }
}
