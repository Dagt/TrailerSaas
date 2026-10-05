# Backend PostgreSQL Baseline

Issue #19 adds migration-first PostgreSQL persistence to the existing Spring
Boot application (ADR-003/006/013). Use JDK 25 and the Maven Wrapper.

## Configuration

Issue #20 uses Spring profiles `local` (safe default), `staging` and `production`.
Use `SPRING_PROFILES_ACTIVE` to explicitly select one non-local profile; unknown,
mixed profiles and non-local default profiles are rejected before opening a DB.
Local HTTP binds to loopback and both local JDBC URLs must use loopback hosts.
Required/invalid DB configuration fails startup with variable names, not values.
No Firebase/R2/Sentry configuration is read or required yet.

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

No password or username defaults exist. Do not put secrets in source control,
JDBC URL query strings or command-line `-D` arguments. Missing configuration
must fail startup rather than selecting an embedded database.

Flyway has its own datasource credentials; it does not borrow runtime credentials.
Use the same database/schema for both connections. The separate URL supports
different connectivity options to that same database, not unrelated databases.
The runtime role must not be superuser or have BYPASSRLS, own future domain
tables, or have schema CREATE privileges. The migrator can own migrated objects.
Creating production roles, domain grants/RLS and TenantContext remains deferred.

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

Local HTTP remains `127.0.0.1:8080`. Overall `/actuator/health` now includes
Spring Boot's standard datasource health check. The existing liveness/readiness
groups retain their lifecycle-only behavior; no extra observability is added.
Health details remain hidden.

## Flyway / JPA

Migrations live in `src/main/resources/db/migration/`. Use
`V<version>__<description>.sql`, for example
`V2__approved_change.sql`. Versions must be unique and ordered; never edit an
already applied versioned migration. Add a subsequent migration instead.
Migration filenames and checksums are validated; baseline-on-migrate is false
and Flyway clean is disabled.

`V1__technical_baseline.sql` only executes `SELECT 1`. It proves that the
versioned migration pipeline runs and records a successful migration. The only
table created is Flyway's own `flyway_schema_history`; no domain objects exist.
There are no functional Tenant/User/Customer/Trailer/Order/Trip/etc. tables or
seed data. Future bounded issues add their actual approved schema migrations.

Flyway is the only schema authority. Hibernate uses `ddl-auto=validate`,
Open EntityManager in View is disabled and Spring SQL initialization is
`never`. Do not replace those values with `update`, `create`, `create-drop`,
`schema.sql` or `data.sql`. There are no entities, repositories, jOOQ or H2.
Runtime grants for future tables must accompany later approved migrations.

## Tests

With a working Docker daemon in Linux-container mode:

```sh
cd backend
./mvnw -B -ntp clean verify
./mvnw -B -ntp dependency:tree -Dincludes=com.h2database:h2
```

PowerShell uses `.\mvnw.cmd`. The H2-filtered tree must contain no dependency.
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
no runtime schema CREATE/BYPASSRLS/superuser permissions and no domain tables.
Configuration context tests also cover explicit/default profiles, required values,
invalid JDBC URLs, local remote-DB rejection and sanitized failures, without DB
connections or future provider requirements. Module-boundary tests continue
unchanged. Tests fail, rather than skip or mock,
when Docker is unavailable.

GitHub Actions uses the runner's local Docker daemon, not a PostgreSQL service
or external database. The four existing CI jobs remain required.
