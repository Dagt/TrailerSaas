package com.trailersaas.jobs;

import java.util.UUID;
import java.util.function.Supplier;

public record JobExecution<P>(UUID jobId, UUID tenantId, int attempt,
                              JobAggregate aggregate, P payload, TenantWork work) {
    public interface TenantWork { <T> T execute(Supplier<T> callback); }
    /** Independent transaction, freshly scoped and fenced against an expired claim.
     * Keep DB callbacks short; never perform network IO inside them. */
    public <T> T inTenantTransaction(Supplier<T> callback) { return work.execute(callback); }
}
