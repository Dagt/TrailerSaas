# ADR-019 — Backup, Restore and Disaster Recovery Baseline

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS stores operational state in PostgreSQL and evidence in Cloudflare R2.

Losing recent:

- Orders;
- Trips;
- Trailer location/coupling;
- POD/BOL metadata;
- Yard Check results

can disrupt operations.

Backups are only useful if restore is proven.

## Decision

Production must have a documented, automated two-part recovery strategy:

1. PostgreSQL database recovery.
2. R2 evidence/object recovery/verification.

## PostgreSQL target

Production database hosting must provide a recovery mechanism capable of meeting the initial target:

- **RPO target:** <= 1 hour;
- **RTO target:** <= 4 hours.

If the selected Railway/database plan cannot meet this before go-live, the production database plan/provider must be upgraded/changed.

Prefer provider-native point-in-time recovery where available.

Additionally, create scheduled encrypted logical exports for portability.

## Logical backup

Use automated `pg_dump`-compatible logical backups on a schedule appropriate to the provider/PITR setup.

Logical backups are:

- encrypted;
- stored outside the live database;
- access-controlled separately from ordinary application credentials;
- lifecycle-managed.

Do not place backup credentials in source control.

## R2 evidence

Operational evidence objects are immutable after upload in normal workflows.

Recovery controls must include:

- private bucket;
- object inventory/checksum metadata;
- restricted deletion credentials;
- controlled retention jobs;
- protection against accidental bulk deletion.

A database restore is incomplete if referenced R2 objects are unavailable.

Before production, choose and test the concrete object-recovery mechanism (for example versioning/replication/export) supported by the chosen Cloudflare setup.

## Restore drills

Before operational production use:

- perform a full PostgreSQL restore into an isolated environment;
- validate application startup/migrations;
- sample-check Trailer/Order/Trip integrity;
- verify referenced evidence can be retrieved.

Repeat restore drills periodically; proposed minimum quarterly.

A backup job that has never been restored is not considered a validated backup strategy.

## Deployment safety

Before risky production migrations:

- verify recent recovery point exists;
- ensure migration rollback/forward recovery plan;
- avoid destructive data changes in the same release unless explicitly planned.

## Incident runbook

Document:

- who initiates restore;
- provider credentials/location;
- restore steps;
- DNS/backend cutover if needed;
- verification checklist;
- communication/escalation.

## Consequences

### Positive

- explicit recovery objectives;
- portable logical backup;
- recovery includes evidence objects, not only DB.

### Negative / trade-offs

- may require higher-cost production DB plan;
- restore drills require operational effort;
- object recovery mechanism needs provider configuration.

## Follow-up

- verify selected production Railway PostgreSQL capabilities before go-live;
- select secondary logical-backup destination;
- create automated backup verification;
- write restore runbook during production-hardening epic.
