# Customer Dispatcher Web — UX Flow v0.1

**Status:** Accepted v0.1  
**Primary actor:** CUSTOMER_DISPATCHER  
**Scope:** One or more explicitly assigned Customers inside a Tenant  
**Related requirements:** FR-030–045, FR-140–155, FR-184–197  
**Related state machines:** Order, Stop, Trip, Trailer

## Goal

Allow customer/manufacturer dispatch users to participate directly in logistics without exposing unrelated Tenant/customer data.

For the initial design-partner workflow, customer-side dispatch can:

- create Orders;
- define destinations/stops;
- assign/designate Trailer where allowed;
- participate in Driver assignment;
- cancel Orders;
- authorize alternate Trailer;
- participate in destination changes;
- participate in Order completion;
- view customer-scoped Trailer inventory and delivery evidence.

---

# 1. Scope banner / active Customer

The UI should clearly show which Customer scope the user is acting under.

Example:

```text
Customer: Hood Container
```

If a future user has multiple customer scopes, provide a scope switcher.

Switching scope changes visible data; it must not be only a frontend filter.

---

# 2. Main navigation

Recommended:

```text
Dashboard
Orders
Trailers
Deliveries
Documents
```

Optional:

`Alerts / Updates`

if customer-scoped notifications are enabled.

Do not show Tenant-wide Drivers, Customers, Users or unrelated operational data.

---

# 3. Customer dashboard

## Required information

### Pending / upcoming Orders

Show:

- Order number;
- appointment;
- destination;
- planning/execution state.

### Active deliveries

Show:

- current Trip/Stop state;
- assigned Trailer;
- Driver name only if allowed by product policy;
- latest operational event relevant to customer.

### Trailer inventory

Summary:

- designated Trailers;
- at Yard;
- at Dock;
- in transit;
- at Customer/final destination;
- Location Unknown;
- OOS where appropriate.

### Recent delivery evidence

Quick links to latest BOL/POD.

---

# 4. Create Order

## Step 1 — Order information

Required/available fields:

- Order number;
- PO;
- shipment number;
- BOL reference if available;
- product description;
- quantity;
- pallet count;
- weight.

## Step 2 — Stops / destinations

Define:

- origin;
- destination(s);
- appointment/date;
- service type where known.

Support multiple Stops when the customer request requires it.

## Step 3 — Trailer

For the design-partner workflow, customer-side dispatch may assign/select the Trailer.

Trailer selector shows only authorized/customer-relevant equipment.

Show:

- Trailer number;
- location;
- load/serviceability warnings.

Do not expose unrelated customer Trailer inventory.

## Step 4 — Review and create

Summary:

- Order;
- cargo;
- Stops;
- appointment;
- Trailer if assigned.

Primary action:

`Create Order`

Result:

- Order enters the Tenant operational queue automatically;
- no second "request approval" step is required.

---

# 5. Orders list

Filters:

- state;
- appointment/date;
- destination;
- active vs delivered/completed.

Rows show:

- Order;
- appointment;
- destination;
- Trailer;
- state;
- delivery progress.

Actions are state-aware.

---

# 6. Order detail

## Overview

Show:

- Order number;
- PO/BOL;
- cargo;
- state;
- appointment;
- Trailer;
- linked delivery Stops.

## Delivery progress

Show each Stop:

- destination;
- appointment;
- state;
- arrival/departure when available;
- POD availability.

## Documents

Show authorized BOL/POD evidence.

## History

Customer-relevant history only:

- create;
- edit;
- cancellation;
- reroute;
- Trailer substitution;
- completion.

Do not expose internal-only Tenant audit details.

---

# 7. Edit Order before departure

Before physical execution starts, allow ordinary edits where business policy permits.

Examples:

- appointment;
- destination;
- cargo/reference corrections;
- planned Trailer.

Primary action:

`Save Changes`

Each material change is auditable.

---

# 8. Change destination / reroute after departure

Once execution has begun, ordinary edit is no longer shown.

Use:

`Request / Confirm Destination Change`

depending on capability.

Form:

- current destination;
- new destination;
- reason/comment.

Show warning:

```text
The Driver has already started this work.
This change will create an operational reroute and will be recorded in history.
```

Result:

- original instruction preserved;
- Transport Operations/Driver updated;
- Trip remains in its current physical lifecycle state.

---

# 9. Cancel Order

## Before departure

Ordinary cancellation allowed if policy permits.

Require:

- confirmation;
- optional/required reason depending final policy.

## After departure

Use explicit active-cancellation flow.

Show:

- current Trip;
- Trailer;
- completed delivery work;
- warning that Driver instructions are required.

Primary action:

`Start Active Cancellation`

The Order must not disappear.

---

# 10. Alternate Trailer authorization

When Driver/Operations report a mismatch and customer-side authorization is required:

Show:

```text
Trailer change requested

Assigned: T114
Requested: T116
Trip: ...
Driver: ...
```

Actions:

- `Authorize replacement`
- `Reject`

Authorization is customer-scoped and audited.

---

# 11. Driver assignment participation

The design partner indicated Driver assignment may be shared.

For MVP, customer-side participation should be modeled conservatively:

- show assigned Driver when permitted;
- permit assignment/selection only if the role/capability mapping explicitly enables it for that Tenant;
- otherwise show "Driver assigned by Transport Operations".

This avoids hard-coding one design partner's shared-responsibility pattern into every future Tenant.

---

# 12. Trailer inventory

## List

Show only customer-scoped/designated Trailer inventory.

Fields:

- Trailer number;
- current location;
- load state;
- coupling/serviceability summary;
- last seen;
- current Order/manifest summary where permitted.

Filters:

- location;
- loaded/empty;
- in transit;
- yard/dock;
- OOS;
- Location Unknown.

## Detail

Show:

- current location;
- last known location;
- last seen;
- current content/Orders;
- delivery status;
- recent customer-relevant movement history.

Customer Dispatcher should not have unrestricted correction capability unless explicitly granted.

---

# 13. Deliveries

A customer-focused delivery view should show:

- Order;
- Stop/destination;
- appointment;
- state;
- arrival/departure;
- Trailer;
- POD status.

This view is optimized around customer obligations rather than Tenant dispatch internals.

---

# 14. Documents

Filter/list:

- BOL;
- POD;
- delivery photos where authorized.

Documents must be customer-scoped server-side.

Open/download access requires backend authorization before signed URL issuance.

---

# 15. Completion

When all delivery obligations are satisfied, customer-side authorized users may participate in Order completion.

UI should distinguish:

`Delivered`

from:

`Completed / Closed`

Do not let customer close an Order with unfinished required deliveries.

---

# 16. Cross-customer protection UX

If a user somehow requests a resource belonging to another Customer:

- do not expose the other Customer name/details;
- return a generic unavailable/not-authorized result.

Frontend filtering is not the security boundary.

---

# 17. MVP exclusions

Do not expose:

- Tenant-wide users;
- unrelated Customers;
- all Drivers;
- all Tractors;
- internal audit history;
- payroll;
- billing;
- maintenance.

---

# 18. Acceptance criteria for wireframing

Ready for wireframes when:

1. Customer Dispatcher can create an Order directly into operations.
2. Multi-Stop and cargo information are supported.
3. Trailer assignment/selecting is customer-scoped.
4. Before-departure edits differ clearly from after-departure reroute/cancellation.
5. Customer can see only its authorized Orders/Trailers/Documents.
6. Alternate Trailer authorization has an explicit flow.
7. Delivered and Completed are separate.
8. Shared Driver assignment remains capability/configuration-driven rather than hard-coded.

# 19. Implemented Customer workflows — issue #59

The Orders navigation is functional: explicit Customer-scoped status/planning
filters, keyset pages, real detail and Information/Cargo/Requested Stops/Review
creation. Stops can be added, removed and reordered. Normalized choices use
associated active Sites/Customer Locations; accepted ad-hoc snapshots remain
historical requests. Optional Trailer selection is scoped and planning-only.

Dashboard shows real five-row Order previews, clearly not totals. Pending/planned/
active/partial/delivered/cancelling views link to Orders; recent requests are ordered
by creation. Customer inventory, global deliveries and Documents remain #60.
No Driver name or private shared Trip route is displayed by this implementation.

Detail separates requested itinerary, mapped operational Stop milestones and recent
specific request decisions. OPEN unallocated cargo/destinations can be replaced;
PLANNED allows reference edits only. After execution starts, destination changes use
real operational reroute. Cancellation shows explicit consequences and reason;
active cancellation includes resolution instructions and remains visibly CANCELLING.
Cancelling one Order never means cancelling the shared Trip. Alternate-Trailer
AUTHORIZE/REJECT is a separate reviewed planning decision, not physical Hook.
DELIVERED and administrative COMPLETED remain separate; only the latter is a
Customer completion action after the server reports DELIVERED.

Forms require renewed review after changes. Double sends and automatic mutation
retries are blocked. Uncertain outcomes retain the same-context in-memory draft and
offer authorized read reconciliation. Drafts survive navigation/access refresh,
but not account/Customer/Tenant switch or browser reload. Old asynchronous results
cannot update the new scope. Shared semantic UI/dialogs, keyboard focus and table
overflow are reused; no new framework. Automated browser tests use jsdom; physical
desktop/tablet and production session testing remain release work under #65.

[Architecture, bounded API/V28 changes, acceptance and evidence](../web/issue-59-customer-orders-workflows.md).
