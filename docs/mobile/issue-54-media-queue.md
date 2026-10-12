# Issue #54 — durable private media and command dependencies

Baseline: `f2bb5529466acbcd145fa1f4e8007c0efe115ef8`, merge of PR #100.
The approved #53 HEAD, V20 and OperationalEvidencePolicy were verified in main.

## Coverage plan prepared before implementation

| Acceptance | Reuse | Addition / verification |
| --- | --- | --- |
| Durable media | Drift v3, namespaced cache/commands | Incremental v4 local_media, real v1/v2/v3 migrations and reopen tests |
| Private bytes | application-support path, UUID generator | Streaming file store, actual hash, atomic rename, orphan inspection and namespace/path tests |
| R2 protocol | #52 endpoints and authenticated ApiClient | Typed document client, separate unauthenticated streaming PUT, restart/lost-ACK HTTP tests |
| Retry / lifecycle | #46 ReplayWorker fenced lane and session | Media passes inside the same lane, durable retry, no reset of live UPLOADING |
| Dependencies | immutable command intent / command graph | Namespaced media edges and separately frozen HTTP request, all-required-files gating |
| Safe cleanup | Accepted grace-period rule | Finalized + all dependent ACK only, durable deletion marker, crash recovery |
| UI | SyncPanel accessible without assigned Trips | Media states/counts and real Sync now, no workflow/camera screens |
| Server authority | #53 validation and current auth | No invented #43/#45 endpoints, no capability/Backend/OpenAPI changes |

## Recoverable checkpoints

A: schema v4, private file store and migration/storage tests.
B: typed transport, actual streaming PUT, retry and finalization recovery.
C: immutable materialization and media dependencies in the existing replay lane.
D: sync visibility, documentation, independent review and four-job final-head CI.

Do not merge. This document records progress, not completion of future workflows.

### Checkpoint A

Drift v4 adds local_media, namespaced command_media_dependencies and immutable
materialized_requests. The original six tables/command trigger are retained;
v1/v2 migrate explicitly and v3 preserves existing rows. The file store writes
streams through an exclusive private temporary file, flushes/closes/renames, then
hashes actual bytes without buffering a whole image. UUID-only namespaces reject
paths and symlinks. Failed or interrupted captures remain inspectable, not erased.
The already locked crypto 3.0.7 is now a direct dependency; no version is upgraded.

32 storage/migration/cache/command tests pass. Drift generation and static analysis
are checked locally. Next: finish typed R2 transport, recovery and HTTP failure
tests (B), then integrate media passes/materialization in ReplayWorker (C).

### Checkpoints B/C

Typed initialization/finalization and a separate unauthenticated streaming R2 PUT
are implemented. Signed grants are ephemeral. Real HTTP tests verify actual bytes,
headers, lost init/final ACK, stable keys, restart, expired/missing uploads,
408/429/5xx, 401 refresh, authorization review, redirect refusal and late responses.
An old identity is superseded only after a definitive UPLOAD_NOT_COMPLETE result;
SQLite also guards uncertain and finalized identities. Init itself is not idempotent.

MediaProcessor is called inside ReplayWorker's existing lane. A shared private
filesystem fence additionally serializes separate database connections/workers;
the OS releases its lock after process death. Recovery never overtakes live HTTP.
Immutable command-media edges and materialized_requests preserve original intent,
then freeze server Document IDs before the first POST. Future #43/#45 commands
remain blocked in production: no EvidenceReplayPort is installed. Controlled test
adapters prove release/replay without adding a production endpoint.

112 media/sync regressions passed before final edge-case additions. The full suite
retained its prior tests; three new widget fixtures needed localhost HTTP and real
async SQLite stream initialization. All four media widget tests now pass individually;
no timeout was extended and no assertion/test was removed. Cleanup is conservative:
explicit offline-use release, finalized server identity, all dependent ACKs, seven-day
default grace and a durable REMOVING tombstone. Next: complete the crash/security
matrix, documentation and all four final-head CI jobs (D). No merge.

## Implemented schema and ownership

Schema v4 adds only local device tables; PostgreSQL V1–V20 and OpenAPI are unchanged.
local_media retains immutable UUID/User/Tenant/type/MIME/file-key/size/SHA/capture
identity, current Document ID, stable completion key, superseded-attempt diagnostic
IDs, attempt count, retry date, safe HTTP/error fields, capture/update/server-final
dates, offline-use pin and PRESENT/REMOVING/REMOVED file marker. Closed states:
LOCAL_ONLY, INIT_PENDING, UPLOAD_PENDING, UPLOADING, UPLOADED, FINALIZE_PENDING,
FINALIZED, FAILED. No tokens, signed URLs, credentials or file bytes enter SQLite.

command_media_dependencies has composite namespace FKs to the command and media,
unique field/medium bindings, and immutable edges inserted only before sending.
materialized_requests stores one immutable endpoint/method/body for the existing
clientOperationId. SQL rejects a different parent endpoint or later rewriting.
The original command payload/UUID/resources/dependency trigger remains intact.
Migration from empty v1 creates the four caches; v1/v2 add the existing command
tables; all v1/v2/v3 upgrades then add the three media tables and indexes. No table
or row is dropped. Installation directly in v4 uses the same schema/constraints.

## Capture and private storage

Future workflows call MediaStore.capture with the established SessionController,
typed document purpose/MIME and a readable byte stream. Capture checks current
identity, own Driver, cached type capability and downloaded Trip when supplied.
These checks constrain continuity only; server authorization remains authoritative.
JPEG/PNG are allowed for all nine types; PDF only for BOL/POD. Default maximum is
25 MiB, selectable through MediaStore's constructor to match server configuration,
never above 100 MiB. Server limits can be stricter and must be aligned per deployment.

PrivateMediaFileStore uses application-support/private-media/User/Tenant/UUID.bin.
It refuses arbitrary keys, unsafe namespace components and symlinks. An exclusive
UUID.bin.part is streamed, flushed, closed and renamed in its directory before
registration. SHA-256 is computed from actual streamed file bytes and checked again
before a PUT. A missing/changed file is a visible review failure, never a replacement
image under the same identity. Interrupted .part and orphan .bin files are retained
for explicit own-namespace inventory/inspection; they are not automatically uploaded,
classified or deleted. Registering a recovered complete file verifies its bytes.

This provides app sandbox isolation, not custom encryption. Android cloud backup is
disabled in the manifest. Device filesystem encryption, iOS file protection/backup
policy and real Android/iOS interruption behavior need native/device verification.
No keys, insecure encryption fallback, gallery copy or new camera permission is added.

## R2 phases, errors and recovery

1. POST uploads with type, exact MIME/length and actual SHA; no initialization replay
   promise or Idempotency-Key header. Persist returned PENDING Document ID first.
2. PUT its temporary signed URL using a separate HttpClient, exact Content-Type and
   Content-Length, streaming bytes. No Firebase headers. Reject credentials, unknown
   query fields, expired grants, non-R2 HTTPS hosts and redirects. Cleartext loopback
   is a test-only injectable option. Neither URLs nor provider error bodies are logged.
3. Persist UPLOADED/FINALIZE_PENDING, then POST completion with the same persisted
   Document ID, checksum and UUID. Only matching id/type/MIME/size/SHA, UPLOADED and
   server uploadedAt confirm FINALIZED. PUT success alone does not release commands.

Grants remain in memory. Restart/in-flight interruption with a known Document ID
first retries completion. A confirmed result preserves that identity and avoids
another PUT. Only definitive 409 UPLOAD_NOT_COMPLETE plus no valid grant permits
a new document attempt from unchanged local bytes. The previous ID/key remains
diagnostic, not a business attachment; the replacement receives a new completion
key. SQLite prevents rotating an uncertain/consumed key or any FINALIZED identity.
Lost initialization ACK can leave an unlinked server PENDING orphan: no listing/replay
API exists to recover its unknown ID. Local bytes remain and a later init may create
another PENDING document, but never duplicate a business completion.

Transport/timeout/408/5xx retain identities with durable exponential 2–300-second
base backoff and 0.5–1.5 jitter. 429 never retries before Retry-After. Backend 401
refreshes Firebase once for the same request; a second denial pauses context. Backend
403/404 and semantic 409 become visible review, not automatic authorization retry.
IDEMPOTENCY_KEY_REUSED never generates a replacement key. R2 signature/redirect
denial is separate from Firebase denial and retains bytes for review. A malformed
success is never confirmation; malformed authorization/state errors still fail closed.
Manual review refreshes current session and retries the same retained attempt.

Backend #52's temporary-to-private publication and real SHA verification provide
finalized-object immutability; the mobile client does not assume a PUT URL is one-use.
DocumentsClient.link is a typed future-owner port for existing TRIP/TRIP_STOP links.
Finalization does not itself create a link or accept Pre-Trip/POD/Fuel/damage evidence.

## One coordinator and immutable dependencies

MediaProcessor is an awaited phase of ReplayWorker, not another worker. The existing
per-database lane plus an app-private OS lock cover complete HTTP attempts, results
and reconciliation; an in-process canonical-directory lane also covers separately
opened SQLite connections. Foreground/reconnect/manual passes wait for live sends
to settle or genuinely time out before recovering SENDING/UPLOADING. Process death
releases the OS lock. Late callbacks cannot mutate the active User/Tenant's view or
acknowledge the previous namespace. A confirmed media ACK survives failed refresh.
Only ready online context replays; lifecycle/network listeners, timers and resources
are canceled or awaited on shutdown. No permanent background service is required.

Each pass processes at most ten media and then eligible commands. Durable retry dates
schedule the earlier of command/media eligibility. Pending media can block its chain
without blocking other resource chains. A new command's full required mapping is
compared with persisted edges, exact types and distinct FINALIZED server IDs before
materialization. Four Pre-Trip positions, both no-receiver files, one Fuel receipt or
the correct damage set must all be complete. Partial/cross-namespace graphs fail closed.

The logical payload contains immutable local media references. Resolution copies it
and stores server IDs transactionally before claim/POST. A crash before this commit
resolves current identities next time; a crash after it reuses identical body/path/key.
No media or request identity changes after the first send. Cleanup cannot remove
unresolved dependencies. EvidenceReplayPort is deliberately absent in production:
inspection/delivery/Fuel/exception drafts remain BLOCKED/FUTURE_WORKFLOW until #43/#45
own endpoints and validated response adapters exist. Test-only adapters use controlled
real HTTP, never simulated production endpoints or a generic executor.

## Cleanup and UI

Files are pinned for offline use by default. A future owner explicitly releases that
pin when no longer needed. Cleanup then requires FINALIZED server identity, all related
commands ACKNOWLEDGED, no upload pending and a configurable minimum-one-day grace
(default seven days). The transaction marks REMOVING before filesystem deletion;
new bindings are refused. Retry completes that tombstone if the file was already
removed, preserving FINALIZED metadata/IDs and never resubmitting. FAILED, unresolved
commands and all unfinalized states are retained. No remote R2 cleanup/retention is added.

The existing Road synchronization panel remains available without assigned Trips.
It shows namespaced counts, human document labels, local/uploading/finalizing/final
states, command references, safe errors and review. Sync now invokes the real shared
worker only in ready context; offline UI never reports business execution. Account/
Tenant/logout changes hide old rows and streams. No capture or full workflow screens.

## Security review and verification matrix

| Scenario | Tests |
| --- | --- |
| v1/v2/v3/fresh schema, original rows/immutability | existing app_database/session_cache/command_store tests + media_storage_test |
| Stream/private hash, partial/orphan/missing/changed bytes, traversal | media_storage_test + media_security_test |
| Real init/PUT/finalize, MIME/length/no Firebase, lost ACK/restart | media_protocol_test + media_security_test |
| 408/429/5xx, token refresh, denial, unsafe URL/redirect | media_protocol_test |
| Live upload, offline interruption, late identity, duplicate workers/connections | media_protocol_test + media_security_test |
| Complete sets, structural namespace FKs, partial graphs, immutable request/replay | media_dependencies_test + media_security_test |
| Finalized/ACK/pin/grace/tombstone cleanup | media_dependencies_test + media_security_test |
| Honest states, online/offline action, no Trips, logout | media_widgets_test; original sync/session widget suites |

The second review strengthened full-set validation, SQL endpoint/identity guards,
R2-vs-Firebase error separation and earliest retry scheduling. All prior #42/#44/#46
tests remain; no timeouts are extended, no real sleeps or disabled tests are used.
Native HTTP widget work runs only against controlled localhost peers; Drift stream
initialization/disposal runs outside Flutter's simulated clock. Final CI results and
exact counts are recorded on the PR's final HEAD, not inferred from an earlier run.

#43 Shift/inspections, #45 delivery/Fuel/exception execution, #47 geofences, Yard,
camera workflow screens, background services, remote cleanup and OQ-057 retention
remain deferred. First departure is still blocked by real #43 prerequisites.

### Checkpoint C security completion

The persisted full-set gate now rejects partial/corrupt bindings before sending.
SQL also enforces unique server identities, matching parent endpoint/method, and
valid immutable UUID/file/hash fields. Real file-database tests close/reopen after
lost ACK and serialize two independent SQLite connections during a live PUT.
Materialized retries assert identical raw HTTP JSON bytes, not merely equal maps.
Ephemeral grants are discarded on namespace change/disposal; a PUT grant must
match its persisted Document ID. The UI's Sync now is exercised against actual
localhost transport, with honest offline and no-assignment states.

The initial full local Backend run executed 1,882 tests with 0 failures and one
PostgreSQL connection error in an existing TripOperationalPostgresTests seed.
That full class was rerun unchanged: 124 passed, 0 failures/errors/skipped and
BUILD SUCCESS. No Backend production/test source or migration was changed for it.
Final CI must independently pass the complete Linux/PostgreSQL 18 clean verify.
Next checkpoint: locked Mobile/generated-code/format/analyze/full suite, Web and
OpenAPI gates, then publish one draft PR for the final four-job CI. No merge.

Local final validation checkpoint: locked dependencies, generated Drift, analyze
and all 256 Mobile tests pass (70 added over the merged 186). The widget fixtures
exercise the real Sync now action and confirmed metadata. OpenAPI validation and
9 tests pass. Two separate default-parallel Web runs hit unchanged one-second
waitFor deadlines under local load. The complete unmodified Web suite passed with
one worker: 244 tests, followed by a successful build. CI retains its original
parallel test command and all existing gates.
The final CI will provide definitive counts/results for all four components.

Final security regressions reject malformed completion acknowledgments (fractional
size, date-only uploadedAt or PENDING status), cross-Trip binding of a capture with
an explicit Trip context, and media dependencies injected into a normal physical
command. Those commands become visibly blocked rather than spinning a retry loop.
The existing Backend S3 signing adapter was exercised with its controlled HTTP
server; its actual SigV4 query fields match the mobile transport allowlist. No
Backend, Web, server OpenAPI, historical Flyway migration or capability changed.
