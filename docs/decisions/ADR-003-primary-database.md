# ADR-003 — PostgreSQL as Primary Database

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS has a strongly relational domain:

- tenants and customer scopes;
- drivers, tractors and trailers;
- Orders, Trips, Stops and manifests;
- many-to-many order/load relationships;
- movement events;
- audit records;
- yard checks;
- alerts.

The system needs transactions, indexing, reporting, concurrency control and reliable migrations.

## Decision

Use **PostgreSQL 18** as the primary relational database for new deployments where supported by the hosting provider.

Use **Flyway** for schema migrations.

If an infrastructure provider does not yet offer PostgreSQL 18, PostgreSQL 17 is an acceptable temporary deployment target without changing the domain design.

## Data strategy

Use a shared database for multiple SaaS tenants, with tenant-scoped records.

Every protected business record must be directly or indirectly tenant-scoped.

Do not create one database per customer/tenant for the MVP.

## Event/history strategy

Do not use full event sourcing.

Maintain:

1. efficient current-state tables/columns for operational reads; and
2. append-oriented operational/audit event history for traceability.

Examples:

- current trailer location/status for fast search;
- movement/event rows for HOOKED, DROPPED, ARRIVED, DEPARTED, CORRECTED, OOS, etc.

## Rationale

PostgreSQL fits:

- transactional consistency;
- complex relational queries;
- many-to-many modeling;
- indexing/search;
- JSONB for limited flexible metadata when appropriate;
- future geospatial extensions if truly needed.

## Consequences

### Positive

- one authoritative transactional data store;
- strong constraints and indexes;
- mature tooling;
- easy reporting;
- scalable far beyond the first design partner.

### Negative / trade-offs

- tenant filtering must be consistently enforced;
- event/history tables require retention/index planning;
- large document/photo binaries should not be stored directly in PostgreSQL.

## Follow-up

- choose ORM/query strategy;
- define tenant-isolation enforcement and whether PostgreSQL RLS is used;
- define backup/restore policy;
- define indexes after physical schema design.
