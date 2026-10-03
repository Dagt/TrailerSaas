# Open Questions

**Status:** Active  
**Convention:** `OQ-###`

Questions marked **BLOCKING** should be resolved before the related implementation is considered stable.

## Orders, jobs and route structure

### OQ-001 — Order vs job cardinality — BLOCKING
Can one customer order create multiple jobs/movements? Can multiple orders be delivered in one trailer/job?

### OQ-002 — Multi-stop jobs — BLOCKING
Discovery says a driver may have several deliveries in one trailer. Does one Job contain multiple stops, or should each stop be a separate Movement under a Trip?

### OQ-003 — Job completion
What exact event completes a job: delivery evidence, trailer drop, departure from destination, return to base, or dispatcher confirmation?

### OQ-004 — Live load
What exact states/actions are required for live-load vs drop-and-hook?

## Trailer assignment and scanning

### OQ-005 — Wrong trailer scan — BLOCKING
If a driver scans a trailer different from the assigned trailer, should the app:
- block;
- warn and require dispatcher approval;
- allow with reason;
- automatically reassign?

### OQ-006 — Trailer identifier uniqueness — BLOCKING
Are trailer IDs guaranteed unique across the transportation company, or only within a customer's naming prefix?

### OQ-007 — Trailer content structure
Does “content” mean only the related order/BOL, or must the system store quantities/line items/product descriptions?

## Location / GPS

### OQ-008 — Full route tracking — BLOCKING for mobile architecture
The questionnaire answer to “full route vs entry/exit only” was ambiguous. Is continuous route history needed or are geofence arrival/departure events sufficient?

### OQ-009 — Background location policy — BLOCKING
Can the company require background location permission on drivers' personal phones during active work? What written policy/consent exists?

### OQ-010 — Geofence confirmation
Should detected arrival/departure be accepted automatically, or always require driver confirmation?

## Permissions

### OQ-011 — Who may correct trailer location? — BLOCKING
Questionnaire says “everyone” in one answer, while broader role answers imply restrictions. Define exact roles allowed to correct location.

### OQ-012 — Customer movement requests
Does a customer-created request require dispatcher approval before becoming a job?

### OQ-013 — Driver handoff
Can a driver transfer an active job directly to another driver, or must dispatch approve/reassign it?

## Delivery evidence

### OQ-014 — Digital signature
Earlier discovery says signature is needed; later history requirement says signature is not needed. Is signed BOL photo/document sufficient?

### OQ-015 — Required POD
Which evidence is mandatory by customer/job type:
- signed BOL;
- photo;
- receiver name;
- digital signature;
- delivery note?

## Yard operations

### OQ-016 — Expected yard inventory
How is the expected trailer list computed? Current location only? Customer assignment? Planned jobs? Manual overrides?

### OQ-017 — Yard history retention
Discovery mentioned 15 days. Is this truly a business rule or simply current operational preference?

### OQ-018 — In-yard moves
Do in-yard moves require an active job/order, or can they be independent trailer movement events?

## Data retention

### OQ-019 — Operational history retention — BLOCKING
Discovery said one month. For a production SaaS and audit value, is one month actually desired, or merely the minimum users thought they needed?

### OQ-020 — Evidence retention
How long must BOLs, photos and delivery evidence be retained?

## Driver shift/payroll

### OQ-021 — Shift tracking
Is shift start/end required in the first MVP or merely a future payroll/export feature?

### OQ-022 — Timekeeping compliance
If shift timestamps are used for payroll, what correction/approval rules and U.S./state labor-record requirements apply?

## Existing systems

### OQ-023 — Samsara
Which tractors use Samsara, and is integration needed for MVP, later phase, or never?

### OQ-024 — Amtech
What data exists in Amtech? Which system is currently authoritative for orders, and is duplicate entry acceptable in MVP?

## Exceptions

### OQ-025 — Changed destination
Who authorizes destination changes after a driver has started a job, and how should the original destination remain visible?

### OQ-026 — Cancelled jobs
What states allow cancellation? What happens to already-recorded movement events?

### OQ-027 — Trailer damage workflow
Is photo + OOS flag enough for MVP, or is maintenance approval/repair tracking required?

## Commercial/product configuration

### OQ-028 — Configurability
Which workflows vary by customer/tenant enough to require configuration in MVP versus standardized behavior?

### OQ-029 — Customer inventory ownership
Are trailers “assigned” to a customer permanently, temporarily, or only by current job/location?

### OQ-030 — Initial SaaS billing metric
Should future pricing be primarily by trailers, drivers, movements, locations, or plan tier? This does not block MVP architecture except for entitlement strategy.
