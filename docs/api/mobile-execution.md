# Driver Mobile Execution API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

All offline-capable mutation requests require `Idempotency-Key: <uuid>`.

The authenticated User must resolve to an active Driver for Driver-only commands.

## 1. Driver home/work

### GET /driver/me

Returns:

- Driver profile;
- open Shift;
- active Trip if any;
- assigned work summary;
- current Tractor context;
- sync-independent server state.

### GET /driver/me/trips

Returns authorized assigned Trips.

Filters may include:

- active;
- date.

Driver cannot use this endpoint to browse unrelated Trips.

## 2. Shift

### POST /driver/me/shifts/on-duty

Capability: `SHIFT_START`.

Request:

```json
{
  "occurredAt": "2026-10-04T14:00:00Z"
}
```

Server validates no other open Shift.

Response: current Shift.

### POST /driver/me/shifts/{shiftId}/off-duty

Capability: `SHIFT_END`.

Request:

```json
{
  "occurredAt": "...",
  "expectedShiftState": "ON_DUTY"
}
```

Server blocks normal closure if unresolved active work/end-of-day requirements exist, unless a separately authorized override is implemented.

## 3. Inspections

### POST /driver/me/inspections/pre-trip

Capability: `PRETRIP_CREATE`.

Request:

```json
{
  "shiftId": "uuid",
  "tripId": "uuid",
  "tractorId": "uuid",
  "odometer": 123456,
  "checklist": {
    "items": []
  },
  "documentIds": {
    "front": "uuid",
    "rear": "uuid",
    "driverSide": "uuid",
    "passengerSide": "uuid"
  },
  "occurredAt": "..."
}
```

Required photos must already be finalized Documents or be represented through the approved upload-finalization workflow before the inspection is considered fully synchronized.

### POST /driver/me/inspections/post-trip

Capability: `POSTTRIP_CREATE`.

Request:

```json
{
  "shiftId": "uuid",
  "tripId": "uuid",
  "tractorId": "uuid",
  "odometer": 123900,
  "damages": "...",
  "failures": "...",
  "comments": "...",
  "occurredAt": "..."
}
```

## 4. Hook Trailer

### POST /trips/{tripId}/hooks

Capabilities:

- `TRIP_EXECUTE_ASSIGNED`
- `TRAILER_HOOK`

Request:

```json
{
  "trailerId": "uuid",
  "tractorId": "uuid",
  "driverId": "uuid",
  "occurredAt": "...",
  "expectedTrailerVersion": 10
}
```

Server validates:

- Driver owns/has access to active Trip;
- Driver/Tractor assignment;
- Trailer exists in Tenant;
- Trailer is not already actively coupled;
- Tractor is not already coupled;
- Trailer is expected or substitution was authorized;
- serviceability permits use.

Response includes active coupling + updated Trailer/Trip projection.

Wrong Trailer without authorization:

`409 TRAILER_SUBSTITUTION_NOT_AUTHORIZED`.

## 5. Drop Trailer

### POST /trips/{tripId}/drops

Request:

```json
{
  "trailerId": "uuid",
  "stopId": "uuid",
  "locationId": "uuid",
  "occurredAt": "..."
}
```

Server closes active coupling, records location and appends DROPPED event atomically.

## 6. Depart

### POST /trips/{tripId}/departures

Request:

```json
{
  "fromStopId": "uuid",
  "nextStopId": "uuid",
  "occurredAt": "..."
}
```

For first departure, server verifies accepted Trip start prerequisites.

Side effects may include:

- Trip -> IN_TRANSIT;
- next Stop -> EN_ROUTE;
- coupled Trailer -> IN_TRANSIT;
- departure event.

Departure is manual.

## 7. Arrival

### POST /stops/{stopId}/arrivals

Capabilities:

- `TRIP_EXECUTE_ASSIGNED`
- `ARRIVAL_CONFIRM`

Request:

```json
{
  "occurredAt": "...",
  "source": "MANUAL",
  "coordinates": {
    "latitude": 41.0,
    "longitude": -88.0,
    "accuracyM": 25
  }
}
```

Source:

- MANUAL
- GEOFENCE_CONFIRMED

Coordinates are optional evidence, not continuous route history.

Side effects:

- Stop -> ARRIVED;
- Trip -> AT_STOP;
- Trailer/current location projection updates where domain rules support it.

## 8. Start service

### POST /stops/{stopId}/service-starts

Request:

```json
{
  "occurredAt": "..."
}
```

Transitions ARRIVED -> IN_SERVICE.

## 9. Drop & Hook sequence

Use the normal Drop endpoint followed by Hook endpoint.

Do not create a hidden combined database shortcut that bypasses coupling invariants.

The mobile UI may present Drop & Hook as one guided workflow while the API records explicit physical facts.

## 10. Delivery completion / POD

### POST /stops/{stopId}/delivery-completions

Capability: `POD_UPLOAD`.

### Signed BOL

```json
{
  "evidenceMode": "SIGNED_BOL",
  "bolDocumentId": "uuid",
  "occurredAt": "..."
}
```

### No receiver

```json
{
  "evidenceMode": "NO_RECEIVER_AVAILABLE",
  "bolDocumentId": "uuid",
  "deliveryPhotoDocumentId": "uuid",
  "comment": "No receiving staff available",
  "occurredAt": "..."
}
```

Server validates uploads are complete.

Atomic result:

- create delivery evidence;
- Stop -> COMPLETED;
- append Stop event;
- update Order fulfillment;
- complete Trip if state-machine conditions are now satisfied.

## 11. Non-delivery Stop completion

### POST /stops/{stopId}/completions

For operational Stop types that do not use POD-specific completion.

Request:

```json
{
  "occurredAt": "...",
  "notes": "..."
}
```

Server validates service-mode requirements.

## 12. Depart Stop

### POST /stops/{stopId}/departures

Request:

```json
{
  "occurredAt": "...",
  "nextStopId": "uuid"
}
```

Transitions to next travel state if work remains.

## 13. Fuel Event

### POST /driver/me/fuel-events

Capability: `FUEL_EVENT_CREATE`.

Request:

```json
{
  "tripId": "uuid",
  "tractorId": "uuid",
  "odometer": 123700,
  "receiptDocumentId": "uuid",
  "occurredAt": "..."
}
```

No gallons/cost calculation in MVP.

## 14. Report operational exception

### POST /trips/{tripId}/exceptions

Capability: `EXCEPTION_REPORT`.

Request:

```json
{
  "stopId": "uuid",
  "category": "EQUIPMENT_DAMAGE",
  "comment": "...",
  "documentIds": ["uuid"],
  "occurredAt": "..."
}
```

Candidate categories:

- DELAY_TRAFFIC
- EQUIPMENT_DAMAGE
- DESTINATION_CUSTOMER_ISSUE
- LOAD_REJECTED
- OTHER

This records/raises an operational issue; it does not allow Driver to arbitrarily edit Trip state.

## 15. Driver updates

### GET /driver/me/updates

**Backend implemented by #36** at `/api/v1/tenants/{tenantId}/driver/me/updates`.
Only the authenticated User's active linked Driver, with fresh TRIP_VIEW_ASSIGNED,
can retrieve its explicitly targeted rows (including reassignment-away notices).
`cursor`/`limit` only; incremental `createdAt ASC, id ASC` keyset, default 50/max 200.
Response: `items` (id/tripId/updateType/occurredAt/structured payload), `nextCursor`.
No arbitrary Driver ID, acknowledgment state, Flutter UI or unrelated Trip browse.
Durable PostgreSQL projection begins at V15 with no synthetic historical backfill.
Push delivery and outbox workers remain #55; missed push cannot lose the instruction.

Returns active server-side operational changes such as:

- reroute;
- active cancellation instruction;
- authorized Trailer substitution;
- Trip reassignment/update.

Push is only a notification channel; the authoritative update is retrievable through API.

## 16. Offline occurrence time

For offline commands:

- `occurredAt` = device-captured operational time;
- server persists/derives `recordedAt` = server receipt time.

Server may reject impossible/future timestamps or flag suspicious clock skew according to the future sync ADR.

The API does not treat device time as unquestionable authority.
