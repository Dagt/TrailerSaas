package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class OutboxPublisherPostgresTests extends JobsFixture {
    @Test void enqueueRequiresAlreadyScopedBusinessTransaction() {
        assertThatThrownBy(()->publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",customer),
                new Payload(customer,"success"),Instant.now(),null)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->publisher.enqueue(tenant,TYPE,
                new JobAggregate("CUSTOMER",customer),new Payload(customer,"success"),Instant.now(),null))).isInstanceOf(JobFailure.class);
    }
    UUID createBusinessRecord() {
        var id=UUID.randomUUID(); jdbc.update("INSERT INTO customers VALUES(?,?,'Transactional','ACTIVE',now(),now(),0)",id,tenant); return id;
    }
    @Test void realBusinessMutationAndRequiredOutboxCommitTogether() throws Exception {
        var ids=transactions.execute(tenant,()-> {
            var c=createBusinessRecord();
            var job=publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",c),new Payload(c,"success"),Instant.now(),null);
            return new UUID[]{c,job};
        });
        assertThat(db.value("SELECT count(*) FROM customers WHERE id=?",ids[0])).isEqualTo(1L);
        assertThat(status(ids[1])).isEqualTo("PENDING");
        assertThat(attempts(ids[1])).isZero();
    }
    @Test void crashBeforeCommitRollsBackBothBusinessAndOutbox() throws Exception {
        UUID[] ids=new UUID[2];
        assertThatThrownBy(()->transactions.execute(tenant,()-> {
            ids[0]=createBusinessRecord();
            ids[1]=publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",ids[0]),new Payload(ids[0],"success"),Instant.now(),5);
            throw new IllegalStateException("simulated process boundary");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(db.value("SELECT count(*) FROM customers WHERE id=?",ids[0])).isEqualTo(0L);
        assertThat(db.value("SELECT count(*) FROM outbox_jobs WHERE id=?",ids[1])).isEqualTo(0L);
    }
    @Test void anotherWorkerCannotClaimRequiredOutboxBeforeBusinessCommit() throws Exception {
        var inserted=new java.util.concurrent.CountDownLatch(1);
        var commit=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var transaction=pool.submit(()->transactions.execute(tenant,()-> {
                var c=createBusinessRecord();
                var id=publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",c),new Payload(c,"success"),Instant.now(),5);
                inserted.countDown();
                try { if (!commit.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("commit barrier timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return id;
            }));
            try {
                assertThat(inserted.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(store.claim(tenant,4,java.time.Duration.ofSeconds(5)).claims()).isEmpty();
            } finally { commit.countDown(); }
            var id=transaction.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(store.claim(tenant,4,java.time.Duration.ofSeconds(5)).claims()).extracting(JobClaim::id).containsExactly(id);
        }
    }
    @Test void actualOutboxWriteFailureRollsBackBusinessEvenIfCallerCatchesIt() throws Exception {
        UUID[] business=new UUID[1];
        db.execute("REVOKE INSERT ON outbox_jobs FROM "+RUNTIME_USERNAME);
        try {
            assertThatThrownBy(()->transactions.execute(tenant,()-> {
                business[0]=createBusinessRecord();
                try { publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",business[0]),new Payload(business[0],"success"),Instant.now(),5); }
                catch (org.springframework.dao.DataAccessException expected) { /* proxy must still mark rollback-only */ }
                return null;
            })).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
            assertThat(db.value("SELECT count(*) FROM customers WHERE id=?",business[0])).isEqualTo(0L);
        } finally { db.execute("GRANT INSERT ON outbox_jobs TO "+RUNTIME_USERNAME); }
    }
    @ParameterizedTest @ValueSource(ints={0,21,-1})
    void boundedAttemptsRejectInvalidProducerPolicy(int count) {
        assertThatThrownBy(()->enqueue("success",count)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void foreignTenantAggregateAndIllegalGlobalContextFailClosed() {
        assertThatThrownBy(()->transactions.execute(tenant,()->publisher.enqueue(tenant,TYPE,
                new JobAggregate("CUSTOMER",foreignCustomer),new Payload(foreignCustomer,"success"),Instant.now(),5)))
                .isInstanceOf(JobFailure.class);
        assertThatThrownBy(()->transactions.execute(tenant,()->publisher.enqueue(null,TYPE,
                new JobAggregate("CUSTOMER",customer),new Payload(customer,"success"),Instant.now(),5))).isInstanceOf(JobFailure.class);
        assertThatThrownBy(()->transactions.execute(tenant,()->publisher.enqueue(other,TYPE,
                new JobAggregate("CUSTOMER",foreignCustomer),new Payload(foreignCustomer,"success"),Instant.now(),5))).isInstanceOf(JobFailure.class);
    }
    @Test void unregisteredVersionTypeAndAggregateAreRejected() {
        for (var type : java.util.List.of(new JobType<>("UNKNOWN",1,Payload.class,"CUSTOMER"),new JobType<>("TEST_EFFECT",2,Payload.class,"CUSTOMER"))) {
            assertThatThrownBy(()->transactions.execute(tenant,()->publisher.enqueue(tenant,type,new JobAggregate("CUSTOMER",customer),
                    new Payload(customer,"success"),Instant.now(),5))).isInstanceOf(JobFailure.class);
        }
        assertThatThrownBy(()->transactions.execute(tenant,()->publisher.enqueue(tenant,TYPE,new JobAggregate("TRAILER",customer),
                new Payload(customer,"success"),Instant.now(),5))).isInstanceOf(JobFailure.class);
    }
    @Test void explicitFutureScheduleSurvivesCommitAndIsNotClaimedEarly() throws Exception {
        var future=Instant.now().plusSeconds(3600);
        var id=transactions.execute(tenant,()->publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",customer),new Payload(customer,"success"),future,5));
        assertThat(store.claim(tenant,4,java.time.Duration.ofSeconds(5)).claims()).isEmpty();
        assertThat(status(id)).isEqualTo("PENDING");
    }
}
