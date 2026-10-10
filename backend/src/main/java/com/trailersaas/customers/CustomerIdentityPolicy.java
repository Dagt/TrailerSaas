package com.trailersaas.customers;

import java.util.UUID;

/** Same-Tenant target identity only; never actor authorization or an active-status policy. */
public interface CustomerIdentityPolicy {
  boolean exists(UUID tenantId, UUID customerId);
}
