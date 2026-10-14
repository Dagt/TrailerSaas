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

Defaults proposed for implementation/tests: worker disabled until operations
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

Implementation and final test evidence are added as checkpoints are completed.
