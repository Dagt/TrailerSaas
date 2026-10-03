# Architecture Decision Records

Architecture choices that materially affect the system should be recorded here.

## Status

No architecture/technology ADR has been accepted yet.

This is intentional: product behavior is still being validated.

## Planned decisions

Likely ADRs include:

- application architecture / monolith vs service boundaries;
- backend language/framework;
- web frontend;
- mobile approach (native/cross-platform);
- database;
- authentication/authorization;
- object/file storage;
- event/current-state strategy;
- offline synchronization;
- geofencing/background location;
- hosting/deployment;
- notifications;
- observability;
- multi-tenant data-isolation strategy;
- QR format/identity approach;
- integration boundary for Samsara/Amtech.

## File naming

`ADR-###-short-title.md`

Example:

`ADR-001-multi-tenant-data-isolation.md`

## Decision rule

An ADR should clearly distinguish:

- context;
- options;
- decision;
- consequences;
- unresolved follow-up.

Do not label a technology as “chosen” in other documentation until the corresponding ADR is accepted.
