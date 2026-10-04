# ADR-015 — Observability and Operational Diagnostics

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS must diagnose:

- backend errors;
- web/mobile crashes;
- failed push processing;
- offline synchronization failures;
- document upload issues;
- operational background jobs.

Audit history is not the same as technical observability.

## Decision

Use a layered observability baseline:

### Backend

- structured JSON application logs to stdout;
- Spring Boot Actuator health/readiness endpoints;
- Micrometer instrumentation;
- OpenTelemetry-compatible traces/metrics where practical;
- correlation/request ID propagated through logs and API error responses.

### Web / Mobile

Use **Sentry** for client error/crash reporting.

Mobile reports should include safe sync diagnostics such as:

- command type;
- error code;
- retry count;
- application version;
- device/platform version;

but never BOL image contents, auth tokens or secrets.

### Backend exception reporting

Sentry may also collect backend exceptions if useful, while structured logs remain the primary backend diagnostic record.

## Audit separation

`audit_events`, movement events and business timelines are business records.

Observability logs/traces are technical diagnostics.

Do not rely on Sentry/log retention as operational audit retention.

## Correlation

Generate/accept a request ID for API requests.

Include it in:

- backend structured logs;
- Problem Details `traceId`;
- asynchronous job context where practical.

For offline commands, also log the `clientOperationId` / idempotency key in a safe structured field.

## Metrics to prioritize

- API error rate/latency;
- DB connection/pool health;
- background job failures;
- FCM delivery failures where observable;
- pending/failed media uploads;
- offline sync retry/conflict rate;
- oldest pending mobile command age;
- R2 upload-finalization failures.

## Health endpoints

Provide separate liveness/readiness behavior.

Readiness should fail when the application cannot safely serve required dependencies such as PostgreSQL.

Do not expose sensitive environment details in public health responses.

## Alerting

Initial production alerts should cover:

- backend unavailable;
- repeated DB connectivity failure;
- sustained elevated 5xx;
- scheduled job failure;
- abnormal document-upload failure;
- backup job failure.

Exact thresholds can be tuned from production baselines.

## Consequences

### Positive

- vendor-neutral backend instrumentation path;
- client crash visibility;
- correlation across API/offline operations;
- clear separation from business audit.

### Negative / trade-offs

- multiple diagnostic surfaces;
- privacy filtering must be maintained;
- metrics/trace backend may evolve after initial deployment.

## Follow-up

- add observability configuration to backend/web/mobile scaffold;
- define log field standard;
- configure production alert routing;
- define log/trace retention outside business retention policy.
