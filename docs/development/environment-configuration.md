# Environment Configuration and Secret Templates

Issue #20 implements ADR-020, using the hosting boundaries accepted in
ADR-009/016. PostgreSQL and API configuration are active; Issue #21 adds optional
Sentry Web/Mobile configuration. There are no real provider IDs, cloud resources,
deployment workflows or Production credentials in the repository.

## Selection and Safe Defaults

| Application | Explicit selector | Default | Active requirements |
| --- | --- | --- | --- |
| Backend | `SPRING_PROFILES_ACTIVE=local`, `staging` or `production` | `local`, loopback HTTP | Five DB variables below; optional separate migration URL |
| Web | `VITE_APP_ENV=development`, `staging` or `production` | `development`, `http://127.0.0.1:8080/api/v1` | Explicit absolute HTTPS API URL outside Development |
| Mobile | `APP_ENV=development`, `staging` or `production` | `development`, `http://localhost:8080/api/v1` | Explicit absolute HTTPS API URL outside Development |

Environment selection never comes from a hostname. No missing/unknown
Staging/Production setting falls back to a different environment. Deployment
operators must pair each explicit selector with that environment's resources;
syntax validation cannot prove ownership of a remote URL or cloud project.
All remote hosts shown here are reserved `example.test` documentation hosts,
not configured environments.

Backend accepts exactly one of the three profiles. Changing default profiles
to Staging/Production is rejected; non-local profiles must be active explicitly.
The `local` profile rejects non-loopback runtime **and** migration JDBC hosts,
including explicit overrides. Use loopback forwarding for a local Docker DB.
Staging/Production use the common non-secret Spring configuration without
duplicated YAML or automatic provider configuration. No profile supplies DB
password defaults. A BeanFactoryPostProcessor validates effective configuration
before datasource beans open; failures name required variables, never values.
Flyway remains the schema authority, runtime/migration credentials are separate,
and Hibernate remains `validate`. No domain migration is added.

Web validates with Zod both at Vite startup/build and at client initialization.
Its application environment is independent of Vite `mode`/`NODE_ENV`:
`npm run build` optimizes a bundle but does **not** select Production resources.
Vite mode only controls which `.env` files are read. Explicit environment values
in the process take precedence over files. Do not reuse local environment files
between environments: `.env.local` also loads for non-local builds.

Mobile uses existing Dart compile-time defines. Configuration validates during
app bootstrap, before opening SQLite; no dotenv or second loader is introduced.
Both clients reject malformed URLs, URL credentials, query strings and fragments;
non-local URLs require HTTPS. Development may explicitly override the loopback
URL for a development host/device. Such an override is a deliberate developer
choice, never an inferred environment or a default. Never supply Production
endpoints or data for Development.

## Variable Catalog

`D/S/P` means Development, Staging and Production, with different values/resources
per environment. `Reserved` means **not read or required by any current app**;
future issue names below define the intended consumer, not an implemented adapter.
Reserved names are provisional configuration slots for later bounded issues.
Required now is distinct from expected future requirements.

### Active Configuration

| Name | Consumer | Purpose | Environments | Required now | Classification | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Backend | Select local/staging/production | D/S/P | Explicit for S/P, optional D | Non-secret config | Active #20 |
| `DB_RUNTIME_JDBC_URL` | Backend | Runtime PostgreSQL database URL | D/S/P | Yes | Server config; no embedded passwords | Active #19 |
| `DB_RUNTIME_USERNAME` | Backend | Runtime role, no migration ownership | D/S/P | Yes | Restricted server config | Active #19 |
| `DB_RUNTIME_PASSWORD` | Backend | Runtime role password | D/S/P | Yes | Secret | Active #19 |
| `DB_MIGRATION_JDBC_URL` | Backend | Flyway connection to same database/schema | D/S/P | No, defaults to runtime URL | Server config; no embedded passwords | Active #19 |
| `DB_MIGRATION_USERNAME` | Backend | Separate migration/admin role | D/S/P | Yes | Restricted server config | Active #19 |
| `DB_MIGRATION_PASSWORD` | Backend | Migration role password | D/S/P | Yes | Secret | Active #19 |
| `VITE_APP_ENV` | Web | Application environment, independent of build mode | D/S/P | Explicit S/P, optional D | Public bundle config | Active #20 |
| `VITE_API_BASE_URL` | Web | Absolute API base URL | D/S/P | Yes S/P, optional D | Public bundle config | Active #16/#20 |
| `APP_ENV` | Mobile | Compile-time application environment | D/S/P | Explicit S/P, optional D | Public app config | Active #17 |
| `API_BASE_URL` | Mobile | Absolute API base URL | D/S/P | Yes S/P, optional D | Public app config | Active #17/#20 |
| `VITE_SENTRY_DSN` | Web | HTTPS ingestion DSN; empty/absent disables SDK | D/S/P | No | Public bundle config, not an admin credential | Active #21 |
| `VITE_SENTRY_ENVIRONMENT` | Web | Must match VITE_APP_ENV; defaults to its value | D/S/P | No | Public bundle config | Active #21 |
| `SENTRY_DSN` | Mobile | Compile-time HTTPS ingestion DSN; empty/absent disables SDK | D/S/P | No | Public app config, not an admin credential | Active #21 |
| `SENTRY_ENVIRONMENT` | Mobile | Must match APP_ENV; defaults to its value | D/S/P | No | Public app config | Active #21 |

Examples of API base URLs: local `http://localhost:8080/api/v1`, Staging
`https://api.staging.example.test/api/v1`, Production
`https://api.production.example.test/api/v1`. No same-origin Production fallback.

### Reserved Firebase / FCM

Client Firebase API keys identify a project; they are not administrative service
credentials or authorization. Client config is extractable and must use provider
restrictions/security rules once the integration exists, as described in the
[Firebase API-key guidance](https://firebase.google.com/docs/projects/api-keys).
Never put service-account
contents, private keys or administrative credentials in Web/Mobile defines.

| Name | Intended consumer | Purpose | Environments | Required now | Classification | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `FIREBASE_PROJECT_ID` | Backend | Expected token issuer/project | D/S/P | No | Server config | Reserved #22/#56 |
| `GOOGLE_APPLICATION_CREDENTIALS` | Backend | Path to protected/mounted service credential JSON | D/S/P | No | Path is config; file contents are secret | Reserved #22/#56 |
| `VITE_FIREBASE_API_KEY` | Web | Firebase client project API key | D/S/P | No | Public client config | Reserved #22 |
| `VITE_FIREBASE_AUTH_DOMAIN` | Web | Client auth domain | D/S/P | No | Public client config | Reserved #22 |
| `VITE_FIREBASE_PROJECT_ID` | Web | Client project | D/S/P | No | Public client config | Reserved #22 |
| `VITE_FIREBASE_APP_ID` | Web | Client app identifier | D/S/P | No | Public client config | Reserved #22 |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | Web | FCM sender identifier | D/S/P | No | Public client config | Reserved #56 |
| `VITE_FIREBASE_VAPID_KEY` | Web | Web-push public key, never its private counterpart | D/S/P | No | Public client config | Reserved #56 |
| `FIREBASE_API_KEY` | Mobile | Firebase client project API key | D/S/P | No | Public app config | Reserved #22/#42 |
| `FIREBASE_PROJECT_ID` | Mobile | Client project | D/S/P | No | Public app config | Reserved #22/#42 |
| `FIREBASE_APP_ID` | Mobile | Platform-specific client app ID | D/S/P | No | Public app config | Reserved #22/#42 |
| `FIREBASE_MESSAGING_SENDER_ID` | Mobile | FCM sender identifier | D/S/P | No | Public app config | Reserved #56 |

These names do not mandate a future SDK provisioning strategy. Native
`google-services.json` / `GoogleService-Info.plist` are ignored pending the relevant
integration; no actual client IDs or service-account file is committed.

### Reserved R2 / Sentry / Deployment

| Name | Intended consumer | Purpose | Environments | Required now | Classification | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `R2_ACCOUNT_ID` | Backend | Storage account | D/S/P | No | Server config | Reserved #52 |
| `R2_ENDPOINT` | Backend | S3-compatible R2 endpoint | D/S/P | No | Server config | Reserved #52 |
| `R2_BUCKET` | Backend | Environment-isolated bucket | D/S/P | No | Server config | Reserved #52 |
| `R2_ACCESS_KEY_ID` | Backend | Restricted storage credential | D/S/P | No | Secret | Reserved #52 |
| `R2_SECRET_ACCESS_KEY` | Backend | Storage secret | D/S/P | No | Secret | Reserved #52 |
| `R2_PUBLIC_BASE_URL` | Backend / generated public links if approved | Optional public asset URL; not private evidence access | D/S/P | No | Public config if used | Reserved #52 |
| `SENTRY_ENVIRONMENT` | Backend only | Optional future server Sentry environment | D/S/P | No | Restricted server config | Reserved; not implemented in #21 |
| `SENTRY_DSN` | Backend only | Optional future server ingestion endpoint | D/S/P | No | Restricted server ingestion config | Reserved; not implemented in #21 |
| `SENTRY_AUTH_TOKEN` | Future release tooling only | Source-map/release administration | D/S/P | No | Secret, never client config | Reserved #64 |
| `RAILWAY_TOKEN` | Future deployment tooling only | Backend deployment authorization | S/P | No | Secret | Reserved #64 |
| `CLOUDFLARE_API_TOKEN` | Future deployment tooling only | Web deployment authorization | S/P | No | Secret | Reserved #64 |

An ingestion DSN is not an [administrative token](https://docs.sentry.io/api/auth/), but should still be protected
from abuse by future provider settings. Never put `SENTRY_AUTH_TOKEN`, R2 keys,
DB passwords or Firebase service credentials under `VITE_*` or Dart defines.
All values distributed inside browser/mobile clients can be extracted.

See [observability setup and privacy](observability.md) for the optional client
SDKs. Empty DSNs in all templates keep them disabled. Explicit Sentry environment
mismatches fail even when no DSN is provided; this catches stale environment files.
Ingestion DSNs require HTTPS, an alphanumeric public key, a numeric project ID,
and no secret-key/password, query or fragment. Validation cannot prove a DSN
belongs to the selected environment: operators must keep projects isolated.
No Sentry setting is required for backend startup.

## Supplying Configuration

### Backend

`backend/.env.example` is documentation, **not automatically loaded** by Spring.
Use its variable names to set process environment securely through the OS/IDE,
or future approved provider secrets. Do not source it as shell code: placeholders
are intentionally not shell-ready credentials. A populated `.env.local` can be
kept only as an ignored local file and supplied using the developer's own tooling.
See [backend local PostgreSQL instructions](../../backend/README.md).

For local Development, supply the five DB variables and run
`./mvnw spring-boot:run`; `local` is default. For a non-local environment, supply
its **separate** DB configuration then select exactly one profile explicitly:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'staging' # or 'production', deliberately
# Supply this environment's DB_* values securely before starting.
.\mvnw.cmd spring-boot:run
```

POSIX shells use `export SPRING_PROFILES_ACTIVE=staging` (or `production`).
These are selection examples, not deployment procedures. Afterward remove/reset
the selector and non-local values before returning to local development.

### Web

From `web/`, copy `.env.example` to ignored `.env.local`, then `npm ci` and
`npm run dev`. For Staging/Production use the reviewed example for that mode,
copying it to `.env.staging.local` or `.env.production.local` respectively. Replace
the example URL with the approved **matching** URL, never secrets. Then:

```sh
npm run build -- --mode staging
npm run build -- --mode production
```

Each copied file must explicitly contain `VITE_APP_ENV=staging` or `production`.
Changing `--mode` alone does not select an application environment. Ensure no
inherited shell/local file overrides this selection; process variables win.
The safe unconfigured CI production build is still Development-targeted.
Restart Vite after changes and rebuild to change a distributed client's config.

### Mobile

`mobile/config/*.example.json` contain only public defines. Flutter reads a file
only when explicitly passed with `--dart-define-from-file`; it does not load `.env`.
From `mobile/` copy the appropriate example to ignored `config/development.json`,
`config/staging.json` or `config/production.json`, then replace its public API URL.

```sh
flutter run -d <device-id> --dart-define-from-file=config/development.json
flutter run -d <device-id> --dart-define-from-file=config/staging.json
flutter run -d <device-id> --dart-define-from-file=config/production.json
```

Existing `--dart-define=APP_ENV=... --dart-define=API_BASE_URL=...` remains supported.
For Android emulator Development use `http://10.0.2.2:8080/api/v1`; a physical
device needs a reachable development-host URL. Native packaging/signing remains
deferred. No tokens, private keys or server secrets belong in these files.

## Isolation and Storage Policy

Staging and Production require separate PostgreSQL databases/roles, Firebase
projects, R2 buckets/credentials (separate buckets preferred), Railway backend
services/environments, Cloudflare web deployments, Sentry environments/config,
API URLs and deployment/signing credentials. Development uses local/synthetic
data and separate non-production resources when integrations later exist.
Ordinary development never requires Production access. Never copy Production
data or DB credentials onto developer machines.

Actual backend/provider secrets belong in approved secret stores: local ignored
private files/OS/IDE configuration, future Railway secrets, protected GitHub
Environments with explicit Production approval, and Cloudflare secret mechanisms.
Mobile signing keys belong in OS/signing stores or approved release secret stores.
This issue configures none of those external resources. Separate least-privilege
credentials by purpose/environment; rotate any exposed secret and remove it from
history through the approved incident process, not just from the latest commit.

## Git and Verification

Root ignore rules protect `.env`, `.env.*`, `.secrets/`, `secrets.local/`,
`credentials.local.*`, service-account JSON, native Firebase files and
keystore/key/certificate/provisioning material throughout the tree. Existing
Android/iOS generated-file rules remain unchanged. Mobile config JSON is ignored
except reviewed `*.example.json`. Safe `.env.example` / `.env.*.example` remain
versionable. Do not rename secrets to `*.example`; exemptions require review.
Ignore rules do not untrack existing files and do not prevent forced adds.

Reproduce checks in [CI documentation](ci.md). Config tests check defaults,
explicit selection, unknown/mixed environments, required values, no fallback,
safe URL structure and absence of requirements for reserved integrations. Backend
PostgreSQL/Flyway integration still runs with self-provisioned Testcontainers.
All four CI jobs require no real providers or Production secrets.

Before committing: `git diff --check`, inspect `git ls-files` for private files,
use `git check-ignore --no-index -v <local-path>` for protected paths, and review
the diff for credentials. Ignoring files is an accident-prevention layer, not a
replacement for code review or future secret scanning.

Tool behavior references: [Spring profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html),
[Vite public environment and mode precedence](https://vite.dev/guide/env-and-mode),
and the project's pinned Flutter CLI (`flutter run --help --verbose`).
