# Issue #56 — Alerts, global devices and FCM

## Verified baseline / checkpoint A

Base: `ec7c73f4ebac0f9dd8be52e2d24d9a5f67ccc4ef` (PR #109 merged;
#24 and #55 closed; #56 open). Main CI #149 completed successfully. V25 is
the latest migration; V1–V25 must remain unchanged. Branch:
`issue-56-alerts-fcm-optimized-ci`.

Accepted contracts: ADR-010/011/013/022, schema hardening B/O/Q,
Alert state machine and alerts API. Existing V24 Missing Alert creation stays
synchronous and atomic with reconciliation. Existing V25 worker/publisher are
reused. Global User registrations must not acquire a Tenant column.

## Acceptance matrix (implementation evidence will replace pending entries)

| Boundary | Required evidence | State |
|---|---|---|
| Alerts lifecycle | Versioned acknowledge/resolve, preserved history, actual recovered Trailer | Pending |
| Alert visibility | Fresh membership/capability/resource/Customer checks, bounded inbox | Pending |
| V26 | Compatible V24/V25 upgrade, constraints, ownership/RLS, historical hashes | Pending |
| Global devices | Immutable User binding, safe rotation/transfer, no cross-User reads | Pending |
| Delivery | Registered V25 job, minimal references, fresh authorization, per-device receipts | Pending |
| Mobile | Explicit permission, registration/refresh/logout, fenced callbacks | Pending |
| Web | Inbox/detail/actions, capabilities, versions and Tenant fencing | Pending |
| CI | Draft FAST, final Ready FULL, fail-closed full gate and final integrable SHA | Pending |

OQ-059 remains open. No automatic recipients or notify-all rule is approved.
Provide an internal validated recipient boundary; keep current Alerts in the
inbox without implicitly subscribing any role. Write-capability role grants
require clarification; ALERT_VIEW alone does not authorize mutation.

## Repository protection audit

Branch-protection GET returned `403 Resource not accessible by integration`.
Rulesets GET returned `403 Upgrade to GitHub Pro or make this repository public`.
The integration cannot configure/verify required checks. Keep main's full push
validation; document exact owner configuration rather than claim protection.
No merge, protection bypass or unrelated #57 implementation is authorized.

## Checkpoint C evidence

V26 adds lifecycle constraints, recipients, global devices, per-destination
technical receipts and narrow controlled registry functions. It reuses V16's
nullable device_id, adding only its User-safe FK. No historical SQL changed.
Focused PostgreSQL 18.6: NotificationSchemaPostgresTests 10 and unchanged
YardCheckReconciliationPostgresTests 20 passed, zero failures/errors/skipped.
The first Draft run #150 (38658689617) executed CI Policy successfully, skipped
all four full suites and correctly left Full CI deferred/non-passing. No full
suite was run on that checkpoint. This is observed GitHub evidence, not simulation.

## Checkpoint D evidence

Inbox/list/detail use fresh ALERT_VIEW plus related Trailer scope and ACTIVE
Tenant, closed filters, bounded keyset pagination and minimized resource/context
projections. SQL lifecycle preserves original V24 facts and requires a recovered
AT_LOCATION projection before resolution; the Alert never repairs the Trailer.
ALERT_ACKNOWLEDGE / ALERT_RESOLVE are separate, currently unassigned capabilities
pending owner clarification, never inferred from ALERT_VIEW. Focused inbox/domain
tests 17 plus exhaustive role mapping 6 passed (zero failures/errors/skipped).

## Checkpoints E/F evidence

Global device HTTP tests: 11 passed; no Tenant requirement, stable proposed UUID,
token rotation/transfer, retained logout history, strict fields and foreign-owner
denial. API receipts exclude tokens and deviceName. Optional deviceId is identity
of this User registration, not authentication or a mobile idempotency key.

The registered ALERT_FCM v1 payload contains recipientId only; aggregate is the
Alert reference. No production recipient policy is registered. Test-only compiled
policy/provider exercise actual Yard reconciliation -> recipient/outbox -> existing
worker -> per-device FCM acceptance diagnostics. Delivery/schema tests 26 passed,
including two workers, rollback on real outbox INSERT failure, permission/policy
revocation, mixed devices, malformed receipts, retry/DEAD and lost ACK fencing.
An FCM acceptance is never labeled delivered. No real Firebase/APNs credentials
or physical-device delivery was used in these tests.
