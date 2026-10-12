# Mobile Offline Sync Protocol v0.1

**Status:** Accepted  
**Date:** 2026-10-04  
**Applies to:** Road Driver Mobile, Yard / Spotter Mobile  
**Related:** API v0.1, schema v1.0, Driver/Yard UX flows

## Goal

Allow critical mobile workflows to continue through temporary loss of connectivity without:

- duplicating operational actions;
- silently overwriting newer server state;
- losing photos/evidence;
- bypassing authorization;
- requiring continuous background execution.

The server remains authoritative. Offline mode records local intent/facts and replays them safely when connectivity returns.

# 1. Core model

The mobile client maintains two persistent queues:

1. **Command queue** — structured operational actions.
2. **Media queue** — local photos/files waiting for R2 upload/finalization.

Examples of queued commands:

- On Duty;
- Pre-Trip;
- Hook;
- Drop;
- Depart;
- Arrive;
- Start Service;
- Complete Stop/Delivery;
- Fuel Event;
- Yard Move;
- Yard Check Scan;
- Post-Trip;
- Off Duty.

Every queued command has an immutable `clientOperationId` UUID used as the HTTP `Idempotency-Key`.

# 2. Local command record

Suggested local SQLite/Drift structure:

```text
local_commands
--------------
id                    uuid/local PK
client_operation_id   uuid UNIQUE NOT NULL
tenant_id             uuid NOT NULL
user_id               uuid NOT NULL
command_type          varchar NOT NULL
endpoint_path         varchar NOT NULL
http_method           varchar NOT NULL
payload_json          text/json NOT NULL
occurred_at           timestamp NOT NULL
created_at            timestamp NOT NULL
depends_on_operation  uuid NULL
aggregate_type        varchar NULL
aggregate_id          uuid NULL
expected_version      bigint NULL
state                 varchar NOT NULL
attempt_count         integer NOT NULL
next_retry_at         timestamp NULL
last_http_status      integer NULL
last_error_code       varchar NULL
last_error_detail     text NULL
acked_at              timestamp NULL
```

Local states:

- `QUEUED`
- `SENDING`
- `ACKNOWLEDGED`
- `RETRYABLE_FAILURE`
- `CONFLICT`
- `BLOCKED`
- `PERMANENT_FAILURE`

The queue survives app restarts.

# 3. Dependency ordering

Commands are not replayed blindly in timestamp order.

Use explicit dependency edges when one action requires another.

Example:

```text
On Duty
   ↓
Pre-Trip
   ↓
Hook
   ↓
Depart
   ↓
Arrive
   ↓
Service
   ↓
Delivery completion
   ↓
Depart next Stop
```

If command B depends on command A, B cannot be sent until A is ACKNOWLEDGED.

If A enters CONFLICT/PERMANENT_FAILURE, dependent commands become BLOCKED until the user/operations resolves the chain.

Independent command chains may sync independently.

# 4. Idempotency

For retry-sensitive commands:

```http
Idempotency-Key: <clientOperationId>
```

Server rules:

- first valid request processes normally;
- same key + same logical request returns the previous logical result;
- same key + materially different request returns `409 IDEMPOTENCY_KEY_REUSED`.

The client never generates a new idempotency key merely because a network retry occurred.

# 5. Retry policy

## Retry automatically

Automatic retry is appropriate for:

- no network;
- connection timeout;
- DNS/connectivity failure;
- HTTP 408;
- HTTP 429, respecting Retry-After when supplied;
- HTTP 5xx.

Use exponential backoff with jitter.

Do not retry faster than the platform can reasonably sustain.

## Authentication retry

For HTTP 401:

1. refresh/reacquire Firebase token;
2. retry once with the same Idempotency-Key;
3. if authentication cannot be restored, pause sync and require sign-in/connectivity.

## Do not blindly retry

These require review or permanent failure:

- 400 VALIDATION_FAILED;
- 403 capability/membership denied;
- 404 protected resource unavailable;
- 409 INVALID_STATE_TRANSITION;
- 409 STALE_VERSION;
- 409 RESOURCE_CONFLICT;
- 409 TRAILER_SUBSTITUTION_NOT_AUTHORIZED;
- 409 LOCATION_MISMATCH.

# 6. Semantic conflicts vs optimistic version

Offline physical commands should rely primarily on **semantic preconditions**, not only a global aggregate version.

Example:

A Driver captured Hook T114 offline. While offline, an unrelated Trailer metadata field changed on the server.

Rejecting only because Trailer `version` changed would be unnecessarily strict.

Therefore the server should validate the actual command invariants:

- Trailer still belongs to Tenant;
- Trailer still available/not actively coupled;
- Tractor still free;
- Trip still executable;
- requested Trailer still assigned/authorized;
- serviceability still permits use.

For administrative edits/corrections, exact `expectedVersion` remains appropriate.

**API v0.1 note:** any mobile command currently carrying `expectedVersion` should be reviewed during OpenAPI finalization to determine whether it is a strict concurrency requirement or only an optional stale-state hint.

# 7. Offline occurrence time

Each command carries:

- `occurredAt` — device-captured operational time;
- server records `recordedAt` — receipt/processing time.

The system preserves both when useful.

Rules:

- server does not blindly trust device time;
- clearly future/impossible timestamps can be rejected or flagged;
- delayed historical occurrence is expected during offline use;
- exact clock-skew tolerance is an implementation/security setting, not a business rule.

# 8. Local authorization snapshot

The app may continue an already-established offline session using its cached authorized context, but this is only a UX continuity mechanism.

It does **not** guarantee server acceptance.

On replay, every command is re-authorized against current server state.

Consequences:

- if a User was disabled while offline, queued commands may be rejected;
- if a Trip was reassigned, stale Driver commands may conflict;
- if permissions changed, cached UI does not override server policy.

Do not allow first-time login/onboarding entirely offline.

# 9. Offline Driver workflow

A previously authenticated Driver may continue already downloaded/assigned work offline.

The app caches enough for the active work:

- current Driver/Shift;
- active/assigned Trip summary;
- Stops;
- operational Site snapshots/instructions;
- assigned/planned Trailer data;
- locally captured evidence;
- pending commands.

The app must not imply that server state has already changed.

Use explicit UI status such as:

```text
Saved on this device — waiting to sync
```

# 10. Wrong Trailer while offline

If the Driver scans a Trailer different from the authorized/expected Trailer and no prior authorization is already present locally:

- block normal Hook continuation;
- record/display the mismatch locally if useful;
- require connectivity/Dispatch authorization before accepting the alternate Trailer.

Offline mode must not become a bypass for substitution authorization.

# 11. Reroute/cancellation while Driver is offline

A Driver may be unaware of a server-side reroute while disconnected.

On reconnect:

1. sync queued commands in dependency order;
2. fetch current server work/updates;
3. if a queued action conflicts with newer instructions, mark CONFLICT;
4. preserve the local action and show the new authoritative instruction.

Do not silently discard the Driver's recorded action.

# 12. Media queue

Implementation note: #54 realizes this model in Drift v4 with a private streaming
file store and media phases inside the existing #46 ReplayWorker. Finalization is
confirmed from #52, while #53 and the future owning workflows retain evidence
authority. See [implemented states, retry, fencing, materialization and cleanup](issue-54-media-queue.md).
Future #43/#45 command contracts are prepared but are not replayed in production
before their endpoints exist. A locally uploaded file is never business completion.

Suggested local structure:

```text
local_media
-----------
id                  uuid/local PK
local_file_uri      text NOT NULL
document_type       varchar NOT NULL
content_type        varchar NOT NULL
size_bytes          bigint NULL
checksum_sha256     varchar NULL
related_command_id  uuid NULL
server_document_id  uuid NULL
state               varchar NOT NULL
attempt_count       integer NOT NULL
last_error          text NULL
created_at          timestamp NOT NULL
finalized_at        timestamp NULL
```

States:

- `LOCAL_ONLY`
- `INIT_PENDING`
- `UPLOAD_PENDING`
- `UPLOADING`
- `UPLOADED`
- `FINALIZE_PENDING`
- `FINALIZED`
- `FAILED`

# 13. Media dependency workflow

Example: signed BOL captured offline.

```text
Capture BOL locally
      ↓
LOCAL_ONLY
      ↓ connectivity
POST /documents/uploads
      ↓
PUT signed R2 URL
      ↓
POST /documents/{id}/upload-completion
      ↓
Document FINALIZED
      ↓
send queued delivery-completion command
```

The delivery-completion command is blocked until required media dependencies are FINALIZED.

The Driver may continue the local workflow offline if the app has captured all required local evidence, but the UI must show that server synchronization is pending.

# 14. Pre-Trip media behavior

Offline Pre-Trip is valid locally when the app has captured:

- odometer;
- checklist;
- front photo;
- rear photo;
- driver-side photo;
- passenger-side photo.

On reconnect:

1. upload/finalize four images;
2. send Pre-Trip command referencing server Document IDs;
3. only then mark server synchronization complete.

# 15. No-receiver POD behavior

Offline local completion requires locally captured:

- BOL photo;
- delivery photo;
- nonblank comment;
- `NO_RECEIVER_AVAILABLE` selection.

On sync, both images finalize before the Delivery Completion command is sent.

# 16. Local file security

BOL/POD/inspection photos can contain commercially sensitive information.

Proposed requirements:

- store pending files in app-private storage;
- do not place them in public photo gallery by default;
- protect encryption keys using OS secure storage where practical;
- remove local synchronized evidence after a short safe grace period unless offline access requires retention;
- logout/account switch must not expose another User's queued files.

Exact encryption-at-rest implementation will be finalized in mobile security implementation.

# 17. Background sync expectations

Do **not** rely on guaranteed background execution.

iOS/Android may suspend the app.

Therefore sync runs opportunistically:

- when app enters foreground;
- when connectivity returns while app is active;
- after a critical action if online;
- via best-effort OS background task where supported.

The product must remain correct even if background sync never runs until the app is reopened.

# 18. Sync cycle

Recommended client cycle:

```text
1. confirm authenticated session
2. upload/finalize ready media
3. select QUEUED/RETRYABLE commands whose dependencies are satisfied
4. send serially within dependent command chain
5. mark ACKNOWLEDGED or failure state
6. continue independent chains
7. refresh authoritative active context
8. fetch Driver updates / active Trip / Yard Check state
9. surface conflicts requiring user attention
```

Do not run dozens of mutations concurrently against the same Trip/Trailer.

# 19. Server refresh after replay

MVP does not require a generic database change-data-capture sync feed.

After replay, clients refresh targeted authoritative resources.

Road Driver:

- `GET /driver/me`
- `GET /driver/me/trips`
- active `GET /trips/{id}`
- `GET /driver/me/updates`

Yard:

- `GET /yard/sites`
- active `GET /yard-checks/{id}`
- Trailer details for conflicted/scanned assets.

This keeps MVP sync understandable and avoids introducing a generic synchronization platform prematurely.

A delta-feed endpoint can be added later if measured performance/scale requires it.

# 20. Yard Check offline protocol

A Yard Check must be created by the server before it can become a trustworthy offline reconciliation session.

Before going offline, the app stores:

- Yard Check ID;
- status/version;
- Site/location context;
- full expected Trailer snapshot;
- current scan results.

While offline:

- scans are stored locally;
- duplicate Trailer scans are locally idempotent;
- scan commands queue with clientOperationId.

On reconnect:

- replay scans;
- handle authoritative conflicts;
- completion is sent only after all prior scan commands are ACKNOWLEDGED.

If the expected snapshot was never downloaded, the app must not invent/start an offline Yard Check from stale inventory.

# 21. Yard Move conflicts

A Yard Move is state-sensitive.

If the Trailer was moved elsewhere while Spotter was offline and server returns LOCATION_MISMATCH/STALE state:

- mark command CONFLICT;
- do not auto-correct;
- fetch current Trailer;
- present recorded vs observed location;
- require an authorized correction or new move command.

# 22. Off Duty offline

The app may queue Off Duty offline only when local state indicates:

- required Post-Trip completed locally;
- no known active unresolved Trip;
- no open Yard Check in local context.

Server revalidates everything on sync.

If server rejects Off Duty, Shift remains open server-side and client shows the conflict.

# 23. Conflict UX categories

Use understandable categories rather than raw HTTP errors:

### Server state changed
Example: Trip reassigned.

### Authorization changed
Example: account/role disabled.

### Physical state conflict
Example: Trailer already coupled elsewhere.

### Evidence incomplete
Example: photo failed to upload.

### Invalid sequence
Example: Depart command blocked because Arrival/Service chain did not sync.

User should have actions such as:

- Retry;
- Refresh;
- Review;
- Contact Dispatch;
- Discard local intent only when safe and confirmed.

Never auto-delete a conflicting command without user-visible trace.

# 24. Queue cleanup

ACKNOWLEDGED commands can be removed from the active queue after a safe local grace period.

Keep lightweight local sync history long enough for troubleshooting.

Do not keep sensitive payload/media indefinitely.

# 25. Telemetry

Record sync diagnostics without logging sensitive evidence content.

Useful metrics:

- pending command count;
- oldest pending age;
- retries;
- conflict rate;
- media upload failure rate;
- time-to-sync after connectivity restoration.

Avoid logging BOL image contents or auth tokens.

# 26. Acceptance criteria

Protocol can be Accepted when:

1. every required offline mobile action has deterministic replay behavior;
2. duplicate retry cannot create duplicate business actions;
3. command dependencies are explicit;
4. media dependencies are explicit;
5. authorization is rechecked server-side;
6. stale/conflicting commands are never silently discarded;
7. wrong-Trailer authorization cannot be bypassed offline;
8. Yard Check snapshot semantics survive offline operation;
9. app correctness does not depend on guaranteed background execution;
10. targeted server refresh is sufficient for MVP without generic CDC sync.
