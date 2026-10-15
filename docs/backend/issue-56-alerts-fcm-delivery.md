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

## Acceptance matrix

| Boundary | Required evidence | State |
|---|---|---|
| Alerts lifecycle | AlertService, SQL lifecycle/terminal/physical guard; AlertInboxPostgresTests | Implemented; write-role grants unapproved/unassigned |
| Alert visibility | Fresh membership/capability/resource/Customer checks, bounded inbox | Implemented; unapproved roles denied |
| V26 | AlertUpgradePostgresTests, NotificationSchemaPostgresTests, historical hashes | Real V25 history upgraded without loss; V1–V25 unchanged |
| Global devices | DeviceApiPostgresTests; immutable owner, rotation/transfer/concurrency | Implemented; no Tenant column, owner-only registry |
| Delivery | AlertDeliveryPostgresTests, FirebaseFcmTransportTests; existing V25 worker | Implemented; no automatic recipient policy installed |
| Mobile | notifications_test.dart plus session regressions | Explicit permission, fencing, private ID, GET-only hints; Drift v9 unchanged |
| Web | Alerts.test.tsx plus Admin/Operations regressions | Versioned explicit actions, scoped query cache and disposed old dialogs |
| CI | CI policy tests; actual Draft #150; final Ready evidence in PR #110 | FAST observed; FULL only after finished published candidate |

OQ-059 remains open. No automatic recipients or notify-all rule is approved.
Provide an internal validated recipient boundary; keep current Alerts in the
inbox without implicitly subscribing any role. Write-capability role grants
remain unapproved/unassigned; ALERT_VIEW alone does not authorize mutation.
No response to the optional policy clarification has been treated as approval.
This is the explicit deny-by-default boundary requested by #56, not a claim that
production acknowledgement/resolution role grants or recipient policy are enabled.

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

## Checkpoint G evidence

Flutter adds an injectable Firebase Messaging/OS port, a private stable per-User
registration ID (no token persisted), explicit permission UI and authenticated
Alert-hint retrieval. Login, refresh, foreground, reconnect and logout reuse the
existing session/API lifecycle; no second command queue, worker or Drift change.
Drift remains v9. Account changes fence callbacks and never reuse a previous
User's registration. Native permission/APNs configuration is committed; physical
Android/iOS and real credentials remain release gate #65 work.

Focused Mobile notification/session tests: 60 passed; fatal-info analysis passed.
The widget regression uses a real asynchronous permission future outside the
Flutter fake-clock zone, including unmount before permission completion.
Web Alerts/Admin/Operations regressions: 73 passed; typecheck and lint passed.
Web actions require downloaded separate capabilities and explicit versioned
confirmation. Production write grants remain deny-by-default pending clarification.

## Runtime architecture and configuration

V26 is additive to the existing Alerts occurrence/history. `alert_recipients`
binds Alert/Tenant/User plus compiled policy code, with composite membership and
Alert FKs and immutable association facts. `alert_deliveries` records only technical
per-device attempts/results; it is not another business ledger, queue or worker.
`user_devices` is global, with immutable User/platform/id, monotonic token revision,
partial unique non-null token and retained inactive rows. A narrow controlled
function derives the owner from the authenticated bootstrap context and serializes
short registry writes. Ordinary runtime SQL cannot insert/update the registry.
No token appears in REST responses, outbox payloads, logs or metrics.

The only cross-User device lookup requires an existing current-Tenant, live leased
ALERT_FCM job matching the recipient/Alert and schema version, plus fresh active
membership and the conservative approved read-role boundary. Ordinary Tenant/no
context queries cannot read the global device registry. The existing worker's
claim-token-fenced transaction wraps every delivery DB phase; network IO occurs
outside that transaction. Policy, eligibility and device revision are revalidated
immediately before sending. A revoked/closed Alert suppresses delivery, not history.
The SQL controlled path intentionally does not expand to Customer/Driver/Yard
read grants; any future approved expansion requires explicit security review.

`AlertNotifications.requestDelivery` is an internal MANDATORY transaction port.
The caller selects a registered, compiled, approved `AlertRecipientPolicy`; candidate
IDs are checked against fresh Tenant/membership/resource scope. The unique recipient
association and its minimal versioned `ALERT_FCM` job commit or roll back together.
Duplicate requests do not enqueue another delivery. No producer/recipient API,
notify-all rule, subscription table or escalation policy is exposed to clients.
Production registers no policy until OQ-059 approval. V24 reconciliations continue
creating the real WARNING/OPEN Alert synchronously, with zero implicit recipients.

Configure existing ADC (`GOOGLE_APPLICATION_CREDENTIALS` or workload identity) and
`FIREBASE_PROJECT_ID`, matching the client environments. The existing FirebaseApp
is reused by Auth and FCM; no second authentication system. Firebase connection/read
timeouts are 5/8 seconds. `FCM_ENABLED` defaults false; enabling it alone does not
create recipients or enable the separately configured #55 worker/Tenant list.
The Firebase transport waits at most 8 seconds, cancels its future on timeout and
sends at most 32 tokens with generic text, 5-minute Android TTL/collapse and APNs
collapse. Data whitelist is exactly kind/tenantId/alertId. No sensitive message body.
ADC, enabled FCM API and matching Firebase/APNs configuration are deployment tasks,
never credentials to commit. Native Firebase/APNs failures are not simulated success.

## Retry, crash and multi-device diagnostics

The existing #55 defaults apply: 5 attempts, exponential 30s initial backoff capped
30m with jitter, 30s handler bound, 2m fenced lease. Unknown/invalid job payloads fail
closed before handler invocation. UNREGISTERED conditionally invalidates the exact
device token revision; a late old invalid-token result cannot invalidate or strand
a rotated registration. Invalid argument/project/APNs auth failures are permanent;
temporary/unavailable/quota/timeout failures use the existing durable retry policy.
Safe codes only are persisted. No SDK exception text, tokens or signed URLs are stored.

Technical statuses distinguish NO_ELIGIBLE_DEVICE, ATTEMPTED, ACCEPTED_BY_FCM,
RETRYABLE_FAILURE, PERMANENT_FAILURE, INVALIDATED_TOKEN, PARTIAL_FAILURE,
SUPPRESSED_AUTHORIZATION and ALERT_INELIGIBLE. Inbox pushSummary overlays DEAD from
the existing outbox terminal state, retaining destination attempt history. A later
successful device cannot erase an earlier permanent failure. Lost job ACK after a
durable accepted/permanent receipt does not resend that destination or turn a
permanent result into success. Independent jobs keep using existing worker slots.
Existing low-cardinality outbox metrics/DEAD diagnostics apply; no new public
Actuator exposure or Administration alert API was added.

Provider acceptance is not phone delivery/display. If the provider accepted an
external effect before receipt persistence crashed, at-least-once replay may show
a duplicate notification. Collapse keys reduce duplicates, not exactly-once
guarantees. Revocation after final preflight cannot recall an already in-flight
generic notification; opening it always requires a fresh authorized server read.

## Lifecycle, privacy and regression audit

Flutter's injectable MessagingPort handles explicit OS permission, denied/reduced
availability, refresh and exceptions. A private atomic identity file stores only
User/registration UUID; Firebase owns the current token. Session epochs and actual
Firebase UID fence registration, logout, hint reads and callbacks. Reconnect and
foreground coalesce event-driven registration attempts; no Timer/HTTP replay queue.
Logout drains old in-flight registration before best-effort owner deactivation,
then deletes the SDK token while the old account is still current. Offline logout
cannot assert backend deletion and never carries that request into another User.
Background clears retrieved detail; Alert capability revocation clears same-Tenant detail
and in-flight reads. System push cannot acknowledge mobile business commands.

Web query keys include area/Tenant/User, cancel on disposal, and confirmation state
is remounted on Tenant/User/Alert change. Closed typed receipts validate owner,
resource, lifecycle/version before any success feedback. A 403/404/409/503 does not
claim success or retry an operational write. The current approved mapping exposes
read-only production Alerts; write UI requires actual downloaded separate grants.

V1–V25 normalized hashes are tested; the exact schema/RLS inventory assertions
were advanced to V26/51 tables/49 RLS tables/56 policies, not loosened. Drift remains
v9 and its generated source is unchanged. Road/Yard/Shift/media/offline worker and
existing physical state machines were not changed. No broker or new infrastructure.

Focused H evidence: 39 Backend security/upgrade/module tests; 75 notification/
device/inbox/schema/startup tests (overlapping, not a full total), all zero failures,
errors and skipped. Mobile 62 notification/session tests plus fatal-info analysis;
Web 68 Alerts/Operations plus 9 Admin layout tests, typecheck/lint; OpenAPI 5 new
contracts plus validation; dependency-free CI policy 12. Final complete suite counts
and current integrable revision are recorded in PR #110 after FULL, not inferred
from these focused runs. Failed development logs were retained outside the repo.
Additional crash/outbox/module focused run: 20 passed, zero failures/errors/skipped;
two concurrent versioned acknowledgement/resolution regressions also passed under
the runtime role. Original retry/lease history and immutable failed attempts remain
visible. No full historical suite was substituted by these checkpoint filters.

## Deferred policies and release limits

OQ-059 remains open: production recipient policies, definitive severities,
escalations and write-role authority are not invented. OQ-057 retention remains
separate. No #57 detector, Customer Portal, generic audit UI or automatic physical
reconciliation was implemented. No real FCM/APNs credentials or Android/iOS physical
device were used here. Release #65 must exercise precise native permission flows,
token refresh/rotation, OS display while foreground/background/terminated, APNs
signed provisioning, account switches and offline logout limitations.

PR: https://github.com/Dagt/TrailerSaas/pull/110 (open, NO MERGE). Final evidence is
maintained in the PR description to avoid another source commit solely to insert
the SHA of that same commit. Description edits do not rerun FULL CI. A protected
current merge revision remains required; see the explicit owner configuration
and recorded GitHub 403 limitation in ../development/ci-optimization.md.
