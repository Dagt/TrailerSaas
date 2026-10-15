# #59 — Customer Order and delivery workflows

## A — Verified baseline and contract audit

Base: `6c3bbb092a161cc97eecb02eec35c7344596c9c7` (merge #112).
#31/#33/#35/#36/#58 closed; #59 open. Working tree clean, no stashes.
Branch: `issue-59-customer-orders-workflows`. NO MERGE.

Reuse Firebase, the #58 validated owner/Tenant/Customer context, query keys and
HTTP cancellation, Orders and existing transactional Trip commands. No #60/#61,
TRIP_VIEW_ALL grant, alternate identity, queue or transport state machine.

Confirmed gaps requiring bounded implementation:

- Order detail has no execution projection; general Trips reads require
  TRIP_VIEW_ALL. Add an Order-authorized public SPI implemented by Trips, returning
  only actual mapped Stops and minimized progress/action hints. Shared work never
  exposes unrelated Orders, Drivers, Tractors or the rest of the route.
- PATCH currently accepts reference fields only. Extend cargo/requested-destination
  replacement only for OPEN Orders with no Manifest or Trip allocation/history
  referencing their children. Lock the Order before checking, validate master data,
  preserve the previous request in specific immutable revision history. Planned
  itinerary changes remain rejected rather than reinterpreting existing planning.
- Existing Customer grants/selection are navigation hints, not current authority.
  Every new API validates persisted access and active Tenant. No broad Trip read.

## Acceptance / implementation evidence

| Checkpoint | Evidence | State |
|---|---|---|
| A | Main/dependencies/contracts, actual API and gaps | Audited |
| B–H | Real scoped reads/commands, bounded request revision, Web workflows | Implemented; focused audit continues |
| I | Runtime PostgreSQL RLS, Customer isolation, concurrency, lifecycle | Pending |
| J | OpenAPI, documentation and regression matrix | Pending |
| K | Full local suites on published candidate; remote CI status | Pending |

GitHub Actions Billing is explicitly unresolved by the owner. Keep Draft while
blocked, run available full local suites once at the final candidate, record
commands/SHA/logs/counts and never report remote Full CI green without execution.
No workflow/protection/test bypass or merge exception is authorized.

Focused evidence so far (not final full-suite evidence): Backend initial workflow,
Order mutations, service and Modulith 69 passed; expanded PostgreSQL run 306/310
passed and exposed four legacy Customer cases seeded with CANDIDATE Tenants.
Operating fixtures now explicitly use ACTIVE, preserving the negative access
tests; all four regressions plus 11 new workflow cases passed (15/15). Existing
RLS inventory remains strict and includes V28's new append-only revision table.
Web Customer workflows and inherited shell: 83 passed; no shell isolation case
removed. Orders' former placeholder assertions now require explicit Customer
queries, while #60 catalogs remain placeholders.

V28 grants child DELETE only for unallocated requests, with a PostgreSQL guard
against committed planning/fulfillment. Replacements preserve original/replacement
facts, actor, server time and Order version in order_request_revisions under RLS.
Reference-only PATCH behavior remains unchanged. No V1–V27 migration is edited.
