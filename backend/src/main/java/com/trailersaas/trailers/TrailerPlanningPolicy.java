package com.trailersaas.trailers;

import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;

/** Planning eligibility only; never changes a Trailer or authorizes physical execution. */
public interface TrailerPlanningPolicy {
    enum Eligibility { ELIGIBLE, NOT_FOUND, CUSTOMER_SCOPE_DENIED, UNAVAILABLE }
    /** Fresh Trailer visibility and eligibility, held stable in the caller's Tenant transaction. */
    Eligibility check(AuthenticatedUserPrincipal actor, UUID tenantId, UUID trailerId);
}
