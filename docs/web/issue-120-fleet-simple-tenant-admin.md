# UX-03 — Fleet Simple Tenant Admin

Implementation of [#120](https://github.com/Dagt/TrailerSaas/issues/120), bounded by [#117](https://github.com/Dagt/TrailerSaas/issues/117). One Draft PR; **NO MERGE**.

## Baseline and scope

Base: `556fab0fb42643d553e53cd17fdbc136afa6ee3b`. `origin/main` was fetched, #127/#128 were confirmed merged, #120 was open and no competing open PR existed. Work began in a clean worktree on `issue-120-fleet-simple-tenant-admin`. The original checkout and its untracked `demo-assets/` were preserved with a SHA-256 inventory.

The approved [Fleet Simple design system](../ux/design-system.md), [screen inventory](../ux/screen-inventory.md), [acceptance criteria](../ux/visual-acceptance-criteria.md), interaction standards and the UX-01/UX-02 implementation records remain authoritative. All `docs/ux/` documents were reviewed. Broad future navigation/dashboard concepts in the functional UX documents do not authorize new capabilities in this issue.

Only Web presentation and this implementation/evidence record change. Backend, OpenAPI, migrations/RLS, Firebase, roles, capabilities, API contracts, workflows, Flutter/Drift and the #121–#125 plans remain untouched. Existing functional tests are retained without modification. No dependency or lockfile changes.

## Structure and components

`AdminPresentation.tsx` contains small presentation components: `AdminPageHeader`, `AdminPanel`, `AdminStatus`. `presentation.ts` supplies copy, field-group metadata and hints. These components do not decide authorization, select a Tenant, create queries or own mutations.

`admin.css` uses existing Fleet Simple tokens: soft canvas, white panels, fine separators, restrained teal eyebrows, cobalt primary actions, semantic status badges and the existing focus ring. Tables keep semantic captions/headings and a named keyboard-focusable horizontal scroll region. Wide operational data scrolls inside its table; it is not removed from narrow layouts. Long text wraps within cells and metadata.

The UX-02 sidebar, contextual header, session gates and provider boundaries are reused. Overview becomes a useful directory of existing modules, without manufactured statistics. Alerts and Audit shortcuts use the same capability/Tenant conditions as the sidebar. Shared `ConfirmDialog` gains only an optional CSS `className`; its native modal behavior, busy guards, Escape handling and focus restoration stay intact.

## Route and interaction inventory

| Family / routes | Presentation covered | Existing behavior preserved |
|---|---|---|
| `/admin` | Overview; grouped Master data / Tenant controls links | No aggregate queries or invented modules; authorized Alerts/Audit only |
| `/admin/customers`, `/:customerId` | Directory, status, create/edit, record metadata, association panel, deactivate dialog | Versioned changes; association list explicitly includes only confirmations during this visit; no claim of complete directory |
| `/admin/locations`, `/admin/sites/:siteId` | Physical Site table/address/timezone/geofence, grouped form, Operational Locations table/create/edit | Physical Site and Operational Location remain distinct; neutral/Customer contexts and partial association limits retained |
| `/admin/drivers`, `/:driverId` | Directory, identity/license/certificate groups, expired status, separate account panel and unlink dialog | Sensitive fields have existing access only; no new linking feature, User/role/membership mutation or profile enrichment |
| `/admin/tractors`, `/:tractorId` | Directory, operational identity, create/edit/status/deactivation | Same validators, versioned payloads and inactive records |
| `/admin/trailers`, `/:trailerId` | Existing filters, wide state table, grouped metadata, designation, read sections/timeline, QR history/dialogs/print | Current vs last-known location, UNKNOWN/LOCATION_UNKNOWN/IN_TRANSIT, serviceability, physical observation and coupling stay distinct; no GPS or designation editor |
| `/admin/settings` | Grouped configuration fields, help, primary save, conflict feedback | Same validation, `expectedVersion`, edit snapshot, 409 blocking and explicit Reload latest |
| `/admin/alerts`, `/:alertId` | Filter grid, recorded Alerts table, metadata/action panel, pagination, acknowledgement/resolution dialogs | Same capabilities, persisted states, physical recovery restriction, reason/version checks and mutation fencing; FCM acceptance never means phone delivery |
| `/admin/audit`, `/:auditEventId` | Filter grid, bounded cursor history, actor references, metadata, before/after comparison and withheld badges | AUDIT_VIEW gate, read-only snapshots, historical actor, absent/null/redacted distinction, cursor validation; no Undo/Restore/edit |

List create actions and form actions remain at their existing routes. Search is not added to resources that have no implemented search/filter contract. Existing Trailer, Alerts and Audit filters are presented clearly without inventing client-side directory completeness or backend search semantics. Existing pagination is retained where the API/UI supports it; master-data directories do not acquire fabricated cursors or total counts.

## Forms, feedback and print

Fields are grouped by meaning while retaining every definition, control type, label, validation, required marker, request conversion and disabled predicate. Site groups are Facility details, Business address and Geofence configuration; Driver identity is separate from License and certificate. Helpful descriptions are associated with controls alongside existing validation errors. Primary save/create and separated deactivate actions reduce ambiguity.

Existing LoadingState, EmptyState, ErrorBanner, Button, StatusBadge, DataTable, DetailList, FormField and ConfirmDialog provide feedback. Detail loading/error states now retain a semantic page heading and safe return link. 401 verification, 403 denial, 404 missing, 409 conflict and service-unavailable copy stay distinct. An uncertain creation still cannot replay a POST; retry/reconcile behavior is unchanged.

QR issuance, revocation-before-replacement, generation options (including the four-module quiet zone), token fencing and public URL configuration are unchanged. Print styling is scoped to the open Admin QR preview and removes application chrome, clipping and canvas backgrounds. The actual Edge print-to-PDF flow was reviewed at default Letter size: one page, square QR and only the DEMO operational number. It respects the user's printer/page settings; physical label stock/printer/scanner certification is not claimed.

## Responsive and accessibility

The inherited UX-02 responsive sidebar/drawer and skip link remain intact. Admin filters use three columns, then two below 75rem and one below 34rem. Forms, panels, actions and metadata reflow; no module or field is hidden to save space. Tables retain local horizontal scrolling with a visible keyboard focus ring.

Browser QA covers 390×844, 768×1024, 1280×800, 1440×900 and 1920×1080, plus 320px and a 720×450 CSS viewport equivalent to the reflow available at 200% on 1440×900. This is a reflow check, not a claim of native browser-zoom certification. Native dialog focus, Escape, trigger focus restoration, reason-required confirmation, field validation, keyboard table scrolling and reduced motion are checked. Existing token contrast pairs are measured against WCAG AA targets; no palette changes are introduced. Screen reader/physical device certification remains outside this host review.

## Security and cache preservation

`AdminIdentity`, `AdminTenant`, membership filtering, capability checks and the authorized Outlet remain in their existing providers. No session/authentication code changes. Tenant switches retain cancellation/removal/invalidation behavior. Query keys, query enable predicates, abort signals, stale-owner protection, mutation callbacks/payloads, validators and recovery rules remain unchanged.

An AST comparison against the base normalizes and compares the existing query/mutation/auth/cache/form/QR calls in the eight touched boundary files. The complete protected call set is identical; this check supplements, and does not replace, behavioral regression tests. The shared Alerts workspace selects Admin presentation only for `area === 'admin'`; Operations keeps the existing element structure and rendering. A before/after Operations Alerts screenshot comparison verifies that boundary.

## Evidence and provenance

No authorized Firebase configuration/credentials or live Backend access were available in this environment. The real `/admin` app truthfully shows **Web sign-in is not configured**; see [environment captures](evidence/issue120/environment/). There is no authenticated application screenshot claim.

As explicitly permitted by the request, `web/dev/tenant-admin.html` is a separate development-only review entry that renders the actual application route components with controlled, synthetic read fixtures. The visible notice says **DEMO DATA · Isolated Tenant Admin UI fixture · No Firebase session or Backend authorization · Writes are rejected**. All records and identifiers are synthetic. All POST/PATCH/DELETE operations reject (503, or 409 for the conflict scenario). It supplies no Firebase token, performs no Backend calls and cannot grant real access. The production entry/routes/auth remain unchanged; the production bundle is checked for fixture/entry exclusion.

Run with `npm run dev` and open `/dev/tenant-admin.html?route=/admin/customers`. Optional `state` values: `loading`, `empty`, `401`, `403`, `404`, `500`, `denied`, `conflict`. QR preview uses only a process-local example public base `https://qr.example.test`, not a production configuration.

The same fixtures/review harness are used on the exact baseline source (before) and candidate (after). Both are labeled isolated DEMO, not authenticated sessions. [Before](evidence/issue120/before/) and [after](evidence/issue120/after/) contain every route variant at all four requested viewports, plus interactive/state/reflow evidence. [browser-review.json](evidence/issue120/browser-review.json) records source SHA, route/state, viewport, document width, image hashes, checks and browser errors. The final evidence package records the definitive PR HEAD and verifies committed PNGs against captures from that HEAD; no self-referential commit hash is embedded in its own commit.

The candidate review produced 178 indexed captures, 13 passing interactive checks, zero page/console errors and ten passing contrast pairs (4.5:1 for normal text; 3:1 for control boundaries). All candidate documents fit their CSS viewport; wide tables scroll locally. The additional Operations Alerts before/after images are byte-identical. [QR print evidence](evidence/issue120/print/) includes browser-generated before/after PDFs and their rendered pages, with only the synthetic `DEMO-TR-104` identity. [Boundary verification](evidence/issue120/boundary-verification.json) compares 112 protected calls across eight files against the base. These checks are limited to the scenarios recorded, not a blanket accessibility/security certification.

Representative comparisons:

| Screen | Before (isolated DEMO) | After (isolated DEMO) |
|---|---|---|
| Customers | [Desktop](evidence/issue120/before/customers-1440x900.png) | [Desktop](evidence/issue120/after/customers-1440x900.png) |
| Physical Site | [Desktop](evidence/issue120/before/site-detail-1440x900.png) | [Desktop](evidence/issue120/after/site-detail-1440x900.png) |
| Driver | [Desktop](evidence/issue120/before/driver-detail-1440x900.png) | [Desktop](evidence/issue120/after/driver-detail-1440x900.png) |
| Tractor | [Desktop](evidence/issue120/before/tractor-detail-1440x900.png) | [Desktop](evidence/issue120/after/tractor-detail-1440x900.png) |
| Trailer | [Mobile](evidence/issue120/before/trailers-390x844.png) | [Mobile](evidence/issue120/after/trailers-390x844.png) |
| Settings | [Desktop](evidence/issue120/before/settings-1440x900.png) | [Desktop](evidence/issue120/after/settings-1440x900.png) |
| Alerts | [Desktop](evidence/issue120/before/alert-detail-1440x900.png) | [Desktop](evidence/issue120/after/alert-detail-1440x900.png) |
| Audit | [Desktop](evidence/issue120/before/audit-detail-1440x900.png) | [Desktop](evidence/issue120/after/audit-detail-1440x900.png) |

## Validation and acceptance matrix

New `AdminPresentation.test.tsx` tests cover primary actions/scroll regions, one h1, capability-filtered Overview (including inactive Tenant), grouped fields, linked help and validation, preserved deactivation payload, partial associations, protected Driver controls, semantic statuses, read-only Audit, Operations exclusion and gallery write/production isolation. Existing suites remain unchanged.

| Checkpoint | Evidence / gate |
|---|---|
| A — baseline/components | Exact base above; clean isolated branch; approved tokens and shared shell reused |
| B — Customers/Sites/Locations | All list/detail/create/edit and partial association constraints; historical tests plus visual/interaction review |
| C — Drivers/Tractors | All routes/forms/account confirmation; synthetic long/null/inactive/expired cases |
| D — Trailers/QR | Existing filters/state/timeline/QR tests; real browser print-to-PDF, preview/revoke confirmation |
| E — Settings | Historical version/conflict tests; browser 409 keeps draft, blocks save and reloads explicitly |
| F — Alerts/Audit | Historical permission/cursor/late-response/redaction tests; before/after and dialogs; no Audit write controls |
| G — responsive/security/accessibility | Viewport matrix, keyboard/focus/contrast/reduced-motion checks; protected AST calls identical; complete security/cache suites retained |
| H — full final Web gate | Fresh `npm ci`, `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` on a frozen archive of final HEAD; logs, real counts and SHA-256 manifest supplied in PR/evidence package |

Full suite results and current Actions status are attached to the Draft PR after the final candidate is frozen. Remote CI blocked by Billing is **NOT VERIFIED**; no workflow/protection/check status is changed to bypass it. Backend, OpenAPI and Flutter tests are not claimed because those areas do not change. Final PR inspection reports conflicts, reviews and unresolved threads without merging.

## Risks and limits

The redesign preserves current API/UI limitations: no comprehensive Customer association read directory, new account selector, audited designation UI, master-data search contract or synthetic summary metrics. The isolated gallery intentionally cannot prove a successful live mutation or authorized Firebase session; historical tests validate those existing behaviors. Live environment integration, physical printer/scanner checks and assistive-technology/native zoom QA remain release-environment work. Table-local horizontal scrolling remains necessary for wide operational/Audit data.
