# TrailerSaaS

Working repository for the discovery, product definition, architecture and implementation of a SaaS platform for trailer / dry-van operational control.

> **Status:** Discovery and product-definition phase. No implementation decisions are final yet.

## Product goal

Build a multi-tenant platform for U.S. trucking / transportation companies that need reliable control of:

- trailers / boxes and their current location;
- load and delivery status;
- driver, tractor and trailer relationships;
- dispatch and work assignments;
- hook / drop events;
- arrival / departure events;
- proof of delivery and documents;
- yard checks and inventory reconciliation;
- alerts, auditability and historical movements;
- customer visibility without exposing other tenants' data.

The initial design partner currently operates more than 100 movements per day with approximately 35 drivers, 33 tractors and 150 trailers.

## Repository purpose

This repository will be the **single source of truth** for the project before and during implementation.

The objective is that, when development begins, Codex or any engineer can understand:

1. what problem is being solved;
2. how the current operation works;
3. who the users are;
4. what the business rules are;
5. what is included in the MVP;
6. what is explicitly out of scope;
7. which questions remain unresolved;
8. which architecture decisions have been approved.

## Documentation

See [docs/README.md](docs/README.md) for the documentation map.

## Current phase

### Phase 0 — Discovery
- [x] Initial operational problem identified
- [x] Requirements questionnaire answered
- [x] Initial actors identified
- [x] Initial product boundaries identified
- [ ] Validate open operational questions
- [ ] Approve MVP workflows
- [ ] Approve business rules
- [ ] Define non-functional requirements
- [ ] Select architecture and technology stack
- [ ] Produce implementation backlog
- [ ] Begin coding

## Working principles

- Do not reproduce the current Google Sheet as the domain model.
- Model business events and derive current state from those events.
- Minimize manual driver input.
- Prefer QR + lightweight location automation over continuous GPS tracking for the MVP unless discovery proves otherwise.
- Keep customer and tenant data strictly isolated.
- Preserve an auditable history of operational changes.
- Separate confirmed requirements from assumptions and future ideas.

## Language

Product and technical documentation may be written in English when useful for implementation, while discovery notes may remain in Spanish. Domain terms used by operators (BOL, hook, drop, yard check, dispatch, shipping/receiving, etc.) should be preserved where they improve clarity.
