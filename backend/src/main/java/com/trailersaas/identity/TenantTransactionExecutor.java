package com.trailersaas.identity;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Background execution pattern: one Tenant in one new transaction using runtime
 * credentials. Requires no ambient transaction; does not authorize the callback.
 */
public interface TenantTransactionExecutor {
    <T> T execute(UUID tenantId, Supplier<T> work);
}
