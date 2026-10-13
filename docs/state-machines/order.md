# Order State Machine

**Status:** Accepted v0.1

## Purpose

An Order represents a customer shipping/commercial request. It is not the same as a Trip, Stop or Trailer.

One Order may require multiple trailers and may be served by one or more Trips/Stops.

## States

- `OPEN` — created and visible to operations; fulfillment has not started.
- `PLANNED` — at least one operational Trip/Stop allocation exists.
- `IN_PROGRESS` — fulfillment has begun.
- `PARTIALLY_DELIVERED` — at least one required delivery/allocation is complete while another remains.
- `DELIVERED` — all required delivery work is complete and required POD evidence exists.
- `COMPLETED` — administratively closed by an authorized Customer/Transport Operations actor.
- `CANCELLING` — cancellation was requested after physical execution had begun and operational resolution is still required.
- `CANCELLED` — order is closed without further fulfillment work.

## Normal transition map

```text
OPEN
 ├── plan fulfillment ─────────────► PLANNED
 └── cancel before departure ──────► CANCELLED

PLANNED
 ├── first execution begins ───────► IN_PROGRESS
 └── cancel before departure ──────► CANCELLED

IN_PROGRESS
 ├── some fulfillment completed ──► PARTIALLY_DELIVERED
 ├── all fulfillment completed ────► DELIVERED
 └── active cancellation ──────────► CANCELLING

PARTIALLY_DELIVERED
 ├── remaining fulfillment active ─► IN_PROGRESS
 ├── all fulfillment completed ────► DELIVERED
 └── cancel remainder ─────────────► CANCELLING

DELIVERED
 └── administrative close ─────────► COMPLETED

CANCELLING
 └── physical resolution complete ─► CANCELLED
```

## Transition rules

| Command / trigger | From | To | Authorized actor | Important side effects |
|---|---|---|---|---|
| Create Order | — | OPEN | Customer Dispatcher, permitted Transport Ops/Admin | audit create |
| Plan fulfillment | OPEN | PLANNED | Transport Ops; customer-side actions as permitted | associate Trips/Stops/manifests |
| Start fulfillment | PLANNED | IN_PROGRESS | system from first physical Trip execution | timestamp/event |
| Record partial delivery | IN_PROGRESS | PARTIALLY_DELIVERED | system | update fulfillment projection |
| Resume remaining fulfillment | PARTIALLY_DELIVERED | IN_PROGRESS | system | none beyond history |
| Mark all delivery obligations satisfied | IN_PROGRESS/PARTIALLY_DELIVERED | DELIVERED | system/domain policy | confirm POD requirements |
| Complete/close Order | DELIVERED | COMPLETED | Customer Dispatcher or Transport Ops/Admin | audit close |
| Cancel before departure | OPEN/PLANNED | CANCELLED | customer-side authority/authorized ops | audit reason; release allocations |
| Cancel after execution started | IN_PROGRESS/PARTIALLY_DELIVERED | CANCELLING | authorized customer/ops | create operational instruction |
| Finish cancellation handling | CANCELLING | CANCELLED | Transport Ops/system | preserve partial deliveries and final trailer/cargo disposition |

## Invariants

1. `COMPLETED` requires `DELIVERED` first.
2. `DELIVERED` requires all required delivery obligations to be terminal-successful.
3. Delivery evidence must satisfy the MVP POD rule before a delivery obligation counts as complete.
4. Cancelling an active Order never deletes completed Stops, POD or movement history.
5. An active cancellation may create return-to-base or reroute instructions.
6. Customer self-service edits are ordinary before departure; after departure they use an audited change/reroute/cancellation workflow.
7. `PARTIALLY_DELIVERED` is meaningful because one Order may span multiple trailers/stops.

## Editing vs state transitions

Changing descriptive fields before execution does not necessarily change Order state.

After execution begins, changes to destination/appointment/critical instructions must be handled as audited operational changes rather than generic CRUD updates.

## Suggested domain events

- `OrderCreated`
- `OrderPlanned`
- `OrderFulfillmentStarted`
- `OrderPartiallyDelivered`
- `OrderDelivered`
- `OrderCompleted`
- `OrderCancellationRequested`
- `OrderCancelled`
- `OrderRerouted`

## Issue #45 fulfillment projection

First real departure starts fulfillment of persistently allocated Orders through
the existing lifecycle port. A delivery checks all actual DELIVERY mappings plus
requested DELIVERY visits; one physical visit cannot satisfy two separate requested
visits at the same destination. Explicit order_stop_id is authoritative; otherwise
matching requires the requested Site/Location or frozen destination snapshot.
Missing/unmapped required work cannot count as delivered. Previous completed visits
require actual delivery_evidence; the current visit must have its real completion
row/event in the same transaction and then receive evidence atomically. Partial
work yields PARTIALLY_DELIVERED; all obligations yield DELIVERED. No administrative
COMPLETED and no silent CANCELLING/CANCELLED change. Deterministic Order locks precede
Trip locks, matching cancellation; immutable fulfillment facts prevent duplicate
recording. Planning and requested destinations remain separate from physical cargo.
