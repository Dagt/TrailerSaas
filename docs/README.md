# Documentation Map

This folder is the project knowledge base and implementation source of truth.

## 1. Discovery

- [Design-partner baseline](discovery/design-partner-baseline.md)
- [Initial questionnaire answer matrix](discovery/questionnaire-answer-matrix.md)
- [Follow-up questionnaire — 2026-10-03](discovery/follow-up-questionnaire-2026-10-03.md)
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

## 4. Workflows

- [Core workflows](workflows/core-workflows.md)

## 5. Architecture

- [Non-functional requirements](architecture/non-functional-requirements.md)

Technology selection is intentionally deferred until product behavior is sufficiently stable.

## 6. Decisions

- [ADR index](decisions/README.md)
- [ADR template](decisions/ADR-000-template.md)

## Documentation status labels

- **Confirmed** — directly supported by discovery or explicitly approved.
- **Proposed** — recommended product behavior awaiting validation.
- **Assumption** — temporary interpretation that must not silently become a requirement.
- **Future** — intentionally outside the MVP.
- **Open** — unresolved and tracked in `open-questions.md`.

## Current discovery state

The second discovery round resolved major questions around:

- many-to-many order/trailer relationships;
- multi-stop trips;
- drop & hook vs live load;
- wrong-trailer authorization;
- non-unique displayed trailer numbers;
- trailer content requirements;
- GPS/geofence behavior;
- customer-side dispatch;
- driver handoff;
- delivery evidence;
- yard reconciliation;
- correction permissions;
- retention preferences;
- on/off duty tracking;
- Samsara/Amtech MVP boundaries;
- destination-change audit;
- OOS workflow;
- driver road-mode and yard-mode workflows.

Remaining open questions are intentionally narrower and should be resolved before architecture is frozen.

## Next documentation milestones

1. Resolve remaining blocking open questions.
2. Approve revised domain model and core workflows.
3. Freeze MVP v0.1.
4. Create UX flow/wireframe specification.
5. Select architecture through ADRs.
6. Define database model and API contracts.
7. Convert requirements into implementation epics/issues.
8. Begin implementation with Codex.
