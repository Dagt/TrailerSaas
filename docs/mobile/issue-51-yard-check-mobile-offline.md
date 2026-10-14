# Issue #51 — Yard Check mobile scan and durable offline replay

Verified base: `9446576d6d9a26a1f8f4f11005ba90a360ed0f95`, merge of PR #107;
#50 closed. Clean tree/no stashes or pre-existing #51 branch. Branch:
`issue-51-yard-check-mobile-offline`. No merge or unrelated implementation.
Baseline CI: Backend 2,251 / Mobile 467 / Web 244 / OpenAPI 39.

## Acceptance matrix prepared before implementation

| Gate | Existing boundary / implementation plan | Required evidence |
| --- | --- | --- |
| A authorized context | Existing SessionController, YardModeController, Yard Site/Location reads, current downloaded capabilities | Yard-only/dual-role, fresh server authority, no Driver/Tractor invention |
| B persistence | Incremental Drift v8→v9; owner/Check FK caches and immutable observation metadata | Real frozen v8 and all older upgrades, file reopen, rollback, FK/owner isolation |
| C start | Existing CommandStore/materialized request/ledger; online-only new intent, server Check ID from validated receipt | Lost ACK/reopen same UUID; no offline-generated Check or inventory |
| C full snapshot | All real detail pages, UUID/count/scope/version validation and atomic cache commit | 0/1/200/201/206; repeated IDs/cursors, stale pages, late owner callback, rollback |
| D QR observations | NativeQrScanner and owner-scoped verified cached bindings; unknown token durable unresolved intent | No fabricated Trailer/ACK/classification; original token/time/UUID, duplicate/fenced camera |
| E replay | Typed Check adapter in the one existing ReplayWorker; same backoff/claims/results/issues | Resolve online through trusted QR + own Tenant Yard read, frozen payload, retries/auth/conflicts/two workers |
| F terminal | Explicit online authoritative Review & Complete/Cancel after resolved scan ACKs | Actual fresh version, reviewed state fencing, immutable retry, no local terminal/MISSING fabrication |
| F reconciliation | Existing #50 endpoints and current Trailer version/correction capability | Human reason/confirm, actual event/Alert receipt, immutable finding, no scan-induced physical effect |
| F Off Duty | Actual Shift capture and replay entry points, not only Yard Home | Known unfinished Check blocks, unknown/stale warns honestly, old Road/Post-Trip conditions retained |
| G security | Namespace and generation fences, private token only, current typed ACK validation | Account/Tenant/mode/dispose/reconnect races, late receipt, malformed response, no starvation |
| H/I review | Published recoverable checkpoints, no migration V24 redesign, full historical tests | Full local checks and four completed exact-HEAD green CI jobs before Ready; NO MERGE |

## Initial architecture audit

Current Drift is v8 with one local_commands/dependency graph, materialized_requests,
command_results, sync issues and media lane. ReplayWorker already serializes sends,
reauthenticates after Firebase 401 and preserves immutable requests across lost ACK.
Add typed YARD_CHECK operations to that infrastructure, not another queue/worker.
Check creation persists its start operation before online send; only a validated
server receipt creates a cached Check identity. Recovery always reuses the same UUID.

The existing opaque QR resolver returns Trailer identity; snapshot items contain
no QR token bindings. Use only previously verified same-owner bindings for local
identification. Unknown tokens remain private UNRESOLVED_QR observations and queued
typed scan intents with original time/UUID. Before first HTTP materialization,
resolve through the existing configured resolver and verify its own Tenant Yard
Trailer. Never fetch a scanned URL, expose a token directory or invent a UUID.
Cached identity is preliminary identification, not authorization; newly materialized
requests require live validation. Frozen retries retain the actual original body.

Start is online only. Complete/cancel require synchronized observations plus an
online authoritative review; offline completion capture is optional in the request
and will not be introduced unless its safety is proven. Store the reviewed state,
revalidate before first materialization and require explicit review if it changed.
Never infer Check version by adding scan counts. A frozen terminal retry keeps its
original version/key even after the server committed and its ACK was lost.

The current Backend Off Duty validates Driver work/Post-Trip, not Yard Checks.
This issue will explicitly document client-only Check enforcement rather than claim
a server guard. Known associated unfinished/uncertain Check work blocks capture/
replay; unknown or stale Check directory status is an explicit warning. No Yard-only
Shift/Driver/Tractor assignment is invented. OQ-059/OQ-057 and device release #65
remain separate; no physical Android/iOS QA is claimed by injected/Linux tests.

## Validation record

Implemented checkpoints A–G are published on `issue-51-yard-check-mobile-offline`.
Focused SQLite snapshot/replay, widget, lifecycle, concurrency and reconciliation
suites have passed locally. Full final-source validation and final-HEAD CI are the
remaining H/I gates; a Draft PR is not release approval. The PR description will
record the exact final SHA, completed Actions run and real counts.


## Implemented architecture and ownership

YardChecksPanel is integrated into YardHomePanel, using the existing design system,
NativeQrScanner and injected scanner port. YardCheckController fences authenticated
UID, User/Tenant/Driver, generation, selected Check, foreground and disposal. Known
Yard-only role users need no Driver or Tractor; Road/Yard mode switching retains
owner-scoped saved inventory and commands. Existing Yard Site/Trailer reads retain
their original Backend capability requirements; no new permission is inferred from
QR identity or local cache. Start requires the authorized Site download and a live
connection. Every mutation is freshly authorized by the existing Backend.

CheckStore downloads every detail page with limit=200 and expectedVersion on all
continuations, validates Check origin/status/version/counts, unique Item/Trailer
IDs, cursor progression and all classifications, then commits the entire candidate
atomically. A version race restarts from page one, at most three attempts per
foreground action, to bound work during concurrent inventory changes. Other
malformed/incomplete candidates fail closed and retain the previous valid snapshot.
A valid empty inventory is complete. Directory pages are also validated and
committed before success is displayed. Explicit denial blocks cached scanning;
later authorized reads restore availability. Older reads cannot regress a newer
Check or remove confirmed observations/reconciliation associations.

Drift v9 adds five metadata/cache tables: cached_yard_checks,
cached_yard_check_items, yard_check_observations, yard_check_reviews and
 yard_check_directories. Composite owner/Check FKs bind children and the original
operation graph; origin facts and observations are immutable and deletion is
rejected. Scan timestamps are ISO UTC text so fractional seconds survive SQLite.
The narrow v8 upgrade transaction evolves the two closed queue/result enums and
adds the five tables/indexes/guards without erasing data. Existing migration paths
v1–v8 remain tested. The frozen deployed v8 DDL fixture includes pending commands,
finalized media and Yard capture metadata; a late migration fault proves rollback
of both schema/version and data before a successful reopen/retry. Fresh v9 has 26
tables. Generated Drift source is committed and checked by generation/diff in CI.

## Start, offline identity and frozen replay

CheckIntent/CheckReplay extend the original CommandStore/ReplayWorker, not a second
queue or HTTP engine. Start is durably recorded before its online POST; its aggregate
is null until a validated server receipt supplies the actual Check ID. Lost ACK
recovery uses that original UUID and request. A historical Start receipt may
acknowledge the operation without replacing a newer live Check projection. The UI
then distinguishes accepted, downloading, incomplete and complete/offline states.

Scans are durably saved before HTTP. Previously verified same-owner QR bindings
provide preliminary identification, using the original expected snapshot to label
local match/mismatch/unexpected. An unknown opaque QR token is private
UNRESOLVED_QR evidence with no fabricated Trailer ID/result. Before first request
materialization the existing configured QR resolver and own Tenant Yard Trailer
read verify identity and active authorization context. Scanned URLs are parsed only;
never fetched. Resolved identity/time/observed location and UUID are preserved; the
public token is not sent to the scan endpoint or exposed in UI/logs. The frozen
request contains exactly trailerId, observedLocationId and scannedAt. Revoked or
foreign tokens remain failed evidence for human review. No token directory exists.

A repeated token/known Trailer retains its first local operation. Different unknown
tokens resolving to the same Trailer receive the actual Backend ALREADY_SCANNED
receipt and do not double count inventory. Check resource lanes retain capture
order; an unrelated movement can continue after a Check conflict. Existing 401
refresh, retries/backoff/Retry-After, claim/recovery, sync issues and result guards
apply. Typed identity/state/time/event/alert validation is required before ACK;
HTTP 200 alone never confirms a scan or physical action.

## Terminal review, findings and physical authority

Review & Complete/Cancel is an online workflow, after every unreviewed scan has a
validated ACK. Unresolved, uncertain, blocked or conflicted observations prevent
terminal review. Current authorized full state is shown for explicit confirmation;
materialization fetches it again and requires the exact reviewed state/version.
No version arithmetic or silent refresh-under-the-same-key occurs. If the state
changed, REVIEW_REQUIRED retains the failed intention and requires new human
review. Once frozen, an uncertain retry keeps original version/body/key. Cancellation
requires a nonblank reason and retains all partial evidence. Neither terminal intent
nor local scan makes MISSING/COMPLETED/CANCELLED authoritative before server ACK.

Failed Check operations may be explicitly reviewed against fresh authorized state
with a reason; only rejected/conflicted/blocked operations qualify. They remain
historical failures, never fake ACKs. Uncertain SENDING/retryable work cannot be
excluded. The original observation/request is retained, and reviewed failed resource
lanes can be released without rewriting immutable dependencies.

Completed MISSING/mismatch findings expose separately authorized reconciliation
only with TRAILER_LOCATION_CORRECT, actual current Trailer projection/version,
reason and human confirmation. Pending physical Trailer commands must be resolved
first. Existing #50 mark-location-unknown/location-correction endpoints produce the
real event and (for Missing) alert receipt, which is displayed as separate history;
original expected/finding facts remain unchanged. No scan, completion or cancellation
calls physical reconciliation automatically. Stale/denied/malformed receipts retain
the command and never silently overwrite location. Backend V24 and APIs are unchanged.

## Off Duty and release boundaries

InspectionStore.offDuty rejects known associated IN_PROGRESS/denied/uncertain Check
work at the actual capture entry point. ReplayWorker also blocks an already queued
Off Duty while such work is known, without closing the Shift locally. Confirmed
terminal refresh releases the local blocker. The Shift UI warns that offline/stale
cache cannot certify absence of open Checks. Existing Post-Trip/Driver/work guards
are preserved. Backend #50 does not enforce open Checks at Off Duty: this is an
explicit client guard/warning, not a claim of server-wide enforcement.

No Backend, Flyway V24, state-machine, authentication, QR/camera dependency, media,
GPS permission or tracking change is part of #51. No Firebase credentials/signed
URLs are stored. Check history and old-owner commands survive logout but cannot be
read by another User/Tenant; QR read-cache clearing follows the existing privacy
policy. Android/iOS physical camera, cancellation, suspend/reopen, accessibility and
network/permission behavior still require release #65. Tests use injected ports and
real Linux file-backed SQLite; they are not physical-device QA. OQ-057/OQ-059,
Administration inbox/push/notifications, automatic reconciliation and unrelated
issues are deferred. NO MERGE.

## PR and reproducible validation record

PR: [#108 — Yard Check mobile scan and offline replay](https://github.com/Dagt/TrailerSaas/pull/108).
Verified base: `9446576d6d9a26a1f8f4f11005ba90a360ed0f95`.
Final Flutter source checkpoint: `e2b96d189e2afe5d9309fc5c1644c7972c6f2709`.
The PR description records the exact final published HEAD and direct final Actions
run/job logs, their counts and readiness. Documentation-only publication does not
change this source checkpoint. Ready is permitted only after all four jobs complete
successfully on that final HEAD. This source record does not claim a pending job
passed and is not a physical-device release certification.

Local final-source Mobile: **528 passed**, no failed/skipped tests. Flutter 3.47.6 /
Dart 3.13.5 locked dependencies, Drift generation/source equality, full formatting,
fatal-info analysis and real file-backed SQLite tests passed. Web **244 passed**
plus npm ci/typecheck/lint/build; OpenAPI **39 passed**, zero failures/skipped, plus
accepted-contract validation. Java 25 Maven clean verify with PostgreSQL 18.6
Testcontainers is running at publication; its completion and final-HEAD CI remain
required gates, not inferred success. Backend/API/V24 source is unchanged from the
verified base. Earlier failed local OpenAPI invocation lacked copied native config
files in the helper workspace; copying the complete project reproduced all 39
unchanged historical contracts successfully, without changing tests or workflows.

All SQLite persistence/migration claims were tested against files in Linux,
including two simultaneous connections. Native Windows format/analyze also passed;
Windows SQLite DLL policy is not substituted for Linux persistence evidence.

The final audit found and fixed nullable owner-key equality after auth switch,
late Site/scanner callbacks, terminal double-review, JSON field ordering in review,
wrong-scope ACKs, older Check read regression, directory denial recovery, and
historical Start ACK projection regression and a false completeness marker on an
empty first page. Regression tests cover these boundaries
without disabling or removing previous tests. Malformed HTTP success never ACKs;
original failed/uncertain commands and evidence remain retained.
