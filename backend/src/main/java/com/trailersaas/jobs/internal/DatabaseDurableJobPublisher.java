package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
class DatabaseDurableJobPublisher implements DurableJobPublisher {
    private final OutboxStore store;
    private final JobRegistry registry;
    private final JobsProperties settings;
    DatabaseDurableJobPublisher(OutboxStore store, JobRegistry registry, JobsProperties settings) {
        this.store=store; this.registry=registry; this.settings=settings;
    }
    public <P> UUID enqueue(UUID tenant, JobType<P> type, JobAggregate aggregate, P payload, Instant availableAt, Integer maxAttempts) {
        store.requireTenant(tenant);
        Objects.requireNonNull(type); Objects.requireNonNull(availableAt);
        int attempts=maxAttempts==null?settings.maxAttempts():maxAttempts;
        if (attempts<1 || attempts>20) throw new IllegalArgumentException("Invalid maximum attempts");
        var handler=registry.registered(type);
        JobRegistry.aggregate(type,aggregate);
        var encoded=registry.encode(type,payload);
        handler.validate(tenant,aggregate,payload);
        var id=UUID.randomUUID();
        store.insert(id,tenant,type,aggregate,encoded,availableAt,attempts);
        return id;
    }
}
