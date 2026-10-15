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
| C | V27, SYSTEM provenance, Missing compatibility, immutable evidence, RLS/unique index | Pending |
| D | Explicit deployment config, typed existing-worker jobs, durable cursor/recovery | Pending |
| E/F | Idempotent creation, current threshold, safe automatic resolution/concurrency | Pending |
| G | Existing Alerts API/Web/OpenAPI, authorized minimal detection projection | Pending |
| H | Real PG18.6 migration, lifecycle, security, race, crash, metrics and FCM test policy | Pending |
| I | Configuration, limits, policy deferral and regression evidence | Pending |
| J | Complete final integrable-HEAD CI, clean published branch, Ready, no merge | Pending |

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
