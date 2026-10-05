# CI Quality Gates

Issue #18 implements the initial checks from ADR-016/017 in
[`ci.yml`](../../.github/workflows/ci.yml). It runs on every pull request targeting
`main` and on pushes to `main`. There are no path filters: documentation-only
changes also receive all four checks. New commits cancel superseded runs on the
same ref.

## Jobs

| Check name | Runtime | Checks |
| --- | --- | --- |
| Backend | Temurin Java 25, Maven Wrapper 3.9.11 | `clean verify`, including startup/health tests and Spring Modulith boundaries |
| Web | Node 24 | `npm ci`, typecheck, lint, tests, production build |
| Mobile | Flutter from `mobile/.flutter-version`, bundled Dart | locked pub install, Drift generation consistency, format, analyze, unit/widget tests |
| OpenAPI | Node 24, locked Redocly CLI | YAML parsing, OpenAPI structure, reference resolution, validator regression tests |

All jobs use GitHub-hosted `ubuntu-24.04` independently. Native Android/iOS
build validation is intentionally deferred. Later issues can add Android and
macOS/iOS jobs alongside the platform-independent Mobile check without changing
the other jobs. There is no release signing.

The stable check names above can be required in repository branch protection.
The workflow reports failed checks but does **not** configure repository rules:
maintainers must require them on `main` after verifying the initial run.

## Reproduce Locally

From the repository root, with JDK 25, Node 24 and the project's Flutter version:

```sh
cd backend
./mvnw -B -ntp clean verify
cd ../web
npm ci
npm run typecheck
npm run lint
npm test
npm run build
cd ../mobile
flutter pub get --enforce-lockfile
dart run build_runner build
git diff --exit-code -- lib/src/persistence/app_database.g.dart
dart format --output=none --set-exit-if-changed lib test
flutter analyze --fatal-infos
flutter test --reporter expanded
cd ../tools/openapi
npm ci
npm run validate
npm test
```

In PowerShell use `.\mvnw.cmd` and `npm.cmd` where execution policy requires
them. Set `$env:REDOCLY_TELEMETRY = 'off'` (or `export REDOCLY_TELEMETRY=off`
in a POSIX shell) to reproduce CI's telemetry setting.

Drift code is committed; regeneration must not change it. Review and commit
intentional schema/code changes together before running that diff check.
No API server, external database or cloud service is required.

## OpenAPI Tooling

`tools/openapi/` is a private development-tool package, separate from Web.
`@redocly/cli` is fixed to **2.58.1** with a committed npm lockfile; validation
uses its local binary, never a global installation or unversioned `npx`.

The minimal `redocly.yaml` configuration enables `struct` and
`no-unresolved-refs` as errors, with no inherited design/style rules. This
validates the accepted OpenAPI 3.1 draft without requiring production server
URLs, operation summaries or other new contract content.

`npm test` executes the same CLI/configuration against the accepted document
and isolated temporary invalid documents. It checks nonzero exits and relevant
diagnostics for malformed YAML, missing required OpenAPI metadata, an invalid
nested response and an unresolved reference. The real contract is never mutated.

Compatibility/breaking-change checks and generated clients are not introduced
here; those can be added when the contracts/clients evolve under ADR-017.

## Caching and Security

- `actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7`
  and the established `subosito/flutter-action@v2` use explicit major versions.
- Maven caches dependencies using `backend/pom.xml`.
- Web and OpenAPI have separate npm cache keys based on their own lockfiles;
  npm caches downloaded packages, not `node_modules`.
- Flutter caches the versioned SDK and pub packages. The pub key includes
  OS/architecture, Flutter version/channel and `mobile/pubspec.lock`.
- Install/check steps always run, including on cache hits. Empty caches remain
  correct. No workspace builds, credentials or operational data are cached.
- Workflow permissions are `contents: read`; checkout does not persist its
  temporary credentials. Use `pull_request`, not `pull_request_target`.
- No repository/Production secrets, GitHub Environments, deployments, external
  resource changes or Railway/Cloudflare/Firebase/R2/database credentials are used.
  Fork PRs can run subject to GitHub's normal contributor-approval policy.
- Current backend checks have no PostgreSQL/Flyway/Testcontainers; #19 owns that
  baseline. Environments/secrets and observability remain #20/#21.

Web's existing lockfile required a metadata/placement correction for ESLint's
transitive `ajv` dependency so `npm ci` could run cleanly. No declared
dependency versions or application behavior were changed.
