# Road Driver Mobile — UX Flow v0.1

**Status:** Accepted v0.1  
**Primary actor:** DRIVER  
**Related requirements:** FR-050–078, FR-080–084, FR-110–115, FR-140–144, FR-190–197  
**Related state machines:** Shift, Trip, Stop, Trailer

## Goal

The Driver app should make the required action obvious, minimize typing, tolerate temporary connectivity loss and preserve accurate operational history.

A Driver should rarely need to understand internal status names.

---

# 1. App start / Home

## If no open Shift

Primary CTA:

`Go On Duty`

Secondary information:

- Driver name;
- assigned/default Tractor if known;
- connectivity/sync status.

## If On Duty

Home shows:

- current active Trip, if any;
- next required action;
- today's assigned work;
- sync/offline status;
- quick access to Fuel and Report Problem when context allows.

Do not overload the home screen with tenant-wide data.

---

# 2. Go On Duty

Driver taps:

`Go On Duty`

Validation:

- Driver active;
- no other open Shift.

Success:

- Shift created;
- Home advances to Pre-Trip requirement.

If offline:

- action may be queued with client operation ID;
- UI shows locally accepted/pending sync status.

---

# 3. Pre-Trip

## Step 1 — Tractor

Confirm/select assigned Tractor.

If Driver has no authorized Tractor assignment, show a clear instruction to contact/receive assignment from operations.

## Step 2 — Odometer

Numeric entry.

## Step 3 — Required photos

Guided camera sequence:

1. Front
2. Rear
3. Driver side
4. Passenger side

Each step shows capture status.

## Step 4 — Checklist

Structured checklist defined later during UX/content design.

Primary CTA:

`Complete Pre-Trip`

## Result

Pre-Trip saved and Home can expose assigned work.

Media upload may remain pending while the structured operation is locally queued; app must show sync state clearly.

---

# 4. Today's Work

Show assigned Trips as simple cards.

Each card includes:

- first/next destination;
- appointment;
- Tractor;
- planned Trailer if known;
- number of Stops;
- Trip state.

Primary action:

`Open Trip`

The Driver sees only authorized/assigned work.

---

# 5. Trip detail before departure

Show:

- Stop sequence;
- current/next Stop;
- appointments;
- Orders/BOL references needed operationally;
- assigned Tractor;
- planned Trailer if known.

Primary next action depends on workflow.

Typical:

`Scan Trailer`

---

# 6. Scan / Hook Trailer

## Scanner

Open QR scanner with manual fallback only if operationally approved later.

After scan show:

```text
Trailer T114
Designated Customer: ...
Load/Order summary: ...
```

Primary action:

`Confirm Hook`

## Correct Trailer

On confirmation:

- queue/send HOOK operation;
- coupling updates;
- Trip can proceed.

## Wrong Trailer

If scanned Trailer differs from expected:

Show:

```text
This is not the assigned Trailer.

Assigned: T114
Scanned: T116

You need authorization before using T116.
```

Primary action:

`Request / Contact Dispatch`

The Driver cannot override the mismatch.

When authorization arrives, UI updates to permit the new Trailer.

---

# 7. Depart

Once prerequisites are satisfied:

Primary CTA:

`Depart`

Confirm if needed:

`Confirm departure from [location]`

On success:

- Trip → `IN_TRANSIT`;
- current Stop → `EN_ROUTE`;
- Trailer location state → `IN_TRANSIT`;
- departure event recorded.

Departure is manual.

---

# 8. En route screen

Keep screen simple.

Show:

- destination;
- appointment;
- Stop/service type;
- current Trailer;
- relevant Order/BOL references.

Available context actions:

- `Mark Arrived`
- `Fuel`
- `Report Problem`

No continuous route map is required.

## Geofence-assisted arrival

If location permission allows and app detects probable arrival:

Prompt:

```text
It looks like you arrived at [destination].
[Confirm Arrival] [Not Yet]
```

Manual `Mark Arrived` remains available.

---

# 9. Arrived / Stop service

After arrival, Stop state becomes `ARRIVED`.

Show service options based on Stop definition:

- Live Load;
- Live Unload;
- Drop & Hook;
- Delivery;
- other allowed service mode.

Primary action:

`Begin Service`

---

# 10. Live Load / Live Unload

Show current Trailer and service type.

Trailer remains coupled.

Driver completes required operational confirmation/evidence.

Once complete:

- load state may update;
- Stop can advance to completion/POD;
- Driver later marks departure manually.

---

# 11. Drop & Hook

## Step 1 — Drop current Trailer

Show current Trailer.

Primary action:

`Confirm Drop`

Record drop location.

## Step 2 — Scan replacement Trailer

Open QR scanner.

Validate replacement.

If wrong/not authorized, block and use the mismatch authorization flow.

## Step 3 — Confirm Hook

Record new coupling.

## Result

UI clearly shows:

```text
Dropped: T114
Now hooked: T116
```

Then continue the Stop/POD flow.

---

# 12. Proof of Delivery

## Normal signed-BOL path

Driver captures signed BOL photo.

Primary action:

`Complete Delivery`

## No receiver available

Driver selects:

`No Receiver Available`

Required:

- BOL photo;
- delivery photo;
- comment.

Then:

`Complete Delivery`

The app must not require an on-screen signature in MVP.

---

# 13. Complete Stop

After service and evidence requirements are satisfied:

Stop becomes `COMPLETED`.

If another Stop exists:

Primary CTA:

`Continue to Next Stop`

Trip advances next Stop to `EN_ROUTE` when departure occurs.

If the Trip requires return to base, it appears as the next/final Stop.

If no Stops remain, Trip may become `COMPLETED` according to the accepted state machine.

---

# 14. Depart Stop

Departure is manual.

Primary CTA:

`Depart`

On confirmation:

- departure timestamp/event recorded;
- next Stop becomes the active destination when applicable;
- Trailer remains coupled unless Drop & Hook changed it.

---

# 15. Fuel

Available during active Shift/Trip.

Required:

- odometer/mileage;
- receipt photo.

Primary action:

`Save Fuel Event`

No gallons/cost calculations in MVP.

May be captured offline and uploaded/synced later.

---

# 16. Report Problem

This is not a generic status editor.

Proposed categories:

- delay/traffic;
- trailer/equipment damage;
- destination/customer issue;
- load rejected;
- other.

Required fields vary by category.

For damage where appropriate:

- photos;
- comment;
- ability for authorized downstream process to mark Trailer OOS.

This flow raises an operational exception without rewriting Trip history.

---

# 17. Reroute / changed instructions

If Operations changes destination/instructions while Driver is active:

Show a prominent in-app update:

```text
Trip Updated

Previous destination: ...
New destination: ...

[Review New Instructions]
```

Driver must not lose the previous instruction history, but normal Driver UI should focus on the new valid next action.

If push was missed, state still appears after app refresh/sync.

---

# 18. Active cancellation

If an active Order/Trip is cancelled:

Driver sees explicit instruction generated by Operations, such as:

- Return to base;
- Proceed to new destination;
- Hold/contact Dispatch.

Do not simply make the Trip disappear.

---

# 19. Return to base

If return to base is a required Trip Stop:

- arrive/confirm as a normal Stop;
- current Trailer returns to the recorded base/yard context as appropriate;
- complete required Stop action.

Trip completion remains independent from Shift completion.

---

# 20. Post-Trip

At end-of-day/equipment workflow:

Required:

- final odometer;
- damages;
- failures/problems;
- comments.

Post-Trip photos are not mandatory under current requirements.

Primary action:

`Complete Post-Trip`

---

# 21. Go Off Duty

Available after required end-of-day workflow.

Primary CTA:

`Go Off Duty`

If an active Trip remains:

Block or show:

```text
You still have active work.
Complete or resolve the active Trip before going Off Duty.
```

No silent auto-completion.

Success closes the Shift.

---

# 22. Offline UX

## Persistent sync indicator

Example states:

- Online / Synced
- Offline — actions saved on this device
- Syncing
- Attention required

## Critical offline-capable actions

At minimum:

- On Duty;
- Pre-Trip structured data;
- Hook/Drop;
- Depart;
- Arrive;
- Stop service actions;
- POD metadata;
- Fuel event;
- Post-Trip;
- Off Duty where safe.

Each uses a client operation ID.

## Media

Photos may remain queued separately from structured events.

UI must distinguish:

```text
Delivery recorded locally
2 photos waiting to upload
```

Do not claim full sync until required evidence is uploaded/acknowledged.

## Conflict

If server rejects a queued operation because state changed:

Do not discard it silently.

Show an actionable sync issue and retain enough local context for reconciliation.

---

# 23. Mobile navigation

Recommended MVP navigation while On Duty:

```text
Home
Work
Alerts/Updates
Profile
```

However, when a Trip is active, the app should strongly foreground the active Trip rather than require repeated navigation.

Exact tab structure is an open visual-design detail.

---

# 24. Driver safety / distraction principle

The app should avoid encouraging complex interaction while the vehicle is moving.

Operational forms, photo capture, scanning and route changes should be designed for use while stopped.

Exact safety warnings/lockout behavior should be evaluated during mobile implementation and usability testing.

---

# 25. Acceptance criteria for wireframing

This flow is ready for visual wireframes when:

1. Driver can start Shift and complete Pre-Trip.
2. Driver can see assigned Trip and Stops.
3. Driver can scan/Hook the correct Trailer.
4. Wrong Trailer cannot be silently used.
5. Driver can manually Depart and confirm/manual Arrive.
6. Live Load/Unload and Drop & Hook have deterministic flows.
7. POD and No Receiver paths are explicit.
8. Multi-Stop Trip is supported.
9. Reroute/cancellation does not make work disappear.
10. Return-to-base is handled as a Stop when required.
11. Post-Trip and Off Duty remain separate from Trip completion.
12. Critical actions have defined offline behavior.

## Issue #44 implementation note

The implemented Road detail shows assigned Stops, human destinations/instructions, Tractor, Expected Trailer separately from physical coupling, QR-guided explicit Hook/Drop and state-aware online execution controls. Every action requires confirmation and an authoritative server result. At the #44 boundary, an uncertain submission was recoverable with its saved key; offline downloaded work did not yet record commands. #46 adds the bounded persistent queue described below. First departure preserves the required #43 On Duty/Pre-Trip block; Delivery/POD remains #45/#53. See [bounded implementation and deferred integrations](../mobile/issue-44-road-execution.md).

## Issue #46 implementation note

Road now records previously authorized/downloaded work as immutable pending intent. The synchronization panel is available even without assigned Trips, shows confirmations and review/blocking issues, and supports manual synchronization. A pending action guide links physical actions through explicit dependencies while keeping downloaded Trip/Stop state unchanged. Offline actions are never presented as confirmed server execution. Foreground/reconnect replay uses the original Idempotency-Key and refreshes current server work. First departure remains blocked until real #43 prerequisites exist, and Delivery/POD, media and Yard remain separate. See [queue migration, security and replay policy](../mobile/issue-46-command-replay.md).

## Issue #43 implementation note

Road home includes real Shift state, On Duty, four-photo Pre-Trip, Post-Trip and
Off Duty. The Pre-Trip Tractor selector deduplicates active equipment from current
nonterminal assigned Trips. No eligible equipment shows: "No tienes un Tractor
asignado. Contacta a operaciones." A Shift is usable without a selected Trip;
inspection without any assignment requires a future assignment model.
Native camera capture writes to private #54 media storage. Local draft/photos,
upload/finalization and accepted inspection are distinct. Offline commands retain
intention/IDs; Pre-Trip waits for real On Duty result and all four finalized photos.
Departure remains subject to fresh server checks. Completed Trips do not close
Shift, and Post-Trip does not perform OOS/exception workflow. #45 remains deferred.
