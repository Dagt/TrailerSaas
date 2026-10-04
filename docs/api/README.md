# API Contracts v1

**Status:** Proposed v0.1  
**Base style:** REST/JSON  
**Prefix:** `/api/v1`

This section translates the Accepted requirements, state machines, UX flows and schema v1.0 into implementation-facing HTTP contracts.

## Contract documents

- [API conventions](conventions.md)
- [Identity and master data](identity-master-data.md)
- [Orders and dispatch](orders-dispatch.md)
- [Mobile execution](mobile-execution.md)
- [Yard operations](yard.md)
- [Documents and evidence](documents.md)
- [Alerts and audit](alerts-audit.md)
- [Endpoint catalog](endpoint-catalog.md)
- [OpenAPI v1 draft skeleton](openapi-v1-draft.yaml)

## Contract principles

1. APIs expose **commands/actions**, not arbitrary status mutation.
2. Tenant context is explicit and always re-authorized server-side.
3. Customer scope is enforced server-side.
4. State-machine rules are authoritative.
5. Critical mobile commands are idempotent.
6. Mutable aggregates use optimistic concurrency.
7. Binary files use R2 upload/download URLs; API stores/authorizes metadata.
8. API responses may expose current projections plus links/IDs to history, but current state never erases operational events.
9. Internal database entities are not serialized directly as JPA entities.
10. OpenAPI will be generated from these accepted contracts before implementation.

## Tenant-scoped path

Tenant-owned resources use:

```text
/api/v1/tenants/{tenantId}/...
```

`tenantId` is a requested context, **not an authorization credential**. The backend derives the authenticated User from Firebase and verifies an active TenantMembership before accessing any resource.

## Current phase

The markdown contracts and initial OpenAPI skeleton are the Phase 4 draft. After review/acceptance, the OpenAPI document should be expanded into the authoritative implementation contract with operation-specific request/response schemas.
