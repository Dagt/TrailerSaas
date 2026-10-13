# Issue #47 — Geofence-assisted arrival

Base verified before editing: `df5be704594f4ef2810b7c3e5f3dcdb7ad528e07`,
merged PR #103; #45 closed. Working tree clean, no stashes, prior branches retained.
Branch: `issue-47-geofence-assisted-arrival`. No merge; no #48 work.

## Acceptance matrix

| Gate | Implementation / evidence required |
| --- | --- |
| A: authorized destination | Own authenticated Trip detail; one next EN_ROUTE Stop, live active Site; no coordinate directory or snapshot-derived target; Tenant/Driver tests |
| B: confirmed command | Strict MANUAL/GEOFENCE_CONFIRMED; contextual radius/accuracy checks inside existing locked ledger winner; all existing authorization/state/chronology/physical rules |
| B: provenance | HUMAN_USER with authenticated actor; USER_ACTION manual, MOBILE_AUTOMATION geofence; Stop and Trailer events; no change to Hook/Drop/Service/Depart |
| C: native adapter | Injected location port, support/services/permission/reduced accuracy/error handling; cancellable one-shot observation, no tracking |
| C: deterministic evaluation | Haversine metres; finite valid coordinates; fresh precise fix; uncertainty circle contained within configured radius; deterministic clock/tests |
| D: human UX | Probable-arrival prompt, Confirm Arrival, Not Yet; no POST until confirmation; stale suggestion rejected; manual control independent |
| E: offline/recovery | Existing RoadCommandCapture/CommandStore/ReplayWorker; durable frozen body/UUID; SQLite reopen, lost ACK, retry/conflict tests; no local state transition |
| F: Driver Updates | Existing Store/Panel/pagination; downloaded updates invalidate before refresh; reroute/cancel/reassign/equipment/instructions/work removal; late callback fencing |
| Privacy/lifecycle | No GPS rows/history; only expressly confirmed Arrival evidence durable; background/Road exit/session/permission/destination invalidation |
| Security review | Twenty requested questions answered with code/test evidence before PR readiness; existing RLS/Flyway untouched |
| G: contracts/docs | OpenAPI matches implemented endpoint/projection; mobile/backend/UX/sync/backlog documentation |
| G: validation | Java25/PostgreSQL18 clean verify; locked Flutter generation/format/analyze/full tests; full Web/OpenAPI; four jobs on exact final head |
| Release #65 | Android/iOS hardware permissions, reduced accuracy, revocation, lifecycle/process restart pending unless explicitly run; production consent/privacy policy still requires review |

## Policy chosen under ADR-014

Assistance is an explicit foreground **Check arrival location** action, at most
one bounded reading at a time, never a timer or route stream. Coordinates come
from live normalized Site data for the next operational Stop, not address text
or historical snapshots. Missing configuration disables assistance only.

A precise reading must be at most 30 seconds old, not future-dated, with positive
finite accuracy at most half the configured radius. Distance plus accuracy must
fit inside that radius. The half-radius budget leaves at least half the region
for spatial separation instead of treating a coarse fix as presence. The short
30-second confirmation window limits movement between a deliberate reading and
confirmation; this is a conservative assistance UX policy, not proof of physical
presence, an offline replay expiry, or a legal assurance. Acquisition times out
after 10 seconds to bound battery use and avoid blocking Road Mode. Manual
arrival is always available. No default Site radius is introduced.

Backend evaluates the same spatial envelope against the locked current Site;
it does not infer permission, presence or device precision from client evidence.
An offline confirmed operation keeps its original occurrence/evidence and is
re-authorized by existing server state rules on replay. Site changes may cause a
safe conflict. Already committed requests retain the existing ledger replay
semantics after fresh current authorization.

## Architecture and authorized data

`GET /api/v1/tenants/{tenantId}/driver/me/trips/{tripId}` adds nullable
`arrivalAssistance`. Fresh TRIP_VIEW_ASSIGNED, active membership, active linked
Driver and current assignment precede the projection. Only the first nonterminal
Stop on an IN_TRANSIT Trip, with status EN_ROUTE, can supply its active normalized
Physical Site. A supplied Operational Location must belong to that Site/Tenant
and be active. The projection includes Trip/Stop/Site versions, Driver/Tractor/
expected Trailer, destination label, Site coordinates and configured radius.
Null disables geography only. Driver Stop snapshots omit geographic keys;
neither historical instructions nor address text create an authorized region.
There is no new directory, capability, endpoint, SQL table or migration.

`ArrivalTarget` checks this identity against current own work. Its stable identity
includes versions and equipment; session ownership is separately fenced by
Firebase User, internal User, Tenant and Driver. The evaluator is pure and uses
the mean Earth radius 6,371,008.8 m, degrees converted to radians, clamped
Haversine input and uncertainty containment `distance + accuracy <= radius`.
Dateline, poles, antipodes, age and accuracy boundaries are reproducible tests.

## Permissions and human UX

The selected MIT Geolocator package and compatibility tradeoff are recorded in
[ADR-024](../decisions/ADR-024-foreground-arrival-location-adapter.md). Android
declares COARSE/FINE only; no background or foreground-service permission.
iOS declares NSLocationWhenInUseUsageDescription only; no Always purpose key or
background mode. The upstream Android plugin includes a dormant service class;
our adapter never supplies foregroundNotificationConfig or enables background
mode. Native platform source review is evidence, not a physical-device test.

The Driver taps **Check arrival location** after the UI explains the purpose:
suggest an arrival while Road Mode is open, without recording a route. The port
checks support, services, foreground permission and OS precision before reading,
then rechecks before showing or confirming a suggestion. Approximate/reduced
precision never produces a suggestion. Errors, disabled GPS, denied/revoked
permission, timeout or missing config leave the existing manual arrival control.
An OS permission dialog that suspends the app cancels that check; a fresh
foreground check is intentional. It never silently starts GPS on reopening.

The prompt reads **It looks like you arrived at [destination].** Its actions are
**Confirm Arrival** and **Not Yet**. Not Yet suppresses that exact context until
it changes. No dialog suggestion mutates Trip/Stop or invokes the Backend.
Confirmation rechecks permission, freshness and the exact context, consumes the
suggestion once, and captures occurredAt at that operation. If it expires, only
manual arrival/new explicit check remains. Pending queue/uncertain commands
disable another arrival without fabricating a local Stop transition.

## Backend and provenance

TripExecutionRequests accepts exactly MANUAL and GEOFENCE_CONFIRMED. Unknown
body/query fields and nonfinite/out-of-range evidence fail strict validation.
MANUAL can omit coordinates and is independent of geographic configuration.
GEOFENCE_CONFIRMED requires positive accurate coordinates, next active EN_ROUTE
Stop, IN_TRANSIT Trip and a live active Site with coordinates/radius. The ledger
winner checks the spatial envelope against that Site under shared lock and
validates destination compatibility. Missing/stale geographic context is a safe
409 RESOURCE_CONFLICT; invalid state remains INVALID_STATE_TRANSITION.

Existing authorization, TRIP_EXECUTE_ASSIGNED + ARRIVAL_CONFIRM, Driver/Trip/
Stop locks, chronological checks and physical Trailer policy remain mandatory.
Fresh authority still precedes ledger replay. Coordinates/source cannot grant
access or prove presence. No strict global Trip-version check is added to the
offline command. A normalized Site without Operational Location can record Stop
arrival but cannot invent an AT_LOCATION Trailer projection.

Both events derive HUMAN_USER and actor_user_id from the authenticated principal.
MANUAL derives USER_ACTION; GEOFENCE_CONFIRMED derives MOBILE_AUTOMATION and
arrivalSource=GEOFENCE_CONFIRMED. Applicable Trailer movement receives the same
provenance/source. Hook, Drop, departure and service remain USER_ACTION. A
suggestion creates no Stop/Trailer/Order/Shift/audit fact.

## Offline and Driver Updates

RoadTripController calls the existing RoadCommandCapture, CommandStore and
ReplayWorker. One clientOperationId/path/body is materialized durably before
HTTP. Confirmed coordinates never change on retry, lost ACK or SQLite reopen.
Only expressly confirmed Arrival evidence is permitted in that payload. There
is no raw-fix table/history, new queue, Drift version or sync endpoint. The server
ACK triggers authoritative refresh, not a local status edit or reuse of an old
ledger response as current work. Existing 408/429/5xx/backoff, 401 recovery,
403/404/409 reconciliation and immutable UUID semantics remain in one lane.

The same DriverUpdatesStore/Panel used by #45 is passed into RoadTripScreen.
New validated instructions emit an in-memory invalidation before persistence,
then refresh own Trip detail after that download commits. Deduplicated historical
tail rows do not emit repeated resets. The existing cursor, pagination and
User/Tenant/Driver namespaces are unchanged. Instructions appear inside Road
Mode as well as home, including without push. Manual refresh, initial load,
foreground and reconnect continue to use the existing API.

Reroute, instructions, reassignment, Trailer expectation, cancellation and work
removal cancel the previous target/read and fence late callbacks. Fresh detail
recomputes the next Stop and live Site; failed reads leave assistance disabled.
Historical instructions confer no execution rights; planned substitution remains
distinct from the physical Hook. Logout/Tenant/Driver/permission change, app
background, Road disposal, terminal Trip or removal of enabled work discard
ephemeral evidence. No Off Duty sampling, GPS timer, breadcrumbs, tracking map,
periodic coordinate upload, external geolocation service or FCM is introduced.

## Security review evidence

This is implementation self-review; the open PR is the artifact for independent
external technical review. Test execution results below must distinguish local
and CI evidence.

| # | Question / answer | Evidence |
| --- | --- | --- |
| 1 | Suggestion cannot mutate Stop | ArrivalAssistanceController has no API/DB; widget checks zero POST; own detail no-mutating SQL test |
| 2 | Forged source cannot bypass permissions | Authorization precedes ledger; geofence current-authority matrix and historical auth suites |
| 3 | Foreign Sites are not exposed | Own Trip/Driver authorization, Tenant predicates/FKs/RLS; foreign Tenant/Stop tests |
| 4 | Only next operational geography is returned | arrivalContext query, withoutGeography projection; live-next-Site test |
| 5 | No position history | No schema change; only confirmed CommandIntent/evidence metadata; privacy contract test |
| 6 | No unauthorized background location | Lifecycle cancels native subscription; foreground-only settings; native adapter/manifest tests |
| 7 | Works without GPS | Manual widget and real offline queue regression |
| 8 | Old suggestion cannot retarget another Stop | Immutable target identity, generation/owner/age checks, confirmedEvidence; next-Stop backend tests |
| 9 | Reroute cancels old context | Store pre-download invalidation, controller generation, late-reading update tests |
| 10 | Human actor retained | Authenticated principal; Stop/Trailer provenance SQL assertions |
| 11 | MOBILE_AUTOMATION only for confirmed geofence | Closed source DTO; all other commands default USER_ACTION; physical Hook/Depart SQL regression |
| 12 | Replay retains body/UUID | Existing immutable store; real SQLite lost-ACK/reopen and retry tests |
| 13 | Double Arrival avoided | Same/different-key PostgreSQL concurrency and unique ledger; consume-once UI |
| 14 | Account change invalidates callbacks | Session generation and four ownership fields; account/widget/update tests |
| 15 | Distance is reproducible | Pure shared-policy Haversine calculations; poles/dateline/antipodes and boundary tests |
| 16 | Approximate location avoids false positives | OS reduced-accuracy gate plus accuracy envelope; adapter/evaluator tests |
| 17 | All RLS restrictions retained | V1–V22 byte-identical, no SQL/schema/grant changes; complete Backend Flyway/RLS suite |
| 18 | #45 guarantees retained | Same Store/Panel/cursor tables; deduplicated-tail regression plus full POD/Fuel/exception/media suites |
| 19 | Manual offline preserved | Existing capture/replay API, no GPS dependency; manual real SQLite test and historical suites |
| 20 | OpenAPI matches code | Implemented #47 source/evidence/own-context schemas; historical 14 plus four new contract tests |

## Publication and external review

The real publication artifact is [PR #104](https://github.com/Dagt/TrailerSaas/pull/104),
opened as Draft on 2026-10-13 against `main`. All ten checkpoints A–G4 are
preserved on `issue-47-geofence-assisted-arrival`.

Implementation HEAD: `07c2fcd19fd964107817462864d816037e184bce`.
Base: `df5be704594f4ef2810b7c3e5f3dcdb7ad528e07` (merged PR #103).
Completed implementation CI: [run 38394857918](https://github.com/Dagt/TrailerSaas/actions/runs/38394857918),
with all four jobs successful on that same head. Actions checked the PR merge
ref containing that head and the verified base; no repository merge was performed.

| Job | Actual CI tests | Result |
| --- | ---: | --- |
| Backend | 2,019 | PASS; 0 failures, errors or skipped; Java 25, PostgreSQL 18.6 Testcontainers, clean verify |
| Mobile | 389 | PASS; no skipped tests; locked dependencies, reproducible Drift v6, format and fatal-info analysis; session regressions also pass individually |
| Web | 244 | PASS; no skipped tests; npm ci, typecheck, lint, tests and build |
| OpenAPI | 18 | PASS; 0 failures or skipped; accepted contract validation also passes |

After G4, only this publication record and the backlog status were updated; no
additional implementation correction was needed. The final documentation commit
must pass the same four jobs before the **Ready for Review** handoff. The exact
final publication SHA, final CI run ID/counts and current review state are kept
in the [PR description](https://github.com/Dagt/TrailerSaas/pull/104) and
[its checks](https://github.com/Dagt/TrailerSaas/pull/104/checks), so this record
does not pretend to contain its own Git commit SHA or an uncompleted CI result.
Readiness also requires no conflicts, rejected reviews or unresolved threads.
The immutable implementation validation above remains independently inspectable.
The PR stays open for external technical review. **NO MERGE; no #48 work.**

## QA and release gate #65

Local Java25/PostgreSQL18.6 Testcontainers, full Web/OpenAPI and Flutter
format/analyze/generation run without changing CI. Windows policy blocks native
SQLite DLL loading with error 4551. The same Flutter 3.47.6 / Dart 3.13.5 source
and official engine were therefore provisioned in an isolated local Ubuntu Docker
checkout: all 389 tests pass, including 70 new geofence tests with real SQLite,
lost ACK/reopen, Driver rebound and targeted instruction persistence. Host source
was mounted read-only; no security policy or test gate was bypassed. This remains
automated local Linux validation, not Android/iOS hardware QA. Completed CI
evidence and the final publication check links are recorded above and in the PR.

The unmodified Web command also passes all 244 tests, typecheck/lint/build in a
local Linux checkout. Parallel Windows runs under load exposed existing timing
failures; both affected files and the complete suite pass serially, and the
original command passes in Linux. No Web source/tests or CI configuration changed.

G2–G4 corrected late confirmation callbacks affecting newer suggestions,
failed reload restoring old context, Driver fencing during HTTP/replay, and Not
Yet suppression across reload gaps. Commit-success instruction signals are
tested after the SQLite transaction commits; malformed pages never signal success.
Same-owner authoritative work removal also clears detail and assistance even when
the session snapshot object is retained. The Site-update locking regression
proves that an Arrival waiting on a concurrent Site writer evaluates its committed
new coordinates and rejects stale evidence with RESOURCE_CONFLICT.
Fresh own-detail validation rejects malformed/wrong-Trip responses. Automatic
reloads stop when work is withdrawn; the 404 regression uses the real queue and
SQLite rather than allowing an old instruction to restore authority. Reroute
coverage also proves the new Site is reloaded and can produce a new suggestion.

Final local validation: Backend `clean verify` **2,019** (0 failures/errors/skipped)
on Java25/PostgreSQL18.6; Mobile **389** (all historical 319 retained), plus locked
dependencies, reproducible Drift, unchanged format and fatal-info analysis;
Web **244** with the original command plus typecheck/lint/build in Linux;
OpenAPI **18** plus contract validation. An initial Linux Backend experiment was
rejected by the existing loopback guard/Docker Desktop networking; that guard was
preserved and the complete final verification ran in the working Windows setup.
No CI, historical tests, migrations V1–V22, RLS policies or capabilities were altered.
Readiness follows the publication gates above and the actual state of PR #104.

No physical Android/iOS tests have been performed in this task. Release #65 must
test the project minimum Android version and supported current Android/iOS:
precise/approximate and reduced accuracy, grant/denial/revocation, disabled GPS,
foreground/background during acquisition/permission/confirmation, Road exit,
reroute/cancel while reading, process close/reopen with pending confirmed command,
and absence of background location/notification/service activity. Native build,
signing and SPM privacy-manifest verification remain device/release work.

Before production, review purpose text, privacy notice, personal-device consent,
company policy and retention under NFR-050/051 and OQ-057. No legal conclusion
or consent entitlement is implied by OS permission. Continuous tracking, push,
Maps, stronger location grants, route history and #48 are outside this issue.
