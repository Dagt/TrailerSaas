# TrailerSaaS — Route and Screen Inventory for UI/UX

**Baseline:** `main` after merged PR #116. Source of truth: `web/src/app/routes.tsx`, `web/src/areas/`, `mobile/lib/src/`, and the five accepted functional UX documents. **VERIFY** means Codex must inspect the actual widget/route before redesign; do not invent a screen.

## Web shared shell — UX-02

| Screen/state | Source | Visual work |
|---|---|---|
| Home and role entry | `app/AppShell`, `app/Home`, route `/` | Identity, clear role entry/navigation |
| Sign-in/unconfigured/expired Firebase | shared auth and `tenant-admin/AdminLayout.tsx` | Cohesive English forms, states and keyboard access |
| Tenant/Customer switch | Admin/Operations/Customer layouts | Context labels, safely clearing prior private content |
| Not found, errors, denied, empty, loading | shared UI components | Unified responsive states and actionable recovery |

## Tenant Admin — UX-03

| Route/view | Visual units / states |
|---|---|
| `/admin` | Landing/navigation, tenant identity |
| `/admin/customers`, `/admin/customers/:customerId` | List, detail, create/edit, status |
| `/admin/locations`, `/admin/sites/:siteId` | Sites/Locations, associations, forms, geofence labels |
| `/admin/drivers`, `/admin/drivers/:driverId` | Lists/details/driver link, careful sensitive-field labeling |
| `/admin/tractors`, `/admin/tractors/:tractorId` | List/detail and editing |
| `/admin/trailers`, `/admin/trailers/:trailerId` | Inventory, metadata, QR history/print, operational state |
| `/admin/settings` | Approved settings and validation |
| `/admin/alerts`, `/admin/alerts/:alertId` | Inbox, filters, details and allowed actions |
| `/admin/audit`, `/admin/audit/:auditEventId` | Read-only list, filters, historical actor, before/after diff |
| Shared Admin modal states | Conflict/409, optimistic version, confirmation, empty/denied |

## Transport Operations — UX-04

| Route/view | Visual units / states |
|---|---|
| `/operations` | Dispatch metrics/real board, exception emphasis |
| `/operations/orders`, `/operations/orders/:orderId` | Order queue/detail, cargo and requested stops |
| `/operations/trips`, `/operations/trips/new` | Lists, trip planning, assignments and validations |
| `/operations/trips/:tripId` | Trip detail, current assignments, stops, reroute/cancel dialogues and paginated timeline |
| `/operations/trailers`, `/operations/trailers/:trailerId` | Search/detail, current/last-known status, coupling/manifest and paginated physical movements |
| `/operations/alerts`, `/operations/alerts/:alertId` | Operational attention list, safe acknowledge/resolve dialogs |
| Shared Operations | Filters, version conflicts, denied, loading, empty, bounded pagination |

## Customer Portal — UX-05

| Route/view | Visual units / states |
|---|---|
| `/customer` | Customer dashboard, real bounded previews |
| `/customer/orders`, `/customer/orders/new`, `/customer/orders/:orderId` | List, create, details, order edits/cancel/decisions where permitted |
| `/customer/trailers`, `/customer/trailers/:trailerId` | Inventory, current vs last-known, Customer-safe movement history |
| `/customer/deliveries`, `/customer/deliveries/:orderId/:stopId` | Delivery list/detail, progress vs actual proof |
| `/customer/documents`, `/customer/documents/:documentId` | Authorized document list/detail, private URL/download state |
| Shared Customer states | Customer selector, no permission, scope reset, denied, stale/unknown/empty, uncertain ACK |

**Do not use internal Trip or Administrative Audit APIs in Customer views**, even for cosmetic widgets. No Driver/Tractor information unless already authorized.

## Flutter Mobile — UX-06 (screen verification required)

Source folders: `mobile/lib/src/app/navigation`, `road`, `yard`, `shift`, `session`, `media`, `sync`, `notifications`. The `road_mode` and `yard_mode` folders include placeholders; **do not count placeholders as implemented screens**. Enumerate the actual widgets and navigation targets in UX-06 before making edits.

| Functional surface to verify | UX expectations |
|---|---|
| Authentication and role entry | Legible active role/user, errors, no invented self-service |
| Road Driver main/navigation | Dense but tap-friendly tasks/assigned work |
| Shift start/end, inspections | Clear required fields and safe validation |
| Trip/Stops | Accurate state transitions and confirmations |
| QR/coupling Hook/Drop | High contrast scan/select/verify/confirm |
| POD/photos/fuel/evidence | Capture state, pending upload vs accepted, retry |
| Yard Spotter main/navigation | Trailer/site/location clarity |
| Yard moves/QR/checks | Action safety and correct source/target distinctions |
| Offline/sync/queue/conflicts | Pending, sent, failed, retry/uncertain states, no false success |
| Alerts/notifications | Local status only, not fabricated push delivery |

## State coverage register (every affected route)

Check: `ready`, `busy`, `empty`, `loading`, `error`, `403/denied`, `unauthenticated`, `long-data`, `different-role`, `changed-Tenant/Customer`, `responsive`. For Mobile also `offline`, `queued`, `media-pending`, `conflict`, `recovery` where relevant.

## Visual acceptance evidence

For each touched route record: screenshot filenames (real browser/device or emulator with provenance), viewport/device, source SHA, sample data origin, permission/role, privacy check, keyboard result, contrast result, responsive result and regression test references. Screens unavailable without Firebase/R2 must be marked blocked rather than replaced with fabricated product captures.

Owner will confirm each row as `verified`, `out of scope` or `fixed`; no implementation issue may silently drop an existing screen.
