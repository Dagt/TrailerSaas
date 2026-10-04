# ADR-022 — PostgreSQL-Backed Outbox and Background Jobs

**Status:** Accepted  
**Date:** 2026-10-04

## Context

TrailerSaaS has asynchronous side effects and scheduled work such as:

- FCM push delivery;
- stale-Trailer detection;
- alert fan-out;
- orphan document cleanup;
- future retention processing;
- future integration webhook processing.

The MVP should not introduce Kafka, RabbitMQ or Redis solely to run these jobs.

At the same time, important side effects should not rely on fragile in-memory events that disappear if the process crashes after the business transaction commits.

## Decision

Use PostgreSQL as the initial durable background-work/outbox store.

For side effects that must occur after a successful business transaction:

1. persist the business state;
2. persist an outbox/job record in the **same PostgreSQL transaction**;
3. background worker claims pending rows;
4. performs the external side effect;
5. marks success or schedules retry.

## Candidate table

Conceptual:

```text
outbox_jobs
-----------
id                  uuid PK
tenant_id           uuid NULL
job_type            varchar NOT NULL
aggregate_type      varchar NULL
aggregate_id        uuid NULL
payload             jsonb NOT NULL
status              varchar NOT NULL
available_at        timestamptz NOT NULL
attempt_count       integer NOT NULL
last_error          text NULL
created_at          timestamptz NOT NULL
processed_at        timestamptz NULL
```

Status:

- PENDING
- PROCESSING
- SUCCEEDED
- DEAD

## Claiming work

Workers should claim work safely using PostgreSQL locking such as:

```sql
FOR UPDATE SKIP LOCKED
```

or an equivalent proven pattern.

This allows more than one backend instance later without duplicate concurrent processing.

## Delivery semantics

Assume **at-least-once** execution for external side effects.

Therefore handlers must be idempotent where duplicate execution matters.

Examples:

- FCM may receive a retried notification attempt;
- integration webhooks use idempotency keys/external IDs;
- cleanup jobs verify current object/reference state before deleting.

## Retry

Use bounded retry with backoff.

After configured attempts, move job to DEAD and surface an operational/technical alert rather than retry forever silently.

## Scheduled scans

Jobs such as stale-Trailer detection may be initiated by a scheduler, but resulting notifications/side effects still use durable job/outbox rows where reliability matters.

## Tenant isolation

Tenant-owned jobs carry `tenant_id`.

Workers execute Tenant-scoped domain work under the same TenantContext/RLS rules defined in ADR-013.

Platform/global maintenance jobs are explicit and use controlled administrative execution paths.

## No external broker for MVP

Rejected initially:

- Kafka;
- RabbitMQ;
- Redis Streams;
- cloud queue service.

Introduce a dedicated broker only if measured throughput, isolation or integration requirements justify it.

## Consequences

### Positive

- no new infrastructure service;
- transactionally reliable side-effect scheduling;
- supports multiple backend instances;
- observable retries/dead jobs.

### Negative / trade-offs

- PostgreSQL also carries background-work load;
- worker implementation/retry cleanup is required;
- not intended for very high-throughput streaming.

## Follow-up

- add outbox schema/migration during scaffold;
- define job types and retry policy;
- add metrics for pending age/failures/dead jobs;
- integration-test crash between business commit and external side effect.
