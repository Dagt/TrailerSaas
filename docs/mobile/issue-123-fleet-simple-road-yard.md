# UX-06 — Fleet Simple Road Driver and Yard Spotter

Implementation for [#123](https://github.com/Dagt/TrailerSaas/issues/123), Epic #117. Branch `issue-123-fleet-simple-mobile-road-yard`. One Draft PR, **NO MERGE**. UX-07/08 and release work #63–65 are not implemented here.

The owner subsequently requested recovery of unpublished progress. Commit `05e4e49` and its existing branch were found clean and retained; see the [recovery snapshot and completion matrix](issue-123-recovery-and-completion.md). Final validation/publication continue from that source, without duplicate implementation or history rewriting.

## Baseline and verified scope

Fetched main at **`b942dfd4b82766770836328e4d6177867259e3cf`**, the verified merge of PR #131. Issue #123 was open and no competing open PR existed. The feature branch started clean in a separate worktree; previous branches/checkouts and local demo assets were preserved.

Read AGENTS/workflow, accepted product/operational/offline rules, the Fleet Simple specification, master plan, screen inventory, acceptance/content standards, UX-01–05 patterns and the implemented Flutter views/tests. [Verified screen inventory](issue-123-screen-inventory.md) was recorded before UI changes. SessionView is the real role entry; ModeShell and Road/Yard placeholder directories are not treated as production screens. Broader conceptual UX flows do not authorize missing features or capabilities. In particular, no public Trailer directory, Yard-specific Shift policy, new inspection checklist questions (OQ-052), or new Return-to-Service authority (OQ-054) is invented.

The original conceptual Option D mockup is unavailable in the repository. Fidelity to an unseen image is not claimed. Approved written Fleet Simple tokens and actual base/final Flutter renders are the review references.

## Presentation architecture and screen composition

`ui/fleet_theme.dart` maps the approved Web tokens to Flutter Material 3: light canvas `#F5F7FA`, white surfaces, cobalt `#2563EB`, restrained teal `#0F766E`, slate text, functional control borders and semantic states. It configures typography, natural-height 48px-minimum actions, focus, fields, cards, navigation and dialogs. It adds no package, font asset or network font dependency. The application installs this one theme at its existing MaterialApp.

`ui/fleet_widgets.dart` supplies small presentation-only components: FleetHeader, FleetSectionTitle, FleetFact, FleetStatus, FleetNotice, FleetRecord, FleetStop, FleetFormBody and FleetAccessState. Shared Road/Yard patterns do not own a session, select context, enqueue work, fetch data or decide permission. Explicit queue/media tone mappings never infer a grant or transition. Unknown/pending/uploaded states cannot acquire acknowledged/finalized success styling.

| Actual experience | Change |
|---|---|
| Session / workspace | Branded readable sign-in, separated fields, truthful access states, compact Tenant context, explicit connected vs cached-offline notice; existing role switch and sign-out retained |
| Road home | Assigned work is brought ahead of synchronization support; prominent Open assigned Trip, Expected Trailer and numbered Stop rows; Shift and operational prerequisites remain separate |
| Trip execution | Clear assignment/status header, Tractor / Expected Trailer / physical coupling facts, prominent permitted current action, ordered readable Stops, separate supporting recovery/evidence/sync |
| Shift | Structured inspection section and consistent US English; server On/Off Duty, local pending intent, accepted inspection and blockers remain distinct |
| Pre/Post-Trip | Real existing form grouped around equipment, odometer, required photo positions/checklist or post-trip observations; natural-height photo rows and separated draft/confirmation actions |
| QR | Legible scan instructions and unavailable-camera feedback; help/error areas scroll at small landscape/enlarged text sizes. Parsing, native scanner, detection callback, cancellation and ownership remain unchanged. Informational rejection/missing-location dialogs say Close rather than implying authorization via Confirm |
| Road evidence | Purpose/context header, private-capture notice, labeled fields and photo controls, secondary local draft vs primary reviewed synchronization; same BOL/No Receiver/fuel/problem payloads and validation |
| Yard | Context, Site selector and prominent scan first; recorded Trailer facts and movement review before auxiliary corrections/damage; Check inventory and sync/history remain accessible |
| Yard actions / Check | Separated correction/OOS sections; explicit destructive OOS confirmation styling; readable snapshot/local findings and natural-height reconciliation/history rows. No scan becomes a correction or physical move |
| Sync / media | Text+icon semantic queue states; secondary Sync now, stacked review actions, readable retry/conflict/uncertain details. File finalization is explicitly separate from accepted business evidence |
| Updates / notifications | Clear section hierarchy and historical instructions, refresh/retry states, retained account/Tenant guards; no push-delivery claim |

Dates formerly displayed raw local microseconds in several panels. `fleetTime` formats only their presentation in US English, with 12-hour time and the device's **actual UTC offset**. No fixed operating timezone, payload timestamp conversion, ETA or new event is introduced. Canonical persisted/transport timestamps are unchanged. A small display adapter translates the existing store-provided instruction error without changing its underlying sentinel.

## Functional and security preservation

Controllers, API clients/contracts, role/capability grants, context ownership, state machines, native integrations, caches, persistence, command capture/replay and their idempotent identities are unchanged. `app_database.dart`, generated Drift output, schema version, dependencies/lockfiles and Android/iOS configuration remain unchanged. Backend/OpenAPI/Flyway/Web are untouched.

The independent Dart-parser audit compares protected method invocations (including arguments/payload expressions) and operational guards in changed existing views against the exact base. Its report preserves **92 protected calls and 177 original operational guards**. The three added SessionView predicates choose presentation labels/notice tone only. They do not replace or relax any original guard. The final run and source hashes accompany PR delivery.

Existing safe behaviors remain:

- Firebase/session and account/Tenant/Driver scope changes hide/fence old views, scans, confirmations and late callbacks; the authorized navigation tree and owner keys are retained.
- A downloaded expected Trailer is not physical coupling. QR identity does not authorize replacement. Informational Close buttons discard the warning dialog result and execute no operation.
- Local intent never advances downloaded Trip/Stop/Trailer/Shift projection. QUEUED, SENDING, uncertain retry, conflict/blocked, rejected and server ACK are distinct.
- The same immutable operation IDs, payloads, dependency edges, materialized requests, replay/review, evidence finalization and server authorization checks remain authoritative.
- Physical movements, corrections, reconciliation, inspections and evidence submission retain their explicit confirmation, validation and reason requirements. Uncertain outcomes do not gain an unsafe retry or discard action.
- Missing/current/last-known, expected/physical, uploaded/finalized/accepted and Shift/Trip completion remain separate. No GPS/map/ETA or fabricated completed work is added.

## Accessibility and responsive strategy

Use natural heights and wrap text instead of truncating critical facts. FleetRecord places actions below text, avoiding ListTile trailing-width constraints on small/enlarged layouts. Dropdowns allow variable item height and expanded label width. Yard fields/actions have explicit spacing; form groups use a shared vertical rhythm. Dialogs retain native focus/route management and gain scrollable content. Yard Check scan has a minimum rather than fixed height. Existing SafeArea/Scaffold keyboard insets and scrollable forms are retained.

New tests cover semantic state distinctions, actual token contrast pairs, 48px actions, long text with a 240px keyboard inset at 2x text scaling, reduced-animation layout, keyboard activation and Escape/focus restoration. Existing permission, owned-dialog and lifecycle regressions remain in place. These checks target accessibility; they are not a blanket WCAG or TalkBack/VoiceOver certification.

## Authentic rendering evidence and remaining device gate

**No Android SDK/AVD, connected Android/iOS device, macOS/Xcode or usable Linux app toolchain is available.** Flutter doctor/devices/emulators logs record this. The project targets Android/iOS; adding an unrelated desktop/web platform to claim a device run is outside scope.

The available evidence is **actual Flutter widget-test engine output**, not emulator/device screenshots. `test/ui/fleet_screens_test.dart` mounts actual production widgets with isolated test fixtures/private temporary SQLite and clearly labels every render. It has no production route/entry and is not imported by lib. QR token/authorization results are injected test inputs, not proof of working camera hardware. Synthetic read/state fixtures and local draft/file setup do not represent a real Firebase session, accepted real command, captured customer photo or live Backend/R2 operation. Capture scenarios assert no HTTP writes/PUTs/business effects; Yard read harnesses explicitly reject accidental writes.

The capture matrix covers 360×800, 430×932, 360×800 at 1.8x text, and 844×390 landscape: sign-in, Road home/Trip/offline, all queue states, QR mismatch/invalid identity, Yard home/move review/Check, Pre/Post-Trip, No Receiver evidence, pending media and damage/OOS review. Small-phone lower-page captures show retained form/history controls. A test-only SDK Roboto font alias replaces Flutter tests' Ahem boxes while preserving font metrics/styles; no product font dependency is added. Baseline uses exact base widgets and default Material theme; copied capture support/theme is test-only and not activated in baseline UI.

Selected before/after widget renders and a provenance manifest are indexed under `docs/mobile/evidence/issue123/`; full final-HEAD replay, hashes and logs are delivered separately with the PR. Capture metadata records the actual source revision/tree, viewport, scale, fixture origin and render errors. The production `mobile/lib` tree remains `6c8e595c35450b8e9f06fcd1cda89f996e26211b`; later test-only changes scroll controls into view before taps. Final Mobile tree and replay are recorded separately, without relabeling earlier runs.

**Remaining visual verification gate:** emulator/device execution, actual QR/camera permissions and preview, safe areas/IME on Android/iOS, native fonts/platform differences, TalkBack/VoiceOver and real authorized Firebase/Backend/storage integration. Widget renders are useful evidence and do not satisfy or replace this device gate. Physical release QA remains #65. See the [Visual Quality Gap Report](issue-123-visual-quality-gap-report.md).

## Validation and acceptance matrix

Exact final HEAD/base, measured counts, command exit statuses, full logs and hashes are published in the Draft PR/delivery after freezing the candidate. This record does not preclaim a successful final run.

| Checkpoint | Evidence / gate |
|---|---|
| Inventory / baseline | Verified source inventory, PR #131 merge, clean new branch |
| Road and Yard composition | Actual production-widget before/after render matrix and manual image inspection |
| Status and offline truth | New tone/label tests plus full existing queue/replay/uncertain/conflict/media suites |
| Authorization and confirmations | Existing identity/context/permission/QR/cancellation/reconciliation tests; source/parser boundary evidence |
| Accessibility | Contrast, scaling, keyboard/focus, natural-height controls; device assistive checks still unverified |
| Locked install | `flutter pub get --enforce-lockfile`; byte-identical lock |
| Generated persistence | `dart run build_runner build`; raw-byte identity against the committed Git blob, with unchanged source/schema |
| Formatting / static analysis | `dart format --output=none --set-exit-if-changed lib test`; `flutter analyze --fatal-infos` |
| Full tests | Full `flutter test` with machine-readable count/failure/skip report; no omitted historical suite |
| Build | `flutter build bundle` where supported; Android APK/iOS native build availability recorded separately |
| Remote CI | Actual current-HEAD Actions inspection. Billing failure is **REMOTE CI NOT VERIFIED — BILLING BLOCKED**, never green |
| Delivery | Single Draft PR, `Closes #123`, no merge; device gate and hosted Full CI remain explicit |

Historical widget assertions are updated only for US English copy, equivalent labeled assignment facts and the secondary Sync button type; no assertions are removed or relaxed. The QR mismatch test additionally requires Close and forbids Confirm. Backend/OpenAPI/Web tests are not represented as run for this mobile-only change. No workflow, protection or check bypass is included.

The first complete suite exposed three off-screen test taps after layout reordering (enlarged QR action, recovery action, and arrival Not Yet). Failed logs are preserved. Tests now scroll the whole control into view and assert hit-testability before tapping; all original authorization, zero-write, exact-key/payload and cancellation expectations remain. No production handler or assertion is weakened. Focused regressions and the full suite are rerun on the final revision.

## Investigated validation archive line endings

The first frozen validation stopped at a raw generated-file comparison. Investigation found that Windows `core.autocrlf=true` converts `git archive` output to CRLF: the exported file was 518,074 bytes, while the actual committed Git blob and regenerated output are both 502,642 bytes with LF. Regeneration on both base and candidate produced bytes identical to their committed Git blobs. This was a validation export artifact, not a baseline Drift or hosted CI defect. The failed attempt and four-way byte/hash comparison are preserved in delivery rather than concealed.

The final source archive uses `git -c core.autocrlf=false archive`, verified against every committed Mobile file. Final validation repeats generation and requires raw-byte identity without normalization. No tracked generated file, schema, repository Git setting or workflow is changed. Earlier widget evidence used identical production widgets with checkout-only newline conversion; Dart semantics are unchanged and exact-final-HEAD replay is supplied separately.
