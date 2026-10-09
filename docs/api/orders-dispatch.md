# Orders and Dispatch API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

## 1. Orders

### Implemented surface — issues #31–#34

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
  Order responses contain no Manifest, Trip, fulfillment, evidence or history projections.
- List supports `customerId`, `status`, `unplanned`, `cursor`, `limit` (default 50,
  maximum 200). Ordering is `createdAt DESC, id DESC`; opaque keyset continuation
  is scoped again on every request, not an authorization token or public cursor
  encoding contract. Responses contain `nextCursor: null` at the end.
  Unbounded lists are not returned.
- `unplanned` is implemented by #34 against active Trip mappings (see boundary below).
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
`TRIP_ASSIGN_TRAILER`. `unplanned` uses #34 Trip planning; it is not
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


## Issue #34 implementation boundary

V13 adds only `trips`, `trip_stops` and `trip_stop_orders`. All are Tenant-owned,
with normal Tenant RLS excluding `/me` bootstrap, no PUBLIC grants and no ownership,
BYPASSRLS or schema CREATE for runtime. Trips have SELECT/INSERT/UPDATE; executable
Stops SELECT/INSERT; mappings SELECT/INSERT/DELETE for coordinated pre-execution
release. Composite FKs protect equipment, Site/Location and Order/requested-Stop
integrity. No destructive cascades or permanent historical FK to `customer_sites`.

POST `/trips` requires Tenant-wide `TRIP_PLAN`, plus the corresponding fresh
assignment capability for each supplied initial equipment ID. Driver/Tractor must
be same-Tenant, exist and be active, held under shared parent locks. A planned
Trailer additionally must be active/IN_SERVICE and pass `TrailerPlanningPolicy`.
Both Driver and Tractor produce ASSIGNED; either absent produces PLANNED, retaining
any valid partial assignment. An inactive supplied resource is rejected rather
than silently discarded. Trailer is optional and never physical coupling.
All milestones start null, version 0; every executable Stop starts PENDING.

Strict create accepts only top-level `orderIds`, `stops`, optional `driverId`,
`tractorId`, `plannedInitialTrailerId`. Orders are unique/nonempty. Stop sequences
are positive/unique but not necessarily contiguous; Stop orderIds may be empty
for operational visits. Their union must exactly cover the top-level Order set.
An Order may appear in multiple Stops and multiple Trips. BASE_RETURN is explicit,
never inserted automatically. Requested Order Stops remain separate historical
rows; no matching by Site/address/name occurs.

**Requested-Stop identity gap:** the accepted public `TripStopInput` has no
`orderStopId`. POST therefore writes `order_stop_id = NULL`. The SQL composite FK
and `UNIQUE NULLS NOT DISTINCT (tenant_id,trip_stop_id,order_id,order_stop_id)`
retain flexible specific mapping for a later approved surface. Generic duplicates
and specific duplicates are rejected, while distinct requested Stops of one Order
may map to the same executable Stop. No public requested-Stop field is invented.

Trip creation locks Orders exclusively in deterministic UUID order before Site,
equipment and Trip writes. Only OPEN/PLANNED are eligible; other states return
INVALID_STATE_TRANSITION. OPEN changes to PLANNED through #33's lifecycle hook,
with one version/time advance and ORDER_PLANNED event; already PLANNED gets no
false transition or version advance. Trip/Stops/mappings and all Order transitions
commit or roll back together. Manifest planning alone still does not plan an Order
operationally. No execution or physical Trailer state changes occur.

Normalized destinations use shared Site locks, coordinated with exclusive Site
locks on Customer association removal. Every Order Customer served at the Stop
must have a current association; neutral Locations may serve multiple Customers,
Customer-specific Locations must match all served Customers. A Trip-only Stop
may use a Tenant Site/neutral Location, but not a Customer-specific Location.
Ad-hoc destinations reuse the strict minimal Order snapshot representation, frozen
at creation; normalized references may coexist with a snapshot. Snapshot text
never authorizes. The Customers-owned usage guard also blocks association removal
for PLANNED/ASSIGNED/IN_TRANSIT/AT_STOP/CANCELLING Trips serving that Customer at
that Site. Terminal Trips alone do not block; historical Stops remain unchanged.


Pre-execution Order cancellation invokes an Orders-owned release SPI implemented
by Trips. The existing Order exclusive lock is acquired first; affected Trip
parents are locked in deterministic UUID order. Only PLANNED/ASSIGNED permit
removing this Order's mappings. Each modified Trip advances version/updatedAt once;
its status, all executable Stops and other Orders' mappings remain. No automatic
Trip cancellation or Stop deletion occurs. A mapping to an active/executed/terminal
Trip causes safe conflict and full rollback, preserving history. Manifest release,
Trip mapping release and cancellation are one transaction.

GET `/trips` and GET `/trips/{tripId}` require Tenant-wide `TRIP_VIEW_ALL`.
Detail exposes the current Trip projection and sequence-ordered executable Stops
with mapped Order IDs, no Tenant ID, requested-Stop identities, fake assignment
history/timeline or Audit data. List keysets `createdAt DESC,id DESC`, default 50,
max 200; supported filters are status, driverId, customerId, cursor, limit.
customerId means at least one mapped Order of that Customer. Unknown/from/to/active
parameters are rejected. Driver-assigned mobile reads remain a later bounded flow.

Order `unplanned=true` selects nonterminal Orders (excluding COMPLETED/CANCELLED)
without mappings to PLANNED/ASSIGNED/IN_TRANSIT/AT_STOP/CANCELLING Trips. Terminal
Trip mappings do not count; Manifests do not count. `false` selects the complement.
The bounded SQL planning-directory SPI retains the Order's fresh Customer scope,
filters and real keyset without N+1 or an Orders→Trips module dependency. Order
active/appointment filters remain explicitly deferred.

No Idempotency-Key replay guarantee exists for create Trip. Reconcile uncertain
responses through GET/list; a blind retry can create another Trip. Durable initial
assignment/reassignment history starts in #35, not a substitute event table here.
Trip reroute/cancellation belongs to #36; Web dispatch belongs to #37. There are no
assignment mutation APIs, Stop events/execution commands, POD, Hook/Drop, coupling,
movement, Shift, audit subsystem, outbox or physical Trailer changes in #34.


## Issue #35 implementation boundary

V14 creates only append-only `trip_assignment_events`, with the seven Accepted
Driver/Tractor assignment/reassignment, Trailer planning and substitution decision
codes. SQL checks require exactly the event's corresponding resource. Tenant-safe
Trip/equipment FKs and the global actor User FK preserve identity without destructive
cascades. Runtime has SELECT/INSERT only; Tenant RLS excludes bootstrap. The index
`(tenant_id,trip_id,occurred_at,id)` makes detail/timeline chronology deterministic.
V1–V13 are unchanged. Pre-V14 current assignments remain authoritative projections;
no synthetic historical actor, reason or sequence is backfilled.

New Trip creates append initial events for each supplied resource with authenticated
User actor, server occurrence time and null reason, atomically with Trip/Stops/mappings
and Order planning. Initial Trip version stays 0. No assignments means no events.

The three assignment endpoints require strict nonnegative integer expectedVersion,
target UUID and nonblank reason. A scoped Trip exclusive lock precedes version/state
checks. Material mutations advance @Version/updatedAt once and append history in the
same transaction. Same-current-resource requests with the current version are 200
no-ops, preserving time/version/event count; stale versions are STALE_VERSION.
Targets are freshly checked under shared parent locks. Driver/Tractor must be active;
normal Trailer planning must be visible, active and IN_SERVICE. No Trailer row changes.

Driver/Tractor changes allow PLANNED, ASSIGNED, IN_TRANSIT, AT_STOP and CANCELLING,
never terminal states. Completing both active current resources and an executable Stop
promotes PLANNED to ASSIGNED; Trailer remains optional. Active reassignment preserves
physical lifecycle/milestones. Ordinary Trailer assignment fills a null expectation in
PLANNED/ASSIGNED/IN_TRANSIT/AT_STOP; replacing an existing expectation ordinarily is
limited to PLANNED/ASSIGNED. Same Trailer is a no-op in those allowed states; CANCELLING
and terminal states reject. Existing expectations in active Trips require substitution.

Fresh capability scopes are derived from PostgreSQL, not Firebase claims. Customer
Dispatcher commands require **every** Customer represented by currently mapped Orders
to be authorized, with nonempty mappings. The locked Trip parent serializes mapping
release; customer derivation does not acquire Order locks in reverse cancellation order.
A mixed/out-of-scope Trip returns generic 404, never the blocking Customer. Dispatcher
still has no TRIP_ASSIGN_TRACTOR or general TRIP_VIEW_ALL; no role mappings change.

Substitution accepts exactly expectedTrailerId, requestedTrailerId, AUTHORIZE/REJECT
and reason, with no invented expectedVersion. ASSIGNED/IN_TRANSIT/AT_STOP only. Exclusive
Trip locking and exact current nonnull expectedTrailerId matching guard concurrency;
a stale/missing expectation gives RESOURCE_CONFLICT. Identical expected/requested is
VALIDATION_FAILED. AUTHORIZE requires visible active/IN_SERVICE target, updates current
planning and appends AUTHORIZED. REJECT needs visible same-Tenant existence only (even
inactive/OOS), leaves current planning intact and appends REJECTED. Both advance version
and time, preserve expectedTrailerId in metadata and change no physical coupling/state.
A scoped requested Trailer must also pass fresh Customer Trailer visibility.

Reassignment metadata retains previousDriverId/previousTractorId/previousTrailerId.
Events retain server actor, reason/time and all previous resources, with no mutable
previous-resource columns or invented assignment-segment tables. Trip detail adds
assignmentHistory. GET timeline requires TRIP_VIEW_ALL and returns only persisted
assignment/substitution items in occurredAt ASC/id ASC order. It is a partial domain
timeline, not generic Audit, reroute, cancellation, execution or evidence history.

`TripTrailerExpectationPolicy` is a public planning-only boundary for the future Hook
caller's already-established runtime Tenant transaction. It reads the current expected
Trailer freshly under a shared Trip lock: matching scanned ID satisfies planning
expectation; mismatch or unknown expectation never authorizes silent substitution.
AUTHORIZE changes that expectation; REJECT does not. The future caller still owns full
assigned-work, capability, physical-state and Hook validation. No approval cache,
Firebase authorization, admin datasource, BYPASSRLS or physical Hook is introduced.

#36 owns reroute/cancellation/notification flows, #37 owns Web, later execution owns
Hook/Drop/Stops/POD/coupling. #61 generic Audit remains deferred. V14 operational history
is durable domain history, not an early generic Audit subsystem or formal event sourcing.


## Issue #36 implementation boundary

V15 adds append-only `trip_operational_events` and `trip_driver_updates`, with
Tenant RLS, tenant-safe Trip/Driver FKs and a composite Stop/Trip FK. Both runtime
tables are SELECT/INSERT only. Stops additionally receive UPDATE for bounded
future-instruction/cancellation projection changes; no delete/cascade is added.
V1–V14 remain unchanged. No generic Audit, outbox jobs, push or Stop execution table.

POST reroutes requires fresh TRIP_REROUTE, expectedVersion, affectedStopId,
normalized newSiteId, reason and instructions. Only IN_TRANSIT/AT_STOP Trips and
PENDING/EN_ROUTE Stops are eligible. Under exclusive Trip then Stop locks, every
mapped Customer must be visible for a scoped actor; all Customers served by the
affected Stop must be compatible with the new Site/Location. Trip-only visits
permit neutral Locations only. Shared Site/Location locks coordinate with master
mutation/disassociation. Same-Tenant Site data is frozen through Customers' public
boundary. Before mutation, durable metadata retains the complete previous
Site/Location/snapshot/schedule/instructions/status/version and replacement.
Trip and Stop versions advance, physical lifecycle and Trailer fields do not.
Later master edits cannot overwrite snapshots. Completed Stops are immutable.

POST cancellations requires both TRIP_REROUTE and ORDER_CANCEL for every mapped
Customer; unmapped Trips require Tenant-wide authority. PLANNED/ASSIGNED -> CANCELLED
sets server cancelledAt and cancels PENDING/EN_ROUTE/EXCEPTION Stops, preserving
terminal Stops byte-for-byte. Inconsistent ARRIVED/IN_SERVICE blocks that command.
IN_TRANSIT/AT_STOP -> CANCELLING preserves startedAt, leaves cancelledAt null and
leaves every Stop/mapping untouched. Active instructions require resolutionType
and nonblank instructions: RETURN_TO_BASE requires a same-Tenant destination;
REROUTE requires a destination compatible with every affected Customer;
HOLD_CONTACT_DISPATCH forbids a destination; OTHER_AUTHORIZED optionally supplies
a same-Tenant destination. Pre-movement resolution is optional. Orders are never
implicitly cancelled and no physical location/coupling/movement is asserted.

Trips-owned `TripCancellationResolutionPort` is a MANDATORY, already Tenant-pinned
transaction hook, not a REST endpoint. Future execution owns authorization and
actual safe-resolution facts. It locks CANCELLING, sets CANCELLED/server cancelledAt,
advances version/time, cancels only nonterminal Stops and persists finished history
plus Driver update. Terminal Stops remain exact; no fake physical facts are emitted.

Every relevant assignment/substitution/create command now uses the same durable
transactional Driver-update sink. A new Driver and a reassigned-away Driver each
receive targeted updates; the old Driver's removal notice excludes the new identity
and future plan. Tractor/Trailer changes target the current Driver; REJECT records
the decision and unchanged expected Trailer. Projection starts at V15 without
synthetic historical delivery/backfill. Update-insert failure rolls back business
state/history. The transaction-bound `TripDriverUpdateRecorded` hook is only an
integration seam for #55 enqueue in the same transaction, never authoritative
truth, delivery proof or an in-memory correctness dependency.

GET driver/me/updates uses fresh TRIP_VIEW_ASSIGNED and Drivers' public active
User-link boundary. Query includes the resolved Driver ID server-side only;
historical targeted removal notices remain retrievable after reassignment.
Incremental keyset uses createdAt ASC/id ASC, opaque cursor, default 50/max 200.
No acknowledgment state or arbitrary Driver lookup. Push remains secondary.
Timeline merges assignment and operational events globally by occurredAt ASC/id ASC;
assignmentHistory remains assignment-only, operationalHistory is separate.
Neither timeline nor detail exposes delivery/outbox/generic Audit rows.

Trip parent serializes scope derivation, assignments, reroutes, cancellations and
Order mapping release. Stop locks follow deterministic sequence/id order; Site
locks follow. Trip commands never take reverse Order locks (Order cancellation
uses Order -> Trip). Same-version commands have one winner and STALE_VERSION for
the loser. History/update/projection share one transaction and roll back together.
No Idempotency-Key replay ledger: uncertain responses must be reconciled using
Trip detail/timeline/Driver updates, never assumed replayable.

#37 Web, #44 physical execution/mobile, #45 POD, #55 outbox workers/FCM/retries and
#61 generic Audit remain deferred. No departure, arrival, Hook/Drop, movement,
coupling, Order cancellation side effect or normal Trip-completion endpoint.


## Issue #37 implementation boundary

`GET /dispatch/board` requires **both** fresh Tenant-wide TRIP_VIEW_ALL and
ORDER_VIEW_ALL. The same runtime Tenant transaction/RLS applies. No new migration,
capability, role mapping or domain command is introduced; V1–V15 remain unchanged.
Orders exposes `OrderDispatchDirectory`, a public bounded read boundary implemented
inside Orders using the existing planning-presence SPI. Trips owns the board.
Neither module imports another module's internal persistence or introduces a cycle.

Exact projection:

- `generatedAt`: server observation time;
- `pendingOrders`: up to 20 nonterminal Orders without mappings to active Trips,
  using #34's `unplanned=true` semantics, with id/customerId/orderNumber/status/
  version/createdAt/updatedAt;
- `activeTrips`: up to 20 PLANNED/ASSIGNED/IN_TRANSIT/AT_STOP/CANCELLING projections,
  with id/status/currentDriverId/currentTractorId/plannedInitialTrailerId/version/
  createdAt/updatedAt and nullable `nextPlannedStop`. That summary contains id,
  sequenceNumber/stopType/siteId/locationId/snapshotName/scheduledAt/status and is
  the first nonterminal executable Stop in sequence order, never a GPS assertion;
- `attentionItems`: type/tripId for the displayed Trips only. INCOMPLETE_ASSIGNMENT
  means PLANNED with Driver or Tractor missing; CANCELLING means that current state.

Both lists use createdAt DESC/id DESC. One bounded SQL Trip projection uses a
lateral first-Stop lookup; Orders uses a bounded ID query and one batch projection.
There are no event-history reads, replay, per-Trip detail calls or unbounded child
collections. Attention is bounded to the displayed preview, not an exhaustive Alert
service. Complete worklists remain available through their real keyset APIs.
`customerId` filters both sections. `driverId` and Trip `status` filter the Trip
section and its attention only: an unplanned Order has no assigned Driver/Trip
status. Unknown parameters, including ambiguous date filters, return validation
errors. Terminal Trip filters legitimately produce no active Trips.

Web routes: `/operations` Dashboard, `/operations/orders` planning queue,
`/operations/trips` paginated list, `/operations/trips/new` planning wizard and
`/operations/trips/:tripId` detail. Firebase identity and `/me` are reused. ACTIVE
Tenant Admin/Transport Operations memberships select the UX workspace only;
every API checks current PostgreSQL authority. Multiple Tenants require explicit
selection. Operations keys include Tenant and filters/IDs; switching cancels and
removes Operations queries and resets forms, while sign-out/identity changes clear
all authenticated cache. No Tenant data or role authority is stored in localStorage.

The queue defaults to unplanned=true and supports Customer/status/unplanned with
opaque nextCursor continuation. Trip list supports Customer/Driver/status. No
appointment/active/date filters are simulated. Master catalogs resolve human names
in Tenant-scoped query cache; Site Locations are loaded for the exact selected Site.
Unavailable names remain clearly unavailable rather than guessed. Dashboard reads
no history; assignment history and merged operational timeline load only in detail.

Planning has Orders, Stops, Resources and Review sections. Selected Order details
supply requested destinations/cargo/references. Each requested Stop becomes its own
draft, never merged by text/Site/address. Ad-hoc snapshots remain exactly frozen.
A requested Stop type outside the executable catalog requires explicit selection;
no operational default is invented. Draft Stops can be reordered with buttons,
removed, associated explicitly with selected Orders, or added with explicit Site/
Location selection. BASE_RETURN is only added explicitly. Coverage/unique sequences/
Instant syntax are validated for UX, then validated again by the backend. Appointments
are entered as ISO 8601 with an explicit offset/Z, without a hidden local timezone.
Active Driver/Tractor and active IN_SERVICE Trailer selectors produce a planning
preview; POST remains authoritative. No request contains orderStopId. Trailer is
optional and does not imply physical coupling. Successful create opens Trip detail.
A 5xx/lost create result preserves the draft and disables repeat submit: reconcile
through Trip reads, never blind POST retry or an invented Idempotency-Key.

State-aware controls call only existing commands: Driver/Tractor assignment on
nonterminal Trips, ordinary Trailer planning under #35's initial/replacement rules,
manual AUTHORIZE/REJECT substitution, future-Stop reroute on IN_TRANSIT/AT_STOP,
and pre-movement/active cancellation. Responses replace cached detail and invalidate
timeline/Trips/board; create also invalidates Orders. No optimistic state transition,
manual version increment or automatic mutation retry. STALE_VERSION blocks the
command and requires explicit **Reload latest** and review. Invalid transitions and
uncertain infrastructure failures likewise offer read reconciliation. Problem UX
retains traceId, safe messages and useful field errors without raw SQL/stack/IDs.

Substitution is a **manual dispatcher decision for a known discrepancy**, not a
fabricated pending Driver scan request. It sends the current expected Trailer guard,
requested Trailer, AUTHORIZE/REJECT and reason, without invented expectedVersion.
REJECT may select an inactive/OOS visible Trailer; AUTHORIZE requires eligible
planning equipment. Both require explicit confirmation. #44 will provide the actual
wrong-Trailer scan flow. No Hook/coupling is authorized by these Web controls.

Reroute preserves the current scheduledAt unless explicitly cleared. It displays
the previous destination and history warning, requires reason/instructions and
confirmation, and sends the current Trip version. Cancellation requires reason and
confirmation; pre-movement does not force resolution. Active cancellation requires
instructions and the backend resolution catalog: RETURN_TO_BASE/REROUTE require Site,
HOLD_CONTACT_DISPATCH hides/clears Site, OTHER_AUTHORIZED allows optional Site.
No generic Order/Trip/Stop status editor or physical execution control exists.

Deferred: #38+ Hook/Drop/coupling/movement; #41 full operational Trailer search/detail/
timeline; #44 Driver execution/real scan discrepancy; #45 POD/Fuel/exceptions;
#55/#56 outbox/push/Alerts; #58+ Customer Portal; #61 generic Audit. Maps/GPS/route
optimization, production mock records, browser-only state changes and demo state
bypass buttons are absent. Demo active states must already exist in an authorized
backend/demo environment; Web cannot manufacture execution facts.
