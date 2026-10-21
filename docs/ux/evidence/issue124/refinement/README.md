# Commercial refinement — actual before / after

This is the owner-requested second pass of PR133, not a new proposal or issue. The previous309 images remain historical first-pass evidence. These comparisons start from f5031b2, not the earlier pre-UX07 main.

All Web interiors visibly disclose **ISOLATED DEMO**, actual React routes, synthetic read fixtures, no real Firebase/Backend authorization and rejected writes. Android interiors disclose **ISOLATED ANDROID REVIEW**; genuine unmodified ADB screenshots, not physical-device certification. Widget captures are explicitly **NOT emulator screenshots**. No generated imagery or edited pixels. Separate-session timestamps are not commercial events.

[Full provenance/hashes](provenance.json) · [Actual route/role/state matrix](route-role-state-matrix.csv). Every priority Web route was captured at390×844,768×1024,1440×900, with relevant1280×800/1920×1080 views. The external archive includes all98 before/after Web captures and50 additional boundary/dialog/reflow captures; selected images below are committed. Unselected files are explicitly external, not nonexistent committed links.

| Priority Web screen | Before | After |
|---|---|---|
| /admin | [View](before/admin-overview-1440x900.png) | [View](after/admin-overview-1440x900.png) |
| /admin/customers | [View](before/admin-customers-1440x900.png) | [View](after/admin-customers-1440x900.png) |
| /admin/trailers | [View](before/admin-trailers-1440x900.png) | [View](after/admin-trailers-1440x900.png) |
| /admin/audit | [View](before/admin-audit-1440x900.png) | [View](after/admin-audit-1440x900.png) |
| /operations | [View](before/operations-dispatch-1440x900.png) | [View](after/operations-dispatch-1440x900.png) |
| /operations/trips/new?orderId=12100010-0000-4000-8000-000000000001 | [View](before/operations-plan-trip-1440x900.png) | [View](after/operations-plan-trip-1440x900.png) |
| /operations/trips/12100030-0000-4000-8000-000000000001 | [View](before/operations-trip-detail-1440x900.png) | [View](after/operations-trip-detail-1440x900.png) |
| /operations/trailers/12000000-0000-4000-8000-000000000050 | [View](before/operations-trailer-detail-1440x900.png) | [View](after/operations-trailer-detail-1440x900.png) |
| /customer | [View](before/customer-dashboard-1440x900.png) | [View](after/customer-dashboard-1440x900.png) |
| /customer/orders | [View](before/customer-orders-1440x900.png) | [View](after/customer-orders-1440x900.png) |
| /customer/trailers/12200000-0000-4000-8000-000000000052 | [View](before/customer-trailer-detail-1440x900.png) | [View](after/customer-trailer-detail-1440x900.png) |
| /customer/deliveries | [View](before/customer-deliveries-1440x900.png) | [View](after/customer-deliveries-1440x900.png) |
| /customer/documents | [View](before/customer-documents-1440x900.png) | [View](after/customer-documents-1440x900.png) |

| Mobile Web | Before | After |
|---|---|---|
| operations-plan-trip-390x844.png | [View](before/operations-plan-trip-390x844.png) | [View](after/operations-plan-trip-390x844.png) |
| customer-orders-390x844.png | [View](before/customer-orders-390x844.png) | [View](after/customer-orders-390x844.png) |

| Genuine Android Emulator, 360px logical width / text1.0 | Before | After |
|---|---|---|
| assigned-trip-verified-360.png | [View](native-before/assigned-trip-verified-360.png) | [View](native-after/assigned-trip-verified-360.png) |
| road-home-default-360.png | [View](native-before/road-home-default-360.png) | [View](native-after/road-home-default-360.png) |
| road-sync-default-360.png | [View](native-before/road-sync-default-360.png) | [View](native-after/road-sync-default-360.png) |
| yard-home-default-360.png | [View](native-before/yard-home-default-360.png) | [View](native-after/yard-home-default-360.png) |
| yard-movement-default-360.png | [View](native-before/yard-movement-default-360.png) | [View](native-after/yard-movement-default-360.png) |

Additional actual native captures: [430×932](native-after/yard-large-430x932.png), [landscape932×430](native-after/yard-landscape-932x430.png), [physical-move confirmation](native-after/yard-confirm-move-default-360.png), canceled with **Not Yet**. Real emulator font2.0 Road Home and unconfigured entry remain external. Initial three wrongly named navigation attempts are excluded and documented in provenance; their logs/pixels remain preserved, not presented as successful route captures. No ANR since boot or app rendering/fatal exception was found in retained native logs. Existing plugin KGP/SDK-version warnings and independent fixture database warning are unsuppressed. Landscape includes the test-wrapper notice bands and native system navigation, not product clipping. Content scrolls.

| Isolated Flutter widget-test render — NOT emulator | Before | After |
|---|---|---|
| road-offline-360x800-scale1.0.png | [View](widget-before/road-offline-360x800-scale1.0.png) | [View](widget-after/road-offline-360x800-scale1.0.png) |
| sync-states-360x800-scale1.0.png | [View](widget-before/sync-states-360x800-scale1.0.png) | [View](widget-after/sync-states-360x800-scale1.0.png) |

Offline/queue renders use the same actual production widgets and source-specific Fleet theme with SDK fonts, local synthetic state and rejected live writes; normal text and lower-scroll states are separately indexed. No locally queued action is claimed server committed. No live Firebase/Backend/R2, real browser chrome200% zoom, physical device/QR/printer, iOS or assistive-technology certification. Native requested1536MB and actual2532432kB guest memory are distinct and recorded per image. Owner commercial acceptance remains pending.
