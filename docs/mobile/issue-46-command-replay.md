# Issue #46 — Persistent command queue and foreground replay

Implementation baseline: main after PR #97, `3449ab0c2bda008be1faa0e47394adf09379729e`.
ADR-012 and the accepted offline protocol remain authoritative. This adds the client
queue; it does not change REST commands, backend state machines, capabilities or RLS.

## SQLite migration and immutable intent

Drift schema v3 incrementally upgrades both deployed v1 (empty scaffold) and v2
(four populated session/work cache tables). Existing cache rows are preserved.
`local_commands` stores immutable UUID, internal User/Tenant, command type, POST
endpoint, original JSON, Trip/aggregate identity, physical resource keys, captured
occurrence, creation time and explicit dependency UUIDs. A per-namespace sequence
breaks same-second SQLite timestamp ties without altering occurrence/creation time.
Mutable sync fields are state, attempts, retry eligibility, safe HTTP/error code and
server acknowledgment time. SQLite CHECKs enforce the seven states and POST method;
a trigger prevents rewriting the original identity, payload, dependencies or chronology.

`local_sync_issues` stores namespaced review categories, a safe code/message, resource,
creation and resolution time. Only a server acknowledgment resolves the issue;
pressing Review/Retry is not proof of success. Both tables have namespace indexes.
No bearer token, Firebase claims, password, arbitrary response body or media enters
the queue. Files/database remain app-private. Public QR identities may be cached in
the existing relevant-Trailer projection after an authenticated scan, never in a
command payload and never as authorization. Session cache replacement/cleanup does
not delete commands or unresolved issues. Another identity/Tenant cannot read or
send those rows through the app. Returning to the original identity requires session
and current backend authorization again.

Acknowledged lightweight history is retained; no unresolved command is auto-pruned.
Automated retention/secure eviction awaits an accepted grace-period policy. OS backup,
native device protection and real-device validation remain deployment concerns.

## Transition from the PR #97 journal

`FileOnlineCommandJournal` is retained as a compatibility reader for the previous
one-receipt User/Tenant namespace. Before replay or capture of a subsequent intent,
the queue transaction inserts the original UUID/path/body unchanged, verifies the
committed row, then conditionally deletes only that old UUID's file. Crash before
commit leaves the file. Crash between commit and deletion results in idempotent
re-import. Even an already acknowledged row cannot be resurrected. A corrupt file,
different existing payload or failed SQLite write leaves the receipt recoverable
and fails visibly; no new key substitutes for it. Legacy foreground controllers
remain injectable for compatibility/regression tests; production Road uses Drift.

## Worker and dependencies

One foreground service owns synchronization for the app's single SQLite connection.
Worker passes share a serialized lane, so no Trip/Trailer/Tractor sends overlap.
SQLite compare-and-set claims QUEUED/RETRYABLE_FAILURE as SENDING before HTTP. On
restart/namespace activation, interrupted SENDING is restored as retryable with the
same immutable key. Account/Tenant changes pause the old namespace. Late responses
cannot acknowledge or restore another identity's context; later authorized replay
uses the backend ledger to resolve an uncertain committed result.

New commands can depend on several earlier commands in the same namespace. Missing,
self, duplicate and cross-namespace dependencies are rejected. Only earlier immutable
edges can be inserted, preventing new cycles; defensive graph validation also blocks
corrupt/missing/cyclic stored graphs. Recording adds the latest unresolved shared
Trip/Trailer/Tractor predecessors transactionally. Each dependency must be ACKNOWLEDGED
before sending. Conflict/permanent failure blocks descendants; an independent chain
can continue. Review never rewrites a command or releases a blocked descendant early.

Sync runs on authenticated foreground startup, app resume, reconnect, capture while
online and manual **Sync now**. A bounded pass sends up to 100 commands; a foreground
timer continues a larger eligible backlog. Correctness does not require an OS
background task. The service refreshes Firebase identity and reuses existing explicit
REST endpoints with `Idempotency-Key: clientOperationId`; there is no `/sync` endpoint.

## Response policy

| Result | Persisted behavior |
|---|---|
| No network | Keep intent; wait for foreground/reconnect. No first offline login. |
| Transport, timeout, 408, 5xx | RETRYABLE_FAILURE with exponential backoff and jitter; identical key/path/body. |
| 429 | Same retry policy, never earlier than Retry-After (seconds or HTTP date); eligibility is persisted. |
| 401 | Refresh Firebase token and allow exactly one immediate attempt with the same key. A second denial becomes an authorization conflict and pauses/revalidates context. |
| 403/404 | CONFLICT; fresh permission/assignment revalidation. The original outcome may remain uncertain because authorization precedes ledger replay. Retain the operation. |
| 409 | CONFLICT requiring explicit review, including state/version/physical conflicts. Key reuse never becomes a replacement key or a normal retry action. Unknown/malformed 409 also remains for review. |
| Recognized validation/other definitive 4xx | PERMANENT_FAILURE with a visible safe issue; retained, not silently discarded. |
| Malformed success/proxy response | Never ACK; retain original intent for retry/reconciliation. |
| Valid acknowledgment | ACKNOWLEDGED, server confirmation time; refresh own Driver/work directory and targeted own-Trip equipment context. Never apply a saved replay response as current state. |

The retry setting starts at 2 seconds, doubles to a 300-second base cap and uses
0.5–1.5 jitter (1–450 seconds); Retry-After can extend this. It is a technical
scheduling setting, not a domain availability rule. Attempts/eligibility survive
restart. Timers are canceled on scope changes/disposal, and do not bypass connectivity.

## Road UX and bounded supported commands

Road shows **Pending synchronization**, **Syncing**, **Confirmed by server**,
**Requires review**, and **Blocked by another operation**, including when no Trip
remains assigned. Sync issues offer fresh work review and retry of the SAME intent.
There is no automatic discard, correction, payload editing or fictitious confirmation.

Only previously downloaded own Driver work may record offline intent. Cached capability
hints constrain UX, but every replay rechecks server membership, Driver and assignment.
The guide records arrival → service → non-delivery completion → next-Stop departure,
including explicit BASE_RETURN. It keeps the downloaded server projection unchanged;
the pending action guide is not a local state-machine replica. Queue reads must finish
before controls open. Each physical action is explicitly confirmed by the user.

Hook/Drop use #38 contracts and existing physical authority. Offline Hook needs a QR
identity already resolved/downloaded plus the downloaded current expectation; unknown
or alternate unapproved QR requires connectivity/dispatch authorization. Drop needs
the downloaded real coupling and a real Stop Location, not the planned Trailer. Drop
then replacement Hook then service completion are separate dependency-linked intents,
not a hidden combined operation. Exact Hook expectedTrailerVersion is preserved;
unrelated server version changes may therefore surface STALE_VERSION under the existing
contract. The worker does not invent a new version or rewrite the request.

## Mandatory integration blocks and exclusions

Production first departure still fails closed until #43 supplies real On Duty/Pre-Trip
records. A cached hint cannot fabricate them. Tests start from a legitimately executing
Trip or use only the existing test-confined prerequisite seam. DELIVERY completion
still requires finalized POD (#45/#53); no fake evidence or load-state mutation is
introduced. Live Load/Unload retains #44 uninterrupted-coupling semantics. Unknown
coupling/QR cannot be replaced by manually entered UUIDs. No Yard, geofence, media,
Shift, document upload, background OS worker or generic sync/domain editor is added.
#45, #47, #52/#53, #54 and Yard issues remain separate.

## Verification

Real SQLite tests cover populated v2 migration, file reopen, immutable requests,
transaction rollback, namespaced ownership, interrupted claims and crash-safe journal
import. Worker tests cover backoff/jitter eligibility, Retry-After, Firebase refresh,
transport loss after commit, all conflict classes, graph ordering/cycles, independent
chains, late responses, account/Tenant changes and a multi-Stop offline sequence.
Widgets verify local intent versus server confirmation and issue/recovery UX without
filesystem/SQLite operations trapped in Flutter's simulated clock.

Real PostgreSQL 18 HTTP tests additionally replay an entire delayed multi-Stop sequence
with both OTHER and explicit BASE_RETURN, including every duplicate and old saved
projection after terminal completion. The existing authorization, concurrency, ledger,
Hook/Drop and physical-history suites remain intact. No H2 substitute or weakened test
is used. Backend/Web/Mobile/OpenAPI jobs remain required.
