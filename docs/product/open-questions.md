# Open Questions

**Status:** Functional discovery complete; remaining items are product-policy / implementation decisions  
**Convention:** `OQ-###`

No further broad operational questionnaire is planned. The three discovery rounds provide enough information to freeze the core workflows and move into UX/architecture.

## Product-policy decisions still open

### OQ-050 — In-transit customer changes
Discovery says normal edits are allowed before departure, changes while driving are not normal, but changes before final arrival can occur.

Recommended product behavior:

- direct customer self-service edits before departure;
- after departure, create an audited change/reroute request/instruction handled by authorized operations.

This is a product decision to formalize in the state machine, not a missing discovery fact.

### OQ-051 — Exact Trip/Run completion event
We now distinguish:

- Delivery/Stop completion;
- Trip/Run completion;
- Shift/Off Duty completion.

Need to choose the exact Trip completion rule for the product state machine (for example return-to-base, final stop departure, dispatcher close, or run-specific behavior).

### OQ-052 — Pre-Trip checklist content
Pre-Trip definitely requires:

- odometer;
- front/rear/driver-side/passenger-side photos;
- checklist.

Exact checklist questions/items remain to be authored during UX/specification.

### OQ-053 — Unsigned-BOL alert
“No receiver available” workflow is defined as:

- reason/status;
- BOL photo;
- delivery photo;
- comment.

The interview did not answer whether this should also trigger an immediate Dispatcher alert.

This is non-blocking; default product behavior can be decided during alert design.

### OQ-054 — OOS → Available authority
Damage/OOS capture is defined, but final authority for returning trailer to service is not yet confirmed.

Candidate roles:

- Administrator;
- Shipping/Supervisor;
- future Maintenance role.

### OQ-055 — Return-to-yard exact location
When a driver returns to base with a trailer, should the system immediately set it to the base Yard, or require exact Yard/Dock confirmation?

Recommended: base Yard by default, with Yard/Spotter moves recording subsequent Dock placement.

### OQ-056 — Official timekeeping usage
On Duty / Off Duty is required, but the product still needs a business/legal decision on whether these timestamps are:

- operational records only; or
- official payroll/timekeeping records.

If official, correction/approval and retention requirements need compliance review.

### OQ-057 — Production retention policy — BLOCKING before production
The design partner wants short operational retention:

- movements: 1 week;
- Yard Checks: 1 week;
- Orders/BOL/photos/deliveries: 1 month.

Before destructive deletion is implemented, validate U.S. legal, contractual, insurance and audit requirements. The architecture should support configurable retention.

### OQ-058 — Shared-address location model
Discovery confirms Stanfast and Black Hawk can share a physical address but use different dock ranges.

Recommended model is:

- Physical Site;
- Customer/Operational Area;
- Yard/Docks.

This should be accepted formally when the data model is frozen.

### OQ-059 — Tenant-level configurability
For MVP, customer-specific POD rules are **not** required. Still decide which settings are tenant-configurable from day one:

- stale-trailer threshold;
- alert recipients;
- retention;
- location/geofence settings;
- inspection checklist;
- customer edit permissions.

### OQ-060 — Initial SaaS billing metric
Commercial billing remains tenant-level. Mobile/web apps are free for end users.

Still choose future plan dimensions:

- trailer count;
- driver count;
- movement volume;
- plan tier;
- hybrid.

This does not block core implementation.

## Resolved by final discovery round

The final round resolved:

- customer vs 2 Fast Logistics operational responsibilities;
- normal edit/cancel window;
- delivery vs shift completion distinction;
- active-order cancellation behavior;
- required cargo/manifest fields;
- Pre-Trip capture;
- Post-Trip capture;
- lightweight fuel fields;
- no-receiver POD exception;
- common POD rule for MVP;
- persistent customer designation of trailers;
- current design-partner trailer-number uniqueness;
- missing-trailer alert ownership and last-known-location display.

## Important correction from earlier discovery

An earlier answer suggested displayed trailer numbers might not be unique. The final clarification states that, in the current design-partner operation, **trailer numbers do not repeat and are assigned to customers**.

The SaaS should still use internal UUIDs and must not assume those human-readable identifiers are globally unique across all tenants.
