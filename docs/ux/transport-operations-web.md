# Transport Operations Web — UX Flow v0.1

**Status:** Accepted v0.1  
**Primary actor:** TRANSPORT_OPERATIONS  
**Related requirements:** FR-030–045, FR-090–103, FR-150–164, FR-170–174, FR-195–197  
**Related state machines:** Order, Trip, Stop, Trailer, Alert

## Goal

Give transportation operations/dispatch a single workspace to:

- see pending and active work;
- convert Orders into executable Trips;
- assign Driver/Tractor/Trailer;
- monitor Trip/Stop state;
- handle trailer mismatch, reroute and cancellation exceptions;
- search trailers;
- react to alerts.

The MVP should prioritize operational clarity over complex analytics.

---

# 1. Main navigation

Proposed primary navigation:

```text
Dashboard
Orders
Trips
Trailers
Drivers
Yards
Alerts
Search
```

Admin-only configuration remains outside this role unless separately granted.

---

# 2. Dashboard

## Purpose

Answer the first operational questions immediately:

- what Orders are pending?
- what Trips are active?
- what deliveries are not completed?
- what trailers need attention?

## Required sections

### Pending Orders

Show at least:

- Order number;
- Customer;
- appointment/date;
- origin/destination summary;
- planning state;
- whether a Trip exists.

Primary action:

`Plan Trip`

### Active Trips

Show:

- Trip reference;
- Driver;
- Tractor;
- current Trailer if known;
- current/next Stop;
- Trip state;
- appointment urgency;
- exception indicator.

### Attention / Alerts

Show:

- missing trailers;
- stale trailers;
- active reroutes/cancellations;
- operational exceptions requiring action.

### Trailer quick search

Search by operational number.

---

# 3. Orders list

## Filters

Required/proposed:

- Customer;
- state;
- appointment/date;
- unplanned only;
- active/in-progress;
- delivered/not completed.

## Row/card information

- Order number;
- Customer;
- PO/BOL reference when present;
- appointment;
- origin/destination;
- Order state;
- linked Trip count;
- manifest/load summary.

## Actions

State-dependent:

- View;
- Plan fulfillment;
- Edit where still legal;
- Cancel;
- Open linked Trip(s).

Do not present a generic editable status dropdown.

---

# 4. Order detail

## Header

Show:

- Order number;
- Customer;
- state;
- appointment;
- created by;
- created timestamp.

## Sections

### Cargo

- description;
- quantity;
- pallet count;
- weight;
- PO;
- BOL reference.

### Stops / destinations

Show requested operational destinations and appointments.

### Fulfillment

Show:

- linked Trip(s);
- Stops serving this Order;
- Trailer/load allocations;
- delivery progress.

### Evidence

Show available BOL/POD.

### History

Show:

- relevant edits;
- cancellation;
- reroutes;
- completion;
- actor/time.

## Primary actions

### Before departure

Depending on capability:

- Edit;
- Plan / attach to Trip;
- Cancel.

### After departure

Do not expose ordinary destructive edit.

Use explicit actions such as:

- `Reroute / Change destination`
- `Cancel active Order`

These open controlled exception flows.

---

# 5. Plan Trip flow

## Entry points

- from Pending Order;
- from Orders multi-select;
- from Trips → New Trip.

## Step 1 — Select Orders

Allow one or more compatible Orders.

Display:

- Customer;
- Order;
- destinations;
- appointment;
- cargo summary.

The UI should warn if selection appears operationally incompatible but backend/domain rules remain authoritative.

## Step 2 — Build Stops

Dispatcher sees an ordered Stop list.

Each Stop includes:

- site/location;
- service type;
- appointment;
- related Orders.

Proposed interactions:

- add Stop;
- remove future Stop;
- reorder future Stops before execution;
- define return-to-base Stop when required.

## Step 3 — Assign resources

Required:

- Driver;
- Tractor;
- Trailer when known.

Trailer may remain unresolved if the real workflow determines it at pickup.

Display availability/attention information without pretending that availability is a simple editable flag.

## Step 4 — Review

Summary:

- Orders;
- Stops;
- Driver;
- Tractor;
- Trailer if assigned;
- appointments;
- obvious conflicts/warnings.

Primary action:

`Assign Trip`

## Result

Trip becomes `ASSIGNED` once assignment invariants are met.

Driver receives the work in the mobile app.

---

# 6. Trip list / dispatch board

The MVP can begin with a data-table/board hybrid rather than a map.

## Suggested columns

- Trip;
- Driver;
- Tractor;
- Trailer;
- state;
- current Stop;
- next Stop;
- next appointment;
- last event time;
- attention/exception.

## Filters

- active;
- assigned/not started;
- in transit;
- at stop;
- cancelling;
- completed;
- Driver;
- Customer;
- date.

## Visual priority

Late/attention conditions should be visible, but exact color system belongs to visual design.

---

# 7. Trip detail

## Header

- Trip state;
- Driver;
- Tractor;
- current Trailer;
- current Stop;
- next appointment.

## Timeline

Show chronological operational timeline:

```text
Assigned
Pre-Trip complete
Trailer hooked
Departed base
Arrived Stop 1
Service started
POD captured
Departed Stop 1
...
```

This is preferable to exposing raw audit records as the primary operational UI.

## Stops

For each Stop:

- sequence;
- site;
- service type;
- state;
- appointment;
- arrival/departure;
- linked Orders;
- evidence.

## Resource assignment

Show current and previous:

- Driver;
- Tractor;
- Trailer segments.

## Allowed operations

State/capability dependent:

- reassign Driver;
- reassign Tractor;
- authorize different Trailer;
- reroute;
- start cancellation;
- view evidence/history.

---

# 8. Wrong-trailer authorization flow

## Trigger

Driver scans a Trailer different from expected/assigned.

## Operations UI

Create a clear attention item:

```text
Trailer substitution requested

Trip: ...
Driver: ...
Expected: T114
Scanned: T116
Location: ...
```

Actions:

- `Authorize T116`
- `Reject / Keep original assignment`

## Authorization result

If approved:

- assignment/coupling may proceed;
- original vs replacement is audited;
- Driver is notified/updated.

If rejected:

- Driver cannot silently continue with the alternate Trailer.

---

# 9. Reroute / destination-change flow

## Entry

From active Order or Trip.

## Form

Required:

- current destination;
- new destination;
- affected Stop(s);
- reason/comment.

Show a warning that Driver instructions will change.

Primary action:

`Confirm reroute`

## Result

- original destination retained in history;
- future Stop plan updated;
- Driver sees new instruction;
- Trip remains physically `IN_TRANSIT` or `AT_STOP` as appropriate.

---

# 10. Active cancellation flow

Do not use a destructive "Delete Order" pattern.

## Form

Show:

- active Trip;
- Driver;
- current Trailer;
- current location/state;
- completed Stops/deliveries.

Require/select resulting operational instruction:

- return to base;
- reroute;
- other authorized resolution.

Primary action:

`Start cancellation`

## Result

Order/Trip enters active cancellation behavior where applicable.

Completed history/POD remains intact.

---

# 11. Trailer search

## Search

By operational trailer number.

## Trailer detail

Show:

- operational number;
- designated Customer;
- current location state;
- current location;
- last known location;
- last seen;
- load state;
- coupling state;
- serviceability;
- current/last Driver;
- current Tractor when coupled;
- manifest/Orders;
- recent movement timeline;
- OOS/damage evidence.

## High-value actions

Capability dependent:

- mark OOS;
- correct location;
- reassign designated Customer (Admin);
- view Yard Check history.

---

# 12. Alerts

## List

Show:

- type;
- related asset/Trip;
- severity;
- created time;
- status.

## Alert detail

Actions:

- acknowledge;
- open related Trailer/Trip/Order;
- resolve where manual resolution is allowed.

Push notification is not the source of truth.

---

# 13. Error/validation UX

## State conflict

Example:

Another dispatcher changed the Trip before this user submitted.

Show:

```text
This Trip changed while you were viewing it.
Refresh to review the latest state before continuing.
```

Do not silently overwrite.

## Authorization

Do not show sensitive cross-customer/tenant details in error messages.

## Invalid transition

Explain the business reason:

```text
This Order can no longer be edited directly because the Driver has already departed.
Use Reroute or Active Cancellation instead.
```

---

# 14. MVP exclusions

Do not add to this role in MVP unless separately approved:

- route optimization;
- full GPS map tracking;
- accounting/invoicing;
- payroll;
- maintenance work orders;
- broad BI/report builder.

---

# 15. Acceptance criteria for UX flow

This flow is ready for wireframing when:

1. Operations can find pending Orders.
2. Operations can create/assign a multi-Stop Trip.
3. Operations can see active Trip state without using a full route map.
4. Wrong-Trailer requests have a deterministic authorization flow.
5. Reroute and active cancellation preserve history.
6. Trailer search exposes trustworthy current + last-known state.
7. Alerts are actionable.
8. No screen depends on arbitrary status-field editing.
