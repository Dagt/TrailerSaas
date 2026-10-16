# #60 — Customer inventory, deliveries and documents

## Baseline / bounded scope (A)

Verified main/base: `c5777cef44a7737ebe67dc876ff0da12dff737e7`, merge #113.
#41/#52/#53/#58/#59 closed; #60 open. Clean checkout, no stashes or partial
#60 branch. Branch: `issue-60-customer-inventory-deliveries-documents`. NO MERGE.

Reuse React/Firebase/CustomerContext/QueryClient, Spring modules, TenantContext,
runtime PostgreSQL RLS, Trailer reads/timeline, Order progress and private R2
metadata/download. No new role grants, authentication, queue, physical transition,
Customer uploads, Audit #61, deployment/retention or Mobile/Drift changes.

## Audited gaps and implementation decisions

* Trailer reads are currently unbounded and Customer DTOs include Driver/Tractor
  identities; timeline visibility follows current designation even for older facts.
  Extend the existing read surface with bounded keysets and explicit selected
  Customer context, redact internal identities, and constrain scoped history to
  the current designation period and Customer-owned operational associations.
  Internal Admin/Operations history stays intact.
* Order progress is per Order. Add a bounded Customer delivery read projection
  over actual Order/Trip/Stop mappings; never fetch every Order in React.
* Document transport has all-or-nothing linked-resource authorization, but no
  discovery index. Reuse that rule with the selected single Customer even for
  multi-role/multi-Customer users. Only published accepted delivery files qualify.
* **Demonstrated persistence gap:** `delivery_evidence` records the acceptance
  mode/actor/time, but `recordDelivery` discards its validated BOL/photo UUIDs.
  `document_links` can also contain unrelated later uploads, so it cannot prove
  which file was accepted. V29 will add nullable historical accepted-file
  references to the existing receipt, with Tenant-safe FKs and mode/pair checks.
  The existing writer will populate them atomically after actual Stop completion.
  No historical evidence IDs are guessed or backfilled. Old receipts still report
  their real acceptance, with file association unavailable. No second evidence
  model, change to POD policy, state machine or V1–V28 checksum.
* Customer-scoped downloads reuse the existing URL endpoint with an explicit
  Customer parameter and fresh authority. URLs stay ephemeral, not Query cache,
  logs or browser storage. R2 failure never becomes a successful preview.

## Acceptance matrix — implementation evidence follows checkpoints

| # | Requirement | Planned evidence / current status |
|---|---|---|
| 1 | Real inventory | Existing Trailer read, bounded SQL / pending |
| 2 | Authorized Trailers only | Designation + fresh capability / pending |
| 3 | Filters | Actual server filters/keysets / pending |
| 4 | Current Location | Operational state-specific rendering / pending |
| 5 | Last Known separate | Explicit historical label / pending |
| 6 | Unknown respected | No fabricated current position / pending |
| 7 | Authorized timeline | Current designation period / pending |
| 8 | Historical Customer privacy | Transfer/shared-work regressions / pending |
| 9 | No internal identities | Redacted Driver/Tractor DTOs / pending |
| 10 | Shared Manifest privacy | Selected-Customer allocations / pending |
| 11 | Scoped deliveries | Actual SQL mappings / pending |
| 12 | Multiple Stops/Trips | Composite Order/Stop identity / pending |
| 13 | Actual progress | Persisted milestone fields / pending |
| 14 | Valid POD/BOL | Exact accepted receipt file IDs / pending |
| 15 | Documents list | Bounded authorized discovery / pending |
| 16 | Authorized files only | Published + accepted + all links / pending |
| 17 | Multi-link protection | Single-Customer fail-closed checks / pending |
| 18 | Server-signed URL | Existing download route / pending |
| 19 | Ephemeral URLs | Abort/unmount/scope fencing / pending |
| 20 | R2 unavailable | Explicit safe failure / pending |
| 21 | Navigation | Actual Order/Delivery/Trailer/file links / pending |
| 22 | Truthful dashboard | Bounded previews, no invented totals / pending |
| 23 | Customer isolation | Negative SQL/HTTP/Web tests / pending |
| 24 | Tenant/RLS | Existing runtime role, no bypass / pending |
| 25 | Revocation | Fresh Tenant/membership/role checks / pending |
| 26 | OpenAPI | Exact implemented contracts/tests / pending |
| 27 | Functional tests | Focused checkpoints / pending |
| 28 | Historical regressions | Full final candidate only / pending |
| 29 | Local final evidence | Exact SHA/counts/raw logs/hashes / pending |
| 30 | Honest hosted CI | Billing owner-deferred, never infer green |
| 31 | One Draft PR, unmerged | Create after initial checkpoint |
| 32 | #61–#65 deferred | No unrelated implementation |

## Validation and release policy

Focused tests during implementation; freeze/publish the final candidate, then run
complete local Backend/Web/OpenAPI/Mobile/CI policy suites once. Any corrective
commit invalidates prior final-SHA evidence. Record actual counts/logs in the PR.
Billing remains owner-deferred: do not retry hosted full CI, change workflows or
claim skipped checks passed. Keep Draft if unavailable. Physical/deployed browser,
Firebase/R2 provider and Android/iOS QA are not claimed; #65 stays separate.
