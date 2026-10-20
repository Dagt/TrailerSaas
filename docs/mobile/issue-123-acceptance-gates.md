# PR #132 — UX-06 acceptance follow-up

This follow-up preserves the existing branch, implementation, history and evidence. No UX-07/08 work is included. The final PR/delivery records the final HEAD, measured results and PASS/FAIL/BLOCKED matrix; historical evidence is not relabeled.

## Exact investigation of run 39463350391

Run: <https://github.com/Dagt/TrailerSaas/actions/runs/39463350391>, source `14561588aa2f122516c12ad226da9ef85a3464d4`.

| Job | Actual result | Evidence / cause |
|---|---|---|
| CI Policy, job 118768144288 | FAILURE, not executed | GitHub annotation: recent account payments failed or spending limit needs increasing; runnerId 0, empty runner name and steps |
| Full CI, job 118770801378 | FAILURE, not executed | Same run-specific Billing annotation; runnerId 0, empty runner name and steps |
| Mobile / Backend / Web / OpenAPI | SKIPPED | Existing workflow excludes full suites for Draft PRs |

Both job-log endpoints returned HTTP 404; no execution logs are available. This limitation is recorded rather than replaced by invented logs. Job details and annotations establish that the runners/steps did not execute. Local dependency-free CI policy tests passed 12/12, which is local evidence only. No source/workflow failure was demonstrated by these unexecuted hosted jobs.

The actual failures above are **REMOTE CI NOT VERIFIED — BILLING BLOCKED**. Separately, even with Billing fixed, the unchanged fail-closed Full CI aggregate intentionally rejects a Draft with skipped full suites. That is expected repository policy, not a reason to weaken the gate.

**Repository-owner action:** inspect the repository owner's GitHub Billing & plans, resolve the failed payment or applicable Actions spending/budget limit, and permit actual Actions execution. The annotation does not identify which account setting specifically requires correction. Once infrastructure and local acceptance are cleared, the owner must authorize the Ready/full-CI phase so all four suites and Full CI actually succeed on the current integrable revision. No checks, protections, workflows or Ready state are changed by this follow-up; no merge is authorized.

## Isolated native verification design

Android tools are installed outside the repository in a dedicated cache/container SDK. The existing Flutter/Gradle/plugin versions are preserved. Windows `emulator -accel-check` confirms WHPX 10.0.26200 is installed and usable. Initial Google APIs API 35 profiles (2/3 GiB) hit System UI ANRs under host memory pressure; attempts/logs remain preserved and are not accepted screenshots. A standard AOSP Android 15/API 35 x86_64 AVD with WHPX, software GPU, four vCPUs and 1536 MB RAM completed the accepted scenarios. Its fingerprint is `Android/sdk_phone64_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`; per-image display/settings are recorded with captures. No Windows features/drivers/BIOS, repository Gradle configuration or dependencies were changed.

The original 8 GiB Gradle heap exceeds the container's 7.7 GiB RAM. The first build was stopped deliberately and preserved; task-local container user properties bound Gradle to 3 GiB, two workers and no parallel builds. Repository Gradle files are unchanged. Emulator and compilation are sequenced to avoid host memory pressure.

Normal `lib/main.dart` and explicit `test/native_review/main.dart` APKs are built and identified separately. The review entry requires both debug mode and `UX06_NATIVE_REVIEW=true`, is never imported by production lib, uses no real Firebase session and displays an `ISOLATED ANDROID REVIEW` banner. It reuses actual production widgets with synthetic MockClient reads and in-memory SQLite. All non-GET fixture HTTP requests are rejected; the Road review journal rejects saves. The unchanged production Android application ID is used only in the dedicated emulator, never a real user's installation. See `mobile/test/native_review/README.md`.

Native camera-permission UI uses the actual Android/MobileScanner path and an emulated camera. The separately labeled Yard review scenario injects a synthetic QR result solely to reach the existing movement confirmation/cancel UI; it is not a physical camera read. Neither native fixtures nor earlier widget renders prove live authorization, accepted Backend commands or physical-device camera operation.

## Regression boundaries

The initially reviewed production `mobile/lib` tree was `6c8e595c35450b8e9f06fcd1cda89f996e26211b`. Native inspection found a selected-navigation icon rendered white on the light-blue indicator. The new rendered-widget regression failed at 1.22:1 contrast; a seven-line presentation change explicitly resolves selected/unselected icon colors from existing Fleet Simple primary/secondary tokens. Both selected states now pass the 3:1 icon target, with 29 focused component/session cases passing. The resulting production lib tree is `3b1e069a1de9a575b91487c52957feb0890b4a98`. Before captures retain their original source; corrected native captures use the new APK/revision.

The Android tree remains `c48b07fae0bbcebb07a46fd43fd02b01fc7e35bb`. No production auth/role/permission, QR parser/callback, physical confirmation, state machine, queue/replay/idempotency, media acceptance, Drift/schema/generated code, Backend/API, Web or native configuration change is introduced.

Final source-boundary checks and the complete applicable Flutter suite recheck permission/context isolation, QR rejection, mandatory physical confirmations, offline vs server ACK, conflict/uncertain states, media finalization vs business acceptance and persistence integrity. No historical test is removed, skipped or weakened. Exact final counts/logs belong to the final validation manifest and PR.

## Native outcomes and provenance

Both normal and opt-in review debug x86_64 APKs built successfully (173.9 s and 52.8 s for the corrected source); all 201 archived Mobile inputs remained byte-identical after the builds. Normal APK SHA256: `b50ed4ba815f20d1d89524c3f52c3ea937f3e6882e3e798ae0ad8b5d399e6cef`; review APK: `a64fd9e7d5420fb4dcd9bb704ce7a22d3170465451f6bf27523c37bdb227039d`. The normal APK kernel contains zero review/synthetic markers. Normal bootstrap and isolated review cold launches returned Status ok (8696 ms / 4605 ms). See [native screenshots and provenance](evidence/issue123/native/README.md). Previous `docs/mobile/evidence/issue123` renders remain explicitly widget-test evidence. Native captures use `adb exec-out screencap -p` without pixel editing and record APK SHA256, entry, captured source revision/Mobile tree, emulator fingerprint/API, viewport/density, system font scale, rotation and fixture origin.

Unexecuted checks remain explicit. No physical-device, TalkBack/VoiceOver, iOS or production/live-integration result is inferred from an Android emulator or a widget test.


## Acceptance matrix

| Gate | Result / scope |
|---|---|
| Preserve branch/history/implementation | PASS: existing branch/PR; no rewrite, new PR, merge or UX-07/08 implementation |
| Exact failed CI run investigation | PASS: two run-specific Billing annotations; jobs never started, logs 404; Draft suite skips explained separately |
| Android build and review-fixture exclusion | PASS: two distinct debug x86_64 APKs; normal entry excludes synthetic markers; 201 source inputs unchanged |
| Road / Trip / Yard native navigation | PASS: actual production widgets with isolated synthetic read context; Android Back and bottom navigation exercised |
| Layout / text / keyboard | PASS within emulator scope: 360/430 logical portrait, landscape, Android 1.8 text scale, lower-page scrolling, real IME and fields/submit accessible by scrolling |
| Camera denial | PASS: native Android permission dialog rejected; scanner shows readable unavailable state; no operation authorized |
| Physical confirmation | PASS: Yard review warns about physical origin and local-vs-server state; Not Yet cancels; no movement confirmed |
| Visual correction | PASS: selected nav icon contrast regression fails before fix (1.22:1); existing Fleet tokens resolve icons and regression passes |
| Full Flutter/static/boundary validation | Final frozen-revision results, counts, source identity and hashes are recorded in the PR and `issue132-final-validation.json`; focused 29 cases already pass |
| Hosted complete CI | BLOCKED: REMOTE CI NOT VERIFIED — BILLING BLOCKED for run 39463350391; new-head status must be inspected separately; Ready/full-suite phase also requires owner authorization |
| Physical device / live integrations / iOS / assistive technology | UNVERIFIED, not inferred from native emulator; staged/device release QA remains separate |

Native log inspection found no Flutter overflow, unhandled exception or fatal app crash; accepted AOSP boot reports no ANR. The fixture-only Drift multiple-instance warning remains visible (two separate in-memory executors); no schema/persistence change or warning suppression was introduced. SDK XML/KGP/deprecation build warnings are retained in logs; dependencies are not upgraded to silence them.

Capture source is `e820b83182000a09cc68238da4757d7d17052054`, full Mobile tree `43fcaec88b35773da54cd6b62faf5a064b085ef8`, production lib tree `3b1e069a1de9a575b91487c52957feb0890b4a98`. The final documentation/evidence-only commit preserves the full Mobile tree; `git rev-parse <final HEAD>:mobile` provides a verifiable bridge to both built APKs and every accepted native screenshot. Old widget and before-contrast captures retain their actual origins; they are not relabeled as final-source renders.

Landscape workspace-context density and cross-platform presentation consistency are deferred visual improvements for UX-07. No additional significant clipping/overlap/status defect was found in the inspected native views; viewport-edge partial content is scrollable, and the actual IME-scrolled capture shows both fields and the full submit action. The test wrapper's landscape notice bands are disclosed separately from product UI. Merge approval remains on hold while mandatory hosted CI is unverified. Owner action is described above; keep Draft and unmerged.
