# Stop / Delivery State Machine

**Status:** Proposed v0.1

## Purpose

A Stop represents one operational visit inside a Trip.

Examples:

- pickup;
- delivery;
- live load;
- live unload;
- drop & hook;
- base return.

Service mode is an attribute/type of the Stop and should not be confused with lifecycle state.

## States

- `PENDING` — Stop is planned but not yet the active destination.
- `EN_ROUTE` — Driver is travelling toward this Stop.
- `ARRIVED` — arrival has been confirmed.
- `IN_SERVICE` — loading/unloading/drop-hook/delivery activity is underway.
- `EXCEPTION` — the planned service cannot currently complete normally.
- `COMPLETED` — Stop obligations and required evidence are satisfied.
- `CANCELLED` — Stop will not be executed.

## Transition map

```text
PENDING
 ├── activate as next destination ─► EN_ROUTE
 └── cancel ───────────────────────► CANCELLED

EN_ROUTE
 ├── confirm arrival ──────────────► ARRIVED
 ├── reroute/cancel stop ──────────► CANCELLED
 └── operational exception ────────► EXCEPTION

ARRIVED
 ├── begin service ────────────────► IN_SERVICE
 ├── operational exception ────────► EXCEPTION
 └── cancel ───────────────────────► CANCELLED

IN_SERVICE
 ├── satisfy service + evidence ───► COMPLETED
 └── operational exception ────────► EXCEPTION

EXCEPTION
 ├── resume service ───────────────► IN_SERVICE
 ├── retry travel / new instruction► EN_ROUTE
 └── abandon this Stop ────────────► CANCELLED
```

## Arrival

Arrival may be:

- suggested by geofence/location;
- explicitly confirmed by Driver;
- manually recorded if location services are unavailable.

Geofence detection alone does not have to finalize the arrival.

## Service modes

### Live Load / Live Unload

Trailer remains coupled.

Stop service records loading/unloading activity and then POD/service completion.

### Drop & Hook

During `IN_SERVICE`:

1. current Trailer is dropped at the Stop location;
2. replacement Trailer is scanned/authorized;
3. replacement Trailer is hooked;
4. related movement events are appended;
5. Stop can complete once required service/evidence is satisfied.

### Delivery

Required POD rule for MVP:

- signed BOL photo when receiver signs; or
- if no receiver:
  - `NO_RECEIVER_AVAILABLE` reason;
  - BOL photo;
  - delivery photo;
  - comment.

## Exception examples

- customer closed;
- load rejected;
- damaged trailer/load;
- no authorized replacement trailer;
- destination changed while en route.

Exception details should be captured separately from lifecycle status.

## Completion invariant

A delivery Stop cannot become `COMPLETED` until its required service actions and POD evidence are satisfied.

Completing a Stop does **not** automatically:

- complete the whole Order;
- complete the Trip;
- mark the Driver Off Duty.

## Suggested domain events

- `StopActivated`
- `StopArrivalConfirmed`
- `StopServiceStarted`
- `StopExceptionRaised`
- `StopExceptionResolved`
- `StopCompleted`
- `StopCancelled`
