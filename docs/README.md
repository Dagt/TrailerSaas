# Documentation Map

This folder is the project knowledge base.

## 1. Discovery

- [Design-partner baseline](discovery/design-partner-baseline.md)
- [Questionnaire answer matrix](discovery/questionnaire-answer-matrix.md)

These files describe how the current operation works. They are evidence, not necessarily the final product design.

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

Documents may use:

- **Confirmed** — directly supported by discovery or explicitly approved.
- **Proposed** — recommended product behavior awaiting validation.
- **Assumption** — temporary interpretation that must not silently become a requirement.
- **Future** — intentionally outside the MVP.
- **Open** — unresolved and tracked in `open-questions.md`.

## Next documentation milestones

1. Resolve blocking open questions.
2. Approve core workflows.
3. Freeze MVP v0.1.
4. Select architecture through ADRs.
5. Define API boundaries and data model.
6. Convert requirements into implementation epics/issues.
7. Begin implementation.
