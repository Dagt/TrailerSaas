# UX-06 Visual Quality Gap Report

Scope: actual Flutter Road/Yard presentation under issue #123. Fleet Simple Option D written specification and native widget-engine base comparisons are available; original conceptual mockups are not. No image-to-image fidelity claim is made.

## Corrected issues and standardized components

| Baseline / observed issue | Correction |
|---|---|
| Default unbranded Material palette and undifferentiated text/actions | One Fleet Simple theme, explicit type hierarchy, functional borders, light surfaces and consistent actions |
| Assigned work appeared below lengthy sync/evidence support | Road task list is ahead of support, with one prominent Open assigned Trip and numbered Stops |
| Trip identity, planned Trailer and coupling competed as raw adjacent text | Labeled assignment facts, exact state, current task and separately ordered executable Stops |
| Shift/evidence/instruction UI mixed English and Spanish | Visible operational copy consistently US English; contract/sentinel strings retained |
| Photo forms and draft/submit controls lacked spacing and hierarchy | Form rhythm, natural-height photo checklist rows and secondary draft vs primary reviewed operation |
| Long trailing labels/actions compressed details | FleetRecord stacks action below full content instead of a fixed trailing slot |
| Site field and scan action visually touched; raw microsecond dates wrapped awkwardly | Field rhythm and human-readable US English date/actual offset presentation |
| Long Site names or text scaling could be constrained by default menu item heights | Variable-height expanded dropdowns; actual long-name Yard read fixture tested |
| Sync CTA competed with the operational primary action | Secondary Sync now without changing its predicate or handler |
| Pending/conflict/ACK were plain dense strings; media labels could be constrained in chips | Explicit text+icon semantic states, wrapping badges and readable history/review rows |
| Yard Check scan had a fixed 56px height | Minimum-height control grows with text scaling |
| QR informational rejection offered a misleading Confirm label | Rejection/missing-location warnings use Close; they retain their early return and grant no action |
| QR help/error area could consume a narrow landscape viewport | Bounded scrollable help/error presentation with unchanged scanner/detection/identity logic |
| OOS confirmation had the same action weight as photo capture | Explicit danger treatment for Confirm Mark OOS, with unchanged photo/comment/authority checks |
| Flutter's initial test renders used Ahem boxes, making visual inspection invalid | Test-only SDK Roboto alias; invalid early captures are not supplied as final visual evidence |

## Ten-point rendered quality review

| Criterion | Finding / practical limit |
|---|---|
| Layout balance | Tasks and current actions have primary weight; synchronization and evidence are supporting sections |
| Typography / hierarchy | Clear workspace/page/section/fact levels, readable 14–16sp body, no decorative display font |
| Spacing consistency | Shared form/record rhythm, separated fields/actions, restrained panel borders |
| Primary actions | Blue task/execution actions; outlined support/draft actions; danger OOS confirmation |
| Operational readability | Recorded origin/expected assignment/physical coupling and private evidence notices remain explicit; identifiers wrap |
| Status distinctions | Pending, sending, uncertain, conflict, rejected, acknowledged and media-finalized/accepted business evidence remain distinct |
| Accessibility | Measured contrast, 48px action minimum, text+icon states, semantic headings, keyboard/Escape/focus and keyboard-inset tests; no device-assistive certification |
| Responsive behavior | Small/large phone, 1.8x text and landscape widget-engine tests; 2x text/keyboard-inset component scenario |
| Cross-screen consistency | One theme and shared Road/Yard presentation primitives match the approved React palette; Web source unchanged |
| Commercial quality | Default scaffold/text dump is replaced by deliberate work/form/history composition. Owner visual approval and emulator/device review remain necessary |

## Evidence and limits

All supplied images are actual Flutter renders with a visible **ISOLATED WIDGET TEST** notice. Synthetic states and fixture reads are not actual customer records, Firebase authorization, Backend-confirmed operations or native camera/photo proof. Selected base/final-source comparisons and provenance are indexed in the implementation evidence; final-HEAD replay and logs accompany the Draft PR.

No Android/iOS emulator/device is available. Flutter doctor identifies absent Android SDK/AVD and native desktop tooling; Linux is detected but this project has no Linux target. The widget-test fallback is disclosed rather than substituted for emulator evidence. Native build/install/camera/location permissions, safe areas, hardware keyboard/IME and assistive technology remain a real verification gate. The Android-independent bundle build is not an installable APK or iOS build.

The unchanged queue's retryable-failure label intentionally says result uncertain. A file finalized by server still requires the owning business workflow's acceptance. A historical recorded location/ACK is not a live Trailer position. Screenshots do not change these facts.

## Deferred to UX-07 / release verification

- Cross-platform shell/context-density and display-language policy review across Web and Mobile; no complete Web redesign here.
- Shared table/list/confirmation conventions and all-portal visual parity; no new global API or navigation framework here.
- Owner commercial review against any subsequently supplied original conceptual references.
- Emulator/device and TalkBack/VoiceOver execution remains an unmet verification gate, not a passed visual criterion; staged integration and physical-device release QA remain #65.

No UX-07/08 issue work, new issue, role grant, GPS/ETA, offline engine change or new business flow is introduced.
