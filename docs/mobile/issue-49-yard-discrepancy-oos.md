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
inventories. Native QA is not claimed. D–G remain pending. Native Android/iOS camera/build/lifecycle QA remains #65; simulated
camera and Linux tests do not count as physical device evidence. OQ-054 return
policy and production privacy/retention remain unresolved. No Yard Check, Missing
reconciliation, notifications, generic audit worker or GPS tracking is included.
