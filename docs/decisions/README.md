# Architecture Decision Records

Architecture choices that materially affect the system are recorded here.

## Accepted decisions

- [ADR-001 — Modular Monolith Architecture](ADR-001-modular-monolith.md)
- [ADR-002 — Backend Platform: Java 25 + Spring Boot 4](ADR-002-backend-platform.md)
- [ADR-003 — PostgreSQL as Primary Database](ADR-003-primary-database.md)
- [ADR-004 — Web Frontend: React + TypeScript + Vite](ADR-004-web-frontend.md)
- [ADR-005 — Mobile Platform: Flutter with Local Offline Queue](ADR-005-mobile-platform.md)
- [ADR-006 — Persistence Access: JPA-First with Explicit Read Queries](ADR-006-persistence-access.md)
- [ADR-007 — Authentication: Firebase Authentication, Authorization in TrailerSaaS](ADR-007-authentication.md)
- [ADR-008 — Object Storage: Cloudflare R2](ADR-008-object-storage.md)
- [ADR-009 — Initial Deployment: Railway + Cloudflare](ADR-009-initial-deployment.md)
- [ADR-010 — Push Notifications: Firebase Cloud Messaging](ADR-010-notifications.md)
- [ADR-011 — Multi-Tenancy, Memberships and Authorization](ADR-011-tenancy-and-authorization.md)
- [ADR-012 — Mobile Offline Synchronization Architecture](ADR-012-mobile-offline-sync.md)

## Phase 6 accepted decisions

- [ADR-013 — PostgreSQL Row Level Security for Tenant Defense-in-Depth](ADR-013-postgresql-rls.md)
- [ADR-014 — Geofence-Assisted Arrival Without Continuous Tracking](ADR-014-geofencing-location.md)
- [ADR-015 — Observability and Operational Diagnostics](ADR-015-observability.md)
- [ADR-016 — CI/CD and Release Workflow](ADR-016-ci-cd.md)
- [ADR-017 — REST API Versioning and Compatibility](ADR-017-api-versioning.md)
- [ADR-018 — Trailer QR Identity and Resolution](ADR-018-qr-identity.md)
- [ADR-019 — Backup, Restore and Disaster Recovery Baseline](ADR-019-backup-recovery.md)
- [ADR-020 — Environment Isolation and Secrets Management](ADR-020-secrets-environments.md)
- [ADR-021 — External Integration Boundary for Samsara and Amtech](ADR-021-external-integrations.md)
- [ADR-022 — PostgreSQL-Backed Outbox and Background Jobs](ADR-022-postgresql-outbox-jobs.md)

ADR-013 through ADR-022 were reviewed and **Accepted** on 2026-10-04 as the architecture-completion package.

## Implementation adapter decisions

- [ADR-024 — Bounded foreground arrival location adapter](ADR-024-foreground-arrival-location-adapter.md), implements ADR-014 within the authorized #47 package selection.

## File naming

`ADR-###-short-title.md`

## Decision statuses

- **Proposed** — current recommendation, still open to review.
- **Accepted** — approved for implementation.
- **Superseded** — replaced by another ADR.
- **Rejected** — evaluated but not selected.

## Decision rule

An ADR must distinguish:

- context;
- requirements/constraints;
- options;
- decision;
- rationale;
- consequences;
- follow-up.

Do not label a technology as chosen in implementation documentation until the corresponding ADR is Accepted.

Future changes to an Accepted baseline require a new or superseding ADR.
