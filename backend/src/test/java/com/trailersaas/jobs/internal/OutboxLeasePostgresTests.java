package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class OutboxLeasePostgresTests extends JobsFixture {
    @Test void expiredWorkerCannotAcknowledgeEvenBeforeAnotherWorkerReclaims() throws Exception {
        var id=enqueue("success",5); var lost=store.claim(tenant,1,Duration.ofMillis(100)).claims().getFirst();
        expired(lost);
        assertThat(store.succeed(lost)).isFalse();
        assertThat(store.fail(lost,new JobFailure(JobFailure.Code.HANDLER_PERMANENT,false),Duration.ofSeconds(1))).isEqualTo(OutboxStore.Outcome.STALE);
        assertThat(status(id)).isEqualTo("PROCESSING"); assertThat(attempts(id)).isEqualTo(1);
        var next=store.claim(tenant,1,Duration.ofSeconds(5)).claims().getFirst();
        assertThat(next.attempt()).isEqualTo(2); assertThat(next.token()).isNotEqualTo(lost.token());
    }
    @Test void competingRecoveryClaimsCreateOnlyOneNewValidLease() throws Exception {
        var id=enqueue("success",5); var lost=store.claim(tenant,1,Duration.ofMillis(100)).claims().getFirst(); expired(lost);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<OutboxStore.Batch> recovery=()-> { barrier.await(); return store.claim(tenant,1,Duration.ofSeconds(5)); };
            var a=pool.submit(recovery); var b=pool.submit(recovery);
            var claims=new ArrayList<JobClaim>(); claims.addAll(a.get(5,TimeUnit.SECONDS).claims()); claims.addAll(b.get(5,TimeUnit.SECONDS).claims());
            assertThat(claims).hasSize(1); assertThat(claims.getFirst().token()).isNotEqualTo(lost.token());
            assertThat(attempts(id)).isEqualTo(2);
        }
    }
    @ParameterizedTest @ValueSource(strings={"locked_by=NULL","locked_until=NULL","status='SUCCEEDED'","status='DEAD'","attempt_count=0"})
    void invalidProcessingLeaseAndTerminalCombinationsCannotCommit(String change) throws Exception {
        var id=enqueue("success",5); var claim=store.claim(tenant,1,Duration.ofSeconds(5)).claims().getFirst();
        assertThatThrownBy(()->db.execute("UPDATE outbox_jobs SET "+change+" WHERE id=?",id)).isInstanceOf(java.sql.SQLException.class);
        assertThat(status(id)).isEqualTo("PROCESSING"); assertThat(attempts(id)).isEqualTo(1);
        assertThat(store.succeed(claim)).isTrue();
    }
    @Test void workerTimeoutSettingsAreTransactionLocalAndDoNotLeakIntoBusinessConnection() throws Exception {
        var id=enqueue("success",5); worker().pollOnce(); assertThat(status(id)).isEqualTo("SUCCEEDED");
        transactions.execute(other,()-> {
            assertThat(jdbc.queryForObject("SELECT current_setting('statement_timeout')",String.class)).isEqualTo("0");
            assertThat(jdbc.queryForObject("SELECT current_setting('lock_timeout')",String.class)).isEqualTo("0");
            assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs",UUID.class)).doesNotContain(id);
            return null;
        });
    }
}
