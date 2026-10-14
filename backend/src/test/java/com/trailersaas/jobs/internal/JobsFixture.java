package com.trailersaas.jobs.internal;

import com.trailersaas.*;
import com.trailersaas.identity.TenantTransactionExecutor;
import com.trailersaas.jobs.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(JobsFixture.Configuration.class)
abstract class JobsFixture extends PostgresIntegrationTest {
    static final JobType<Payload> TYPE=new JobType<>("TEST_EFFECT",1,Payload.class,"CUSTOMER");
    record Payload(UUID customerId, String behavior) {}
    @Autowired DurableJobPublisher publisher;
    @Autowired OutboxStore store;
    @Autowired JobRegistry registry;
    @Autowired TenantTransactionExecutor transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired Probe handler;
    @Autowired PlatformTransactionManager manager;
    EquipmentFixtures db;
    UUID tenant, other, customer, foreignCustomer;
    final List<DurableJobWorker> workers=new ArrayList<>();

    @BeforeEach void seedJobs() throws Exception {
        db=new EquipmentFixtures(flyway); tenant=db.tenant(); other=db.tenant();
        customer=customer(tenant); foreignCustomer=customer(other);
        handler.calls.set(0); handler.entered=null; handler.release=null;
    }
    @AfterEach void closeWorkers() { workers.forEach(DurableJobWorker::stop); workers.clear(); }
    UUID customer(UUID t) throws Exception {
        var id=UUID.randomUUID(); db.execute("INSERT INTO customers VALUES(?,?,'Before','ACTIVE',now(),now(),0)",id,t); return id;
    }
    UUID enqueue(String behavior, int attempts) {
        return transactions.execute(tenant,()->publisher.enqueue(tenant,TYPE,new JobAggregate("CUSTOMER",customer),
                new Payload(customer,behavior),Instant.now().minusSeconds(1),attempts));
    }
    String status(UUID id) throws Exception { return (String)db.value("SELECT status FROM outbox_jobs WHERE id=?",id); }
    int attempts(UUID id) throws Exception { return (Integer)db.value("SELECT attempt_count FROM outbox_jobs WHERE id=?",id); }
    void expired(JobClaim claim) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(10))
                .until(()->transactions.execute(claim.tenantId(),()->jdbc.queryForObject(
                        "SELECT locked_until<=clock_timestamp() FROM outbox_jobs WHERE id=?",Boolean.class,claim.id())));
    }
    JobsProperties settings(List<UUID> tenants) { return new JobsProperties(true,tenants,Duration.ofSeconds(5),4,4,
            Duration.ofSeconds(1),Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(2)); }
    DurableJobWorker worker() { return worker(settings(List.of(tenant)),new SimpleMeterRegistry()); }
    DurableJobWorker worker(JobsProperties settings, SimpleMeterRegistry metrics) {
        var worker=new DurableJobWorker(store,registry,settings,new JobMetrics(metrics,settings)); workers.add(worker); return worker;
    }
    void due(UUID id) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20))
                .until(()->transactions.execute(tenant,()->jdbc.queryForObject(
                        "SELECT available_at<=clock_timestamp() FROM outbox_jobs WHERE id=?",Boolean.class,id)));
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Configuration {
        @Bean Probe probe(JdbcTemplate jdbc) { return new Probe(jdbc); }
    }
    static class Probe implements JobHandler<Payload> {
        final JdbcTemplate jdbc;
        final AtomicInteger calls=new AtomicInteger();
        volatile CountDownLatch entered, release;
        Probe(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        public JobType<Payload> type() { return TYPE; }
        public void validate(UUID tenant, JobAggregate aggregate, Payload payload) {
            if (payload.customerId()==null || payload.behavior()==null || aggregate==null || !aggregate.id().equals(payload.customerId()) ||
                    !Set.of("success","retry","permanent","secret","blocked","uncooperative").contains(payload.behavior()) ||
                    jdbc.queryForObject("SELECT count(*) FROM customers WHERE id=? AND tenant_id=?",Long.class,payload.customerId(),tenant)!=1L) {
                throw new JobFailure(JobFailure.Code.INVALID_SCOPE,false);
            }
        }
        public void execute(JobExecution<Payload> job) {
            int attempt=calls.incrementAndGet();
            if (job.payload().behavior().equals("retry") && attempt==1) throw new JobFailure(JobFailure.Code.HANDLER_RETRYABLE,true);
            if (job.payload().behavior().equals("permanent")) throw new JobFailure(JobFailure.Code.HANDLER_PERMANENT,false);
            if (job.payload().behavior().equals("secret")) throw new IllegalStateException("private-bearer ?X-Amz-Signature=secret https://signed.example/private");
            if (job.payload().behavior().equals("uncooperative")) {
                entered.countDown();
                boolean finished=false;
                while(!finished) {
                    try { finished=release.await(10,TimeUnit.SECONDS); }
                    catch (InterruptedException deliberatelyIgnored) { /* test-only uncooperative downstream */ }
                }
            }
            if (job.payload().behavior().equals("blocked")) {
                entered.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new JobFailure(JobFailure.Code.HANDLER_TIMEOUT,true); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new JobFailure(JobFailure.Code.HANDLER_TIMEOUT,true); }
            }
            job.inTenantTransaction(()->jdbc.update("UPDATE customers SET name=?,version=version+1 WHERE id=? AND name<>?",
                    "effect-"+job.jobId(),job.payload().customerId(),"effect-"+job.jobId()));
        }
    }
}
