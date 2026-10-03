# MVP Scope v0.1

**Status:** Draft for approval

The MVP should prove that TrailerSaaS can create a trustworthy operational record of trailer location, state, content/order association and movement while reducing spreadsheet/message dependency.

## In scope

### A. Authentication and tenant isolation

- Tenant/company
- Users
- Roles/permissions
- Customer-scoped access

### B. Master data

- Customers
- Customer contacts/users
- Drivers
- Tractors
- Trailers
- Locations
- Trailer QR identity

### C. Orders and dispatch

- Create order/job
- Order/PO/shipment/BOL references
- Origin and destination
- Appointment date/time
- Assign driver
- Assign tractor
- Assign/reassign trailer when known
- Active/pending/completed/cancelled job visibility

### D. Driver mobile workflow

- Sign in
- Start/end shift
- View assignments
- Start job
- Hook trailer via QR
- Drop trailer via QR
- Confirm arrival/departure
- Upload evidence/photos/BOL
- Report basic exception/delay/damage
- Temporary offline queue/retry for critical actions

### E. Trailer inventory and history

- Search by trailer ID
- Current location
- Load state
- Operational state
- Time at current location
- Current/last related order
- Last driver
- Last tractor
- Movement history
- OOS/damage flag
- Auditable corrections

### F. Yard check

- Select yard
- Snapshot expected trailers
- QR scan found trailers
- Missing list
- Unexpected list
- Completion history

### G. Alerts

Initial required candidate:

- no trailer movement for configurable period, with 3 days as discovery baseline.

Additional alert types should not delay MVP unless validated as blocking.

### H. Customer portal

- Customer user accounts
- Customer-scoped trailer inventory
- Customer-scoped job/order visibility
- Current trailer location/status/content
- Request movement (subject to final approval workflow)
- View allowed documents/evidence

### I. Auditability

- who performed important action;
- when action occurred;
- correction history;
- assignment/reassignment history.

## Candidate MVP geolocation behavior

- Store known location coordinates/geofence.
- Use geofence-assisted arrival/departure where mobile OS behavior allows.
- Allow explicit confirmation/fallback.
- Do not require continuous route recording.

This remains subject to technical validation and privacy policy.

## Out of scope for MVP

Unless scope is explicitly changed:

- full payroll;
- wage calculation;
- accounting/invoicing;
- ELD/HOS;
- continuous live fleet map;
- route optimization;
- fuel management;
- full preventive-maintenance system;
- RFID/NFC hardware;
- custom telematics hardware;
- deep Samsara integration;
- Amtech integration;
- AI optimization;
- customer billing;
- marketplace/load-board features.

## Future candidates

- Samsara integration
- Amtech integration
- richer maintenance
- analytics and efficiency KPIs
- route/ETA intelligence
- configurable customer workflows
- webhooks/API
- bulk imports
- advanced reporting
- RFID/NFC
- automated POD extraction/OCR if justified
- payroll export

## MVP exit criteria — proposed

Before calling the MVP operationally successful:

1. A dispatcher can create and assign a job.
2. A driver can complete a representative drop-and-hook workflow using mobile.
3. Hook/drop establishes a trustworthy driver/tractor/trailer relationship.
4. Trailer search returns a useful current state and history.
5. Yard check detects missing/unexpected trailers.
6. Delivery evidence can be attached and retrieved.
7. Customer user can see only its own data.
8. Corrections are auditable.
9. Critical driver actions can survive temporary connectivity loss.
10. Tenant data cannot be accessed across tenants in authorization tests.
