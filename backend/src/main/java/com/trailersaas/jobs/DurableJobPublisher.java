package com.trailersaas.jobs;

import java.time.Instant;
import java.util.UUID;

/** Joins an already-authorized business transaction; never opens an independent commit.
 * No global job publishing/execution is authorized in #55. Null maxAttempts selects the configured default. */
public interface DurableJobPublisher {
    <P> UUID enqueue(UUID tenantId, JobType<P> type, JobAggregate aggregate, P payload,
                     Instant availableAt, Integer maxAttempts);
}
