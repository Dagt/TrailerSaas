# Domain Model

**Status:** Accepted conceptual model v0.1 on 2026-10-04  
**Important:** This is not a database schema.

## Core principles

1. Do **not** copy the current spreadsheet as the domain model.
2. Do **not** assume `Order = Trip = Trailer = Delivery`.
3. Separate:
   - customer order;
   - driver trip/run;
   - stop;
   - trailer;
   - load/manifest;
   - movement events.
4. Keep trailer load state, operational state, physical location and usage/purpose independent.
5. Preserve operational history through events and auditable corrections.

## Tenant

One subscribing transportation company.

Owns or scopes:

- users;
- drivers;
- tractors;
- trailers;
- customers;
- sites/locations;
- orders;
- trips;
- documents;
- alerts;
- audit history.

## Customer

A manufacturing/company customer served by the tenant.

A customer may:

- have multiple users;
- have multiple sites;
- create orders/deliveries through customer-side dispatch users;
- have designated trailer inventory;
- view only its authorized data.

## User / Role

Candidate operational roles:

- Tenant Administrator
- Transport Operations / Shipping
- Customer Dispatcher / Shipping
- Yard / Spotter
- Driver
- External Recipient (usually no persistent account)

Authorization remains tenant-scoped and, for customer users, customer-scoped.

## Physical Site

Represents a real street-address-level facility.

Fields/concepts:

- name;
- address;
- timezone;
- coordinates;
- optional geofence.

A physical site is **not necessarily unique to one customer context**. Discovery confirmed that different customers may operate at the same street address with different dock ranges.

## Operational Location

Represents an operational sublocation within or associated with a physical site.

Candidate types:

- BASE
- YARD
- DOCK
- CUSTOMER_AREA
- SHOP
- OTHER
- UNKNOWN

Examples:

- Hood Container yard
- Dock 17
- Black Hawk Dock 5
- Stanfast yard

This separation allows one physical site to contain multiple customer/dock contexts.

## Driver

Known fields:

- name;
- license number;
- medical certificate expiration;
- active status.

Operational relationships:

- shifts;
- trips;
- inspections;
- trailer movement events.

## Shift

Represents driver operational duty window.

MVP requirement:

- on-duty timestamp;
- off-duty timestamp.

Not a payroll calculation object.

## Tractor

Known concepts:

- tenant-scoped operational number;
- optional/default driver association;
- tractor used for a trip/movement;
- odometer/mileage observations from pre/post-trip or fuel events.

Continuous tractor GPS is not required by TrailerSaaS MVP.

## Trailer

Primary tracked non-powered asset.

### Identity

The final design-partner clarification states that displayed trailer numbers do **not repeat within the current 2 Fast Logistics operation** and are assigned to customers.

TrailerSaaS must nevertheless avoid using a human-readable trailer number as the database identity because uniqueness is not guaranteed across all SaaS tenants.

Each Trailer therefore requires:

- internal immutable unique ID/UUID;
- displayed operational identifier;
- persistent designated customer association;
- QR identity that resolves to the internal trailer ID.

For the initial design partner, the displayed operational identifier can be treated as tenant-unique.

### Trailer attributes

- operational identifier;
- customer/designation where applicable;
- type/size;
- heavy-load/special capability;
- current load state;
- current operational state;
- current operational location;
- OOS/damage state;
- QR identity.

### Load state

Candidate values:

- EMPTY
- LOADED
- UNKNOWN

### Operational state

Candidate values:

- AVAILABLE
- ASSIGNED
- LOADING
- UNLOADING
- IN_TRANSIT
- AT_CUSTOMER
- IN_YARD
- AT_DOCK
- OUT_OF_SERVICE
- LOCATION_UNKNOWN

Final state machine still requires specification.

### Usage/purpose

Candidate values:

- DELIVERY
- SHUTTLE
- WIP_STORAGE
- TOOLING_STORAGE
- OTHER

Purpose must not replace load state or location.

## Order

Represents a customer shipping/commercial request.

Known information may include:

- order number;
- PO;
- shipment number;
- BOL;
- customer;
- appointment/date requirements;
- origin/destination references.

### Cardinality

Confirmed:

- one Order may require multiple trailers;
- one Trailer/load may carry multiple Orders;
- multiple Orders may belong to the same or different customers.

Therefore Order ↔ Trailer is **many-to-many through load/manifest allocation**, not a direct one-to-one relationship.

## Trip / Run

Represents the driver's operational execution unit.

A Trip may:

- use one driver at a time;
- use one tractor at a time, subject to reassignment/recovery;
- contain multiple stops;
- carry/serve multiple orders;
- change trailer during drop & hook;
- include a return to base.

Lifecycle concepts must be separate:

- a Delivery/Stop can be completed when service is finished and POD/BOL evidence is recorded;
- a Trip/Run can continue across multiple Stops and may include return to base;
- a Shift ends only when the driver marks Off Duty at the end of the workday.

Off Duty must never implicitly complete every delivery/trip in the shift.

### Candidate trip states

- PLANNED
- ASSIGNED
- PRE_TRIP
- ACTIVE
- IN_TRANSIT
- AT_STOP
- RETURNING_TO_BASE
- POST_TRIP
- COMPLETED
- CANCELLED
- EXCEPTION

## Stop

Represents a visit within a Trip.

Candidate stop types:

- BASE_DEPARTURE
- PICKUP
- DELIVERY
- LIVE_LOAD
- LIVE_UNLOAD
- DROP_HOOK
- FUEL
- BASE_RETURN
- OTHER

A Trip may contain multiple delivery stops.

Stop data can include:

- physical site;
- operational location/dock if known;
- scheduled appointment;
- arrival time;
- departure time;
- service type;
- related orders;
- evidence.

## Load Manifest / Order Allocation

Represents what a trailer is carrying for a segment of a trip.

Must support:

- one or more orders;
- PO;
- BOL;
- product description;
- quantity;
- pallet count;
- weight;
- trailer;
- trip/segment context.

A generic “units” field is not required by the design partner for MVP.

This prevents “contents” from becoming a single free-text field.

## Trailer Assignment / Coupling Segment

Conceptual relationship describing which trailer is coupled to which tractor/driver during a time interval.

It can be derived from HOOKED/DROPPED events but may also be maintained as current derived state for efficient queries.

## Trailer Movement Event

Immutable/auditable operational fact.

Candidate event types:

- HOOKED
- DROPPED
- ARRIVED_LOCATION
- DEPARTED_LOCATION
- MOVED_WITHIN_YARD
- LOCATION_CONFIRMED
- LOCATION_CORRECTED
- LOCATION_MARKED_UNKNOWN
- MARKED_OOS
- RETURNED_TO_SERVICE
- LOAD_STATE_CHANGED

Metadata:

- tenant;
- trailer;
- trip;
- stop;
- driver;
- tractor;
- location;
- occurrence timestamp;
- sync timestamp where relevant;
- actor/source;
- evidence;
- correction relationship.

## Inspection

Minimal MVP concept for driver pre/post-trip workflow.

Candidate fields:

- type: PRE_TRIP / POST_TRIP;
- driver;
- tractor;
- trip/shift;
- odometer/mileage;
- timestamp;
- notes/exceptions.

### Pre-Trip baseline

- odometer/mileage;
- front photo;
- rear photo;
- driver-side photo;
- passenger-side photo;
- structured checklist.

### Post-Trip baseline

- final odometer/mileage;
- damages;
- failures/problems;
- comments.

This is an operational inspection record, not a complete DOT compliance subsystem unless separately specified.

## Fuel Event

Optional driver action discovered in the ideal-day workflow.

MVP fields:

- driver;
- tractor;
- trip;
- odometer/mileage;
- receipt photo;
- timestamp.

Gallons, price-per-gallon and total paid are not required by the current design partner.

Full fuel management remains out of scope.

## Document / Evidence

Examples:

- BOL;
- signed BOL photo;
- POD photo;
- trailer damage photo;
- inspection photo.

Must preserve:

- tenant scope;
- related entity;
- uploader/source;
- timestamp.

Standalone digital signature is not required for MVP.

## Yard Check

Inventory reconciliation session for an operational yard/site.

Expected set is based on trailers currently recorded in:

- the yard; and/or
- associated docks.

Contains:

- expected snapshot;
- scanned/found trailers;
- missing trailers;
- unexpected trailers;
- actor;
- timestamps.

A discrepancy does not silently rewrite history.

## Alert

Initial candidate:

- trailer has not moved for configured threshold (discovery baseline: 3 days).

## Audit Event

Tracks meaningful administrative changes that are not already fully represented by immutable operational events.

Examples:

- destination changed;
- trailer assignment changed;
- location corrected;
- customer/master data changed;
- trip reassigned.

## Conceptual relationship sketch

```text
Tenant
 ├── Users / Roles
 ├── Drivers ── Shifts
 ├── Tractors
 ├── Trailers
 ├── Customers
 │    ├── Customer Users / Dispatchers
 │    └── Physical Sites
 │         └── Operational Locations / Docks / Yards
 ├── Orders
 │    └── Load Manifest / Order Allocations
 ├── Trips / Runs
 │    ├── Stops
 │    ├── Driver / Tractor
 │    ├── Trailer Movement Events
 │    ├── Inspections
 │    ├── Fuel Events
 │    └── Documents / Evidence
 ├── Yard Checks
 ├── Alerts
 └── Audit Events
```

## Remaining modeling questions

See `docs/product/open-questions.md`, especially:

- exact customer-order modification cutoff;
- exact cancellation state machine;
- customer trailer designation/ownership semantics;
- evidence rules by customer;
- retention/compliance policy;
- precise inspection/fuel fields.


## Customer designation of trailers

For the initial design partner:

- a trailer is designated to a customer;
- the designation remains in effect until Administration changes it;
- reassignment to another customer requires administrative authorization.

This designation is separate from current location, current load and current Order.

## Missing trailer state

When a Yard Check cannot locate an expected trailer:

- current location can become `LOCATION_UNKNOWN`;
- Administration receives an alert;
- Administration owns investigation;
- the system preserves:
  - last known location;
  - last seen timestamp;
  - last driver.

`LOCATION_UNKNOWN` must not erase last-known-location history.
