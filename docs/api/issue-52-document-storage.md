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

## Implemented acceptance and recovery evidence

| Requirement | Implemented files | Behavioral evidence |
| --- | --- | --- |
| Metadata and links | `V19__private_documents.sql`, `DocumentRepository` | `DocumentPostgresTests`: closed catalogs, milestones, composite FK/uniqueness, immutable published records, exact column grants, RLS with reused connection/bootstrap |
| R2/private direct transport | `R2ObjectStorage`, `ObjectStoragePort`, `R2Configuration` | `R2ObjectStorageTests`: actual signed HTTP PUT/HEAD/COPY/GET, method/key/header/expiry restrictions, missing object and provider errors |
| Initialization | `DocumentController`, `DocumentService.initialize` | Strict payloads, three currently authorized types, denied read-only roles/future workflows, failed initialization leaves no row or fake URL |
| Finalization | `DocumentService.complete`, existing `MobileOperationLedger` | Actual copied-byte SHA-256/MIME/size, missing upload, concurrent completion with PostgreSQL lock-wait evidence, lost-ACK replay, incompatible key, storage/DB rollback |
| Links | `DocumentService.link`, Trips-owned `DocumentStopDirectory` | Current Driver assignment, foreign Tenant/missing target denial, pending-document denial, duplicate link, forced link rollback |
| Metadata and download | Identity-owned `DocumentAccessPolicy`, `visibleLinks`, `download` | Customer A/B and shared/multiple Stops, revocation, additive persisted grants, unlinked download denied, safe DTO/Problem Details, provider failure never returns fake success |
| Orphan inspection | `PendingUploadInspection`, `DatabasePendingUploadInspection` | Old pending only, linked/finalized exclusions, same-Tenant context; inspection waits for in-flight publication and rejects its committed result |
| HTTP authentication | Existing Firebase/Internal User filter and Tenant authorization | All five endpoints reject unauthenticated, suspended and role-revoked callers; client actor/source hints and unknown fields rejected |
| Configuration/contracts | `.env.example`, environment docs, OpenAPI implemented schemas | `R2ConfigurationTests` profile/TLS/host/credentials/limits/readiness; three new OpenAPI assertions plus prior contract suite |

## Reviewer-style security audit

This is a second pass over the implemented code and error paths, not external
approval or a substitute for review. The audit confirmed or strengthened:

1. Every database query is tenant-pinned; RLS also denies absent/bootstrap context.
2. Customer readers must authorize every linked Stop and every mapped Customer.
   Filtering only one visible link would disclose foreign bytes and is forbidden.
3. Read grants remain additive; they never grant upload or link permission.
4. Link locks Trip parent, Driver, then Document. Finalization locks Driver then
   Document and never later acquires a Trip lock. No reverse lock order is added.
5. Completion uses the existing ledger in the same transaction as publication;
   fresh authority is checked even before replaying an already committed result.
6. R2 has no shared transaction with PostgreSQL. A failed copy/verification/update
   does not publish a reference. Retry uses a new private candidate, while a
   committed replay preserves the original key, bytes and upload time.
7. The pending PUT signs only staging. The fresh publication key is never exposed
   in a PUT grant; subsequent staging PUTs cannot change published bytes. A DB
   trigger additionally forbids any rewrite of an UPLOADED row.
8. SHA-256 hashes the copied bytes, not ETag/client metadata. Stored headers must
   match the initialized MIME; actual byte length must match the accepted size.
9. SDK logging is disabled; provider exceptions are mapped without raw causes,
   signed URLs or credentials. URLs intentionally contain SigV4 protocol fields
   and are returned only as temporary bearer grants to authorized clients.
10. Read-only metadata/download transactions use a consistent database snapshot.
    Authorization is refreshed on every request; already-issued URLs expire but
    cannot be immediately revoked, which is explicitly documented.
11. Orphan inspection is bounded and revalidates under the Document lock; it is
    not a transferable deletion lease. No jobs, retention or valid-evidence
    deletion are enabled.
12. V1–V18, physical workflows, role mappings and capability catalogs are unchanged.
    Existing schema inventory assertions are extended, never relaxed or removed.

## Local/staging verification and remaining integrations

Run `backend/mvnw -B -ntp clean verify` with Docker and Java 25. Tests use real
PostgreSQL 18 plus a controlled HTTP S3 peer independently verifying Signature V4
and storing/copying actual bytes. The peer covers the critical protocol subset;
it is test-only, not a production fallback or full R2 emulator. No Cloudflare
credentials are needed in CI.

The peer also rejects adding an unsigned `x-amz-copy-source` to a pending PUT:
it cannot turn that one-object grant into a copy of another private object.
The backend's independently signed COPY remains permitted. This follows the
[S3 CopyObject signed-header requirement](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CopyObject.html)
and [R2 Signature V4 presigned transport](https://developers.cloudflare.com/r2/api/s3/presigned-urls/).
Include this negative case in the staging provider smoke check as well.

For staging, configure a separate private R2 bucket and bucket-scoped credentials,
disable public access/R2.dev, set the reserved environment variables and
`R2_ENABLED=true`, and restrict browser PUT CORS to the exact staging origins and
required headers. Startup checks the authenticated bucket endpoint; upload also
checks availability. Confirm a direct client PUT, completion/link and authorized
GET with the deployed configuration. This provider setup is not created by #52.

#53 owns evidence-set validation and additional resource compatibility; #43/#45
own their command workflows, #54 owns Mobile media, #55 owns jobs. Unpublished
candidate objects require future prefix enumeration with reference revalidation.
OQ-057 still blocks destructive retention. A published object is not proof of a
valid POD, inspection, image format or virus-free file. No delivery_evidence,
physical state changes or evidence UI are introduced.
