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

- [Relational data model v0.1](data-model/schema-v0.1.md)

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
- [Transport Operations Web](ux/transport-operations-web.md)
- [Road Driver Mobile](ux/road-driver-mobile.md)

## 7. Workflows

- [Core workflows](workflows/core-workflows.md)

## 8. Architecture

- [Non-functional requirements](architecture/non-functional-requirements.md)

## 9. Security

- [Tenancy and authorization](security/tenancy-and-authorization.md)
- [Permission capabilities](security/permission-capabilities.md)

Technology selection is intentionally deferred until product behavior is sufficiently stable.

## 10. Decisions

- [ADR index](decisions/README.md)
- [ADR template](decisions/ADR-000-template.md)

## Documentation status labels

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

## Next documentation milestones

1. Freeze product state machines and lifecycle semantics.
2. Approve revised domain model and core workflows.
3. Freeze MVP v0.1.
4. Create UX flow/wireframe specification for Web, Road Mode and Yard Mode.
5. Select architecture through ADRs.
6. Define database model and API contracts.
7. Convert requirements into implementation epics/issues.
8. Begin implementation with Codex.
