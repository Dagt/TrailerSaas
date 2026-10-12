# Mobile Local Offline Data Model v0.1

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
