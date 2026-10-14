package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.JobAggregate;
import java.time.Instant;
import java.util.UUID;

record JobClaim(UUID id, UUID tenantId, String type, JobAggregate aggregate, String payload,
                int attempt, int maxAttempts, UUID token, Instant lockedUntil) {}
