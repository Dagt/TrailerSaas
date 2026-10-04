# Yard Operations API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

Critical Yard mobile mutations require `Idempotency-Key`.

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
  "observedLocationId": "uuid",
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
