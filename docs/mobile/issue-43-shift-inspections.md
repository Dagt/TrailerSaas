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
