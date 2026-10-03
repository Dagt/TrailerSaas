# Domain Model

**Status:** Draft conceptual model. Not a database schema.

## Core principle

Do **not** model the current spreadsheet's single “status” column as one enum.

Discovery mixes at least four distinct dimensions:

1. load state;
2. operational state;
3. location;
4. usage/purpose.

These must remain separate.

## Tenant

Represents one subscribing transportation company.

Key relationships:

- has users;
- has drivers;
- has tractors;
- has trailers;
- has customers;
- has locations;
- has jobs/orders;
- owns configuration and audit scope.

## Customer

A company served by the tenant.

A customer may:

- own or be assigned trailer inventory;
- have multiple customer users;
- have multiple locations;
- request jobs;
- view only authorized operational data.

## Location

A known operational place.

Candidate types:

- BASE
- YARD
- CUSTOMER_SITE
- SHOP
- OTHER

Candidate fields:

- name;
- address;
- timezone;
- latitude/longitude;
- geofence radius;
- active flag;
- tenant/customer relationship.

Exact dock tracking is not an MVP priority, but optional dock metadata may exist later.

## Driver

Operational person executing work.

Known fields:

- name;
- license number;
- medical certificate expiration;
- active status.

Possible later fields require additional discovery.

## Tractor

Powered vehicle used for movement.

Known requirements:

- unique tenant-scoped number;
- relationship to movement/job;
- optional current/default driver assignment.

Continuous tractor GPS is not required in the initial product model.

## Trailer

Primary tracked asset.

Known fields/concepts:

- unique identifier within tenant;
- designated customer if applicable;
- type/size;
- heavy-load capability or other restrictions;
- current operational status;
- current load state;
- current location;
- out-of-service/damage state;
- current/last associated order or content where meaningful;
- QR identity.

### Trailer load state

Candidate values:

- EMPTY
- LOADED
- UNKNOWN

### Trailer operational state

Candidate values:

- AVAILABLE
- ASSIGNED
- LOADING
- IN_TRANSIT
- AT_CUSTOMER
- UNLOADING
- OUT_OF_SERVICE

Exact state machine must be validated before implementation.

### Trailer usage/purpose

Candidate values:

- DELIVERY
- SHUTTLE
- WIP_STORAGE
- TOOLING_STORAGE
- OTHER

Purpose should not overwrite load state or location.

## Order

Represents the commercial/customer request and identifiers received from shipping.

Candidate fields:

- order number;
- PO;
- shipment number;
- customer;
- requested delivery date/time;
- references/documents.

One order may need one or more operational jobs/movements. Cardinality must be validated.

## Job / Movement

Operational unit assigned to a driver.

Candidate relationships:

- tenant;
- order;
- driver;
- tractor;
- origin;
- destination;
- appointment;
- trailer assignment(s);
- status.

Candidate status:

- CREATED
- ASSIGNED
- STARTED
- AT_ORIGIN
- IN_TRANSIT
- AT_DESTINATION
- COMPLETED
- CANCELLED
- EXCEPTION

The final state machine remains open.

## Trailer Movement Event

Immutable/auditable fact about a trailer movement.

Examples:

- HOOKED
- DROPPED
- ARRIVED_LOCATION
- DEPARTED_LOCATION
- MOVED_WITHIN_YARD
- MARKED_OOS
- RETURNED_TO_SERVICE
- LOCATION_CORRECTED

Candidate metadata:

- trailer;
- job;
- driver;
- tractor;
- location;
- timestamp;
- source (driver, dispatcher, geofence, yard check, integration);
- actor;
- evidence;
- correction relationship.

## Shift

Represents driver shift start/end if included in MVP.

It should not become a payroll engine.

## Document / Evidence

Examples:

- BOL;
- signed BOL;
- POD;
- trailer damage photo;
- delivery photo.

Needs storage metadata, uploader, tenant scope and entity relationship.

## Yard Check

Represents an inventory reconciliation session for a location.

Contains:

- expected trailer set/snapshot;
- scanned/found trailers;
- missing trailers;
- unexpected trailers;
- actor;
- start/end timestamps.

## Alert

Represents operational attention required.

Initial candidate:

- trailer inactive/no movement for configured period.

Future alerts may include appointment risk, mismatch or yard reconciliation exceptions.

## Audit Event

Tracks meaningful administrative changes that are not already represented as immutable operational events.

Examples:

- user changed customer assignment;
- dispatcher corrected location;
- job reassigned;
- master-data change.

## Conceptual relationship sketch

```text
Tenant
 ├── Users
 ├── Drivers
 ├── Tractors
 ├── Trailers
 ├── Customers
 │    ├── CustomerUsers
 │    └── Locations
 ├── Orders
 │    └── Jobs/Movements
 │         ├── Driver
 │         ├── Tractor
 │         ├── Origin/Destination
 │         ├── Trailer Movement Events
 │         └── Documents/Evidence
 ├── YardChecks
 ├── Alerts
 └── AuditEvents
```

## Important unresolved modeling questions

- Can one job contain multiple customer stops?
- Can one order produce multiple jobs?
- Can a job change trailers more than once?
- How should live-load differ from drop-and-hook in the state machine?
- How should in-yard moves relate to jobs?
- Is “content” structured line-item data or free-form/order metadata?
- Are trailer identifiers unique per tenant or only unique per customer prefix?
