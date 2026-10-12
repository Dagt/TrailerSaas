# Issue #54 — durable private media and command dependencies

Baseline: `f2bb5529466acbcd145fa1f4e8007c0efe115ef8`, merge of PR #100.
The approved #53 HEAD, V20 and OperationalEvidencePolicy were verified in main.

## Coverage plan prepared before implementation

| Acceptance | Reuse | Addition / verification |
| --- | --- | --- |
| Durable media | Drift v3, namespaced cache/commands | Incremental v4 local_media, real v1/v2/v3 migrations and reopen tests |
| Private bytes | application-support path, UUID generator | Streaming file store, actual hash, atomic rename, orphan inspection and namespace/path tests |
| R2 protocol | #52 endpoints and authenticated ApiClient | Typed document client, separate unauthenticated streaming PUT, restart/lost-ACK HTTP tests |
| Retry / lifecycle | #46 ReplayWorker fenced lane and session | Media passes inside the same lane, durable retry, no reset of live UPLOADING |
| Dependencies | immutable command intent / command graph | Namespaced media edges and separately frozen HTTP request, all-required-files gating |
| Safe cleanup | Accepted grace-period rule | Finalized + all dependent ACK only, durable deletion marker, crash recovery |
| UI | SyncPanel accessible without assigned Trips | Media states/counts and real Sync now, no workflow/camera screens |
| Server authority | #53 validation and current auth | No invented #43/#45 endpoints, no capability/Backend/OpenAPI changes |

## Recoverable checkpoints

A: schema v4, private file store and migration/storage tests.
B: typed transport, actual streaming PUT, retry and finalization recovery.
C: immutable materialization and media dependencies in the existing replay lane.
D: sync visibility, documentation, independent review and four-job final-head CI.

Do not merge. This document records progress, not completion of future workflows.

### Checkpoint A

Drift v4 adds local_media, namespaced command_media_dependencies and immutable
materialized_requests. The original six tables/command trigger are retained;
v1/v2 migrate explicitly and v3 preserves existing rows. The file store writes
streams through an exclusive private temporary file, flushes/closes/renames, then
hashes actual bytes without buffering a whole image. UUID-only namespaces reject
paths and symlinks. Failed or interrupted captures remain inspectable, not erased.
The already locked crypto 3.0.7 is now a direct dependency; no version is upgraded.

32 storage/migration/cache/command tests pass. Drift generation and static analysis
are checked locally. Next: finish typed R2 transport, recovery and HTTP failure
tests (B), then integrate media passes/materialization in ReplayWorker (C).
