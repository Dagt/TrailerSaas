package com.trailersaas.trips;

import java.util.UUID;

/**
 * Transaction-bound integration hook only. Durable truth is the Driver update row. Future outbox
 * consumers must enqueue in the same transaction, never infer delivery from this hook.
 */
public record TripDriverUpdateRecorded(UUID tenantId, UUID updateId, UUID driverId) {}
