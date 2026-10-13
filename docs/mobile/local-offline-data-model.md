# Mobile Local Offline Data Model v0.1

Issue #48 evolves the deployed schema to Drift v7 with incremental v1–v6 upgrades.
cached_yard_contexts stores only authorized Site/Location projections, selected
Site, refresh time and denial marker under User/Tenant. Existing cached_trailers
retains relevant scanned Yard projections, with no raw GPS. Explicit denial closes
cached Yard continuity; logout clears read caches, never unresolved commands.
local_commands permits aggregate_type TRAILER with a real aggregate_id and NULL
trip_id. command_results permits TRAILER and retains its verified immutable
projection_json. Existing materialized_requests freezes the eventual HTTP body.
The pending version reference is part of immutable logical intent/dependencies;
it resolves only from a same-namespace same-Trailer ACK. No future version, second
queue or table of positions. Real v6 file migration tests preserve commands,
results/frozen bodies, foreign keys and immutability guards. Older tables/edges
and all Road/Shift/media/update data remain intact.

At its baseline, issue #47 kept Drift v6 and all existing tables. The cached own Trip execution
context may contain `arrivalAssistance` for the next authorized Site only, including
Trip/Stop/Site versions and explicit geography. Historical snapshots are never
evaluated. Raw GPS readings/suggestions are in-memory only. Existing immutable
Arrival payloads accept MANUAL or GEOFENCE_CONFIRMED; the latter contains expressly
confirmed coordinates/accuracy with its original occurredAt/UUID. No GPS table,
position history, new synchronization system or migration is introduced.

**Status:** Accepted  
**Storage:** SQLite via Drift  
**Related:** offline-sync-protocol.md

This is a local-device persistence design, not a copy of the full server database.

## Principles

The mobile database stores only what is necessary for:

- current authorized work;
- offline execution;
- evidence upload;
- synchronization/reconciliation.

Do not mirror every server table locally.

## Tables / stores

### local_session_context

Cached non-authoritative context:

- internal user ID;
- active Tenant ID;
- Driver ID when applicable;
- cached roles/capabilities summary;
- last successful auth/sync timestamp.

### cached_trips

Minimal Trip projection:

- Trip ID;
- server version;
- state;
- Driver/Tractor;
- planned/current Trailer summary;
- last refreshed timestamp.

### cached_stops

- Stop ID;
- Trip ID;
- sequence;
- state;
- Site/location snapshot;
- appointment;
- service type;
- instructions;
- linked Order references needed by Driver.

### cached_trailers

Only relevant/scanned/assigned assets:

- Trailer ID;
- operational number;
- designated Customer summary;
- location/load/serviceability;
- current server version;
- last refreshed timestamp.

### cached_yard_checks

- Yard Check ID;
- server version;
- Site/location;
- state;
- expected counts;
- snapshot downloaded flag.

### cached_yard_check_items

- Yard Check ID;
- Trailer ID;
- expected location;
- observed location;
- local result;
- server result if synced;
- scan timestamp.

### local_commands

Defined by offline sync protocol.

### local_media

Defined by offline sync protocol.

Implemented by #54 in schema v4 with immutable file/SHA/User/Tenant identity,
server Document/completion attempt identity, durable retry/review fields and
offline-use/deletion markers. Two narrow local tables, command_media_dependencies
and materialized_requests, preserve bindings and the frozen HTTP body separately
from the unchanged #46 logical command intent. Composite namespace FKs prohibit
cross-account/Tenant references. See [schema, migrations and recovery](issue-54-media-queue.md).

### local_sync_issues

User-visible reconciliation record:

- issue ID;
- command ID;
- category;
- server error code;
- related resource;
- message;
- created timestamp;
- resolved timestamp.

## Data ownership

All local rows are namespaced by active Tenant/User where relevant.

Logging out or switching accounts must not make one User's operational cache visible to another.

## Refresh strategy

Server data replaces cached projections when authoritative refresh succeeds.

Pending local commands are not overwritten merely because a server refresh occurred.

The UI may need to show:

- authoritative server state;
- pending local action;
- conflict between both.

## Local IDs

Use UUIDs for local commands/media.

Do not use autoincrement IDs as idempotency keys.

## Cleanup

After successful sync and a safe grace period:

- ACKNOWLEDGED commands may be pruned;
- finalized uploaded media may be removed locally;
- completed old Trips/Yard Checks may be evicted from cache.

Do not clean unresolved conflicts automatically.

## Issue #43 schema v5 (incremental v1/v2/v3/v4 upgrades)

local_commands adds typed aggregate_type TRIP/SHIFT/INSPECTION; Trip/aggregate IDs
are nullable for commands genuinely lacking them (no sentinel UUID). Existing
physical intents remain non-null and immutable. command_results stores verified
historical Shift/inspection IDs; command_result_dependencies binds an inspection or
Off Duty to a prior same-namespace On Duty ACK. inspection_drafts persists inputs,
photo positions and immutable Shift reference; camera_captures persists context
before native camera launch. Existing cache/commands/issues/media/frozen requests
are preserved. Foreign keys and insert-before-send/immutability triggers protect
namespace and generated-ID dependencies. No token or signed URL is stored here.
Only materialized_requests contains the final frozen HTTP path/body. Typed Off Duty
templates resolve only with the acknowledged real Shift ID; intent is unchanged.

## Issue #45 schema v6

Incremental v1/v2/v3/v4/v5 upgrades add road_drafts, road_captures,
cached_driver_updates and driver_update_cursors, preserving the complete existing
cache/command/media/result graph and frozen requests. Draft context is immutable;
queued drafts cannot change. Camera context is recorded before native launch;
namespace FKs and global camera claims protect cross-account references and live
captures. Confirmed updates are namespaced, immutable server identities with stable
sequence from createdAt/id pagination, not an authorization cache. Update history
survives removal of current work. Instructions and cursors additionally bind to the
current own Driver ID; a durable denial marker hides cached instructions after
explicit 401/403 until authorized refresh. Fuel drafts freeze the captured Tractor
and reject reassignment before queuing. No token, signed URL or binary enters these tables.

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
