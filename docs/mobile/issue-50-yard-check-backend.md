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

Baseline merged CI: Backend 2,148; Mobile 467; Web 244; OpenAPI 31. New validation
and actual PR/CI evidence will be recorded as checkpoints finish. No #50 suite
has yet been declared approved. Physical Android/iOS QA remains release #65.

B: 24 PostgreSQL 18.6 tests pass (0 failures/errors/skipped), including 206-item
pagination, frozen original facts, empty/location-specific inventory, OOS
inventory, foreign/inactive context, closed queries and fresh-authority replay.
V24 adds Check/items, append-only reconciliation and the minimal authorized Alert
foundation; original migration checksums remain unchanged. Historical strict
catalog/latest-version tests are expanded for V24, never relaxed.
