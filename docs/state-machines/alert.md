# Alert State Machine

**Status:** Proposed v0.1

## Purpose

Alerts represent operational conditions requiring attention.

Push notification delivery is a channel; the persisted Alert is the source of truth.

Examples:

- stale Trailer;
- missing Trailer;
- reroute/change attention;
- operational exception.

## States

- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`

## Transition map

```text
(condition detected)
     └─────────────────────────────► OPEN

OPEN
 ├── user acknowledges ────────────► ACKNOWLEDGED
 └── condition resolves ───────────► RESOLVED

ACKNOWLEDGED
 └── condition resolves ───────────► RESOLVED
```

## OPEN

Created when an alert-producing condition occurs.

Side effects may include:

- push notification through FCM;
- badge/inbox visibility;
- association with Trailer/Trip/Order/etc.

Push failure does not change Alert state.

## ACKNOWLEDGED

Means an authorized user has seen/accepted responsibility for the Alert.

Acknowledgement does **not** mean the underlying problem is solved.

Record:

- acknowledged_by;
- acknowledged_at.

## RESOLVED

The condition no longer requires attention.

Resolution can be:

- explicit by authorized user; or
- automatic when the underlying condition is corrected.

Examples:

- missing Trailer is scanned and location recovered;
- stale Trailer moves;
- reroute instruction is accepted/handled.

## Recurrence

If the same condition happens again after resolution, prefer creating a new Alert occurrence rather than mutating historical timestamps of the old Alert.

## Suggested domain events

- `AlertOpened`
- `AlertAcknowledged`
- `AlertResolved`
