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

## Implemented API, boundaries and authorization

| Endpoint | Gate and fresh context | Required evidence | Result |
| --- | --- | --- | --- |
| POST /stops/{stopId}/delivery-completions | POD_UPLOAD + TRIP_EXECUTE_ASSIGNED; own active Driver/current assignment, exact IN_SERVICE DELIVERY/AT_STOP | SIGNED_BOL: BOL; NO_RECEIVER_AVAILABLE: distinct BOL + DELIVERY_PHOTO + Unicode-nonblank comment | stopId/tripId/statuses/versions/completedAt |
| POST /driver/me/fuel-events | FUEL_EVENT_CREATE + own assigned work; executing Trip, current active Tractor, own open Shift | finalized own FUEL_RECEIPT linked to exact Trip | immutable id/Trip/Tractor/odometer/receipt/occurrence/creation |
| POST /trips/{tripId}/exceptions | EXCEPTION_REPORT + own assigned work; optional Stop belongs to exact Trip | damage: >=1 DAMAGE_PHOTO; other categories: optional validated photos | immutable fact, category/comment/doc IDs/occurrence/recording |

Strict JSON rejects unknown/coerced/duplicate/trailing data and query parameters.
SIGNED_BOL cannot use generic POD or no-receiver fields. All UUID idempotency keys
use the existing ledger. Replay revalidates current resource authority/documents
before returning stored information. Fuel/damage terminal replay needs an actual
committed own ledger result, not a client flag or historical upload authority.
No new capability, role mapping, R2 contract or physical state rule is introduced.

## Transaction and fulfillment proof

Inspections owns application orchestration; Orders owns fulfillment, Trips owns its
shared real Stop completion transition, Documents owns synchronous validation and
immutable evidence insertion. There is no Trips -> Documents dependency or cycle.
Sorted Order locks precede Trip UPDATE, own Driver SHARE and ordered Stop locks;
Documents follows with sorted shared file locks. Evidence validation precedes ledger
claim. The winner calls recordDelivery, which verifies COMPLETED + version increment
and an authenticated completion event written by the current PostgreSQL transaction.
Its callback performs real Trip execution and fulfillment; evidence then inserts and
everything commits together. Failed insert/deferred commit rolls back every effect.

Fulfillment requires nonempty DELIVERY mappings, every allocated Delivery successful
with evidence, and every requested Delivery visited. Exact requested IDs are used
when present; otherwise Site/Location or normalized snapshot must match. Distinct
matching prevents one visit fulfilling two required visits at the same destination.
Missing allocations never imply success. First departure starts allocated Orders
through the existing lifecycle, including reconciling a legacy OPEN projection to
its persisted plan. Partial work remains PARTIALLY_DELIVERED, full work DELIVERED;
COMPLETED is an independent authorized administrative action. Cancellation remains
blocked from ordinary fulfillment. Explicit BASE_RETURN prevents early Trip close.
Trip completion never closes Shift or manufactures physical movement/cargo facts.

## Flutter, Drift v6 and real transport

Four additive tables: road_drafts, road_captures, cached_driver_updates and
driver_update_cursors. v1–v5 upgrade atomically and preserve all queues, photo/result
edges and frozen requests. Draft/capture identities are immutable and namespace-safe;
queued drafts cannot change. Native camera context persists before launch; complete
private files repair interrupted association. Late callbacks preserve only the
original User/Tenant files. Native camera requests cannot overlap inspection captures.
Host camera tests inject real JPEG files; permissions/iOS and Android process death
still require physical-device QA, with no production test images or manual bypass.

Signed BOL/no-receiver, Fuel and problem forms save drafts and seek explicit intent
confirmation. The same replay lane finalizes R2 media, resolves/freeze IDs, attaches
to exact authorized context and sends only implemented typed endpoints. Final ACK
requires validated result identity/context/occurrence and survives a failed refresh;
authoritative work is read afterward. Lost ACK never generates another UUID or PUT.
Pending offline service can precede POD without advancing cached Stop state. Its
existing resource dependency waits for ACK. The UI and retained forms remain usable
without assigned Trips, show pending versus server confirmation and retain conflicts.

Transport/408/429/5xx retain the exact frozen request and use existing durable retry;
Retry-After is never rounded down by SQLite. 401 refreshes once. 403/404/semantic409
retain intent and photographs for review. No second worker, arbitrary endpoint,
false offline state, token persistence or R2-public fallback is added. Photos remain
conservatively pinned for offline recovery; no new automatic deletion is enabled.

## Driver Updates and cursor continuity

The existing targeted GET is fetched initially, manually, after ready/reconnect and
foreground. Each pass is bounded to ten pages of 200. Only backend-issued cursors
are persisted. Because nextCursor is a page continuation, not an end watermark,
the latest non-null anchor is retained; its final tail is reread and deduplicated
on later refresh. This avoids fabricating a cursor or skipping newly created rows.
Rows get a stable local sequence from server createdAt/id ordering (not occurredAt);
the inbox presents the most recent 200 first, preserving older metadata. Repeated
or malformed pages fail safely without advancing that page's cursor. Updates survive
Trip removal; namespaced views hide old account/Tenant data. Payload rendering is
whitelisted human fields, without claims/actor data or raw JSON. No push/outbox exists.

## Acceptance -> production code -> tests

| Acceptance | Code | Verification |
| --- | --- | --- |
| POD structural rules/current context | RoadOperationsService, OperationalEvidencePolicy | RoadOperationsPostgresTests; EvidencePostgresTests and Documents regressions |
| Real transition and same-tx proof | DriverDeliveryExecution, DriverTripExecutionService, recordDelivery | Real Shift -> Pre -> multistop POD -> Order -> BASE_RETURN -> Post -> Off; deferred-commit and insertion rollback |
| Partial/multi-Order fulfillment | DatabaseOrderFulfillment, existing Order lifecycle | Distinct visits, missing requested work, shared Stop, cancellation, no administrative close |
| Fuel/problem persistence | V22, RoadOperationsController/Service | Category/evidence/current assignment, same receipt concurrency, rollback, terminal replay |
| RLS/schema/minimal grants | V22, strict historical inventories | RoadSchemaPostgresTests, TenantRlsPostgresTests and Flyway checksums |
| Mobile real private capture/replay | RoadEvidenceStore/Replay/View, existing MediaStore/ReplayWorker | road_evidence_test: real files/SQLite/HTTP, offline modes, lost ACK/restart, 401/408/429/5xx, review/conflicts, malformed ACK, file tampering and account switch |
| Migration/frozen requests | AppDatabase v6 | Existing actual v1–v4 upgrade tests plus populated v5/reopen and late-failure rollback |
| Updates without push | DriverUpdatesStore/View | Pagination 205+, tail deduplication/restart, reassignment, malformed/repeated cursor, safe payload and widget cancellation display |
| Functional controls/honest states | RoadEvidenceForm/Panel, SyncPanel | road_widgets_test: two captures, offline saved state, no work, real form controls, logout |
| Exact public contracts | OpenAPI implemented-issue-45 schemas | Existing strict Documents/Shift tests plus #45 request/result/capability and closed catalog checks |

## Deliberate limits

No Yard/OOS mutations, geofences, FCM/outbox, generic Audit, OCR/signature recognition,
cost/gallons, payroll, or remote retention. OQ-053 unsigned-BOL alerts and OQ-057
retention remain open. Requested delivery mapping ambiguities fail conservatively;
they do not infer load quantities or fabricate fulfilled obligations. Device-level
camera/process recovery and release signing remain native release QA requirements.
Final exact-head CI results are recorded in the PR; this document does not claim a
merge or issue closure.

## Final independent audit and corrections

### Recovery checkpoint G (2026-10-12)

Recovered local changes after published E/F `7c926daca28b2e4660ef47b972d9d9e043682967`;
no files, commits or stashes were discarded. Main remains the approved #102 baseline.

| Requirement | Implemented | Tested | Remaining |
| --- | --- | --- | --- |
| POD / fulfillment / joint transaction | Production ports, ledger and V22 | 102 affected PostgreSQL/evidence/module tests pass | Final full clean verify and exact-head CI |
| Fuel / exceptions / fresh replay authorization | Production endpoints and owning-module replay SPI | Current-context, terminal replay, rollback and receipt concurrency | Final full suite and CI |
| Mobile evidence / immutable requests | v6, real private capture and existing replay lane | Road folder: 78 passing; earlier full suite: 315 | Full suite after audit additions |
| Driver Updates | Driver-scoped cursor/history and durable denial fencing | Paging/restart/profile switch/revocation | Final full suite and CI |
| OpenAPI / documentation | Local strict implemented #45 contracts | 13 contract tests and validation pass | Publish G1, remove stale implementation wording |
| Web regression | Production Web unchanged | Typecheck/lint/build pass | Repeat complete tests without competing Backend load |
| Release | Same published branch; no PR created | Historical migrations and gates preserved | Four green suites, one PR, exact-head CI, Ready; no merge |

Next step: verify remaining concurrency gaps and documentation, then complete the
unchanged local gates, publish reviewed implementation, and run all four CI jobs.

Reviewed production commands, public ports, V22 and historical inventory diffs,
evidence/ledger ordering, client capture/materialization, lifecycle and authorization
boundaries. Fuel drafts now retain the captured Tractor immutably; a later reassignment
cannot silently substitute another Tractor. Driver instructions/cursors additionally
bind to the current own Driver profile, preventing reuse after a profile relink.
Explicit 401/403 invalidates operational context and durably blocks cached instruction
visibility until an authorized refresh. Background read failures remain visible;
app shutdown fences and awaits outstanding instruction reads before closing SQLite.

Order destination matching uses bounded SQL-computed edges and PostgreSQL jsonb
equality, avoiding a query for every candidate visit. Replay facts are supplied by
an owning Inspections implementation of a Documents SPI, without a reverse module
dependency or another ledger. Optional Stop-parent checks do not lock a different
Trip; opposing foreign-Stop requests are tested to reject safely rather than creating
a lock inversion. The actual Stop transition port requires the caller's transaction.
Typed mobile acknowledgments reject unexpected fields and never downgrade an ACK
because the subsequent refresh fails. Retry deadlines round upward to honor 429.

The complete local Backend clean verify passed 1,972 tests (0/0/0), before the final
focused audit additions; the latest pre-addition Road/module subset passed 48.
The full Mobile suite passed 315 before the final Tractor/profile-denial additions;
the strengthened Road subset now passes 78. OpenAPI validates with 13 tests.
Web typecheck/lint/build passed locally; its parallel Windows run hit two unchanged
timing failures under load. The unmodified Linux CI gates must all pass on the final
HEAD before Ready for Review. Counts of that exact run supersede these checkpoints.
Historical V1–V21, R2 signing/publication/byte verification, role mappings, 57
capabilities, and all CI gates remain unchanged. No disabled or weakened tests.

### Checkpoints E/F

Mobile infrastructure, forms and instructions are implemented and published with
typed real transport, persistent v6 forms/captures and the existing synchronization
lane. The full local suite passed 314 tests before the final late-identity test;
the strengthened Road subset now passes 31 tests. Static analysis is clean. Existing
actual v1–v4 upgrade tests remain, plus populated v5/reopen and rollback tests.
OpenAPI validation and 13 tests pass; the former #43/#53 future-endpoint assertions
now check the exact implemented #45 marker and typed contracts without removing
their previous document/inspection security checks. Next: final audit, generated
code/format/locked dependencies and exact final-head four-job CI. No merge.
