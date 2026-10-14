package com.trailersaas.jobs.internal;

import com.trailersaas.identity.TenantTransactionExecutor;
import com.trailersaas.jobs.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class OutboxStore {
    final JdbcTemplate jdbc;
    final TenantTransactionExecutor transactions;
    OutboxStore(JdbcTemplate jdbc, TenantTransactionExecutor transactions) { this.jdbc=jdbc; this.transactions=transactions; }
    private <T> T workerTransaction(UUID tenant, Supplier<T> work) {
        return transactions.execute(tenant,()-> {
            jdbc.queryForObject("SELECT set_config('statement_timeout','5s',true)",String.class);
            jdbc.queryForObject("SELECT set_config('lock_timeout','1s',true)",String.class);
            return work.get();
        });
    }

    void requireTenant(UUID tenant) {
        if (tenant==null || !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NULLIF(current_setting('app.current_tenant_id',true),'')::uuid=?
                  AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL
                  AND EXISTS(SELECT 1 FROM tenants WHERE id=?)
                """,Boolean.class,tenant,tenant))) throw new JobFailure(JobFailure.Code.INVALID_SCOPE,false);
    }
    void insert(UUID id, UUID tenant, JobType<?> type, JobAggregate aggregate, String payload, Instant at, int attempts) {
        requireTenant(tenant);
        jdbc.update("""
                INSERT INTO outbox_jobs(id,tenant_id,job_type,aggregate_type,aggregate_id,payload,status,available_at,max_attempts)
                VALUES(?,?,?,?,?,?::jsonb,'PENDING',?,?)
                """,id,tenant,type.name(),aggregate==null?null:aggregate.type(),aggregate==null?null:aggregate.id(),payload,Timestamp.from(at),attempts);
    }
    record Batch(List<JobClaim> claims, int recovered, int exhausted) {}
    Batch claim(UUID tenant, int limit, Duration lease) {
        if (limit<1 || limit>100 || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("Invalid claim bounds");
        return workerTransaction(tenant, () -> {
            requireTenant(tenant);
            int dead=jdbc.update("""
                WITH expired AS (
                  SELECT id FROM outbox_jobs WHERE tenant_id=? AND status='PROCESSING'
                    AND locked_until<=clock_timestamp() AND attempt_count>=max_attempts
                  ORDER BY locked_until,id FOR UPDATE SKIP LOCKED LIMIT ?)
                UPDATE outbox_jobs j SET status='DEAD',locked_by=NULL,locked_until=NULL,
                  processed_at=clock_timestamp(),last_error='LEASE_EXHAUSTED'
                FROM expired e WHERE j.id=e.id
                """,tenant,limit);
            var claims=jdbc.query("""
                WITH eligible AS (
                  SELECT id,status='PROCESSING' AS recovered FROM outbox_jobs
                  WHERE tenant_id=? AND attempt_count<max_attempts AND
                    ((status='PENDING' AND available_at<=clock_timestamp()) OR
                     (status='PROCESSING' AND locked_until<=clock_timestamp()))
                  ORDER BY CASE WHEN status='PROCESSING' THEN locked_until ELSE available_at END,id
                  FOR UPDATE SKIP LOCKED LIMIT ?)
                UPDATE outbox_jobs j SET status='PROCESSING',locked_by=gen_random_uuid(),
                  locked_until=clock_timestamp()+(? * interval '1 millisecond'),
                  attempt_count=j.attempt_count+1,
                  last_error=CASE WHEN e.recovered THEN 'LEASE_EXPIRED' ELSE j.last_error END
                FROM eligible e WHERE j.id=e.id
                RETURNING j.*,e.recovered
                """,(rs,n) -> new AbstractMap.SimpleImmutableEntry<>(new JobClaim(
                    rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),rs.getString("job_type"),
                    rs.getString("aggregate_type")==null?null:new JobAggregate(rs.getString("aggregate_type"),rs.getObject("aggregate_id",UUID.class)),
                    rs.getString("payload"),rs.getInt("attempt_count"),rs.getInt("max_attempts"),
                    rs.getObject("locked_by",UUID.class),rs.getTimestamp("locked_until").toInstant()),rs.getBoolean("recovered")),
                    tenant,limit,lease.toMillis());
            return new Batch(claims.stream().map(Map.Entry::getKey).toList(),
                    (int)claims.stream().filter(Map.Entry::getValue).count(),dead);
        });
    }
    boolean succeed(JobClaim claim) {
        return workerTransaction(claim.tenantId(), () -> {
            requireTenant(claim.tenantId());
            return jdbc.update("""
                UPDATE outbox_jobs SET status='SUCCEEDED',locked_by=NULL,locked_until=NULL,processed_at=clock_timestamp()
                WHERE id=? AND tenant_id=? AND status='PROCESSING' AND locked_by=? AND locked_until>clock_timestamp()
                """,claim.id(),claim.tenantId(),claim.token())==1;
        });
    }
    enum Outcome { RETRY, DEAD, STALE }
    Outcome fail(JobClaim claim, JobFailure failure, Duration delay) {
        boolean dead=!failure.retryable() || claim.attempt()>=claim.maxAttempts();
        return workerTransaction(claim.tenantId(), () -> {
            requireTenant(claim.tenantId());
            int changed=jdbc.update("""
                UPDATE outbox_jobs SET status=?,locked_by=NULL,locked_until=NULL,
                  processed_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                  available_at=CASE WHEN ? THEN available_at ELSE clock_timestamp()+(? * interval '1 millisecond') END,
                  last_error=?
                WHERE id=? AND tenant_id=? AND status='PROCESSING' AND locked_by=? AND locked_until>clock_timestamp()
                """,dead?"DEAD":"PENDING",dead,dead,delay.toMillis(),failure.code().name(),claim.id(),claim.tenantId(),claim.token());
            return changed==0?Outcome.STALE:dead?Outcome.DEAD:Outcome.RETRY;
        });
    }
    <T> T fencedTransaction(JobClaim claim, Supplier<T> callback) {
        return workerTransaction(claim.tenantId(), () -> {
            requireTenant(claim.tenantId());
            var ids=jdbc.queryForList("""
                SELECT id FROM outbox_jobs WHERE id=? AND tenant_id=? AND status='PROCESSING'
                    AND locked_by=? AND locked_until>clock_timestamp() FOR UPDATE
                """,UUID.class,claim.id(),claim.tenantId(),claim.token());
            if (ids.isEmpty() || Thread.currentThread().isInterrupted()) throw new JobFailure(JobFailure.Code.LEASE_EXPIRED,true);
            T result=callback.get();
            if (Thread.currentThread().isInterrupted() || jdbc.queryForObject(
                    "SELECT locked_until>clock_timestamp() FROM outbox_jobs WHERE id=?",Boolean.class,claim.id())!=Boolean.TRUE) {
                throw new JobFailure(JobFailure.Code.LEASE_EXPIRED,true);
            }
            return result;
        });
    }
    record Statistics(long pending,long processing,long succeeded,long dead,double oldestDueAge) {}
    Statistics statistics(UUID tenant) {
        return workerTransaction(tenant, () -> {
            requireTenant(tenant);
            return jdbc.queryForObject("""
                SELECT count(*) FILTER(WHERE status='PENDING'),count(*) FILTER(WHERE status='PROCESSING'),
                  count(*) FILTER(WHERE status='SUCCEEDED'),count(*) FILTER(WHERE status='DEAD'),
                  COALESCE(GREATEST(0,EXTRACT(EPOCH FROM clock_timestamp()-min(available_at)
                    FILTER(WHERE status='PENDING' AND available_at<=clock_timestamp()))),0)
                FROM outbox_jobs WHERE tenant_id=?
                """,(rs,n)->new Statistics(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getDouble(5)),tenant);
        });
    }
}
