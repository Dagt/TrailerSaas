# Yard Operations API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

Critical Yard mobile mutations require `Idempotency-Key`.

## Issue #50 implemented Yard Check Backend

All eight method surfaces below are implemented with fresh YARD_CHECK_EXECUTE,
ACTIVE Tenant/membership and existing Tenant-wide Spotter scope. Six POSTs use
closed JSON, no query fields, UUID Idempotency-Key and the existing
MobileOperationLedger. Start returns 201; subsequent successful commands 200.
The original #40/#48/#49 APIs remain unchanged. No Flutter Check implementation
or second sync/physical engine is introduced; mobile Check/offline remains #51.

| Route under /yard-checks | Implemented contract |
| --- | --- |
| POST root | siteId, optional nullable locationId; one coherent INSERT SELECT MVCC snapshot of active AT_LOCATION inventory in active YARD/DOCK scope |
| GET root | Optional siteId/status/from/to; UUID cursor and limit 1–50 |
| GET /{yardCheckId} | Item UUID cursor, limit 1–200, expectedOnly and expectedVersion required with cursor; changed version rejects STALE_VERSION |
| POST /{yardCheckId}/scans | trailerId, active same-Site observedLocationId (exact selected area for Location scope), original nonfuture scannedAt >= server startedAt |
| POST /{yardCheckId}/completion | expectedVersion; server completedAt/actor; only remaining expected rows become MISSING |
| POST /{yardCheckId}/cancellation | expectedVersion/reason; server cancelledAt/actor; partial NULL results remain pending history |
| POST /{yardCheckId}/items/{itemId}/mark-location-unknown | expectedTrailerVersion/reason; COMPLETED/MISSING with current original expected location |
| POST /{yardCheckId}/items/{itemId}/location-correction | expectedTrailerVersion/newLocationId/reason; COMPLETED/LOCATION_MISMATCH with reviewed destination equal to original observed active Location |

Snapshot membership/original version/location/labels/state/last-known facts never
follow live inventory changes. OOS/load/coupling are inventory facts, not filters
for equipment availability. No overlap prohibition/reservation is invented.
Pending expected result is nullable per accepted hardening D. A first scan returns
MATCHED, UNEXPECTED or LOCATION_MISMATCH, retaining actor/time/UUID. Same-key replay
returns its original receipt. A new-key duplicate returns ALREADY_SCANNED without
replacing its observation or incrementing version; this is not a persisted result.
Scan/completion/cancellation never mutate Trailer or create movement/Alert.

Detail is {check,items,nextCursor,snapshotComplete}. Summary has identity/scope/
status/version, start/completion/cancellation actors/server times, expected,
scanned, matched, missing, unexpected, mismatch, remaining and total item counts.
Pages are bounded and version-fenced; consume every page and verify unique counts
before future offline use. snapshotComplete=true means the first page contained
the entire selected set; a final continuation page alone remains false. expectedOnly
retrieves complete original expected membership; reconciliationId is a separate
audit association rather than a rewritten finding.

Explicit reconciliation additionally requires fresh TRAILER_LOCATION_CORRECT,
exact current Trailer version, human confirmation/reason and current normalized
context. Both commands reuse existing physical rules/projection/event writer.
HUMAN_USER/USER_ACTION comes from the principal; no fabricated Driver/Trip/Tractor.
Mark unknown preserves last-known/seen/Driver, load, serviceability, coupling,
designation and Orders. A moved Trailer cannot be erased by an old missing item.
Event/Trailer/append-only reconciliation/ledger commit atomically. Confirmed
missing also persists exactly one TRAILER_MISSING/WARNING/OPEN Administration
Alert linked to Trailer plus yardCheckId/itemId/movementEventId/actorUserId.
This provisional OQ-059 policy is explicitly owner-authorized; no recipients,
push/email/worker/outbox/escalation/automatic resolution. No Alert exists merely
because scan/completion detected MISSING. Reconciliation returns its ID, Check/
item IDs, Trailer/event and nullable alertId. The historical snapshot remains intact.

Foreign resources are safe 404; current Tenant/membership/capability denial is
403 (authentication 401), malformed/time/closed DTO/query input 400. 409 includes
STALE_VERSION, INVALID_STATE_TRANSITION, RESOURCE_CONFLICT and
IDEMPOTENCY_KEY_REUSED. Late persistence failure rolls back every side effect.
Terminal same-UUID replay returns original receipt under fresh authority; new
scans/completion/cancellation reject. No silent conflict correction.

See [complete architecture/acceptance/security/QA and #51 handoff](../mobile/issue-50-yard-check-backend.md)
and the actual strict DTOs in OpenAPI. Examples below follow these implementation
rules; completion timestamps are server-owned, not supplied in its request.

## Issue #49 implemented corrective and damage boundary

Yard Home now supports explicit Location Correction, Load State Correction,
Discrepancy Report and Report Damage / Mark OOS. The original Yard Move remains
unchanged. Yard Check and Missing reconciliation Backend are implemented in #50;
mobile Check UI/offline remains #51 and notification delivery remains #55/#56.
No capability or role grant changes: YARD_SPOTTER is Tenant-scoped, has the two
correction capabilities, and has no OOS grant; a currently authorized multi-role
user may use OOS. Selecting Site, QR scan, a local draft or an upload grants no
mutation authority. Return to Service and unresolved OQ-054 remain separate.

See [#49 contracts, acceptance, tests and release limitations](../mobile/issue-49-yard-discrepancy-oos.md).

## Issue #48 implemented boundary

Yard context and Yard Move are implemented; correction/OOS are implemented in #49 below;
Yard Check/reconciliation Backend is implemented by #50; mobile remains #51. Fresh YARD_MOVE_EXECUTE,
ACTIVE membership and Tenant status ACTIVE precede all Yard reads and ledger
replay. The accepted YARD_SPOTTER scope is TENANT; there is no invented Site,
Driver or Tractor assignment, and selecting a Site does not grant permission.

GET /yard/sites accepts only UUID cursor and limit (1–50, default 50). It returns
{items,nextCursor}; each active normalized Site has id/name/timezone/version,
locations (up to 200) and nullable nextLocationCursor. Locations include
id/siteId/name/type/version and nullable Customer id/name. Active Site/Location
and live Customer association are required; Customer-context Locations also
require an ACTIVE Customer. Neutral locations are valid. No coordinates,
address-derived target, open Yard Check or foreign directory is returned.
GET /yard/sites/{siteId}/locations continues a selected Site with limit 1–200
(default 200). Every page freshly authorizes; cursor is a position, not authority.

The existing GET /api/v1/trailer-qr/{publicToken} remains the real QR resolver.
GET /yard/trailers/{trailerId} is a minimal freshly authorized active same-Tenant
read for review/ACK/conflict refresh, not a Trailer listing. It supplies
trailerId/operationalNumber/designatedCustomer, locationState/currentLocation,
lastKnownLocationId/lastSeenAt, loadState/serviceabilityState/couplingState,
active/version. Recorded location includes id/name/type/siteId/siteName and
live active/siteActive flags; unknown/in-transit has no stationary location.
It exposes no Orders, Driver/Tractor identities or mutation. A QR grants no access.

POST /yard-moves accepts exactly the five fields below, a UUID Idempotency-Key
and no query fields. All three IDs are real UUIDs; expectedTrailerVersion is a
nonnegative integer. observedAt must be valid RFC3339 with four-digit year, not
future and not earlier than lastSeenAt, following the existing Shift time policy.
Delayed offline time is retained at PostgreSQL microsecond precision.

The ledger winner locks the Trailer, validates active/IN_SERVICE/AT_LOCATION/
UNCOUPLED, checks real source and exact version, and locks live normalized from/to
Locations and their Site with the existing master-data parent protocol. Both
locations must be distinct and in the same active Site; BASE/YARD/DOCK/
CUSTOMER_AREA/SHOP/OTHER are real location types, never LOCATION_UNKNOWN.
Fresh authority is rechecked after lock waits. The existing
TrailerMovementProjection.yardMove / DatabaseTrailerMovementProjection /
MovementRules.yard appends YARD_MOVED and updates current/last-known location,
lastSeenAt, recordedAt and version in the same transaction as the ledger result.
Last Driver is preserved when no Driver is involved. User is HUMAN_USER and
source USER_ACTION; Trip/Driver/Tractor references remain NULL, never fabricated.
Load State, designated Customer and Orders are not changed.

200 returns {trailer,movementEvent}; event has id/eventType/occurredAt/recordedAt.
Same key and same logical body returns the saved historical result after fresh
authorization; different body/User/type returns IDEMPOTENCY_KEY_REUSED. The
client must refresh current state after ACK. A stationary compatible Trailer's
wrong origin returns 409 LOCATION_MISMATCH even if version also changed;
matching origin with stale version returns STALE_VERSION. Authorized minimal
currentTrailer may accompany these/state conflicts. Foreign/invisible resources
are safe 404 without projection; invalid state/context rejects with 409, malformed
request with 400, authorization with 401/403 and infrastructure with safe 5xx.
Failure rolls back event, projection/version and ledger. No silent correction.

See [architecture, acceptance and QA](../mobile/issue-48-yard-move.md).

## 1. Yard context

### GET /yard/sites

Returns Sites/Operational Locations available to the authenticated Yard/Spotter context.

May include:

- Site;
- Yard;
- Docks;
- Customer areas;
- current open Yard Check summary.

This endpoint is a scoped convenience projection, not a replacement for master-data Sites API.

## 2. Yard Move

### POST /yard-moves

Capability: `YARD_MOVE_EXECUTE`.

Request:

```json
{
  "trailerId": "uuid",
  "fromLocationId": "uuid",
  "toLocationId": "uuid",
  "observedAt": "2026-10-04T20:00:00Z",
  "expectedTrailerVersion": 12
}
```

Server validates:

- Trailer in same Tenant;
- user may act in Site/context;
- destination is valid Operational Location;
- Trailer state/serviceability permits requested movement;
- current recorded state has not become incompatible.

Successful result:

- append YARD_MOVED event;
- update Trailer current location projection;
- preserve actor/time.

If `fromLocationId` differs from authoritative location, normal Yard Move does not silently correct it.

Return:

`409 LOCATION_MISMATCH`

with current authoritative projection.

## 3. Correct Trailer location

### POST /trailers/{trailerId}/location-corrections

Capability: `TRAILER_LOCATION_CORRECT`.

Request:

```json
{
  "expectedVersion": 12,
  "newLocationId": "uuid",
  "reason": "Trailer physically found at Dock 17"
}
```

Result:

- append LOCATION_CORRECTED;
- update current location;
- preserve previous value;
- audit actor/reason.

## 4. Update Trailer load state

### POST /trailers/{trailerId}/load-state-changes

Capability: `TRAILER_STATE_CORRECT` or an explicit operational capability assigned to the actor.

Request:

```json
{
  "expectedVersion": 13,
  "loadState": "EMPTY",
  "reason": "Confirmed after unloading"
}
```

Allowed values:

- EMPTY
- LOADED
- UNKNOWN

Do not use this endpoint to represent LOADING/UNLOADING activities.

## 5. Start Yard Check

### POST /yard-checks

Capability: `YARD_CHECK_EXECUTE`.

Request:

```json
{
  "siteId": "uuid",
  "locationId": "uuid"
}
```

`locationId` may be null when the Check covers the approved Site/Yard scope as a whole.

Server atomically:

1. validates scope;
2. creates Yard Check IN_PROGRESS;
3. snapshots expected Trailers/current expected locations;
4. returns expected item summary.

Response: `201` with `{check,items,nextCursor,snapshotComplete}`. `check` contains
identity/scope/status/version, authenticated actor/server timestamps and all counts.
`items` contains at most 200 real snapshot rows. A larger inventory has nextCursor
and snapshotComplete=false; all remaining pages must be retrieved. See the closed
YardCheckDetail schema in OpenAPI for the complete response shape.

For mobile offline continuation, the complete expected snapshot must be retrievable/stored locally before connectivity is lost.

## 6. Get Yard Check

### GET /yard-checks/{yardCheckId}

Returns:

- status;
- Site/location;
- expected/scanned counts;
- classification counts and individual results;
- item snapshot;
- version.

### GET /yard-checks

Filters:

- siteId;
- status;
- from/to.

## 7. Scan during Yard Check

### POST /yard-checks/{yardCheckId}/scans

Capability: `YARD_CHECK_EXECUTE`.

Idempotency required.

Request:

```json
{
  "trailerId": "uuid",
  "observedLocationId": "uuid",
  "scannedAt": "..."
}
```

Server returns result:

- MATCHED
- UNEXPECTED
- LOCATION_MISMATCH
- ALREADY_SCANNED

Duplicate scan must not create duplicate Yard Check items.

Scanning does not silently mutate authoritative Trailer location.

## 8. Complete Yard Check

### POST /yard-checks/{yardCheckId}/completion

Capability: `YARD_CHECK_EXECUTE`.

Request:

```json
{
  "expectedVersion": 5
}
```

Server:

1. verifies Yard Check still IN_PROGRESS;
2. marks unobserved expected Trailers as MISSING;
3. freezes result snapshot;
4. sets COMPLETED.

No reconciliation correction is automatically applied solely by completion.

## 9. Cancel Yard Check

### POST /yard-checks/{yardCheckId}/cancellation

Request:

```json
{
  "expectedVersion": 4,
  "reason": "Shift interrupted"
}
```

Partial scan data may remain for audit/diagnostics but is not treated as completed inventory.

## 10. Reconcile missing Trailer

### POST /yard-checks/{yardCheckId}/items/{itemId}/mark-location-unknown

Capabilities:

- `YARD_CHECK_EXECUTE`;
- `TRAILER_LOCATION_CORRECT` or explicit reconciliation capability.

Server validates item result = MISSING.

Atomic side effects:

- preserve last known location/seen/Driver;
- Trailer -> LOCATION_UNKNOWN;
- append Trailer movement event;
- create Administration alert;
- record Yard reconciliation context.

## 11. Reconcile location mismatch

### POST /yard-checks/{yardCheckId}/items/{itemId}/location-correction

Request:

```json
{
  "expectedTrailerVersion": 14,
  "newLocationId": "uuid",
  "reason": "Confirmed during Yard Check"
}
```

The correction is explicit and auditable.

## 12. Yard mobile sync caveat

Starting a new Yard Check while offline is allowed only if the server-generated expected snapshot was already obtained and a valid Check ID exists.

The mobile app must not invent expected inventory from stale cached Trailer lists.

## Issue #40 implementation boundary

The generic Trailer location-corrections and load-state-changes backend commands
are implemented, using the #39 transactional engine. Correction accepts only
newLocationId (not observedLocationId); it represents a confirmed physical
observation. Both require reason/expectedVersion, fresh capabilities, server
actor/time and durable history. No generic physical-state PATCH or offline replay
wrapper is added here. Mark unknown preserves the last physical observation.

Yard Move and its mobile UI are implemented by #48 as documented above.
Discrepancy UI/OOS evidence is implemented by #49; Yard Check and its scoped reconciliation
are implemented by #50; mobile remains #51. The #40 commands are not exposed as an implicit Yard correction.

### #49 implemented HTTP contracts

All four POSTs require a UUID Idempotency-Key, closed JSON, no query fields,
fresh YARD_MOVE_EXECUTE, ACTIVE membership/Tenant and same-Tenant active Trailer
and selected active Site. Mutations additionally require their exact capability.
Common physical fields: trailerId, siteId, expectedTrailerVersion (integer >=0),
observedAt (nonfuture RFC3339, four-digit year), and reason (trimmed nonblank <=4000).

| POST route | Additional fields / change | Capability / event |
| --- | --- | --- |
| /yard/location-corrections | newLocationId, different active same-Site normalized destination, no lastSeenAt regression | TRAILER_LOCATION_CORRECT / LOCATION_CORRECTED |
| /yard/load-state-changes | loadState EMPTY/LOADED/UNKNOWN, different from prior value | TRAILER_STATE_CORRECT / LOAD_STATE_CHANGED |
| /yard/out-of-service | comment replaces reason; documentIds 1–50 distinct finalized own exact-Trailer DAMAGE_PHOTO IDs | TRAILER_OOS_MARK / MARKED_OOS |
| /yard/discrepancy-reports | No expectedTrailerVersion; recordedLocationId nullable but present, reportedLocationId, observedTrailerVersion | YARD_MOVE_EXECUTE / no physical event |

Report fields also include trailerId/siteId/observedAt/reason. Historical versions
may be older, never future. Report preserves historical recorded observation,
does not update Trailer/version/location/load/serviceability and needs no correction
grant. POST returns {trailer,report}; report includes id, actorUserId, observedAt,
recordedAt and clientOperationId. GET /yard/discrepancy-reports is separately
authorized with TRAILER_VIEW_ALL, bounded limit 1–50 and UUID chronological cursor;
no update/delete or notification worker is provided.

Physical commands return {trailer,movementEvent}; OOS also returns documentIds.
Events contain id/eventType/occurredAt/recordedAt. Provenance is HUMAN_USER /
USER_ACTION, authenticated actor and original UUID; no invented Trip/Driver/Tractor.
Trailer row then live Site/Locations are locked before authority is rechecked.
Version conflicts are STALE_VERSION; invalid/context conflicts RESOURCE_CONFLICT;
evidence failures include EVIDENCE_REQUIRED/UPLOAD_NOT_COMPLETE/RESOURCE_NOT_FOUND.
Foreign/invisible resources are safe 404; there is no silent location repair.
Location correction is an explicit physical observation. Load State correction
preserves location (including unknown/in-transit), lastSeenAt, coupling, serviceability,
Orders and designation. OOS changes serviceability only and never closes a Trip or
performs Drop. Original #40 administrative routes/body/server-time behavior remain
compatible and share the same physical projection/event writer.

V23 adds append-only yard_discrepancy_reports and trailer_oos_evidence with Tenant
composite FKs, runtime RLS, SELECT/INSERT-only runtime grants and immutable history.
The OOS association guard requires the actual same-transaction MARKED_OOS event,
same Trailer/actor/UUID and one uploaded own exclusive Trailer link per document.
Each document may be consumed by one Yard OOS event. State, event, accepted evidence
and ledger receipt commit together; complete rollback includes version and event.
R2 transport precedes PostgreSQL and is not a distributed transaction.
