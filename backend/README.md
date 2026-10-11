# Backend PostgreSQL and Identity Boundary

## Orders foundation (issue #31)

V10 creates only `orders`, `order_cargo_lines` and `order_stops`; V1–V9 remain
unchanged. The initial Order aggregate is independent of Trips, Trailers and
manifests. The accepted v1.0.1 milestones exist from creation; #31 creates only
OPEN/version 0 with null milestone timestamps.

All three tables use Tenant RLS excluding identity bootstrap, non-destructive
tenant-safe parent FKs, and runtime **SELECT/INSERT only**. Runtime has no
UPDATE/DELETE, ownership or RLS bypass. Stop FKs use V5's existing
`(tenant_id, site_id, id)` Location key and preserve historical snapshots.
The full status catalog is structural preparation, not implemented commands.

`POST /api/v1/tenants/{tenantId}/orders` atomically creates the projection,
zero or more cargo lines and requested Stops. List/detail use the public identity
Order access boundary with fresh memberships/roles and Customer scope.
Normalized destination validation uses the public Customers policy; Orders never
reads Customers/Identity internal repositories. That policy holds the Site's
shared parent lock during association validation to coordinate with association
removal. Snapshots contain destination data, never authorization context.
Customers owns the public `CustomerSiteUsageGuard` SPI; Orders implements a
fresh Tenant/Customer/Site-scoped usage query invoked after the removal obtains
the exclusive Site lock. Only COMPLETED/CANCELLED Orders are terminal for this
check. Active Orders and existing active Customer-specific Locations prevent
removal with RESOURCE_CONFLICT; terminal history survives safe removal. Customers
does not depend on Orders internals, and no association FK/cascade is added.
The server principal still supplies `created_by_user_id`, but public Order DTOs
omit creator User identifiers until an authorized audit/history surface exists.

GET/list supports customerId/status and real opaque keyset cursor/limit, ordered
by creation timestamp and ID descending; default 50, maximum 200. Detail reads
children only after the scoped parent is visible. Authorization is reapplied on
each request/page. Normalized destinations require Customer↔Site compatibility
and optional neutral/same-Customer Location within the exact Site.

See [Orders API](../docs/api/orders-dispatch.md) for the minimum snapshot shape,
implemented schemas and explicit gaps: public Manifest reads/commands, Order
mutations/transitions (#33), unplanned/active/appointment filters and richer
fulfillment/history. Unsupported fields/filters fail validation. No idempotency
ledger/replay guarantee, UI, Trip, outbox or audit subsystem is introduced.

## Manifest planning (issue #32)

V11 adds only `load_manifests` and `manifest_orders`; V1–V10 are unchanged.
Both have Tenant RLS excluding bootstrap and non-destructive composite FKs.
Runtime uses SELECT/INSERT/UPDATE on Manifests (no DELETE), and
SELECT/INSERT/DELETE on allocations (no UPDATE). Orders still have no Trailer FK.

Internal Orders services open Manifests with optional Trailers, add/remove
allocations for OPEN Orders, and explicitly close OPEN -> CLOSED. CLOSED history
cannot be changed through these services. Existing commands require expectedVersion;
the locked parent and @Version protect concurrent child changes. Every mutation
advances updatedAt/version; explicit close sets closedAt. There is no Manifest
REST CRUD, automatic closure, partial unload/returns/remainder handling or Trip.

Order POST now accepts optional plannedTrailerId. Fresh ORDER_CREATE plus
TRIP_ASSIGN_TRAILER/customer scope and the public `TrailerPlanningPolicy`
validate a visible, same-Tenant active IN_SERVICE Trailer. A shared Trailer lock
holds eligibility stable through commit. Order, cargo, Stops, OPEN/version-0
Manifest and one all-null-amount allocation commit atomically. Order remains OPEN;
no Trailer state is mutated. Omission/null creates no Manifest. Allocation commands
return only revision metadata to avoid exposing unrelated Customers in a shared
Manifest. Closing is Tenant-wide only. No new capabilities or dependencies are added.

See [Orders API](../docs/api/orders-dispatch.md) for scope, lifecycle and deferments.

Issue #19 adds migration-first PostgreSQL persistence to the existing Spring
Boot application (ADR-003/006/013). Use JDK 25 and the Maven Wrapper.

## Configuration

Issue #20 uses Spring profiles `local` (safe default), `staging` and `production`.
Use `SPRING_PROFILES_ACTIVE` to explicitly select one non-local profile; unknown,
mixed profiles and non-local default profiles are rejected before opening a DB.
Local HTTP binds to loopback and both local JDBC URLs must use loopback hosts.
Required/invalid DB configuration fails startup with variable names, not values.
Issue #22 requires FIREBASE_PROJECT_ID and official Google Application Default
Credentials for backend Firebase Admin. R2/Sentry backend integrations remain reserved.

[`backend/.env.example`](.env.example) lists safe placeholders only. Spring does
not load it automatically; supply variables to the process using secure OS/IDE
configuration. Never source the example as a script or commit populated copies.
See the [central environment catalog](../docs/development/environment-configuration.md)
for selection commands, secret classifications, reserved integrations and isolation.

Supply these variables to the backend process in every profile, including local:

| Variable | Purpose |
| --- | --- |
| `DB_RUNTIME_JDBC_URL` | Runtime PostgreSQL JDBC URL, for example `jdbc:postgresql://127.0.0.1:5432/trailersaas` |
| `DB_RUNTIME_USERNAME` | Application database role |
| `DB_RUNTIME_PASSWORD` | Application role password |
| `DB_MIGRATION_USERNAME` | Separate migration/admin role used by Flyway |
| `DB_MIGRATION_PASSWORD` | Migration role password |
| `DB_MIGRATION_JDBC_URL` | Optional Flyway URL; defaults to `DB_RUNTIME_JDBC_URL` |
| `FIREBASE_PROJECT_ID` | Expected Firebase ID-token project; required in every profile |
| `GOOGLE_APPLICATION_CREDENTIALS` | Protected non-production credential JSON path for local ADC; optional with official ambient ADC |

No password or username defaults exist. Do not put secrets in source control,
JDBC URL query strings or command-line `-D` arguments. Missing configuration
must fail startup rather than selecting an embedded database.

Flyway has its own datasource credentials; it does not borrow runtime credentials.
Use the same database/schema for both connections. The separate URL supports
different connectivity options to that same database, not unrelated databases.
The runtime role must not be superuser or have BYPASSRLS, own future domain
tables, or have schema CREATE privileges. The migrator can own migrated objects.
Production roles remain operator-provisioned. V2 grants only SELECT/INSERT on users
to the configured runtime role. V3 grants SELECT only on tenants, tenant_memberships
and role_assignments. V4 adds RLS without changing these grants. V6 adds only
onboarding INSERT on memberships/roles; UPDATE/DELETE remain unavailable.

## Local PostgreSQL

An existing local PostgreSQL 18 instance can be used directly. Alternatively,
run the official image using Docker Desktop's Linux engine or Docker Engine.
The following is a development-only example, not deployment infrastructure.

PowerShell example (passwords are prompted, never stored in the example):

```powershell
$env:DB_RUNTIME_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5432/trailersaas'
$env:DB_RUNTIME_USERNAME = 'trailersaas_runtime'
$env:DB_MIGRATION_USERNAME = 'trailersaas_migration'
$env:DB_MIGRATION_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Migration password' -AsSecureString)).Password
$env:POSTGRES_PASSWORD = $env:DB_MIGRATION_PASSWORD
docker run --detach --name trailersaas-postgres --publish 127.0.0.1:5432:5432 --env POSTGRES_DB=trailersaas --env POSTGRES_USER=trailersaas_migration --env POSTGRES_PASSWORD --volume trailersaas-pgdata:/var/lib/postgresql postgres:18-alpine
Remove-Item Env:POSTGRES_PASSWORD
docker exec -it trailersaas-postgres psql -U trailersaas_migration -d trailersaas
```

Inside psql, provision only the local application role:

```sql
CREATE ROLE trailersaas_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
GRANT CONNECT ON DATABASE trailersaas TO trailersaas_runtime;
GRANT USAGE ON SCHEMA public TO trailersaas_runtime;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
\password trailersaas_runtime
\q
```

The interactive `\password` command prompts for the runtime password.
Then supply that same password to the backend and run:

```powershell
$env:DB_RUNTIME_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Runtime password' -AsSecureString)).Password
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

On POSIX shells export the same five required variables and use
`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. Obtain passwords through
your normal secure local environment mechanism, not literals in shell history.

The official image makes POSTGRES_USER the container's bootstrap admin; that is
acceptable only for this local example, not a production role policy. Its
initialization variables apply on first initialization of a fresh volume.
Do not reset/reuse an existing data volume just to change credentials.
PostgreSQL 18 data is mounted at `/var/lib/postgresql`, not the older data mount.
To stop/start this local instance use `docker stop trailersaas-postgres` /
`docker start trailersaas-postgres`; stopping preserves the named volume.

Local HTTP remains `127.0.0.1:8080`. Issue #21 keeps liveness lifecycle-only,
and includes PostgreSQL in readiness as well as aggregate health. Health details
and components remain hidden. Native JSON logs, safe X-Request-Id, ProblemDetails,
Micrometer and non-exporting OTel tracing are documented in
[baseline observability](../docs/development/observability.md). No external
observability services or credentials are required.

## Flyway / JPA

Migrations live in `src/main/resources/db/migration/`. Use
`V<version>__<description>.sql`, for example
`V2__approved_change.sql`. Versions must be unique and ordered; never edit an
already applied versioned migration. Add a subsequent migration instead.
Migration filenames and checksums are validated; baseline-on-migrate is false
and Flyway clean is disabled.

`V1__technical_baseline.sql` only executes `SELECT 1`. It proves that the
versioned migration pipeline runs and records a successful migration. The only
table it creates indirectly is Flyway's own `flyway_schema_history`.
`V2__identity_users.sql` creates only global users, with firebase_uid UNIQUE and
no tenant_id. Its baseline is Accepted schema v1.0 plus the Accepted v1.0.1
hardening amendment merged in #75. #23 reuses users; it must not recreate or
silently redefine it when adding Tenant/membership/roles. No seed data is added.
`V3__identity_tenants_memberships_roles.sql` creates only tenants,
tenant_memberships and role_assignments. V1/V2 remain unchanged.
`V4__identity_tenant_rls.sql` adds policies to those three tables without
creating tables or changing V1/V2/V3 or their runtime grants.

Flyway is the only schema authority. Hibernate uses `ddl-auto=validate`,
Open EntityManager in View is disabled and Spring SQL initialization is
`never`. Do not replace those values with `update`, `create`, `create-drop`,
`schema.sql` or `data.sql`. Identity has four JPA persistence models; no jOOQ or H2.
Runtime grants for future tables must accompany later approved migrations.

V2's quoted runtimeRole Flyway placeholder comes from DB_RUNTIME_USERNAME.
Startup validates `[A-Za-z_][A-Za-z0-9_]{0,62}` (1–63 ASCII identifier characters)
and equality with the runtime datasource username before migration. This blocks
SQL injection/configuration overrides while allowing independent environment role
names. V3 reuses exactly this placeholder for SELECT-only grants and V4 for
runtime-targeted policies. No role is created,
no privileges go to PUBLIC, and runtime remains without
ownership, schema CREATE, SUPERUSER or BYPASSRLS.

## Firebase authentication (#22)

Supply an explicit FIREBASE_PROJECT_ID and official ADC before starting. For local
development, set GOOGLE_APPLICATION_CREDENTIALS through the OS/IDE to a protected,
ignored service credential file for a separate non-production project; never
commit its contents or pass credentials through command-line arguments. Managed
environments may supply official ambient ADC instead. No second secret format or
client Firebase setup is introduced. Invalid/missing project or ADC fails startup
without provider details. FIREBASE_AUTH_EMULATOR_HOST is unsupported/rejected.

The standard Spring Security bearer filter accepts Authorization: Bearer ID token.
Firebase Admin Java 9.11.0 verifyIdToken validates signature, issuer, project/audience,
expiry and structure; Google access tokens/custom tokens are not substitutes.
Firebase SDK types remain internal to identity infrastructure. A small
FirebaseTokenVerifier returns only verified UID/email/displayName and the SDK's
typed emailVerified field. Spring builds a
typed AuthenticatedUserPrincipal with internal User ID and UID and **no authorities**.
Custom role/Tenant/Customer/capability claims never grant business access. User alone
does not grant Tenant access. #24 adds fresh capability/scope authorization;
#26 adds transaction-local RLS as defense-in-depth, not authorization.

GET /api/v1/me returns userId, email, displayName and that User's persisted
memberships, each with tenantId, tenantName, status and roles
(roleCode/scopeType/customerId). A User with none still receives memberships: [].
Valid first authentication inserts users by UID with PostgreSQL ON CONFLICT DO
NOTHING, then separately reads the winner under READ COMMITTED. UNIQUE(firebase_uid)
arbitrates concurrent first logins; both callers resolve the same UUID. UUIDs are
application-generated. Metadata/timestamps are captured only on first creation,
not overwritten by later tokens; email is not an identity/authorization key and
different UIDs may share it. General authentication does not require email_verified;
the verified-email invitation match belongs exclusively to #25.

The API is stateless: no login sessions/cookies, form/basic/password auth or browser
redirects. /api/** is authenticated by default; the three accepted health probes
stay public without exposing extra Actuator. Non-API missing paths remain 404.
Missing/malformed/invalid/expired credentials return generic 401 Problem Details
with AUTHENTICATION_REQUIRED and the existing requestId/traceId correlation.
Authorization, token claims, provider messages and credential contents are never
copied into logs or public errors. #25 adds invitation acceptance below.

References: [official Firebase setup/ADC](https://firebase.google.com/docs/admin/setup),
[ID-token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

## Membership persistence and bootstrap directory (#23)

User → TenantMembership → RoleAssignment links the global identity to Tenants.
Membership status is ACTIVE/SUSPENDED/DISABLED with SQL checks; invitation state
is separate and reserved for #25. Tenant status remains an unconstrained, required
varchar because its vocabulary is still candidate. No Tenant status edit API exists.
Multiple roles are allowed. SQL enforces the accepted role/scope matrix:
CUSTOMER_DISPATCHER requires CUSTOMER and a customer_id; TENANT_ADMIN,
TRANSPORT_OPERATIONS, YARD_SPOTTER and DRIVER require TENANT and no customer_id.
Partial unique indexes prevent duplicate roles per membership/Customer; the
composite membership FK prevents cross-Tenant role references. FKs have no
destructive delete cascades.

V5 adds real Customers and the tenant-safe role_assignments Customer FK, preserving
V3's customer_id and scope checks. Test migrator fixtures now create Customers
before inserting Customer-scoped roles. No production Tenant/membership/role write
commands or Admin endpoints are implemented. Runtime has SELECT on all three V3
tables and V6's onboarding INSERT on memberships/roles, retaining V2's existing
SELECT/INSERT on global users.

MembershipDirectory is an internal identity/bootstrap boundary for /me. Its input
is AuthenticatedUserPrincipal, never an arbitrary User ID supplied by HTTP.
The narrow repository performs one User-filtered join, with tenant-safe role
joining and stable Tenant name/id and roleCode/scopeType/customerId ordering.
The user_id index supports this real lookup. The (tenant_id, membership_id) role
index supports its join across both scopes; partial uniqueness indexes alone
require scope predicates and cannot cover this unfiltered join.
A read-only transaction uses the
ordinary runtime datasource; there is no admin connection or BYPASSRLS.
#26 establishes the authenticated internal User's bootstrap RLS context before
this query, separately from normal TenantContext lookups. No generic
findAll/findById tenant repository is exposed.

User alone grants no Tenant access; membership existence does not grant capabilities.
ACTIVE/SUSPENDED/DISABLED entries describe persisted status in /me, without granting
permission to act. Roles remain DB data, never Spring authorities or Firebase custom
claims. Principal/authorities stay identity-only and empty respectively; memberships
and roles are freshly read for every request, without token/SecurityContext caching.

## Capability and scope authorization (#24)

The public identity module API is `Capability` (57 permissions after #28)
and `TenantAuthorization`. Consumers request capabilities, never compare role
names or use Spring role/authority annotations. The only role mapping is internal
`RoleCapabilities`; its exact conservative grants are documented in
[permission capabilities](../docs/security/permission-capabilities.md#implemented-conservative-mapping-24).
TBD/limited/override grants remain denied, not inferred from that draft matrix.

Each call queries current PostgreSQL data by **internal userId + requested tenantId**,
resolving only ACTIVE memberships and their current role assignments. A left join
distinguishes an ACTIVE roleless membership from no active membership. The narrow,
read-only repository uses ordinary runtime SELECT grants; no authorization cache,
admin connection or authorization enrichment is introduced. Before reading roles,
authorization pins the requested Tenant in the same transaction. Tenant.status
is deliberately not an authorization rule.

- `requireTenantCapability(principal, tenantId, capability)`: only TENANT-scoped
  grants qualify; a Customer-scoped role never grants Tenant-wide access.
- `requireCustomerCapability(principal, tenantId, customerId, capability)`: a
  TENANT grant or matching CUSTOMER grant qualifies. The target is mandatory.
  Consumers must derive it from tenant-safe resource data, not trust a request hint.
- `requireAssignedWorkCapability(principal, tenantId, capability, assignmentSatisfied)`:
  only TRIP_VIEW_ASSIGNED/TRIP_EXECUTE_ASSIGNED qualify, with a grant and a
  server-derived assignment condition. False denies. The other methods cannot
  authorize these two capabilities. Future Driver/Trip services must compute the
  condition from persisted assignment data; no client boolean is trusted.

Example future service integration:

```java
authorization.requireCustomerCapability(principal, tenantId, resource.customerId(), Capability.ORDER_EDIT);
// Then validate domain state before executing the command.
```

Missing/SUSPENDED/DISABLED memberships produce 403 TENANT_ACCESS_DENIED. Missing
grants or failed assignment constraints produce CAPABILITY_DENIED; a potential
Customer grant outside the target (or missing target) produces CUSTOMER_SCOPE_DENIED.
The identity advice precedes the generic infrastructure fallback. Problem Details
have generic public details and preserve automatic traceId/X-Request-Id correlation,
without exposing internal grants or authorized Customer IDs.

`/api/v1/me` remains a bootstrap directory of all three membership statuses; its
contents do not authorize protected actions. Role deletion or membership suspension
takes effect on the next authorization with the same token. Firebase custom claims
still cannot grant capabilities and Spring authorities remain empty.

#24's synthetic authorization consumers remain exclusively in test sources.
#27 adds Customer persistence/FK and production master-data consumers below;
Driver/Trip lookup and domain-state execution remain deferred. #25 consumes
USER_INVITE without adding capability mappings or Spring authorities.

## Transaction-local PostgreSQL RLS (#26)

`TenantContext` is a small public identity boundary backed by the current JPA
transaction's physical connection, not a ThreadLocal scope or a separate
transaction. It requires an active, joined, non-autocommit transaction and uses
prepared `set_config(..., ?, true)` calls:

| Mode | app.current_tenant_id | app.current_user_id |
| --- | --- | --- |
| Tenant | Requested Tenant UUID | Empty |
| Bootstrap /me | Empty | Authenticated internal User UUID |

Repeating the same context is allowed. Switching Tenant/User or mixing modes
throws a programming/security error without changing settings. No context
returns no rows from `tenants`, `tenant_memberships` or `role_assignments`.
The policies also reject mixed modes. Commit/rollback clears the local values;
the connection may return empty settings, handled with `NULLIF(..., '')`.

V4 enables RLS on memberships and roles with Tenant `USING` and `WITH CHECK`.
Their bootstrap policies are **SELECT-only**, restricted to the User's own
memberships and those memberships' roles. Tenant SELECT is restricted to the
current Tenant or the bootstrap User's membership Tenants, including all three
membership statuses. Global `users` stays outside RLS: verified UID resolution
happens before Tenant selection using narrow repositories. No role-based or
Customer-level SQL policy is introduced.

Runtime remains non-owner, non-SUPERUSER, non-BYPASSRLS, without schema CREATE,
and restricted SELECT/onboarding INSERT on V3 tables. `ENABLE ROW LEVEL SECURITY`
already protects it;
`FORCE` is deliberately deferred to preserve the separate owner/migrator
administrative path, not to give runtime a bypass. Future Tenant-owned tables
must receive their own RLS policies in the migration that creates them.

Authorization retains its REQUIRED transaction propagation. Future commands
open an outer transaction, authorize, then use repositories in that same
transaction; the setting remains in force throughout:

```java
@Transactional
public void command(AuthenticatedUserPrincipal principal, UUID tenantId) {
    authorization.requireTenantCapability(principal, tenantId, Capability.ORDER_EDIT);
    // Tenant-scoped repository reads/writes and domain-state validation follow.
}
```

`TenantTransactionExecutor.execute(tenantId, callback)` is the background
extension point: one Tenant per transaction using the normal runtime role.
It rejects ambient/nested transactions, so a callback cannot switch Tenant by
calling the executor again. For an existing application transaction, use
authorization/`TenantContext`, not this executor. It grants no capabilities;
future workers must enforce their own server-side authorization/work contract.
No actual job is implemented.

## Customers, Sites and Operational Locations (#27)

`V5__customers_sites_locations.sql` creates four Tenant-owned tables:
`customers`, `physical_sites`, `customer_sites`, `operational_locations`.
V1-V4 and Accepted schema documents are unchanged. Customer status is
ACTIVE/INACTIVE; Location types are BASE/YARD/DOCK/CUSTOMER_AREA/SHOP/OTHER.
All relationships use Tenant-safe composite FKs, including the previously
deferred `role_assignments(tenant_id, customer_id)` FK. There is no destructive
CASCADE or fake LOCATION_UNKNOWN row. The Location identity includes amendment
N's UNIQUE(tenant_id, site_id, id).

All four tables enable RLS with Tenant USING/WITH CHECK and mutually exclusive
Tenant/bootstrap settings from #26. They have no bootstrap policy: no context,
or /me bootstrap context, means no master-data rows. Customer filtering remains
application authorization, not Customer-level RLS. Users remains global.
Runtime retains its restricted ownership model and existing V2/V3 grants.
V5 grants SELECT/INSERT/UPDATE on Customers, Sites and Locations, and
SELECT/INSERT/DELETE (not UPDATE) on customer_sites; no other writes/deletes.

`TenantAuthorization.resolveCustomerScope(principal, tenantId, capability)`
returns an immutable `CustomerAccessScope`: Tenant-wide or a set of Customer
UUIDs, never roles. It resolves fresh ACTIVE membership and current DB grants
in the consumer's transaction. Assignment-scoped capabilities cannot use it.
CUSTOMER_VIEW/SITE_VIEW are Tenant-wide for Tenant Admin and Transport
Operations, Customer-scoped for Customer Dispatcher, absent for Driver/Yard
Spotter. Operations/Dispatcher do not acquire any master-data writes.
Other ambiguous mapping entries remain deferred/deny-by-default.

One Physical Site can have multiple customer_sites associations: the address is
not duplicated per Customer. Dispatcher Customer lists filter by permitted IDs;
Site queries use associations; Location queries filter by permitted customer_id,
excluding neutral NULL Locations. Tenant-wide readers see neutral Locations.
Direct Customer access outside scope is 403 CUSTOMER_SCOPE_DENIED; a Site
not visible through associations returns 404 RESOURCE_NOT_FOUND.

All paths below are relative to `/api/v1/tenants/{tenantId}`:

| Path | Methods / capability |
| --- | --- |
| /customers | POST CUSTOMER_CREATE; GET CUSTOMER_VIEW |
| /customers/{customerId} | GET CUSTOMER_VIEW; PATCH CUSTOMER_EDIT |
| /sites | POST SITE_CREATE; GET SITE_VIEW |
| /sites/{siteId} | GET SITE_VIEW; PATCH SITE_EDIT |
| /customers/{customerId}/sites | POST CUSTOMER_EDIT |
| /customers/{customerId}/sites/{siteId} | DELETE CUSTOMER_EDIT |
| /sites/{siteId}/locations | POST SITE_CREATE; GET SITE_VIEW |
| /locations/{locationId} | PATCH SITE_EDIT |

Writes require Tenant-wide grants. Responses are DTOs, never JPA entities.
Master-data list responses are `{"items": [...]}`, ordered by name then id,
without cursor/filter parameters in this baseline; unsupported query parameters
are rejected. Operational/history cursor pagination remains a later concern.
OpenAPI documents these shapes and only expands #27 paths/schemas.

Creates default to version 0 and ACTIVE Customer / active Site / active Location.
PATCH requires a nonnegative integral expectedVersion. @Version protects races
after comparison; conflicts return 409 STALE_VERSION. No-op PATCH does not
increment the version. Omitted fields stay unchanged; explicit null clears
nullable fields. Address objects update supplied subfields, while null clears
the address. Location siteId is immutable and an attempted PATCH is rejected;
no move endpoint exists. Coordinates are independently optional and rounded to
the schema's six decimals; IANA zones, coordinate ranges, positive integer
radius and two-character country codes are validated without paired-coordinate
rules.

Customer-specific Location creation, Customer changes and active updates require
the Customer-Site association in the same transaction. Inactive historical
Locations can retain a removed association; reactivation requires revalidation.
Association removal returns 409 RESOURCE_CONFLICT when an active Location
references that Tenant/Customer/Site; inactive history is retained.

Both operations lock the **parent Physical Site**: FOR SHARE for Location
validation; FOR UPDATE before association removal and its active-Location check.
This intentionally serializes association changes within a Site. PostgreSQL
requires UPDATE privilege for SELECT row locks, even FOR SHARE, so locking
customer_sites directly would contradict its approved no-UPDATE grant.
The parent lock uses existing Site UPDATE permission instead; no privileges or
security-definer shortcuts are added. The association is read after locking
under the standard READ COMMITTED transaction, so a committed removal is seen.
Concurrent creates/reactivations and removals follow the same lock protocol.
No permanent Location-to-association FK prevents retaining inactive history.
See [PostgreSQL row-lock privileges](https://www.postgresql.org/docs/18/sql-select.html).

Duplicate Customer names/associations map to safe 409 RESOURCE_CONFLICT;
validation to 400 VALIDATION_FAILED; missing resources to safe 404.
Problem Details retain code, safe field errors, traceId and X-Request-Id without
returning SQL/constraint names, internal grants, tokens or foreign metadata.

V5 validates the new FK immediately. Existing databases with orphan Customer
role UUIDs must be repaired by an administrator before upgrading, not silently
seeded or migrated with the constraint disabled. Equipment (#28/#29), Web forms
(#30), Orders/Trips and audit remain unimplemented.

## Drivers and Tractors master data (#28)

V7__drivers_tractors.sql adds only drivers and tractors, preserving V1–V6.
Drivers carry optional global user_id, name, nullable license/certificate date,
active, timestamps and optimistic version. Tractors carry the original display
number, a private STORED generated normalized key, active, timestamps and version.
Both use Tenant-safe id uniqueness and non-cascading FKs. Drivers may exist
without a User. The partial (tenant_id,user_id) index prevents two linked Drivers
for the same User within one Tenant, while allowing links across Tenants.

Tractor normalization implements amendment I exactly, with deterministic "C"
collation and no PostgreSQL extension:

```sql
translate(
  regexp_replace(btrim(operational_number, ' ') COLLATE "C", ' +', ' ', 'g'),
  'abcdefghijklmnopqrstuvwxyz',
  'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
)
```

Display is never rewritten. SQL rejects empty normalized keys and C0/C1
controls (PostgreSQL text also rejects NUL); application validation rejects
all controls before persistence. Punctuation, zeros, separators and non-ASCII
remain distinct. Uniqueness is enforced by (tenant_id,normalized key).

Runtime grants are exactly SELECT/INSERT/UPDATE on both tables, without DELETE,
ownership, schema CREATE or bypass privileges. RLS USING and WITH CHECK require
the transaction-local Tenant and exclude bootstrap-user context. No-context
and /me-bootstrap queries see zero equipment rows. Existing policies are intact.

Tenant Admin may create/edit via DRIVER_CREATE/EDIT and TRACTOR_CREATE/EDIT.
The approved DRIVER_VIEW/TRACTOR_VIEW additions extend the exhaustive catalog
to 57 and grant only Admin and Transport Operations generic Tenant-wide reads.
Operations receives no writes; Dispatcher/Yard/Driver receives no generic browse.

Identity's public DriverAccountLinkPolicy requires the existing transaction's
Tenant context and checks fresh ACTIVE membership plus TENANT DRIVER role for
the target User. It reveals no internal roles, grants no actor capability and
never establishes arbitrary Tenant access. Drivers uses only this public API;
actor authorization remains DRIVER_EDIT. Linking locks the Driver and checks
expectedVersion; an existing link cannot be silently replaced. Linking creates
no membership/role/invitation. Unlinking preserves User/access/history.

Implemented Tenant-scoped routes:

- POST/GET /drivers; GET/PATCH /drivers/{driverId}
- POST /drivers/{driverId}/user-link: userId + expectedVersion, 200 Driver
- DELETE /drivers/{driverId}/user-link: required expectedVersion JSON body, 204
- POST/GET /tractors; GET/PATCH /tractors/{tractorId}

No general create/PATCH accepts Driver userId. Nullable license/certificate
fields can be cleared explicitly; expired dates remain valid metadata.
Lists return items, deterministically ordered by name/id or normalized key/id,
without filters/pagination. Unknown query/body fields are rejected.
All mutations require expectedVersion except creation; @Version protects races.
No-op PATCH and already-null unlink retain version. active supports deactivation/
reactivation; neither aggregate supports hard delete. Version conflicts return
STALE_VERSION; expected UNIQUE/FK/CHECK violations return generic RESOURCE_CONFLICT.
Infrastructure SQL states are not converted to 409 and remain sanitized 5xx.

PostgreSQL 18 tests exercise generated normalization, exact grants, RLS
unfiltered/cross-Tenant queries, bootstrap/pool cleanup, link uniqueness and fresh
eligibility. Real HTTP tests cover permissions, nullable patching, link/unlink,
concurrent links, stale versions and safe infrastructure errors. Concurrent JPA
transactions prove @Version detects simultaneous Driver/Tractor updates.
No Trailer, UI, default assignment, Driver Shift, audit or #29/#30 is implemented.

## Membership invitation/onboarding (#25)

V6__membership_invitations.sql creates only membership_invitations and
invitation_role_assignments. V1–V5 and Accepted schema documents stay unchanged.
SQL enforces four invitation statuses, nonempty normalized email, unique token
hash, one PENDING Tenant/email, the role/scope matrix and partial role uniqueness.
Tenant-safe parent/Customer FKs preserve history without CASCADE. List and
parent-role indexes support actual ordered list/role reads.

POST/GET /api/v1/tenants/{tenantId}/invitations and POST
/api/v1/tenants/{tenantId}/invitations/{invitationId}/revoke require USER_INVITE
through fresh PostgreSQL authorization. Lists return items ordered by createdAt
DESC/id, with optional status only; token/hash are never returned. Creation uses
server UTC time and exactly seven days. A live PENDING conflicts; an expired
PENDING is locked and marked EXPIRED before replacement. The unique index
arbitrates concurrent creation. Revocation is idempotent and preserves roles;
ACCEPTED cannot become REVOKED.

Java SecureRandom generates 32 random bytes encoded as unpadded Base64 URL-safe.
Only lowercase-hex SHA-256 is stored. Creation returns acceptanceToken once for
the Admin to copy. Raw tokens are absent from DB/logs/MDC/errors; token DTOs have
redacted toString. There is no email provider, outbox, UI or expiry job. Email
normalization is String.trim + Locale.ROOT lowercase of the complete email,
without provider aliases/dot/plus transformation. Original email is display data.

POST /api/v1/invitations/accept is globally Firebase-authenticated and accepts
only {"token":"..."}, with canonical 32-byte encoding. The SDK's isEmailVerified
and current token email are allowlisted into authenticatedEmail and
authenticatedEmailVerified, distinctly from persisted first-login email used by
/me. Missing/unverified/mismatching current email denies acceptance even if
users.email matches. No arbitrary claim/Firebase role authorizes onboarding;
general authentication still does not require verified email. /me is unchanged.

An internal identity-only context establishes app.current_invitation_hash via
prepared, transaction-local set_config on the same JPA/Hibernate physical
connection. Hash-only RLS allows SELECT/FOR UPDATE of that one invitation and
SELECT of its intended roles, not enumeration/insertion/update. The acceptance
UPDATE policy retains hash-only USING for row locking, but WITH CHECK requires
a matching non-null server-derived Tenant as well as the hash and no bootstrap
User context. A wrong Tenant cannot update that invitation. Tenant policies cover
Admin operations; /me bootstrap/no context sees zero invitation rows. Existing
RLS policies remain unchanged. After locking and validating the invitation, only
the acceptance service pins its server-derived Tenant in the same transaction.
No migration/admin datasource, BYPASSRLS, SECURITY DEFINER or general Tenant
authorization bypass is used.

The narrow repository uses prepared SQL through the current JPA transaction;
Admin reads include Tenant and the sole onboarding read includes hash. No global
browse API or dependency on customers.internal exists. Customer integrity uses
V5's tenant-safe FKs, without an invented ACTIVE Customer requirement. One
transaction creates an ACTIVE membership, exactly the intended roles and
ACCEPTED User/time. Existing memberships are never merged. FOR UPDATE serializes
same-token acceptance; same-User retries return the existing membership without
reactivating it/changing roles. Any failure rolls back everything. Refresh /me
to read the new DB membership. Principal remains identity metadata; Spring
authorities remain empty and User alone grants no Tenant access.

V6 grants SELECT/INSERT/UPDATE on membership_invitations, SELECT/INSERT on
invitation_role_assignments, and only additional INSERT on tenant_memberships
and role_assignments. No intended-role UPDATE/DELETE, membership/role mutation
grant, ownership, schema CREATE, PUBLIC grant or administrative privilege is
added. The validated runtimeRole placeholder is reused. RLS context clears
after commit/rollback, including pooled-connection reuse.

Invalid format/request: 400 VALIDATION_FAILED. Admin conflicts: 409
RESOURCE_CONFLICT. Every syntactically valid but unacceptable invitation: 409
INVITATION_NOT_ACCEPTABLE with "The invitation cannot be accepted." No email,
token/hash, intended roles/Customers, accepted User, SQL or constraint names leak
in public errors; Problem Details/correlation remain intact. No membership
mutation API, audit subsystem or #28–#30 implementation is included.

Only expected PostgreSQL UNIQUE (23505), FK (23503) and CHECK (23514) failures
from invitation writes map to these safe 409 domain errors. Connection failures,
timeouts, generic JDBC/pool failures, unknown SQLStates and other DB failures
propagate to the existing safe 5xx Problem Details handler; they are not permanent
invitation conflicts. Both domain and infrastructure failures roll back all
membership/role/invitation changes together.

## Trailers and QR identities (#29)

V8__trailers_qr_identities.sql adds only trailers and trailer_qr_identities;
V1–V7 and Accepted schema/state documents remain untouched. Trailer UUID is
independent of number. Amendment I/V7's exact generated STORED expression is
reused with C collation, ASCII-only trim/collapse/case fold, controls rejected
and original display preserved. No ownership/size fields are introduced.
Tenant-safe nullable FKs protect Customer, current/last Location and last Driver.
Closed SQL catalogs keep location/load/serviceability/usage separate:
AT_LOCATION needs a Location; LOCATION_UNKNOWN forbids one; IN_TRANSIT has no
extra unapproved constraint.

POST sets LOCATION_UNKNOWN / UNKNOWN / IN_SERVICE, active=true, version=0 and
null provenance. Generic PATCH changes only number/type/notes/purpose/active,
requires expectedVersion and @Version, retains version on no-op, and never
changes designation or physical state. Integrity 23505/23503/23514 maps to safe
RESOURCE_CONFLICT; infrastructure failures stay safe 5xx. Responses omit Tenant/
normalized key. Exact seven filters AND with Customer scope; no pagination.
TrailerAccessPolicy exposes fresh Tenant-wide/Customer UUID scope from identity.
Dispatcher sees only matching designation, never foreign/unassigned; direct
invisible IDs are 404 and explicit foreign Customer filter is 403.

QR management uses TRAILER_EDIT. Issue locks Trailer; one-active partial UNIQUE
is the final barrier. SecureRandom generates 16 bytes/128 bits, unpadded URL-safe
Base64 (22 canonical characters), stored as public_token per ADR-018. Revoke
locks parent/QR, retains history, is idempotent; reissue preserves Trailer UUID.
Token and operational number are separately available for future labels.

GET /api/v1/trailer-qr/{publicToken} requires Firebase. TrailerQrLookupContext
sets prepared transaction-local app.current_trailer_qr_token on the existing JPA
connection. Its SELECT-only policy requires pure QR bootstrap mode: empty Tenant,
bootstrap User and invitation-hash settings. It exposes just the active exact QR,
no Trailer/enumeration/writes. After DB-derived Tenant is pinned, the special
policy stops applying and only normal Tenant RLS supplies visibility,
followed by fresh ACTIVE membership and TrailerAccessPolicy scan authorization.
Admin/Operations, scoped Dispatcher, Yard MOVE/CHECK or Driver HOOK/DROP may
identify; no physical mutation is authorized. Unknown/revoked/unauthorized
lookups and inactive/missing underlying Trailers all return identical 404.
Deactivation leaves QR identity/history intact; the same active QR resolves
again after Trailer reactivation. No admin/migrator/BYPASSRLS path is used.
Commit/rollback clears both settings, verified with a one-connection pool.

Both tables enable Tenant USING/WITH CHECK excluding /me bootstrap. Runtime
gets SELECT/INSERT/UPDATE only, never DELETE/ownership/schema CREATE/bypass.
No Customer-level RLS exists; Customer filtering is application authorization.
QR tokens are redacted from MDC HTTP paths and Problem Details instances.
Infrastructure logger levels are inherited normally; no Hibernate/Hikari/
transaction logger is disabled for QR privacy. SafeInfrastructureDiagnostics
adds a correlated structured fallback record with failureCategory, databaseFailure,
exceptionType, rootCauseType and SQLState when available. It never logs the
Throwable, message, SQL or parameter values. Request paths remain redacted;
no request token is added to application diagnostic fields. The public QR is
an identifier, not an authentication credential. Infrastructure failures remain
safe 5xx and retain DB/pool/transaction diagnostics under ADR-015.

PostgreSQL 18 tests cover exact schema/grants, state/FK invariants, generated
normalization, RLS/context/pool isolation and QR uniqueness/history. Real HTTP
tests cover scoped reads/filters, strict PATCH, safe failures, optimistic races,
serialized concurrent QR issuance, reissue and all-role authenticated resolution.
No movement events, couplings, physical commands, timeline, audit subsystem,
UI, image/printing/provider infrastructure or #30 is implemented.

## Tenant settings and Web CORS (#30)

V9__tenant_settings.sql creates only tenant_settings: Tenant PK/FK, positive
stale_trailer_threshold_hours, nullable default_timezone, timestamps and @Version.
Existing Tenants receive 72 hours/NULL/version 0. New Tenant provisioning must
create settings in its own future flow; the runtime cannot INSERT settings.
V1–V8 stay unchanged. Runtime has SELECT/UPDATE only, no DELETE/ownership/schema
CREATE/PUBLIC/bypass privileges. Tenant USING/WITH CHECK excludes /me bootstrap;
no context sees no settings rows.

GET/PATCH /api/v1/tenants/{tenantId}/settings require TENANT_SETTINGS_MANAGE from
fresh PostgreSQL authorization. PATCH requires expectedVersion, positive integral
threshold, IANA timezone or explicit null clearing, and rejects unknown fields.
@Version prevents silent overwrite; stale/races return STALE_VERSION, and no-op
retains version. No retention, billing or arbitrary settings JSON is implemented.

Spring Security CORS uses WEB_ALLOWED_ORIGINS, a comma-separated **exact origin**
allowlist. Outside local, supply HTTPS origins for the actual Web environments;
empty means no cross-origin access. Wildcards, URL credentials/paths/queries and
non-local loopback are rejected without echoing values. Local defaults only to
http://127.0.0.1:5173 and http://localhost:5173; override explicitly if Vite chooses
another port. Allowed methods: GET/POST/PATCH/DELETE/OPTIONS; headers:
Authorization/Content-Type/X-Request-Id; exposed X-Request-Id. Credentials=false;
bearer authentication and all existing Tenant/capability/RLS checks remain required.
No session cookie or wildcard fallback is added. V8 QR RLS/redaction/diagnostics
remain unchanged.

PostgreSQL 18 tests cover migration/backfill/schema/grants/RLS, Admin-only settings,
validation, nullable/no-op/stale/concurrent updates and real HTTP CORS preflight,
unknown origins and continued 401 authentication.

## Tests

With a working Docker daemon in Linux-container mode:

```sh
cd backend
./mvnw -B -ntp clean verify
./mvnw -B -ntp dependency:tree
```

PowerShell uses `.\mvnw.cmd`. The dependency tree must contain no H2 dependency.
First execution needs network access for Maven and official container images.

`PostgresIntegrationTest` is the shared test foundation: JUnit Testcontainers
starts `postgres:18-alpine`, creates a separate restricted runtime role and
registers the same variable names used by application.yml. Each container uses
generated disposable passwords; no local/Production credentials are required.
The Spring context is evicted after the test class, avoiding reuse of a datasource
pointing to a stopped container in later subclasses. Container reuse is disabled.

The existing HTTP health/startup tests now run against that real container.
Additional assertions prove PostgreSQL major 18, separate JDBC connection users,
Flyway migration history/validation, JPA startup/native query, no H2 driver,
no runtime schema CREATE/BYPASSRLS/superuser permissions and the four identity
plus four master-data tables and Flyway history.
Configuration context tests also cover explicit/default profiles, required values,
invalid JDBC URLs, local remote-DB rejection and sanitized failures, without DB
connections or real provider requirements. Security/adapter tests cover safe 401,
typed principals, ignored custom claims, stateless context cleanup and token privacy.
PostgreSQL tests cover V2/grants/UID uniqueness, duplicate emails, concurrent first
logins and real HTTP authentication-to-users with a test-only verifier. No FirebaseApp,
ADC or external Firebase/Google/emulator call is made in CI. V3 tests exercise
membership status/uniqueness, the composite Tenant FK, all role/scope combinations,
partial role uniqueness, restricted identity runtime grants, preserved history and
the invitation tables added only by V6. Real HTTP tests cover multi-Tenant User isolation,
ignored claim/header/User-ID hints, roleless memberships, Customer-scoped roles,
stable ordering and fresh status/role reads; original 401/stateless tests remain.
#24 tests cover the exact five-role mapping across all capabilities, scoped ACTIVE
resolution, deny-by-default, multiple roles, Customer A/B isolation, assigned-work
constraints and bypass prevention. Real HTTP/PostgreSQL tests prove same-token
revocation, ignored malicious claims, safe correlated 401/403 responses and the
difference between membership visibility and authorization. Flyway now ends at V9;
existing table/column and runtime grant assertions remain, with RLS enabled.
V4 tests deliberately omit Tenant predicates and verify isolation in PostgreSQL,
bootstrap privacy, context conflicts, and outer-transaction authorization.
A one-connection Hikari pool proves physical reuse by PostgreSQL backend PID
after both commit and rollback, for Tenant and bootstrap modes. Temporary
test-only INSERT/UPDATE/DELETE grants demonstrate cross-Tenant WITH CHECK
rejection and bootstrap SELECT-only behavior, then REVOKE in finally; V4 adds
no write grant. Background execution is exercised for successive Tenants.
V5 tests verify all new RLS tables with deliberately missing Tenant predicates,
no-context/bootstrap denial, cross-Tenant INSERT/UPDATE rejection, exact grants,
closed SQL catalogs, composite FKs and no cascades. HTTP tests exercise CRUD,
scoped shared Sites/neutral Locations, validation, version conflict, fresh
authorization and safe correlated errors. Concurrent PostgreSQL transactions
prove @Version conflict detection and Site-lock serialization of association
removal against Location creation/reactivation.
V6 tests cover schema/grants/FKs, uniqueness, history, Tenant/hash/bootstrap
isolation, pool cleanup after commit/rollback, strict token/email/role validation
and privacy. HTTP tests cover create/re-invite/list/revoke, current verified email
versus stored snapshot, generic denial, eight concurrent same-token acceptances
and concurrent creations, exact roles, /me refresh and rollback after a controlled
test-only DB trigger failure. No production failure hook exists. Firebase tests
use only the existing fake boundary and SDK mocks, without provider network.
Module-boundary tests continue
unchanged. Tests fail, rather than skip or mock,
when Docker is unavailable.

GitHub Actions uses the runner's local Docker daemon, not a PostgreSQL service
or external database. The four existing CI jobs remain required.


### Issue #34: operational Trip planning

V13 creates Trips, executable Stops and flexible Order mappings, with Tenant RLS
and minimum SELECT/INSERT plus Trip UPDATE and mapping DELETE for coordinated
pre-execution cancellation. V1–V12 remain intact. Trip creation is PLANNED unless
both valid active Driver and Tractor are supplied, then ASSIGNED; Trailer remains
optional planning, not physical coupling. Public module policies hold equipment
eligibility and shared Site locks inside the same transaction.

Orders are exclusively locked in deterministic UUID order before any planning
writes. OPEN→PLANNED uses the existing lifecycle/history hook once; already PLANNED
permits additional Trips without a false transition. An Orders-owned release SPI
lets cancellation detach only this Order's PLANNED/ASSIGNED mappings atomically
with Manifest release, preserving Trips/Stops/history and bumping each Trip once.
No Orders→Trips dependency is introduced. A Customers-owned guard prevents Site
association removal while active executable Stops serve that Customer.

POST/list/detail Trip APIs are Tenant-wide TRIP_PLAN/TRIP_VIEW_ALL; optional IDs
also require their assignment capabilities. Strict implemented OpenAPI responses
contain current projection/executable Stops, no assignment history. `order_stop_id`
is structurally supported but NULL for POST: the accepted input has no requested-
Stop identifier. Order `unplanned` now queries nonterminal operational Trip mappings,
independent of Manifests and with the original Customer scope/keyset. No replay
ledger is claimed; uncertain create responses require read reconciliation.

PostgreSQL 18 regressions cover cardinality, tenant-safe FKs, exact schema/grants,
RLS, catalog checks, atomic writes, Order planning, cancellation releases and real
Order/Site lock waits. #35 assignment events, #36 Trip mutations, #37 Web dispatch
and all physical execution remain outside this implementation.


## Trip assignment history and substitution (#35)

V14 adds `trip_assignment_events` only (Tenant RLS; runtime SELECT/INSERT, no history
UPDATE/DELETE). V1–V13 remain unchanged. POST /trips now creates initial Driver/Tractor/
Trailer events atomically without changing version 0. Pre-V14 projections are not
backfilled with synthetic history. Assignment/reassignment commands use an exclusive
Trip lock, expectedVersion, fresh all-mapped-Customer scope and shared eligibility
locks. Substitution uses the Accepted expectedTrailerId guard: AUTHORIZE changes the
planned expectation; REJECT preserves it, including when the visible target is OOS
or inactive. Neither affects physical Trailer state/coupling. Detail and the bounded
TRIP_VIEW_ALL timeline expose chronological domain assignment history. See the
[API implementation boundary](../docs/api/orders-dispatch.md#issue-35-implementation-boundary)
for state/no-op rules, metadata, the planning-only future Hook policy and exclusions.


### Issue #36: operational Trip changes and Driver updates

V15 adds only `trip_operational_events` and `trip_driver_updates` (append-only,
Tenant RLS/SELECT+INSERT, composite Trip/Stop and Tenant-safe Trip/Driver FKs), plus
Stop UPDATE and its composite identity key. V1–V14 are unchanged. Reroute retains
frozen previous/replacement destinations before changing future instructions;
cancellation preserves terminal Stop history. Business/history/Driver projection
commit atomically. No push/outbox worker, generic Audit or physical execution.

`POST trips/{id}/reroutes`, `POST trips/{id}/cancellations`, merged Trip timeline,
and `GET driver/me/updates` are bounded backend surfaces. Final safe cancellation
is an already-authorized Tenant transaction port for future execution, never a
public command. Driver update projection begins at V15 without invented backfill.
See [API boundary](../docs/api/orders-dispatch.md#issue-36-implementation-boundary)
for state/scoping/locking and [Driver API](../docs/api/mobile-execution.md).


### Issue #37: bounded Dispatch Board read projection

GET `/api/v1/tenants/{tenantId}/dispatch/board` requires fresh Tenant-wide
TRIP_VIEW_ALL and ORDER_VIEW_ALL under existing runtime Tenant RLS. It previews
at most 20 unplanned nonterminal Orders and 20 active Trips, with a single next
nonterminal executable Stop per Trip and current assignment/cancellation attention.
Customer filters both sections; Driver/Trip status filter Trips/attention only.
No date interpretation, history replay, new migration or new grants. Orders-owned
public `OrderDispatchDirectory` keeps persistence internal and avoids module cycles.
PostgreSQL 18 tests cover isolation, authority, semantics, deterministic bounds,
filters and constant query count without assignment/operational entity reads.
The [implementation boundary](../docs/api/orders-dispatch.md#issue-37-implementation-boundary)
documents exact response fields and deferred operational features.

### Issue #38: physical Hook/Drop coupling and server replay

V16 adds only `trailer_movement_events`, `trailer_couplings` and
`processed_client_operations`; V1–V15 are unchanged. Movement provenance implements
Accepted hardening M: distinct actor/source catalogs, HUMAN_USER requires User;
Hook/Drop derive HUMAN_USER + USER_ACTION from authentication. Coupling active
partial unique indexes protect Trailer and Tractor. A SECURITY INVOKER close-only
trigger plus column-scoped UPDATE prevents reopening/tampering with closed history.
There is no unique Trip coupling: its one-active semantic guard is serialized by
the Trip parent lock. Tenant-safe FKs have no destructive cascade.

Runtime has SELECT/INSERT on the three tables and UPDATE only on coupling
dropped_at/drop_event_id plus ledger status/processed_at/result_entity_type/
result_entity_id/response_data to finish the transaction-local claim. All have Tenant RLS excluding bootstrap. No DELETE,
ownership, PUBLIC, BYPASSRLS, schema CREATE or administrator datasource is used.
Device identity remains NULL until its parent registration surface exists.

Trips coordinates current assigned Driver authority and locked execution context;
Trailers owns physical facts/projection/ledger. Public module boundaries preserve
Trips → Trailers → Tractors without a cycle or cross-module internal imports.
Both commands allow ASSIGNED/AT_STOP/CANCELLING and never perform departure or
normal Trip/Stop/Order execution transitions. Drop validates its normalized
Stop/Site/Location and actual Order Customer compatibility through public policies.

Accepted hardening L uses conflict-safe INSERT PROCESSING with the Tenant+operation
unique index as replay arbitration. Duplicates wait for winner commit and read its
terminal result, or claim after winner rollback. There is no prior existence query,
advisory lock, process mutex or committed PROCESSING lease. request_hash and created_at
are NOT NULL; processed_at is NULL only for PROCESSING. Claim, physical writes and
terminal SUCCEEDED response commit together; failure rolls back everything without
persisting a transient REJECTED response. SHA-256 fingerprints the logical request.
Fresh authority precedes replay; physical
version checks apply only on first execution. Fact/interval/projection/saved result
commit atomically. PostgreSQL 18 tests cover both race directions, double coupling,
same-key concurrency, reassignment/substitution, rollback at each write, exact
privileges, provenance, RLS and pool cleanup. Full local verify requires Docker;
GitHub Actions always runs Testcontainers with `postgres:18-alpine` without skips.
See the [implemented API boundary](../docs/api/mobile-execution.md#issue-38-implementation-boundary)
for request/results, strict initial expected version, replay semantics and deferred
#39/#40/#41/#44/#46 work.

### Issue #39: movement/projection core and reconciliation

No migration, grant change or public endpoint is added. V1–V16 remain unchanged.
`TrailerMovementProjection` exposes seven typed operations joining the caller's
runtime Tenant transaction (MANDATORY), with required expected Trailer version.
The caller owns authentication, capability/Customer/Trip/Stop authorization,
parent lock ordering and full offline idempotency. Trailers never locks a Trip
or claims another operation key. Location identity is checked through the public
Customers `OperationalLocationDirectory`; Customer compatibility remains the
workflow's responsibility. Optional Driver/Trip/Stop/Tractor references retain
V16's tenant-safe FKs and HUMAN_USER's real global User FK.

Each accepted operation locks/refreshes Trailer, checks version/current state,
validates parents, appends a fixed event and updates the current row before flush.
`@Version` advances exactly once; infrastructure failures propagate and expected
integrity conflicts are safe domain failures. Hook/Drop reuse this writer after
their unchanged physical/ledger checks. Current GET/list still reads Trailer rows.

`TrailerProjectionReconciliation.compare` is an explicit diagnostic in a caller
transaction. It takes a shared Trailer lock and reads Tenant-scoped facts in
recordedAt/id order. It derives six movement-owned fields where facts support
them, compares only known fields, and returns MATCH, DRIFT or INSUFFICIENT_HISTORY.
Unknown baseline is distinct from a known NULL; known mismatch takes precedence
over incomplete history. There is no synthetic initial fact, full aggregate rebuild,
automatic repair, HTTP repair surface or normal-read history replay. See the
[operation matrix and metadata](../docs/api/mobile-execution.md#issue-39-implementation-boundary).

PostgreSQL 18 regressions cover the transition/load matrices, provenance, location
integrity, preservation, stale offline facts, history comparison, both write-failure
rollbacks and real lock waits, including movement versus Hook/Drop. The complete
#38 suite is preserved. Local Docker availability is required for the full suite.
Alerts/outbox, physical Trip/Stop execution, Yard mobile,
full Trailer timeline and generic Audit remain their later bounded Issues.

## Explicit Trailer commands (#40)

Six POST commands under /api/v1/tenants/{tenantId}/trailers/{trailerId} return
the authoritative Trailer DTO: location-corrections, mark-location-unknown,
load-state-changes, out-of-service, return-to-service and customer-designation-changes.
Typed strict bodies reject unknown fields and scalar coercion. Every command
requires expectedVersion and nonblank reason (comment for OOS), uses fresh
capability authorization and a locked/refreshed Trailer, and commits event plus
current row plus @Version atomically. Generic PATCH remains metadata/active only.

Location/load operations reuse TrailerMovementProjection. The internal typed
TrailerEventWriter shares #38/#39 chronology/provenance with the three
administrative facts; it queries chronology before dirtying Trailer, avoiding
multiple version increments. HUMAN_USER/USER_ACTION/authenticated User and
server command time are derived exclusively by the server. Before/after metadata
is fixed per event; history is append-only. No migration or privilege change.

CustomerIdentityPolicy exposes only same-Tenant existence with a shared target
lock, no internal entity or invented active-status restriction. Designation
changes immediately affect fresh scoped reads. OOS is independent of load,
location, designation and coupling: planning/new Hook rejects it, existing Drop
continues to close the interval. OQ-054 stays unresolved; 57 capabilities and
all role mappings are unchanged.

No Alerts/outbox (#57), evidence/documentIds (#49/#53 after #52), timeline (#41),
Yard/offline UI (#48/#49), second ledger or generic Audit (#61) is implemented.
Movement reconciliation preserves its six-field coverage across the three
known non-owned administrative facts.

### Issue #41 Trailer read surface

V17 creates only trailer_couplings_trailer_history and
load_manifests_trailer_lookup indexes. V1–V16, grants, RLS and command behavior
are unchanged. TrailerReadRepository is a Trailers-owned native cross-domain
read model: same-Tenant joins for display labels, no foreign internal Java APIs,
no writes and no event replay. Repeatable-read read-only transactions keep
projection/coupling/Manifest reads consistent. List/detail/timeline are covered
by fixed-query-count, index-path, fresh Customer scope and mixed-Manifest tests
on PostgreSQL 18. Timeline keyset ordering is occurredAt/id (physical chronology),
while reconciliation remains recordedAt/id (server chronology).

GET list adds derived coupling/filter/labels; GET detail retains the basic DTO
fields and adds bounded summaries. Command responses remain the original basic
Trailer DTO. OPEN Manifest previews cap at 20 parents/50 visible Orders total;
scoped readers never receive hidden counts or restriction hints. Administrative
reasons, Trip references and location-change IDs are conservatively redacted for
scoped timeline readers. No actor User IDs or raw metadata are public.

Per-Manifest `ordersTruncated` distinguishes visible allocations omitted by the
global preview limit from genuinely empty allocations, without hidden Customer
counts or flags. Parent existence checks and the 51-row child lookahead remain
inside the same bounded queries. PostgreSQL tests also record default-planner
EXPLAIN ANALYZE/BUFFERS node/index evidence and verify bounded returned rows,
separately from ordered-index eligibility diagnostics, without exact cost assertions.

## Driver execution (#44)

[Execution boundary](../docs/mobile/issue-44-road-execution.md) documents V18 immutable Stop events, the shared V16 ledger, own assigned Trip detail and five typed internal execution commands. Production first departure fails closed until #43 validates duty/inspection; tests substitute only the prerequisite seam. Delivery is blocked pending finalized POD. No capability catalog, physical Hook/Drop contract or historical migration changes.
## Private documents (#52)

Enable `R2_ENABLED=true` only with that environment's private R2 bucket and all
five R2 variables in `.env.example`. The AWS SDK v2 S3 adapter uses region `auto`,
Signature V4, strict TLS and bucket readiness validation. No public URL fallback,
test storage or embedded credentials exists in the application. Disabled storage
fails document APIs safely; CI injects an explicit test-only HTTP S3 peer.

Initialization returns a signed temporary PUT. Clients send bytes directly to
R2, then finalize with UUID `Idempotency-Key`. Finalization copies to a fresh
private key, hashes the actual bytes and publishes its database identity only
after verification. A reusable upload grant cannot rewrite finalized bytes.

See [document contracts/security matrix](../docs/api/documents.md) and
[implementation traceability](../docs/api/issue-52-document-storage.md).
No evidence approval, mobile media queue, cleanup worker or retention is enabled.
