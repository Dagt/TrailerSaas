package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import com.trailersaas.jobs.JobFailure.Code;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class DurableJobWorker {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger(DurableJobWorker.class);
    private final OutboxStore store;
    private final JobRegistry registry;
    private final JobsProperties settings;
    private final JobMetrics metrics;
    private final RetryPolicy retry;
    private final Semaphore slots;
    private final ExecutorService handlers=Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean polling=new AtomicBoolean();
    private final AtomicInteger cursor=new AtomicInteger();
    DurableJobWorker(OutboxStore store, JobRegistry registry, JobsProperties settings, JobMetrics metrics) {
        this.store=store; this.registry=registry; this.settings=settings; this.metrics=metrics;
        slots=new Semaphore(settings.concurrency());
        retry=new RetryPolicy(settings.initialDelay(),settings.maxBackoff(),()->ThreadLocalRandom.current().nextDouble());
        LOG.atInfo().addKeyValue("event","outbox_worker_configured").addKeyValue("enabled",settings.enabled())
                .addKeyValue("tenantCount",settings.tenants().size()).log("Durable worker configuration loaded");
    }
    void pollOnce() {
        if (settings.tenants().isEmpty()) return;
        if (!polling.compareAndSet(false,true)) { metrics.count("poll.overlap"); return; }
        long started=System.nanoTime();
        var tenant=settings.tenants().get(Math.floorMod(cursor.getAndIncrement(),settings.tenants().size()));
        try {
            int capacity=Math.min(settings.batchSize(),slots.availablePermits());
            if (capacity==0 || !slots.tryAcquire(capacity)) { metrics.count("capacity.exhausted"); return; }
            OutboxStore.Batch batch;
            long claiming=System.nanoTime();
            try { batch=store.claim(tenant,capacity,settings.lease()); }
            catch (RuntimeException failure) { slots.release(capacity); throw failure; }
            finally { metrics.duration("claim.duration",claiming); }
            slots.release(capacity-batch.claims().size());
            for (int i=0;i<batch.recovered();i++) metrics.count("lease.recoveries");
            for (int i=0;i<batch.exhausted();i++) { metrics.count("dead"); metrics.count("lease.exhausted"); }
            var running=new LinkedHashMap<JobClaim,Future<JobFailure>>();
            for (var claim : batch.claims()) {
                try {
                    var task=new FutureTask<JobFailure>(()-> {
                        long handling=System.nanoTime();
                        try { execute(registry.decode(claim.type(),claim.payload()),claim); return null; }
                        catch (JobFailure failure) { return failure; }
                        catch (RuntimeException failure) { return new JobFailure(Code.HANDLER_FAILURE,true); }
                        finally { metrics.duration("handler.duration",handling); }
                    });
                    handlers.execute(()-> { try { task.run(); } finally { slots.release(); } });
                    running.put(claim,task);
                } catch (RejectedExecutionException failure) { slots.release(); }
            }
            long deadline=System.nanoTime()+settings.handlerTimeout().toNanos();
            for (var entry : running.entrySet()) {
                long executing=started;
                JobFailure failure;
                try { failure=entry.getValue().get(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS); }
                catch (TimeoutException expired) { entry.getValue().cancel(true); failure=new JobFailure(Code.HANDLER_TIMEOUT,true); }
                catch (ExecutionException failed) { failure=new JobFailure(Code.HANDLER_FAILURE,true); }
                catch (InterruptedException stopped) {
                    running.values().forEach(f->f.cancel(true)); Thread.currentThread().interrupt(); return;
                }
                try {
                    var claim=entry.getKey();
                    String outcome;
                    if (failure==null) {
                        outcome=store.succeed(claim)?"succeeded":"stale";
                    } else {
                        metrics.failure(failure.code());
                        outcome=store.fail(claim,failure,retry.delay(claim.attempt())).name().toLowerCase(Locale.ROOT);
                    }
                    metrics.count(outcome);
                    LOG.atInfo().addKeyValue("event","outbox_result").addKeyValue("jobId",claim.id())
                            .addKeyValue("tenantId",claim.tenantId()).addKeyValue("attempt",claim.attempt())
                            .addKeyValue("result",outcome).addKeyValue("errorCode",failure==null?null:failure.code().name())
                            .log("Durable job result");
                } catch (RuntimeException unavailable) { infrastructureFailure("result_write_failed"); }
                finally { metrics.duration("processing.duration",executing); }
            }
        } catch (RuntimeException unavailable) { infrastructureFailure("poll_failed"); }
        finally {
            try { metrics.refresh(tenant,store.statistics(tenant)); }
            catch (RuntimeException unavailable) { infrastructureFailure("metrics_refresh_failed"); }
            metrics.duration("poll.duration",started);
            polling.set(false);
        }
    }
    private <P> void execute(JobRegistry.Decoded<P> decoded, JobClaim claim) {
        JobRegistry.aggregate(decoded.handler().type(),claim.aggregate());
        store.fencedTransaction(claim,()-> {
            decoded.handler().validate(claim.tenantId(),claim.aggregate(),decoded.payload()); return null;
        });
        if (Thread.currentThread().isInterrupted()) throw new JobFailure(Code.HANDLER_TIMEOUT,true);
        decoded.handler().execute(new JobExecution<>(claim.id(),claim.tenantId(),claim.attempt(),
                claim.aggregate(),decoded.payload(),new JobExecution.TenantWork() {
                    public <T> T execute(java.util.function.Supplier<T> callback) { return store.fencedTransaction(claim,callback); }
                }));
    }
    private void infrastructureFailure(String reason) {
        metrics.count("infrastructure.failures");
        LOG.atWarn().addKeyValue("event",reason).log("Durable worker database operation unavailable; leases remain recoverable");
    }
    @PreDestroy void stop() { handlers.shutdownNow(); }
}
