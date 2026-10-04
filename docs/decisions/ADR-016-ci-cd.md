# ADR-016 — CI/CD and Release Workflow

**Status:** Accepted  
**Date:** 2026-10-04

## Context

TrailerSaaS will contain:

- Java/Spring backend;
- React/TypeScript web app;
- Flutter mobile app;
- Flyway database migrations;
- OpenAPI contract.

The project is initially maintained by a small team/solo developer with Codex assistance, so automation must protect `main` without creating enterprise-scale process overhead.

## Decision

Use **GitHub Actions** for CI and deployment orchestration.

Use protected `main` as the integration branch.

Normal implementation work occurs through feature branches + pull requests once coding begins.

## Pull request checks

At minimum:

### Backend

- compile;
- unit tests;
- Testcontainers integration tests;
- architecture/module-boundary tests;
- Flyway migration validation.

### Web

- TypeScript check;
- lint;
- tests;
- production build.

### Mobile

- Dart/Flutter analyze;
- unit/widget tests;
- build validation appropriate to runner/platform.

### Contract

- validate OpenAPI syntax;
- fail on accidental incompatible contract/schema changes when compatibility tooling is introduced.

## Deployment environments

Use three logical environments:

- Development/local;
- Staging;
- Production.

Staging and Production use separate:

- databases;
- R2 buckets/prefixes/credentials;
- Firebase projects or isolated environment configuration;
- backend secrets;
- URLs.

Do not point Staging clients at Production data.

## Backend deployment

Build a reproducible Docker image for Spring Boot.

Deploy Staging from accepted main commits.

Production deploy is manually approved/gated from a known commit/image.

Do not rebuild different source for Production after Staging validation; promote the same artifact/image when practical.

## Web deployment

Build React/Vite from the same source commit.

Deploy to separate Cloudflare environments/projects/domains for Staging and Production.

## Database migrations

Flyway migrations run as a controlled deployment step.

Rules:

- migration failure blocks application release;
- migrations are forward-only under normal development;
- destructive schema/data changes require explicit rollout strategy;
- backups/restore readiness required before risky production migrations.

## Mobile release

Mobile app-store releases are not automatic on every merge.

CI builds/tests continuously; signed App Store / Play production release remains an explicit release action.

## Branch protection

Once scaffold exists, protect `main` with:

- required CI checks;
- no force pushes;
- PR review/approval policy appropriate to team size;
- signed/verified provenance where practical.

## Consequences

### Positive

- repeatable quality gates;
- clear Staging/Production separation;
- same backend artifact can be promoted;
- suited to Codex issue/PR workflow.

### Negative / trade-offs

- iOS build/release requires macOS runner/Xcode;
- release secrets require careful GitHub Environment permissions;
- migrations must be coordinated with application compatibility.

## Follow-up

- create workflow files during repository scaffold;
- configure GitHub Environments;
- add branch protection after first CI workflows are green;
- document emergency rollback/redeploy process.
