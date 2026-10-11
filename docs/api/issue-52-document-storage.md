# Issue #52 implementation and review plan

Baseline: `aa616f0c831df416e683e24849b9b52653044967` (PR #98). Status: implementation in progress; not complete until merge.

## Acceptance traceability (prepared before implementation)

| Criterion | Planned code / endpoint | Planned verification |
| --- | --- | --- |
| Metadata, catalogs, Tenant-safe links | V19; Documents repository | PostgreSQL 18 DDL, CHECK, FK, uniqueness, runtime privilege and RLS tests |
| Authorized initialization; direct binary PUT | Documents controller/service; ObjectStoragePort | HTTP authorization/strict DTO tests; real S3 protocol PUT tests |
| Verified finalization | upload-completion; S3 adapter | Real HEAD/GET/COPY, size/MIME/SHA mismatch and missing-object tests |
| Immutable published bytes | Separate temporary PUT key and fresh private publication key | Reuse old PUT URL after completion; final GET bytes unchanged |
| Idempotency and atomicity | Existing Identity-owned MobileOperationLedger; Document row lock | Concurrent completion, key reuse, rollback and lost-response replay |
| Resource linking | Trips-owned DocumentStopDirectory; links endpoint | Fresh assignment, Tenant integrity, duplicate links, revoked access |
| Metadata and signed download | Document access policy; GET and download-url | Customer A/B, shared Stops, unlinked/pending denial, safe DTOs |
| Orphan eligibility, no worker/deletion | Documents-owned pending-upload inspection hook | Old pending/unlinked only; finalization races and Tenant context |
| Environment isolation and fail closed | Typed R2 configuration and SDK adapter | Disabled/local configuration; TLS/host/bucket/credential validation |
| Contracts and documentation | OpenAPI, documents/environment/backend docs | OpenAPI validation and existing/new contract assertions |
| Existing behavior preserved | No physical, Mobile or Web changes; V1–V18 unchanged | Full Backend, Mobile, Web, OpenAPI CI on final HEAD |

## Security and publication design

Signed PUTs address only a server-generated temporary key. Finalization copies to
a fresh private publication key and verifies that copy's actual size, content type
and SHA-256 bytes before publishing its identity in PostgreSQL. A previously issued
PUT can never address the publication key. Copies left by a failed database
transaction remain private and unpublished; retry creates and verifies a fresh
candidate. A committed completion replays through the existing ledger.

Only the existing `POD_UPLOAD` permission supports upload now: BOL, POD and delivery
photos for an active Driver. Other document types remain recognized storage types
but their upload workflows await #43/#45/#53. Read permissions confer no upload
authority. Linking requires current Driver assignment to the target executable
Stop. Shared Stop documents require access to every mapped Customer for a
Customer-scoped reader; ambiguous/empty Customer context fails closed. #53 will
define narrower evidence associations without broadening this infrastructure.

No jobs run automatically. Retention remains blocked by OQ-057. No evidence
validation, delivery completion, Documents UI/media queue or physical lifecycle
changes are part of #52.

## Recovery checkpoint

Recovered on the existing `issue-52-r2-document-storage` branch: V19, all five
Documents endpoints, the S3/R2 adapter, public authorization/resource boundaries,
PostgreSQL and signed HTTP protocol tests, OpenAPI and configuration documentation.
The implementation was previously uncommitted; no unrelated work was discarded.

Verified locally before recovery: Web 244 tests plus typecheck/lint/build, Mobile
186 tests plus locked dependencies/Drift/format/analyze, and OpenAPI 8 tests plus
validation. Targeted Documents PostgreSQL tests passed. The first full Backend
run exposed old exact V18/table/RLS inventories; these now include V19 without
removing any prior assertion. Next step: repeat complete Java 25/PostgreSQL 18
`clean verify`, finish the security review, then validate all four CI jobs on the
published final commit. This checkpoint does not declare #52 complete or ready
to merge.
