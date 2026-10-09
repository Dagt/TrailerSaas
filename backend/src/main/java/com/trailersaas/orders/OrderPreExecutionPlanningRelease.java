package com.trailersaas.orders;

import java.util.UUID;

/**
 * Orders-owned SPI: caller holds exclusive Order lock, handlers join cancellation transaction.
 * Parent locks must be deterministic; executed/historical work must not be destructively detached.
 */
public interface OrderPreExecutionPlanningRelease {
  void release(UUID tenant, UUID order);
}
