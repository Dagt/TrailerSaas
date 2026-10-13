# Issue #49 — Yard discrepancy, corrective commands and OOS evidence

Verified base: `82a48132b9153aaa6445ac890040e219f82a24f3` (PR #105 merged;
#48 closed). Dependencies #40/#53 closed; clean tree, no stashes. Existing work
and branches preserved. Branch: `issue-49-yard-discrepancy-oos`. No merge, no #50.

## Acceptance matrix prepared before implementation

| Requirement | Existing code | Required change | Verification | Risk |
| --- | --- | --- | --- | --- |
| Explicit location correction | TrailerMovementProjection.correctLocation, #40 writer, Yard context | Scoped Yard ledger orchestration with selected Site, strict version/reason/observedAt | Same-location/stale/permission/foreign Site, actor/before-after/rollback | Silent physical overwrite |
| Load correction | changeLoadState / MovementRules.load | Typed idempotent Yard command; no location/coupling/Order effects | All distinct load transitions, same-state/stale/replay | Global editor or guessed version |
| Report without correction grant | Yard capability/current projection; no suitable nonmutating report table | Append-only discrepancy report with historical observed state and bounded review read | Persistence, no Trailer version/event change, concurrency/ledger/RLS | Report mistaken for correction |
| OOS with photos | #40 serviceability writer; validateTrailerOos policy | Public transition port shared with admin; context-bound DAMAGE_PHOTO producer; immutable event-evidence association | Uploaded/type/uploader/Trailer, full transaction rollback/concurrent replay | Uploaded bytes mistaken for evidence acceptance |
| Existing admin API compatibility | TrailerAdministrativeCommandController/Requests | Preserve closed bodies/server time and independent return grant | Entire original #40 suite | Breaking admin clients |
| Yard photo capture | Native camera, PrivateMediaFileStore, MediaStore/Processor, R2 | Real Trailer-scoped capture context without Driver/Trip; same upload/finalize lane | Camera injected, real files/hash/reopen/file missing/denials | Cross-account evidence or fabricated Driver |
| Typed offline commands | Drift v7, YardReplay, CommandStore/ReplayWorker | Serialized physical types; unresolved actual predecessor version; append-only report does not increment Trailer version | Mixed move/correction/load/OOS chains; frozen requests/lost ACK/two workers | Invented version or successor after conflict |
| Durable context and UI | Yard Home/QR/scope fencing | Reason/location/load/photo/review screens with capability-specific controls and honest ACK | Yard-only/dual-role/widgets/double taps/account and Site changes | Late confirmation or premature success |
| Persistence security | V1–V22, runtime RLS and grants | Next migration V23 only for reports/event-evidence; local v8 if needed, preserve queues | Exact inventories/FKs/RLS/append-only/v1–v7 upgrade/fresh SQLite | History loss or foreign references |
| External review | Four unchanged CI jobs | Published A–G, one Draft PR then Ready only exact-head green | Full baseline retained (2071/424/244/23) plus additions | False completion |

## Architecture and contract choices

Yard remains the application orchestrator. It consumes public Trailer and
Documents ports; neither module imports Yard, so no Modulith cycle is introduced.
Existing administrative endpoints remain unchanged and do not claim mobile replay.
New typed Yard commands use `/yard/location-corrections`, `/yard/load-state-changes`,
`/yard/discrepancy-reports`, `/yard/out-of-service` with the existing ledger.
All require current Yard context; mutations additionally require their exact
TRAILER_LOCATION_CORRECT / TRAILER_STATE_CORRECT / TRAILER_OOS_MARK capability.
No role grants change. Current YARD_SPOTTER has correction/load authority but no
OOS grant; authorized multiple-role users may perform OOS. Reporting requires
only Yard authority and never invokes correction. Return to Service is untouched.

A scoped Documents producer initializes DAMAGE_PHOTO for one real Trailer under
TRAILER_OOS_MARK, reusing the same R2 transport/finalization. Binding persists with
the initialized document so restart never loses its authority context. OOS validates
finalized own exact-Trailer documents and commits an immutable association to the
actual MARKED_OOS event, state and ledger together. R2 upload precedes PostgreSQL;
there is no distributed transaction. No public generic Trailer attachment grant.

Reports preserve the caller's historical recorded observation; they do not mutate
Trailer or manufacture an event/version. Physical commands use only authoritative
predecessor ACK versions and freeze body once before sending. Conflicting parents
block successors; independent Trailer chains continue. Existing sent intents remain
immutable; after review the user creates a new action rather than editing history.

## Checkpoints and release boundary

A: typed Yard location/load commands reuse the existing projection, writer and
ledger. 165 real PostgreSQL/regression/Modulith tests pass, plus two explicit
permission-port tests (0 failures/errors/skips). The 52 #48 and 88 #40 tests
are retained unchanged; their shared Yard setup moved into a reusable fixture.
B: append-only discrepancy reports and bounded separately authorized review are
implemented. V23 adds Tenant-safe reports and the immutable OOS event/evidence
association foundation, with RLS and SELECT/INSERT-only grants. 147 PostgreSQL,
authorization, strict catalog, correction/#48 and Modulith tests pass (0/0/0),
including 17 new report cases. C: scoped DAMAGE_PHOTO initialization uses the existing signed R2 transport and
finalization, without a Driver/Trip. OOS and #40 share the same public transition
port/writer. Validated own finalized exact-Trailer evidence, MARKED_OOS, version
and ledger commit together. 370 transport/evidence/administrative/Yard/Modulith
tests pass (0 failures/errors/skips), including 23 new OOS cases. Confirmed
audit corrections preserve legacy integrity-error mapping and exact V23 version
inventories. Native QA is not claimed. D is implemented: real corrective forms, native camera injection, durable v8 drafts and typed existing replay graph. E adds mixed replay/recovery/security proofs and two audit corrections: load correction preserves null unknown/in-transit location; reports reject invented future observed versions. F1–F4 audit/contracts/documentation are implemented below; G publication and final exact-head CI remain pending. Native Android/iOS camera/build/lifecycle QA remains #65; simulated
camera and Linux tests do not count as physical device evidence. OQ-054 return
policy and production privacy/retention remain unresolved. No Yard Check, Missing
reconciliation, notifications, generic audit worker or GPS tracking is included.

## Recovery audit F1 (2026-10-13)

Recovered local and remote HEAD: `590e328cf82ffa797b152aa881fef1af5723159b`.
Five A–E commits preserved; main remains the verified PR #105 baseline. No stash,
staged change, later unpublished commit, existing PR or V1–V22 change was found.
Local OpenAPI/documentation after E were retained. No branch recreation or reset.

Local Flutter 3.47.6/Dart 3.13.5 locked dependency resolution, reproducible Drift
generation, format, fatal-info analysis and the entire 467-test suite pass. This
retains all 424 baseline tests and adds 43. Real Linux SQLite and HTTP/private-file
tests cover all typed commands, mixed ACK-derived versions, lost ACK/reopen,
408/429/5xx, 401 refresh, 403/404/409, two separate SQLite connections, report-only
independence, explicit conflict review, native callback ownership and widget forms.
OpenAPI validation and all 30 tests pass (23 retained, seven new). Web full checks
also pass with 244 tests, typecheck/lint/build. These are local results, not CI.

The complete Windows Java 25/PostgreSQL 18.6 run executed 2,139 tests and ended
with one failure and one error, zero skipped; it is **not an approved Backend run**.
The failure is the exact latest migration-description assertion still expecting
V22's description despite already checking V23. It will be corrected to the actual
V23 name, preserving exact inventory assertions. The error is an existing Trip
assignment connection failing with Windows `BindException: Address already in use:
getsockopt`; it must be rechecked and definitive full validation completed in Linux
CI. The affected 43 correction/report cases already passed independently.

Previously reproduced D defects were corrected before publication: typed-history
rendering no longer casts nonexistent move fields in corrective rows; text
controllers remain alive until dialog exit animation completes. No test was removed,
ignored or relaxed. Native Android/iOS physical QA remains unperformed (#65).

| Requirement / critical audit question | Implemented | Available evidence | Defect / pending | Action |
| --- | --- | --- | --- | --- |
| 1,3 Report cannot overwrite current or historical state | Separate append-only row, no physical port | YardReportsPostgresTests historic/nonmutating, actual-correction and nullable-origin cases; mobile report replay | None found | F2 verified |
| 2 Location correction requires exact capability | Before ledger and after Trailer/Site locks | YardActionPermissionTests; all Yard invalid/revoked cases | None found | Retain |
| 4,5 OOS rejects foreign/wrong-Trailer/pending/unvalidated photos | Own exclusive Trailer link, type/UPLOADED/scope checks | YardOosEvidencePostgresTests actual signed HTTP bytes | None found | Retain |
| 6,7 Evidence belongs to actual MARKED_OOS and rolls back together | Shared writer plus strengthened V23 event/document/processing-ledger/state guard | Real event association, post-flush rollback and runtime wrong-kind/historical-event/actor/Trailer/state rejection | Reproduced wrong ledger kind was accepted before F2 | Fixed and verified |
| 8 No photo reuse for another OOS | Tenant/document unique constraint and business conflict check | Administrative Return then rejected consumed photo | None found | Retain |
| 9 Replay never bypasses current authority | Fresh capability/Tenant/membership and OOS contextual rechecks | Revoked role/membership and same-key tests | None found | Retain |
| 10 Driverless Yard photo upload | Scoped OOS producer; no fabricated Driver/Trip | Actual zero-Driver-profile upload/finalize/OOS and camera tests | Native devices pending | #65 |
| 11 Tenant isolation and downloads | Composite FKs, runtime Tenant RLS, all-linked reads | Strict inventories, reports RLS, document transport security and direct OOS runtime RLS/grant/immutability proof | None found | F2 verified |
| 12 Return remains independent | Original #40 endpoint/capability unchanged | Entire admin regression suite; Yard no-Return test | OQ-054 remains unresolved | No policy invention |
| 13 Versions derive only from actual predecessor ACK | Typed same-Trailer physical refs; report never predecessor | Four-command offline mixed chain and SQLite reopen | None found | Retain |
| 14 Conflicts do not release old descendants | Existing dependency graph plus explicit retained failed/new-root review | Conflict/review and independent #48 asset tests | None found | Retain |
| 15 Lost ACK creates no duplicate transition | Same UUID/frozen request and original ledger | All corrective kinds/OOS loss and two workers | None found | Retain |
| 16 Account switch fences late callbacks | Dialog/context/version fencing; private capture retains original owner | Native late-account test and widget review switch | Physical process/lifecycle pending | #65 |
| 17 Preserve all 2,762 baseline tests | No removed tests/workflow changes | Mobile/Web/OpenAPI retained full counts; Backend 2,139 executed | Full Backend Linux approval pending | CI gate |
| 18 OpenAPI equals actual six method surfaces | Closed DTOs, scoped producer, separate bounded review | 30 contract tests, including original #45/#48 | None found | F3 published; F4 documentation |
| 19 V1–V22 remain intact, V23 isolated | Only V23 diff from baseline; strict catalog/Flyway | Git diff and security inventories | Latest description assertion stale before F2 | Exact F2 correction verified |
| 20 Yard Check/Missing remains outside scope | No #50/#51 route/workflow or grant | Existing implemented-route inventories and scope review | None found | Do not start #50 |

## F2–F4 final local evidence and transaction audit

F2 (`3fadc34`) closes the reproduced runtime association gap: V23 now requires
the matching actor's OOS/PROCESSING ledger row, actual OUT_OF_SERVICE projection
and MARKED_OOS fact written in the same transaction. Historical or other-kind
ledgers cannot authorize a new association. Both `xmin` comparisons use PostgreSQL's
native `pg_current_xact_id()::xid` conversion, replacing the manual numeric modulo.
The documented [xid8-to-xid cast](https://www.postgresql.org/docs/18/functions-info.html#FUNCTIONS-TXID-SNAPSHOT)
preserves low unsigned bits; actual PostgreSQL 18.6 tests cover wrap-sized and
maximum unsigned xid8 values without integer overflow. Production joins one
top-level REQUIRED transaction; nested/savepoint event creation is not introduced.
An inconsistent transaction fails closed with the SQL constraint, never acceptance.

The lock order remains fresh authorization → ledger claim → Trailer UPDATE lock →
live Site/Locations SHARE locks → sorted Document locks → shared transition/event
flush → evidence association → saved ledger response. Historical receipt replay
uses fresh authority, Trailer/Site and document/association checks in this same
order, does not append evidence or repeat the physical transition, and returns the
stored historical receipt. The client downloads current authorized state after ACK.
Existing #38–#48 parent/Trailer/document locking protocols remain unchanged.

F2 tests directly use runtime credentials for illegal association inserts,
cross-Tenant reads, SELECT/INSERT-only grants and UPDATE/DELETE rejection; the
migration owner also cannot rewrite accepted history. Nullable recorded locations
and an old offline report after an actual correction leave current state unchanged.
The exact latest migration description is updated to V23's actual name, not removed.
The final focused 57 cases (30 OOS, 20 reports, seven application/Flyway) pass with
zero failures/errors/skips. The previous focused 140 cases also passed, including
all 84 Trip assignment cases that reproduced the Windows-only connection problem.

F3 (`3e30d7e`) publishes all six actual method surfaces and seven new contract tests,
retaining #40/#45/#48 and generic Documents contracts. #47's schema-version privacy
assertion advances to v8 while retaining every original native/privacy assertion.
F4 completes all required API/UX/offline/data-model/backlog/README documentation.
No workflows, role grants, V1–V22, Web code, dependency lockfiles or native permission
grants are changed. F5 below consolidates existing iOS purpose descriptions. Initial signed upload remains non-idempotent and may leave an
unused PENDING reservation after lost ACK; this does not mutate Trailer or accept
evidence, and retention/cleanup is not silently enabled.

| Local gate | Actual result | Scope / limitation |
| --- | --- | --- |
| Backend full clean verify | 2,139 executed; 1 failure, 1 Windows connection error, 0 skipped before F2 | Not approved; exact failed assertion fixed and affected tests rechecked. Final full Linux CI required. |
| Backend final focused verification | 57 passed, 0 failures/errors/skipped; preceding 140 passed | Actual Java 25.0.3, PostgreSQL 18.6 Testcontainers/R2 signed HTTP, Flyway V23/runtime credentials |
| Mobile complete suite | 467 passed, 0 failures/skipped | Flutter 3.47.6/Dart 3.13.5; locked pub, reproducible Drift, format, fatal-info analysis, Linux real SQLite |
| Web complete | 244 passed, 0 failures/skipped | npm ci, typecheck, lint and production build |
| OpenAPI complete | Valid; 30 passed, 0 failures/skipped | All 23 prior cases plus seven #49 cases; no test removal or ignore-file change |

Final Backend CI is expected to collect 2,148 cases (2,071 retained + 77 added);
only actual completed Linux CI logs may establish the release count/approval.
Native Android/iOS permissions, real camera, physical process death/lost-data,
background/reopen and signed release builds remain #65. No physical QA is claimed.
OQ-054 Return policy and production privacy/retention remain unresolved; #50/#51
Yard Check/Missing, #55 notifications and #61 generic audit remain outside this PR.

### Final native-purpose correction F5

The final native file review found two inherited NSCameraUsageDescription entries:
one described QR Hook/Drop, the other only inspections. Neither described the new
Yard damage purpose. F5 consolidates one shared QR/inspection/damage description
and broadens the existing private Photo Library purpose text to operational evidence.
No native permission, dependency, background mode or location grant is introduced.
Python plist parsing and the duplicate-key count verify the actual published XML.
An added contract regression enforces the single camera/library key, stated damage
purpose and absence of background/Always location. All 31 OpenAPI tests pass,
retaining 23 prior cases and adding eight #49 cases. Physical OS permission/camera
behavior remains #65; this static verification is not a device test.

### #49 implemented HTTP contracts

All four POSTs require a UUID Idempotency-Key, closed JSON, no query fields,
fresh YARD_MOVE_EXECUTE, ACTIVE membership/Tenant and same-Tenant active Trailer
and selected active Site. Mutations additionally require their exact capability.
Common physical fields: trailerId, siteId, expectedTrailerVersion (integer >=0),
observedAt (nonfuture RFC3339, four-digit year), and reason (trimmed nonblank <=4000).

| POST route | Additional fields / change | Capability / event |
| --- | --- | --- |
| /yard/location-corrections | newLocationId, different active same-Site normalized destination, no lastSeenAt regression | TRAILER_LOCATION_CORRECT / LOCATION_CORRECTED |
| /yard/load-state-changes | loadState EMPTY/LOADED/UNKNOWN, different from prior value | TRAILER_STATE_CORRECT / LOAD_STATE_CHANGED |
| /yard/out-of-service | comment replaces reason; documentIds 1–50 distinct finalized own exact-Trailer DAMAGE_PHOTO IDs | TRAILER_OOS_MARK / MARKED_OOS |
| /yard/discrepancy-reports | No expectedTrailerVersion; recordedLocationId nullable but present, reportedLocationId, observedTrailerVersion | YARD_MOVE_EXECUTE / no physical event |

Report fields also include trailerId/siteId/observedAt/reason. Historical versions
may be older, never future. Report preserves historical recorded observation,
does not update Trailer/version/location/load/serviceability and needs no correction
grant. POST returns {trailer,report}; report includes id, actorUserId, observedAt,
recordedAt and clientOperationId. GET /yard/discrepancy-reports is separately
authorized with TRAILER_VIEW_ALL, bounded limit 1–50 and UUID chronological cursor;
no update/delete or notification worker is provided.

Physical commands return {trailer,movementEvent}; OOS also returns documentIds.
Events contain id/eventType/occurredAt/recordedAt. Provenance is HUMAN_USER /
USER_ACTION, authenticated actor and original UUID; no invented Trip/Driver/Tractor.
Trailer row then live Site/Locations are locked before authority is rechecked.
Version conflicts are STALE_VERSION; invalid/context conflicts RESOURCE_CONFLICT;
evidence failures include EVIDENCE_REQUIRED/UPLOAD_NOT_COMPLETE/RESOURCE_NOT_FOUND.
Foreign/invisible resources are safe 404; there is no silent location repair.
Location correction is an explicit physical observation. Load State correction
preserves location (including unknown/in-transit), lastSeenAt, coupling, serviceability,
Orders and designation. OOS changes serviceability only and never closes a Trip or
performs Drop. Original #40 administrative routes/body/server-time behavior remain
compatible and share the same physical projection/event writer.

V23 adds append-only yard_discrepancy_reports and trailer_oos_evidence with Tenant
composite FKs, runtime RLS, SELECT/INSERT-only runtime grants and immutable history.
The OOS association guard requires the actual same-transaction MARKED_OOS event,
same Trailer/actor/UUID and one uploaded own exclusive Trailer link per document.
Each document may be consumed by one Yard OOS event. State, event, accepted evidence
and ledger receipt commit together; complete rollback includes version and event.
R2 transport precedes PostgreSQL and is not a distributed transaction.

### #49 scoped Trailer DAMAGE_PHOTO producer

POST /trailers/{trailerId}/damage-uploads requires TRAILER_OOS_MARK, ACTIVE Tenant/
membership and a real active own-Tenant Trailer; no Driver/Trip is required. The
existing closed Initialize DTO admits documentType=DAMAGE_PHOTO, image/jpeg or
image/png, positive deployment-bounded sizeBytes and optional SHA-256. Mobile
always captures and verifies a private byte copy/hash. It returns the existing
201 PENDING signed PUT reservation and persists an exclusive TRAILER link.
Initialization is not idempotent; lost ACK can leave an unused PENDING reservation.

The original upload-completion/metadata/download routes are reused. Completion
requires own uploader, fresh OOS capability and active Trailer, verifies bytes/
size/hash and the same-key ledger before UPLOADED. Generic links cannot relink a
Trailer-scoped photo into assigned Trip work. No permanent private URLs or R2
credentials enter SQLite. Metadata/download retain all-linked-resource scope;
no DOCUMENT_VIEW_ALL grant is added to Spotter. Upload/finalization alone is no
OOS event or accepted operational evidence: /yard/out-of-service validates and
associates the finalized set atomically to the actual event. Road/POD/Fuel/inspection
transport and evidence policy remain unchanged.

## #49 typed corrective graph and evidence recovery

YARD_MOVE, LOCATION_CORRECTION, LOAD_STATE_CHANGE and OOS share the existing
TRAILER resource lane, CommandStore, ReplayWorker, immutable request/result and
UUID/Idempotency-Key. The next physical intent references the previous physical
operation, never an invented version. It materializes only after a verified
same-User/Tenant/Trailer ACK result. OOS additionally waits for every own bound
photo to be FINALIZED; document IDs are resolved once before sending. A frozen
request never recalculates coordinates/location/version/photos during replay.

DISCREPANCY_REPORT is append-only observation with its historical downloaded
location/version; it does not increment Trailer version and cannot be a physical
predecessor. Its observation resource lane can sync even after a physical conflict.
Existing failed physical parents continue blocking old descendants; unrelated
Trailers continue normally. No operation is converted into another type.

Explicit conflict review first downloads current authorized Trailer state. Failed
reload remains closed. Only definitive failed same-Trailer Yard work may receive
an explicit new root action with a new UUID/version, recorded in immutable local
yard_conflict_reviews. Original requests/UUIDs/failed states remain preserved,
covered old retries are prohibited and old descendants never automatically run.
Uncertain queued/sending/retryable work cannot be replaced. No false ACK is forged.

Damage drafts/capture metadata persist before the native camera and survive restart.
Photos use existing private files/hash, LocalMedia, R2 processor and finalized
media edges, not a second queue/worker. Late camera results stay in their original
User/Tenant namespace; current account permission never transfers. Missing bytes,
denials, upload retries and partial sets retain draft/evidence for explicit review.
Network 408/429 Retry-After/5xx, 401 refresh, 403/404/409, lost ACK and two workers
retain the original same-key frozen request. Only a real server receipt is success.

## #49 deployed schema: Drift v8

Version 8 preserves the one v7 command/media graph and adds nullable immutable
local_media.trailer_id for DAMAGE_PHOTO without Trip. Three narrow metadata stores:
yard_drafts (owner, selected Site/Trailer, immutable observed version/ref/time,
comment, retained photo IDs, eventual command ID), yard_captures (persisted camera
ownership and PENDING/CAPTURED/FAILED/CANCELLED outcome), yard_conflict_reviews
(immutable old-failed/new-action links and reviewed authorized projection/version).
They are not queues. Existing local_commands/materialized_requests/command_results/
command_media_dependencies remain the only durable operation graph.

Scope/FK/content/queued-draft/late-camera and immutable review guards apply in
SQLite. One native camera acquisition across Inspection/Road/Yard contexts is
allowed. v7→v8 adds metadata without rebuilding commands/results/requests. A
frozen actual published v7 DDL fixture verifies migration and retained ACK bytes;
earlier v1–v6 migrations retain their historical constraints and data. Fresh schema
has 21 tables; all upgrades, FK checks, namespace and ledger guards are tested.
Read caches may be cleared on logout; pending commands/drafts/private bytes are
retained in their original owner namespace. There is no GPS/history store.

### #49 implemented review and damage UX

Scan → recorded Trailer → explicit action. Location uses current recorded value,
authorized destination, reason and Confirm Correction. Load State shows current
and new value/reason and preserves other dimensions. Without correction grant,
Report Discrepancy is available and explicitly creates no state change.

Damage uses the existing injected NativeInspectionCamera/ImagePicker: real image
bytes are privately copied/hash-verified, comment and all photo IDs retained before
explicit Confirm Mark OOS. Not Yet preserves drafts/photos and sends no OOS.
Upload/finalization is independently visible and never serviceability success.
Saved local, waiting evidence, queued/sending, server ACK and conflicts are distinct.
There is no Return-to-Service shortcut or new capability grant. Forms close on
User/Tenant/Driver/Site/Trailer/version/mode changes; double confirmation is fenced.

Conflict handling retains original commands and descendants. A fresh authorized
read and explicit review may create a new action; failed reload or uncertain ACK
does not permit rebasing. Old damaged evidence can be explicitly retained in a new
reviewed draft without changing its file/hash/identity. Physical Android/iOS camera,
permission/reduced access/background/restart/lost-data QA remains #65; injected
camera/Linux widget tests are not physical-device QA.
