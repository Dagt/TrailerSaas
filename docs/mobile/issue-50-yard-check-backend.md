# Issue #50 — Yard Check snapshot and reconciliation Backend

Verified base: `7d2a9d7a1cbaea002f8a051e03658348554c307d`, merge of PR #106;
#49 closed. Branch: `issue-50-yard-check-backend`. Clean recovered tree, no stashes;
all prior branches and V1–V23 preserved. Backend only; no #51 implementation.

## Acceptance matrix prepared before implementation

| Gate | Existing boundary / implementation plan | Required evidence |
| --- | --- | --- |
| A authority | YARD_CHECK_EXECUTE, ACTIVE Tenant/membership, existing Tenant-wide Spotter scope | Foreign resources, revoked membership/capability, no invented Site assignments |
| B start | New V24 Check/items; one INSERT SELECT MVCC inventory view | Frozen Yard/Dock inventory, empty/large snapshots, concurrent movement |
| B read | Bounded UUID pagination; counts and version; full snapshot retrieval | No omissions/duplicates, changed-version rejection, Tenant-safe reads |
| C scan | Existing MobileOperationLedger; Check lock; original observation | Matched/unexpected/mismatch, same/new-key duplicates, no Trailer effects |
| D terminal | Exact version; Check lock; missing only at completion | Completion/cancellation races, immutable terminal history, no reconciliation effects |
| E missing | Explicit YARD_CHECK_EXECUTE + TRAILER_LOCATION_CORRECT; shared markLocationUnknown | Event/projection/reconciliation/alert/ledger atomicity; last-known preservation |
| E mismatch | Same shared correctLocation engine, reviewed destination/version/reason | Stale rejection, provenance, no implicit scan correction |
| F security | Composite FKs, runtime RLS, narrow lifecycle guards/grants | Actual runtime isolation, owner history protection, rollback/concurrency, Modulith |
| G contracts | Strict existing REST/UUID ledger conventions; OpenAPI evolves planned examples | All historical tests plus new contracts; unchanged Mobile/Web |
| H review | One Draft PR, four completed exact-HEAD green jobs before Ready | Real counts/logs, no conflicts/rejected reviews/unresolved threads; NO MERGE |

## Contract audit and decisions

Accepted hardening D supersedes result NOT NULL: pending expected rows have NULL
result; ALREADY_SCANNED is a response outcome, never a fifth persisted result.
BR-061/FR-131 define expected inventory as active Trailers recorded AT_LOCATION in
active YARD/DOCK locations of the selected active Site (or selected YARD/DOCK).
Serviceability/load/coupling are snapshot facts, not availability filters. No
overlap prohibition is specified; independent simultaneous Checks are allowed.
Checks do not reserve inventory or authorize later Trailer mutations.

Snapshot insertion uses one PostgreSQL statement/view, not repeated inventory
reads. Original identity/location/version/state facts remain immutable. Scans,
completion and cancellation serialize on the Check row. Reconciliation retains
separate append-only associations rather than rewriting the completed snapshot.

The planned completion example includes completedAt. The current request explicitly
requires server completion time: implementation uses expectedVersion only, with
authenticated completion actor/server timestamp. Scan scannedAt remains the
original nonfuture observation time; starting/terminal timestamps are server-owned.

FR-103/BR-181 require a real persisted Administration alert on confirmed missing.
The owner explicitly authorized provisional WARNING / OPEN persistence, exactly
one TRAILER_MISSING alert per confirmed reconciliation, with Trailer/check/item/
movement/actor context and no individual recipients. OQ-059 stays open for final
severity/recipient/notification policies. No notification worker/push (#55/#56), generic
Audit (#61), destructive retention (OQ-057), or mobile #51 is introduced.

## Future #51 handoff

Start requires online server identity and a complete downloaded expected snapshot.
Retrieve every expected page and retain Check/version/context/counts; a partial
page is not a complete snapshot. Future scans use stable UUIDs and original times;
completion waits for all scans to be acknowledged. A conflict requires fresh
authorized state, never silent correction or replacement of an existing command.

## Validation record

Baseline merged CI: Backend 2,148; Mobile 467; Web 244; OpenAPI 31. Focused/local
evidence and actual PR/CI gates are recorded below. Full final-HEAD approval is
established only by completed CI logs. Physical Android/iOS QA remains release #65.

B: 24 PostgreSQL 18.6 tests pass (0 failures/errors/skipped), including 206-item
pagination, frozen original facts, empty/location-specific inventory, OOS
inventory, foreign/inactive context, closed queries and fresh-authority replay.
V24 adds Check/items, append-only reconciliation and the minimal authorized Alert
foundation; original migration checksums remain unchanged. Historical strict
catalog/latest-version tests are expanded for V24, never relaxed.

## Implemented architecture and transaction boundaries

YardCheckService owns start/read/scan/lifecycle orchestration and
YardCheckReconciliationService owns explicit physical confirmation. Yard consumes
the existing public identity/Trailer ports and the new minimal
MissingTrailerAlerts port in Notifications. Notifications only joins PostgreSQL
and persists the accepted Alert; it does not import Yard or own a second ledger.
Modulith retains the same twelve modules with no dependency cycle.

Start locks its live normalized Site (and optional selected Location), then
rechecks current authority. One data-modifying CTE inserts the Check and its
complete expected membership from a single READ COMMITTED statement snapshot.
No per-Trailer inventory loop or client list is used. An in-flight atomic movement
is seen entirely before or after its commit, never as unrelated read fragments.
The snapshot stores original operational number, Trailer version/location/load/
serviceability/coupling, location/Site labels and last-known/seen facts. It exposes
no Customer directory, Driver identity, Orders, Trips or Documents.

Scans lock Check → active Trailer → observed Location/Site/Customer parents,
then reauthorize. They classify against expected membership/location, retain the
first observation actor/original scannedAt/server recordedAt/UUID and increment
Check version only on a new observation. ALREADY_SCANNED is an API outcome, not
a persisted classification. No physical engine is called by scan or completion.
Site-wide Checks allow any active normalized observation Location in that Site;
location-specific Checks restrict observations to that selected area. Scans
cannot predate startedAt or lie in the future. Expanded years reject safely.

Completion/cancellation lock Check and validate IN_PROGRESS/exact version, live
Site and fresh authority. Completion updates remaining expected rows to MISSING
and then freezes COMPLETED, authenticated actor/server timestamp. Cancellation
freezes CANCELLED with reason/actor/server time, retaining unobserved NULL results.
Same UUID retries retain the original receipt; new terminal commands reject.

Reconciliation lock order is fresh authority → ledger claim → Check → item →
Trailer → Site/Location/Customer context → fresh authority → shared physical engine
→ Alert (missing only) → append-only reconciliation → saved ledger response.
Only COMPLETED/MISSING or COMPLETED/LOCATION_MISMATCH items qualify. Both commands
require current TRAILER_LOCATION_CORRECT independently of YARD_CHECK_EXECUTE.
Exact current Trailer version is mandatory. Missing also requires it still be
AT_LOCATION at the original expected location: a later Yard Move cannot be erased
by an old missing finding. Correction's reviewed destination must equal the
original observed active same-Site location; arbitrary alternatives reject.
Occurrence/record times for these explicit Backend confirmations are server-owned.
No Driver/Tractor/Trip IDs are invented. Existing engine/rules/writer own physical
effects and derive HUMAN_USER/USER_ACTION from the principal. Last-known location,
lastSeenAt, lastDriver, load, serviceability, designation and open coupling are
preserved by markUnknown; no Drop/Order/Shift effect is introduced.

The original Check/items remain unchanged by reconciliation. A unique append-only
association preserves Check/item/Trailer/event/actor/reason/times/UUID plus nullable
Alert ID. Exactly one accepted reconciliation per item; concurrent different
Checks for the same Trailer still serialize on its version/row lock. Current
authorization is checked before any ledger access and after lock waits, including
historical receipt replay. Historical receipts are not current command authority.

## V24, RLS and lifecycle guards

V24 adds yard_checks, yard_check_items, yard_check_reconciliations and alerts,
with Tenant-composite resource/ledger FKs, bounded lookup indexes and the unique
(Tenant,Check,Trailer) item key. Pending result is nullable per hardening D;
scanned results are the four accepted values. Narrow row checks explicitly test
IS NOT NULL so SQL unknown truth cannot admit incomplete terminal/scan records.

Runtime is a nonowner NOSUPERUSER/NOBYPASSRLS role. All four tables have Tenant
RLS and no PUBLIC grants. Check/items grant SELECT/INSERT/UPDATE only;
reconciliation/alerts SELECT/INSERT only. Triggers deny deletion/rewrite even by
the migration owner; Check version must advance by one and terminal history
cannot reopen. Expected rows are inserted only during new Check creation, original
facts never change and a found observation cannot be replaced. Deferred validation
requires MISSING to commit with COMPLETED, and an Alert with its reconciliation.
New start/scan/physical associations verify the matching live PROCESSING ledger,
actor/operation kind and current native PostgreSQL xid. Accepted physical event
and projection must match the association; historical receipts cannot append new
evidence/alerts. Guard functions grant no PUBLIC EXECUTE and are not SECURITY DEFINER.

Alerts minimally preserve the accepted schema's Trailer reference, lifecycle and
message fields. #50 only admits the owner-approved provisional TRAILER_MISSING /
WARNING / OPEN occurrence. Its metadata is exactly yardCheckId/itemId/
movementEventId/actorUserId. No recipient rows are fabricated. Alert/event/Trailer/
reconciliation/ledger are one PostgreSQL transaction; a late persistence error
rolls back every effect. #55/#56 will require compatible migrations for additional
Alert producers/lifecycle/delivery; no outbox or notification claim is made here.
OQ-059 remains open for final recipients/severity/notification policy.

## HTTP contracts and conflicts

| Method and suffix under /yard-checks | Closed request / result |
| --- | --- |
| POST root | siteId, optional nullable locationId → 201 Detail |
| GET root | Site/status/from/to filters, UUID cursor, limit 1–50 → Page<Summary> |
| GET /{id} | UUID item cursor, limit 1–200, expectedOnly, expectedVersion → Detail |
| POST /{id}/scans | trailerId, observedLocationId, scannedAt → outcome/check/item |
| POST /{id}/completion | expectedVersion → terminal Summary with server time |
| POST /{id}/cancellation | expectedVersion, nonblank reason → terminal Summary |
| POST /{id}/items/{item}/mark-location-unknown | expectedTrailerVersion, reason → reconciliation/Trailer/event/Alert |
| POST /{id}/items/{item}/location-correction | expectedTrailerVersion, newLocationId, reason → reconciliation/Trailer/event; Alert null |

All six POSTs require a UUID Idempotency-Key; subsequent successful commands use
200 and replay keeps the exact saved receipt. Reasons are trimmed nonblank <=4000,
versions are nonnegative integer int64. DTOs/queries are closed; arbitrary actor,
source or permission fields reject. Authentication is Firebase/internal User.
400 covers malformed/time/query/DTO validation; 401 unauthenticated; 403 current
Tenant/membership/capability denial; 404 hides foreign/missing resource identity;
409 covers STALE_VERSION, INVALID_STATE_TRANSITION, RESOURCE_CONFLICT and
IDEMPOTENCY_KEY_REUSED. Infrastructure remains safe 5xx and rolls back.

Each detail page locks Check for SHARE so counts/items share its stable version.
Cursor pages require exact expectedVersion; a changed version yields STALE_VERSION
and a restarted download, never mixed observed pages. All reads remain freshly
authorized. snapshotComplete is true only when the first page contains the entire
selected set. A final continuation page alone remains false. Future #51 accumulates
all unique IDs and validates expectedCount (expectedOnly=true) or totalItemCount
(all results). Original expected membership never changes; reconciliationId is a
separate current audit association, not a changed observation/snapshot version.

## Security and acceptance evidence

| Critical invariant | Inspectable regression evidence |
| --- | --- |
| One consistent immutable inventory | YardCheckPostgresTests: scope/empty/OOS/state filtering, 206-item pagination, committed view during atomic movement, original facts after master/live change |
| No physical side effect from scan/completion | YardCheckScanPostgresTests and Lifecycle tests assert unchanged Trailer/event/Alert; original scan and late replay preserved |
| Single terminal state | Lifecycle: scan/completion and completion/cancellation races, stale version rollback, cancelled partial NULL, terminal new-key rejection |
| Explicit shared physical effects | Reconciliation: missing/mismatch engine/event/actor/UUID, current-context/version rejection, preserved real lastDriver/open coupling/load/OOS |
| Atomic real Alert | Reconciliation: WARNING/OPEN/Trailer and exact metadata association, alert/association/ledger late failure full rollback, concurrent same/different Check winner |
| Fresh authority and ownership | Security: role/membership/Tenant revocation, after-Site-lock wait, other actor same UUID, additional correction capability before ledger |
| Runtime RLS/FKs/grants | Actual unqualified cross-Tenant reads, foreign Site/Trailer and wrong-Site observation FKs, nonowner/nonBYPASS/no PUBLIC and least grants |
| Immutable history under owner/runtime | Security: pending original facts, terminal delete/update, scan wrong-kind/historical ledger, appended expected row, Alert/association replay forgery |
| No second framework or mobile implementation | Existing ledger/movement modules and twelve-module Modulith verification; no mobile source/schema/lockfile or workflow changes |
| Actual API and baseline compatibility | 39 OpenAPI tests include all prior 31; closed eight method surfaces and provisional Alert semantics; existing #40/#48/#49 suites retained |

## Publication and honest validation gate

[PR #107](https://github.com/Dagt/TrailerSaas/pull/107) exists as Draft while final
checks complete. A–F are published; G publishes actual OpenAPI/documentation and
audit corrections. Checkpoint H records the actual publication and review gates below. The PR
description/checks carry the exact final publication SHA, run ID and completed
counts on that SHA, avoiding a document containing its own commit hash.

Local focused runs: B 24 passed; C 40 passed (24 existing snapshot +16 new scan
cases, subsequently refactored into shared fixtures without duplicating tests);
D 78 executed with one stale catalog-list assertion, fixed without weakening it;
E 27 passed including 18 reconciliations and seven corrected application cases;
F 215 passed with no failures/errors/skips. The first Windows clean verify was
stopped after reproducing a stale exact table-list assertion; additional old
catalog lists are expanded for V24, preserving every old table. All four affected
suites, every final #50 test and Modulith were rechecked: **187 passed, zero
failures/errors/skips**, including **103 new #50 cases**. The interrupted full
attempt is not reported approved. Final full Linux clean verify is required on
the final published HEAD. Local Web **244** and OpenAPI valid/**39** pass; Mobile
**467** passes on unchanged Flutter 3.47.6/Dart 3.13.5/locked Drift v8 source,
including reproducible generation, format and fatal-info analyze. No test/workflow
is removed, disabled, weakened or bypassed. Original migration checksums remain
unchanged; only V24 is introduced.

Residuals: #51 mobile UI/SQLite/offline integration is unimplemented; this Backend
contract enables it without client-invented inventory. No Android/iOS physical QA
is claimed, which remains #65. OQ-059 definitive policies and OQ-057 retention are
open; no legal/retention conclusion or recipient/delivery implementation is invented.
No auto-reconciliation, reservation, overlapping-Check prohibition or Alert resolution.
Original #40/#48/#49 flows and machines are retained. **NO MERGE; no issue #51 work.**

## H — Actual publication and final review gates

The single [PR #107](https://github.com/Dagt/TrailerSaas/pull/107) targets main from
`issue-50-yard-check-backend`. Its verified base is the approved PR #106 merge,
`7d2a9d7a1cbaea002f8a051e03658348554c307d`. The complete implementation/local
regression source is G, `a352e69d53a64ca49366e269c8a53bf35ffba1a2`; H changes
only this publication record. A–G are preserved recoverable commits.

The initial [G CI run 38491820427](https://github.com/Dagt/TrailerSaas/actions/runs/38491820427)
started successfully; this reference is not final approval and is superseded by
the publication HEAD run. The PR [checks](https://github.com/Dagt/TrailerSaas/pull/107/checks)
and description record the exact final H SHA, final run ID, all four completed
job counts/results, actual Draft/Ready state and any subsequent correction.
No in-progress or superseded run constitutes readiness. Draft removal requires
full Backend clean verify plus Mobile/Web/OpenAPI success on that same final HEAD,
zero failures/errors/skips, clean published branch, mergeability and no rejected
reviews/unresolved threads. This avoids a self-referential commit hash or a false
static CI/status claim in a file whose publication changes that hash.

Required suite counts preserve the merged baseline: Backend 2,148 +103 new cases,
Mobile 467, Web 244 and OpenAPI 31 +8 new cases. Completed logs, not these expected
counts, establish approval. No #51/mobile implementation or native-device QA is
claimed. OQ-059/retention/release #65 remain as documented above. **NO MERGE.**
