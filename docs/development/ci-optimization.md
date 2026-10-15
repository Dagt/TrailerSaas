# Draft FAST / Ready FULL validation

Draft PRs run only the dependency-free **CI Policy** tests. Backend/Mobile/Web/
OpenAPI names remain unchanged but those suites are deferred. **Full CI** is a
separate fail-closed aggregate: it deliberately reports failure on Draft (not a
test failure), since GitHub treats individually skipped jobs as successful checks.
Draft results are not merge approval.

Implementers run affected tests locally: Maven `-Dtest=ConcreteTests test`,
`flutter test test/path_test.dart`, Web `npm test -- path.test.tsx`, and OpenAPI
validation/tests when contracts change. Expand shared auth/RLS/queue regressions
when those boundaries change. Do not run full suites after each checkpoint.

Only once code, focused regressions, security, migrations and docs are complete,
push a clean candidate and mark Ready. Official PR events opened/synchronize/
reopened/ready_for_review/edited run the four independent full jobs when not
Draft. converted_to_draft invalidates the aggregate and cancels obsolete runs.
edited includes base retargeting; it may also revalidate a Ready description edit.
Finish PR-body edits before Ready to avoid unnecessary duplicate full runs.
Concurrency retains PR-ref grouping/cancellation. Checkout keeps GitHub's current
test merge ref. Verify its parents and tree against latest head/base before review.
On a full failure, return to Draft, preserve logs, fix with focused tests, publish
the new candidate, then return Ready. Never reuse an earlier head's green results.

Existing Maven/npm/Flutter caches remain lock/version keyed. Mobile's duplicated
individual session test commands are removed: both unchanged test files are
discovered by the full `flutter test` command; policy tests verify they remain.
The final full log must also show these files executed. No tests are disabled.
Full Backend still runs clean verify/PostgreSQL; no fake artifact reuse is added.

## Owner setup / enforcement boundary

Keep required checks **Backend, Mobile, Web, OpenAPI**, and additionally require
**Full CI** from GitHub Actions. Require PRs, latest-branch/up-to-date validation,
block direct/force pushes and deletion, apply to administrators, and allow no
bypasses. A skipped/neutral individual check is insufficient: Full CI requires
actual successful results for all four jobs plus policy in that run. Main push
retains full validation because protection cannot currently be verified.

This integration's branch-protection read returned 403 (administration denied).
Rulesets read returned 403 (private-repository plan requires Pro/public). The
owner must enable a supported plan and configure Settings > Branches/rulesets >
main with the requirements above; then inspect required-check names after their
first run. Until then this repository cannot claim enforced merge protection.
No protection was disabled/changed. Technical workflow checks alone cannot
prevent an owner/admin manually merging an unprotected branch.

References: [GitHub status checks](https://docs.github.com/en/pull-requests/reference/status-checks),
[required checks and current test merge](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).
Policy tests are deterministic simulations, not evidence that real GitHub events
fired. Actual Draft/Ready run IDs and final counts belong in the PR. No fabricated
time/minute savings or physical native QA is reported.
