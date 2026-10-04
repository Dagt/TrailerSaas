# Yard / Spotter Mobile — UX Flow v0.1

**Status:** Proposed v0.1  
**Primary actor:** YARD_SPOTTER  
**Related requirements:** FR-050–055, FR-090–103, FR-120–136, FR-170–174  
**Related state machines:** Shift, Trailer, Yard Check, Alert

## Goal

Provide a fast mobile workflow for physical Trailer movement and Yard inventory reconciliation.

The Yard/Spotter experience shares authentication, Shift, Pre/Post-Trip, QR and sync infrastructure with Road Driver Mode, but has a distinct operational home and actions.

---

# 1. App start / mode

If the user has only YARD_SPOTTER role, app opens directly to Yard Mode after Shift start.

If the user has multiple roles, show a simple mode choice where needed:

```text
Road Mode
Yard Mode
```

Do not require separate mobile apps.

---

# 2. On Duty

If no open Shift:

Primary CTA:

`Go On Duty`

Same Shift rules as Road Driver:

- active user/Driver profile;
- no duplicate open Shift;
- offline queue supported.

After On Duty:

- require Pre-Trip if applicable to the yard Tractor/equipment workflow.

---

# 3. Pre-Trip

Use the same baseline operational inspection pattern unless the tenant later defines a yard-specific checklist:

- odometer;
- front photo;
- rear photo;
- driver-side photo;
- passenger-side photo;
- checklist.

Primary CTA:

`Complete Pre-Trip`

After completion, enter Yard Home.

---

# 4. Yard Home

The Yard Home should make the two high-frequency actions obvious:

```text
[ Move Trailer ]
[ Start Yard Check ]
```

Secondary information:

- selected/current Site;
- open Yard Check, if any;
- sync status;
- recent moves;
- attention alerts relevant to yard work.

Optional quick search:

- Trailer number.

---

# 5. Select Site / Yard context

Before yard operations, user must have an active Site/Yard context.

Show authorized/relevant Sites.

Example:

```text
Hood Container — 4800 Austin Blvd
Stanfast / Black Hawk — 710 Kimberly Dr
```

Within a physical Site, show operational areas/locations separately.

Do not confuse street address with Customer/Dock context.

---

# 6. Move Trailer

## Step 1 — Scan Trailer

Open QR scanner.

After scan show:

- Trailer number;
- designated Customer;
- recorded current location;
- load state;
- serviceability;
- current Order/content summary where authorized.

## Step 2 — Validate

If Trailer is OOS or otherwise incompatible with requested move, show warning/block according to domain policy.

If recorded location differs from where it is physically found:

Show:

```text
Location mismatch

Recorded: Yard A
Observed: Dock 17
```

Do not silently overwrite.

## Step 3 — Choose destination

Destination selector is constrained to the active Site/context:

- Yard;
- Dock;
- another permitted operational location.

## Step 4 — Confirm move

Summary:

```text
Trailer T114
From: Yard
To: Dock 17
```

Primary CTA:

`Confirm Move`

Result:

- `YARD_MOVED` event;
- current location projection updated;
- actor/time preserved.

---

# 7. Location mismatch correction

If a mismatch is discovered during a normal yard move and the Spotter has correction capability:

Offer:

`Correct Recorded Location`

Require:

- observed location;
- reason/comment.

Show previous vs corrected value.

This is an audited correction, not a destructive edit.

If the user lacks capability, show:

`Report discrepancy`

and do not alter authoritative location.

---

# 8. Trailer state/order updates

Discovery indicates Yard Spotter may modify certain Trailer order/status variants.

MVP UX should expose only explicit allowed commands, for example:

- correct location;
- update load state where authorized;
- mark/report OOS;
- attach/update current operational Order context if the approved backend capability permits it.

Do not expose a generic free-form status editor.

Each action is capability and state checked.

---

# 9. Mark / report OOS

If damage is found:

Primary action:

`Report Damage / Mark OOS`

Required:

- photos;
- comment/context.

Result:

- Trailer serviceability → OUT_OF_SERVICE when actor has authority;
- Trailer removed from normal available-equipment selection;
- event/audit history created.

Returning OOS → IN_SERVICE remains a separate authorized action/policy.

---

# 10. Start Yard Check

## Select context

Choose:

- Physical Site;
- specific Yard/operational area where required.

Primary action:

`Start Yard Check`

On start:

- expected Trailer snapshot is created;
- check state → IN_PROGRESS.

Display:

```text
Expected: 34
Scanned: 0
Remaining: 34
```

---

# 11. Yard Check scan loop

The main screen should be optimized for repeated QR scanning.

After each scan show immediate result:

### MATCHED

```text
T114
Matched — Yard
```

### UNEXPECTED

```text
T116
Not expected in this Yard
```

### LOCATION_MISMATCH

```text
T120
Recorded: Dock 8
Observed: Yard
```

Duplicate scan:

```text
Already scanned
```

must not create duplicate result rows.

---

# 12. Yard Check progress

Persistent summary:

- Expected;
- Scanned;
- Matched;
- Unexpected;
- Location mismatches;
- Remaining.

Allow user to inspect Remaining list before completion.

The expected snapshot must not change because another user moves a Trailer after the Yard Check started.

---

# 13. Complete Yard Check

Primary action:

`Review & Complete`

Review screen groups:

- Matched;
- Missing;
- Unexpected;
- Location Mismatch.

Require confirmation:

`Complete Yard Check`

Result:

- unobserved expected Trailers become MISSING in this snapshot;
- Yard Check → COMPLETED;
- snapshot becomes immutable.

Completion does not silently apply all corrections.

---

# 14. Missing Trailer reconciliation

For a confirmed missing Trailer:

Show:

```text
Trailer T114 not found

Recorded location: Hood Yard
Last seen: ...
Last Driver: ...
```

Authorized action:

`Mark Location Unknown`

Result:

- Trailer location state → LOCATION_UNKNOWN;
- last known location preserved;
- last seen preserved;
- last Driver preserved;
- Administration alert created.

Administration owns investigation.

---

# 15. Unexpected / mismatch reconciliation

For unexpected Trailer:

Possible authorized action:

`Confirm Current Location`

For mismatch:

`Correct Location`

Both require explicit confirmation and audit.

Never rewrite silently just because QR was scanned.

---

# 16. Trailer search

Search/scan a Trailer to see:

- current recorded location;
- last known location;
- load state;
- serviceability;
- designated Customer;
- current Order/content summary;
- recent yard movements.

Keep history concise on mobile; full history remains better suited to web.

---

# 17. Post-Trip

At end of yard shift/equipment use:

Required:

- final odometer;
- damages;
- failures/problems;
- comments.

Primary CTA:

`Complete Post-Trip`

---

# 18. Off Duty

After required end-of-day workflow:

`Go Off Duty`

If a Yard Check is still IN_PROGRESS:

Warn/block:

```text
You have an unfinished Yard Check.
Complete or cancel it before going Off Duty.
```

If there are unsynced critical operations, do not lose them. Show sync status and preserve local queue.

---

# 19. Offline behavior

Critical operations should be queueable:

- On Duty;
- Pre-Trip structured data;
- Yard Move;
- location correction command where safe;
- Yard Check scan;
- Yard Check completion;
- OOS report;
- Post-Trip;
- Off Duty where safe.

Each uses a client operation ID.

## Yard Check offline rule

A Yard Check may continue offline if its expected snapshot is already stored locally.

If the snapshot has not been downloaded, do not start an offline Yard Check based on incomplete inventory.

## Sync conflicts

Example:

Trailer was moved by another user while Spotter was offline.

Do not silently accept stale correction.

Show:

```text
This Trailer changed while you were offline.
Review the latest recorded location before applying your correction.
```

---

# 20. Navigation

Recommended while On Duty:

```text
Yard Home
Move
Yard Check
Search
Profile
```

Exact tabs may change during visual design.

The two primary tasks—Move Trailer and Yard Check—must remain one-tap accessible.

---

# 21. Acceptance criteria for wireframing

Ready for wireframes when:

1. Spotter can go On Duty and complete Pre-Trip.
2. Spotter can select Site/Yard context.
3. Spotter can scan and move a Trailer Yard↔Dock / Dock↔Yard.
4. Location mismatches never auto-correct silently.
5. Spotter can start/scan/complete a Yard Check.
6. Yard Check expected inventory is snapshot-based.
7. Missing Trailer can transition to LOCATION_UNKNOWN with Admin alert.
8. OOS reporting is explicit and evidence-based.
9. Offline Yard Moves/Yard Check scans have deterministic sync behavior.
10. Post-Trip and Off Duty close the shift without erasing unsynced operations.
