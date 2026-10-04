# ADR-021 — External Integration Boundary for Samsara and Amtech

**Status:** Proposed  
**Date:** 2026-10-04

## Context

Discovery identified:

- Samsara as the confirmed GPS/fleet system;
- Amtech as a source of Orders/BOL/customer information.

MVP can operate with manual data entry and does not require either integration.

Future integrations must not couple external schemas directly into core domain logic.

## Decision

Treat external systems as adapters outside the core domain.

Use an integration boundary/module such as:

```text
integration
├── samsara
└── amtech
```

Each adapter translates vendor-specific data into TrailerSaaS application commands/read models.

Core modules must not import vendor SDK/model classes.

## Source-of-truth rule

TrailerSaaS remains authoritative for its own operational state once a workflow is created/executed.

External source identifiers are stored as external references, not as primary keys.

Example:

```text
external_system = AMTECH
external_type = ORDER
external_id = ...
internal_order_id = UUID
```

## Idempotency

Inbound integration processing must be idempotent by external event/object identifier + Tenant.

Do not create duplicate Orders/events because a webhook/job was retried.

## Samsara

Potential future uses:

- read Tractor/Driver GPS context;
- reconcile known vehicle position;
- assist operational visibility.

Samsara GPS must not silently overwrite a dropped Trailer's location because phone/tractor GPS tracks the vehicle/Driver, not a detached Trailer.

Any automatic state effect needs a separately approved mapping/business rule.

## Amtech

Potential future uses:

- import customer/order/BOL references;
- reduce manual re-entry.

Imported Order data still passes through TrailerSaaS validation, Tenant/customer scoping and state rules.

## Credentials

Integration credentials are Tenant-specific where vendor/account ownership requires it and are stored through the secrets/config architecture.

Do not store vendor credentials in generic JSON business tables without encryption/secret-management review.

## Webhooks/jobs

When supported:

- verify webhook signatures;
- persist an idempotent receipt/integration event;
- process through background application service;
- record failures/retries.

Polling integrations follow equivalent idempotency/audit rules.

## MVP

No Samsara/Amtech implementation is required for MVP.

Architecture only preserves a clean future boundary.

## Consequences

### Positive

- vendor replacement remains possible;
- core domain stays clean;
- integrations cannot bypass state/authorization logic;
- external retries are safe.

### Negative / trade-offs

- mapping layer requires explicit work later;
- some duplicate data is intentional to preserve internal operational truth.

## Follow-up

- create integration epic only after MVP core is stable;
- obtain exact API/webhook capabilities and customer credentials;
- create provider-specific ADR/addendum when implementing each integration.
