# #57 — scheduled stale Trailer detection

## Verified baseline (A)

Main/base `460ad608d59eba78c749993cda0f067d9c77fa63`, merged #110;
#30/#39/#55/#56 closed completed and #57 open. Clean prior branch, no stashes
or unpublished work. V26 is the latest migration. Historical V1–V26 are immutable.
Previous validated #110 suites: Backend 2397, Mobile 581, Web 262, OpenAPI 44;
these are reference counts, not final #57 evidence. Branch:
`issue-57-stale-trailer-detector`. NO MERGE.

The owner explicitly deferred recipient/escalation/write-role policies. OQ-059
does not block this detector; no production policy or human role grant is added.

## Physical and execution semantics (B)

The current positive Tenant Settings threshold is read for every batch. The
existing initial 72-hour value is not a global detection constant. Only active
Trailers with non-null, nonfuture physical last_seen_at are eligible. Inclusive
comparison: last_seen_at <= database evaluation time minus current threshold.
Administrative updated_at, replay reception time, load/OOS corrections and a
missing-location classification are not physical observation substitutes.
Existing movement rules advance physical freshness using occurredAt for departure,
arrival, yard movement, correction/recovery and hook/drop; the detector writes no
movement, Trailer projection, Trip, Order, User or mobile ledger operation.

Use one typed versioned STALE_SCAN job per bounded batch through #55. The Spring
scheduler only initiates/resumes durable work for an explicit enabled Tenant
subset of the existing worker allowlist. A single RLS-protected technical cursor
row per Tenant bounds scheduling/concurrent continuation state; it is not another
queue or business ledger. Claim tokens and the current scan/job binding fence
all DB effects. Cursor and next-batch enqueue commit with Alert mutations.
An expired/lost ACK replays safely; an old completed batch does not repeat effects.
A DEAD batch remains observable and a later authorized schedule resumes its
uncommitted cursor through a new job, without resurrecting terminal outbox rows.

Trailer row locks serialize detection with physical projections. No SKIP LOCKED
cursor advancement may permanently omit a busy Trailer: bounded lock timeouts
retry the batch through the existing worker. Unresolved Alert uniqueness is also
enforced by a partial unique PostgreSQL index for OPEN/ACKNOWLEDGED STALE_TRAILER.
Original evidence (physical timestamp, threshold, detection time, job and SYSTEM
origin) never changes on repeated scans. A resolved occurrence is terminal.

Recovery is evaluated per Alert type. STALE may recover in transit; Missing keeps
its existing AT_LOCATION requirement. A valid observation within the current
threshold resolves stale with SYSTEM provenance. Increasing the configured
threshold may also make the condition no longer stale; record a distinct policy
change reason, never a fictitious movement. Administrative Trailer edits alone
cannot freshen last_seen_at. Null/future observations are insufficient evidence
for either a new stale occurrence or automatic recovery; retain diagnostics.
Inactive Trailers are not new candidates; administrative deactivation alone does
not rewrite/resolve an existing occurrence.

## Acceptance matrix

| Checkpoint | Evidence required | State |
|---|---|---|
| A/B | Baseline, accepted contracts and physical/bounded execution semantics | Verified/designed |
| C | V27, SYSTEM provenance, Missing compatibility, immutable evidence, RLS/unique index | Implemented; focused evidence below |
| D | Explicit deployment config, typed existing-worker jobs, durable cursor/recovery | Implemented; focused evidence below |
| E/F | Idempotent creation, current threshold, safe automatic resolution/concurrency | Implemented; focused evidence below |
| G | Existing Alerts API/Web/OpenAPI, authorized minimal detection projection | Implemented; focused evidence below |
| H | Real PG18.6 migration, lifecycle, security, race, crash, metrics and FCM test policy | Implemented; focused evidence below |
| I | Configuration, limits, policy deferral and regression evidence | Implemented; focused evidence below |
| J | Complete final integrable-HEAD CI, clean published branch, Ready, no merge | Pending final candidate CI |

Draft checkpoints use focused tests. Final Ready triggers complete CI only once
the candidate is finished; failures return to Draft and revalidate a new candidate.

## Checkpoint C evidence

V27 adds only one technical cursor table, composite Tenant/job references, immutable
system evidence and guarded system recovery. Existing V24 Missing trigger bodies
are retained and invoked only for Missing. A partial unique index protects both
OPEN and ACKNOWLEDGED stale occurrences. No historical SQL changes. Threshold
comparison uses numeric elapsed seconds to support the existing full positive
integer setting range without timestamp-subtraction overflow; nonfinite values
are rejected conservatively. Initial focused PostgreSQL run: 41 tests passed
(schema plus original Missing reconciliation/inbox); subsequent schema/module run:
4 passed, zero failures/errors/skipped. These are focused, overlapping evidence.

## Durable execution checkpoint D

The existing worker's explicit deployment allowlist is exposed through a read-only
public TenantJobScope port. Detector configuration must be an enabled subset;
defaults remain disabled. Initial defaults are 30-second scheduler poll, one-hour
cycle and 100 Trailers per job (limits 5s–1h poll, 1m–1d cycle, 1–500 batch).
The scheduler visits one configured Tenant per poll, creates or resumes a durable
chain, and does not enumerate Tenants. Continuations share a logical scan ID and
commit cursor/next-job/Alerts together. Repeated batch ACKs are no-ops.

Runtime deliberately lacks UPDATE on Tenant administration, so PostgreSQL
SELECT FOR SHARE cannot be used directly on that table. V27's closed
lock_stale_tenant function only locks the current live, scoped detector job's own
Tenant. It returns a boolean, grants no mutation/directory capability, checks the
current Tenant context and existing job/cursor binding, and exposes no foreign
data. All ordinary queries still run with the runtime role and RLS. No broad
Tenant UPDATE, BYPASSRLS or administrative connection was introduced.

Focused detector/condition run: 13 passed, zero failures/errors/skipped. Includes
actual worker claiming, concurrent schedulers/workers, paginated continuations,
no physical/ledger mutation, current Tenant thresholds, in-transit recovery,
repeated ACKNOWLEDGED occurrences and the real recipient/outbox/FCM handler through
an explicit test-only compiled policy. Production has zero such policy beans.

## Configuration and operations

Disabled by default. Explicit detector Tenants must be an enabled subset of the
existing worker allowlist; no Tenant directory/global scan exists. Example
configuration (only substitute deliberately approved Tenant IDs):

```yaml
trailersaas:
  jobs:
    enabled: true
    tenants: ["<approved-tenant-uuid>"]
  stale-detector:
    enabled: true
    tenants: ["<approved-tenant-uuid>"]
    poll-interval: 30s
    scan-interval: 1h
    batch-size: 100
```

Standard environment binding supports TRAILERSAAS_STALEDETECTOR_ENABLED and
TRAILERSAAS_STALEDETECTOR_TENANTS. Duplicate/over-1000 Tenants, empty enabled list,
invalid duration/batch and worker scope mismatch reject startup. No new business
threshold: existing Tenant Settings remains a positive integer, initially 72h,
read under a shared lock in each batch. Numeric elapsed arithmetic supports its
full accepted integer range without overflowing PostgreSQL timestamp subtraction.

One Tenant per poll, round-robin: with N Tenants scheduling opportunity is roughly
N × poll interval plus worker queue time. A completed cycle waits scan-interval.
This is periodic attention, not an immediate movement notification SLA. Frozen
UUID upper bound/cursor and batches bound each transaction; newly inserted rows
missed by the cursor are evaluated next cycle. Inactive rows count toward batch
size. Busy Trailer locks cause a retry, never SKIP LOCKED cursor advancement.

STALE_SCAN v1 payload contains only logical scanId; aggregate is its own TENANT.
The existing #55 worker supplies claims, token-fenced Tenant transactions,
attempts, backoff, timeout, leases and sanitized errors. Defaults remain 5 attempts,
30s initial exponential retry capped at 30m with bounded jitter. Handler DB work
has 5s statement and 1s lock limits; DB time drives availability/evaluation.
Continuation enqueue, cursor and Alert changes commit or roll back together.
Lost batch ACK replay is a no-op. DEAD stays terminal; at/after next_scan_at an
authorized scheduler creates a new job resuming the unfinished logical cursor.
Old DEAD history remains inspectable. Investigate persistent failures rather than
resetting terminal rows. One failing Tenant does not block independent work.

## V27, invariants and provenance

One RLS-scoped technical cursor row per Tenant is not another queue or ledger.
Composite Tenant/job FKs and the current live job/cursor binding protect detector
references. Runtime has no BYPASSRLS, superuser or Tenant administration UPDATE.
The closed row-lock function checks current Tenant, no bootstrap User and live
registered job binding, uses fixed search_path and revokes PUBLIC execute. It
provides no mutation/directory capability. Ordinary work uses runtime credentials.

STALE_TRAILER is WARNING/OPEN with SYSTEM origin, real detector job, server
detection time, original physical last_seen_at and threshold. No invented human,
mobile operation, Yard finding or physical event. SQL rechecks active physical
evidence/settings under locks. Partial unique index covers OPEN and ACKNOWLEDGED.
Repeated scans leave every original field/version/ack unchanged and do not request
notification again. SYSTEM recovery records the real current observation,
threshold, job, server time/reason; resolved_by is NULL. ACK history survives and
RESOLVED remains terminal. Later inactivity creates a new occurrence.

Stale may recover in transit; Missing retains active AT_LOCATION recovery, mobile
identity and Yard association invariants. Existing Missing trigger bodies remain
unchanged and type-specific. V1–V26, V24 alerts, mobile ledger, movement rules and
Drift v9 are untouched. V27 backfills only the derived HUMAN_USER provenance of
historical human resolutions, preserving original history.

Only physical occurredAt freshness matters. Administrative updated_at, receipt
of old offline movement, designation/load/OOS changes do not freshen evidence.
Null/nonfinite/future observations are diagnostics, not stale/recovery proof.
Administrative deactivation stops new alerts but leaves existing history open;
it is not evidence of physical recovery. Increasing the threshold may resolve
as explicit policy re-evaluation with a distinct reason/evidence; decreasing it
may create a later occurrence. Neither operation fabricates movement.

## API, Web and notification integration

Existing Alerts inbox/detail/filter accept Missing and STALE, preserving current
ACTIVE membership/Tenant, ALERT_VIEW, Trailer/Customer scope, cursor and 1–100
limit. Stale has closed nullable detection: detectedAt,lastSeenAt,thresholdHours,
elapsedHours calculated **at detection by the server**,jobId. Separate
resolutionEvidence carries current lastSeenAt,thresholdHours,jobId. Actor types
are explicit. Missing detection is null; minimized Yard context stays intact.
No raw job payload/mobile identity/device token/arbitrary metadata is exposed.

AlertsWorkspace reuses the existing inbox/filter/Trailer link and history. It
labels physical observation, applied threshold, original elapsed time and SYSTEM
recovery; browser time is not authority. Effective capabilities still govern
write buttons. Operations Dashboard links to the same authorized inbox, not a
parallel dashboard or false Trip-based stale Trailer count.

**Zero automatic production recipients.** OQ-059, OQ-057, final severity/escalation
and human write-role assignments remain pending client discussion. WARNING is
technical provisional attention, not final client policy. No notify-all, Admin,
Operations, Driver default or subscription exists. No production
StaleAlertRecipientPolicy is registered. The explicit compiled opt-in interface
uses AlertNotifications.requestDelivery in the detection transaction. A test-only
authorized policy proves real detection → Alert → recipient → outbox → FCM handler.
Future approved policies must pass fresh recipient/Trailer/Customer checks at
production and delivery. FCM acceptance is not delivery; inbox works without push.
No client submits SYSTEM provenance. Existing separate ALERT_ACKNOWLEDGE and
ALERT_RESOLVE capabilities remain unassigned/deny-by-default.

## Observability

Low-cardinality existing Micrometer counters: stale.detector.scans.started,
completed,failed,recovered; tenants.processed; trailers.evaluated/stale;
alerts.created/reused/resolved; errors.retryable; observations.insufficient/future.
Timer scan.duration and gauge jobs.dead complete the prefix. DEAD is the sum of
configured Tenant counts refreshed at scheduling. Committed effect counts are
recorded after transaction success. Metrics can reset at restart; PostgreSQL
retains durable history. No IDs/error text/tokens are tags. Safe scheduler logs
omit payloads/secrets. Existing #55 diagnostics expose DEAD/last_error; HTTP
Actuator remains health-only, using existing approved registry export or secured
local Actuator JMX for technical operations. No public admin diagnostic API.

## Focused validation and security audit

Real Testcontainers PostgreSQL18.6/runtime RLS and Java25 cover inclusive 72h,
current independent/int-max thresholds, absent/future/nonfinite/inactive facts,
repeated OPEN/ACK, recurrence, real in-transit physical recovery, late old movement,
two schedulers/workers, blocked physical row retry without cursor loss, rollback
of continuation enqueue, lost ACK/expired lease fencing, DEAD resumption and
independent failing/healthy Tenants. SQL unique index and immutable history are
exercised beyond SELECT-before-insert. API verifies typed SYSTEM projection,
foreign 404, revoked access and denied human writes. Metrics exclude ID tags.

V26→V27 first creates/resolves a real Missing occurrence and preserves its original
Alert fields, mobile ledger and movements. A separate hash fixture freezes all
V1–V26 normalized bytes. Strict current schema/RLS inventories advance to V27;
frozen historical V25→V26 upgrade tests remain unchanged. No test is weakened.
Focused runs overlap and are not summed as full-suite evidence. Web affected
suites: 72 passed; typecheck/lint passed. OpenAPI: valid, 48 passed (44 historical
+4 #57). An initial local OpenAPI copy missed historical Backend/iOS fixture
files; complete-repository rerun passes. Failed diagnostic logs are retained.

The first integration run exposed PostgreSQL's UPDATE-privilege requirement for
Tenant SELECT FOR SHARE. The narrow current-job lock function fixed this without
granting Tenant UPDATE or changing ordinary worker credentials. A later audit
changed detector scheduling from application time to database clock consistently.

Final current-HEAD Full CI evidence is recorded in
[PR #111](https://github.com/Dagt/TrailerSaas/pull/111); Draft focused checks are not
final approval. No physical Android/iOS QA was performed (#65 release gate).
No Flutter/Drift change, continuous tracking, retention, #58, #61, automatic
recipients/escalation or human permission grants belong to this implementation.
PR remains unmerged.


Final focused checkpoint: 105 Backend tests passed, zero failures/errors/skipped
(`StaleDetectorPostgresTests`, `StaleSchemaPostgresTests`, `StaleUpgradePostgresTests`,
`StaleConditionTests`, `TenantRlsPostgresTests`, `NotificationSchemaPostgresTests`,
`AlertInboxPostgresTests`, `OutboxSchemaPostgresTests`, `TrailerSaasApplicationTests`).
Separate Modulith validation: 2 passed; CI Policy: 12 passed. The new stale tests
are 26 real worker/DB/API tests, 3 schema/security tests, 4 deterministic condition/
configuration tests and 1 actual historical upgrade test. A test setup initially
used a 15s lease with the existing 3s expiry wait; changing that new fixture lease
to 1s made the intended expiry test deterministic without weakening any timeout,
assertion or production lease policy. The isolated regression and expanded suite
both passed. No implementation tests were removed, disabled or skipped.
