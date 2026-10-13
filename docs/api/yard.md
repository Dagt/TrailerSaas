# Yard Operations API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

Critical Yard mobile mutations require `Idempotency-Key`.

## Issue #48 implemented boundary

Yard context and Yard Move are implemented; the later correction/OOS UI and
Yard Check/reconciliation workflows remain #49–#51. Fresh YARD_MOVE_EXECUTE,
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

Response:

```json
{
  "id": "uuid",
  "status": "IN_PROGRESS",
  "startedAt": "...",
  "expectedCount": 34,
  "scannedCount": 0,
  "items": []
}
```

For mobile offline continuation, the complete expected snapshot must be retrievable/stored locally before connectivity is lost.

## 6. Get Yard Check

### GET /yard-checks/{yardCheckId}

Returns:

- status;
- Site/location;
- expected/scanned counts;
- grouped results;
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
  "expectedVersion": 5,
  "completedAt": "..."
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
Discrepancy UI/OOS evidence remains #49; Yard Check and its scoped reconciliation
remain #50/#51. The #40 commands are not exposed as an implicit Yard correction.
