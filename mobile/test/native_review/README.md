# UX-06 isolated Android review entry

This is an explicitly selected **debug-only test application entry**, not a production route or alternate authorization flow. `lib/main.dart` never imports it. It refuses startup outside debug mode or without `--dart-define=UX06_NATIVE_REVIEW=true`. No dependency or Android configuration change is required.

Build the normal application separately, then build this review entry:

```sh
flutter build apk --debug --target-platform android-x64 --no-pub
flutter build apk --debug --target-platform android-x64 --no-pub \
  -t test/native_review/main.dart --dart-define=UX06_NATIVE_REVIEW=true
```

Preserve each APK under a distinct filename/hash before the next build overwrites its output. Use a **dedicated disposable emulator**, never a real user's installation: the unchanged Android application ID is shared with the normal APK. Do not publish/distribute this test APK as the product. Normal production builds continue to use `lib/main.dart` and must be checked for fixture-marker exclusion.

The visible `ISOLATED ANDROID REVIEW` banner accompanies actual production Flutter widgets. Synthetic session/read fixtures, MockClient and temporary in-memory SQLite replace live Firebase/Backend data. All non-GET fixture HTTP requests return 403 and log `ISOLATED_REVIEW_WRITE_REJECTED`; the Road review journal rejects saves. Yard's existing local draft/command storage can operate only in this disposable in-memory fixture database. No successful Backend operation or real authentication is represented. The fixture's capabilities do not grant a real user permissions.

The workspace exercises the actual dual-mode navigation, assigned work and native QR route. The sign-in page permits native keyboard/inset inspection without credentials. Native QR permission review uses the real MobileScanner plugin and Android camera permissions; an emulated camera is not physical-camera certification. The separately labeled Yard movement page uses an injected synthetic scan to reach the actual existing review/cancel dialog. Never call that synthetic scan a camera read.

Record APK/entry/source revision and complete Mobile tree, emulator/image versions, Android build fingerprint/API, serial, pixel size/density, font scale, orientation, keyboard and camera-permission state for each screenshot. Capture pixels with `adb exec-out screencap -p`; do not label previous widget-engine PNGs as emulator captures. Keep any unverified device/live-integration gate explicit.

Acceptance results and actual provenance are recorded in `docs/mobile/issue-123-acceptance-gates.md` and the PR delivery. These instructions do not preclaim a successful native run.
