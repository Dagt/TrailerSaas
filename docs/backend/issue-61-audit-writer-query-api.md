# #61 — Administrative Audit Writer and Query API

## A — Verified baseline and bounded architecture

Base/main: `a7598b3dd7e1bf10376d53c1a845ef4d24aefc83`, merge PR114.
Dependencies24/26/60 closed; issue61 open. No existing PR/branch61 found.
Original checkout has legitimate untracked `demo-assets/`, preserved unchanged.
Implementation uses a clean separate Git worktree and branch
`issue-61-audit-event-writer-query-api`; no reset, deletion or historical edits.
Baseline local evidence:2510 Backend/581 Mobile/404 Web/65 OpenAPI/12 CI Policy.
These previous-SHA counts are reference only, never final validation evidence.

NFR040–042, FR170–174, ADR011/013/015/017 and schema amendments E/M apply.
V29 is the latest migration. V30 must retain global actor references and the
acting membership through the accepted Tenant/membership/User composite FK.
Global User references and disabled memberships remain historical identities, not current grants. The current global users table has no disable flag or administrative disable command; Firebase revocation and membership status remain the real access controls.

The reserved `audit` module becomes a leaf infrastructure/business-record module:
public typed writer/query ports; no imports of identity or foreign internals.
The identity HTTP adapter applies Firebase, current Tenant ACTIVE, active
membership and existing AUDIT_VIEW, then invokes the public audit reader. This
avoids identity→audit→identity cycles when identity settings/onboarding produce
events. No browser Audit writes, new role grants or Customer subset API.

Owner services authorize and validate, capture an explicit minimized before map,
perform/flush the mutation and record its resulting allowed state in the same
transaction. Writer requires an existing writable transaction and matching pinned
TenantContext; no async/REQUIRES_NEW/eventual audit. No-op/version-conflict/rollback
does not leave a success record. Generic read/technical logs are not Audit.

## Administrative family matrix

| Family / real command | #61 decision | Evidence/status |
|---|---|---|
| Tenant Settings PATCH | Required audit, threshold/timezone/version only | Implemented; AuditPostgresTests |
| Customer create/PATCH | Required audit, name/status/version | Implemented; AuditPostgresTests |
| Physical Site create/PATCH | Required audit, allowed business address/geofence/config | Implemented; AuditPostgresTests |
| Customer/Site association/removal | Required audit of Customer aggregate relation, no fake Customer version | Implemented; AuditPostgresTests |
| Operational Location create/PATCH | Required audit, name/type/code/Site/Customer/active/version | Implemented; AuditPostgresTests |
| Driver create/PATCH/link/unlink | Required audit, minimal identity/state; raw license/medical values excluded, changes explicitly indicated | Implemented; AuditPostgresTests |
| Tractor create/PATCH | Required audit, operational number/active/version | Implemented; AuditPostgresTests |
| Trailer create/metadata PATCH | Required audit; free-form capability notes excluded, changes indicated | Implemented; AuditPostgresTests |
| QR issue/revoke | Required audit of identity/state; public token and token hashes excluded | Implemented; AuditPostgresTests |
| Invitation create/expire/revoke/accept | Required audit of status/expiry/approved roles, no email/token/hash/body | Implemented; AuditPostgresTests |
| Membership/roles created by invitation acceptance | Required audit of actual resulting membership and role scopes; lost-ACK branch returns without writing again | Implemented; AuditPostgresTests |
| Standalone membership disable/role-edit/User administration | No production mutation endpoint exists; do not invent it | Code audit of identity controllers |
| Trailer designation/correction/OOS/Hook/Drop/Yard moves | Existing immutable specialized movement history with actor/source/time/reason/before-after; no duplicate generic event | TrailerEventWriter/MovementRules |
| Orders revisions/completion/active commercial changes | Existing V28 immutable request/operational history; no duplicate ledger | order_request_revisions/fulfillment/Trip events |
| Trip planning/assignments/reroute/cancellation | Existing specialized immutable assignment/operational events | trips module |
| Shift/inspection/POD/Fuel/Yard Check/reconciliation | Existing physical evidence/events/reconciliation histories; no indiscriminate duplication | Existing modules |
| Alerts/Outbox/FCM/device token lifecycle | Existing occurrence/ack/resolution/delivery/queue metadata; credentials/devices not copied into generic snapshots | Existing modules; OQ059 unchanged |

## V30 persistence and ownership

`audit_events` has UUID PK, Tenant, actor type/global User/acting membership,
source, action, entity type/UUID, nullable before/after maps, real optional reason
and database `statement_timestamp()` occurrence time. The writer creates UUIDs;
clients cannot set time, actor or source. New human actions require User and
membership; the composite Tenant/membership/User FK preserves attribution.
SYSTEM and INTEGRATION require null human references and their matching source.
No User delete cascade, fake mobile operation or second business ledger.

RLS is enabled and forced. The sole policy targets the non-superuser NOBYPASSRLS
runtime role, requires the pinned Tenant and excludes bootstrap User mode.
Runtime receives SELECT/INSERT only; UPDATE/DELETE/TRUNCATE are absent. An
immutable trigger also rejects row UPDATE/DELETE by the migration owner; owner
DDL is a trusted operational boundary, not a runtime privilege. No retention or
history deletion endpoint. V1–V29 are unchanged and normalized source hashes are
frozen in the new test fixture. Fresh V30 and real V29→V30 upgrade are tested.

Five read indexes support Tenant/time, entity, User, membership and action, all
ending `(occurred_at DESC,id DESC)`. Membership index follows amendment E and
supports FK/historical attribution diagnostics. Tests inspect real EXPLAIN plans
with sequential scans disabled to establish usable ordered index paths; this is
not a production-scale performance claim. The additional unique change identity
index supports actual retry/concurrency protection.

## Writer and transaction boundary

Public `AuditWriter` is consumed only through the module API. Owner services
apply existing authorization/validation and actual optimistic or row locking,
capture before, mutate/flush and record after. The MANDATORY writer validates
matching transaction-local Tenant, entity ownership and actual human membership.
JDBC joins the same Spring/JPA transaction. Required insert failure rolls back
the domain change; later domain failure rolls back Audit. No independent commit,
async listener, outbox or queue. The public port is not an authorization API.

`Action` fixes entity type and snapshot whitelist. Java rejects arbitrary beans,
unknown nested keys and nonfinite numbers; SQL independently checks the closed
catalog, bounded JSON object shape, actor/source, aggregate change identity and
no-op rules. Each snapshot is bounded to 64 KiB (both compact application encoding
and PostgreSQL JSONB text); larger business state fails the whole required
mutation instead of silently truncating evidence. This explicit operational
limit should be considered before importing exceptionally large legacy fields.

A generated `change_key` deduplicates the actual domain effect: resulting version
for versioned entities, real association UUID for association/unassociation,
once-per-action for monotonic QR/invitation/onboarding actions. Identical committed
retry does not append a second record; divergent evidence for the same identity
fails closed. No-op PATCH writes nothing; stale/concurrent loser writes nothing.
Creation APIs retain their existing contracts: two genuinely created entities
have separate histories; Audit does not add generic HTTP command idempotency.
Existing mobile ledger and specialized event replay are unchanged.

## Snapshot minimization and actor history

Explicit owner-specific helpers contain only approved business fields. Driver
license/medical values and free Trailer notes are replaced by presence flags;
actual edit field names are carried in `redactedChanges`. Invitation email/name,
acceptance token/hash and QR token are excluded. No Firebase/FCM credentials,
signed URL, full document, request body or JPA object is serialized. Site business
coordinates/configuration are existing authorized master data, not device GPS.

The actor response exposes stable historical User/membership IDs rather than
joining mutable current profile details. Disabled membership references remain
readable to another currently authorized Admin. Global users currently have no
administrative disable field/command; this issue adds neither. FK retention
keeps User identity available when Firebase access is later disabled externally.
The expiry transition is SYSTEM, creation/revocation/acceptance are the actual
human principal. Acceptance creates actual membership/roles and two distinct
records atomically; its existing accepted/lost-ACK branch emits no new record.

## Query API and access

GET `/api/v1/tenants/{tenantId}/audit-events` and `/{auditEventId}` reuse Firebase,
TenantAuthorization and runtime RLS. Reads additionally require ACTIVE Tenant.
Existing CANDIDATE Tenant bootstrap administration remains compatible; the writer
does not alter the owning service's accepted authorization behavior. No grants
change. Customer Dispatcher/Driver/Yard/Operations Audit reads remain denied.
There is no Customer Audit capability or improvised Customer subset.

Filters: canonical actorUserId/entityId, closed actionCode/entityType, inclusive
RFC3339 four-digit-year from/to, limit default50/max100 and cursor. Unknown/duplicate fields reject.
The existing ReadCursor binds User/Tenant/filters and immutable anchor UUID; the
server fetches its authorized timestamp and seeks `(occurred_at,id)<(anchor,id)`.
No timestamp trust or in-memory pagination. Changing page limit is allowed;
changing context rejects. Current access is checked even for a previously valid
cursor. Foreign detail and unavailable detail return identical generic404 code.
No HTTP Audit mutations. Response DTOs/minimized snapshot shapes are in OpenAPI.

## Acceptance and security matrix

| Requirement | Implementation / executable evidence |
|---|---|
| V30, real historical upgrade/checksums | AuditUpgradePostgresTests; existing latest-schema/table/RLS inventories extended, not relaxed |
| Required same-transaction Audit | Actual Customer/settings writes; induced PostgreSQL BEFORE INSERT failure; domain exception rollback |
| Actor/source and historical membership | HUMAN_USER/USER_ACTION; SYSTEM/SYSTEM_PROCESS; INTEGRATION/EXTERNAL_INTEGRATION; disabled member history via another Admin |
| No-op/invalid/denied/stale/retry/concurrency | Real HTTP, direct mandatory writer, two concurrent HTTP transactions; identical change dedupe/divergent retry rejection |
| Selected administrative family coverage | Matrix above; real HTTP production services; QR and invitation secrets excluded |
| No physical duplication | Actual location correction writes specialized event and zero generic Audit rows |
| Fresh authorization/tenant isolation | Customer/Driver/Yard/Operations denial; revoked roles/membership/Tenant; foreign UUID/cursor; separate runtime connections and pooled reset |
| SQL defense | FORCE RLS, NOBYPASSRLS, no UPDATE/DELETE/TRUNCATE; cross-Tenant membership FK; fabricated actor/action/payload constraints |
| Query API | All filters, malformed/changed-context cursor, multiple pages/timestamp ties, detail and bounded SQL/index paths |
| Modulith | Public leaf ports; explicit producer dependencies; existing module validation |
| OpenAPI | Existing full contracts plus audit.test.mjs closed routes/catalog/DTOs/snapshot minimization |
| Final regressions | PR115 records the exact published candidate, complete local logs/counts and SHA-256 manifest; previous-SHA results are not final evidence |

## Validation protocol and limitations

Focused implementation run `issue61-focus3.log`:184 tests, zero failures/errors/
skipped with Java25/PostgreSQL18.6; this is development evidence, not final-SHA
full validation. Additional focused tests cover physical nonduplication and ties. A failing PostgreSQL regression reproduced an extended-year filter returning500; the reader now rejects unsupported extended/negative years as400 before querying. The superseded candidate run is not final evidence. Full regression also located two historical schema assertions requiring the exact V30 inventory:54 tables and Audit presence (with zero duplicate Trip Audit rows), replacing the old reserved-table absence. Existing column/grant/RLS assertions stay intact.
Final candidate must run complete Backend clean verify, Web locked install/type/
lint/tests/build, Mobile locked install/Drift9 reproducibility/format/fatal-info
analysis/tests, OpenAPI and local CI Policy. Evidence is recorded externally with
source archive identity, exact HEAD, environment, full logs and SHA-256 manifest.
The PR will link final results without a self-referential source-SHA commit.

Hosted Actions Billing is owner-deferred. A Draft-policy skip is not success;
blocked remote jobs are NO VERIFICADO, regardless of local green. PR115 stays
Draft/open/unmerged. No workflow or branch-protection change. No physical-device
QA is claimed. No Admin Audit UI62, privacy/legal conclusion, retention63–65,
new grants, recipient policy or OQ059 changes. No historical backfill or universal
coverage claim: absent standalone membership/role/User admin commands are not
invented, and sufficient specialized histories remain their authoritative source.
