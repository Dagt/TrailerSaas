# ADR-020 — Environment Isolation and Secrets Management

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS integrates:

- PostgreSQL;
- Firebase Authentication/FCM;
- Cloudflare R2;
- Railway;
- Cloudflare web hosting;
- GitHub Actions;
- Sentry/observability.

Environment mixing or leaked credentials could expose production customer data.

## Decision

Maintain three logical environments:

- Development/local;
- Staging;
- Production.

Staging and Production must be isolated for all stateful/external resources.

## Separation requirements

Use distinct:

- PostgreSQL databases;
- Railway services/projects/environments as appropriate;
- R2 buckets or strictly isolated credentials/prefixes (separate buckets preferred);
- Firebase projects/configuration for non-production vs production;
- Sentry environments/projects/config where appropriate;
- API base URLs;
- signing/deployment credentials.

Production mobile/web configuration must never point to Staging by accident.

## Secret storage

Secrets are stored only in approved secret/config systems such as:

- Railway environment variables/secrets;
- GitHub Actions encrypted secrets / GitHub Environments;
- Cloudflare secret/config mechanisms;
- local developer secret files excluded by git;
- OS/app signing stores for mobile release.

Repository contains only safe templates such as:

`.env.example`

with variable names and documentation, no secret values.

## Secret classes

Examples:

- DB credentials;
- Firebase service credentials;
- R2 access key/secret;
- Sentry DSN/config where sensitive;
- signing keys/certificates;
- API integration secrets.

Do not log these values.

## Least privilege

Create separate credentials by purpose/environment.

Examples:

- application DB role;
- migration DB role;
- backup role;
- R2 application upload/download role;
- backup/administration credentials.

Avoid one "master" credential reused everywhere.

## Rotation

Secrets must be replaceable without code changes.

Rotate immediately on:

- suspected exposure;
- staff/access change where relevant;
- provider recommendation/incident.

Periodic rotation cadence can depend on provider/security policy, but long-lived production credentials must have documented owners.

## Developer access

Production access is not required for ordinary local development.

Use seeded/synthetic Development/Staging data.

Do not copy customer production databases into developer machines unless an approved sanitized support process exists.

## GitHub Environments

Production deployment secrets use a protected GitHub Environment with explicit deployment approval.

Staging uses a separate Environment/secrets set.

## Consequences

### Positive

- reduces accidental production exposure;
- makes CI/CD environment behavior predictable;
- supports credential rotation;
- keeps local development safe.

### Negative / trade-offs

- more configuration/resources to maintain;
- environment parity requires discipline.

## Follow-up

- create environment variable catalog during scaffold;
- create `.env.example`;
- configure GitHub Environments;
- verify no secret values exist in repository history before production.
