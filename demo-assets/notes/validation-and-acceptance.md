# UX-08 validation and acceptance

Review date: **October 22, 2026**, America/Mexico_City. Issue #125, Epic #117.
Branch: `issue-125-commercial-demo-assets`. Base/capture application revision:
`fa0898e12cbdc358ea7cd7119c76a1e3a11e248c` (merged UX-07).

This is an additive commercial-assets PR. The complete `web` and `mobile` Git
trees remain identical to that capture/test revision; the package verifier checks
the identity against the final checkout. Backend, OpenAPI, auth, authorization,
query/cache behavior, operational state, Drift and offline implementation are
unchanged. No old test is edited or removed. Final PR HEAD is recorded in the PR
description after commit; image provenance retains its actual capture commit.

## Local results

| Gate | Result | Actual evidence / limit |
|---|---|---|
| Locked Web install | PASS | `npm ci`; lockfile unchanged |
| TypeScript | PASS | `npm run typecheck` |
| ESLint | PASS | `npm run lint` (`--max-warnings 0`) |
| Complete Web suite | PASS | `npm test -- --maxWorkers=1 --reporter=default --reporter=json ...`: **668 passed, 0 failed, 0 skipped; 33 files** |
| Production Web build | PASS | `npm run build`; no application/dependency changes |
| Local CI policy | PASS | `node --test .github/scripts/ci-policy.test.mjs`: **12 passed, 0 failed/skipped** |
| Real-browser regression | PASS | Existing `web/scripts/issue124-browser-regression.cjs`: **22 checks**, **26 review captures**, 0 page/console errors; modal focus/keyboard, enum labels/filters, 390/768/1280/1440/1920 and reduced-motion scenarios as enumerated in report |
| Commercial Web capture | PASS | **29 PNGs** from real React routes; every capture checks document width and demo disclosure; 0 page errors. 390×844, 768×1024 and 1440×900 assets |
| Production route isolation | PASS | **8 routes**, including `/dev/*.html`, on built preview show no synthetic demo UI; normal protected routes retain real unconfigured authentication |
| Flutter locked dependencies / generation | PASS | `flutter pub get --enforce-lockfile`; `dart run build_runner build`; byte-identical lockfile and generated Drift file |
| Dart format / analysis | PASS | `dart format --output=none --set-exit-if-changed lib test`; `flutter analyze --fatal-infos`: no issues |
| Complete Flutter suite | PASS | `flutter test --reporter expanded --file-reporter json:...`: **672 passed, 0 failed/skipped** |
| Native Android build / evidence | PASS | Explicit debug review target/define; **6 actual emulator PNGs**, installed APK SHA-256 verified on every capture. Yard review dismissed with Not Yet; no confirmed movement. Native QR screen reports camera unavailable; no scan result or authority inferred |
| Native reviewed log | PASS within scope | No `E/flutter`, application fatal exception, app ANR or rejected-write marker in the inspected capture-session log. A UIAutomator tooling process failed during overlapping dumps (`UiAutomationService ... already registered`); sequential retry succeeded. Build-tool deprecation and SDK XML warnings retained |
| Presentation | PASS | **10 editable slides**, speaker notes and original-aspect screenshot images; Artifact Tool package/geometry/font/re-import validation; every rendered slide visually inspected. No PowerPoint/iOS rendering claim |
| Asset integrity / links / scope | PASS when verifier succeeds | `python demo-assets/tools/verify-package.py`; PNG hashes/dimensions, exact source-tree bridge, all indexed screenshots and local Markdown targets; PPTX CRC/slides/notes |
| Existing local assets | PASS | Original checkout's **112 files** retain their baseline SHA-256; no deletion or replacement |
| Real Firebase / Backend / private storage / FCM | NOT VERIFIED | Not configured or used in the isolated package; synthetic reads are not infrastructure proof |
| Physical Android / iOS / field connectivity | NOT VERIFIED | Emulator evidence only; production/device gates remain outside UX-08 |
| Hosted CI | **NOT VERIFIED — BILLING BLOCKED** | Owner-reported restriction; actual new-PR run status is recorded in the PR. Draft deferral is not a passing Full CI gate |
| Owner acceptance / external sharing | BLOCKED pending owner review | PR stays Draft, no merge, no prospect delivery |

The first parallel Web run had 5 timing-sensitive failures under concurrent
emulator/Flutter build load; a two-worker rerun had 1 asynchronous route-render
failure. Both logs are preserved. The complete single-worker rerun passed with
the same source, assertions and timeout settings. This is a resource-sensitive
test limitation, not a claimed product fix. No test was weakened to obtain a pass.

Capture tooling also retained unsuccessful/reframed attempts outside the package:
a timeline exact-text locator included a rendered reason prefix; two detail
frames were recaptured to show the relevant content; Android's outer review
banner is visible in pixels but absent from UIAutomator semantics. Final Android
capture verifies review APK markers and installed identity and was visually
inspected. A UIAutomator process interruption was retried without app changes.
The final deck corrected its font API and runtime environment before finalization.
Only accepted captures and the validated deck are published here.

## Requirement-by-requirement matrix

| User / Issue #125 requirement | Status | Evidence |
|---|---|---|
| Review implemented five-role capabilities | PASS | [Capability/claims inventory](capabilities-and-claims.md), linked actual source routes/widgets and implementation records |
| Capture all three Web portals and both Flutter roles when executable | PASS | [Asset index](../asset-index.md), 29 Web + 6 actual Android emulator frames |
| Real, readable, truthful screenshots; no generated mocks/secrets | PASS locally | Direct Playwright/ADB PNGs, per-file SHA-256 and provenance; inspected assets, synthetic fixture values, no signed URLs or credentials |
| Concise English commercial presentation | PASS | [10-slide editable deck](../ux08/presentation/trailersaas-commercial-demo.pptx), English source text and speaker notes |
| 10–15 minute demonstration across required workflows | PASS | [13-minute walkthrough](suggested-client-walkthrough.md): visibility, dispatch, Road, Yard, Audit, Customer and limits |
| Distinguish implementation / isolated demo / unvalidated infrastructure | PASS | Deck 9, captions, runbook and [limitations](limitations.md); no end-to-end success or performance claim |
| Reproduce locally without production | PASS | [Runbook](local-demo-runbook.md), existing loopback DEV entries and explicit Android review target; scripts never seed production |
| Preserve local assets and all application/security behavior | PASS | Additive `ux08/` paths, original 112-file inventory, zero application-tree diff, retained full regression suites |
| No UI redesign, new features, invented metrics or infrastructure | PASS | Only `demo-assets/` added; no production imports, SDKs/APKs committed, dependency/workflow edits or deployment |
| Applicable local gates with remote limit explicit | PASS local / BLOCKED remote | Table above and [machine result summary](../ux08/validation/local-results.json); remote status never described as green |
| English index, captions, source identity and reusable files | PASS | README, index, captions, per-capture manifests, tools and deck source |
| Optional real clips | PASS — deliberately omitted | Optional scope; no synthetic flow video supplied |
| One Draft PR, linked to #125, no merge | PASS at publication | [PR #134](https://github.com/Dagt/TrailerSaas/pull/134) is Draft, linked with Closes #125, unmerged; final PR description supplies HEAD |

Full raw command logs, test JSON, source archive, native APK and slide previews
remain in the local `issue125-*` review bundle outside Git. The committed
[machine summary](../ux08/validation/local-results.json) records log hashes and
counts without committing large runtime/build artifacts. Asset manifests and
package SHA-256 list travel with the presentation. No Backend/OpenAPI suite is
claimed executed; those areas are untouched.
