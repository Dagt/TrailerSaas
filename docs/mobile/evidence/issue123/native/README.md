# UX-06 native Android evidence

Real unmodified `adb exec-out screencap -p` captures from a WHPX Android Emulator, AOSP Android 15/API 35 x86_64, 1536 MB RAM, four vCPUs, software GPU. This is not a physical device. APK source: `e820b83182000a09cc68238da4757d7d17052054`; complete Mobile tree: `43fcaec88b35773da54cd6b62faf5a064b085ef8`. See [provenance.json](provenance.json) for each image's hash, installed APK hash, entry, fingerprint, viewport, density, rotation, text scaling, permission and keyboard state.

Only `production-unconfigured-360.png` uses the normal `lib/main.dart` APK. It shows the actual missing-auth-configuration state, with no synthetic session. All remaining images visibly say **ISOLATED ANDROID REVIEW**: actual production widgets, synthetic reads/local SQLite, no real Firebase/Backend, HTTP writes rejected. The native permission dialog uses the actual scanner and Android permission controller. Yard confirmation uses a separately labeled synthetic scan, not a camera result; **Not Yet** cancels without recording a movement. No fixture status constitutes real authorization or a Backend-confirmed action.

The normal APK kernel contains none of the review/synthetic markers. The explicit review target requires debug mode plus `UX06_NATIVE_REVIEW=true` and is not imported or routed from production. Both APKs were built against all 201 byte-verified Mobile inputs. Subsequent evidence/docs commits preserve that complete Mobile tree; the PR records the exact final HEAD and fresh validation.

Portrait captures are 720×1600 @320 dpi (360×800 logical), larger phone 860×1864 @320 dpi (430×932), landscape 1600×720. Scaling is the actual Android `font_scale=1.8`, not resized pixels. Keyboard capture confirms `mIsInputViewShown=true`; scrolling keeps both fields and Sign in available above the IME. Android three-button navigation and status bars remain in the raw pixels. Content below the viewport is scrollable; landscape workspace-context density is a UX-07 improvement, not omitted content. The black bands around the isolated review notice in landscape belong to the test wrapper, not product navigation.

Earlier Google APIs AVD attempts encountered **System UI** ANRs under host memory pressure; these rejected attempts and logs are preserved separately, not presented as accepted product screenshots. The lighter standard AOSP AVD completed the accepted scenarios and `dumpsys activity lastanr` reported no ANR since boot. Runtime log inspection found no Flutter overflow, unhandled exception or fatal application crash. One unsuppressed Drift multiple-instance warning belongs to two separate fixture `NativeDatabase.memory` executors; production persistence/schema is unchanged. AOSP has no GMS: no live Google/Firebase, physical QR/camera, iOS, release signing/store build or TalkBack/VoiceOver result is claimed.

| Native scenario | Capture |
|---|---|
| production-unconfigured-360 | [View](production-unconfigured-360.png) |
| road-home-360 | [View](road-home-360.png) |
| road-trip-360 | [View](road-trip-360.png) |
| yard-home-360 | [View](yard-home-360.png) |
| yard-home-430 | [View](yard-home-430.png) |
| yard-text-scale-1.8-360 | [View](yard-text-scale-1.8-360.png) |
| yard-text-scale-1.8-lower | [View](yard-text-scale-1.8-lower.png) |
| yard-landscape-top | [View](yard-landscape-top.png) |
| sign-in-ime-scrolled-360 | [View](sign-in-ime-scrolled-360.png) |
| qr-permission-dialog-360 | [View](qr-permission-dialog-360.png) |
| qr-permission-denied-360 | [View](qr-permission-denied-360.png) |
| yard-confirm-move-360 | [View](yard-confirm-move-360.png) |
| yard-move-cancelled-360 | [View](yard-move-cancelled-360.png) |
