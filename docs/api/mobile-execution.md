# Driver Mobile Execution API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

All offline-capable mutation requests require `Idempotency-Key: <uuid>`.

The authenticated User must resolve to an active Driver for Driver-only commands.

## 1. Driver home/work

### Issue #42 implemented read subset

GET `/driver/me` returns own active linked Driver id/name/active/version. GET
`/driver/me/trips` returns nonterminal currently assigned work, ordered createdAt DESC/id DESC,
with cursor/limit (default50/max200), executable Stops and relevant planned Trailer context.
Both require fresh ACTIVE membership and TRIP_VIEW_ASSIGNED. Unknown query fields, including
active/date/Driver IDs, are rejected. No administrative actor histories, generic directory,
fabricated singular active Trip context. #43 adds own real shiftContext (see the implemented boundary below). Planned Trailer is not physical coupling.
The implemented own-home subset is described below. `/me` adds fresh Tenant-scope capability
hints for navigation only; server authorization remains mandatory on every resource request.
See [session/cache boundary](../mobile/issue-42-session-cache.md). Replay remains #46.

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

Implemented #47 extends #44 at the same endpoint. Both `MANUAL` and
`GEOFENCE_CONFIRMED` require human confirmation, fresh current assignment and
the existing capabilities/state/ledger rules. MANUAL never requires GPS.
GEOFENCE_CONFIRMED requires coordinates with positive finite accuracy at most
half the live configured Site radius, and Haversine distance plus accuracy within
that radius. The Site must be active and belong to the next EN_ROUTE Stop on an
IN_TRANSIT Trip; missing context is a safe 409 RESOURCE_CONFLICT. No default radius
or snapshot-derived target. Evidence is not proof of presence or permission.

Own authenticated Trip detail adds nullable `arrivalAssistance` with exactly that
next Trip/Stop/Site, configured geography and context versions/equipment. Driver
Stop snapshots omit latitude/longitude/radius. Stop and applicable Trailer events
derive HUMAN_USER + USER_ACTION for manual, HUMAN_USER + MOBILE_AUTOMATION for
geofence, and matching arrivalSource. Flutter supplies no actor/source_type.
See [architecture and validation](../mobile/issue-47-geofence-assisted-arrival.md).

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

Coordinates are optional for MANUAL and required for GEOFENCE_CONFIRMED;
they are event evidence, not continuous route history.

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

## Issue #39 implementation boundary

This is a reusable backend domain boundary, not new HTTP execution/correction APIs.
`TrailerMovementProjection` supplies typed depart, arrive, yardMove, correctLocation,
markLocationUnknown, recoverLocation and changeLoadState operations. No arbitrary
event name/metadata input is accepted. Each joins the caller's runtime transaction
and requires its Tenant context plus expected Trailer version. The orchestrator
must already authorize the workflow and lock Trip/equipment parents; the engine
then locks Trailer and validates physical Location identity through Customers' public
directory. It never takes a reverse Trip/equipment lock or independently claims an
offline key. Optional clientOperationId belongs to the enclosing winner transaction.

| Operation / fixed fact | Required location state | Projection effect |
|---|---|---|
| DEPARTED | AT_LOCATION with current location | IN_TRANSIT, current NULL, lastKnown origin, physical observation |
| ARRIVED | IN_TRANSIT | AT_LOCATION, current/lastKnown destination, physical observation |
| YARD_MOVED | AT_LOCATION; matching origin and distinct destination | AT_LOCATION at destination, physical observation |
| LOCATION_CORRECTED | Any valid state; nonblank reason | AT_LOCATION at corrected destination; observation only when explicitly confirmed |
| LOCATION_MARKED_UNKNOWN | AT_LOCATION or IN_TRANSIT; nonblank reason | LOCATION_UNKNOWN/current NULL; preserve lastKnown/lastSeen/lastDriver |
| LOCATION_RECOVERED | LOCATION_UNKNOWN | AT_LOCATION at recovered location, physical observation |
| LOAD_STATE_CHANGED | Any valid location; different accepted load state | Only load state changes; manual USER_ACTION requires nonblank reason |

All six distinct EMPTY/LOADED/UNKNOWN transitions are supported. Same-load requests
are deterministic RESOURCE_CONFLICT and append nothing. Departure/arrival/Yard/
correction/recovery never infer load state; no serviceability/designation mutation
is implemented. Every resulting AT_LOCATION has a real current location; IN_TRANSIT
and LOCATION_UNKNOWN have current NULL. No fake Location or snapshot is physical
identity. Missing Driver preserves the last real Driver; administrative corrections
and mark-unknown preserve physical lastSeen. Server updatedAt is separate.

Hook remains observation without departure; Drop remains AT_LOCATION and preserves
load. Their existing authority, replay and response shapes are unchanged. The same
writer appends the fact and projection atomically, flushing the authoritative
@Version once. Safe domain reasons include STALE_VERSION and INVALID_STATE_TRANSITION;
infrastructure errors remain infrastructure failures. No force update exists.

### Provenance, metadata and chronology

Typed provenance separates responsible actor from causal source: HUMAN_USER with
USER_ACTION or MOBILE_AUTOMATION requires a real User; SYSTEM/SYSTEM_PROCESS and
INTEGRATION/EXTERNAL_INTEGRATION require no fabricated User. The future server
orchestrator derives these values; no new client-controlled actor fields exist.
Confirmed geofence arrival can carry typed GEOFENCE_CONFIRMED only with human
MOBILE_AUTOMATION provenance. A suggestion cannot invoke a physical fact by itself.

Internally generated JSONB metadata is bounded by type:

- DEPARTED: previousLocationId (also event.locationId is the origin).
- ARRIVED: destinationLocationId, optional typed arrivalSource.
- YARD_MOVED: previousLocationId, newLocationId.
- LOCATION_CORRECTED: previousLocationState/previousLocationId/
  previousLastKnownLocationId/newLocationId, physicalObservation; a non-observational
  correction also records preservedLastSeenAt/preservedLastDriverId, including NULL.
- LOCATION_MARKED_UNKNOWN: previousLocationState/previousLocationId,
  preservedLastKnownLocationId/preservedLastSeenAt/preservedLastDriverId.
- LOCATION_RECOVERED: previousLocationState, recoveredLocationId.
- LOAD_STATE_CHANGED: previousLoadState, newLoadState.
- HOOKED/DROPPED: type, Location, actual Driver and occurrence explain their effect;
  no fabricated location transition or arbitrary metadata dump.

Preserved timestamp metadata is explicitly ISO-8601 text (or NULL), independent of
the persistence JSON mapper's Instant serialization settings.

occurredAt remains operational time, recordedAt is server persistence chronology.
Under the exclusive Trailer lock the writer uses microsecond server time, advancing
past the last recorded timestamp if equal/backward readings occur. Reconciliation
orders recordedAt ASC, id ASC, never occurredAt. Late occurrence is retained only
after expected-version/state preconditions pass; a stale offline attempt neither
appends a fact nor overwrites projection. This is not latest-occurrence-wins.

### Diagnostic comparison and deferments

Reconciliation derives locationState/current/lastKnown/lastSeen/lastDriver/load
only where supported by history. MATCH requires all six fields known and matching;
DRIFT means a known field differs; otherwise INSUFFICIENT_HISTORY explicitly tracks
missing baseline. Number/type/active/purpose/designated Customer/serviceability
are outside this reconstruction. No synthetic baseline, silent repair or current
GET replay is introduced. Known Accepted MARKED_OOS, RETURNED_TO_SERVICE and
DESIGNATED_CUSTOMER_CHANGED facts leave all six movement/load fields unchanged;
serviceability and designation are outside this projection. Truly unknown event
types invalidate earlier derived coverage until interpretable facts establish it again.

#40 owns authorized correction/mark-unknown/load-state/OOS/return-to-service/
designation endpoints; #41 owns full Trailer search/detail/timeline Web/API;
#44 owns Driver departure/arrival and Trip/Stop orchestration; #48 owns Yard Move
API/mobile. LOCATION_UNKNOWN's returned fixed movement summary lets a later caller
persist its alert intent in the same transaction when #56/#57 infrastructure exists;
no alert/outbox is fabricated here. #61 still owns generic Audit. No new OpenAPI
endpoint, Web/Flutter UI, coupling rule, physical execution command or capability
is delivered by #39.

## Issue #44 implementation boundary

See [Driver execution implementation](../mobile/issue-44-road-execution.md) for the five strict execution commands, own assigned detail, V18 Stop facts, physical projection and foreground idempotent recovery. First departure now uses real #43 duty/Pre-Trip validation; no fabricated prerequisites. Ordinary non-POD completion still rejects Delivery; the separate implemented #45 completion validates #53 evidence. #47 implements human-confirmed geofence assistance with independent manual fallback. QR identification adds version for the existing Hook contract. #46 supplies the existing offline replay worker. All capabilities and #38 physical contracts remain unchanged.

## Issue #46 client replay boundary

Flutter persists immutable command intents in Drift and replays the implemented
#38/#44 endpoints with the SAME Idempotency-Key and original occurrence/payload.
No public API, capability or domain state machine changes. A ledger replay response
is an acknowledgment; current own-Driver/work/detail reads reconcile state afterward.
See [queue migration, dependencies and response policy](../mobile/issue-46-command-replay.md).
First departure uses real #43 prerequisites. #45 delivery completion requires finalized #53 evidence. There is no generic /sync endpoint.

## Issue #53 evidence policy integration

Backend `OperationalEvidencePolicy` now validates the four finalized Pre-Trip
positions, signed BOL/no-receiver delivery, Driver damage and Fuel receipts from
Tenant-pinned PostgreSQL state. The #53 policy itself does not own the inspection,
delivery-completions, Fuel or exception endpoints above. Their implemented #43/#45 orchestration owns real
Shift/inspection context and departure prerequisites. #45 must invoke the typed
policy inside its command/ledger transaction and coordinate Stop event, fulfillment
and Trip effects. Its high-level application orchestration calls Documents and
Trips public APIs; Trips does not import Documents. #49 owns the contextual public
Yard/OOS evidence workflow; #54 owns media upload sequencing on the client.
See [server policy and joint transaction contract](issue-53-evidence-validation.md).

## Issue #43 implemented Shift / inspection boundary

All four commands above are implemented: strict JSON/UUID headers, fresh capabilities
SHIFT_START / SHIFT_END / PRETRIP_CREATE / POSTTRIP_CREATE, ACTIVE membership and own
active Driver before MobileOperationLedger replay. occurredAt is command occurrence;
createdAt/updatedAt are server persistence chronology. No query parameters accepted.
GET /driver/me adds shiftContext: currentShift, inspections (max50), authorizedTractors
(max50, deduplicated), usedTractors (max50), offDutyBlockers. Hints grant no permission.
Only active Tractors from nonterminal currently assigned Trips are eligible for
Pre-Trip, revalidated at save. tripId may be null with an own open Shift and a real
current assignment for that Tractor. Inspection before any Trip assignment needs a
future explicit Driver–Tractor assignment model. No new assignment relation exists.
Four distinct own UPLOADED documents must match each required type. Inspection plus
immutable INSPECTION links commit/rollback together; explicit Trip links additionally
use #53 authorization. Missing photo: EVIDENCE_REQUIRED; PENDING: UPLOAD_NOT_COMPLETE;
invisible context: RESOURCE_NOT_FOUND. OQ-052 stays open: checklist.items accepts
bounded scalar maps (100 items, 1–20 fields, keys max80, text max2000); no invented
regulatory catalog or approval boolean unlocks execution. Odometer 0..10^12, max3
fractional decimals. Post-Trip optional text fields max4000, no required photos;
without Trip, equipment must have an own Pre-Trip in this Shift.
Responses: Shift id/state/onDutyAt/offDutyAt/createdAt/updatedAt; inspection
id/shiftId/tripId/tractorId/inspectionType/odometer/occurredAt/createdAt. No tenant/actor IDs.
Off Duty blockers ACTIVE_WORK and POST_TRIP_REQUIRED reflect real assigned work and
used equipment. No override; closing Shift never completes work or changes coupling.
First departure reuses #44 and requires own open Shift, valid matching Driver/Shift/
current Tractor Pre-Trip with four linked finalized photos. Explicit inspection Trip
must match. Occurrence must follow inspection/Shift occurrence. Other-Shift history or
reassigned Tractor cannot authorize it. Delivery/POD completion is implemented by #45 below.
See ../mobile/issue-43-shift-inspections.md for locking, migrations and offline materialization.

## Issue #45 implemented Road evidence workflows

Delivery completion, own Fuel Events and Trip exceptions above are now implemented.
All use strict DTOs and UUID Idempotency-Key, current membership/capability/Driver
assignment checks before ledger replay. Signed BOL accepts only BOL; no-receiver
requires a distinct DELIVERY_PHOTO and Unicode-normalized nonblank comment (max4000).
Delivery requires IN_SERVICE/AT_STOP; recordDelivery wraps the actual Stop event,
Order fulfillment and Trip effects in one transaction. Required BASE_RETURN is
honored; Shift and physical Trailer state are unchanged. Fuel requires tripId,
current active Tractor and own open Shift during execution; odometer 0..10^12,
max3 decimals, finalized FUEL_RECEIPT. Exceptions require a comment, a closed
category and optional exact Trip Stop; damage requires finalized DAMAGE_PHOTO.
They never edit lifecycle, OOS or location. Committed Fuel/damage replays on a
terminal Trip still revalidate current assignment, types and every link.

Responses and exact schemas are marked implemented-issue-45 in OpenAPI. Safe
errors include EVIDENCE_REQUIRED, UPLOAD_NOT_COMPLETE, VALIDATION_FAILED,
RESOURCE_NOT_FOUND, CAPABILITY_DENIED, INVALID_STATE_TRANSITION, RESOURCE_CONFLICT
and IDEMPOTENCY_KEY_REUSED. Infrastructure failures remain safe 5xx.
Flutter retrieves the existing targeted Driver Updates API without push; original
ascending server cursor semantics are preserved. See the [implementation and
acceptance matrix](../mobile/issue-45-pod-fuel-exceptions.md).
