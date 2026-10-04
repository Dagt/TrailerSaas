# Design Partner Baseline

**Status:** Confirmed from discovery questionnaire  
**Discovery date:** 2026-10-03  
**Purpose:** Describe the initial real-world operation used to design the product. This is not the same as the generic SaaS tenant model.

## Operating profile

The initial design partner is **2 Fast Logistics Inc**, a U.S. trucking / transportation operation serving manufacturing and packaging-related customers. The supplied company logo identifies the business as “2 Fast Logistics Inc.”

### Current scale

- More than **100 movements per day**
- Approximately **35 drivers**
- Approximately **33 tractors**
- Approximately **150 trailers / boxes**
- Operations in **Illinois, Wisconsin, Indiana and Michigan**
- More than **200 recurring or known destinations**
- Three customer facilities function as operational bases / yards

### Typical cargo

- Finished cardboard boxes in packs
- Corrugated cardboard sheets
- Paper rolls
- Machine parts / spare parts
- Supplies used by customer companies

### Known customer examples

- Stanfast
- Hood Container
- Black Hawk
- HRCA

These names belong to the current discovery context and should not be hard-coded into the SaaS.

## Current request and dispatch process

Orders arrive through a mixture of:

- email;
- shipping documents;
- phone messages;
- in-person communication.

Known identifiers include:

- shipment number;
- order number;
- PO;
- BOL.

Shipping personnel choose the driver. Tractors are commonly associated with drivers but can be changed. Trailer selection depends on availability and may not be known until the driver receives the BOL.

Assignments may change after dispatch. Shipping personnel or dispatchers can modify them.

Both scheduled and urgent jobs exist. Appointment time is the primary operational priority.

## Typical trip example

A representative trip:

1. Starts in Chicago.
2. Driver uses tractor 270.
3. Trailer T114 is used.
4. Driver receives the BOL.
5. Driver plans route.
6. Driver performs tractor pre-trip inspection.
7. Driver hooks the trailer.
8. Driver inspects tires, lights and trailer condition.
9. Driver verifies cargo against BOL.
10. Driver reports arrival and travel updates / delays.
11. Driver travels to Grand Rapids, Michigan.
12. Driver checks in with destination shipping/receiving.
13. Destination assigns a dock or gives drop/hook instructions.
14. Loaded trailer may be left at customer.
15. Driver may pick up an empty trailer.
16. Completion is currently reported by message to a group.
17. Signed BOL remains as evidence.

A live load is also possible.

## Current trailer control problem

Trailer location is not reliably controlled.

Known issues:

- Current physical location can disagree with recorded location.
- This occurs often.
- If a trailer is missing, staff search documents and/or GPS information.
- The company does not currently maintain a reliable tractor ↔ trailer relationship.
- Hook/drop events are not systematically recorded.
- Trailer dwell time at a customer is not known reliably.
- Trailers can remain at customers for days or weeks.
- Staff want alerts when a trailer has not moved for roughly three days.
- Trailer damage and out-of-service state need to be recorded.
- Photos are useful evidence.

## Trailer identifiers and types

Identifiers are unique within the relevant operating context.

Examples of naming conventions:

- Stanfast: `T#`
- Black Hawk: `BH T#`
- Hood Container: `HC T#`
- HRCA: box truck rather than standard trailer pattern

Known trailer types:

- 53' dry van
- 48' dry van
- Two low-profile 53' dry vans

Some trailers are intended for heavier loads such as paper rolls.

## Current spreadsheet

The Google Sheet is maintained by approximately seven people including yard spotters, shipping/receiving and administrators.

It currently captures roughly:

- trailer number;
- location;
- state/status;
- order;
- date;
- checked flag.

Known problems:

- missing updates;
- incorrect data;
- old values are overwritten;
- no reliable attribution of who changed a value;
- no reliable timestamped movement history.

Current spreadsheet labels mix several concepts:

- MT = empty
- FULL = loaded
- DROP = left at customer
- SHUTTLE = transfer
- DELIVERY = order ready for delivery
- LOADING = loading in progress
- TOOLING STORAGE = machine-tool/material storage
- WIP STORAGE = work-in-process storage

The future domain model should separate these concerns instead of copying this single status field.

## Yard check

- Performed daily at least in one known operation.
- May require physically walking the yard.
- Takes approximately one hour.
- Used to verify trailer/product presence.
- If a trailer is missing, staff investigate documents.
- Incorrect spreadsheet positions are manually corrected.
- Approximately 15 days of yard-check history was suggested during discovery.
- QR scanning was considered useful.
- Automatic missing/unexpected trailer reconciliation was explicitly considered useful.

## Drivers

- Work in shifts.
- A driver can perform multiple movements per shift.
- Another driver may continue work started by someone else.
- Reporting is currently performed by message.
- Most common omission: forgetting arrival/departure updates.
- All have smartphones.
- Both iOS and Android must be supported.
- Company phones are not generally provided.
- Temporary offline operation was requested even though bad-signal locations are not considered common.
- A simple hook/drop action is considered operationally realistic.

Driver record needs identified:

- name;
- license number;
- medical certificate expiration.

## Existing systems

- Some tractors use **Samsara**.
- Some customer/company processes use **Amtech**.
- These systems should be treated as possible future integrations, not copied or tightly coupled into the MVP without further discovery.

## Customer interaction

Primary customers need system access.

Expected customer capabilities include:

- view their assigned trailer inventory;
- view location;
- create/request movements;
- view only their own trailers/jobs;
- support multiple contacts/users.

Third-party final delivery recipients generally do **not** need permanent accounts.

Delivery evidence may include:

- BOL;
- signed BOL;
- photos;
- proof of delivery.

Whether the product needs an independent digital signature feature remains unresolved.

## Success criteria expressed during discovery

Top desired outcomes:

1. reliable trailer location;
2. current delivery status;
3. trailer contents;
4. more automated logistics;
5. real inventory data.

The most expensive stated operational problem is lost/misplaced orders. Staff may spend roughly **1–2 hours** searching for trailers or verifying information.

A later-use case is shift entry/exit time for payroll, but full payroll is not established as an MVP requirement.


## Follow-up discovery additions — 2026-10-03

The second discovery round established several additional facts that supersede earlier ambiguity:

- Orders, trips and trailers do not have a one-to-one relationship.
- Multiple orders can travel in one trailer, and one order can require multiple trailers.
- One trip can contain multiple delivery stops.
- Displayed trailer numbers are not globally unique across the operation.
- Wrong-trailer substitution requires dispatcher authorization.
- Continuous route history is not needed; arrival/departure events are sufficient.
- Arrival may be geofence-assisted and driver-confirmed; departure is manual.
- Always-on background location on drivers' personal phones should not be an MVP dependency.
- Customer/manufacturer dispatch users can create orders directly into the operational queue.
- Signed BOL photo/file is the primary proof of delivery when a signature is available.
- The ideal Road Mode includes On Duty, Pre-Trip, tractor photos/mileage, assigned work, QR trailer confirmation, trip execution, BOL evidence, return to base, Post-Trip and Off Duty.
- Yard/Spotter Mode is a distinct workflow centered on yard↔dock trailer moves.
- Samsara is used for GPS by the transportation company, while Amtech contains orders/BOL/customer data; neither integration is required for MVP.
- Design-partner retention preference is short: one week for movements/Yard Checks and one month for Orders/BOL/photos/deliveries, subject to later production policy review.

See:
- `follow-up-questionnaire-2026-10-03.md`
- `design-partner-facilities.md`
