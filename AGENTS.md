# AGENTS.md

## Purpose

This file defines the working rules for Codex and any automated coding agent operating in this repository.

## Current project phase

The repository is ready for **issue-driven implementation**. Discovery, ADR-001 through ADR-022, state machines, UX flows, schema v1.0, API v0.1 and mobile offline sync are accepted. The MVP implementation backlog is tracked in GitHub issues #1–#66. Implement only from a bounded child Issue and do not infer that an undocumented feature is approved simply because it seems useful.

Before implementing a feature, read:

1. `README.md`
2. `docs/README.md`
3. `docs/product/mvp-scope.md`
4. `docs/product/requirements-catalog.md`
5. `docs/product/business-rules.md`
6. `docs/product/open-questions.md`
7. relevant ADRs under `docs/decisions/`
8. accepted state machines under `docs/state-machines/`
9. accepted UX flows under `docs/ux/`
10. accepted schema/API contracts under `docs/data-model/` and `docs/api/`
11. mobile sync specification under `docs/mobile/` when working on mobile behavior
12. `docs/backlog/README.md` and the specific GitHub Issue being implemented

## Source-of-truth priority

When documents conflict, use this priority:

1. Accepted ADRs
2. Approved requirements / business rules
3. Approved workflow documentation
4. MVP scope
5. Discovery notes
6. Assumptions / ideas

Do not silently resolve contradictions. Surface them.

## Implementation rules

- Treat the product as a multi-tenant SaaS.
- Tenant isolation is mandatory.
- Operational history must be auditable.
- Do not model the existing spreadsheet literally.
- Separate trailer load state, operational state, location and usage/purpose.
- Prefer event-driven operational history over destructive overwrites.
- Mobile workflows must minimize typing.
- Any offline-capable action must be designed for retry and idempotency.
- Do not add continuous GPS tracking unless an approved requirement or ADR explicitly calls for it.
- Do not add payroll, accounting, ELD, routing optimization or full fleet-maintenance modules to the MVP unless the scope document changes.
- Do not introduce a vendor or framework without documenting the decision in an ADR when it materially affects architecture.

## Coding phase checklist

For every implementation Issue:

- work from a child GitHub Issue (#15–#65), not directly from an Epic;
- include requirement IDs in PR descriptions where practical;
- add or update tests with behavior changes;
- update documentation when business behavior changes;
- avoid schema changes without migration strategy;
- preserve audit history;
- never trust client-supplied tenant identifiers for authorization;
- make background/mobile retries idempotent.

## Documentation conventions

- Functional requirements: `FR-###`
- Non-functional requirements: `NFR-###`
- Business rules: `BR-###`
- Open questions: `OQ-###`
- Architecture decisions: `ADR-###`

## Branch / PR workflow

Follow `docs/backlog/codex-workflow.md`. Keep one bounded Issue per branch/PR unless the Issue explicitly requires otherwise.

## Important

If a requirement is ambiguous, stop and reference the corresponding open question rather than inventing behavior.
