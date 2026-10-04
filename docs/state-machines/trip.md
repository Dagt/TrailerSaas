# Trip / Run State Machine

**Status:** Accepted v0.1

## Purpose

A Trip/Run is the operational execution unit assigned to a Driver/Tractor and containing an ordered list of Stops.

A Shift may contain multiple Trips. Off Duty does not complete a Trip automatically.

## States

- `PLANNED` — route/Stops exist but required execution assignments are incomplete.
- `ASSIGNED` — Driver and Tractor are assigned and the Trip can be prepared for execution.
- `IN_TRANSIT` — Driver is physically moving toward the next active Stop.
- `AT_STOP` — Driver has arrived at the current Stop.
- `CANCELLING` — the Trip is being safely terminated after execution began.
- `COMPLETED` — all required Stops for this Trip are terminal and no operational work remains.
- `CANCELLED` — Trip is terminated and no further execution is expected.

Operational incidents/delays are events/flags, not a permanent `EXCEPTION` lifecycle state.

## Transition map

```text
PLANNED
 ├── assign driver + tractor ──────► ASSIGNED
 └── cancel ───────────────────────► CANCELLED

ASSIGNED
 ├── first departure ──────────────► IN_TRANSIT
 └── cancel before movement ───────► CANCELLED

IN_TRANSIT
 ├── arrive current Stop ──────────► AT_STOP
 ├── begin active cancellation ────► CANCELLING
 └── final transition complete ────► COMPLETED (only if no Stops remain)

AT_STOP
 ├── depart toward next Stop ──────► IN_TRANSIT
 ├── complete final Stop/Trip ─────► COMPLETED
 └── begin active cancellation ────► CANCELLING

CANCELLING
 └── safe physical resolution ─────► CANCELLED
```

## Assignment rules

`PLANNED → ASSIGNED` requires:

- active Driver;
- active Tractor;
- at least one executable Stop.

Trailer may still be unknown if the workflow determines it at pickup.

## Start rule

A Trip becomes `IN_TRANSIT` when the Driver records the first actual departure for the Trip.

Before departure, domain policy may require:

- Driver is On Duty;
- required Pre-Trip completed for the active Tractor;
- required paperwork/instructions available;
- assigned/authorized trailer confirmed when applicable.

## Completion rule

A Trip is `COMPLETED` when:

1. all required Trip Stops are terminal (`COMPLETED` or deliberately `CANCELLED`);
2. there is no unresolved active-cancellation instruction;
3. any required final/base-return Stop that belongs to this Trip is complete.

A return to base is therefore modeled as a Stop when it is part of the Trip, not as a universal hard-coded rule.

This separates Trip completion from Off Duty.

## Reroute rule

A reroute normally does **not** change Trip lifecycle state.

Instead it:

- changes/adds future Stop instructions;
- preserves original route/destination in audit history;
- notifies the Driver;
- keeps the Trip `IN_TRANSIT` or `AT_STOP` as physically appropriate.

## Active cancellation

If cancellation occurs after movement starts:

1. Trip enters `CANCELLING`.
2. Operations provide a safe physical resolution:
   - return to base;
   - redirect;
   - unload/drop at an authorized location.
3. Required movement/Stop events are recorded.
4. Once no further work remains, transition to `CANCELLED`.

## Driver reassignment

Driver reassignment is exceptional.

It does not erase prior assignment history.

If needed:

- close the previous assignment segment;
- assign new Driver through authorized dispatch;
- preserve actor/timestamps;
- Trip stays in its physical lifecycle state unless the operation itself changes.

## Suggested domain events

- `TripPlanned`
- `TripAssigned`
- `TripDeparted`
- `TripArrivedAtStop`
- `TripDepartedStop`
- `TripRerouted`
- `TripCancellationStarted`
- `TripCancelled`
- `TripCompleted`
- `TripDriverReassigned`
- `TripTractorReassigned`
