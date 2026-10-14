package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class OutboxSecurityMetricsPostgresTests extends JobsFixture {
    @Test void processingGaugeIncludesCommittedClaimsWhileHandlerIsStillRunning() throws Exception {
        var id=enqueue("blocked",5);
        handler.entered=new CountDownLatch(1); handler.release=new CountDownLatch(1);
        var cfg=new JobsProperties(true,List.of(tenant),Duration.ofSeconds(5),4,4,Duration.ofSeconds(5),
                Duration.ofSeconds(20),5,Duration.ofSeconds(1),Duration.ofSeconds(2));
        var metrics=new SimpleMeterRegistry(); var worker=worker(cfg,metrics);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var poll=pool.submit(worker::pollOnce);
            try {
                assertThat(handler.entered.await(3,TimeUnit.SECONDS)).isTrue();
                assertThat(status(id)).isEqualTo("PROCESSING");
                assertThat(metrics.get("jobs.stored").tag("status","processing").gauge().value()).isEqualTo(1);
            } finally { handler.release.countDown(); poll.get(5,TimeUnit.SECONDS); }
        }
        assertThat(metrics.get("jobs.stored").tag("status","processing").gauge().value()).isZero();
        assertThat(metrics.get("jobs.stored").tag("status","succeeded").gauge().value()).isEqualTo(1);
    }
    @Test void handlerExceptionsCannotLeakTokensSignedUrlsOrStacksToStoredErrorsOrStructuredLogs(CapturedOutput output) throws Exception {
        var id=enqueue("secret",1); worker().pollOnce();
        assertThat(status(id)).isEqualTo("DEAD");
        assertThat(db.value("SELECT last_error FROM outbox_jobs WHERE id=?",id)).isEqualTo("HANDLER_FAILURE");
        assertThat(output.getAll()).contains("outbox_result",id.toString(),"HANDLER_FAILURE")
                .doesNotContain("private-bearer","X-Amz-Signature","signed.example","IllegalStateException: private");
    }
    @Test void configuredTenantScopeDoesNotEnumerateOrClaimOtherTenants() throws Exception {
        var foreign=transactions.execute(other,()->publisher.enqueue(other,TYPE,new JobAggregate("CUSTOMER",foreignCustomer),
                new Payload(foreignCustomer,"success"),Instant.now().minusSeconds(1),5));
        var own=enqueue("success",5); worker().pollOnce();
        assertThat(status(own)).isEqualTo("SUCCEEDED"); assertThat(status(foreign)).isEqualTo("PENDING");
        transactions.execute(tenant,()-> {
            assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs WHERE id=? FOR UPDATE SKIP LOCKED",UUID.class,foreign)).isEmpty();
            assertThat(jdbc.update("UPDATE outbox_jobs SET status='DEAD' WHERE id=?",foreign)).isZero();
            return null;
        });
        var forged=new JobClaim(foreign,tenant,"TEST_EFFECT",new JobAggregate("CUSTOMER",customer),"{}",1,5,UUID.randomUUID(),Instant.now().plusSeconds(10));
        assertThat(store.succeed(forged)).isFalse();
        assertThat(status(foreign)).isEqualTo("PENDING");
    }
    @Test void samePhysicalConnectionDoesNotRetainTenantAfterCommitOrRollback() throws Exception {
        enqueue("success",5);
        try(var c=jdbc.getDataSource().getConnection()) {
            c.setAutoCommit(false);
            try(var q=c.createStatement()) {
                q.execute("SET LOCAL app.current_tenant_id='"+tenant+"'");
                try(var r=q.executeQuery("SELECT count(*) FROM outbox_jobs")) { r.next(); assertThat(r.getLong(1)).isPositive(); }
                c.commit();
                try(var r=q.executeQuery("SELECT NULLIF(current_setting('app.current_tenant_id',true),'')")) { r.next(); assertThat(r.getString(1)).isNull(); }
                try(var r=q.executeQuery("SELECT count(*) FROM outbox_jobs")) { r.next(); assertThat(r.getLong(1)).isZero(); }
                q.execute("SET LOCAL app.current_tenant_id='"+other+"'"); c.rollback();
                try(var r=q.executeQuery("SELECT count(*) FROM outbox_jobs")) { r.next(); assertThat(r.getLong(1)).isZero(); }
            }
            c.rollback();
        }
    }
    @Test void terminatedRuntimeConnectionRecoversWithoutLosingCommittedJobOrLeakingContext() throws Exception {
        var id=enqueue("success",5);
        try(var c=jdbc.getDataSource().getConnection(); var s=c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("SET LOCAL app.current_tenant_id='"+tenant+"'");
            int pid; try(var r=s.executeQuery("SELECT pg_backend_pid()")) { r.next(); pid=r.getInt(1); }
            assertThat(db.value("SELECT pg_terminate_backend(?::int)",pid)).isEqualTo(true);
            assertThatThrownBy(()->s.executeQuery("SELECT 1")).isInstanceOf(SQLException.class);
        }
        worker().pollOnce(); assertThat(status(id)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs",UUID.class)).isEmpty();
    }
    @Test void metricsRepresentStoredCountsAgeFailuresAndUseOnlyBoundedLabels() throws Exception {
        var metrics=new SimpleMeterRegistry(); var cfg=settings(List.of(tenant)); var probes=new JobMetrics(metrics,cfg);
        var id=transactions.execute(tenant,()->publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",customer),
                new Payload(customer,"secret"),Instant.now().minusSeconds(60),5));
        probes.refresh(tenant,store.statistics(tenant));
        assertThat(metrics.get("jobs.stored").tag("status","pending").gauge().value()).isEqualTo(1);
        assertThat(metrics.get("jobs.oldest.pending.age").gauge().value()).isGreaterThanOrEqualTo(60);
        var worker=worker(cfg,metrics); worker.pollOnce();
        assertThat(db.value("SELECT last_error FROM outbox_jobs WHERE id=?",id)).isEqualTo("HANDLER_FAILURE");
        assertThat(metrics.get("jobs.handler.failures").tag("code","HANDLER_FAILURE").counter().count()).isEqualTo(1);
        assertThat(metrics.get("jobs.retry").counter().count()).isEqualTo(1);
        assertThat(metrics.get("jobs.handler.duration").timer().count()).isEqualTo(1);
        for(var meter:metrics.getMeters()) for(var tag:meter.getId().getTags()) {
            assertThat(tag.getKey()).isIn("status","code");
            assertThat(tag.getValue()).doesNotContain(tenant.toString(),customer.toString(),id.toString(),"private-bearer","Signature");
        }
    }
    @Test void timeoutIsRetryableAndDoesNotLeaveClaimOrBusinessEffectConfirmed() throws Exception {
        var id=enqueue("blocked",5); handler.entered=new CountDownLatch(1); handler.release=new CountDownLatch(1);
        var cfg=new JobsProperties(true,List.of(tenant),Duration.ofSeconds(5),4,4,Duration.ofMillis(100),
                Duration.ofSeconds(2),5,Duration.ofSeconds(1),Duration.ofSeconds(2));
        try { worker(cfg,new SimpleMeterRegistry()).pollOnce(); }
        finally { handler.release.countDown(); }
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(db.value("SELECT last_error FROM outbox_jobs WHERE id=?",id)).isEqualTo("HANDLER_TIMEOUT");
        assertThat(db.value("SELECT version FROM customers WHERE id=?",customer)).isEqualTo(0L);
    }
    @Test void uncooperativeTimeoutRetainsBoundedSlotWhileOtherWorkerCanContinue() throws Exception {
        var stuck=enqueue("uncooperative",5); handler.entered=new CountDownLatch(1); handler.release=new CountDownLatch(1);
        var cfg=new JobsProperties(true,List.of(tenant),Duration.ofSeconds(5),1,1,Duration.ofMillis(100),
                Duration.ofSeconds(2),5,Duration.ofSeconds(1),Duration.ofSeconds(2));
        var metrics=new SimpleMeterRegistry(); var worker=worker(cfg,metrics);
        try {
            worker.pollOnce(); assertThat(status(stuck)).isEqualTo("PENDING");
            var independent=enqueue("success",5);
            worker.pollOnce(); assertThat(metrics.get("jobs.capacity.exhausted").counter().count()).isEqualTo(1);
            assertThat(handler.calls.get()).isEqualTo(1);
            worker().pollOnce(); assertThat(status(independent)).isEqualTo("SUCCEEDED");
        } finally { handler.release.countDown(); }
    }
    @Test void disabledWorkerCannotClaimOrInvokeEvenIfManuallyPolled() throws Exception {
        var id=enqueue("success",5);
        var cfg=new JobsProperties(false,List.of(tenant),Duration.ofSeconds(5),4,4,Duration.ofSeconds(1),
                Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(2));
        worker(cfg,new SimpleMeterRegistry()).pollOnce();
        assertThat(status(id)).isEqualTo("PENDING"); assertThat(attempts(id)).isZero(); assertThat(handler.calls.get()).isZero();
    }
}
