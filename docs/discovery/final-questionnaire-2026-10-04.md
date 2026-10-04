# Final Discovery Questionnaire — 2026-10-04

**Status:** Confirmed discovery evidence  
**Source:** Third and final requirements clarification round with the initial design partner  
**Purpose:** Close the remaining functional ambiguities before freezing product flows and moving into architecture/UX specification.

## 1. Responsibility split: customer vs 2 Fast Logistics

For the Hood Container example:

| Action | Responsible party |
|---|---|
| Create order | Hood Container |
| Define destinations/stops | Hood Container |
| Define appointment time | Driven by final customer delivery appointment |
| Assign driver | Both / shared operational responsibility |
| Assign tractor | 2 Fast Logistics |
| Assign trailer | Hood Container |
| Change destination | Both |
| Change appointment time | Final customer |
| Cancel order | Hood Container |
| Authorize use of a different trailer | Hood Container |
| Mark order complete | Both |

### Product implication

TrailerSaaS must support customer/manufacturer-side operational control, not only transportation-company dispatch.

The customer-side dispatcher is an active operational actor who can create work, choose trailer and participate in completion/cancellation decisions.

## 2. Customer modification/cancellation window

The design partner indicated:

- customer can modify before driver assignment;
- customer can modify before driver departure;
- changes while driver is already in transit are **not normal**;
- changes before arrival at final customer can still occur.

### Product interpretation

Normal self-service edits should be straightforward before departure.

After departure, changes should be treated as an exception / controlled change rather than an ordinary silent edit, because in-transit destination changes require coordination.

## 3. Delivery completion, trip completion and shift completion

The final clarification indicates that **Off Duty is the end of the driver's workday**, not the completion of each delivery.

Therefore the product should distinguish at least:

1. **Delivery/Stop Completed** — cargo delivered / service completed and evidence recorded.
2. **Trip/Run Completed** — operational run completed according to dispatch/run rules.
3. **Shift Completed** — driver marks Off Duty at the end of the workday.

This supersedes any prior implication that Off Duty should close every Trip automatically.

## 4. Cancellation after driver departure

If an Order is cancelled after departure:

- Hood Container authorizes cancellation;
- driver receives new instructions;
- driver may return to base or be redirected;
- new destination/instruction may come from 2 Fast Logistics or Hood Container;
- trailer/cargo would normally return to base if the cancelled load is not redirected.

### Product implication

Cancellation after departure is not a simple delete/cancel button. It is an operational exception that may create a return-to-base or reroute instruction and must preserve history.

## 5. Required cargo/manifest data for MVP

Selected as needed:

- product description;
- quantity;
- pallet count;
- weight;
- Order number;
- PO;
- BOL.

Not selected:

- generic "units" field.

"Number of pieces" was not confirmed.

## 6. Pre-Trip requirements

Selected:

- odometer/mileage;
- front photo;
- rear photo;
- driver-side photo;
- passenger-side photo;
- checklist.

Not explicitly selected as separate fields:

- tire photo;
- lights photo;
- dedicated damage photo;
- free-text comment.

### Product implication

MVP needs a structured Pre-Trip record with four standard exterior photos, odometer and checklist.

Exact checklist items remain a UX/content definition task rather than a domain ambiguity.

## 7. Post-Trip requirements

Selected:

- final mileage/odometer;
- damages;
- failures/problems;
- comments.

Not explicitly selected:

- mandatory post-trip photos;
- generic tractor/trailer condition field.

## 8. Fuel event

Selected:

- odometer/mileage;
- receipt photo.

Not selected/confirmed:

- gallons;
- price per gallon;
- total paid;
- GPS location.

### Product implication

Fuel logging remains intentionally lightweight.

## 9. No receiver available / unsigned BOL

The proposed exception flow was accepted as sufficient:

1. Select **No receiver available**.
2. Capture BOL photo.
3. Capture delivery photo.
4. Add comment.

The question of whether Dispatch must receive an immediate alert was not answered.

## 10. POD rule configuration

For MVP, the same delivery-closing rule can be used for all customers.

Per-customer POD rule customization is **not required for the first version**.

## 11. Trailer designation to customer

Clarified:

- a trailer remains assigned/designated to a customer until Administration changes it;
- reassignment to another customer is possible only with administrative authorization;
- current design-partner clarification states trailer numbers **do not repeat** and are assigned to customers.

### Important correction to prior discovery

An earlier answer created ambiguity about duplicate visible trailer numbers. The final clarification states that trailer numbers do not repeat in the current 2 Fast Logistics operation.

### SaaS modeling implication

Even though the current design partner uses unique displayed trailer numbers, TrailerSaaS should still keep an immutable internal ID/UUID for each trailer and must not rely on human-readable identifiers being globally unique across all tenants.

## 12. Missing trailer / Location Unknown

When Yard Check cannot find a trailer expected at that location:

- generate an automatic alert to Administration;
- Administration is responsible for investigating;
- set current location to **Unknown**;
- preserve and display:
  - last known location;
  - last seen timestamp;
  - last driver.

Example presentation accepted as useful:

- Current location: Unknown
- Last known location: Hood Container Yard
- Last seen: Oct 2, 4:15 PM
- Last driver: John Smith

## Functional discovery status

This third round resolves the major product-behavior questions needed to proceed into:

1. product-flow freeze;
2. state-machine definition;
3. UX specification;
4. architecture ADRs;
5. data model/API contracts.

Remaining items are mostly implementation-policy, compliance or UX-detail questions rather than missing understanding of the core operation.
