# Issue #48 — Yard Mode and Trailer Move

Verified base: `798d987b242dab74c3e58ec81eee8a2c61f8d049`, merged PR #104;
#47 closed. Clean tree and no stashes; prior branches preserved.
Branch: `issue-48-yard-mode-trailer-move`. No merge; #49–#51 are outside scope.

## Acceptance matrix

| Gate | Implementation and required evidence |
| --- | --- |
| A: Yard context | Fresh active Tenant/membership and YARD_MOVE_EXECUTE; bounded active Sites/Locations; same-Tenant joins and Customer association checks; scope/RLS tests |
| B: physical command | Strict request, actual origin, same active Site for both areas, correct version, active IN_SERVICE stationary uncoupled Trailer; existing yardMove engine |
| B/C: atomic history | Existing MobileOperationLedger, locked Trailer and live locations; HUMAN_USER/USER_ACTION; original observedAt, recordedAt, UUID, before/after; rollback/concurrency tests |
| C: conflicts | LOCATION_MISMATCH distinct from STALE_VERSION; no silent correction, no event/version on rejection; safe authorized current projection |
| D: usable Yard | Yard-only and multi-role entry, NativeQrScanner, real QR lookup followed by minimal authorized Yard detail; Site/destination review and explicit confirmation; widgets |
| E: durable intent | Same CommandStore/ReplayWorker, User/Tenant fencing, persist before send, stable UUID/path/body, actual SQLite reopen/lost ACK/retry tests |
| E: successive moves | Per-Trailer dependency; second intent retains planned origin, no invented version; materialize only from first verified ACK; block dependents on conflict, independent assets proceed |
| F: compatibility | Existing Road/Shift/POD/Fuel/geofence/update/media tests retained; OpenAPI and documentation match real endpoints |
| G: external review | Full local suites and four final exact-head CI jobs, real counts, open Ready PR only when gates pass; no merge |

## Authorization and scope decisions

Accepted hardening A and ADR-011 explicitly make YARD_SPOTTER Tenant-scoped;
future per-location assignments are outside the accepted MVP schema. Reuse
YARD_MOVE_EXECUTE without adding role grants, Site assignments, Driver or Tractor
requirements. Selecting a Site does not grant permission. Active normalized
locations from the same Site constrain a physical Yard move; Customer context is
not exclusive ownership of a shared Site. No Order/load/designation change occurs.

The existing global QR resolver identifies an asset only. A minimal Yard-owned
Trailer read, freshly gated by YARD_MOVE_EXECUTE, supplies recorded location,
Customer display, state, coupling and version without granting generic browse.
The client does not persist or trust a scanned URL as Backend authority.

## Offline design

Reuse existing local_commands and materialized_requests. A follow-up move stores
an explicit reference to an earlier operation and its intended origin/destination.
Its expected version is unresolved until the preceding same-Trailer ACK provides
an authoritative version and matching destination. The final HTTP body is frozen
before sending and never recomputed on retry. No future version, fictitious Trip,
second queue or second worker. Server current state and exact expected version
remain authoritative; a competing Spotter yields review, never silent rebasing.

## Checkpoint evidence

A: GET yard/sites includes bounded active Site/location projections (50 Sites,
200 locations per embedded page); continuation uses yard/sites/{id}/locations.
GET yard/trailers/{id} has fresh Yard capability and no listing surface. Eleven
PostgreSQL context/security tests pass with 0 failures/errors/skipped. No server
migration, role grant or RLS policy changed.

Further implementation, tests and native QA limitations will be recorded as each
recoverable checkpoint is published.

B: POST yard-moves uses the existing physical engine and MobileOperationLedger
in one transaction. Trailer row locking precedes live Site/location/Customer
checks, with the existing Site parent protocol protecting association removal.
Same active Site, IN_SERVICE, AT_LOCATION and UNCOUPLED are mandatory. Strict
expectedTrailerVersion, origin, observedAt and JSON are checked. Twenty-seven
PostgreSQL tests pass with 0 failures/errors/skipped, including manual rollback
invariants, matching event/projection and stable ledger replay. Device-future
observations are rejected, following the existing Shift time policy; chronology
cannot move lastSeenAt backwards. No Trip/Driver/Tractor is invented.

C: Forty-nine Yard PostgreSQL tests plus two Modulith tests pass (0 failures,
errors or skipped), including same/different-key concurrency, revocation before
ledger replay, real QR revocation, coupled/OOS/unknown states, Site write locking,
and a ledger-result failure after physical writing that rolls back all effects.
The audit found that shared capability authorization does not itself reject a
cancelled Tenant. Yard now explicitly requires Tenant status ACTIVE before every
context read and command/replay; Candidate/Suspended/Cancelled regressions pass.
This is bounded to Yard and does not change historical grants or other workflows.

D: Yard Home replaces the production SessionView placeholder. Yard-only users
need no fabricated Driver/Tractor; multi-role users retain Road navigation and
its existing Shift/inspection controls. NativeQrScanner is reused with scoped
cancellation. QR lookup is followed by the authorized minimal Yard Trailer read.
Site/destination selection, recorded state, human review/Not Yet, observed time,
local pending state, server ACK and original conflicts are real. Review dialogs
close on owner changes and cannot display/confirm the previous account's data.
Six real SQLite widget tests cover this flow; the 11 controller/context tests
also pass. No new native permission or package was introduced. Correction/OOS
UI, Yard Check and reconciliation remain #49–#51.

Drift v7 adds only cached_yard_contexts and extends existing aggregate/result
constraints to TRAILER. Existing queues, frozen requests and confirmed results
are retained; real v1–v6 migration/reopen checks exercise namespace integrity.
Native camera/device QA is pending #65; Linux widget tests do not claim it.

E: Seventeen file-backed SQLite replay tests pass, plus the real v6 migration
regression. Two offline moves keep their original UUIDs and intended origins;
the second has a typed operation reference instead of a fabricated version.
Only the first verified same-Trailer ACK resolves that version, and the HTTP
request then freezes in the existing materialized_requests table. Lost ACK,
reopen, 408/429/500/503, Firebase 401 refresh, 403/404, semantic 409, malformed ACK,
User/Tenant fencing and two workers/connections retain identity. A failed parent
blocks its descendants while independent Trailers continue. ACK is terminal;
a late timeout cannot downgrade it. Existing command_results retains the verified
Trailer result; no second queue, worker or client state machine.

Focused audit regressions reproduced and corrected three Backend edge cases:
stationary origin mismatch takes precedence over version mismatch; expanded
non-RFC3339 years reject safely before JDBC instead of returning 500; fresh
membership/capability/Tenant authorization is rechecked after waiting for live
Site locks. The original pre-ledger check remains mandatory for replay. All three
regressions now pass; the complete Backend suite is being rerun on these changes.

## Security and acceptance evidence

| Risk / gate | Result and inspectable evidence |
| --- | --- |
| Fresh authority / scope | YardContextService.authorize uses current Tenant capability/membership and ACTIVE Tenant. YardPostgresTests covers Tenant/role revocation before replay and during Site lock waits. No role grants changed. |
| Foreign Site/Trailer | Every resource query/join carries Tenant; invalid foreign IDs return safe 404/403 without currentTrailer. Real runtime PostgreSQL RLS remains in place. |
| Physical state | The existing MovementRules.yard owns the transition; YardMoveService adds required active/IN_SERVICE/AT_LOCATION/UNCOUPLED and live same-Site preconditions. No snapshot/address/unknown location authority. |
| Source/version conflicts | Wrong stationary origin is LOCATION_MISMATCH; matching origin with stale version is STALE_VERSION. Original body/UUID is retained, with authorized downloaded recorded state shown separately. |
| Atomic event/projection/ledger | Existing engine, row locks and MobileOperationLedger; concurrent same/different-key tests yield one physical winner. Failure saving ledger response rolls back event, version and location. |
| Provenance | SQL assertions prove HUMAN_USER/USER_ACTION, authenticated User, original observedAt/clientOperationId and previous/new location; no fictitious Trip/Driver/Tractor. Load and designation preserved. |
| QR reads | Existing real QR resolver, revoked QR regression and zero movement/event from lookup. A token/cached scan never grants server capability. |
| Human UI | Six widget tests prove review/Not Yet/confirmation, server ACK distinction, mode switching, recorded mismatch and closed account-bound dialog. |
| Durable sequential moves | Seventeen real SQLite replay tests plus v6 upgrade preserve frozen UUID/path/body; predecessor ACK alone resolves the second version. No speculative counter or source rebasing. |
| Late results / two workers | Same-database lane serializes; two actual file connections use the same immutable request/ledger identity. One verified result and terminal ACK survive a late lost ACK. |
| Privacy / ownership | User/Tenant/Driver identity fences callbacks; cancelled scanner/review routes. No new location permission, GPS, raw coordinates, tracking or asset-history replica. |
| Regression / contract | Full Mobile 424 (389 historical +35); Web 244; OpenAPI 23 (18 historical +5) and validation pass. Native device evidence remains separate. |

## Local validation and release limits

Flutter 3.47.6 / Dart 3.13.5, enforce-lockfile, reproducible Drift generation,
format and analyze --fatal-infos pass. All 424 Flutter tests pass in the isolated
local Linux checkout with real SQLite; Windows native SQLite policy limitation
from #47 remains unchanged. All original Web commands pass on the same source
in Linux, including 244 tests. OpenAPI validation and all 23 tests pass.

Backend uses Java 25 and real PostgreSQL 18.6 Testcontainers. The first complete
2068-test suite passed before the three audit regressions; each of those corrected
regressions then passed. A later 2071-test full run had no assertion failures but
one Windows setup connection error (BindException: Address already in use:
getsockopt) in historical EvidencePostgresTests. The affected concurrency test
passed on repeat. That failed full run is not reported as green; a complete
verification is being repeated. Final exact-head counts/CI belong in the PR.

No physical Android/iOS QA was performed. Release #65 must verify camera grant/
denial/revocation, actual QR labels, mode/account/foreground changes while scanning
or reviewing, process kill/reopen with two pending moves, native builds/signing,
and minimum supported devices. Production privacy/consent/retention review remains
open; no legal conclusion or destructive retention is introduced. Yard-specific
Shift/equipment policy is not invented from Driver grants. #49 correction/OOS UI,
#50/#51 Yard Check/reconciliation, tracking and continuous GPS remain outside scope.

F: OpenAPI documents the four scoped context/move operations, strict request,
safe errors/current projection, provenance and bounded pages. Historical Arrival
privacy tests retain their assertions while recognizing the actual v7 upgrade.
No workflows, historical test cases, backend migrations or RLS policies weakened.
