package com.trailersaas.jobs;

import java.util.UUID;

public interface JobHandler<P> {
    JobType<P> type();

    /** Required, fail-closed domain resource/aggregate/Customer-scope validation.
     * Called under runtime TenantContext both on enqueue and before execution.
     * Producers must separately authorize their principal before enqueueing. */
    void validate(UUID tenantId, JobAggregate aggregate, P payload);

    /** Outside the claim transaction. External effects MUST be idempotent by job ID
     * and use bounded timeouts. DB effects use execution.inTenantTransaction. */
    void execute(JobExecution<P> execution);
}
