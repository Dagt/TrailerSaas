# Backend PostgreSQL and Identity Boundary

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
and role_assignments. V4 adds RLS without changing these grants.

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
FirebaseTokenVerifier returns only verified UID/email/displayName. Spring builds a
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
copied into logs or public errors. #25 remains unimplemented.

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

Customers do not exist yet. V3 retains the accepted customer_id and scope checks;
#27 must add the tenant-safe Customer FK when it creates customers, before
Customer-scoped assignment writes are exposed. Only test migrator fixtures
insert structural Customer UUIDs now. No production Tenant/membership/role write
commands or Admin endpoints are implemented. Runtime has only SELECT on the
three V3 tables, retaining V2's existing SELECT/INSERT on global users.

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

The public identity module API is `Capability` (the 53 documented permissions)
and `TenantAuthorization`. Consumers request capabilities, never compare role
names or use Spring role/authority annotations. The only role mapping is internal
`RoleCapabilities`; its exact conservative grants are documented in
[permission capabilities](../docs/security/permission-capabilities.md#implemented-conservative-mapping-24).
TBD/limited/override grants remain denied, not inferred from that draft matrix.

Each call queries current PostgreSQL data by **internal userId + requested tenantId**,
resolving only ACTIVE memberships and their current role assignments. A left join
distinguishes an ACTIVE roleless membership from no active membership. The narrow,
read-only repository uses ordinary runtime SELECT grants; no authorization cache,
admin connection or principal enrichment is introduced. Before reading roles,
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

No Customer table/FK, Driver/Trip lookup, domain-state rules, invitations or mutation
endpoints are implemented here. All synthetic consumers and
HTTP endpoints demonstrating this integration live exclusively in test sources.

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
and SELECT-only on V3 tables. `ENABLE ROW LEVEL SECURITY` already protects it;
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
no runtime schema CREATE/BYPASSRLS/superuser permissions and only the four identity
tables plus Flyway history.
Configuration context tests also cover explicit/default profiles, required values,
invalid JDBC URLs, local remote-DB rejection and sanitized failures, without DB
connections or real provider requirements. Security/adapter tests cover safe 401,
typed principals, ignored custom claims, stateless context cleanup and token privacy.
PostgreSQL tests cover V2/grants/UID uniqueness, duplicate emails, concurrent first
logins and real HTTP authentication-to-users with a test-only verifier. No FirebaseApp,
ADC or external Firebase/Google/emulator call is made in CI. V3 tests exercise
membership status/uniqueness, the composite Tenant FK, all role/scope combinations,
partial role uniqueness, read-only runtime grants, preserved history and absent
Customer/invitation tables. Real HTTP tests cover multi-Tenant User isolation,
ignored claim/header/User-ID hints, roleless memberships, Customer-scoped roles,
stable ordering and fresh status/role reads; original 401/stateless tests remain.
#24 tests cover the exact five-role mapping across all capabilities, scoped ACTIVE
resolution, deny-by-default, multiple roles, Customer A/B isolation, assigned-work
constraints and bypass prevention. Real HTTP/PostgreSQL tests prove same-token
revocation, ignored malicious claims, safe correlated 401/403 responses and the
difference between membership visibility and authorization. Flyway now ends at V4;
existing table/column and runtime grant assertions remain, with RLS enabled.
V4 tests deliberately omit Tenant predicates and verify isolation in PostgreSQL,
bootstrap privacy, context conflicts, and outer-transaction authorization.
A one-connection Hikari pool proves physical reuse by PostgreSQL backend PID
after both commit and rollback, for Tenant and bootstrap modes. Temporary
test-only INSERT/UPDATE/DELETE grants demonstrate cross-Tenant WITH CHECK
rejection and bootstrap SELECT-only behavior, then REVOKE in finally; V4 adds
no write grant. Background execution is exercised for successive Tenants.
Module-boundary tests continue
unchanged. Tests fail, rather than skip or mock,
when Docker is unavailable.

GitHub Actions uses the runner's local Docker daemon, not a PostgreSQL service
or external database. The four existing CI jobs remain required.
