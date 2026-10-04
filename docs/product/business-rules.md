# Business Rules

**Status:** Accepted v0.1 on 2026-10-04  
**Convention:** `BR-###`

## Identity and tenancy

- **BR-001** — Every operational record belongs to exactly one tenant.
- **BR-002** — Customer users can access only authorized data for their customer within the tenant.
- **BR-003** — For the initial design partner, displayed trailer numbers are unique within the tenant; internal asset identity must nevertheless use an immutable system ID/UUID.
- **BR-004** — Each trailer has an internal unique identity independent of displayed identifier.
- **BR-005** — QR identity resolves to the internal trailer asset.

## Order / trip / trailer relationships

- **BR-010** — One Order may require multiple trailers.
- **BR-011** — One trailer/load may contain multiple Orders.
- **BR-012** — One Trip may contain multiple Stops.
- **BR-013** — A Trip may service multiple Orders.
- **BR-014** — The data model must not enforce a one-to-one Order↔Trip↔Trailer relationship.
- **BR-015** — Customer-side authorized dispatcher may create an Order directly into the operational queue.
- **BR-016** — Normal customer self-service modification/cancellation is allowed before driver departure; changes after departure are operational exceptions and must be audited/controlled.

## Trailer state

- **BR-020** — Load state and operational state are independent.
- **BR-021** — Location is independent of load state.
- **BR-022** — Usage/purpose must not replace state or location.
- **BR-023** — Trailer can be OOS.
- **BR-024** — MVP OOS evidence requires photo support; maintenance work-order management is outside scope.
- **BR-025** — Current trailer state should be derived from authoritative events/corrections rather than destructive overwrite.

## Hook / drop

- **BR-030** — Hook identifies trailer, driver and, when known, tractor/trip.
- **BR-031** — Drop records operational location.
- **BR-032** — Driver cannot silently substitute a different trailer.
- **BR-033** — Trailer substitution requires dispatcher authorization.
- **BR-034** — Authorized substitution must be auditable.

## Trip execution

- **BR-040** — Normal Road Mode begins with On Duty and Pre-Trip.
- **BR-041** — Road Mode ends with return-to-base/Post-Trip/Off Duty in the design-partner workflow.
- **BR-042** — Departure is manually recorded.
- **BR-043** — Geofence may assist arrival, but driver confirmation/manual fallback remains available.
- **BR-044** — Continuous route tracking is not required.
- **BR-045** — Mid-task driver changes are exceptional and controlled by dispatch.
- **BR-046** — Driver-segment history must remain attributable after reassignment.

## Drop & Hook / Live Load

- **BR-050** — Drop & Hook means current trailer is dropped and a replacement trailer may be hooked before departure.
- **BR-051** — Live Load/Unload keeps the same trailer with driver through loading/unloading.
- **BR-052** — Multiple delivery stops may repeat arrival/service/evidence/departure in one Trip.

## Yard operations

- **BR-060** — Yard moves can exist as independent operational movement events.
- **BR-061** — Yard Check expected inventory is based on trailers currently recorded in yard/associated docks.
- **BR-062** — A scanned location discrepancy requires warning/confirmation before correction.
- **BR-063** — Missing trailer may be marked LOCATION_UNKNOWN.
- **BR-064** — Yard reconciliation corrections must identify responsible actor.

## Location correction permissions

Design-partner selected roles:

- administrator;
- shipping;
- yard spotter.

- **BR-070** — Drivers do not have unrestricted location-correction permission.
- **BR-071** — Correction preserves prior value, new value, actor, timestamp and reason.

Exact distinction between tenant shipping and customer shipping remains part of authorization design.

## Delivery evidence

- **BR-080** — Signed BOL photo/file is accepted POD evidence when signature is available.
- **BR-081** — Standalone on-screen digital signature is not required for MVP.
- **BR-082** — If no receiver is available, the driver records No receiver available + BOL photo + delivery photo + comment.
- **BR-083** — MVP uses one common POD rule for all customers; customer-specific POD configuration is deferred.

## Destination changes

- **BR-090** — Destination change after dispatch requires dispatcher authorization.
- **BR-091** — Original and new destination, actor and timestamp must remain auditable.

## Shift/payroll boundary

- **BR-100** — MVP shift tracking records only On Duty and Off Duty.
- **BR-101** — Breaks, lunch, overtime and wage calculation are outside the current MVP.
- **BR-102** — If timestamps are later used as legal payroll/timekeeping records, compliance rules must be reviewed before relying on them.

## Mobile/offline

- **BR-110** — Critical offline actions must be idempotent on replay.
- **BR-111** — Offline action should preserve original occurrence timestamp where safe/available.
- **BR-112** — Server remains authoritative for authorization/conflict resolution.

## Alerts

- **BR-120** — Three-day no-movement threshold is the design-partner baseline, not a universal constant.

## Retention

Design-partner operational preferences:

- movements: 1 week;
- yard checks: 1 week;
- Orders/BOL/photos/deliveries: 1 month.

- **BR-130** — These values are not yet a universal SaaS retention policy.
- **BR-131** — Destructive retention must not be implemented before product/legal/contractual review.


## Trailer customer designation

- **BR-132** — A trailer's designated customer persists until changed by Administration.
- **BR-133** — Customer designation is independent from current location, load and Order.
- **BR-134** — Trailer reassignment to another customer requires administrative authorization and audit.

## Driver inspection

- **BR-140** — Pre-Trip requires odometer, four standard exterior photos and a checklist.
- **BR-141** — Post-Trip records final odometer, damages, failures/problems and comments.
- **BR-142** — These records are operational workflow evidence, not by themselves a complete regulatory/DOT compliance system.

## Fuel event

- **BR-150** — MVP Fuel Event requires odometer/mileage and receipt photo only.
- **BR-151** — Gallons, price-per-gallon and total paid are not required for the initial workflow.

## Lifecycle separation

- **BR-160** — Delivery/Stop completion, Trip/Run completion and Shift completion are distinct.
- **BR-161** — Off Duty means end of driver's workday and must not implicitly complete every Trip/Delivery.
- **BR-162** — POD/BOL can complete a Delivery/Stop while the Trip or Shift continues.

## Cancellation / reroute

- **BR-170** — Hood Container/customer authority can cancel an active Order in the design-partner workflow.
- **BR-171** — After departure, cancellation/change produces instructions rather than destructive deletion.
- **BR-172** — Driver may return to base or be redirected.
- **BR-173** — Original destination/instruction must remain auditable.

## Missing trailer

- **BR-180** — Confirmed missing trailer may transition to LOCATION_UNKNOWN.
- **BR-181** — LOCATION_UNKNOWN triggers an Administration alert.
- **BR-182** — Administration owns investigation.
- **BR-183** — Last known location, last seen time and last driver remain visible.
