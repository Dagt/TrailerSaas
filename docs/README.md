# Documentation Map

This folder is the project knowledge base and implementation source of truth.

## 1. Discovery

- [Design-partner baseline](discovery/design-partner-baseline.md)
- [Initial questionnaire answer matrix](discovery/questionnaire-answer-matrix.md)
- [Follow-up questionnaire — 2026-10-03](discovery/follow-up-questionnaire-2026-10-03.md)
- [Final questionnaire — 2026-10-04](discovery/final-questionnaire-2026-10-04.md)
- [Design-partner facilities and docks](discovery/design-partner-facilities.md)

These files describe the current real-world operation. They are evidence, not necessarily the final generic SaaS design.

## 2. Product

- [Product vision](product/product-vision.md)
- [Actors and permissions](product/actors-and-permissions.md)
- [MVP scope](product/mvp-scope.md)
- [Requirements catalog](product/requirements-catalog.md)
- [Business rules](product/business-rules.md)
- [Open questions](product/open-questions.md)

## 3. Domain

- [Domain model](domain/domain-model.md)

## 4. Data Model

- [Relational data model v0.1](data-model/schema-v0.1.md) — legacy draft
- [Relational data model v1.0](data-model/schema-v1.0.md) — Accepted

## 5. State Machines

- [State machine index](state-machines/README.md)
- [Order](state-machines/order.md)
- [Trip / Run](state-machines/trip.md)
- [Stop / Delivery](state-machines/stop.md)
- [Trailer](state-machines/trailer.md)
- [Driver Shift](state-machines/shift.md)
- [Yard Check](state-machines/yard-check.md)
- [Alert](state-machines/alert.md)

## 6. UX / User Flows

- [UX flow index](ux/README.md)
- [Tenant Admin Web](ux/tenant-admin-web.md)
- [Transport Operations Web](ux/transport-operations-web.md)
- [Customer Dispatcher Web](ux/customer-dispatcher-web.md)
- [Road Driver Mobile](ux/road-driver-mobile.md)
- [Yard / Spotter Mobile](ux/yard-spotter-mobile.md)

## 7. Workflows

- [Core workflows](workflows/core-workflows.md)

## 8. API

- [API contract index](api/README.md)
- [API conventions](api/conventions.md)
- [Endpoint catalog](api/endpoint-catalog.md)
- [OpenAPI v1 draft](api/openapi-v1-draft.yaml)

## 9. Mobile

- [Offline sync protocol](mobile/offline-sync-protocol.md)
- [Local offline data model](mobile/local-offline-data-model.md)

## 10. Architecture

- [Non-functional requirements](architecture/non-functional-requirements.md)

## 11. Security

- [Tenancy and authorization](security/tenancy-and-authorization.md)
- [Permission capabilities](security/permission-capabilities.md)

Baseline technology and architecture decisions ADR-001 through ADR-022 are accepted under `docs/decisions/`.

## 12. Decisions

- [ADR index](decisions/README.md)
- [ADR template](decisions/ADR-000-template.md)

## 13. Backlog

- [MVP implementation backlog](backlog/README.md)
- [Implementation order](backlog/implementation-order.md)
- [Codex issue workflow](backlog/codex-workflow.md)

## Documentation status labels

- **Accepted** — approved as an implementation baseline; changes require explicit review/update.
- **Confirmed** — directly supported by discovery or explicitly approved.
- **Proposed** — recommended product behavior awaiting validation.
- **Assumption** — temporary interpretation that must not silently become a requirement.
- **Future** — intentionally outside the MVP.
- **Open** — unresolved and tracked in `open-questions.md`.

## Current discovery state

The three planned discovery rounds are complete.

Core functional behavior is now sufficiently defined to move from requirements elicitation into product specification and architecture. The final round clarified:

- customer vs 2 Fast Logistics responsibilities;
- customer edit/cancel behavior;
- delivery vs trip vs shift completion;
- cancellation after departure;
- required manifest/cargo fields;
- Pre-Trip and Post-Trip capture;
- lightweight fuel logging;
- unsigned-BOL exception evidence;
- one common POD rule for MVP;
- persistent customer designation of trailers;
- current design-partner trailer-number uniqueness;
- missing-trailer alerts and last-known-location behavior.

Remaining open items are mostly product-policy, compliance, UX-content or architecture decisions rather than gaps in understanding the operating workflow.

## Current implementation milestone

The specification and backlog phases are complete. GitHub tracking issue #66 links the 14 MVP Epics, and `docs/backlog/implementation-order.md` defines the dependency waves for issue-driven implementation.
