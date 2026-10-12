# Issue #43 — Driver Shift and inspections

Baseline: `4580626595bd7f8ba3aa18ec46d9705253848578`, verified merge of PR #101.
Working branch: `issue-43-driver-shift-inspections`. No merge.

## Acceptance and implementation plan

| Criterion | Existing implementation | Addition | Required verification |
| --- | --- | --- | --- |
| On/Off Duty | Identity, Driver identity, mobile operation ledger | Inspections-owned Shift commands and own-home projection | HTTP, fresh authorization, replay, concurrent open/close |
| Persistence | V1–V20, TenantContext/RLS | V21 shifts/corrections/inspections, exact historical inventories | PostgreSQL 18, constraints/grants/FKs/checksums |
| Four Pre-Trip photos | OperationalEvidencePolicy and private R2 | Typed inspection orchestration and permanent inspection links | Missing/type/pending/foreign/reuse and atomic rollback |
| Post-Trip | Accepted request only | Immutable structured record, separate Shift closure | No photos required, chronology and active work |
| First departure | TripDeparturePrerequisites fail-closed port | Real Shift/inspection prerequisite adapter | Existing departure with real requirements; original physical rules |
| Mobile/offline | Drift v4, CommandStore, media phase and immutable materialization | Typed Shift commands/results, real generated-ID dependencies, camera forms | Migration/restart/ACK loss/namespace/lifecycle/widget |
| Architecture | Documents depends on Trips; reserved Inspections module | Inspections orchestrates existing public ports; home/attachment callbacks owned by consumers | Modulith without Java module cycles |
| Scope | #44 execution, #46 replay, #52/#53/#54 media | Shift/inspection workflows only | No #45 endpoints, payroll, fake state or overrides |

## Decisions pending or constrained

OQ-052 leaves checklist questions un-authored. The implementation must persist a
bounded structured checklist without inventing a regulatory catalog or treating
client answers as permission. Four validated photos and authorized real context
remain server requirements.

The owner confirmed the MVP assignment boundary: only active Tractors from
nonterminal Trips currently assigned to the authenticated Driver are eligible.
The selector deduplicates Tractors; PostgreSQL revalidates the assignment at save.
An inspection may have tripId=null with a currently assigned Tractor and own open
Shift. Without eligible equipment, show “No tienes un Tractor asignado. Contacta
a operaciones.” A later reassignment leaves offline intent/photos retained as a
conflict. No independent Driver–Tractor relation or master-data browse grant exists.
Inspection before any Trip assignment requires an explicit future assignment model.

Inspection occurrence and persistence chronology must remain distinct for offline
commands. V21 includes an explicit occurred_at alongside created_at; this is a
bounded storage extension of the existing request's occurredAt, not a new state.

## Recoverable checkpoints

A: V21 and persistence security. B: Shift commands/home/ledger. C: inspection/photo
association. D: real departure prerequisites. E: Flutter/camera/offline. F: security,
contracts and final-head CI. Record validation and next steps after each publication.

### Checkpoint A

V21 creates the three accepted tables, composite Tenant/Driver/Shift foreign keys,
one-open-Shift index, insert-only inspection/correction history, Tenant RLS and
minimal runtime grants. Inspection photo ownership extends document_links with a
unique INSPECTION association; its production writer/read authorization is next.
Historical table inventories and latest-version assertions remain exact, with all
previous columns/checksums preserved. 140 affected PostgreSQL/module tests passed:
0 failures, 0 errors, 0 skipped. V1–V20 unchanged.
Next: finish real Shift/home/ledger commands, then inspection orchestration.

### Checkpoints B/C/D

On/Off Duty, own-home enrichment, typed strict requests, fresh authorization,
exclusive Driver serialization and MobileOperationLedger are implemented. Off Duty
blocks all unresolved assigned Trips and missing end-of-day Post-Trip equipment
requirements; it changes only the Shift. Domain events accompany durable Shift
effects and the existing ledger, without an outbox or new ledger.

Pre/Post-Trip orchestration creates immutable records. Four own finalized photos
are locked in UUID order, validated with #53, and attached permanently in the
same transaction. Existing Trip links and inspection links use all-or-nothing
document authorization; arbitrary HTTP inspection attachment is rejected.
Photos cannot be reused for another inspection. The no-Trip path stores inspection
links only. Post-Trip has no required photos and never changes physical state.

The production departure adapter now queries real open Shift/valid matching
inspection/photos and occurrence time. Trips consumes its existing port, implemented
by Inspections; Documents consumes a Documents-owned inspection read callback.
No reverse Java module dependencies/cycles or lock upgrades are introduced.
42 affected tests passed, including the real complete On Duty → four-photo
Pre-Trip → departure → arrival/service/non-delivery completion → Post-Trip →
Off Duty sequence, rollback, lost ACK and Modulith. Existing physical tests still
use their confined prerequisite fixture, now including the time-aware port method.
Next: extend the namespace-safe mobile queue for commands without Trip and durable
generated Shift-ID dependencies, then camera/forms/UI and full regressions/CI.

### Checkpoint E

Drift v5 preserves legacy v1–v4 tables/rows and permits typed Shift/inspection intents
without invented Trip IDs. Confirmed result bindings supply the real Shift ID;
immutable materialization waits for all finalized media and ACK dependencies.
Native camera uses durable private capture context, original namespace copying,
active-request fencing and recovery of complete files after SQLite failure. Drafts
remain editable until queued; camera-in-flight prevents queueing. Road home/forms
include On/Off Duty, four-photo Pre-Trip, Post-Trip and conflict/sync visibility.
Used equipment remains available for Post-Trip when assigned Trips have completed.
277 Mobile tests and fatal-info analysis passed locally. 19 inspection PostgreSQL
regressions and 11 Shift command tests pass; 2 module tests also pass. Exact current
CI counts will be recorded after the final-head four-job run. No #45 endpoints.
Next: final independent security review, remaining contracts/docs and full CI.

## Final architecture and safety review

Inspections owns the Shift/inspection transactions. Dependencies point from
Inspections to public Identity/Drivers/Tractors/Trips/Documents contracts. Trips
owns TripDeparturePrerequisites; Inspections supplies its bean. Drivers owns the
own-home callback; Documents owns InspectionDocumentDirectory. ObjectProvider
callbacks avoid reverse module imports. Module boundary tests verify no cycles.
Lock order: Tenant authorization -> optional exclusive parent Trip -> exclusive
active Driver -> own Shift -> active Tractor -> UUID-sorted Documents -> existing
ledger. First departure retains Trip -> shared Driver -> ordered Stops -> equipment;
its prerequisite read never upgrades these locks. Driver-exclusive Shift operations
serialize against execution and current assignment validation. On/Off Duty do not
lock Trips after Driver; their blocker query is a bounded current-state read.

Inspection/photo creation is one Tenant-pinned transaction. Attachment requires a
new own Pre-Trip inspection in that same PostgreSQL transaction; normal Documents
HTTP links do not accept INSPECTION. A photo may belong to only one inspection;
Trip context links can coexist. All document links must be authorized as a set.
Current Driver/Customer authorization is always reevaluated; uploader history cannot
bypass linked-resource visibility. R2 temporary/final publication, byte checksum,
private keys, immutable UPLOADED state and completion ledger are unchanged.

V21 adds occurred_at to inspections to preserve the accepted offline request's
occurrence separately from recorded created_at. The three tables use composite
Tenant foreign keys and historical User attribution. Corrections are stored but
no arbitrary correction endpoint exists. Inspect/close cannot use another Shift.
No destructive cascade, normal DELETE, inspection UPDATE, role/capability expansion
or V1–V20 edit is introduced. One partial unique index prevents duplicate open Shift.

## Mobile identity and crash protocol

Drift v5 upgrades v1/v2/v3/v4 incrementally. Original queue payload/path/key remains
immutable; aggregateType distinguishes TRIP/SHIFT/INSPECTION without fake IDs.
command_results contains only validated historical Shift/inspection receipts;
command_result_dependencies binds a real generated Shift ID to its prior On Duty
ACK within the same User/Tenant. All four finalized media and result dependencies
must be resolved before materialization. That separate final HTTP request is frozen
before claim; exact body/path/UUID survives timeout, ACK loss and restart. Existing
physical response validation remains strict and separate from inspection validation.
Off Duty waits for unresolved current namespace operations conservatively: uncertain
physical work cannot be silently abandoned. Conflict review retains original intent.
No future delivery/Fuel/report endpoint is supported by the installed adapter.

inspection_drafts holds edit inputs until queueing; camera_captures stores immutable
namespace/draft/position before native UI. A shared in-flight fence protects camera
recovery while an actual capture is running. Files are copied with UUID names into
private #54 storage, streamed and checksummed; a late old-account result remains in
its original namespace. Capture blocks queueing until resolved. Complete private
copies can repair failed SQLite associations; ambiguous native loss/partial files
require an explicit retake and remain retained. No fake photo or visible old-user
callback. Existing worker/file lane fencing and SENDING recovery are preserved.

## Native adapter decision and limits

image_picker 1.2.4, maintained under flutter.dev, supplies Source.camera and Android
retrieveLostData; requestFullMetadata=false. It is a platform adapter, not a second
camera/storage/sync system. Camera permission and iOS purpose strings are explicit;
no video/audio/GPS workflow or public gallery destination. Host tests use controlled
XFile results, real private files, SQLite and localhost HTTP/S3. CI cannot prove
physical camera permission dialogs or Android/iOS process-death behavior; device
QA remains required. OS backup/encryption limitations remain those documented for
#54; no homemade encryption or embedded credential is introduced.

## Acceptance -> code -> verification

| Requirement | Owner / code | Verification |
| --- | --- | --- |
| One real open Shift, own reads | ShiftService, ShiftRepository, DriverShiftContext | ShiftCommandsPostgresTests; ShiftSchemaPostgresTests |
| Fresh capabilities, isolation and replay | TenantAuthorization, DriverIdentityPolicy, MobileOperationLedger | Suspended/revoked/inactive/foreign requests; incompatible UUID; lost ACK |
| Four real photos, atomic association | InspectionService, OperationalEvidencePolicy.attachPreTripInspection | Each missing/pending/type/duplicate/foreign photo; insert failure rollback; concurrent reuse |
| MVP assigned Tractor selection | DatabaseDriverShiftContext, InspectionStore, ShiftHomePanel | Deduplicated nonterminal assignment; absent/inactive/reassigned Tractor; explicit/null Trip |
| Real first departure | DatabaseTripDeparturePrerequisites | Complete real SQL/HTTP sequence; other Shift/Tractor/explicit Trip mismatch; original execution regressions |
| Post-Trip and Off Duty | InspectionService, ShiftRepository.blockers | No required photos; active-work block; Post failure/Off failure rollback; concurrent close; Trip finish leaves Shift open |
| Durable mobile / generated IDs | Drift v5, ShiftReplay, CommandResults | Real v1–v4 migration tests, frozen body/key, restart, lost ACK, media/dependency gating |
| Camera/forms/namespace | InspectionCapture, InspectionStore, ShiftHomePanel/InspectionScreen | Four private files, interrupted association repair, native in-flight fence, logout; widget labels/controls |
| Existing media/physical behavior | Original ReplayWorker/MediaProcessor/R2 | Full #42/#44/#46/#52/#53/#54 regressions, SENDING tests, exact SQL/schema assertions |
| No future workflows | Strict installed ShiftReplay / OpenAPI | #45 delivery/Fuel/exceptions remain unimplemented, no generic POST executor |

## Deferred

#45 owns POD/Delivery/Fuel/Report Problem workflows; #49 owns Yard/OOS workflows;
#47 geofences, #55 outbox/jobs, #61 generic Audit and OQ-057 destructive retention
remain untouched. No administrative Shift correction/override or payroll. OQ-052
checklist catalog remains open. Photos plus checklist storage are not regulatory
certification or recognition of damage/signature. Shift completion never completes
Trip/Order or changes physical state. Current assignment inspection limitation is
explicitly MVP scope, not a new independent Driver–Tractor model.

### Checkpoint F — audit and validation

Atomic v5 migrations now commit their version marker with schema/data and check
foreign-key integrity; an induced late index failure proves rollback/retry from v4.
Additional replay regressions cover 408/429/5xx, one Firebase renewal and recorded
departure waiting on matching Pre-Trip without local state changes. Selector/widget
regressions prove deduplication and the no-equipment message. All 284 Mobile tests
passed locally with locked dependencies and generated Drift code; OpenAPI 11 passed.
The full local Backend run identified two additional historical exact table-count
assertions in AuthorizationPostgresTests/TenantRlsPostgresTests: V21 adds three
tables, so the exact count is 37 rather than 34. Assertions remain exact; no grants,
columns, checksum or isolation checks were removed. Final validation requires all
four jobs on the published final SHA. Device camera QA remains as above.
