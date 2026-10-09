# Orders and Dispatch API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

## 1. Orders

### Implemented surface — issues #31–#33

The Accepted v0.1 contracts below describe the full future surface. Issue #31
implements only POST/list/detail for the initial Order aggregate:

- POST accepts `customerId`, `orderNumber`, nullable PO/shipment/BOL references,
  optional `cargoLines` (zero or more), and at least one `requestedStops`.
  Optional `plannedTrailerId` is implemented by #32 as initial Manifest planning,
  never a direct Order↔Trailer FK. Unknown fields give `400 VALIDATION_FAILED`.
- Creation is atomic and always produces `OPEN`, version 0 and null
  `deliveredAt`/`completedAt`/`cancelledAt`. Issue #33 adds the bounded reference
  edits, cancellation/completion and internal lifecycle hooks described below. Cargo units and requested Stop types remain open strings; the
  weight-unit example below is not a closed catalog.
- GET/list returns Order summaries and `nextCursor`; GET/detail and POST return
  the projection plus `cargoLines` and `requestedStops`, their IDs and timestamps.
  There are no public Manifest, Trip, fulfillment, evidence or history projections.
- List supports only `customerId`, `status`, `cursor`, `limit` (default 50,
  maximum 200). Ordering is `createdAt DESC, id DESC`; opaque keyset continuation
  is scoped again on every request, not an authorization token or public cursor
  encoding contract. Responses contain `nextCursor: null` at the end.
  Unbounded lists are not returned.
- `unplanned` is deferred until planning/assignment exists (#32/#34).
  `active` has no defined Order field/state mapping. Appointment ranges do not
  specify which of multiple requested Stops to match. Those Accepted filters
  remain future contracts and are rejected rather than silently simulated.
- ORDER_CREATE is checked fresh against the target Customer; ORDER_VIEW_ALL
  sees the Tenant, ORDER_VIEW_CUSTOMER_SCOPED sees current authorized Customers.
  A foreign explicit customer filter gives CUSTOMER_SCOPE_DENIED; an invisible
  direct resource gives generic RESOURCE_NOT_FOUND. Firebase claims grant nothing.
- POST has no idempotent replay ledger in #31. An uncertain response must be
  reconciled using reads, not blindly replayed.

**Destination snapshot representation:** the prior contract specifies an object
without an exact shape. The implemented minimum reuses the existing Site DTO:
`name` (nonblank, required), optional `address` with `line1`, `line2`, `city`,
`stateRegion`, `postalCode`, `countryCode`; optional `timezone`, `latitude`,
`longitude`, `geofenceRadiusM`. Address fields are individually nullable,
country code is two characters when supplied, timezone is IANA, coordinates are
independently nullable/in range and radius is positive when supplied. No new
address taxonomy or coordinate-pair rule is introduced. Only name is required for
an ad-hoc historical destination; the snapshot is frozen at creation.

Normalized destinations require a same-Tenant Customer↔Site association.
Association removal checks fresh active Order use under the Site's exclusive
parent lock, coordinated with creation's shared lock. OPEN, PLANNED, IN_PROGRESS,
PARTIALLY_DELIVERED, DELIVERED and CANCELLING block removal; COMPLETED/CANCELLED
alone do not. Existing active Customer-specific Locations still block removal.
Historical Stops remain intact and have no permanent FK to the mutable association.
An optional Location belongs to that exact Site/Tenant and is neutral or matches
the Order Customer. A snapshot may coexist with normalized references but never
bypasses their validation. Text/address matching grants no authority. An ad-hoc
snapshot does not create or impersonate master Site/Location data; authorization
always comes from the Order Customer. Future physical execution retains the
v1.0.1 requirement to normalize before any transition requiring a real Location.

The OpenAPI implemented request/response schemas are separate from
`CreateOrderRequest` and future operational contracts below; #33's strict mutation
schemas implement only the bounded behavior described in its implementation section.
Creator User identity remains server-derived persistence metadata only; it is not
included in POST detail, list summaries or GET detail. Authorized actor/provenance
presentation belongs to the future audit/history surface.

### POST /orders

Capability: `ORDER_CREATE`.

Customer Dispatcher requests are automatically scoped to an authorized Customer.

Request:

```json
{
  "customerId": "uuid",
  "orderNumber": "ORD-123",
  "poNumber": "PO-88",
  "shipmentNumber": "SHIP-2",
  "bolReference": "BOL-456",
  "cargoLines": [
    {
      "description": "Corrugated sheets",
      "quantity": 20,
      "palletCount": 10,
      "weight": 18500,
      "weightUnit": "LB"
    }
  ],
  "requestedStops": [
    {
      "sequenceNumber": 1,
      "siteId": "uuid",
      "locationId": null,
      "siteSnapshot": null,
      "stopType": "DELIVERY",
      "scheduledAt": "2026-10-05T15:00:00Z",
      "instructions": "..."
    }
  ],
  "plannedTrailerId": "uuid"
}
```

`plannedTrailerId` additionally requires fresh `TRIP_ASSIGN_TRAILER` for the Order
Customer and fresh Trailer read scope. Admin/Operations can plan Tenant-wide;
Customer Dispatcher must see the Trailer through its designated authorized
Customer. Same-Tenant, active and IN_SERVICE are required. In the same transaction
as Order/cargo/Stops, #32 creates an OPEN/version-0 Manifest and one allocation
with `allocatedQuantity`, `allocatedPalletCount`, `allocatedWeight` all null
(amount unspecified). Null/omission creates no Manifest. The Order stays OPEN,
and no physical Trailer field, coupling or Trip is changed.

### Internal Manifest planning — issue #32

V11 implements many-to-many `load_manifests`/`manifest_orders`, with an optional
Trailer and tenant-safe parent FKs. Multiple Manifests may reference one Trailer;
an Order can appear once per Manifest and across multiple Manifests.
No public Manifest CRUD endpoints are introduced.

Service commands create OPEN/version 0, add/remove allocations only for OPEN
Orders in an OPEN Manifest, and explicitly close OPEN -> CLOSED without reopen.
Every existing-Manifest mutation requires `expectedVersion`, increments version
and advances `updatedAt`; close records server `closedAt`. Parent row locking
serializes allocation commands, while JPA `@Version` and expected-version checks
prevent lost updates (`STALE_VERSION`). No lifecycle of Order/Trip/Trailer/Shift
automatically closes a Manifest. CLOSED allocations are preserved and immutable
through the service layer. Partial unload, returns, cancelled remainders and
post-departure reallocation remain deferred.

Allocation amounts are independently nullable/nonnegative, including zero and
decimals for quantity/weight; pallets are integral. There is no unit conversion,
automatic cargo distribution, capacity check or inferred fully-allocated state.
All commands authorize from current PostgreSQL memberships/roles. Scoped actors
can alter only their own Customer Orders; mutation results expose only Manifest
revision metadata, never other allocations. Closing requires Tenant-wide
`TRIP_ASSIGN_TRAILER`. `unplanned` still requires #34 Trip planning; it is not
implemented as “no Manifest”. Active/appointment filters remain deferred too.

For a requested Stop, either a normalized `siteId` or an approved `siteSnapshot` may be supplied. `siteSnapshot` is used when the operational destination has not yet been normalized into reusable master Site data and is preserved for historical accuracy.

Response: `201` Order detail.

### GET /orders

Filters:

- customerId;
- status;
- appointmentFrom/To;
- unplanned;
- active;
- cursor/limit.

Customer-scoped callers cannot broaden scope by passing another `customerId`.

### GET /orders/{orderId}

Returns:

- Order projection;
- cargo;
- requested Stops;
- fulfillment progress;
- linked Trips/Stops;
- permitted evidence summary;
- relevant history summary.

### PATCH /orders/{orderId}

Capability: `ORDER_EDIT`.

Only ordinary editable fields before physical execution.

Request includes `expectedVersion`.

If execution has begun:

`409 INVALID_STATE_TRANSITION` with guidance to use reroute/active-cancellation actions.

### POST /orders/{orderId}/cancellations

Capability: `ORDER_CANCEL`.

Request:

```json
{
  "expectedVersion": 5,
  "reason": "Customer cancelled",
  "resolution": {
    "type": "RETURN_TO_BASE",
    "destinationSiteId": "uuid",
    "instructions": "..."
  }
}
```

Before execution, `resolution` may be omitted and Order can transition directly to CANCELLED.

After execution, resolution/instructions are required as appropriate and Order enters CANCELLING until physical resolution completes.

### POST /orders/{orderId}/completion

Capability: `ORDER_COMPLETE`.

Requires Order state DELIVERED.

Request:

```json
{
  "expectedVersion": 8
}
```

## 2. Trip planning

### POST /trips

Capability: `TRIP_PLAN`.

Request:

```json
{
  "orderIds": ["uuid"],
  "stops": [
    {
      "sequenceNumber": 1,
      "stopType": "PICKUP",
      "siteId": "uuid",
      "locationId": "uuid",
      "siteSnapshot": null,
      "scheduledAt": "...",
      "orderIds": ["uuid"],
      "instructions": "..."
    },
    {
      "sequenceNumber": 2,
      "stopType": "DELIVERY",
      "siteId": "uuid",
      "siteSnapshot": null,
      "scheduledAt": "...",
      "orderIds": ["uuid"]
    }
  ],
  "driverId": "uuid",
  "tractorId": "uuid",
  "plannedInitialTrailerId": "uuid"
}
```

Rules:

- one or more executable Stops;
- Driver/Tractor required to create directly as ASSIGNED;
- if assignment is incomplete, Trip may be created PLANNED;
- Trailer may be null;
- Order/Stop relationships are validated tenant/customer-side.

Response returns Trip projection + Stops.

### GET /trips

Filters:

- status;
- driverId;
- customerId;
- from/to;
- active.

### GET /trips/{tripId}

Returns:

- current Trip;
- current Driver/Tractor/planned/current Trailer summary;
- ordered Stops;
- linked Orders;
- assignment history summary;
- operational timeline.

## 3. Assignment commands

### POST /trips/{tripId}/driver-assignments

Capability: `TRIP_ASSIGN_DRIVER`.

Request:

```json
{
  "expectedVersion": 2,
  "driverId": "uuid",
  "reason": "Initial assignment"
}
```

Used for initial assignment and explicit reassignment.

Backend records assignment event/history.

### POST /trips/{tripId}/tractor-assignments

Capability: `TRIP_ASSIGN_TRACTOR`.

### POST /trips/{tripId}/planned-trailer-assignments

Capability: `TRIP_ASSIGN_TRAILER`.

This is expected/planned Trailer selection, not physical Hook.

## 4. Trailer substitution authorization

### POST /trips/{tripId}/trailer-substitution-decisions

Capability: `TRAILER_SUBSTITUTE_AUTHORIZE`.

Request:

```json
{
  "expectedTrailerId": "uuid",
  "requestedTrailerId": "uuid",
  "decision": "AUTHORIZE",
  "reason": "Assigned trailer unavailable"
}
```

Decision:

- AUTHORIZE
- REJECT

If authorized, Driver can retry/continue Hook using the requested Trailer.

Authorization does not itself create physical coupling.

## 5. Reroute / destination change

### POST /trips/{tripId}/reroutes

Capability: `TRIP_REROUTE`.

Request:

```json
{
  "expectedVersion": 9,
  "affectedStopId": "uuid",
  "newSiteId": "uuid",
  "newLocationId": "uuid",
  "scheduledAt": "...",
  "reason": "Customer changed receiving location",
  "instructions": "..."
}
```

Side effects:

- preserves original Stop/destination history;
- updates/adds future operational instruction;
- creates audit/Trip event;
- creates Driver update/notification where configured;
- Trip remains physically IN_TRANSIT/AT_STOP as appropriate.

## 6. Trip cancellation

### POST /trips/{tripId}/cancellations

Capability: appropriate dispatch cancellation capability; initially governed by Order cancellation/Transport Operations authorization rules.

Request:

```json
{
  "expectedVersion": 9,
  "reason": "...",
  "resolutionType": "RETURN_TO_BASE",
  "destinationSiteId": "uuid",
  "instructions": "..."
}
```

Possible resolution types for MVP:

- RETURN_TO_BASE
- REROUTE
- HOLD_CONTACT_DISPATCH
- OTHER_AUTHORIZED

After movement starts, Trip enters CANCELLING until safe physical resolution completes.

## 7. Dispatch board projection

### GET /dispatch/board

Capability: `TRIP_VIEW_ALL`.

Query filters:

- date;
- customerId;
- driverId;
- status.

Returns a read-optimized projection:

```json
{
  "pendingOrders": [],
  "activeTrips": [],
  "attentionItems": []
}
```

This is a read model, not an aggregate mutation endpoint.

## 8. Operational timeline

### GET /trips/{tripId}/timeline

Returns ordered user-facing events, for example:

- assigned;
- Pre-Trip completed;
- Hook;
- departure;
- arrival;
- service;
- POD;
- reroute;
- cancellation;
- completion.

Internal audit noise should not be mixed into the primary operational timeline.

## Issue #33 implementation boundary

PATCH `/orders/{orderId}` is implemented for OPEN/PLANNED only and changes exactly
`poNumber`, `shipmentNumber`, `bolReference`. Omission preserves; explicit null clears.
Strict bodies require a nonnegative integer `expectedVersion`. No-op keeps version;
material edit advances it once. Cargo, Stops, Customer, number and Trailer are immutable
through this command. Invalid state returns `409 INVALID_STATE_TRANSITION`, distinct
from `409 STALE_VERSION` after the scoped Order lock/version check.

`POST /orders/{orderId}/cancellations` requires ORDER_CANCEL, expectedVersion and
nonblank reason. OPEN/PLANNED -> CANCELLED sets server cancelledAt and releases only
allocations in OPEN Manifests, advancing each affected Manifest version/updatedAt.
CLOSED allocations and all cargo/requested Stops/snapshots remain unchanged; empty
Manifests remain OPEN. IN_PROGRESS/PARTIALLY_DELIVERED -> CANCELLING requires a
resolution with nonblank type/instructions and optional Customer-compatible Tenant
Site. No closed resolution catalog is assumed. It emits that operational instruction
payload but creates no Trip/Stop or physical Trailer change. cancelledAt stays null
until the future operational flow calls the cancellation-finished hook.

`POST /orders/{orderId}/completion` requires ORDER_COMPLETE and expectedVersion only.
DELIVERED -> COMPLETED preserves deliveredAt and sets server completedAt. No reopen.
All commands resolve fresh PostgreSQL capabilities/Customer scopes; invisible IDs
return generic 404. An optional Idempotency-Key is not backed by a replay ledger;
an uncertain response must be reconciled, not assumed replayable.

### Order / Manifest concurrency protocol

Allocation add/remove takes the scoped Order shared lock before checking OPEN,
then locks its Manifest. Order mutation takes the scoped Order exclusive lock first.
Pre-execution cancellation locks affected OPEN Manifests in UUID order, rechecks
OPEN after acquiring each lock, removes allocations, and commits together with Order
status/version/milestones. A failure rolls back every change. Allocation that owns
its shared lock finishes before cancellation releases it; allocation waiting behind
cancellation observes the new state and rejects. Manifest close remains independent
and explicit; CLOSED history is never released.

### Future execution and history integrations

Orders-owned `OrderLifecyclePort` exposes only plan, start, partial, resume, deliver
and finish-cancellation hooks inside an already-established runtime Tenant transaction.
The future caller owns workflow authorization, operational planning, Stops/POD checks.
Manifest planning alone never changes OPEN to PLANNED. No execution REST or Trips are
created. Every accepted transition advances version/updatedAt with server milestones.

`OrderLifecycleEvent` carries action, Tenant/Order/Customer, previous/new state,
version, server occurrence time, human User/membership attribution or SYSTEM, and
reason/resolution where applicable. Publication is an **in-process transaction-bound
hook, not durable history**. Audit persistence/query and generic actor infrastructure
remain #61; future durable listeners must participate in the transaction (for example
BEFORE_COMMIT), never treat a rolled-back publication as committed history. No reason
or resolution is exposed in GET. #33 deliberately adds no history table/column or
Audit API. Until #61, the operational resolution payload has no durable replay store;
this bounded integration gap is explicit, not a claim of audit durability.
