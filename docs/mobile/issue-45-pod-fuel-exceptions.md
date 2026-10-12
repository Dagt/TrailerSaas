# Issue #45 — POD, Fuel and operational exceptions

Baseline: `4a77d91e83e7f5978371234af08ca5b6584e454e`, merged PR #102.
Working tree was clean; no unpublished #45 branch, stash or implementation was found.
Branch: `issue-45-pod-fuel-operational-exceptions`. No merge authorized.

## Acceptance plan (prepared before implementation)

| Criterion | Existing code | Required change | Risk | Verification |
| --- | --- | --- | --- | --- |
| Delivery evidence | OperationalEvidencePolicy, V20, private R2 | Production delivery application orchestration and typed Trip completion port | False completion, replay after revocation | Real HTTP/PG evidence, transaction proof, lost ACK |
| Order fulfillment | OrderLifecyclePort, requested Stops and Trip mappings | Required-delivery projection and immutable fulfillment facts | Partial work counted as all work; cancellation lock inversion | Multi-Stop/multi-Order, missing obligations, concurrent cancellation and rollback |
| Trip completion | DriverTripExecutionService | Reuse existing completion transition and ordered Stops | BASE_RETURN skipped; Shift implicitly closed | Real execution lifecycle and independent Shift |
| Fuel | validateFuel, FUEL_EVENT_CREATE | V22 event/receipt association and strict own-Driver command | Foreign receipt/Tractor; duplicate event | PostgreSQL, authority revocation, replay, concurrency |
| Exceptions | validateDriverDamage, EXCEPTION_REPORT | Append-only categories/context/document references | Arbitrary state editor | Every category, optional Stop, damage evidence, unchanged physical projections |
| Mobile evidence actions | Drift v5, MediaStore, camera, immutable materialization | Typed delivery/Fuel/exception capture and replay adapters | Request/key changes after sending | Real SQLite/files/HTTP, restart, dependencies, lost ACK |
| Driver Updates | Own targeted GET updates, ascending keyset | Durable namespaced incremental history and Road UI | Missed push or reassignment loses instructions | Cursor/reconnect/restart, safe retained history |
| Contracts/security | Strict DTOs, existing ledger/RLS | OpenAPI implemented shapes, V22 exact inventories | Inadvertent permission or schema weakening | Full Backend/Mobile/Web/OpenAPI and module boundaries |

## Dependency and lock design

Inspections provides the application orchestration already used by #43 and calls
Documents, Trips and Orders through typed public ports. Trips remains independent
of Documents. Orders owns fulfillment and its state machine; Trips owns Stop/Trip
transitions. Delivery first locks the associated Orders in UUID order, then the
Trip, current Driver and ordered Stops. This preserves cancellation's Order-before-
Trip ordering. Evidence is validated before the existing ledger; its winner calls
recordDelivery around the real completion/fulfillment operation in one transaction.

## Recoverable checkpoints

A: V22 persistence, exact catalogs and RLS tests. B: production POD/evidence/ledger.
C: fulfillment and joint rollback. D: Fuel/exceptions. E: Flutter forms and replay.
F: durable Driver Updates. G: contracts, independent audit and final-head CI.
Every substantial checkpoint is committed and pushed. This plan does not claim
implementation complete. #47–#51, #55, retention, OCR and generic editors are excluded.

### Checkpoint A

V22 creates Fuel/receipt history, operational exceptions and their document edges,
and durable Order delivery fulfillment facts. Runtime access is SELECT/INSERT only;
each table has the accepted Tenant-pinned RLS predicate. The optional exception Stop
has a composite FK to its exact Trip. Receipt reuse and duplicate fulfillment facts
are constrained. Historical exact table/Flyway/RLS inventories now include V22;
V1–V21 are unchanged and all exact/security assertions remain.

Affected validation passed: RoadSchemaPostgresTests (8), TenantRlsPostgresTests (17),
ShiftSchemaPostgresTests (7), TrailerSaasApplicationTests (7): 39 tests, zero failures,
errors or skipped, PostgreSQL 18, BUILD SUCCESS. Next: production typed Delivery
port, evidence/ledger orchestration, fulfillment and real HTTP transaction tests.

### Checkpoints B/C/D: initial Backend integration

The production delivery endpoint now validates evidence before the ledger and uses
recordDelivery around Trips' actual shared completion transition. Stop/event,
Order fulfillment facts/projections, Trip and evidence commit together. Orders
locks precede Trip locks. Required destinations use distinct matching visits;
every actual DELIVERY allocation must also be successful with accepted evidence.
No mapping-free or partially delivered Order is administratively closed.

Fuel records the finalized own receipt with the existing type policy; exceptions
record only append-only facts and optional validated damage references. The three
strict commands require current own Driver/capabilities and stable ledger identity.
No physical, Shift, cancellation or commercial editor is introduced.

33 new real PostgreSQL/HTTP/S3 command tests pass, including both POD modes,
multi-Stop/two-visit fulfillment, shared Orders, BASE_RETURN, concurrent identical
keys, revoked assignment, strict DTOs, Fuel, all exception categories, and rollback
at event/fulfillment/evidence insertion and deferred commit. Earlier execution,
inspection and module regression classes passed 61 tests. Next: extend terminal
replay/locking edge coverage and complete Flutter schema v6, forms, typed replay
and Driver Updates, then all final-head CI gates. No PR or merge yet.
