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

These decisions were reviewed and **Accepted** on 2026-10-04 as the baseline architecture for TrailerSaaS. Future changes must be recorded through new or superseding ADRs.

## Architecture decisions still to make

Likely next ADRs:

- multi-tenant enforcement and PostgreSQL RLS;
- offline synchronization protocol;
- geofencing/background location implementation;
- observability;
- CI/CD;
- QR format/identity approach;
- API conventions and versioning;
- integration boundary for Samsara/Amtech.

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
