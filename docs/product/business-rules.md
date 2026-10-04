# Business Rules

**Status:** Draft refined after second discovery round  
**Convention:** `BR-###`

## Identity and tenancy

- **BR-001** — Every operational record belongs to exactly one tenant.
- **BR-002** — Customer users can access only authorized data for their customer within the tenant.
- **BR-003** — Displayed trailer numbers are not assumed unique across the tenant.
- **BR-004** — Each trailer has an internal unique identity independent of displayed identifier.
- **BR-005** — QR identity resolves to the internal trailer asset.

## Order / trip / trailer relationships

- **BR-010** — One Order may require multiple trailers.
- **BR-011** — One trailer/load may contain multiple Orders.
- **BR-012** — One Trip may contain multiple Stops.
- **BR-013** — A Trip may service multiple Orders.
- **BR-014** — The data model must not enforce a one-to-one Order↔Trip↔Trailer relationship.
- **BR-015** — Customer-side authorized dispatcher may create an Order directly into the operational queue.
- **BR-016** — Customer modification/cancellation is allowed only before the configured/defined delivery cutoff; exact state rule remains open.

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
- **BR-082** — Delivery may require an exception path when no receiver is available to sign.
- **BR-083** — Evidence requirements may vary by customer; configuration depth is still open.

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
