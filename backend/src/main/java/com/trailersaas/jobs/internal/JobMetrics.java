package com.trailersaas.jobs.internal;

import io.micrometer.core.instrument.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class JobMetrics {
    final MeterRegistry registry;
    private final Map<UUID,OutboxStore.Statistics> snapshots=new ConcurrentHashMap<>();
    JobMetrics(MeterRegistry registry, JobsProperties settings) {
        this.registry=registry;
        Gauge.builder("jobs.worker.enabled",settings,s->s.enabled()?1:0).register(registry);
        for (var status : List.of("pending","processing","succeeded","dead")) {
            Gauge.builder("jobs.stored",snapshots,s -> s.values().stream().mapToLong(v -> switch(status) {
                case "pending" -> v.pending(); case "processing" -> v.processing();
                case "succeeded" -> v.succeeded(); default -> v.dead();
            }).sum()).tag("status",status).register(registry);
        }
        Gauge.builder("jobs.oldest.pending.age",snapshots,s -> s.values().stream()
                .mapToDouble(OutboxStore.Statistics::oldestDueAge).max().orElse(0)).baseUnit("seconds").register(registry);
    }
    void refresh(UUID tenant, OutboxStore.Statistics stats) { snapshots.put(tenant,stats); }
    void count(String suffix) { registry.counter("jobs."+suffix).increment(); }
    void failure(com.trailersaas.jobs.JobFailure.Code code) {
        registry.counter("jobs.handler.failures","code",code.name()).increment();
    }
    void duration(String suffix,long start) { registry.timer("jobs."+suffix).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS); }
}
