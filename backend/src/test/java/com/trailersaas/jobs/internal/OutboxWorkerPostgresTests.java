package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import com.trailersaas.jobs.JobFailure.Code;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class OutboxWorkerPostgresTests extends JobsFixture {
    @Test void committedWorkSurvivesUntilNewWorkerClaimsIt() throws Exception {
        var id=enqueue("success",5);
        assertThat(status(id)).isEqualTo("PENDING"); assertThat(handler.calls.get()).isZero();
        worker().pollOnce();
        assertThat(status(id)).isEqualTo("SUCCEEDED"); assertThat(attempts(id)).isEqualTo(1);
        assertThat(db.value("SELECT version FROM customers WHERE id=?",customer)).isEqualTo(1L);
    }
    @Test void twoIndependentConnectionsClaimDisjointBoundedBatches() throws Exception {
        var ids=new HashSet<UUID>(); for(int i=0;i<8;i++) ids.add(enqueue("success",5));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<OutboxStore.Batch> task=()-> { barrier.await(); return store.claim(tenant,4,Duration.ofSeconds(5)); };
            var a=pool.submit(task); var b=pool.submit(task);
            var all=new ArrayList<JobClaim>(); all.addAll(a.get(5,TimeUnit.SECONDS).claims()); all.addAll(b.get(5,TimeUnit.SECONDS).claims());
            assertThat(all).hasSize(8);
            assertThat(all.stream().map(JobClaim::id)).containsExactlyInAnyOrderElementsOf(ids);
            assertThat(all.stream().map(JobClaim::token).distinct()).hasSize(8);
            assertThat(all).allMatch(c->c.attempt()==1);
        }
    }
    @Test void skipLockedSkipsAnotherTransactionsLockedJobWithoutBlocking() throws Exception {
        var locked=enqueue("success",5); var eligible=enqueue("success",5);
        try(var c=jdbc.getDataSource().getConnection()) {
            c.setAutoCommit(false);
            try(var s=c.createStatement()) { s.execute("SET LOCAL app.current_tenant_id='"+tenant+"'"); }
            try(var s=c.prepareStatement("SELECT id FROM outbox_jobs WHERE id=? FOR UPDATE")) { s.setObject(1,locked); s.executeQuery().close(); }
            var result=store.claim(tenant,4,Duration.ofSeconds(5));
            assertThat(result.claims()).extracting(JobClaim::id).containsExactly(eligible);
            c.rollback();
        }
        assertThat(status(locked)).isEqualTo("PENDING");
    }
    @Test void twoWorkersExecuteOnlyOneValidClaimPerJobAndDoNotHoldClaimLocksDuringHandler() throws Exception {
        var id=enqueue("blocked",5);
        handler.entered=new CountDownLatch(1); handler.release=new CountDownLatch(1);
        var first=worker(); var second=worker();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(first::pollOnce);
            assertThat(handler.entered.await(2,TimeUnit.SECONDS)).isTrue();
            var b=pool.submit(second::pollOnce); b.get(3,TimeUnit.SECONDS);
            transactions.execute(tenant,()-> {
                assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs WHERE id=? FOR UPDATE NOWAIT",UUID.class,id)).containsExactly(id);
                return null;
            });
            handler.release.countDown(); a.get(3,TimeUnit.SECONDS);
        } finally { handler.release.countDown(); }
        assertThat(handler.calls.get()).isEqualTo(1); assertThat(status(id)).isEqualTo("SUCCEEDED");
    }
    @Test void crashAfterClaimCommitRecoversWithNewTokenAndOldWorkerCannotOverwrite() throws Exception {
        var id=enqueue("success",5);
        var old=store.claim(tenant,1,Duration.ofMillis(150)).claims().getFirst();
        assertThat(status(id)).isEqualTo("PROCESSING"); assertThat(handler.calls.get()).isZero();
        expired(old);
        var recovered=store.claim(tenant,1,Duration.ofSeconds(5));
        var current=recovered.claims().getFirst();
        assertThat(current.token()).isNotEqualTo(old.token()); assertThat(current.attempt()).isEqualTo(2);
        assertThat(recovered.recovered()).isEqualTo(1);
        assertThat(store.succeed(old)).isFalse();
        assertThat(store.fail(old,new JobFailure(Code.HANDLER_PERMANENT,false),Duration.ofSeconds(1))).isEqualTo(OutboxStore.Outcome.STALE);
        assertThatThrownBy(()->store.fencedTransaction(old,()->jdbc.update("UPDATE customers SET name='forbidden' WHERE id=?",customer))).isInstanceOf(JobFailure.class);
        assertThat(store.succeed(current)).isTrue();
        assertThat(status(id)).isEqualTo("SUCCEEDED");
    }
    @Test void crashDuringHandlerRollsBackDbEffectButCommittedClaimRemainsRecoverable() throws Exception {
        var id=enqueue("success",5);
        var crashed=store.claim(tenant,1,Duration.ofMillis(250)).claims().getFirst();
        assertThatThrownBy(()->store.fencedTransaction(crashed,()-> {
            jdbc.update("UPDATE customers SET name='uncommitted',version=version+1 WHERE id=?",customer);
            throw new IllegalStateException("process stops before effect commit");
        })).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(db.value("SELECT version FROM customers WHERE id=?",customer)).isEqualTo(0L);
        expired(crashed); worker().pollOnce();
        assertThat(status(id)).isEqualTo("SUCCEEDED"); assertThat(attempts(id)).isEqualTo(2);
        assertThat(db.value("SELECT version FROM customers WHERE id=?",customer)).isEqualTo(1L);
    }
    @Test void effectCommittedBeforeLostSuccessReceiptIsAtLeastOnceButLogicallyIdempotent() throws Exception {
        var id=enqueue("success",5);
        var lost=store.claim(tenant,1,Duration.ofMillis(500)).claims().getFirst();
        handler.execute(new JobExecution<>(lost.id(),tenant,lost.attempt(),lost.aggregate(),new Payload(customer,"success"),new JobExecution.TenantWork() {
            public <T> T execute(java.util.function.Supplier<T> callback) { return store.fencedTransaction(lost,callback); }
        }));
        assertThat(db.value("SELECT version FROM customers WHERE id=?",customer)).isEqualTo(1L);
        expired(lost); worker().pollOnce();
        assertThat(status(id)).isEqualTo("SUCCEEDED"); assertThat(attempts(id)).isEqualTo(2);
        assertThat(handler.calls.get()).isEqualTo(2);
        assertThat(db.value("SELECT version FROM customers WHERE id=?",customer)).isEqualTo(1L);
        assertThat(store.succeed(lost)).isFalse();
    }
    @Test void exhaustedCrashLeaseBecomesDeadWithoutAnotherHandlerAttempt() throws Exception {
        var id=enqueue("success",1);
        var lost=store.claim(tenant,1,Duration.ofMillis(100)).claims().getFirst(); expired(lost);
        var metrics=new SimpleMeterRegistry(); worker(settings(List.of(tenant)),metrics).pollOnce();
        assertThat(status(id)).isEqualTo("DEAD"); assertThat(attempts(id)).isEqualTo(1);
        assertThat(handler.calls.get()).isZero();
        assertThat(db.value("SELECT last_error FROM outbox_jobs WHERE id=?",id)).isEqualTo("LEASE_EXHAUSTED");
        assertThat(metrics.get("jobs.lease.exhausted").counter().count()).isEqualTo(1);
        assertThat(store.succeed(lost)).isFalse();
        assertThat(store.claim(tenant,4,Duration.ofSeconds(5)).claims()).isEmpty();
        assertThatThrownBy(()->db.execute("UPDATE outbox_jobs SET status='PENDING',processed_at=NULL WHERE id=?",id)).isInstanceOf(java.sql.SQLException.class);
    }
    @Test void concurrentRetryResultsFenceTheSameClaimAndPreserveOneOriginalJob() throws Exception {
        var id=enqueue("success",5); var claim=store.claim(tenant,1,Duration.ofSeconds(5)).claims().getFirst();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<OutboxStore.Outcome> retry=()-> { barrier.await(); return store.fail(claim,new JobFailure(Code.HANDLER_RETRYABLE,true),Duration.ofSeconds(1)); };
            var a=pool.submit(retry); var b=pool.submit(retry);
            assertThat(List.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(OutboxStore.Outcome.RETRY,OutboxStore.Outcome.STALE);
        }
        assertThat(status(id)).isEqualTo("PENDING"); assertThat(attempts(id)).isEqualTo(1);
        assertThat(db.value("SELECT count(*) FROM outbox_jobs WHERE tenant_id=?",tenant)).isEqualTo(1L);
    }
    @Test void successfulTerminalJobAndOriginalIntentCannotBeRewrittenOrResurrected() throws Exception {
        var id=enqueue("success",5); worker().pollOnce();
        assertThatThrownBy(()->db.execute("UPDATE outbox_jobs SET status='PENDING',processed_at=NULL WHERE id=?",id)).isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(()->db.execute("UPDATE outbox_jobs SET attempt_count=0 WHERE id=?",id)).isInstanceOf(java.sql.SQLException.class);
        var pending=enqueue("success",5);
        assertThatThrownBy(()->db.execute("UPDATE outbox_jobs SET payload='{\"schemaVersion\":1,\"data\":{}}' WHERE id=?",pending)).isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(()->db.execute("UPDATE outbox_jobs SET max_attempts=6 WHERE id=?",pending)).isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(()->db.execute("DELETE FROM outbox_jobs WHERE id=?",pending)).isInstanceOf(java.sql.SQLException.class);
    }
    @Test void retryEventuallySucceedsAndPersistedAttemptCounterAndBackoffRemainAuthoritative() throws Exception {
        var id=enqueue("retry",5); var metrics=new SimpleMeterRegistry(); var worker=worker(settings(List.of(tenant)),metrics);
        worker.pollOnce(); assertThat(status(id)).isEqualTo("PENDING"); assertThat(attempts(id)).isEqualTo(1);
        assertThat(metrics.get("jobs.retry").counter().count()).isEqualTo(1);
        assertThat(store.claim(tenant,4,Duration.ofSeconds(5)).claims()).isEmpty();
        due(id); worker.pollOnce();
        assertThat(status(id)).isEqualTo("SUCCEEDED"); assertThat(attempts(id)).isEqualTo(2);
        assertThat(metrics.get("jobs.succeeded").counter().count()).isEqualTo(1);
    }
    @Test void permanentFailureDoesNotBlockAnIndependentEligibleJob() throws Exception {
        var failed=enqueue("permanent",5); var good=enqueue("success",5); worker().pollOnce();
        assertThat(status(failed)).isEqualTo("DEAD"); assertThat(attempts(failed)).isEqualTo(1);
        assertThat(status(good)).isEqualTo("SUCCEEDED");
    }
    @Test void retryableErrorExhaustionBecomesDeadAndNeverResurrects() throws Exception {
        var id=enqueue("secret",1); worker().pollOnce();
        assertThat(status(id)).isEqualTo("DEAD"); assertThat(attempts(id)).isEqualTo(1);
        worker().pollOnce(); assertThat(handler.calls.get()).isEqualTo(1);
        assertThat(db.value("SELECT last_error FROM outbox_jobs WHERE id=?",id)).isEqualTo("HANDLER_FAILURE");
    }
    @ParameterizedTest @ValueSource(strings={"UNKNOWN_JOB_TYPE","UNSUPPORTED_PAYLOAD_VERSION","INVALID_PAYLOAD","INVALID_SCOPE"})
    void fabricatedJobFailsClosedBeforeInvokingHandler(String kind) throws Exception {
        var id=UUID.randomUUID();
        String type=kind.equals("UNKNOWN_JOB_TYPE")?"UNREGISTERED":"TEST_EFFECT";
        int version=kind.equals("UNSUPPORTED_PAYLOAD_VERSION")?2:1;
        var resource=kind.equals("INVALID_SCOPE")?foreignCustomer:customer;
        String extra=kind.equals("INVALID_PAYLOAD")?",\"secret\":\"private-bearer\"":"";
        String payload="{\"schemaVersion\":"+version+",\"data\":{\"customerId\":\""+resource+"\",\"behavior\":\"success\""+extra+"}}";
        db.execute("INSERT INTO outbox_jobs(id,tenant_id,job_type,aggregate_type,aggregate_id,payload,status,available_at,max_attempts) VALUES(?,?,?,'CUSTOMER',?,?::jsonb,'PENDING',now(),5)",id,tenant,type,resource,payload);
        worker().pollOnce();
        assertThat(status(id)).isEqualTo("DEAD"); assertThat(handler.calls.get()).isZero();
        assertThat(db.value("SELECT last_error FROM outbox_jobs WHERE id=?",id)).isEqualTo(kind);
    }
}
