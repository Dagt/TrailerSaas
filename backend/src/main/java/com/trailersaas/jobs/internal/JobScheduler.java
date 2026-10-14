package com.trailersaas.jobs.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="trailersaas.jobs",name="enabled",havingValue="true")
class JobScheduler {
    private final DurableJobWorker worker;
    JobScheduler(DurableJobWorker worker) { this.worker=worker; }
    @Scheduled(fixedDelayString="${trailersaas.jobs.poll-interval:5s}",initialDelayString="${trailersaas.jobs.poll-interval:5s}")
    void poll() { worker.pollOnce(); }
}
