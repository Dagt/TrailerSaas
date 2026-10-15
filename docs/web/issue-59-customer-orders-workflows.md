# #59 — Customer Order and delivery workflows

## Verified baseline and scope

Base: `6c3bbb092a161cc97eecb02eec35c7344596c9c7` (merge #112).
#31/#33/#35/#36/#58 were closed; #59 was open. Initial checkout was clean with
no stashes. Branch: `issue-59-customer-orders-workflows`.
[Draft PR #113](https://github.com/Dagt/TrailerSaas/pull/113). NO MERGE.

Implements FR-030–045 / FR-140–155 within the accepted Orders/Trips contracts.
Firebase, the #58 owner/Tenant/Customer context, shared HTTP/UI, React Query and
existing transport state machines are reused. No second application, identity,
queue, business approval, physical state editor or TRIP_VIEW_ALL grant.
#60 catalogs/evidence and the full #61 Audit surface are deferred.

## Architecture and query boundaries

`OrdersList` supplies actual status and unplanned filters, preserves them through
keyset pages and forces the active Customer filter. Server authorization is fresh
on every request. No appointment-range or fictitious active filter is simulated.
Rows link to actual work rather than inventing planning facts from a partial page.
Dashboard cards are bounded five-row previews, explicitly not total counts:
OPEN, PLANNED, IN_PROGRESS, PARTIALLY_DELIVERED, DELIVERED, CANCELLING and recent
requests by creation time. No Dispatch Board or Tenant-wide resources are fetched.

`CreateOrder`, `OrderEditors`, `OrderDetail` and `OrderActions` use typed contracts
in `orderContracts.ts`, owner-scoped queries in `orderQueries.ts` and the existing
API client. New reads, under `/api/v1/tenants/{tenantId}`:

- GET `/orders/customer-context?customerId=...`: active Tenant/membership and fresh
  effective capabilities for the Customer; rendering hints, never authorization.
- GET `/orders/{orderId}/progress?cursor=...&limit=20`: Order-authorized minimal
  execution projection. UUID-ascending Trip keyset, limit 1–50, opaque to authority.
  Repeatable-read keeps each page internally coherent. The Web requires all loaded
  pages to match the current Order version before operational actions.
- Existing `/sites` and `/sites/{siteId}/locations` accept optional explicit
  `customerId`. Fresh SITE_VIEW, active Tenant, real Customer-Site association and
  Customer-specific Locations apply even to multi-role callers. Unfiltered reads
  retain existing semantics; no new directory endpoint. Blank filters are invalid.

Orders owns public `CustomerOrderProgress`; Trips implements it without importing
another module's internal packages. Only actual `trip_stop_orders` relationships
select Trips/Stops. No other Order IDs, Customer IDs, Driver/Tractor IDs, full route,
private assignment history or raw metadata is returned. Expected Trailer number
requires existing scoped Trailer visibility. Per Trip: at most 500 relevant Stops;
overflow fails closed, not silent truncation. At most 100 own reroutes and 100
substitutions are returned. History is withheld when work spans other Customers,
even if an actor happens to have access to both. No general Trip read grant.

Action hints require the capability for **all** Customers mapped to the Trip.
The existing command independently locks and reauthorizes. Customer command
receipts retain the existing DTO shape but redact Driver/Tractor and return empty
Stop/assignment/operational-history arrays. Tenant-wide receipts remain unchanged.
The portal refreshes its minimized projection after the validated ACK. Normalized requested Site/Location names are resolved through existing Customer-filtered reads and identified as current labels; frozen snapshots remain separate. Creation review includes the selected planned Trailer number.

## Creation, cargo and destinations

Information → Cargo → Requested Stops → Review → explicit confirmation uses the
existing POST `/orders`. Order number is required; PO/shipment/BOL and cargo values
retain existing optional semantics. Multiple cargo lines and requested Stops can
be added, removed and reordered, with unique positive sequence numbers. Appointment
values require explicit timezone. No unit conversions or new closed cargo catalog.

A destination uses an authorized Site/Location or the accepted frozen ad-hoc Site
snapshot. Text does not grant authorization or create master data. Requested Stops
are commercial requests, not invented executable Trip Stops. The server persists
the actual Order/cargo/Stops, returns OPEN/version 0 and exposes the request to the
existing Operations queue. Tests verify this through the real Dispatch Board.

Optional planned Trailer uses the existing scoped Trailer read with explicit
`designatedCustomerId`, active/IN_SERVICE eligibility and TRIP_ASSIGN_TRAILER.
Missing choices do not block an Order without a Trailer. The server revalidates
visibility and eligibility; existing Manifest planning is reused. No Hook/Drop,
physical location change or #60 inventory workspace is introduced.

## Bounded edits and V28 persistence

Existing PATCH accepts reference fields and now optional non-null `cargoLines`
and `requestedStops`, using the existing strict create validators. It still
requires actual `expectedVersion`. References remain editable OPEN/PLANNED;
content replacement requires OPEN and **no** Manifest allocation (including CLOSED
history), Trip mapping or fulfillment event. Planned itinerary rewrites are rejected
with INVALID_STATE_TRANSITION rather than guessing dispatch semantics.

The parent Order is exclusively locked before version/commitment checks; Sites are
validated under existing normalized-destination locks in deterministic order.
Only provided child arrays are replaced, in the same transaction as the Order
version and history. Existing children are preserved if omitted. Customer, Order
number and planned equipment are not PATCH fields. Competing versions yield one
winner and STALE_VERSION; any persistence failure rolls back the whole change.

V28 (`V28__unallocated_order_request_revisions.sql`) adds only
`order_request_revisions`: Tenant/Order/actor, actual Order version, previous and
replacement request JSON and server recorded time. Composite Tenant/Order FK,
unique Tenant/Order/version, JSON-object/version constraints, forced Tenant RLS
excluding identity bootstrap, runtime SELECT/INSERT only. No historical migration
checksum is changed. DELETE is newly granted only on requested cargo/Stop children,
with a SECURITY INVOKER trigger that locks the parent and rejects committed work.
No UPDATE/DELETE grant on revision history, no generic ledger or Audit API.

Specific human reference/content edits, cancellation/requested cancellation and
administrative completion append before/after facts in V28 after the actual version
flush. Required history failure rolls back the business mutation. Cancellation
reason/resolution is now durable, correcting the pre-existing in-memory-event gap.
The progress projection exposes up to 20 recent minimized decisions, omitting actor
IDs and raw facts. Physical fulfillment events remain separate and unchanged.
No historical backfill or generic #61 actor/history querying is claimed. Native SQL
uses the same JPA transaction and permits historical migration upgrade fixtures to
boot before V28 exists; there is no new JPA entity falsely requiring V28 at V27.

## Cancellation, reroute, substitution and completion

- OPEN/PLANNED cancellation sends a reason and current Order version. Existing
  releasable pre-execution planning is released; historical request facts remain.
- IN_PROGRESS/PARTIALLY_DELIVERED sends reason plus an explicit existing resolution
  instruction and authorized destination where applicable. The actual response is
  CANCELLING, visible in list/dashboard/detail. It does not pretend physical
  resolution is complete and never calls whole-Trip cancellation for one Order.
- Operational destination changes use existing `/trips/{id}/reroutes`, the actual
  mapped Stop and Trip version, previous/new destination, reason, instructions and
  explicit appointment. No post-departure requested-Stop PATCH.
- Explicit AUTHORIZE/REJECT uses existing trailer-substitution-decisions with current
  `expectedTrailerId`, requested Trailer and reason. No invented expectedVersion in
  that body: its server guard is locked expected-Trailer comparison. AUTHORIZE
  changes planning expectation only; REJECT retains it and permits a visible
  inactive/OOS reported alternate per the existing contract. Both persist actual
  decisions. Neither performs coupling/Hook or mutates the Trailer physically.
- All eight Order states remain distinct. DELIVERED reflects actual operational
  obligations/evidence; POD alone does not force it. Only authorized, explicitly
  reviewed DELIVERED → COMPLETED sends current Order version to the existing
  completion endpoint. No manual Delivered shortcut or state-machine rewrite.

## Concurrency, uncertain responses and privacy

`useSubmission` prevents double send and never automatically retries a mutation.
No invented durable Idempotency-Key/replay guarantee is attached to commercial
commands. Timeout/408/5xx or malformed/contradictory successful responses are
uncertain: retain the draft, block repeat sending and reconcile via authorized
reads. Definitive validation/conflict rejection can be explicitly revised;
STALE_VERSION requires reload and renewed review. A successful HTTP status alone
is insufficient: validate IDs, Customer, actual response schema, state and version
outcome. The outgoing version is always read from the server, never guessed.

`workspaceMemory` is ephemeral per owner/Tenant/Customer/role context. Same-context
navigation/access refresh retains drafts and converts interrupted pending sends to
uncertain. Scope/account changes clear forms and sensitive queries; browser reload
does not persist drafts. Firebase remains memory-only. Aborted late ACKs cannot
navigate/populate a new owner; aborted mutation does not imply server rollback.
No credentials, tokens or sensitive Order drafts are placed in localStorage.

API reads reject foreign identities, duplicate IDs/impossible history versions and
contradictory scope choices. These browser checks supplement, never replace,
PostgreSQL authorization/RLS. The new Customer APIs require active persisted Tenant
and membership. Existing Tenant-wide Admin/Operations authority is preserved.
Effective role catalog, OQ-059 and Alerts policies remain unchanged.

## Acceptance matrix

| # | Criterion | Implementation / automated evidence |
|---|---|---|
| 1 | Only own Orders | Explicit scoped list/detail; inherited Order HTTP/RLS tests and Customer portal negative cases |
| 2 | Real operational queue | CustomerOrderWorkflowPostgresTests: actual create → Dispatch Board |
| 3 | Cargo/Stops durable | Real PostgreSQL create/replacement/history tests; multi-line Web create |
| 4 | Multiple Stops | Ordered add/remove/reorder form, persisted arrays and unique-sequence validation |
| 5 | Cannot create for another Customer | Forged Customer HTTP test, server capability target check |
| 6 | Trailer selector isolated | Explicit designatedCustomerId, scoped server read, foreign HTTP-200 rejection test |
| 7 | State-bounded edit | OPEN unallocated guard; OPEN/CLOSED Manifest and SQL delete regression; PLANNED rejects rewrite |
| 8 | Operational changes are commands | Reroute/substitution real endpoints, actual IDs/versions; no physical client writes |
| 9 | Pre-execution cancellation | Existing service regressions plus reviewed Web cancellation/ACK |
| 10 | Active cancellation visible | IN_PROGRESS/PARTIAL → CANCELLING with durable reason/resolution; dashboard/detail |
| 11 | Shared Trip privacy | Own mapped Stops only; no other Orders/Drivers/Tractors/private history; PostgreSQL assertions |
| 12 | Reroute scope | All mapped Customers checked fresh; existing TripOperationalPostgresTests and Web guard tests |
| 13 | Explicit durable substitution | Real Customer REJECT/AUTHORIZE events with current actor; Web request-body assertions |
| 14 | AUTHORIZE is not Hook | Coupling/Trailer/movement byte equality in PostgreSQL and no Hook POST |
| 15 | REJECT preserves expectation | Actual server decision regression, same expectation and durable rejection |
| 16 | Progress is operational | Actual Order/Trip/Stop mapping, milestones, no evidence-derived invented status |
| 17 | DELIVERED ≠ COMPLETED | All eight states tested; explicit administrative dialog |
| 18 | Completion authority/state | Existing 53 mutation cases; Web DELIVERED-only command and contradictory-ACK rejection |
| 19 | Concurrency | Two same-version edits produce one winner; real PostgreSQL locking/rollback tests |
| 20 | Uncertain results | Lost committed creation ACK reconciled through reads; single POST, retained uncertain draft |
| 21 | Context cleanup | Inherited 51 shell tests plus pending mutation/same-owner refresh/Customer switch fencing |
| 22 | Backend authorization | Current persisted capabilities, membership/Tenant revocation, runtime RLS |
| 23 | No internal data leak | Closed minimized progress/history, safe Customer receipts and generic errors |
| 24 | Regressions | Full local final-candidate suite evidence recorded in PR; no historical test removed |
| 25 | Available final evidence | Exact published SHA, commands, counts, log hashes and limitations in PR #113 |
| 26 | Honest remote CI | Billing owner-deferred; Draft while full remote checks remain unverified |
| 27 | #60/#61 deferred | No inventory/delivery/evidence catalog or generic Audit implementation |
| 28 | Open unmerged PR | PR #113, NO MERGE; external review required |

## Tests and execution evidence

New Backend classes: CustomerOrderWorkflowPostgresTests (16),
OrderRequestRevisionPostgresTests (7), OrderRequestUpgradePostgresTests (1).
PostgreSQL 18.6/Testcontainers, actual runtime role, two concurrent HTTP edits,
forced-RLS/pooled context, cross-Tenant FK, append-only grants, committed-child
SQL guard, induced history failure rollback, real V27→V28 physical movement/history
and all V1–V27 checksum equality. Modulith remains covered by the existing suite.

Focused final security/migration run: 24 passed, zero failures/errors/skipped.
Expanded history/Order regression: 74 passed, zero failures/errors/skipped (overlaps
the 24; do not sum these as unique coverage). Earlier Customer HTTP 56 and RLS 17
also passed. New Web workflow cases: 45; the final label/review run passed all 45. The prior 44-case run with shared HTTP: 55
passed. Typecheck and lint passed. Earlier inherited Customer/Operations/HTTP
regressions: 151 passed before the final five additions. OpenAPI: validation and
59 tests passed, including eight new #59 contracts. Focused results do not replace
the full final-candidate suites.

Initial runs exposed CANDIDATE Tenant seeds used by old positive Customer cases;
operating fixtures now explicitly ACTIVE, preserving all negative access assertions.
Historical-schema boot exposed the unnecessary new JPA entity validation; same-
transaction native revision persistence preserves upgrade support. A new contract
test caught an unquoted YAML `#59` truncating a description; the description was
fixed rather than weakening the test. None of these failures is hidden as green.

Final local validation uses a Git archive of the clean published candidate for
Node/Flutter/Java 25 `mvnw clean verify`. A Windows full attempt hit a native socket BindException during an existing Road test fixture; the affected test and full Backend are revalidated in Linux with the same PostgreSQL 18.6 runtime-role tests, without weakening assertions or changing host networking.
Node 24 runs locked npm ci, Web typecheck/lint/full tests/build, OpenAPI validate/
full tests and dependency-free CI policy tests. Flutter 3.47.6/Dart 3.13.5 runs
locked pub get, reproducible Drift generation, format, fatal-info analysis and
all tests; Drift remains v9 with no Mobile changes. Final counts, candidate SHA,
commands and per-log SHA-256 are published in PR #113 and local evidence files.
The full attempt also exposed one historical Authorization inventory still expecting 52 tables. It now requires exactly 53 and explicitly verifies the new table's forced RLS and SELECT/INSERT-only runtime grants; no assertion is removed. No earlier SHA's result is substituted for that execution. Git archives are created with core.autocrlf=false: an initial Windows export converted generated-source LF to CRLF; byte comparisons against the real Git blob proved that only export conversion differed. No Drift source/schema was changed.

## Release and deferred limitations

The owner explicitly deferred GitHub Actions Billing. Remote Full CI is
**NO VERIFICADO** until six actual final-HEAD checks execute successfully. PR stays
Draft; no workflow, protection, billing, test or merge exception is performed.
Branch-protection enforcement could not be verified in the previous integration;
that existing owner/plan limitation remains. Local full suites cannot certify a
GitHub-hosted merge ref. Final review must verify base/head and current checks.

No physical Android/iOS, production Firebase, deployed browser, printer or field QA
is claimed. #65 retains physical/release testing. Browser testing here is automated
jsdom, not physical tablet QA. Access/roles can change after a read; commands always
reauthorize and can safely reject. Uncertain creates require human read reconciliation;
there is no durable browser queue. Very large progress is bounded with explicit
continuation/overflow behavior, not a complete global delivery feed. Site selection
keeps the existing Customer-specific Location visibility (neutral choices are not
newly exposed). The full #60 inventory/delivery/Documents experience and generic
#61 Audit/history search remain separate work. No Alerts recipient policy changes.
