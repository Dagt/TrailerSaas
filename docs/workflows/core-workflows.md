# Core Workflows

**Status:** Proposed from discovery. Must be validated before implementation.

## WF-001 — Create and dispatch a job

1. Shipping/dispatcher receives customer order information.
2. User creates or imports an Order.
3. User provides identifiers such as order number, PO, shipment number and BOL reference when available.
4. User creates a Job/Movement with origin, destination and appointment.
5. Driver is assigned.
6. Tractor is assigned.
7. Trailer is assigned if already known; otherwise assignment may remain pending.
8. Driver receives job in mobile app.
9. Assignment changes remain possible while allowed by job state.
10. Reassignment is audited.

### Exception paths

- urgent order;
- appointment changed;
- destination changed;
- driver/tractor reassigned;
- trailer unknown until pickup.

## WF-002 — Driver starts job

1. Driver opens assigned job.
2. Driver reviews origin, destination, appointment and known documents.
3. Driver starts job.
4. Pre-trip inspection remains part of real operation, but whether the SaaS records a formal inspection checklist is **not yet confirmed**.
5. Driver proceeds to trailer pickup.

## WF-003 — Hook trailer

Preferred UX:

1. Driver taps **Hook trailer**.
2. Driver scans durable QR on trailer.
3. App resolves trailer identity.
4. System validates whether trailer is expected/allowed for job.
5. Driver confirms.
6. System records HOOKED event with:
   - trailer;
   - job/order;
   - driver;
   - tractor;
   - timestamp;
   - location/source.
7. Current trailer/job relationship is updated from the event.

### Mismatch behavior

If scanned trailer differs from expected assignment, the system should not silently accept it. Exact options are open:

- block and require dispatcher approval;
- warn and allow with reason;
- auto-reassign under policy.

Tracked as an open question.

## WF-004 — Depart origin

Proposed hybrid design:

1. Driver or geofence indicates departure.
2. App may prompt a one-tap confirmation.
3. DEPARTED_LOCATION event is recorded.
4. Job transitions to in-transit state if valid.

The MVP should avoid continuous route tracking unless separately approved.

## WF-005 — Arrive destination

1. Geofence may detect arrival at known destination.
2. App prompts driver to confirm if required.
3. ARRIVED_LOCATION event is recorded.
4. Dwell timer becomes derivable from timestamps.
5. Driver follows customer shipping/receiving instructions.

## WF-006 — Drop and hook

For a typical drop-and-hook delivery:

1. Driver arrives destination.
2. Driver selects **Drop trailer**.
3. Driver scans current trailer QR.
4. App records drop location and DROP event.
5. Driver uploads required POD/BOL/photo evidence.
6. If taking another trailer:
   - selects **Hook trailer**;
   - scans replacement trailer;
   - system records HOOK event.
7. Job can continue or complete according to route/order structure.

## WF-007 — Live load

1. Driver arrives destination.
2. Current trailer remains connected unless operation dictates otherwise.
3. Loading/unloading state may change without trailer drop.
4. Evidence is captured.
5. Driver departs with same trailer.
6. Job completes or continues.

The exact load-state transitions require validation.

## WF-008 — Proof of delivery

Candidate evidence:

- BOL photo/document;
- signed BOL;
- delivery photos;
- timestamp;
- job and customer association.

Standalone digital signature is unresolved.

## WF-009 — Yard check

1. Authorized user chooses yard/location.
2. System computes/snapshots expected trailers.
3. User starts Yard Check.
4. User scans trailer QR codes while walking yard.
5. Each scan is matched to expected inventory.
6. User completes check.
7. System presents:
   - expected count;
   - found count;
   - missing trailers;
   - unexpected trailers.
8. Corrections are handled as auditable follow-up actions rather than silently rewriting history.

## WF-010 — Trailer search

User searches by trailer ID.

Result should show at minimum:

- current location;
- time at location;
- load state;
- operational state;
- current/last order;
- last movement;
- last driver;
- last tractor;
- evidence/history links.

## WF-011 — Trailer inactivity alert

1. System evaluates trailer's last meaningful movement.
2. If threshold is exceeded (discovery example: 3 days), create alert.
3. Administration receives in-app alert.
4. Alert remains actionable/acknowledgeable according to final alert design.

Threshold should be tenant-configurable unless product decision says otherwise.

## WF-012 — Driver shift

1. Driver starts shift.
2. System records shift-start timestamp.
3. Driver performs jobs.
4. Driver ends shift.
5. System records shift-end timestamp.

MVP may expose these timestamps for reporting, but does not calculate wages/payroll.

## WF-013 — Correct wrong operational data

1. Authorized user selects incorrect state/location/event.
2. User provides correction and reason where required.
3. System preserves original information.
4. System records correction event/audit entry.
5. Derived current state is updated.
6. User/actor and timestamp remain visible.

Never implement destructive history replacement as the normal correction mechanism.
