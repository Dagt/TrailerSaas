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
to the configured runtime role; Tenant grants/RLS and TenantContext remain deferred.

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

Flyway is the only schema authority. Hibernate uses `ddl-auto=validate`,
Open EntityManager in View is disabled and Spring SQL initialization is
`never`. Do not replace those values with `update`, `create`, `create-drop`,
`schema.sql` or `data.sql`. Identity has one JPA entity/repository; no jOOQ or H2.
Runtime grants for future tables must accompany later approved migrations.

V2's quoted runtimeRole Flyway placeholder comes from DB_RUNTIME_USERNAME.
Startup validates `[A-Za-z_][A-Za-z0-9_]{0,62}` (1–63 ASCII identifier characters)
and equality with the runtime datasource username before migration. This blocks
SQL injection/configuration overrides while allowing independent environment role
names. No role is created, no privileges go to PUBLIC, and runtime remains without
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
does not grant Tenant access; no Tenant context or membership lookup exists yet.

GET /api/v1/me returns userId, email, displayName and memberships: [] until #23.
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
copied into logs or public errors. #23–#26 remain unimplemented.

References: [official Firebase setup/ADC](https://firebase.google.com/docs/admin/setup),
[ID-token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

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
no runtime schema CREATE/BYPASSRLS/superuser permissions and only users plus Flyway history.
Configuration context tests also cover explicit/default profiles, required values,
invalid JDBC URLs, local remote-DB rejection and sanitized failures, without DB
connections or real provider requirements. Security/adapter tests cover safe 401,
typed principals, ignored custom claims, stateless context cleanup and token privacy.
PostgreSQL tests cover V2/grants/UID uniqueness, duplicate emails, concurrent first
logins and real HTTP authentication-to-users with a test-only verifier. No FirebaseApp,
ADC or external Firebase/Google/emulator call is made in CI. Module-boundary tests continue
unchanged. Tests fail, rather than skip or mock,
when Docker is unavailable.

GitHub Actions uses the runner's local Docker daemon, not a PostgreSQL service
or external database. The four existing CI jobs remain required.
