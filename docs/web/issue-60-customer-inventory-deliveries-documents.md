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

* At baseline, Trailer reads were unbounded and Customer DTOs included Driver/Tractor
  identities; timeline visibility follows current designation even for older facts.
  The existing read surface now has opt-in bounded keysets and explicit selected
  Customer context, redacted internal identities, and scoped history constrained to
  the current designation period and Customer-owned operational associations.
  Internal Admin/Operations history stays intact.
* Order progress is per Order. A bounded Customer delivery read projection joins
  over actual Order/Trip/Stop mappings; never fetch every Order in React.
* Baseline document transport had all-or-nothing linked-resource authorization,
  but no discovery index. The new index reuses that rule with the selected single Customer even for
  multi-role/multi-Customer users. Only published accepted delivery files qualify.
* **Demonstrated persistence gap:** `delivery_evidence` records the acceptance
  mode/actor/time, but `recordDelivery` discards its validated BOL/photo UUIDs.
  `document_links` can also contain unrelated later uploads, so it cannot prove
  which file was accepted. V29 adds nullable historical accepted-file
  references to the existing receipt, with Tenant-safe FKs and mode/pair checks.
  The existing writer populates them atomically after actual Stop completion.
  No historical evidence IDs are guessed or backfilled. Old receipts still report
  their real acceptance, with file association unavailable. No second evidence
  model, change to POD policy, state machine or V1–V28 checksum.
* Customer-scoped downloads reuse the existing URL endpoint with an explicit
  Customer parameter and fresh authority. URLs stay ephemeral, not Query cache,
  logs or browser storage. R2 failure never becomes a successful preview.

## Acceptance matrix — implementation and named regression evidence

| # | Requirement | Implementation / regression evidence |
|---|---|---|
| 1 | Real inventory | Existing Trailer endpoint + CustomerTrailerInventory; TrailerReadPostgresTests |
| 2 | Authorized Trailers only | Current designation + fresh capability, foreign UUID HTTP tests |
| 3 | Filters | Actual server filters, 106-Trailer keyset test and Web filter tests |
| 4 | Current Location | AT_LOCATION-only current label and authorized Location reference |
| 5 | Last Known separate | Historical label in inventory/detail, Web state tests |
| 6 | Unknown respected | LOCATION_UNKNOWN/IN_TRANSIT never present historical Site as current |
| 7 | Authorized timeline | Current designation period, occurredAt and authorized cursor anchor |
| 8 | Historical Customer privacy | Transfer/late-offline/shared-Trip regressions |
| 9 | No internal identities | Customer Driver/Tractor/coupling/note redaction in HTTP and strict Web schemas |
| 10 | Shared Manifest privacy | Explicit selected Customer allocations even with additive roles |
| 11 | Scoped deliveries | SQL actual Order/Trip/Stop associations; CustomerOperationalReadsPostgresTests |
| 12 | Multiple Stops/Trips | Composite Order/Stop route, keyset and multi-Trip tests |
| 13 | Actual progress | Persisted milestones; Stop and Order states remain separate |
| 14 | Valid POD/BOL | V29 exact receipt references; existing EvidencePostgresTests exercise real writer |
| 15 | Documents list | Bounded published/accepted association index, strict filters |
| 16 | Authorized files only | No pending, unrelated upload or textual BOL reference accepted |
| 17 | Multi-link protection | Single-Customer all-links tests including shared Stop and internal Trip attachment |
| 18 | Server-signed URL | Existing download route + actual signed test peer byte retrieval |
| 19 | Ephemeral URLs | Component-only grant, abort/expiry/cancel/logout/Customer/Tenant switch tests |
| 20 | R2 unavailable | CustomerStorageDisabledPostgresTests uses real disabled production adapter |
| 21 | Navigation | Actual Order/Delivery/Trailer/file routes and accessible semantic links |
| 22 | Truthful dashboard | Five-row previews, no page-length totals or broad resource directory |
| 23 | Customer isolation | Negative SQL/HTTP/Web, wrong-Customer HTTP200 rejected |
| 24 | Tenant/RLS | Existing runtime role, no new grants/policies; RLS/Modulith regressions |
| 25 | Revocation | Fresh ACTIVE Tenant/membership/role on Customer reads and download |
| 26 | OpenAPI | Exact implemented endpoints, nullable states and six new contract tests |
| 27 | Functional tests | Focused Backend/Web/OpenAPI logs retained, no disabled cases |
| 28 | Historical regressions | Full suites required on frozen published candidate; final results in PR/evidence |
| 29 | Local final evidence | Exact SHA/counts/raw logs/hashes recorded externally and in PR to avoid self-referential SHA edits |
| 30 | Honest hosted CI | Billing owner-deferred, never infer green |
| 31 | One Draft PR, unmerged | [PR #114](https://github.com/Dagt/TrailerSaas/pull/114) |
| 32 | #61–#65 deferred | No unrelated implementation |

## Implemented architecture and HTTP surface

React routes remain in the existing Customer shell: `/customer/trailers`,
`/customer/trailers/:trailerId`, `/customer/deliveries`,
`/customer/deliveries/:orderId/:stopId`, `/customer/documents` and
`/customer/documents/:documentId`. `CustomerReadViews` exports the eight inventory,
detail/history, delivery, document and dashboard components. Shared semantic
tables, captions, focus, error/loading/empty states and responsive overflow are
reused; no UI dependency or alternate auth context. Order detail links to real
mapped Delivery Stops and designated planned Trailers. No physical controls.

`readQueries` uses the existing authenticated ApiClient/React Query and
`customerKey(uid,userId,tenantId,customerId,resource,filters)`. Owner validation
comes from current server assignments; it is navigation information, never API
authority. Strict response schemas reject internal identities, foreign Customers,
wrong resource IDs, repeated cursors/rows, inconsistent accepted receipt data and
empty continuation pages. Scope changes cancel queries, remove private cache and
unmount the old workspace, including downloads. No persistent browser cache.

Backend adds only three read paths under `/tenants/{tenant}/customers/{customer}`:
`GET /deliveries`, `GET /deliveries/{order}/{stop}`, `GET /documents`.
`CustomerOperationalReads` is a read-only repeatable-read cross-domain SQL model,
using the existing public identity capability port and runtime RLS. No foreign
module repository imports, new service, event model or business writes on reads.
Current ACTIVE Tenant/membership/Customer and the specific existing scoped/all
read capability are checked. Multiple allowed Customers or an Admin role never
widen an explicitly selected Customer. Generic invisible-resource responses do
not expose foreign names/IDs.

Deliveries are ordered by `(orderId,stopId)` ascending, limit20/max50. Documents
are file/Order/Stop associations ordered uploadedAt descending, then file/Order/
Stop UUIDs ascending, limit20/max50. A file can have multiple authorized
associations, so page length is not a file total. Cursor context binds current
user/Tenant/Customer/resource/filters; cursor is only a position. Documents also
revalidate the authoritative anchor. Each delivery page uses one bounded bulk
document query over exact composite pairs (at most two accepted files per row),
not one HTTP/SQL lookup per row. No live Driver/Tractor or shared Trip directory.

Existing Trailer reads add optional limit/cursor (default50/max100 when paging),
UUID ascending. Legacy calls without paging retain their existing unpaged order;
the portal always requests a bound. List `designatedCustomerId`, and detail/
timeline `customerId`, narrow the projection. Unknown filters fail closed.
Customer timelines require current designation and physical occurredAt within
that period, excluding older late-offline facts and any Trip with foreign mapped
Orders. Authorized previous/new Location labels are fetched in one batch. Null,
hidden and last-known Locations never become a fabricated current position.

## Evidence, V29 and private downloads

V29 adds nullable accepted BOL/photo references and three indexes only. Composite
Tenant FKs and mode/pair checks preserve the existing append-only receipt and
RLS/grants. V1–V28 remain byte-identical. A real V28→V29 test preserves checksums,
ambiguous old links and receipt values. Old NULL file references stay NULL.
The existing `recordDelivery` writes validated IDs within the same Stop/
fulfillment/receipt transaction; signed BOL and no-receiver rules are unchanged.

Only exact accepted published files appear. All links must reference existing
TRIP_STOP work serving exclusively the selected Customer, with <=50 links. A
shared Stop/file or unrelated internal attachment withholds the file even for
multi-Customer/Admin users in selected mode. Generic POD uploads have no current
accepted writer: the filter supports the catalog but does not invent acceptance.
Legacy receipts can show real acceptance without a downloadable file identity.

Existing metadata/download endpoints accept optional `customerId` and recheck
these same rules plus existing linked-resource authorization. Ordinary internal
transport remains compatible. React requests a URL only after explicit action;
no auto-open, mutation cache, persistence, logging or URL signing in the browser.
The returned grant stays in component memory, requires HTTPS (development
loopback excepted), a future expiry <=300 seconds, and no embedded credentials.
Expiry, click-time check, discard and unmount remove the link; aborted or late
responses cannot populate the new owner. Already-issued bearer URLs can remain
usable until their real expiry. Client clock skew fails closed rather than
claiming instant revocation. Existing private storage validates bytes/checksum.
R2 disabled/unavailable returns safe503 for download while reads still work.

## Security audit and demonstration prerequisites

No new roles, capability grants, RLS bypass, Customer uploads, physical commands,
GPS, counters, routes between movements or hidden-directory requests. Customer
history does not authorize historical other-Customer work. Shared Manifest
previews include selected allocations only. No accepted evidence is inferred
from upload status alone; receipt and actual mappings are mandatory. Customer
HTTP200 payloads with another Customer, internal identities or contradictory
receipts are rejected before display. Read handlers are mutation-free.

A demonstration needs a real Firebase/internal user, ACTIVE Tenant/membership,
Customer Dispatcher assignment, Customer-designated Trailer and actual mapped
Order/Delivery. Accepted-file demonstration requires a real finalized BOL (and
photo for no-receiver) accepted by Driver execution **after V29** and configured
private R2. No seed data or operational data is created by the portal. Historical
acceptance without retained file IDs is intentionally unavailable for download.
Empty states and R2-unavailable behavior are usable without those prerequisites.
Desktop/tablet layout uses existing responsive styles; tests use jsdom, not
physical browsers/devices or a production Firebase/R2 deployment. Native/mobile,
production provider configuration and physical release QA remain under #65.

## Tests and final evidence

Named new coverage: `CustomerOperationalReadsPostgresTests`,
`CustomerEvidenceUpgradePostgresTests`, `CustomerStorageDisabledPostgresTests`,
extended `TrailerReadPostgresTests`/`EvidencePostgresTests`, `CustomerReads.test.tsx`
and `customer-reads.test.mjs`. Existing session/Orders/Trailer/Evidence/HTTP cases
remain. Migration inventories append V29 rather than dropping old entries;
positive operational Customer fixtures now explicitly create ACTIVE Tenants.

Focused Web: 134 passed (38 new +96 existing), typecheck/lint passed. OpenAPI:
65 passed (six new +59 historical), validation passed. These are checkpoint
results only. Final published-SHA raw logs, runtime versions, Surefire counts,
archive/log SHA-256 and full-suite results are attached to PR114's acceptance
record and external evidence archive. No final result is inferred from a focused
run or earlier SHA. If the candidate changes, all full local gates must rerun.

Backend checkpoint B–D/F: existing Trailer reads now support opt-in UUID keysets,
explicit selected Customer and redacted identities. Customer history is restricted
to the current designation period and non-shared work. New read-only Customer
Delivery/Document projections join actual persisted associations and enforce the
existing all-linked-resource rule for the selected Customer. Existing metadata and
download accept optional `customerId`; ordinary internal transport stays intact.
V29 adds two nullable historical file references to the existing receipt and three
read indexes, with mode/pair and composite Tenant FK checks; no new table or grants.
The real evidence writer now retains both validated file IDs in its current joint
transaction. Legacy ambiguous receipts keep NULL file references.

Focused Backend run: 213 passed, 0 failures/errors/skipped, Java25/PostgreSQL18.6.
Includes 32 new Customer read cases, one actual V28→V29 upgrade case, seven new
Trailer cases, all existing Evidence/Documents/Trailer read tests and Modulith.
The initial 204-case run exposed two obsolete/incorrect test expectations:
Customer Driver-name disclosure and the new test's denied-code expectation. The
historical parameterized test now preserves Admin/Operations name assertions and
requires Customer redaction; no case or assertion is disabled. Failed logs remain
outside the checkout. These focused results do not replace final-candidate checks.

Expanded Backend checkpoint: 332 passed, zero failures/errors/skipped, including
actual disabled R2 HTTP behavior, eight new Trailer cases, legacy Trailer HTTP/
administrative reads and strict Tenant migration inventory. A subsequent
33-case Customer read run also passed after adding a 75-delivery two-page
regression. These overlapping runs are not added together as a suite total.

Focused tests during implementation; freeze/publish the final candidate, then run
complete local Backend/Web/OpenAPI/Mobile/CI policy suites once. Any corrective
commit invalidates prior final-SHA evidence. Record actual counts/logs in the PR.
Billing remains owner-deferred: do not retry hosted full CI, change workflows or
claim skipped checks passed. Keep Draft if unavailable. Physical/deployed browser,
Firebase/R2 provider and Android/iOS QA are not claimed; #65 stays separate.
