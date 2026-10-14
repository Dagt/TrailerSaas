# #55 — PostgreSQL outbox and durable job worker

Baseline: `efc615a484cba24a8446d2f5c39988c472f91d05` (merged #108, closed #51).
#19/#26/#21 are closed and implemented. Its tree equals the validated #108 HEAD
(`983d1bb`): final Actions #143 passed Backend 2251, Mobile 555, Web 244, OpenAPI 39.
These are baseline evidence, not final #55 results. V24 is the latest migration;
#55 adds V25 without rewriting historical SQL. ADR-022, ADR-013, ADR-015 and
schema amendment Q govern this implementation.

## Acceptance matrix / implementation checkpoints

| Checkpoint | Required evidence |
|---|---|
| A baseline/contracts | Accepted ADR/Q audit, runtime role, transaction hook, synchronous Missing alert retained |
| B V25 | outbox_jobs constraints, immutable intent/terminal state, RLS, indexes, grants and Flyway history |
| C producer | Registered typed/versioned payload, aggregate validation, MANDATORY business transaction, commit/rollback tests |
| D claim | Bounded due rows, SKIP LOCKED, committed lease/token/attempt, independent workers/connections |
| E recovery | Expired lease, new token, old-token/expired-token fencing, crash/at-least-once/idempotent effect tests |
| F retry | Bounded attempts/backoff/jitter, safe error codes, exhaustion DEAD, no resurrection |
| G tenancy | Explicit deployment Tenant allowlist, transaction-local context each time, runtime NOBYPASSRLS, no global execution |
| H operations | Spring scheduler, bounded concurrency/timeouts, Micrometer low-cardinality metrics, safe structured logs |
| I final gate | Full Backend/Mobile/Web/OpenAPI on published HEAD; direct CI logs; Draft until all green; NO MERGE |

## Architecture policy

The generic jobs module consumes only the public identity TenantContext executor.
Business modules publish through a public typed port. Registered handlers own
payload/resource/Customer-scope validation and idempotency. #55 supplies test-only
handlers for lifecycle evidence, not artificial production notification effects.
No production job type for #56/#57 is introduced.

Tenant discovery is an explicit deployment-controlled allowlist, not enumeration
of tenants or a SECURITY DEFINER/BYPASSRLS directory. This configuration is a
technical service authorization boundary, never an HTTP caller-provided UUID.
Each independent transaction reestablishes existing TenantContext and verifies
Tenant existence. A missing, bootstrap, foreign or NULL-Tenant context fails closed.
Global jobs remain inaccessible to runtime; their future administrative path is
not implemented. Setting a Tenant UUID does not replace handler resource/scope
authorization. Multi-instance workers use the same least-privilege runtime role.

Operational defaults: worker disabled until operations
configures the allowlist and enables it; poll every 5 seconds; round-robin one
Tenant per poll; batch/concurrency 4; handler timeout 30 seconds; lease 2 minutes;
5 attempts by default, hard maximum 20; initial retry 30 seconds, exponential
backoff capped at 30 minutes, 0.8–1.2 jitter capped again. Database clock governs
availability/leases; controllable policy time/jitter support deterministic tests.
Interrupted handlers must cooperate with cancellation and use bounded downstream
timeouts. Non-cooperative work retains its concurrency slot until it really exits;
the worker cannot create unbounded replacement threads. External effects remain
at-least-once, including timeout/crash after an external effect but before ACK.

The existing V24 Missing reconciliation, synchronous WARNING/OPEN alert, event,
ledger, physical movements and QR/mobile replay are unchanged. No FCM, stale scan,
document deletion, webhook, external broker, new business ledger or mobile queue
belongs to this issue. Production rollout/retention and handlers remain separate.

## Schema and lifecycle

V25 introduces only `outbox_jobs`. Intent identity, Tenant, type, aggregate,
payload, maximum attempts and creation time cannot be rewritten; runtime has
SELECT/INSERT/UPDATE but no DELETE, no table ownership and no BYPASSRLS.
PENDING starts at attempt 0. Claim increments attempts and commits PROCESSING
with a fresh UUID token and database-derived lease. SUCCEEDED/DEAD clear the
lease and retain a server terminal timestamp. Terminal rows cannot resurrect.
Q stores a monotonic attempt counter and latest safe failure, not a second
business-event ledger. Structured result logs correlate attempts by job ID.

Payloads use the closed envelope `{"schemaVersion":1,"data":{...}}` with a positive
integer version (1–999), maximum UTF-8 JSON size 16 KiB and registered record DTO.
Job/aggregate names are bounded uppercase identifiers (64 characters). The
registry rejects unknown fields, missing creator properties, scalar coercion,
duplicate keys and unsupported type/version combinations. The handler validates
actual same-Tenant aggregate/payload resources both on enqueue and execution.
No arbitrary map, class name, plugin or user code is dynamically dispatched.
All current registered types are Tenant-only; NULL-Tenant producer calls fail.

## Transaction and claiming protocol

`DurableJobPublisher` has MANDATORY propagation and requires the caller's already
established matching TenantContext. It does not choose a Tenant for an unscoped
business transaction. Required outbox failure marks the joined transaction
rollback-only even if a caller catches the database exception. No REQUIRES_NEW,
in-memory event publication or mobile ledger substitution is used.

`OutboxStore` claims through one CTE: select due PENDING or expired PROCESSING
rows for the authorized Tenant, order by due/expiry time plus ID, apply
`FOR UPDATE SKIP LOCKED LIMIT ?`, update token/lease/attempt, RETURNING the claims.
A separate bounded CTE marks expired exhausted claims DEAD. Each poll handles at
most the configured claim batch and a bounded exhausted-recovery batch. SQL uses
`clock_timestamp()` consistently, not device/JVM clocks. Initial scheduling is an
explicit producer Instant; subsequent retry availability is database time + delay.

Claim commits before handler execution. Outcomes use a new Tenant-scoped runtime
transaction and `WHERE id AND tenant_id AND status='PROCESSING' AND locked_by
AND locked_until>clock_timestamp()`. Expired tokens fail even before a replacement
claim exists. Losing a result write leaves a committed recoverable lease.

Handler DB callbacks use `JobExecution.inTenantTransaction`: reestablish context,
lock/fence the current job row, validate before and after the callback, and roll
back interrupted/expired effects. Keep these callbacks short and free of network
IO. Worker-only transactions apply LOCAL 5-second statement and 1-second lock
timeouts, which reset with the transaction and do not change producer transactions.
External handler work executes without a claim transaction/row lock held.

## Retry, deadlines and shutdown

Retryable failures return PENDING with future availability; permanent, unknown,
unsupported or invalid work becomes DEAD. Attempts at the persisted limit also
become DEAD. Expired nonexhausted work receives a new token and increments its
attempt on reclaim; exhausted leases become DEAD without invoking another handler.
`last_error` accepts only fixed safe codes, never exception messages or payload.

Virtual-thread execution has a semaphore bound, a batch-wide handler deadline and
interrupt-on-timeout. Cancellation never releases a concurrency slot before the
actual task exits, including cancellation before its callable starts. A handler
ignoring interruption cannot trigger an unbounded succession of replacement
threads. Other Backend instances can continue; operators must restart a saturated
instance or correct the noncooperative handler. Java cannot safely kill arbitrary
in-process code. Downstream clients must enforce their own shorter timeouts.
Shutdown stops new polling and interrupts handlers; committed leases survive.
Disabled configuration blocks both scheduler and explicit internal polling.

The precision audit reproduced a positive nanosecond lease becoming a zero
millisecond PostgreSQL interval. Configuration now rejects handler timeouts below
1 ms (lease must be at least twice that), and the claim primitive independently
rejects submillisecond leases. A deterministic policy regression covers this
fail-fast boundary; a bad configuration cannot enter an always-failing poll loop.

## Monitoring and technical operations

Micrometer registers low-cardinality `jobs.stored{status}` gauges, oldest eligible
pending age (seconds), worker enabled, claim/handler/processing/poll timers and
success/retry/dead/stale/recovery/exhaustion/handler-failure/infrastructure/stop
counters. The only dimension values are closed statuses and safe error codes;
Tenant/User/Trailer/job IDs and raw errors are never labels. Queue gauges cache
the last authorized per-Tenant sample and aggregate only configured scope; they
are refreshed per poll, not by unscoped scrape-time queries. Disabled workers do
not imply a globally empty database. Logs include job ID, Tenant ID, attempt,
outcome and bounded error code, without raw exceptions, credentials or signed URLs.

The installed Spring Actuator/Micrometer mechanisms are retained. HTTP exposure
remains health-only: no public metrics/dead-job/Administration endpoint is added.
For local OS-authorized technical inspection, opt into existing Actuator JMX with
`SPRING_JMX_ENABLED=true` and
`MANAGEMENT_ENDPOINTS_JMX_EXPOSURE_INCLUDE=health,metrics`. The JVM-local Metrics
MBean exposes registry names/values; no remote JMX listener/port is configured.
Remote access requires a separately secured operations deployment; never expose
unauthenticated HTTP metrics. A real integration test invokes the Metrics MBean,
observes actual DEAD state and verifies HTTP `/actuator/metrics` remains 404.
DEAD counts/errors and immutable rows support technical triage; #55 deliberately
does not offer automatic retry/reset or deletion of terminal history.

## Crash and regression evidence

Real PostgreSQL 18.6 Testcontainers/runtime-role tests cover business+outbox atomic
commit/rollback and actual INSERT-privilege failure, commit-before-claim,
claim-before-handler crash, rollback during handler, effect commit before lost
success persistence, late/stale worker, competing claim/recovery/retry connections,
SKIP LOCKED, exhaustion, terminal immutability, connection termination/recovery,
pooled context/timeouts, foreign/global rejection, malformed/unknown payload,
bounded/uncooperative timeouts and safe metrics/logs. A test-only Customer update
keyed by job ID demonstrates durable idempotent logical effects on redelivery;
this is not an assertion of exactly-once external delivery or a production handler.
Policy tests use a fixed clock/jitter; lease tests wait only short test leases,
not production intervals. Frozen normalized SHA-256 hashes independently guard
every baseline migration V1–V24 in addition to Flyway validation.

Historical tests remain: exact migration/table/policy/module inventories evolve
to V25, one additional table/policy and the jobs module. Prior domain commands
still assert they generate no artificial outbox rows. V24 Alert/event/ledger
transaction and all existing frontend contracts are unchanged. #56/#57, global
maintenance, retention/cleanup, alerts inbox and external delivery are deferred.
Final full-suite counts and exact-HEAD Actions evidence are recorded in the PR;
no pending/focused run is substituted for the full final gate. NO MERGE.
