# UX-06 actual Flutter widget renders

**ISOLATED WIDGET TEST — not emulator/device evidence.** Synthetic fixtures, no live Firebase/Backend/R2 and no confirmed real operation. No generated imagery.

Base: `b942dfd4b82766770836328e4d6177867259e3cf`. Captured source: `05e4e4990853831c36d18b3934a359bf9ebf14a2`. Captured Mobile tree: `14a4af3e87b9e2219f1af837aae33d718c29d38b`. Production `mobile/lib` tree: `6c8e595c35450b8e9f06fcd1cda89f996e26211b`, retained through final HEAD. Later test-only changes scroll controls into view before tapping; no production widget changes. Final SHA/Mobile tree and fresh replay are delivered in the PR. Historical origins remain accurate.

Flutter 3.47.6 / Dart 3.13.5; Linux widget-test engine; 1x pixel ratio. SDK Roboto test alias replaces Ahem; no product font package. Four layouts: 360×800, 430×932, 360×800 at 1.8x text, 844×390 landscape.

Full original matrix: 71 before + 76 after PNGs. Selected files here: 29. Exact hashes, source, environment, scale and scenario are in [provenance.json](provenance.json). Full/final-HEAD matrix and logs are separate delivery artifacts.

**Unverified gate:** Android/iOS emulator/device, native camera/QR/permissions, keyboard/IME/safe areas, TalkBack/VoiceOver and configured live services. Widget renders do not certify these.

| State / viewport / scale | Before | After |
|---|---|---|
| sign-in-360x800-scale1.0 | [View](before/sign-in-360x800-scale1.0.png) | [View](after/sign-in-360x800-scale1.0.png) |
| configuration-error-360x800-scale1.0 | — | [View](after/configuration-error-360x800-scale1.0.png) |
| road-home-430x932-scale1.0 | [View](before/road-home-430x932-scale1.0.png) | [View](after/road-home-430x932-scale1.0.png) |
| road-offline-360x800-scale1.0 | — | [View](after/road-offline-360x800-scale1.0.png) |
| road-offline-lower-360x800-scale1.0 | — | [View](after/road-offline-lower-360x800-scale1.0.png) |
| road-trip-430x932-scale1.0 | [View](before/road-trip-430x932-scale1.0.png) | [View](after/road-trip-430x932-scale1.0.png) |
| qr-mismatch-360x800-scale1.8 | — | [View](after/qr-mismatch-360x800-scale1.8.png) |
| sync-states-360x800-scale1.0 | — | [View](after/sync-states-360x800-scale1.0.png) |
| sync-states-lower-360x800-scale1.0 | — | [View](after/sync-states-lower-360x800-scale1.0.png) |
| yard-home-360x800-scale1.0 | [View](before/yard-home-360x800-scale1.0.png) | [View](after/yard-home-360x800-scale1.0.png) |
| yard-home-360x800-scale1.8 | — | [View](after/yard-home-360x800-scale1.8.png) |
| yard-move-review-360x800-scale1.0 | — | [View](after/yard-move-review-360x800-scale1.0.png) |
| yard-move-review-844x390-scale1.0 | — | [View](after/yard-move-review-844x390-scale1.0.png) |
| yard-qr-error-360x800-scale1.0 | — | [View](after/yard-qr-error-360x800-scale1.0.png) |
| yard-check-360x800-scale1.0 | — | [View](after/yard-check-360x800-scale1.0.png) |
| yard-check-lower-360x800-scale1.0 | — | [View](after/yard-check-lower-360x800-scale1.0.png) |
| pre-trip-360x800-scale1.0 | [View](before/pre-trip-360x800-scale1.0.png) | [View](after/pre-trip-360x800-scale1.0.png) |
| pre-trip-lower-360x800-scale1.0 | — | [View](after/pre-trip-lower-360x800-scale1.0.png) |
| post-trip-lower-360x800-scale1.0 | — | [View](after/post-trip-lower-360x800-scale1.0.png) |
| evidence-360x800-scale1.0 | [View](before/evidence-360x800-scale1.0.png) | [View](after/evidence-360x800-scale1.0.png) |
| evidence-lower-360x800-scale1.0 | — | [View](after/evidence-lower-360x800-scale1.0.png) |
| media-pending-360x800-scale1.0 | — | [View](after/media-pending-360x800-scale1.0.png) |
| yard-damage-430x932-scale1.0 | — | [View](after/yard-damage-430x932-scale1.0.png) |

No operational field is painted into an image separately: these are exact engine PNG outputs. Long pages use ordinary viewport/lower-scroll captures; critical controls remain scroll-accessible.
