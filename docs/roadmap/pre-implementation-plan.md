# Pre-Implementation Roadmap

**Status:** Active  
**Date:** 2026-10-04

This roadmap describes the remaining work before Codex begins implementation.

## Phase 1 — State Machines

**Progress: Accepted v0.1 on 2026-10-04.**

Explicit lifecycle/state transitions have been documented for:

1. Order
2. Trip / Run
3. Stop / Delivery
4. Trailer
5. Driver Shift
6. Yard Check
7. Alert where needed

Each state machine must define:

- valid states;
- allowed transitions;
- actor/capability required;
- side effects;
- audit requirements;
- exception/cancellation paths;
- events emitted.

### Exit criteria

No major entity should rely on informal status changes.

Current status: the v0.1 machines are documented and Accepted under `docs/state-machines/`. Phase 2 UX/User Flows is now the active next phase.

## Phase 2 — UX / User Flows

**Progress: Accepted v0.1 on 2026-10-04.**

Define screen-level flows for:

### Web
- Tenant Admin
- Transport Operations
- Customer Dispatcher

### Mobile
- Road Driver Mode
- Yard / Spotter Mode

For each flow define:

- entry point;
- required data;
- user actions;
- validations;
- success state;
- error/exception state;
- offline behavior where relevant.

### Exit criteria

All MVP workflows have a deterministic UI flow.

Current status: Tenant Admin Web, Transport Operations Web, Customer Dispatcher Web, Road Driver Mobile and Yard / Spotter Mobile are Accepted v0.1 under `docs/ux/`. Phase 2 is closed.

## Phase 3 — Freeze Relational Schema

**Progress: Accepted v1.0 on 2026-10-04.**

Refine the relational model into a migration-ready schema.

Define:

- final tables;
- foreign keys;
- constraints;
- enum/value strategy;
- indexes;
- audit/event tables;
- tenant scoping;
- idempotency constraints;
- retention-sensitive entities.

### Exit criteria

Ready to generate Flyway migrations.

Current status: schema v1.0 is Accepted and migration-ready. Phase 3 is closed.

## Phase 4 — API Contracts

**Progress: Accepted v0.1 on 2026-10-04. Markdown contracts are approved; the OpenAPI YAML remains a derived implementation artifact to expand during scaffold.**

Define REST API contract before implementation.

Document:

- resources;
- commands/actions;
- request/response DTOs;
- pagination;
- errors;
- authorization requirements;
- idempotency;
- upload flows;
- mobile sync endpoints.

OpenAPI should become part of the implementation contract.

Current status: API conventions, identity/master data, Orders/dispatch, Driver mobile, Yard, documents/evidence, alerts/audit and the consolidated endpoint catalog are Accepted v0.1. Phase 4 is closed. `openapi-v1-draft.yaml` is a derived skeleton that will be expanded during repository scaffold without changing accepted domain behavior.

## Phase 5 — Mobile Offline Sync Protocol

**Progress: Accepted v0.1 on 2026-10-04.**

Define:

- local command queue;
- client operation IDs;
- retry rules;
- conflict handling;
- media upload retry;
- server acknowledgement;
- stale local data strategy.

### Exit criteria

Offline behavior is deterministic before mobile coding begins.

Current status: `docs/mobile/offline-sync-protocol.md`, `docs/mobile/local-offline-data-model.md` and ADR-012 are Accepted. Phase 5 is closed.

## Phase 6 — Architecture Completion

**Progress: Draft complete — ADR-013 through ADR-022 created; pending acceptance.**

Finalize remaining ADRs:

- PostgreSQL RLS / tenant defense-in-depth;
- sync protocol;
- geofence implementation;
- observability;
- CI/CD;
- API conventions/versioning;
- QR format;
- backups/restore;
- secrets/environments;
- external integration boundary;
- durable background jobs/outbox.

### Current status

ADR-012 is Accepted. ADR-013 through ADR-022 form the proposed architecture-completion package. Once accepted, Phase 6 closes and Phase 7 becomes active.

## Phase 7 — Implementation Backlog

Convert approved specification into:

- Epics
- GitHub Issues
- dependencies
- acceptance criteria
- requirement IDs
- implementation order

Suggested top-level epics:

1. Platform foundation
2. Identity / tenancy / authorization
3. Master data
4. Orders / manifests
5. Trips / dispatch
6. Trailer movement tracking
7. Driver mobile
8. Yard mode
9. Yard check
10. Documents / evidence
11. Alerts / notifications
12. Customer portal
13. Audit / history
14. Production hardening

## Phase 8 — Repository Scaffold

Only after the specification above is stable:

- create backend project;
- create web project;
- create Flutter project;
- configure CI;
- configure environments;
- add baseline migrations;
- add architectural tests;
- add security test harness.

## Phase 9 — MVP Implementation

Recommended implementation order:

1. tenancy/auth foundation;
2. customers/sites;
3. drivers/tractors/trailers;
4. Orders/manifests;
5. Trips/Stops;
6. movement/event model;
7. basic Web operations;
8. Driver Road Mode;
9. documents/POD;
10. Yard Mode/Yard Check;
11. alerts;
12. Customer portal;
13. production hardening.

## Rule

Do not ask Codex to "build TrailerSaaS" from the full repository at once.

Implementation should be issue-driven and incremental, with each issue pointing to approved requirements, state machines, API contracts and acceptance criteria.
