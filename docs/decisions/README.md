# Architecture Decision Records

Architecture choices that materially affect the system are recorded here.

## Proposed decisions

- [ADR-001 — Modular Monolith Architecture](ADR-001-modular-monolith.md)
- [ADR-002 — Backend Platform: Java 25 + Spring Boot 4](ADR-002-backend-platform.md)
- [ADR-003 — PostgreSQL as Primary Database](ADR-003-primary-database.md)
- [ADR-004 — Web Frontend: React + TypeScript + Vite](ADR-004-web-frontend.md)
- [ADR-005 — Mobile Platform: Flutter with Local Offline Queue](ADR-005-mobile-platform.md)

These are currently **Proposed**, not yet marked Accepted. The intent is to review them against the final product requirements before implementation begins.

## Architecture decisions still to make

Likely next ADRs:

- persistence/query strategy (JPA vs jOOQ vs hybrid);
- authentication / identity provider;
- multi-tenant enforcement and PostgreSQL RLS;
- object/file storage;
- offline synchronization protocol;
- geofencing/background location implementation;
- hosting/deployment;
- push notifications;
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
