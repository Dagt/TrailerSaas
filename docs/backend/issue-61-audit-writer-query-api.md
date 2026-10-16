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
Disabled users and memberships remain historical references, not current grants.

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
| Tenant Settings PATCH | Required audit, threshold/timezone/version only | Pending implementation/tests |
| Customer create/PATCH | Required audit, name/status/version | Pending |
| Physical Site create/PATCH | Required audit, allowed business address/geofence/config | Pending |
| Customer/Site association/removal | Required audit of Customer aggregate relation, no fake Customer version | Pending |
| Operational Location create/PATCH | Required audit, name/type/code/Site/Customer/active/version | Pending |
| Driver create/PATCH/link/unlink | Required audit, minimal identity/state; raw license/medical values excluded, changes explicitly indicated | Pending |
| Tractor create/PATCH | Required audit, operational number/active/version | Pending |
| Trailer create/metadata PATCH | Required audit; free-form capability notes excluded, changes indicated | Pending |
| QR issue/revoke | Required audit of identity/state; public token and token hashes excluded | Pending |
| Invitation create/expire/revoke/accept | Required audit of status/expiry/approved roles, no email/token/hash/body | Pending |
| Membership/roles created by invitation acceptance | Required audit of actual resulting membership and role scopes; lost-ACK branch returns without writing again | Pending |
| Standalone membership disable/role-edit/User administration | No production mutation endpoint exists; do not invent it | Code audit of identity controllers |
| Trailer designation/correction/OOS/Hook/Drop/Yard moves | Existing immutable specialized movement history with actor/source/time/reason/before-after; no duplicate generic event | TrailerEventWriter/MovementRules |
| Orders revisions/completion/active commercial changes | Existing V28 immutable request/operational history; no duplicate ledger | order_request_revisions/fulfillment/Trip events |
| Trip planning/assignments/reroute/cancellation | Existing specialized immutable assignment/operational events | trips module |
| Shift/inspection/POD/Fuel/Yard Check/reconciliation | Existing physical evidence/events/reconciliation histories; no indiscriminate duplication | Existing modules |
| Alerts/Outbox/FCM/device token lifecycle | Existing occurrence/ack/resolution/delivery/queue metadata; credentials/devices not copied into generic snapshots | Existing modules; OQ059 unchanged |

## Acceptance matrix / validation

Persistence: V30, actual runtime RLS/grants, immutable rows, actor/membership FKs,
historical upgrade/checksums and useful query indexes — pending.
Writer: public typed catalog, explicit whitelist, provenance, same transaction,
no-op/replay/concurrency/rollback tests — pending.
Reads: two GET routes, fresh AUDIT_VIEW/ACTIVE Tenant, strict bounded filters,
context-bound immutable-anchor keysets, safe errors and cross-Tenant denials — pending.
Security: no Customer/Driver/Yard general Audit access; no raw credentials,
invitation/FCM/public QR tokens, signed URLs, document contents or JPA dumps — pending.
Full regression and final published-SHA local evidence — pending final candidate.
Hosted Actions Billing remains owner-deferred: NO VERIFICADO if blocked. Draft
skips are never passes. Keep one Draft PR, clean published branch and NO MERGE.
Admin Audit UI62 and production/retention63–65 are explicitly out of scope.
