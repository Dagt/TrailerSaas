# #62 — Admin Audit and operational history

## A — Verified baseline and implementation boundaries

Verified main: `8a 8b 50532e 5216b 973e 1f 476a 24aca 821b 6ebf 06` (merged PR115).
Issues 37/41/61 are closed and 62 is open. No prior PR for this branch exists.
The original checkout and its untracked `demo-assets/` remain untouched; this
issue uses a separate worktree and `issue-62-admin-audit-operational-history-views`.

Audit already has a closed 24-action catalog, minimized snapshots, historical
actor IDs, mandatory AUDIT_VIEW, runtime RLS and descending keyset pagination.
This issue consumes those existing read ports; it does not change AuditWriter,
grants, migrations 1–30, operational state machines or mobile/Drift 9.

Trip timeline currently returns all assignment/operational events, ascending,
without a cursor. The Trip detail also embeds these full histories. The bounded
Web path will opt into SQL keyset pages and omit embedded histories; legacy
clients retain their original default responses. Equal timestamps require a
stable tie across both event tables. Each page reauthorizes TRIP_VIEW_ALL.

Trailer internal reads already have paginated enriched movement history.
Customer history uses a separate minimized projection: retain that boundary.
Expected Trailer assignment is planning, never evidence of physical coupling.

## Web architecture and contracts

The existing React 19/Firebase/ApiClient/TanStack Query application hosts
`/admin/audit` and `/admin/audit/:auditEventId` inside AdminLayout. Both navigation
and direct routes require the current membership's AUDIT_VIEW capability. The
Backend remains authoritative on every read; no role grant, second API, writer
change or browser mutation was introduced. Operations and Customer have no Audit
navigation or improvised Customer Audit subset.

Admin now reuses the existing validated `/me` query, rechecking on focus,
reconnect and every 60 seconds while foregrounded. An inactive Tenant, suspended/
disabled membership or removed capability removes protected content. A403 Audit
read triggers identity revalidation. Authentication/expired-session behavior stays
with the shared Firebase boundary; neither snapshots nor credentials are logged
or stored in browser storage.

The closed Zod schemas follow the 24 actions,10 entity types and 11 per-action
snapshot projections from #61. Unknown fields, mismatched action/entity, invalid
actor/source combinations, UUIDs, timestamps, wrong detail IDs and snapshots over
64 KiB reject safely. Zod diagnostics containing submitted values are replaced by
a generic ApiProblem before reaching UI/error tooling. Existing Backend RLS and
AUDIT_VIEW rules are unchanged. Actor labels use actual historical User and
membership UUIDs; no current directory lookup impersonates a historical name.
SYSTEM/INTEGRATION retain their null human references and actual source.

## Audit list, filters and pagination

The semantic responsive table displays local time (canonical timestamp in the
`time` attribute/title), historical actor/type, action, entity reference, source,
a field summary and detail link. There are no invented totals or directory/name
filters. Empty, loading, denied, unavailable and unexpected-response states are
explicit and safe; no protected previous results remain visible after failure.

The six URL-backed filters are exactly actorUserId, actionCode, entityType,
entityId, from and to. The two catalog selectors use the real API enums; an
OpenAPI regression checks parity. Dates require an explicit RFC3339 offset or Z,
retain their original precision/offset and are inclusive. Nanosecond boundaries
are compared as integer epoch nanoseconds, avoiding Date millisecond rounding. A textual timestamp
input deliberately avoids silently interpreting an ambiguous datetime-local value
or a daylight-saving overlap. Reversed ranges, duplicate/unknown URL parameters,
invalid UUIDs/catalogs and timezone-free dates are rejected before HTTP.

The list requests 25 rows and follows the actual Backend nextCursor. Infinite
query page parameters retain the requested cursor and ancestor identities, so
repeated cursors (including cycles), repeated event IDs, malformed pages and
empty pages advertising continuation fail closed. Applying filters creates a new
first-page query; an invalid cursor can explicitly reset to the initial page.
No full-history download, in-memory pagination or page-length total is used.

## Detail and deterministic comparison

Detail displays the real actor, source, action/entity, exact/local occurrence
instant, reason or its recorded absence and the event UUID. View entity history
links to the same authorized Audit list with entityType/entityId filters; it does
not invent a resource route or grant new access. Back navigation preserves filters.

The field table sorts paths deterministically and recursively compares permitted
objects (including business address). Arrays retain their recorded order and
objects within them serialize with stable key order. Booleans/numbers/UUIDs/time
strings retain their values. The field-absent sentinel differs from explicit
null and from the string `"null"`. Added, Removed, Changed and Unchanged are
separate labels. Creation has no prior snapshot. `redactedChanges` creates a
Withheld row reading “Sensitive field changed — value withheld”, even when a
presence flag remained true. Sensitive values are never reconstructed.
React text rendering escapes historical strings; no HTML injection/Undo/restore/
edit/delete/replay action exists.

## Cache and lifecycle boundary

Audit keys are `audit / Firebase UID / internal User / Tenant / resource /
filters-or-event`, with cursor chains in the infinite query's page parameters.
The cache is memory-only, gcTime 0, with AbortSignal on every read. Tenant switches
cancel and remove all prior Audit queries, reset the route and remount the scoped
workspace. Logout/identity change uses the existing shared cancellation and cache
clear. Revocation unmounts protected queries; late responses cannot populate the
new namespace. Route unmount removes inactive Audit data rather than retaining
snapshots indefinitely. No localStorage/sessionStorage persistence or analytics
capture of snapshots exists. Browser navigation is only a hint; every actual API
request rechecks server authority.

## Compatible Trip timeline extension

Existing `GET /trips/{tripId}/timeline` without query parameters retains exactly
its legacy `{items}` response, ascending occurrence/UUID order. Supplying limit
or cursor opts into `{items,nextCursor}` with default 50/max 100. Each page requires
fresh TRIP_VIEW_ALL and an available Trip under the existing runtime Tenant RLS.
Unknown/duplicate query parameters reject; a cursor never grants resource access.
The unchanged ReadCursor binds User, Tenant, Trip and ordering to two UUID
positions: event identity and a fixed event-table discriminator. The anchor's
actual occurrence time is read from its authorized immutable row.

SQL independently seeks and limits the assignment and operational tables to
limit+1, then UNION ALL/orders/limits their bounded candidates. The order is
`occurred_at ASC,id ASC,kind ASC`, assignment before operational if both timestamp
and UUID coincide. At most two batch entity reads obtain the selected payloads;
there is no per-row query or fetching full history to paginate in Java. Existing
`trip_assignments_chronological` and `trip_operations_chronological` indexes
cover both branches; PostgreSQL EXPLAIN tests establish usable ordered paths.
No schema migration is needed; V1–V30 remain byte-for-byte unchanged.

`GET /trips/{tripId}?includeHistory=false` returns current state/Stops with empty
embedded assignmentHistory/operationalHistory. The default true preserves legacy
clients and command receipts. The new Web path explicitly omits embedded history
and uses one paginated merged timeline, removing the duplicate assignment panel.
A current Stop execution summary displays only actual authorized arrival/service/
completion/departure timestamps. It does not synthesize physical events into
the assignment/operational union. Oldest-first labels, previous/replacement
assignments, frozen reroute destinations, cancellation/substitution reasons and
actual actor IDs are shown. No inferred source/actor is invented when absent.

Keyset continuation is not a frozen export: late-recorded older physical facts
may appear when refreshing a new first page, not retroactively in an already-read
prefix. No timeline means a reconstructed route. Expected Trailer remains
planning, distinct from physical coupling. No state transitions or mutation
permissions were modified.

## Trailer history and Customer privacy

The existing internal TrailerReadView/TrailerTimeline is reused. It retains
newest physical-observation-first order, separate recorded time, locations,
load/serviceability/location-state changes, authorized reasons, Load more and
related real Trip links. Actual actor type/source are now readable; a denied/
unavailable next page hides previous timeline rows, while transient 500 preserves
loaded facts and read retry. Query identity now includes Firebase UID as well as
Tenant/Trailer/namespace. Current, Last Known, LOCATION_UNKNOWN and IN_TRANSIT
remain distinct.

Customer inventory/history still uses #60's separate strict Customer projection,
context keys and current-designation-period rules. No internal actor/Driver/
Tractor/reason fields, shared Trip route, generic Audit or Tenant-wide cache is
added to it. Customer scope remains Backend-enforced; regression tests exercise
both Customer reads and portal context/authentication behavior.

## Acceptance and security evidence

| Requirement | Implementation / executable evidence |
|---|---|
| Capability navigation and direct-route denial | AuditWorkspace tests: no request without AUDIT_VIEW or eligible current Admin context |
| List/detail real contract | audit/api and closed schemas; incompatible 200/wrong UUID/catalog/snapshot tests |
| Filters/date/cursor | Exact six URL filters, offset preservation, reversed/invalid/duplicate rejection, multi-page/reset/cycle tests |
| Before/after semantics | contracts.test: deterministic nested/array diff, creation, missing/null/string-null, deleted and withheld values |
| Historical and nonhuman actors | Real historical IDs, SYSTEM/INTEGRATION tests without invented current User |
| Tenant/account/logout/revocation | Deferred network responses, aborted requests, namespace/cache removal, capability and membership changes |
| Read-only and minimized data | GET-only assertions, escaped HTML strings, no Undo/restore/writes/secret reconstruction |
| SQL-bound Trip pages | TripTimelinePostgresTests:206 events, cross-table equal timestamp/UUID, malformed/context-bound cursor, legacy response |
| Real authorization/RLS | Runtime role checks, foreign Trip 404, restricted roles 403, revoked page 403, missing/other Tenant context |
| No N+1 / index suitability | Fixed query counts for 2 versus 50 events, history omission saves two queries, EXPLAIN existing index paths |
| Existing specialized history | Trip/Trailer regression suites, unchanged state machines and AuditWriter; no physical Audit duplication |
| Customer-safe reuse | Existing CustomerReads/CustomerPortal and Backend TrailerRead/Audit authorization regressions |
| Responsive and keyboard semantics | Existing layout breakpoints, semantic headings/table/time/labels; Tab navigation regression |
| API compatibility | OpenAPI operational-history tests retain legacy schema and document opt-in parameters; Audit catalog parity |
| Final full validation | Exact published-HEAD source archive, complete logs/XML/counts and SHA256 manifest linked from PR116 |

Focused implementation evidence (not final-SHA approval):67 initial Audit Web
tests; expanded 364 Web tests across 17 affected files, clean typecheck/lint;
14 affected OpenAPI tests plus validation;23 initial PostgreSQL/architecture/unit
tests. The corrected complete TripTimelinePostgresTests class passed 19 tests,
including fixed query-count assertions. An expanded 464-test Backend run had one
new-fixture UUID collision (463 passed); method-scoped UUIDs corrected the test
isolation without changing assertions or production behavior. Its failure log
and 19-test passing rerun are retained. Two intermediate existing Web navigation
assertions caught duplicated explanatory copy; the copy was fixed and the full
169-test affected UI subset passed, followed by 75 Audit tests including exact
nanosecond bounds. Broader Backend and final full-source results are recorded in the PR and
external evidence archive, preserving intermediate failures and corrections.

## Scope, diagnostics and final release boundary

No 63–65 implementation, retention, new permissions, Customer Audit, live GPS,
invented data or generic Undo. No Mobile/Drift changes. No production or physical-
device QA is claimed. Automated Web coverage uses jsdom, not a deployed browser
or physical desktop/tablet accessibility certification. No fake screenshots are
provided; real authenticated browser/release verification remains pending.

Hosted Actions billing remains an external limitation: blocked remote runs are
NO VERIFICADO. PR116 remains Draft and unmerged. The exact frozen HEAD, complete
local results, evidence SHA256 and actual hosted-run URL/status belong in the PR
body and external evidence manifest, avoiding a self-referential source-SHA doc
commit. Prior Backend full validation took 29m 17s, exceeding the current remote
20-minute timeout; this risk is recorded without changing workflow limits,
branch protection or weakening checks. OQ057/059 and future production issues
remain unchanged.
