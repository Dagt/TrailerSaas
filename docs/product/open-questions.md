# Open Questions

**Status:** Active after second discovery round  
**Convention:** `OQ-###`

Most first-round blocking questions are now resolved. This file contains only issues still requiring product/technical clarification.

## Orders, trips and customer permissions

### OQ-031 — Customer edit/cancel cutoff — BLOCKING
Discovery says customer dispatcher can modify/cancel “before delivery.”

Which exact system state is the cutoff?

Possible interpretations:

- before driver accepts/starts;
- before departure;
- before arrival at destination;
- any time before POD/delivery completion.

### OQ-032 — Customer dispatcher vs transport dispatcher — BLOCKING
Which actions belong to manufacturing/customer dispatch versus transportation-company operations?

Specifically:

- who plans stop sequence?
- who assigns driver?
- who assigns tractor?
- who assigns trailer?
- who can change appointment/destination?
- who can correct trailer data?

### OQ-033 — Trip completion semantics
Discovery describes return to base as task completion.

Should system distinguish:

- **Delivery completed** at POD/service completion;
- **Trip completed** at return to base/Post-Trip?

This is the recommended interpretation but should be explicitly approved.

### OQ-034 — Cancellation after trip start
Current behavior is “contact shipping.”

Define:

- who authorizes cancellation;
- resulting Trip/Order status;
- what happens to active trailer/load;
- whether return-to-base is automatically created.

## Manifest / cargo

### OQ-035 — Detailed cargo fields
Confirmed minimum includes Order, PO, BOL, weight and multiple orders.

Are any of these required in MVP?

- product description;
- quantity;
- pallet count;
- units;
- hazardous/material classification.

## Driver inspections and fuel

### OQ-036 — Pre/Post-trip detail — BLOCKING for UX
The ideal workflow requires Pre-Trip/Post-Trip, truck photos and mileage.

Define:

- required photo count/types;
- whether checklist items are needed;
- whether defects must create an exception;
- whether Post-Trip also requires photos/mileage.

### OQ-037 — Fuel event fields
The ideal workflow includes optional fuel logging.

Clarify whether “total de la carga” means:

- gallons/liters;
- dollar total;
- both;
- receipt photo;
- other.

## Delivery evidence

### OQ-038 — No receiver available
When nobody is available to sign BOL:

- what evidence is mandatory?
- must driver select a reason?
- is an unsigned BOL photo enough?
- should dispatch be alerted?

### OQ-039 — Customer-specific POD rules
Discovery says requirements may vary by customer.

Does MVP need configurable evidence rules per customer, or can MVP use one standard rule plus exception?

## Trailer/customer relationship

### OQ-040 — Trailer designation/ownership semantics — BLOCKING
Discovery says trailers are company-controlled/mostly leased and often designated to customers.

Is customer assignment:

- permanent until manually changed;
- temporary allocation;
- inferred from current order;
- separate “home customer” plus temporary operational assignment?

This affects inventory scoping.

### OQ-041 — Duplicate visible trailer IDs
Displayed numbers are not globally unique.

What exact namespace disambiguates them operationally?

Examples:

- customer + trailer number;
- fleet prefix + trailer number;
- ownership group + number.

QR will remain internally unique regardless.

## Yard and locations

### OQ-042 — Shared address / customer dock context
Stanfast and Black Hawk share 710 Kimberly Dr but have separate dock ranges.

Should one PhysicalSite contain:

- multiple customer OperationalAreas; and
- docks assigned to each area?

Recommended: yes, but confirm operational expectations.

### OQ-043 — Location Unknown workflow
When Yard Check misses a trailer and it becomes LOCATION_UNKNOWN:

- should an alert be created?
- who resolves it?
- does prior recorded location remain visible as “last known location”?

### OQ-044 — Return-to-yard inference
When driver marks arrival back at base, should the currently hooked trailer automatically be located in the base yard, or should driver/yard user confirm the exact yard/dock?

## OOS

### OQ-045 — Return to service
MVP damage flow is OOS + photos/comment.

Who is allowed to change OOS → available?

- admin;
- shipping;
- yard spotter;
- maintenance/supervisor role.

## Retention / compliance

### OQ-046 — Production retention policy — BLOCKING before production
Design partner wants:

- movements: 1 week;
- yard checks: 1 week;
- Orders/BOL/photos/deliveries: 1 month.

Before implementing destructive deletion, validate U.S. contractual/legal/audit needs and whether product retention should be longer/configurable.

### OQ-047 — Timekeeping reliance
On/Off Duty is requested, without payroll calculation.

Will these timestamps be:

- operational only; or
- used as official payroll/time records?

If official, correction/approval and compliance requirements must be defined.

## Configurability and commercial

### OQ-048 — Tenant/customer configurability
Which behaviors need configuration in MVP?

Candidates:

- POD evidence requirements;
- stale-trailer threshold;
- retention;
- allowed customer edits;
- inspection fields;
- alert recipients.

### OQ-049 — Initial SaaS billing metric
Should pricing primarily depend on:

- trailer count;
- driver count;
- movements;
- locations;
- plan tier;
- hybrid model.

This does not block core implementation but affects entitlements.

## Resolved in second discovery round

The following first-round topics are considered resolved enough for product design:

- many-to-many Order/Trailer relationships;
- multi-stop trips;
- Drop & Hook semantics;
- Live Load/Unload semantics;
- wrong-trailer requires dispatch authorization;
- visible trailer numbers are not globally unique;
- continuous route tracking not required;
- arrival can be geofence-assisted/confirmed;
- departure is manual;
- always-on personal-phone location should not be required;
- customer dispatcher can create Orders directly;
- mid-task driver changes are dispatch-controlled exceptions;
- signed BOL photo is sufficient primary POD;
- standalone digital signature is not required;
- Yard Check expected inventory comes from recorded yard/dock state;
- discrepancy correction requires warning/confirmation;
- driver does not have unrestricted location correction;
- shift tracking is On/Off Duty only;
- no wage calculation in MVP;
- Samsara integration is not an MVP dependency;
- Amtech integration is not an MVP dependency;
- dispatcher authorizes destination changes;
- destination changes are auditable;
- OOS MVP is flag + photos/comment;
- Road Mode and Yard/Spotter Mode require distinct workflows.
