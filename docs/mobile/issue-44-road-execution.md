# Issue #44 — Driver execution boundary

Backend remains the only operational authority. Road detail uses fresh own-Driver
`GET /driver/me/trips/{tripId}` because the administrative `GET /trips/{tripId}`
contains actor/assignment histories that must not be exposed to Driver callers.
The new projection reuses the fixed-batch assigned-work reader, adds the current
Tractor display number, real physical coupling, started/completed milestones and
the departure-prerequisite hint. A terminal result remains readable only while
currently assigned. No generic equipment directory or administrative history is added.

## Commands and state

All five strict commands require authenticated User, fresh ACTIVE membership,
own active linked Driver, current assignment and TRIP_EXECUTE_ASSIGNED. Departure
also requires DEPARTURE_CONFIRM; arrival requires ARRIVAL_CONFIRM. A required
canonical UUID Idempotency-Key identifies the immutable request.

| Endpoint | Transition |
|---|---|
| POST /trips/{tripId}/departures | ASSIGNED → IN_TRANSIT; first ordered PENDING Stop → EN_ROUTE |
| POST /stops/{stopId}/arrivals | Active EN_ROUTE → ARRIVED; Trip → AT_STOP |
| POST /stops/{stopId}/service-starts | Active ARRIVED → IN_SERVICE |
| POST /stops/{stopId}/completions | Active non-delivery IN_SERVICE → COMPLETED |
| POST /stops/{stopId}/departures | Completed visit departs; next ordered PENDING Stop → EN_ROUTE, Trip → IN_TRANSIT |

No generic state input, Stop skipping, implicit base return or Shift completion.
The final service completion completes Trip only if all planned Stops are terminal
and Trip is AT_STOP, never CANCELLING. Ordinary execution cannot resolve cancellation.
Stop events record previous/new Trip state, so final completion is a durable fact.
Orders and Manifests are not automatically completed or physically unloaded.
Delivery always rejects ordinary completion with EVIDENCE_REQUIRED; #45/#53 will
validate finalized POD. Live service requires the same coupling to remain present
from service start, with no intervening Drop/re-Hook even of the same Trailer.
DROP_HOOK requires a real Drop at that Stop during service and
a later different authorized replacement Hook. Both reuse #38 endpoints and ledger.

## Mandatory start integration implemented by #43

BR-040 requires real On Duty/Pre-Trip for normal Road Mode. #43 supplies the
Inspections-owned implementation of the Trip-owned TripDeparturePrerequisites port:
own open Shift and matching current Driver/Tractor/Shift Pre-Trip with four finalized
photos. An explicit inspection Trip must match. Occurrence is revalidated at Depart.
No fake state, configurable bypass or mobile statement satisfies it. Original #44
tests retain their confined prerequisite fixture; #43 also tests the complete real
from-start non-delivery sequence with PostgreSQL and actual finalized documents.

## Physical truth and locking

Current coupling comes only from trailer_couplings, never plannedInitialTrailerId.
Execution uses Trip → linked Driver → ordered Stops → Tractor → Trailer; the common
exclusive Trip parent serializes dispatch reassignment/substitution/reroute/cancellation
and Hook/Drop. There is no reverse Order lock or new Order lifecycle call. Equipment
and physical projection join the winner's transaction through Trailers' public API.
Departure validates active Tractor and active IN_SERVICE expected coupled Trailer when
planned. Arrival can still record a visit to an ad-hoc snapshot, but changes Trailer
location only with the Stop's real same-Tenant Customer-compatible Operational Location.
A snapshot never creates fake location. Unknown stationary Trailer location, or another
Trip's coupling on the Tractor, cannot silently become authorized travel.
If a visit has no Operational Location, Arrival does not manufacture a Trailer arrival.
A later departure remains blocked when the existing #39 projection cannot perform its
accepted AT_LOCATION-to-IN_TRANSIT transition. An authorized physical location correction
must resolve that gap; Road does not invent a Location or bypass the transition matrix.

V18 adds only Accepted trip_stop_events (including hardening M source_type),
Tenant-safe Trip/Stop FKs, provenance checks, RLS, SELECT/INSERT grants and history
index. V1–V17 are preserved. Runtime cannot update/delete facts. Actor is always
HUMAN_USER / USER_ACTION / authenticated User; occurrence is device operational time,
recording is server persistence time. Future/backwards operational time is rejected.

The V16 ledger implementation is extracted into Identity's public transaction-joining
MobileOperationLedger and reused by physical and Stop commands. Claims, domain changes,
facts and saved result commit atomically; failure rolls back PROCESSING too. Replay
rechecks current membership/Driver/assignment but returns the saved projection, not a
new mutation. Changed action/resource/payload/User conflicts. Infrastructure remains 5xx.

## Foreground mobile recovery, not #46 replay

Actions require a freshly loaded detail, connectivity and current capability hints;
server still re-authorizes. UI confirms physical action, displays sending and only
publishes a fresh own-Trip read after confirmation. A replay response may describe an earlier state and never re-enables actions or overwrites cache by itself. Confirmed terminal Trips leave the assigned-work cache transactionally. Stale/denied state requires authoritative reload.
Offline displays downloaded work with no execution controls and queues no command.

Before POST, a single unresolved foreground receipt is durably flushed to app-private
storage, namespaced by internal User/Tenant. It contains UUID, Trip/path and exact body,
never Firebase token/password/raw QR/media. After uncertain network/5xx outcome it
survives restart. The explicit Recover action reuses its immutable key/body; no automatic
retry, worker, backoff or command dependency queue is implemented. A second operation
cannot replace an unresolved receipt. Identity/Tenant changes hide old projections and
ignore late results. The original namespace's unresolved receipt remains isolated for
later authorized reconciliation rather than silently being replayed under another account.
An uncertain submission from another Trip can be explicitly recovered without navigating
to work that is no longer assigned. The original key/path/body still go through fresh
server authorization. Road has a dedicated recovery panel independent of the assigned
Trip list, including an empty list, and rechecks its namespace when resumed or returning
from Trip detail. Original work need not remain assigned. Recovery requires an online
server-validated context and never claims that a cached replay response is current.
A denied assignment refreshes work but cannot prove whether an earlier attempt committed:
that receipt is retained, with a clear unconfirmed-outcome message.
A successful recovery of another Trip refreshes the full work set and never substitutes
its saved response for the currently displayed Trip.

### Explicit response policy

| Result | Foreground receipt and recovery |
|---|---|
| Transport error, timeout, 408, 5xx | Retain exact UUID/path/body; explicit recovery only |
| 429 | Retain; honor Retry-After seconds or HTTP-date with an in-memory eligibility gate, shared across foreground controllers; no timer sends a POST and no delay is persisted |
| 401/403 | Retain because fresh authorization precedes replay lookup; invalidate operational snapshot and clear cached authorization/work before refreshing /me and assigned work; refresh outage cannot restore denied permissions |
| 404 RESOURCE_NOT_FOUND | Retain because lost assignment can conceal a prior committed result; refresh current assigned work, show denial without claiming the physical action failed |
| 400 VALIDATION_FAILED | Recognized definitive rejection; clear only the matching receipt and reconcile current work |
| 409 INVALID_STATE_TRANSITION / RESOURCE_CONFLICT / STALE_VERSION / EVIDENCE_REQUIRED | Recognized semantic rejection after authorized replay lookup; clear only the matching receipt and reconcile current work |
| Key-reuse conflict, unknown/malformed 4xx or response | Retain; cannot establish the original operation's outcome |
| Success | Clear only the acknowledged UUID, then re-read authoritative detail/work before enabling actions; never apply a saved POST projection as current state |

A receipt that remains inaccessible after assignment/permission revocation deliberately
blocks another foreground command for that User/Tenant. It does not manufacture a new key,
a confirmation or an abandonment/replay protocol. Later authorized reconciliation remains
possible; generalized operation resolution and offline replay belong to #46.
Strict stored endpoint checks prohibit other-Tenant or relative/traversal paths. Account/Tenant
switches ignore late responses; acknowledgement cannot erase a newer receipt. The journal
and camera are injectable widget boundaries; file and SQLite integration tests retain real
persistence coverage without filesystem I/O inside Flutter's fake widget clock.

Live Load/Unload completion is the explicit manual service completion for non-POD Stop types
under the current API. It requires an uninterrupted physical coupling and does not infer or
write load state or evidence. DELIVERY remains blocked by EVIDENCE_REQUIRED. Drop & Hook
requires immutable Drop and authorized replacement Hook facts linked to actual intervals;
Hook's #38 contract has no stopId, so it is correlated by Trip, service time and coupling
rather than inventing a request field. POD/document-specific completion remains #45/#53.

QR scanning uses mobile_scanner 7.4.2 with camera-only permission. No manual UUID/number
bypass, gallery/media workflow or continuous location tracking. Only opaque token is
extracted from a raw QR or HTTPS /t/token label and sent to configured backend; scanned
URLs are never fetched. QR scan gains an additive version field needed by the existing
strict Hook contract. QR identity still grants no authority. Wrong expected Trailer is
blocked pending dispatch substitution; Drop must identify the real active coupling.
Drop takes that identity from the fresh authenticated coupling projection and confirms it
explicitly, with no manually entered ID. It does not depend on public QR resolution:
closing an existing inactive/OOS coupling remains possible under #38's server rules.
The replacement Hook still requires camera QR identification and fresh server validation.

## Deferred

#43 real duty/inspection prerequisites; #45 POD/Fuel/exceptions and fulfillment; #46
SQLite command queue, dependency/replay worker; #47 geofence assistance/Driver update
UX; #52/#53 finalized documents/evidence; #54 media queue. Native Android/iOS permission,
camera lifecycle and physical end-to-end validation on devices remain deployment gates.
No production demo states, forced transitions, fake evidence, Shift auto-completion,
Yard workflow or authorization bypass are present.

## Issue #46 successor

The foreground-only recovery described above is the #44 baseline. Production Road
now transitions to the [persistent Drift command queue and foreground replay worker](issue-46-command-replay.md)
implemented by #46. Old file receipts migrate with the original identity; legacy
controllers remain compatibility/test boundaries. The new intent guide never mutates
cached Trip/Stop/Trailer state. #43 start prerequisites and #45/#53 Delivery evidence
blocks remain unchanged.

## Issue #43 real prerequisite integration

The previously blocked from-start integration is now implemented by Inspections:
real own open Shift plus validated same-Shift/current-Tractor Pre-Trip/photos.
Trips still consumes its own prerequisite port without depending on Inspections.
No configuration bypass exists. Driver assignment and all #44 physical rules still
apply; Delivery completion remains blocked until #45. See issue-43-shift-inspections.md.
