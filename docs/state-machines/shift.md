# Driver Shift State Machine

**Status:** Proposed v0.1

## Purpose

Shift tracks only the Driver's operational On Duty / Off Duty window for MVP.

It is not a payroll engine and does not model breaks, lunch or wage calculations.

## States

- `ON_DUTY`
- `OFF_DUTY` (terminal state of a recorded Shift)

Operationally, no open Shift row means the Driver is currently Off Duty.

## Transition map

```text
(no open Shift)
   └── mark On Duty ───────────────► ON_DUTY

ON_DUTY
   └── mark Off Duty ──────────────► OFF_DUTY / Shift closed
```

## On Duty command

Requirements:

- Driver is active;
- Driver does not already have another open Shift.

Side effects:

- create Shift row;
- record `on_duty_at`;
- emit `DriverWentOnDuty`.

## Off Duty command

Requirements:

- an open Shift exists;
- any mandatory end-of-day Post-Trip workflow applicable to the driver's current equipment has been handled, unless an authorized override/correction is used.

Side effects:

- set `off_duty_at`;
- emit `DriverWentOffDuty`.

## Important separation

A Driver may complete multiple Trips during one Shift.

Therefore:

- Stop completion ≠ Trip completion;
- Trip completion ≠ Shift completion;
- Off Duty must not automatically complete active Trips or Orders.

If an active Trip remains when Off Duty is attempted, the system should block or require an explicit authorized exception rather than silently closing it.

## Corrections

If On/Off Duty timestamps need correction:

- do not rewrite without history;
- preserve previous/new value, actor, timestamp and reason.

Whether these timestamps are official payroll records remains a production-policy/compliance decision.

## Suggested domain events

- `DriverWentOnDuty`
- `DriverWentOffDuty`
- `ShiftTimeCorrected`
