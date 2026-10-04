# ADR-006 — Persistence Access: JPA-First with Explicit Read Queries

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS has a transactional relational domain with many standard CRUD operations plus some read-heavy operational views:

- trailer current state;
- dispatch boards;
- multi-stop trips;
- yard inventory;
- audit/event history;
- dashboard filters.

The project should avoid premature complexity while preserving the ability to optimize SQL-heavy read models.

## Decision

Use **Spring Data JPA / Hibernate** as the default persistence mechanism for transactional write-side domain data and ordinary CRUD.

Use:

- explicit DTO/projection queries for read models;
- PostgreSQL-native SQL when a query is clearer or substantially more efficient;
- **jOOQ may be introduced selectively** for complex reporting/read-side queries if justified.

Do not introduce both JPA and jOOQ across every module from day one.

Use **Flyway** as the only schema migration authority.

Use **Testcontainers with PostgreSQL** for persistence/integration tests rather than relying on H2 behavior.

## Rationale

- JPA keeps MVP implementation velocity high.
- The domain contains enough relationships and transactions to benefit from ORM support.
- Explicit query projections avoid loading large entity graphs for dashboards.
- jOOQ remains available for complex SQL without forcing two persistence paradigms everywhere.

## Guardrails

- Do not expose JPA entities directly through API responses.
- Avoid unbounded lazy-loading from controllers.
- Avoid automatic many-to-many mappings where an explicit domain association has business data.
- Every tenant-scoped query must enforce tenant scope.
- Use pagination for unbounded list/history endpoints.
- Analyze N+1 behavior in integration tests/profiling.

## Consequences

### Positive

- lower initial complexity;
- familiar Spring ecosystem;
- strong transaction integration;
- room for optimized SQL later.

### Negative / trade-offs

- Hibernate behavior must be understood and tested;
- complex analytics queries may become awkward and need a dedicated read approach later.

## Follow-up

- define repository boundaries by module;
- define API DTO mapping conventions;
- define jOOQ adoption criteria.
