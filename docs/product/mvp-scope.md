# MVP Scope v0.1

**Status:** Draft for approval after second discovery round

The MVP should prove that TrailerSaaS can maintain trustworthy trailer inventory/location and coordinate customer orders, driver trips, stops and delivery evidence without relying on spreadsheets and message reconstruction.

## In scope

### A. Authentication and tenancy

- Multi-tenant company model
- Internal users and roles
- Customer-scoped users
- Server-side authorization
- Audit attribution

### B. Master data

- Customers
- Customer users/dispatchers
- Drivers
- Tractors
- Trailers
- Physical sites
- Yards/docks/operational sublocations
- QR identity

### C. Orders, trips and stops

- Customer-side order creation
- Order number / PO / shipment / BOL references
- Multiple orders in one trailer/trip
- One order spanning multiple trailers
- Multi-stop trips
- Appointment information
- Driver assignment
- Tractor assignment
- Trailer assignment when known
- Dispatch-authorized trailer substitution
- Destination change with audit
- Cancellation/exception support

### D. Driver mobile — Road Mode

- Sign in
- On Duty / Off Duty
- Pre-trip
- Tractor photos
- Odometer/mileage
- View assigned trips/orders/stops
- Scan trailer QR
- Manual departure
- Geofence-assisted + confirmed arrival with manual fallback
- Loading / unloading
- Drop & Hook
- Multi-stop execution
- BOL/POD photo upload
- Return to base
- Post-trip
- Basic delay/damage/exception reporting
- Temporary offline queue/retry for critical actions

### E. Lightweight fuel event

- Optional fuel action during active trip
- Odometer/mileage
- Fuel total/details once final fields are confirmed

This is not full fuel management.

### F. Yard / Spotter Mode

- On/Off Duty
- Pre/Post-trip
- Yard moves
- Yard ↔ dock movement
- Dock ↔ yard movement
- QR trailer identification
- Authorized trailer state/location updates
- Auditable movement history

### G. Trailer inventory and history

- Internal unique trailer identity
- Non-unique visible trailer numbers supported
- Search/disambiguation by trailer/customer context
- Current location
- Load state
- Operational state
- Time at location
- Current load manifest / orders
- Last driver
- Last tractor
- Movement history
- OOS + photos
- Auditable corrections

### H. Yard check

- Select site/yard
- Expected inventory snapshot from recorded yard/dock state
- QR scan
- Missing list
- Unexpected list
- Location-discrepancy warning
- Confirmed correction workflow
- Location Unknown handling

### I. Alerts

Initial required alert:

- trailer has not moved for configured period (design-partner baseline: 3 days).

### J. Customer portal / customer dispatcher

- Customer user accounts
- Customer-scoped trailer inventory
- Customer-scoped order/delivery visibility
- Direct order/delivery creation
- Modify/cancel before allowed cutoff
- View location/status/content
- View permitted BOL/POD/evidence
- No access to unrelated customers

### K. Auditability

- assignment/reassignment history
- destination changes
- trailer substitutions
- location corrections
- actor/timestamp/reason
- original value retained

## MVP geolocation behavior

- Known sites may have geofences.
- Geofence can suggest probable arrival.
- Driver confirms arrival.
- Manual arrival remains available.
- Departure is manual.
- Continuous route history is not required.
- Always-on background tracking is not an MVP dependency.

## Design-partner retention preferences

Current requested operational retention:

- trailer movements: 1 week;
- yard checks: 1 week;
- orders: 1 month;
- BOL/photos/deliveries: 1 month.

These values must remain configurable/policy-driven until product/legal retention review is complete.

## Out of scope for MVP

- full payroll/wage calculation;
- break/lunch/overtime payroll rules;
- accounting/invoicing;
- ELD/HOS replacement;
- continuous live route tracking;
- route optimization;
- full fuel-management system;
- full preventive-maintenance/work-order system;
- repair cost/vendor management;
- RFID/NFC hardware;
- custom telematics hardware;
- Samsara integration;
- Amtech integration;
- AI optimization;
- customer billing;
- marketplace/load-board features;
- standalone digital signature capture.

## Future candidates

- Samsara integration
- Amtech integration
- richer maintenance
- analytics/KPIs
- route/ETA intelligence
- configurable customer evidence rules
- webhooks/API
- bulk imports
- advanced reporting
- RFID/NFC
- payroll export
- configurable retention by plan/customer

## MVP exit criteria — proposed

1. Customer dispatcher can create an order that appears in operations.
2. Operations can plan a multi-stop Trip and assign driver/tractor/trailer.
3. Driver can complete On Duty → Pre-Trip → Trip → Return → Post-Trip → Off Duty.
4. Driver can complete Drop & Hook by QR.
5. Wrong-trailer scan requires dispatcher-controlled substitution.
6. Multi-order/multi-stop relationships remain traceable.
7. Trailer search returns trustworthy current state and history.
8. Yard/Spotter can perform yard moves.
9. Yard Check detects missing/unexpected/discrepant trailers.
10. POD/BOL evidence can be captured.
11. Customer user can see only customer-scoped data.
12. Corrections/destination changes are auditable.
13. Critical mobile actions survive temporary connectivity loss.
14. Cross-tenant authorization tests prevent data leakage.
