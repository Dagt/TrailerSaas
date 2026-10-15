# #58 — secure Customer portal shell

Verified main/base: `92e2a1682f0ec87e17ec478ac4ebecbee2c7fe4e`, merge #111.
#16/#24 completed; #58 open. Working tree clean, no stashes. Branch
`issue-58-customer-portal-shell`. Last integrated tree matches validated #111
(Backend 2432, Mobile 581, Web 266, OpenAPI 48); final #58 evidence is independent.
NO MERGE. No #59/#60 commercial workflows, new roles, OQ-059 changes or migrations.

## Architecture audit (A)

Reuse existing Firebase/global User, /me, React Router, QueryClient, API client and
shared UI. /me capabilities are Tenant-only; CUSTOMER_DISPATCHER CUSTOMER roles
supply navigation candidates, never server authorization. Customer detail/list
already enforce capability and persisted Customer scope on every call. The portal
requests details of explicit assigned IDs, not a Tenant-wide directory. This
also avoids downloading unrelated options for multi-role Admin/Customer accounts.

Two confirmed baseline gaps required narrow regressions: scoped Customer reads do not currently check
Tenant ACTIVE, and a cancelled late HTTP 401 can expire a later account. /me now supplies
an additive tenantActive boolean hint so the portal can exclude inactive Tenant contexts
(including dual-role users) without changing Tenant capabilities. It remains a
hint, not access authority. Preserve administrative bootstrap behavior; scoped
Customer reads must check the real Tenant state. No new endpoint or role grant.
The baseline Firebase adapter used SDK default persistence; it now selects official
in-memory persistence before auth actions so tokens are not stored in browser
localStorage. Existing authentication is retained, not replaced.

## Acceptance matrix

| Checkpoint | Required evidence | Status |
|---|---|---|
| A | Verified base, dependencies, contracts, real APIs and gaps | Audited |
| B/C | Firebase, ACTIVE membership/Tenant, explicit Customer roles, validated selection | Implemented; evidence below |
| D/F | Shared responsive layout, five protected routes, honest dashboard/placeholders | Implemented; evidence below |
| E | Owner/Tenant/Customer keys, signals, scope revalidation and cache disposal | Implemented; evidence below |
| G/H | Cross-scope/revocation/late response/error tests; real PG18.6; regressions | Implemented; evidence below |
| I | Configuration, limits, current contracts and test evidence | Implemented; evidence below |
| J | Clean final published HEAD; six green current-integrable-HEAD CI jobs; Ready/unmerged | Final run and exact SHA recorded in PR #112 |

Accepted FR-001–006/BR-001–002 define access boundaries. FR-184–197 referenced by
#58 preserve designation/lifecycle/cancellation contracts; this shell executes no
such commercial/physical commands. OQ-059 and human Alert writes stay deferred.


## Identity and server-derived context (B/C)

Firebase Authentication, AuthProvider, global internal User and persisted
TenantMembership/RoleAssignment are reused. CUSTOMER_DISPATCHER + CUSTOMER + a
valid assigned UUID, ACTIVE membership and tenantActive=true are the navigation
requirements. Tenant-only capabilities may be [] and grant no scoped permission.
A Customer assignment never becomes a Tenant-wide capability. Missing/unknown
Tenant-active hint fails closed for this portal.

The optional additive /me tenantActive boolean reports only whether the current
persisted Tenant is ACTIVE. It deliberately does not expose internal candidate/
administrative lifecycle vocabulary. The existing raw status remains internal.
Every API still freshly authorizes; browser roles, selected IDs, query keys and
this boolean are never authoritative. Customer-scoped Customer list/detail now
check the actual Tenant ACTIVE under the established runtime Tenant transaction.
Tenant-wide administrative bootstrap behavior stays unchanged, so an explicitly
separate Admin role retains its existing authority; the portal never derives that
role/capability from Customer access. No role, capability, database grant, new
endpoint or migration is added.

Tenant options come only from eligible /me assignments; their names come from
that own-User server directory. Customer names/status/versions come from the
existing GET /tenants/{tenantId}/customers/{customerId}. The portal requests only
explicit assigned IDs, in batches of at most four concurrent reads. It does not
download the Tenant-wide Customers list and filter it, including for dual-role
Admin/Customer accounts. The existing list API is independently covered by real
Backend scope tests. One eligible Tenant/Customer selects automatically; multiple
require explicit selection. Unavailable/inactive assignments do not open a scope.
All identities/required Customer fields and returned ID are validated. A partial
malformed/network-failed choice collection cannot become a valid completed view.

Selecting a Customer triggers a fresh detail read before providing its context:
Firebase UID, internal userId, actual membership, explicit tenantId checked equal to the membership Tenant,
customerId and verified Customer. The role/membership fields describe downloaded
hints; only successful fresh API reads establish that the current read succeeded.
They do not establish future business-operation permissions. No Drivers/Tractors,
assignment, inventory, Order, delivery, document or operational totals are invented.

## Routes, layout and dashboard (D/F)

Within the existing React app/router:

- /customer — verified Customer dashboard;
- /customer/orders — honest later-phase Order management;
- /customer/trailers — later-phase inventory;
- /customer/deliveries — later-phase Customer delivery view;
- /customer/documents — later-phase authorized evidence;
- unknown subroutes — protected generic not-found with a way home.

The shared application header retains the TrailerSaaS brand, skip link and area
navigation. Public area links select a sign-in page; authenticated navigation
shows only eligible downloaded role hints. Customer-only users see no Admin or
Operations links/content. Direct access to those areas still evaluates their
own existing guards, and their layouts receive no Customer context. Existing
sign-in is reused with a Customer title. No new login/identity/session service.

The portal topbar shows session information, current Tenant and scope selectors,
Refresh access and Sign out. The verified banner labels current Tenant/Customer.
The navigation/dashboard provide Pending Orders, Active Deliveries, Trailer
Inventory and Recent Evidence cards linking to the protected placeholders.
No numeric counts, fake successful actions, business lists or mutation buttons.
Shared UI/styles are reused, with a responsive single-column/tablet navigation
rule at 850px, readable labels, semantic navigation/headings, focus indicators,
keyboard-accessible links and native selects. No visual library/navigation engine.

## Query boundary, cache and revalidation (E/G)

queries.ts exposes the typed Customer read, owner validation, query options and
key factory. Business/context keys include Firebase UID, internal User UUID,
Tenant UUID, Customer UUID, resource and filters. The permitted-choice index is
separate owner/User/Tenant metadata keyed by the assigned Customer ID set; it is
not an operational multi-Customer dataset. /me remains the existing own-identity
bootstrap cache. No global protected ['orders'] key or durable browser cache.

Tenant/Customer changes validate selection against current eligible assignments,
cancel requests, remove previous Customer resource caches, remount their context
and reset routes to /customer. Tenant switch also clears the previous Customer
selection. Account change/sign-out cancels and clears shared cache; old owners'
responses never populate the new namespace. Leaving the portal removes its
private namespace. No localStorage/IndexedDB persistence for portal data/choices.

AbortSignal reaches the existing HTTP transport. The shared API client now checks
cancellation before/after token acquisition, before handling late HTTP errors and
after JSON decoding. A cancelled old 401/token failure cannot expire a new account,
even if transport ignores abort. No HTTP retry engine/worker is introduced.

/me is rechecked on route change, explicit Refresh access, focus/reconnect and a
30-second foreground healthy-session interval. Errors stop scheduled refetch;
explicit retry/focus/reconnect remains available. Current protected views are
retired during revalidation/paused reads. A local read sequence ensures a new
successful /me read revalidates Customer context even if React Query structurally
shares unchanged server JSON. This sequence is ephemeral technical metadata,
not a fabricated server ID, timestamp or permission token. Changed role sets
remount selection state, remove invalid contexts and fence prior callbacks.

Revocation is observed at these revalidation points; the browser cannot promise
instant push-based revocation while disconnected. Independently, every server
resource request rechecks actual persisted membership/capability/Customer scope.
No offline cached authorization or business operation is granted by this shell.

## Session, privacy and safe errors

The official Firebase SDK is configured with inMemoryPersistence **before**
observation/sign-in/token operations. The app adds no token/password storage;
reload/new browser context requires sign-in instead of a remembered localStorage
session. Existing shared explicit token refresh and sign-out are retained. Password
form reset behavior is unchanged. Query cache is memory-only and clears on account
change/sign-out. No Firebase claims are interpreted as business roles.

Loading, unauthenticated/expired session, suspended/disabled/no membership,
missing role, no active Customer, selection, inactive Customer, unknown response,
unavailable resource and network failure all have explicit safe states. 401 uses
the existing refresh/sign-in path. 403/404 reveal no unrelated name/existence;
409/503/malformed responses use shared safe errors. No raw exception/SQL/private
server detail is rendered, no automatic redirect loop and no blind HTTP retry.
The navigation identity query mounts only for an authenticated account, preserving
the existing zero-cache logout contract.

## Backend and security evidence (G/H)

Real runtime PostgreSQL18.6/Testcontainers tests preserve RLS and no-BYPASSRLS,
Tenant isolation, Customer list/detail scope, multi-Customer union limited to
assigned IDs, revoked roles, suspended membership, arbitrary UUID denial and
fresh permissions independent of React. New tests prove own-Tenant disabled
Customer reads reject, /me can return Customer roles with capabilities [],
tenantActive changes without leaking raw lifecycle vocabulary, internal
Drivers/Tractors/Settings/Alerts/Dispatch reads reject and known foreign versus
invented Customer IDs have the same generic denied detail. No historical SQL,
ledger, outbox, Alerts policies, Trailer projection or Drift schema changes.

New Web coverage includes 51 portal/query tests plus three cancellation regressions
and one SDK memory-persistence regression. It covers one/multiple contexts, all
five direct routes, no Admin-only access, duplicate-free scope choices, forged
option/URL/query IDs, denied APIs despite unchanged /me, inactive Customer between
choice/detail, role/membership/Tenant revocation, late Tenant/Customer/account
responses, old-owner 401, logout/unmount during reads, partial malformed responses,
explicit session recovery, safe 401/403/404/409/503, keyboard navigation and source
responsive rules. JSDOM/CSS assertions are not physical device/browser layout QA.

Focused final runs (overlapping, not summed as full-suite totals):

| Suite | Evidence |
|---|---|
| Web affected Portal/API/Firebase/routes/Admin/Operations/Alerts | 152 passed; typecheck/lint passed |
| Backend Customer HTTP, Membership directory/HTTP, Identity, Authorization, Tenant RLS, Modulith | 78 passed plus a separate 1-test administrative-bootstrap regression passed, 0 failures/errors/skipped; Java25/PostgreSQL18.6 |
| OpenAPI | Valid; 51 passed, 0 failed/skipped (48 preserved +3 contracts) |

Additional shared-client Admin/Trailer regression subset: 118 passed. These overlap the 152-test subset and are not summed.

Failed diagnostic logs are retained outside the checkout. Tests first reproduced
inactive-Tenant read and cancelled late-401 defects. Further focused regressions
found unchanged-/me structural-sharing reuse, an unauthenticated disabled-query
cache residue after logout and exposure of unapproved raw Tenant lifecycle data.
Fixes retain the original assertions: no old-owner side effects, no stale protected
view, zero private logout cache and no raw lifecycle vocabulary. Strict response
field inventories add only tenantActive; no test is removed or relaxed. Existing
Customer role test fixtures now use ACTIVE operating Tenants, while separate
inactive-Tenant denial and administrative bootstrap tests retain their distinction.

## Delivery and deferred scope (I/J)

PR: https://github.com/Dagt/TrailerSaas/pull/112. Exact final HEAD, complete
acceptance matrix, six final-HEAD job results/counts and direct workflow/job logs
are recorded in the PR after final validation. This avoids a circular repository
commit embedding its own future hash/CI result. Draft focused results are not
final approval. Full CI runs only after the finished candidate is published/Ready;
a red candidate returns to Draft, is fixed with focused tests and revalidated.

Branch protection read was rechecked during #58 and still returned 403 Resource
not accessible by integration. Preserve the documented owner setup/limitation in
ci-optimization.md; no protection/workflow was changed or falsely claimed enforced.

#59 owns real Orders/commercial operations. #60 owns inventory/delivery/evidence
views. New consumers must reuse this validated context/key/signals and implement
actual server Customer-filtered resources; the shell is not a substitute for those
API contracts. OQ-059, human Alert write grants, #61, retention, new tracking and
physical release gate #65 remain outside scope. No physical Android/iOS QA or
production Firebase sign-in/device/browser QA is claimed. PR remains unmerged.
