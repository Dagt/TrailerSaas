# Core Workflows

**Status:** Draft refined after second discovery round

## WF-001 — Customer creates order

1. Authorized customer-side dispatcher/shipping user creates an Order.
2. Order may include:
   - order number;
   - PO;
   - shipment number;
   - BOL/reference;
   - origin;
   - one or more destinations/stops;
   - appointment/date information;
   - known load information.
3. Order appears automatically in the tenant's operational queue.
4. No separate approval request is required in the design-partner workflow.
5. Customer may modify/cancel before the final allowed cutoff; exact state cutoff remains open.

## WF-002 — Transport operations plans trip/run

1. Shipping/dispatcher reviews pending orders.
2. One Trip/Run can serve one or more Orders.
3. Dispatcher defines stop sequence.
4. Driver is assigned.
5. Tractor is assigned.
6. Trailer is assigned if known.
7. Trailer may remain unknown until pickup/BOL handling.
8. Driver receives the Trip on mobile.
9. Any reassignment is audited.

## WF-003 — Driver starts shift and pre-trip

1. Driver opens mobile app.
2. Driver marks **On Duty**.
3. Driver begins Pre-Trip.
4. Driver records odometer/mileage.
5. Driver captures required tractor photos.
6. Driver completes pre-trip confirmation.
7. Driver proceeds to office/dispatch instructions.

The MVP inspection flow is operational evidence, not a full regulatory inspection product.

## WF-004 — Hook assigned trailer

1. Driver opens active Trip.
2. Driver selects/scans trailer QR.
3. System resolves unique internal Trailer ID.
4. System validates assignment/availability.
5. If correct:
   - driver confirms hook;
   - system records HOOKED event;
   - current driver/tractor/trailer coupling is updated.
6. If wrong trailer is scanned:
   - app warns/blocks normal continuation;
   - driver contacts dispatch;
   - dispatcher must authorize substitution;
   - substitution is audited.

Driver cannot silently take a different trailer because it may belong to another order.

## WF-005 — Depart base

1. Driver marks departure manually.
2. System records DEPARTED_LOCATION.
3. Active trailer becomes IN_TRANSIT / on route.
4. Trip moves into active transit state.

Departure is manual in the current preferred workflow.

## WF-006 — Optional fuel event

During an active Trip:

1. Driver selects **Fuel**.
2. Records mileage/odometer.
3. Records fuel total/details required by final field definition.
4. Event is associated with driver, tractor and trip.

This is lightweight logging only; full fuel management is out of scope.

## WF-007 — Arrive destination

Preferred behavior:

1. If location permission is available, geofence may detect probable arrival.
2. App prompts: **“It looks like you arrived — confirm.”**
3. Driver confirms arrival.
4. Manual arrival remains available.
5. System records ARRIVED_LOCATION.
6. Dwell time becomes derivable.

Continuous route tracking is not required.

## WF-008 — Live Load / Live Unload

1. Driver arrives.
2. Driver selects Loading or Unloading.
3. Trailer remains coupled.
4. Load/operational state changes as appropriate.
5. Delivery/load evidence is captured.
6. Driver marks departure manually.
7. Same trailer continues with driver.

## WF-009 — Drop & Hook

1. Driver arrives at destination.
2. Driver selects **Drop & Hook**.
3. Driver scans/confirms current trailer.
4. System records trailer dropped at customer/site.
5. Driver scans replacement trailer.
6. System validates replacement trailer.
7. System records new HOOKED event.
8. Trailer/tractor relationship changes audibly/auditably.
9. Trip continues with replacement trailer.

## WF-010 — Proof of delivery

Baseline flow:

1. Driver captures photo/file of signed BOL when receiver signature is available.
2. Evidence is associated with Order/Stop/Trip.
3. If nobody is available to sign:
   - driver records delivery evidence available;
   - system follows an exception path rather than blocking delivery indefinitely.
4. Standalone signature-on-screen is not required for MVP.

Evidence requirements may be configurable by customer later.

## WF-011 — Multi-stop trip

If one trailer/run has several deliveries:

1. Driver completes arrival/service/evidence/departure for Stop 1.
2. Trip advances to Stop 2.
3. Repeat for each destination.
4. Orders may differ by stop.
5. Trailer may remain same or change through Drop & Hook.
6. History must retain which orders/trailer applied to each segment.

## WF-012 — Return to base and complete trip

1. Driver marks arrival at base.
2. Current trailer location becomes yard/base where appropriate.
3. Driver completes Post-Trip.
4. Driver marks **Off Duty**.
5. Trip/Run is completed according to final state rules.

Discovery identifies return to base as the normal completion point for the driver's described task/run.

## WF-013 — Yard / Spotter mode

1. Yard/Spotter marks On Duty.
2. Completes Pre-Trip.
3. Selects Yard Mode.
4. Performs yard moves:
   - yard → dock;
   - dock → yard;
   - dock → dock where allowed.
5. Scans trailer QR for each move.
6. Records destination operational location.
7. Authorized yard user may update permitted trailer state/order/location values.
8. Every move is an auditable movement event.
9. User completes Post-Trip.
10. Marks Off Duty.

## WF-014 — Yard check

1. Authorized user selects facility/yard.
2. System snapshots expected trailers based on current recorded yard/dock location.
3. User scans physically found trailers.
4. At completion system identifies:
   - found;
   - missing;
   - unexpected;
   - recorded-location discrepancies.
5. For a discrepancy:
   - show warning;
   - require confirmation;
   - do not silently rewrite history.
6. Missing trailer can be marked LOCATION_UNKNOWN according to reconciliation flow.
7. Yard Check is saved in history.

## WF-015 — Trailer search

Search by visible identifier plus customer/fleet context where necessary.

Trailer detail should show:

- current location;
- dwell time;
- load state;
- operational state;
- current/last load manifest;
- last movement;
- last driver;
- last tractor;
- related orders;
- evidence/history.

Because displayed trailer numbers may not be globally unique, the UI must disambiguate duplicates.

## WF-016 — Trailer inactivity alert

1. System evaluates last meaningful movement.
2. If configured threshold is exceeded, create alert.
3. Administration receives in-app notification.
4. Discovery baseline is 3 days.

## WF-017 — Destination change

1. Customer/operations requests destination change.
2. Dispatcher authorizes it.
3. System preserves:
   - original destination;
   - new destination;
   - actor;
   - timestamp;
   - reason/comment where required.
4. Driver sees updated destination.

## WF-018 — Driver reassignment / recovery

Normal policy is to avoid mid-task driver changes.

If change is required:

1. Dispatch controls reassignment.
2. System closes/preserves prior driver's segment.
3. New driver receives remaining work.
4. History shows exactly which driver performed each segment/action.

## WF-019 — Trailer damage / OOS

1. Authorized user marks trailer OOS.
2. Uploads photos.
3. Adds optional comment.
4. Trailer is excluded from normal available inventory.
5. Full maintenance work-order/cost tracking is not part of MVP.

## WF-020 — Correct wrong operational data

1. Authorized role selects incorrect record/state.
2. Enters corrected value.
3. Adds reason.
4. System preserves previous value.
5. System records actor and timestamp.
6. Derived current state is updated.

Normal correction must not destructively erase operational history.
