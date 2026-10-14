# Yard Check State Machine

**Status:** Accepted v0.1

## Purpose

A Yard Check is a point-in-time reconciliation between:

- trailers expected at a Site/Yard/Dock set; and
- trailers physically observed/scanned.

## States

- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

## Transition map

```text
(start)
  └── create expected snapshot ────► IN_PROGRESS

IN_PROGRESS
  ├── finish scan/reconcile ───────► COMPLETED
  └── abort ───────────────────────► CANCELLED
```

## Start

When started:

1. select Site/Yard context;
2. snapshot expected Trailer set from current recorded Yard/Dock locations;
3. persist expected locations;
4. record user and start timestamp.

The snapshot must not change just because live inventory changes while the check is running.

## During IN_PROGRESS

Each QR scan can produce:

- `MATCHED`
- `UNEXPECTED`
- `LOCATION_MISMATCH`

Duplicate scan of the same Trailer should be idempotent within the Yard Check.

A location mismatch should warn the user; it must not silently correct current location.

## Complete

Completion:

1. mark unobserved expected Trailers as `MISSING`;
2. preserve all results;
3. set completed timestamp;
4. transition to `COMPLETED`.

A completed Yard Check becomes immutable as a historical snapshot.

## Reconciliation after completion

Reconciliation is performed through separate auditable commands.

For a confirmed missing Trailer:

- Trailer location state may become `LOCATION_UNKNOWN`;
- last known location/seen/Driver remain visible;
- Administration alert is created;
- Administration owns investigation.

For a location mismatch:

- authorized user may confirm correction;
- correction appends movement/audit history.

## Cancel

Cancellation does not apply reconciliation side effects.

The partial scan may be retained for audit/diagnostics but must not be treated as a completed inventory result.

## Suggested domain events

- `YardCheckStarted`
- `YardCheckTrailerScanned`
- `YardCheckCompleted`
- `YardCheckCancelled`
- `YardCheckMissingTrailerConfirmed`
- `YardCheckLocationMismatchConfirmed`

## Issue #50 implementation boundary

The Backend implements this accepted lifecycle with V24 snapshots/items, nullable
pending result per hardening D and separate append-only reconciliation associations.
Check/items are serialized and terminal findings are immutable; reconciliation
never rewrites the completed result. UUID ledger replay returns the original
receipt under fresh authority, while a new terminal command rejects. Completion
and cancellation timestamps/actors are server-owned; scans retain original time.

Missing reconciliation explicitly invokes the existing markLocationUnknown engine
and commits Trailer/event/association/ledger plus one real Administration Alert.
The owner-authorized provisional Alert policy is TRAILER_MISSING/WARNING/OPEN,
Trailer and minimal Check/item/event/actor metadata, no individual recipients or
notification delivery. OQ-059 stays open. No automatic reconciliation or Alert is
produced by scan/completion. Mobile Check implementation remains #51.

See [implementation, acceptance, concurrency and handoff](../mobile/issue-50-yard-check-backend.md).
