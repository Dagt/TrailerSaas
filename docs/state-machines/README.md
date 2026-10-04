# State Machines

**Status:** Accepted v0.1  
**Date:** 2026-10-04

These state machines convert the approved discovery/business rules into explicit lifecycle behavior before API and UI implementation.

## Machines

- [Order](order.md)
- [Trip / Run](trip.md)
- [Stop / Delivery](stop.md)
- [Trailer](trailer.md)
- [Driver Shift](shift.md)
- [Yard Check](yard-check.md)
- [Alert](alert.md)

## Design rules

1. State transitions happen through explicit commands/use cases, not arbitrary field updates.
2. Authorization is capability-based and tenant/customer scoped.
3. Every important transition records actor and timestamp.
4. Mobile-originated transitions that can run offline use idempotent client-operation IDs.
5. Corrections do not destructively rewrite history.
6. A business exception should not become a permanent lifecycle state unless it materially changes what actions are legal.
7. Delivery completion, Trip completion and Shift completion are separate concepts.
8. Trailer status is intentionally split into orthogonal dimensions rather than one giant enum.

## Implementation pattern

Conceptually:

```text
Command
  ↓
Authorization
  ↓
Current-state / invariant validation
  ↓
Transition
  ↓
Persist current state
  ↓
Append operational/audit event
  ↓
Side effects (alerts, push, projections)
```

These documents are the source of truth for valid transitions once accepted.
