# UX-07 actual before / after evidence

> Historical first-pass evidence for f5031b2. Current owner-requested commercial refinement and final-source comparisons: [refinement index](refinement/README.md). Original captures/provenance remain preserved.

All images are unmodified native browser or Android Emulator captures of implemented components. **ISOLATED DEMO / ISOLATED ANDROID REVIEW** means synthetic reads/context with rejected writes, not real Firebase/Backend authorization or accepted operations. Only normal-entry unconfigured/Home/404 captures show the actual unconfigured environment. No generated imagery, Photoshop, fictional metrics, customer records or staged success.

Base: `cb08d3da85ade73ada7bc5bcf38cc7178c9e6b4a`. Web source: `dc2b6e74d254f10006e94e291c8357f9c7d8d6cc`, complete Web tree `35c5ee93c02b0e3ace6570b1b57dd7c64d67070f`. Android source: `7971457f3d66aaaba39867b2d17e25604e9c36d8`, complete Mobile tree `f3db13b8516495265fab5401174ec45a7c858187`. The first-pass documentation-only HEAD f5031b2 preserved those complete trees. Current refinement uses different source trees; follow the refinement index for the current final SHA and validations. [Provenance/hashes](provenance.json) · [complete actual route-role-state matrix](route-role-state-matrix.csv).

The complete archive retains 309 captures and manifests; 38 representative PNGs are committed below. Other indexed images are explicitly external evidence, not broken claims of committed files. Browser coverage: all 37 actual route/scenario variants at 390×844 and 1440×900; representative dashboards/directories/forms additionally 768×1024, 1280×800,1920×1080; 320px and 720×450 reflow, boundary states, native dialogs and keyboard checks. Browser chrome zoom itself was not available; equivalent reflow is disclosed. QR print images are actual browser print CSS using process-only `https://qr.example.test`, not physical printer/scan certification.

| Web scenario | Before | After |
|---|---|---|
| admin-overview-1440x900 | [View](before/admin-overview-1440x900.png) | [View](after/admin-overview-1440x900.png) |
| admin-driver-detail-1440x900 | [View](before/admin-driver-detail-1440x900.png) | [View](after/admin-driver-detail-1440x900.png) |
| admin-trailers-1280x800 | [View](before/admin-trailers-1280x800.png) | [View](after/admin-trailers-1280x800.png) |
| admin-audit-detail-1440x900 | [View](before/admin-audit-detail-1440x900.png) | [View](after/admin-audit-detail-1440x900.png) |
| operations-dispatch-1440x900 | [View](before/operations-dispatch-1440x900.png) | [View](after/operations-dispatch-1440x900.png) |
| operations-orders-390x844 | [View](before/operations-orders-390x844.png) | [View](after/operations-orders-390x844.png) |
| operations-trip-detail-1440x900 | [View](before/operations-trip-detail-1440x900.png) | [View](after/operations-trip-detail-1440x900.png) |
| operations-plan-trip-390x844 | [View](before/operations-plan-trip-390x844.png) | [View](after/operations-plan-trip-390x844.png) |
| customer-dashboard-1440x900 | [View](before/customer-dashboard-1440x900.png) | [View](after/customer-dashboard-1440x900.png) |
| customer-create-order-390x844 | [View](before/customer-create-order-390x844.png) | [View](after/customer-create-order-390x844.png) |
| customer-document-detail-390x844 | [View](before/customer-document-detail-390x844.png) | [View](after/customer-document-detail-390x844.png) |
| Independent Customer query failures | [View](qa-before/customer-500-390.png) | [View](qa/customer-500-390.png) |
| Four-stage Trip plan / final review | [View](forms/before-plan-review-390.png) | [View](forms/after-plan-review-390.png) |
| Browser QR print media | [View](forms/before-qr-print-media-1440.png) | [View](forms/after-qr-print-media-1440.png) |

Native before comparisons use the preserved [UX-06 native evidence](../../../mobile/evidence/issue123/native/README.md). Its original capture commit remains accurate and its complete Mobile tree equals the UX-07 base. Timestamps/test data reflect separate sessions; they are not actual customer events. Compare [Road before](../../../mobile/evidence/issue123/native/road-home-360.png) / [after](native/road-home-360.png), and [landscape before](../../../mobile/evidence/issue123/native/yard-landscape-top.png) / [after](native/yard-landscape.png). The compact context groups all existing facts; it does not promise reduced height for every enlarged/long-string case. Content remains naturally sized and scrollable.

Native Android: real AOSP Android 15/API35 x86_64 emulator, WHPX/software GPU; 360×800 and 430×932 logical portrait, landscape, system text scales1.8/2.0. Actual IME and camera denial tested; physical move dialog canceled with Not Yet, no operation confirmed. At2.0 the dialog body scrolls while actions remain available; partial viewport content is not omitted text. The review wrapper notice/landscape bands and direct synthetic Yard screen are not product chrome. Runtime shows no ANR since boot or app overflow/unhandled/fatal exception in inspected logs. The unsuppressed Drift warning is from two independent memory fixture databases. Requested RAM differs from actual guest minimum; actual MemTotal is recorded.

| Native actual scenario | Capture |
|---|---|
| production-unconfigured-360 | [View](native/production-unconfigured-360.png) |
| road-home-360 | [View](native/road-home-360.png) |
| yard-home-360 | [View](native/yard-home-360.png) |
| yard-home-430 | [View](native/yard-home-430.png) |
| yard-text-scale-1.8-lower | [View](native/yard-text-scale-1.8-lower.png) |
| yard-landscape | [View](native/yard-landscape.png) |
| sign-in-ime-360 | [View](native/sign-in-ime-360.png) |
| camera-permission-denied-360 | [View](native/camera-permission-denied-360.png) |
| yard-confirm-move-360 | [View](native/yard-confirm-move-360.png) |
| yard-confirm-text-scale-2.0 | [View](native/yard-confirm-text-scale-2.0.png) |

No physical device, live integrations, native browser zoom, iOS or manual assistive-technology certification is inferred from these scoped checks.
