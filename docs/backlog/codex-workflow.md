# Codex Issue Workflow

**Status:** Required implementation workflow

TrailerSaaS should be implemented issue-by-issue, not through a broad prompt such as "build the MVP".

## Before starting an Issue

Codex/implementer must:

1. read `AGENTS.md`;
2. read the Issue body and dependencies;
3. verify dependencies are completed/available;
4. read referenced Accepted docs;
5. inspect current code/tests/migrations;
6. identify any contradiction before editing.

## Branch convention

Recommended:

```text
issue-<number>-<short-slug>
```

Example:

```text
issue-22-firebase-user-auth
```

Do not implement multiple unrelated Issues in one branch.

## Pull request content

PR should contain:

- `Closes #<issue>`;
- concise behavior summary;
- requirement/ADR references where relevant;
- schema migration summary when applicable;
- tests added/changed;
- any intentional deviation requiring review.

## Required checks

During implementation keep the PR Draft and run affected tests locally. See
`../development/ci-optimization.md` for FAST/FULL policy. Finish code, focused
tests, migrations, security and documentation before publishing a final candidate
and marking Ready. Ready triggers complete CI; wait for all four suites and
the fail-closed Full CI aggregate on the current integrable revision. If red,
return to Draft before fixing and revalidate the final candidate. Do not repeatedly
run full historical suites at checkpoints. No merge authorization is implied.

Before merge:

- project-specific CI green;
- relevant unit/integration tests green;
- Flyway validation when DB touched;
- tenant/customer isolation tests when auth/data scope touched;
- OpenAPI/docs updated when HTTP contract changes;
- no secrets committed.

## Scope control

If implementation discovers a missing requirement:

- do not invent it silently;
- open/update an Issue or Open Question;
- keep the current PR within accepted scope.

## Completion

An Issue is complete only when:

- all acceptance criteria are met;
- code and tests are merged;
- documentation is consistent;
- migrations/contracts are committed when applicable.

Epics close only after all required child Issues for the MVP scope are complete.
