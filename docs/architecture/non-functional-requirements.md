# Non-Functional Requirements

**Status:** Draft  
**Convention:** `NFR-###`

These requirements should guide architecture selection later. They do not prescribe a stack.

## Security and tenancy

- **NFR-001** — Tenant data isolation is mandatory.
- **NFR-002** — Authorization shall be enforced server-side for every protected operation.
- **NFR-003** — Customer users require customer-level scoping in addition to tenant scoping.
- **NFR-004** — Sensitive tokens/credentials shall not be stored in source control.
- **NFR-005** — Production traffic shall use TLS.
- **NFR-006** — Uploaded documents/photos shall not be publicly enumerable by default.
- **NFR-007** — Audit-relevant actions shall be attributable to actor/source.

## Mobile

- **NFR-010** — Driver mobile experience shall support iOS and Android.
- **NFR-011** — Critical operational actions shall tolerate temporary loss of connectivity.
- **NFR-012** — Offline synchronization shall be idempotent.
- **NFR-013** — Mobile UX should minimize typing and screen count for repetitive driver actions.
- **NFR-014** — Background location usage shall be limited to approved operational behavior.
- **NFR-015** — GPS/geofence failure must have a manual fallback.

## Performance

Initial design-partner scale is >100 movements/day, ~35 drivers, ~33 tractors and ~150 trailers. SaaS architecture should comfortably exceed this.

- **NFR-020** — Common trailer search should feel interactive under normal conditions.
- **NFR-021** — QR resolution and hook/drop confirmation should not require expensive synchronous processing.
- **NFR-022** — Dashboard queries should use derived/indexed state rather than replaying an unbounded event log on every request.
- **NFR-023** — Architecture should scale across multiple tenants without redesign of core tenancy.

Precise latency/SLA targets remain to be defined.

## Reliability and consistency

- **NFR-030** — Duplicate mobile retries must not create duplicate hook/drop/arrival/departure events.
- **NFR-031** — Important operational events require durable persistence.
- **NFR-032** — Derived current state must be recoverable/reconcilable from source events and corrections where feasible.
- **NFR-033** — Concurrent assignment/correction conflicts must have deterministic handling.
- **NFR-034** — File upload and operational event creation must fail safely if one succeeds and the other does not.

## Auditability

- **NFR-040** — Significant administrative corrections must preserve before/after or equivalent correction history.
- **NFR-041** — Event source must distinguish user action, mobile automation/geofence, system process and external integration.
- **NFR-042** — Offline events should preserve occurrence time and synchronization time where relevant.

## Privacy

- **NFR-050** — Collect only location data required for approved operational workflows.
- **NFR-051** — Continuous employee tracking shall not be enabled by default without validated business/legal requirements.
- **NFR-052** — Customer portal must expose only customer-scoped data.
- **NFR-053** — Data-retention policy must be explicit before production.

## Maintainability

- **NFR-060** — Domain logic should not be embedded exclusively in UI clients.
- **NFR-061** — Business rules should be testable independently of presentation.
- **NFR-062** — Schema and API changes require migration/backward-compatibility strategy where applicable.
- **NFR-063** — External vendors such as Samsara shall be isolated behind integration boundaries/adapters when introduced.
- **NFR-064** — Key architecture decisions shall be recorded as ADRs.

## Observability

- **NFR-070** — Production shall support structured application logs.
- **NFR-071** — Errors in background/offline synchronization shall be diagnosable.
- **NFR-072** — Alert generation and failed processing should be observable.
- **NFR-073** — Correlation identifiers should be considered for multi-step operational workflows.

## Backup and recovery

- **NFR-080** — Production data shall have a documented backup strategy.
- **NFR-081** — Restore procedure shall be tested before relying on the system operationally.
- **NFR-082** — Uploaded evidence requires a durability/retention strategy separate from database backups if stored externally.

## Compliance / legal

Items requiring later review before U.S. production:

- privacy policy for location data;
- employee/driver consent and company policy for personal-device location permissions;
- data-retention rules;
- timekeeping implications if shift data is used for payroll;
- document/evidence retention obligations.

No legal conclusion is encoded in this document.
