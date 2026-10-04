# ADR-017 — REST API Versioning and Compatibility

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS has:

- web and mobile clients;
- offline mobile commands that may be replayed after delay;
- OpenAPI-driven contracts;
- a backend that will evolve while older mobile app versions may still exist.

Breaking API changes therefore need deliberate compatibility rules.

## Decision

Use path-based major versioning:

```text
/api/v1/...
```

The accepted API v0.1 markdown contracts define the behavior that will become API major version `v1`.

## Compatibility rules inside v1

Allowed when backward-compatible:

- add optional response fields;
- add optional request fields with safe defaults;
- add new endpoints;
- add new enum values only when clients are designed to tolerate unknown values or compatibility is otherwise proven;
- add filters/query parameters.

Avoid/consider breaking:

- renaming/removing fields;
- changing field meaning;
- making optional request fields required;
- changing state semantics;
- changing endpoint behavior incompatibly;
- reusing an enum/value with different meaning.

A breaking contract change requires either:

- a compatibility bridge within v1; or
- a new major API path such as `/api/v2`.

## Mobile compatibility

Because mobile releases cannot be forced instantly:

- backend should support the currently supported production mobile version during rollout;
- breaking server changes must not be deployed before a compatible mobile migration strategy exists;
- offline commands captured by an older supported app must remain interpretable while that app version is supported.

## OpenAPI

The OpenAPI specification becomes the machine-readable source of truth for HTTP schema once expanded during scaffold.

Markdown API docs remain the behavioral/rationale source.

CI should validate:

- OpenAPI syntax;
- generated/client compatibility as introduced;
- accidental breaking changes where tooling supports it.

## Deprecation

When an endpoint/field is deprecated:

1. mark it deprecated in OpenAPI/docs;
2. instrument usage if practical;
3. define a removal version/date;
4. remove only in a compatible major-version process.

## Consequences

### Positive

- simple predictable URL/version model;
- protects mobile/offline compatibility;
- OpenAPI can generate DTO/client contracts.

### Negative / trade-offs

- temporary compatibility code may remain during migrations;
- multiple major versions may eventually coexist.

## Follow-up

- expand `openapi-v1-draft.yaml`;
- add API compatibility checks to CI;
- define supported-mobile-version policy before production.
