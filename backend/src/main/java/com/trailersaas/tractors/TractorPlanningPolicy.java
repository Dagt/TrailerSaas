package com.trailersaas.tractors;

import java.util.UUID;

/** Caller owns assignment authorization and an established runtime Tenant transaction. */
public interface TractorPlanningPolicy {
  enum Eligibility {
    ELIGIBLE,
    NOT_FOUND,
    INACTIVE
  }

  Eligibility check(UUID tenant, UUID id);
}
