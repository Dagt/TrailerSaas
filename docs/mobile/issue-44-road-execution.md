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
from service start. DROP_HOOK requires a real Drop at that Stop during service and
a later different authorized replacement Hook. Both reuse #38 endpoints and ledger.

## Mandatory start integration remains blocked

BR-040 requires On Duty/Pre-Trip for normal Road Mode. #43 has not created those
authoritative records. `TripDeparturePrerequisites` is a narrow Trip-owned integration
boundary; its production implementation fails closed. No fake Shift, inspection,
configurable bypass or client statement satisfies it. Departure's accepted transition
is exercised with a test-only substituted prerequisite policy, not a production override.
The full live from-start sequence therefore awaits #43. This is an explicit integration
gap, not a claim that Shift/inspections or a usable production first departure exists.

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

QR scanning uses mobile_scanner 7.4.2 with camera-only permission. No manual UUID/number
bypass, gallery/media workflow or continuous location tracking. Only opaque token is
extracted from a raw QR or HTTPS /t/token label and sent to configured backend; scanned
URLs are never fetched. QR scan gains an additive version field needed by the existing
strict Hook contract. QR identity still grants no authority. Wrong expected Trailer is
blocked pending dispatch substitution; Drop must identify the real active coupling.

## Deferred

#43 real duty/inspection prerequisites; #45 POD/Fuel/exceptions and fulfillment; #46
SQLite command queue, dependency/replay worker; #47 geofence assistance/Driver update
UX; #52/#53 finalized documents/evidence; #54 media queue. Native Android/iOS permission,
camera lifecycle and physical end-to-end validation on devices remain deployment gates.
No production demo states, forced transitions, fake evidence, Shift auto-completion,
Yard workflow or authorization bypass are present.
