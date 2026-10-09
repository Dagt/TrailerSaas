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

## Issue #38 implementation boundary

The runtime API implements physical coupling only:

- `POST /api/v1/tenants/{tenantId}/trips/{tripId}/hooks`;
- `POST /api/v1/tenants/{tenantId}/trips/{tripId}/drops`.

Both return 200 with `coupling`, a bounded `trailer` current projection, the locked
`trip` projection and `movementEvent` (id/type/occurredAt/recordedAt). No Tenant or
replay-ledger metadata is public. The strict implemented OpenAPI defines the exact
body: Hook requires trailerId/tractorId/driverId/occurredAt/expectedTrailerVersion;
Drop requires trailerId/stopId/locationId/occurredAt. The canonical UUID header
`Idempotency-Key` is required. Actor/source fields are never client inputs.

### Authority and physical preconditions

Every request, including replay, requires fresh ACTIVE membership/capability,
the authenticated User's active linked Driver and that Driver's current Trip
assignment. Hook uses TRAILER_HOOK; Drop uses TRAILER_DROP; both additionally
require TRIP_EXECUTE_ASSIGNED. There is no Admin/Operations override and no generic
Trailer browse requirement. Assigned physical command authority is independent of
QR/UUID identification. Trip states allowed for either command are ASSIGNED,
AT_STOP and CANCELLING; other states return INVALID_STATE_TRANSITION.

First Hook requires the active current assigned Tractor, active IN_SERVICE Trailer,
an exact Trailer version and the durable current expected Trailer. The #35
substitution policy is reused: mismatch/null expectation returns
TRAILER_SUBSTITUTION_NOT_AUTHORIZED; AUTHORIZE changes the expectation and permits
a later physical command, whereas REJECT never grants authority. Hook cannot
double-couple a Trailer/Tractor or give the same Trip a second active coupling.
The latter is a service invariant, not a new unique Trip constraint.

Drop closes only this Trip's existing interval, with occurrence no earlier than
Hook. It validates a same-Trip normalized Stop and same-Site Operational Location,
including current compatibility for the Stop's actual mapped Order Customers.
An ad-hoc frozen snapshot cannot establish a physical Drop location. Drop can
release already coupled inactive/OOS equipment. The actual assigned Driver is
recorded in the Drop event; the original interval's Driver and Tractor are preserved.

### Projection, provenance and history

`trailer_couplings` is the authoritative physical Trailer/Tractor relationship;
plannedInitialTrailerId remains an expectation. Hook preserves location/load/
serviceability/designation/purpose, updating only lastDriverId/lastSeenAt/version/
updatedAt. It is not departure. Drop also sets AT_LOCATION, currentLocationId and
lastKnownLocationId. Neither command changes Trip/Stop/Order lifecycle or load state.

Each successful command creates exactly one append-only HOOKED/DROPPED fact with
server-derived `actor_type=HUMAN_USER`, `source_type=USER_ACTION` and actor_user_id.
This applies Accepted hardening M; SQL rejects invalid catalogs and HUMAN_USER
without a User. occurredAt is device time; recordedAt is server time. Closed
intervals cannot be rewritten/reopened and are never deleted. Drop followed by
Hook is two explicit operations, never an implicit combined shortcut.

### Serialization, replay and atomicity

Locks are acquired in this order: exclusive Trip, shared active Driver identity,
transaction-local unique-row operation claim, exclusive Tractor, exclusive Trailer,
coupling interval. Drop then validates locked Stop/destination parents. Existing
assignment/substitution commands use the same Trip parent lock; administrative
equipment changes wait for physical parent locks. Site compatibility retains the
shared Site versus exclusive association-removal protocol.

Accepted hardening L governs arbitration: after authentication, Tenant context and
fresh assigned-Driver authorization, INSERT a PROCESSING row using
`ON CONFLICT (tenant_id,client_operation_id) DO NOTHING RETURNING id`. No prior
existence query, advisory lock, process mutex or committed lease is used. The unique
index makes duplicates wait for the competing transaction. A winner commit lets
the duplicate read the terminal row in a new statement; a winner rollback removes
the claim, allowing the waiting INSERT to claim and continue.
The canonical request SHA-256 includes operation type, path Trip and every logical
body field, including Hook's expectedTrailerVersion. It excludes transport JSON
ordering and bearer tokens. Equivalent Instant representations canonicalize equally.

Same key/User/type/hash returns the saved SUCCEEDED result, even after the original
physical change/version advance or a later Drop. Current authorization is still
required. Changed key content/type/path/User returns IDEMPOTENCY_KEY_REUSED. First
execution alone checks physical state/version. request_hash and server-created
created_at are mandatory; PROCESSING has processed_at NULL and terminal
SUCCEEDED/REJECTED requires processed_at. The same transaction writes the business
effect and bounded response, then updates its claim to SUCCEEDED before commit.
PROCESSING is never intentionally committed independently. Failed commands,
including transient failures, roll back the claim and all business writes; #38 does
not persist rejected responses. device_id remains NULL until the User-device registration parent
exists (#56); no placeholder device table/FK or invented device identity is created.

Movement fact, interval, Trailer projection and saved bounded response commit in
one runtime Tenant transaction. Failure at any write rolls all four back. Lost
response after commit is recoverable by the same key; a new Hook key with an old
version receives STALE_VERSION. Integrity conflicts are safely mapped; infrastructure
failures stay safe 5xx. Runtime cannot update/delete movement facts or delete ledger
rows. Ledger UPDATE is restricted to status, processed_at and result/response columns;
claim identity, request hash and created_at cannot be updated by runtime. It cannot
delete intervals, rewrite immutable interval fields, own tables or bypass RLS.

### Deferred surfaces

#39 owns broader movement/current projection; #40 corrections/OOS/load commands;
#41 full Trailer search/detail/timeline. Physical facts are not fabricated into the
Trip assignment/operational timeline. #44 owns departure and normal Trip execution,
arrival/service/POD and Driver flows; #46 owns client queue/dependencies/retries.
No Flutter/Web execution UI, Shift, inspection, outbox or generic Audit is added.
