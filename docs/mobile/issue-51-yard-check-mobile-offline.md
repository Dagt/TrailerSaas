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

Implementation and tests are pending. Baseline counts are references, not final
approval. Actual final PR/CI evidence will be linked after publication.
