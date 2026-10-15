package com.trailersaas.identity;

import java.util.*;

/** Fresh action hints for a specific Customer. Commands must authorize again. */
public interface CustomerActionAccess {
    Set<Capability> capabilities(AuthenticatedUserPrincipal actor, UUID tenant, UUID customer);
    boolean covers(AuthenticatedUserPrincipal actor, UUID tenant, Collection<UUID> customers, Capability capability);
}
