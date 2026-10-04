# MVP Implementation Backlog

**Status:** Accepted backlog structure  
**Tracking issue:** #66

The implementation backlog converts the accepted specification into GitHub Epics and bounded implementation Issues.

## Epics and child issues

### #1 — Platform foundation
- #15 Spring Boot modular monolith scaffold
- #16 React web scaffold
- #17 Flutter mobile scaffold
- #18 CI quality gates + OpenAPI validation
- #19 PostgreSQL/Flyway/Testcontainers baseline
- #20 environment configuration/secrets templates
- #21 observability/request correlation baseline

### #2 — Identity, tenancy and authorization
- #22 Firebase token verification / internal User
- #23 Tenant membership and role persistence
- #24 capability + Customer-scope authorization
- #25 invitation/onboarding
- #26 TenantContext + PostgreSQL RLS

### #3 — Master data and equipment
- #27 Customers/Sites/Operational Locations
- #28 Drivers/Tractors
- #29 Trailers/QR identities
- #30 Tenant settings + Admin master-data web

### #4 — Orders and manifests
- #31 Orders/cargo/requested Stops
- #32 load manifests / Order allocations
- #33 Order edit/cancellation/completion

### #5 — Trips and dispatch
- #34 Trip/executable Stop planning
- #35 assignments + Trailer substitution authorization
- #36 reroute + active cancellation
- #37 dispatch board + Trip detail web

### #6 — Trailer movement/current state
- #38 Hook/Drop coupling transactions
- #39 movement events/current projections
- #40 corrections/OOS/load-state commands
- #41 Trailer search/detail/timeline

### #7 — Driver mobile Road Mode
- #42 mobile auth/Tenant/local-cache shell
- #43 Shift + Pre/Post-Trip
- #44 Trip execution
- #45 POD/Fuel/exceptions
- #46 offline command queue/replay
- #47 geofence-assisted arrival + Driver updates

### #8 — Yard mobile mode
- #48 Yard context + Trailer Move
- #49 discrepancy correction + OOS

### #9 — Yard Check
- #50 snapshot/reconciliation backend
- #51 mobile scan/offline replay

### #10 — Documents/evidence
- #52 R2 upload/finalize/download
- #53 evidence validation
- #54 mobile media queue

### #11 — Alerts/notifications/jobs
- #55 PostgreSQL outbox/job worker
- #56 Alerts/device registration/FCM
- #57 stale Trailer detector

### #12 — Customer portal
- #58 Customer portal shell/scope
- #59 Customer Order/delivery workflows
- #60 Customer Trailer/delivery/document views

### #13 — Audit/history
- #61 audit writer/query API
- #62 Admin audit + operational history views

### #14 — Production hardening
- #63 backup automation/restore runbook
- #64 Staging/Production deployment gates + monitoring
- #65 final production-readiness gate

## Rules

1. Work from a child Issue, not directly from the Epic.
2. Read all documents referenced by the Issue before coding.
3. Do not silently expand scope.
4. If an Issue conflicts with an Accepted ADR/state machine/schema/API contract, stop and surface the conflict.
5. Every behavior change requires tests.
6. Schema changes require Flyway migration.
7. Tenant/customer authorization requires isolation tests where applicable.
8. Mobile retry-sensitive mutations require idempotency tests.
9. Do not close an Issue until its acceptance criteria are satisfied.
