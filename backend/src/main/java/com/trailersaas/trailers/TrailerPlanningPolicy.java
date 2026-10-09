package com.trailersaas.trailers;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.UUID;

/** Planning eligibility only; never changes a Trailer or authorizes physical execution. */
public interface TrailerPlanningPolicy {
  enum Eligibility {
    ELIGIBLE,
    NOT_FOUND,
    CUSTOMER_SCOPE_DENIED,
    UNAVAILABLE
  }

  /**
   * Same-Tenant existence/visibility only, including inactive or OOS resources for rejection
   * history.
   */
  Eligibility visible(AuthenticatedUserPrincipal actor, UUID tenantId, UUID trailerId);

  /** Fresh Trailer visibility and eligibility, held stable in the caller's Tenant transaction. */
  Eligibility check(AuthenticatedUserPrincipal actor, UUID tenantId, UUID trailerId);
}
