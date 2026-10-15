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
will request details of explicit assigned IDs, not a Tenant-wide directory. This
also avoids downloading unrelated options for multi-role Admin/Customer accounts.

Two gaps require narrow regressions: scoped Customer reads do not currently check
Tenant ACTIVE, and a cancelled late HTTP 401 can expire a later account. /me needs
an additive tenantStatus hint so the portal can exclude inactive Tenant contexts
(including dual-role users) without changing Tenant capabilities. It remains a
hint, not access authority. Preserve administrative bootstrap behavior; scoped
Customer reads must check the real Tenant state. No new endpoint or role grant.
The current Firebase adapter uses SDK default persistence; configure official
in-memory persistence before auth actions so tokens are not stored in browser
localStorage. Existing authentication is retained, not replaced.

## Acceptance matrix

| Checkpoint | Required evidence | Status |
|---|---|---|
| A | Verified base, dependencies, contracts, real APIs and gaps | Audited |
| B/C | Firebase, ACTIVE membership/Tenant, explicit Customer roles, validated selection | Pending |
| D/F | Shared responsive layout, five protected routes, honest dashboard/placeholders | Pending |
| E | Owner/Tenant/Customer keys, signals, scope revalidation and cache disposal | Pending |
| G/H | Cross-scope/revocation/late response/error tests; real PG18.6; regressions | Pending |
| I | Configuration, limits, current contracts and test evidence | Pending |
| J | Clean final published HEAD; six green current-integrable-HEAD CI jobs; Ready/unmerged | Pending |

Accepted FR-001–006/BR-001–002 define access boundaries. FR-184–197 referenced by
#58 preserve designation/lifecycle/cancellation contracts; this shell executes no
such commercial/physical commands. OQ-059 and human Alert writes stay deferred.
