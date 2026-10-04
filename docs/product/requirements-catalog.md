# Functional Requirements Catalog

**Status:** Draft derived from two discovery rounds  
**Convention:** `FR-###`

## Tenant, identity and access

- **FR-001** — The system shall support multiple tenant transportation companies.
- **FR-002** — Tenant data shall be isolated.
- **FR-003** — A tenant shall support role-based internal users.
- **FR-004** — A tenant shall support customer users scoped to a customer account.
- **FR-005** — Customer users shall not access unrelated customer data.
- **FR-006** — Customer-side Dispatcher/Shipping shall be representable as a distinct authorization role.

## Master data and locations

- **FR-010** — Authorized users shall manage drivers.
- **FR-011** — Driver records shall support name, license number and medical-certificate expiration.
- **FR-012** — Authorized users shall manage tractors.
- **FR-013** — Authorized users shall manage trailers.
- **FR-014** — Each trailer shall have an internal unique ID independent of its displayed trailer number.
- **FR-015** — Displayed trailer identifiers may be duplicated across customer/fleet contexts.
- **FR-016** — Trailer shall support type/size/capability metadata.
- **FR-017** — Authorized users shall manage customers and customer users.
- **FR-018** — Authorized users shall manage physical sites.
- **FR-019** — A physical site shall support multiple operational sublocations such as yards and docks.
- **FR-020** — Multiple customer contexts may exist at the same street address.
- **FR-021** — Known sites may store geofence metadata.

## Orders, trips, stops and manifests

- **FR-030** — Authorized customer-side users shall create Orders.
- **FR-031** — Orders shall support order number, PO, shipment number and BOL references.
- **FR-032** — The operational model shall support multiple Orders in one Trip/trailer load.
- **FR-033** — One Order may be allocated across multiple trailers.
- **FR-034** — A Trip shall support multiple Stops.
- **FR-035** — Stops shall support origin/destination/site, appointment and service type.
- **FR-036** — A Trip shall be assignable to a driver.
- **FR-037** — A Trip shall be assignable to a tractor.
- **FR-038** — Trailer assignment may be unknown until pickup.
- **FR-039** — The system shall represent a load/manifest containing one or more Orders.
- **FR-040** — Manifest/order allocation shall support at least order, PO, BOL and weight when available.
- **FR-041** — Reassignment shall be auditable.
- **FR-042** — Destination changes shall preserve original/new destination, actor and timestamp.
- **FR-043** — Customer-created Orders shall enter operations automatically in the design-partner workflow.
- **FR-044** — Customer users shall be able to modify/cancel before the final allowed cutoff.
- **FR-045** — Trips/Orders shall support cancellation and exception states.

## Driver mobile — duty and inspection

- **FR-050** — Driver shall mark On Duty.
- **FR-051** — Driver shall mark Off Duty.
- **FR-052** — Driver shall complete a Pre-Trip workflow.
- **FR-053** — Pre-Trip shall support tractor photos.
- **FR-054** — Pre-Trip shall support odometer/mileage.
- **FR-055** — Driver shall complete a Post-Trip workflow.
- **FR-056** — The system shall not calculate driver wages in MVP.

## Driver mobile — trip execution

- **FR-060** — Driver shall view assigned Trips and Stops.
- **FR-061** — Driver shall start/execute an assigned Trip.
- **FR-062** — Driver shall record trailer hook by QR scan.
- **FR-063** — Driver shall record trailer drop by QR scan.
- **FR-064** — Hook shall associate trailer, driver, tractor, trip and timestamp.
- **FR-065** — Drop shall associate trailer, trip, stop/location and timestamp.
- **FR-066** — Driver shall record manual departure.
- **FR-067** — Driver shall confirm arrival, with optional geofence assistance.
- **FR-068** — Manual arrival fallback shall always exist.
- **FR-069** — Driver shall record loading/unloading state where relevant.
- **FR-070** — Driver shall execute Drop & Hook with old-trailer drop and replacement-trailer hook.
- **FR-071** — Driver shall support Live Load/Live Unload with same trailer.
- **FR-072** — Driver shall execute multiple Stops within one Trip.
- **FR-073** — Driver shall upload BOL/POD photos/documents.
- **FR-074** — Driver shall record basic delay/damage/operational exception.
- **FR-075** — Critical mobile actions shall support temporary offline capture and later synchronization.
- **FR-076** — Offline synchronization shall be idempotent.
- **FR-077** — Driver shall mark return to base.
- **FR-078** — Driver may record an optional Fuel Event during an active Trip.

## QR and trailer mismatch

- **FR-080** — Each trailer shall be assignable a durable QR identity.
- **FR-081** — QR shall resolve to the internal unique trailer asset.
- **FR-082** — Scanning a different trailer from the planned assignment shall not silently replace it.
- **FR-083** — Wrong-trailer substitution shall require dispatcher authorization.
- **FR-084** — Authorized substitution shall be audited.

## Trailer state and history

- **FR-090** — Load state shall be separate from operational state.
- **FR-091** — Location shall be separate from load/operational state.
- **FR-092** — Trailer purpose/usage shall be separate from load state and location.
- **FR-093** — Trailer may be marked OOS.
- **FR-094** — OOS workflow shall support photos and comment/context.
- **FR-095** — System shall preserve trailer movement history for configured retention.
- **FR-096** — Trailer search shall support duplicate displayed identifiers through customer/fleet disambiguation.
- **FR-097** — Trailer detail shall show current location and dwell time.
- **FR-098** — Trailer detail shall show last driver and tractor.
- **FR-099** — Trailer detail shall show related manifest/orders and evidence.
- **FR-100** — Authorized corrections shall preserve previous value, new value, actor, timestamp and reason.
- **FR-101** — Missing trailer may be marked LOCATION_UNKNOWN through reconciliation flow.

## Geolocation

- **FR-110** — Known sites may define geofences.
- **FR-111** — Geofence may suggest probable arrival.
- **FR-112** — Driver confirmation shall be supported for detected arrival.
- **FR-113** — Departure shall be manually recordable and is the design-partner preferred behavior.
- **FR-114** — Continuous route tracking is not an MVP requirement.
- **FR-115** — MVP shall not depend on always-on background tracking of drivers' personal devices.

## Yard / Spotter

- **FR-120** — Yard/Spotter shall have a role/mode-specific mobile workflow.
- **FR-121** — Yard/Spotter shall record yard-to-dock and dock-to-yard moves.
- **FR-122** — Yard moves shall use trailer QR where practical.
- **FR-123** — Yard moves shall create auditable movement events.
- **FR-124** — Authorized yard users may update permitted trailer state/location data.

## Yard check

- **FR-130** — Authorized user shall start a Yard Check for a facility/yard.
- **FR-131** — Expected set shall be based on trailers currently recorded in the yard/associated docks.
- **FR-132** — User shall scan found trailers.
- **FR-133** — System shall identify missing and unexpected trailers.
- **FR-134** — Location discrepancies shall show warning and require confirmation before correction.
- **FR-135** — Reconciliation correction shall be auditable.
- **FR-136** — Yard Check history shall follow configured retention.

## Delivery evidence

- **FR-140** — Signed BOL photo/file shall be supported as primary POD evidence.
- **FR-141** — Standalone signature-on-screen is not required for MVP.
- **FR-142** — Delivery flow shall support an exception when no receiver is available to sign.
- **FR-143** — Evidence requirements may vary by customer in a later/configurable policy.
- **FR-144** — Evidence shall retain uploader/source, timestamp and related Order/Stop/Trip.

## Customer portal

- **FR-150** — Customer users shall view authorized trailer inventory.
- **FR-151** — Customer users shall view current location/status/content.
- **FR-152** — Customer users shall view their own Orders/deliveries.
- **FR-153** — Customer users shall view permitted evidence/documents.
- **FR-154** — Authorized customer dispatcher shall create Orders directly.
- **FR-155** — Customer modification/cancellation shall be restricted by state and audit rules.

## Alerts and dashboard

- **FR-160** — System shall expose pending Orders and undelivered work.
- **FR-161** — System shall expose inventory views for empty, loaded, customer-located, in-transit, yard and stale trailers where derivable.
- **FR-162** — System shall detect trailers without movement beyond configured threshold.
- **FR-163** — Administration shall receive in-app stale-trailer alert.
- **FR-164** — Discovery baseline for stale threshold is 3 days, not a hard-coded global constant.

## Audit

- **FR-170** — Important administrative changes shall be auditable.
- **FR-171** — Audit shall identify actor and timestamp.
- **FR-172** — Operational corrections shall not erase original history.
- **FR-173** — Event source shall distinguish user, mobile/geofence, system and integration.
- **FR-174** — Driver reassignment shall preserve which driver performed each segment.

## Retention

- **FR-180** — Retention shall be policy/configuration driven rather than hard-coded into domain logic.
- **FR-181** — Design-partner requested movement and Yard Check retention is 1 week.
- **FR-182** — Design-partner requested Order/BOL/photo/delivery retention is 1 month.
- **FR-183** — Production retention remains subject to legal/contractual/product review.
