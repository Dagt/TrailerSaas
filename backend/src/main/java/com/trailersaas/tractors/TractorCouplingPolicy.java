package com.trailersaas.tractors;

import java.util.UUID;

/** Exclusive Tractor parent lock serializes physical use and holds eligibility until commit. */
public interface TractorCouplingPolicy {
  /** Existing same-Tenant Tractor; Drop deliberately permits deactivated equipment. */
  boolean lock(UUID tenantId, UUID tractorId, boolean requireActive);
}
