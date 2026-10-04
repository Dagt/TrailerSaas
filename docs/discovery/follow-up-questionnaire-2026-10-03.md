# Follow-up Discovery Questionnaire — 2026-10-03

**Status:** Confirmed discovery evidence  
**Source:** Follow-up questionnaire answered by the initial design partner  
**Purpose:** Resolve ambiguities from the first requirements questionnaire. Where an answer remains unclear, it is explicitly marked as unresolved.

## 1. Orders, trips and deliveries

- Order-to-operation cardinality is **many-to-many** in practice.
- Multiple orders can travel in one trailer.
- One order can require multiple trailers.
- One trailer/run can contain multiple deliveries/stops.
- Multiple orders in the same trailer may belong to the same customer or to different customers.
- The design-partner answer identifies **return to base** as the point at which the driver's work/task is considered complete for the described workflow.
- After completing a delivery, the driver may:
  - return to base;
  - receive another order;
  - pick up another trailer;
  - follow another operational instruction.

### Product implication

The domain must not assume:

`Order = Job = Trip = Trailer`

A richer model is required, likely separating customer order(s), driver trip/run, stop(s), and trailer/load assignment(s).

## 2. Drop & Hook and Live Load

Two common movement patterns were confirmed.

### Drop & Hook

Typical sequence:

1. Driver arrives with trailer A.
2. Driver drops trailer A.
3. Driver hooks trailer B.
4. Driver leaves with trailer B.

### Live Load / Live Unload

The driver remains with the same trailer while it is loaded/unloaded and then leaves with that same trailer.

No third common movement type was identified in this follow-up.

## 3. Trailer mismatch

If dispatch assigns trailer T114 but the driver cannot find it and sees T116:

- the driver must notify dispatch;
- the driver **cannot simply take T116**;
- dispatch authorizes any trailer substitution.

Reason: the alternate trailer may already belong to another order.

## 4. Trailer identifier uniqueness

The follow-up clarified that displayed trailer numbers are **not globally unique across the entire operation**.

Therefore:

- the product needs an internal globally unique asset ID/UUID;
- the visible operational identifier should be modeled within an owning/designated customer/fleet namespace where necessary;
- QR codes must resolve to the internal trailer asset, not only the printed trailer number.

## 5. Trailer contents

Desired trailer/load information includes at least:

- order number;
- PO;
- BOL;
- weight;
- multiple orders.

The respondent indicated that storing richer information could also be useful.

### Product implication

“Contents” should not be a single free-text field. The model should support a manifest/load association to one or more orders, while optional product description, quantity, pallet count and other attributes can be added where available.

## 6. GPS, arrival and departure

Continuous route history is **not required** for the described workflow.

An event timeline is sufficient, e.g.:

- departed Chicago 10:15;
- arrived Customer A 12:04;
- departed Customer A 13:10;
- arrived Customer B 14:35.

### Arrival

Preferred behavior:

- GPS/geofence may detect probable arrival;
- app may prompt: **“It looks like you arrived — confirm.”**
- manual arrival remains available.

### Departure

Departure should be **manual** in the current preferred workflow.

### Personal-device privacy

Drivers use personal phones. Requiring persistent background location may create resistance/problems.

### Product implication

MVP should use location as an assistive feature rather than continuous employee tracking:

- geofence-assisted arrival when permission is available;
- explicit driver confirmation;
- manual fallback;
- manual departure;
- no always-on route tracking requirement.

## 7. Customer-side dispatch

The respondent clarified that dispatch users can belong to the **manufacturing/customer companies**.

A customer-side dispatcher should be able to create a delivery/order in the platform, and it should appear automatically in the operational system rather than requiring a separate “request approval” step.

Customer users can modify/cancel the request **before delivery**.

### Still to define

The exact cutoff state for modification/cancellation must be translated into system states (for example before driver starts, before arrival, or any time before delivery completion).

## 8. Driver handoff

Normal operation does **not** change drivers mid-task.

If a driver change is necessary:

- the work generally returns to base / is handled operationally;
- dispatch performs the reassignment;
- the system should preserve which driver performed each segment/action.

## 9. Delivery evidence

A signed BOL remains the primary proof when a receiver is available.

- Photo/file of signed BOL is sufficient.
- Direct digital signature on the phone is not necessary for MVP.
- In some deliveries there may be nobody available to sign; delivery can still occur.
- The workflow therefore needs an exception path for **BOL not signed / no receiver available**.

### Mandatory evidence baseline

The selected requirement is:

- photo of the signed BOL when signature is obtainable.

Evidence requirements may vary by customer.

## 10. Yard check

Expected yard inventory is based on trailers currently recorded as being:

- in the yard; or
- in the company's docks associated with that facility.

When a scanned trailer is found somewhere different from the recorded location:

1. show a warning/discrepancy;
2. require a user confirmation;
3. do not silently rewrite location.

If an expected trailer is missing:

- show it as missing;
- mark location as **unknown** after the appropriate reconciliation action.

## 11. Location correction permissions

The following roles were selected as able to correct a wrong trailer location:

- administrator;
- shipping;
- yard spotter.

Driver was **not** selected.

Each correction should preserve:

- previous value;
- corrected value;
- actor;
- date/time;
- reason.

## 12. Design-partner retention preferences

Current stated preferences:

| Record type | Requested retention |
|---|---:|
| Trailer movements | 1 week |
| Orders | 1 month |
| BOL | 1 month |
| Photos | 1 month |
| Deliveries | 1 month |
| Yard checks | 1 week |

The respondent explicitly stated that older history is not operationally necessary for the current workflow.

### Product note

These are **design-partner preferences**, not yet the final SaaS-wide retention policy. Regulatory, contractual and product/audit needs must be evaluated before destructive retention is implemented.

## 13. Driver shift/timekeeping

Only the following is requested:

- on-duty timestamp;
- off-duty timestamp.

Not requested:

- breaks;
- lunch;
- overtime calculation;
- supervisor time approval;
- wage/payroll calculation.

TrailerSaaS should not become a payroll engine in the MVP.

## 14. Existing systems

### Samsara

- Used by the transportation company.
- The respondent only confirmed GPS/location usage.
- Customers do not have access.
- Deep integration is not required for initial manual workflow.

### Amtech

Known current data:

- orders;
- BOL;
- customers.

Manual re-entry into TrailerSaaS is acceptable for the initial version.

Therefore Amtech integration is not an MVP dependency.

## 15. Changes and exceptions

### Destination change

- Authorized by dispatcher.
- System should preserve original destination and changed destination, actor and timestamp.

### Cancellation after trip start

- Driver/operation contacts shipping.
- Exact system state transition still requires specification.

### Damaged trailer

MVP requirement is limited to:

- mark trailer OOS;
- take/upload photos;
- optional comment/context.

Repair vendor, cost, maintenance work order and return-to-service approval workflow are not requested in the initial scope.

## 16. Driver ideal-day workflow

The design partner described the desired driver flow as:

1. Open app.
2. Mark **On Duty**.
3. Perform pre-trip inspection.
4. Capture truck photos.
5. Record mileage/odometer.
6. Go to office.
7. Dispatcher loads/assigns work and provides paperwork.
8. Driver scans trailer QR to confirm correct trailer.
9. Driver marks departure from base.
10. Trailer becomes **in route / in transit**.
11. Optional fuel event may be recorded during the trip, including mileage and fuel total.
12. Driver marks arrival at destination.
13. Driver marks operation:
    - loading;
    - unloading; or
    - drop & hook.
14. For drop & hook:
    - record dropped trailer at customer;
    - scan/record replacement trailer.
15. Driver confirms delivery with BOL photo.
16. Driver marks departure.
17. If there are multiple stops, repeat destination workflow for each stop.
18. Driver marks arrival back at base.
19. Trailer/location state returns to yard as appropriate.
20. Driver performs post-trip inspection.
21. Driver marks **Off Duty**.

## 17. Yard/spotter mode

A yard-mode user follows a different workflow:

1. On Duty.
2. Pre-trip.
3. Perform **yard moves**.
4. Move trailers:
   - yard → dock;
   - dock → yard;
   - dock → dock where allowed.
5. Yard spotter can update trailer order/status/location according to authorized rules.
6. Post-trip.
7. Off Duty.

This confirms that **Driver Road Mode** and **Yard/Spotter Mode** should be separate role/mode-specific mobile workflows even if delivered by the same mobile application.

## 18. Design-partner facilities supplied

See `design-partner-facilities.md` for the exact facility/dock examples provided during discovery.
