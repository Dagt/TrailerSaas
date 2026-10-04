# ADR-013 — PostgreSQL Row Level Security for Tenant Defense-in-Depth

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS is a shared-database multi-tenant SaaS. Application authorization is already defined as the primary security layer, and schema v1.0 uses explicit `tenant_id` columns plus tenant-safe foreign keys.

A second database-level isolation layer can reduce the impact of accidental missing tenant predicates.

The main risk with PostgreSQL RLS in a pooled Spring application is incorrectly scoped session state leaking between requests.

## Decision

Use **PostgreSQL Row Level Security (RLS)** as defense-in-depth for tenant-owned production tables.

Application authorization remains mandatory and authoritative.

The runtime application database role:

- must not be superuser;
- must not have `BYPASSRLS`;
- is subject to tenant policies.

The migration/administration role is separate and may own schema objects / bypass policies as required for migrations and controlled maintenance.

## Tenant context mechanism

At the start of every tenant-scoped database transaction, set a transaction-local PostgreSQL setting:

```sql
SET LOCAL app.current_tenant_id = '<tenant-uuid>';
```

Policies resolve the current tenant through `current_setting(..., true)`.

Conceptual policy:

```sql
USING (
  tenant_id = current_setting('app.current_tenant_id', true)::uuid
)
WITH CHECK (
  tenant_id = current_setting('app.current_tenant_id', true)::uuid
)
```

Use **transaction-local** context rather than persistent session-level `SET` so pooled connections do not retain tenant state after the transaction.

## Transaction rule

All tenant-owned repository access must execute inside an explicit transaction carrying TenantContext.

Do not open repository calls first and set tenant context later.

## Background jobs

Tenant-wide scheduled work must execute **one Tenant at a time**, establishing TenantContext for each transaction.

Examples:

- stale Trailer detection;
- retention jobs;
- alert processing.

Do not give normal background workers a global `BYPASSRLS` role merely for convenience.

Cross-tenant platform administration, if later required, uses a separate tightly controlled administrative path/role.

## Tables

RLS should cover tenant-owned operational tables including at minimum:

- tenant_memberships / role_assignments;
- customers / sites / locations;
- drivers / tractors / trailers;
- Orders / manifests;
- Trips / Stops;
- movement/history;
- shifts / inspections / fuel;
- documents metadata/links;
- Yard Checks;
- alerts;
- audit;
- processed client operations.

Global `users` and `tenants` require policy design according to access pattern and should not be forced into the same policy blindly.

## Customer scope

RLS initially enforces **Tenant isolation**, not every business permission.

Customer-level scope remains application authorization because:

- roles/capabilities are dynamic;
- resource relationships vary;
- expressing all capability logic in RLS would duplicate domain authorization.

Customer-scope tests remain mandatory.

## Testing

Required integration tests with real PostgreSQL/Testcontainers:

1. Tenant A query cannot read Tenant B rows even if repository query omits tenant predicate.
2. Tenant A INSERT cannot create a Tenant B row.
3. Tenant context is not retained after transaction completion.
4. connection reuse between Tenant A and Tenant B does not leak state.
5. background per-Tenant jobs cannot cross boundaries.
6. migration role behavior is separate from application role.

## Consequences

### Positive

- second isolation barrier;
- protects against classes of missing tenant-filter bugs;
- complements tenant-safe FKs;
- no customer-role logic duplicated into the database.

### Negative / trade-offs

- every tenant DB transaction must establish context correctly;
- transaction/pooling tests become critical;
- troubleshooting queries require awareness of RLS;
- migrations need a distinct DB role.

## Follow-up

- implement a Spring TenantContext transaction hook/interceptor;
- add Testcontainers RLS isolation test suite;
- define exact global-table policies during Flyway implementation.
