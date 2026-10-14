# Yard / Spotter Mobile — UX Flow v0.1

**Status:** Accepted v0.1  
**Primary actor:** YARD_SPOTTER  
**Related requirements:** FR-050–055, FR-090–103, FR-120–136, FR-170–174  
**Related state machines:** Shift, Trailer, Yard Check, Alert

## Issue #49 implemented corrective and damage boundary

Yard Home now supports explicit Location Correction, Load State Correction,
Discrepancy Report and Report Damage / Mark OOS. The original Yard Move remains
unchanged. #50 implements the Yard Check Backend; the Check mobile UI/offline
flows below remain #51. Notification delivery remains #55/#56.
No capability or role grant changes: YARD_SPOTTER is Tenant-scoped, has the two
correction capabilities, and has no OOS grant; a currently authorized multi-role
user may use OOS. Selecting Site, QR scan, a local draft or an upload grants no
mutation authority. Return to Service and unresolved OQ-054 remain separate.

See [#49 contracts, acceptance, tests and release limitations](../mobile/issue-49-yard-discrepancy-oos.md).

## Implemented slice — issue #48

The current application provides Yard Home, authorized Site selection, existing
native QR scan, recorded Trailer review, constrained destination selection,
explicit Review/Confirm Move and Not Yet, and durable pending/conflict/server-ACK
states. Yard-only users enter this real context; users with Road and Yard access
retain the mode switch and existing Road controls. Current Yard capabilities are
Tenant-wide and do not require or invent a linked Driver/Tractor/Shift. Existing
Driver Shift/inspection functionality remains available in Road for dual-role
users; no new Yard-specific Shift grant or equipment policy is inferred here.

Only configured active normalized Locations at the selected active Site are
destinations. The UI shows recorded origin, planned origin after an earlier
pending move, Customer designation, load/serviceability/coupling/version and
observed time. Confirmation attests the reviewed physical origin; a discrepancy
must be reviewed, not corrected by this flow. A local save is explicitly waiting
to sync, never presented as a server location change. Scan and Not Yet create
no physical command/event. Review dialogs and scanner callbacks are fenced and
closed on account/Tenant/Driver/mode change.

LOCATION_MISMATCH and STALE_VERSION retain the original move and show the latest
authorized downloaded recorded location/version with refresh time. Failed or
partial downloads do not authorize an old context. Offline use requires an
established session and downloaded context/scanned Trailer; replay re-authorizes.
Consecutive moves depend on the previous same-Trailer ACK, never a guessed version;
a parent conflict blocks descendants while other assets may sync. Correction and OOS reporting are implemented in #49;
Yard Check/reconciliation Backend is implemented by #50; its mobile UI remains #51.
Physical camera/permission/lifecycle QA remains #65.

The following sections describe the accepted broader Yard UX, beyond this slice.

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

### #49 implemented review and damage UX

Scan → recorded Trailer → explicit action. Location uses current recorded value,
authorized destination, reason and Confirm Correction. Load State shows current
and new value/reason and preserves other dimensions. Without correction grant,
Report Discrepancy is available and explicitly creates no state change.

Damage uses the existing injected NativeInspectionCamera/ImagePicker: real image
bytes are privately copied/hash-verified, comment and all photo IDs retained before
explicit Confirm Mark OOS. Not Yet preserves drafts/photos and sends no OOS.
Upload/finalization is independently visible and never serviceability success.
Saved local, waiting evidence, queued/sending, server ACK and conflicts are distinct.
There is no Return-to-Service shortcut or new capability grant. Forms close on
User/Tenant/Driver/Site/Trailer/version/mode changes; double confirmation is fenced.

Conflict handling retains original commands and descendants. A fresh authorized
read and explicit review may create a new action; failed reload or uncertain ACK
does not permit rebasing. Old damaged evidence can be explicitly retained in a new
reviewed draft without changing its file/hash/identity. Physical Android/iOS camera,
permission/reduced access/background/restart/lost-data QA remains #65; injected
camera/Linux widget tests are not physical-device QA.


### #51 implemented mobile inventory

Yard Home retains Move/Correction/OOS and adds Start, Resume, persistent progress,
large Scan Trailer, findings and explicit Review & Complete/Cancel. Select a real
authorized Site and optional Yard/Dock scope before online Start; only server ID
and a fully downloaded snapshot enable offline inventory. Scanner returns directly
to progress and the next scan. Offline unknown tokens display UNRESOLVED_QR and
pending synchronization, not a Trailer name or successful classification. Local
match/mismatch/unexpected labels remain LOCAL_PENDING until typed server ACK.
Expected/original location facts remain separate from live Trailer projection.

Not Yet sends no command. Completion requires a valid durable ACK for every
captured observation, including reviewed failed scans. Conflict review retains
history and never counts as synchronization. Cancellation separately permits
explicitly reviewed definitive failures, retains partial evidence and never makes
MISSING findings; uncertain work still blocks it. Both require online review;
only ACK changes displayed authoritative terminal/MISSING state.
MISSING/mismatch reconciliation is separately authorized, reasoned and confirmed,
with actual server event/alert history displayed after ACK. Check history is never
automatic physical correction. Late camera/review callbacks are fenced by owner,
Check, lifecycle and disposal. Reason controllers survive the full dialog exit
animation, and context invalidation removes the owned review route before a late
confirmation can execute. Shift Off Duty blocks known unfinished associated
Checks at capture/replay and warns about unknown/stale offline state; it retains
existing Driver/Post-Trip work requirements. Physical QA remains #65.
