# Issue #47 — Geofence-assisted arrival

Base verified before editing: `df5be704594f4ef2810b7c3e5f3dcdb7ad528e07`,
merged PR #103; #45 closed. Working tree clean, no stashes, prior branches retained.
Branch: `issue-47-geofence-assisted-arrival`. No merge; no #48 work.

## Acceptance matrix (implementation plan)

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
