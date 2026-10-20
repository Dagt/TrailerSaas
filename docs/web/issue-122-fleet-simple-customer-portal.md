# UX-05 — Fleet Simple Customer Portal

Implementation record for [#122](https://github.com/Dagt/TrailerSaas/issues/122), Epic #117. One Draft PR, **NO MERGE**. Branch `issue-122-fleet-simple-customer-portal`.

## Baseline and references

Fetched `origin/main` at **`7f655a26b94afa41f3cbb1e2a8fd028f3aedda24`**, the confirmed merge of PR #130. Its changes are present in main; #122 was open and no competing open PR was found. Work began in a clean, separate worktree. Existing checkouts/branches and all 112 local demo assets were preserved with SHA-256 inventories.

Reviewed AGENTS, backlog/CI workflow, accepted product/security/domain/API constraints, the Fleet Simple design system, master plan, screen inventory, visual criteria, interaction/content standards, UX-01–04 records/components and Customer implementations #58–60. Actual contracts and routes take precedence over future concepts in the functional UX flow.

The repository contains actual UX-01–04 implementation captures. Searches of tracked images and local demo asset names found no original owner-approved conceptual Option D mockup. We do **not** claim pixel fidelity to an unavailable concept. The written approved specifications and actual baseline screens are the review references. See the separate [Visual Quality Gap Report](issue-122-visual-quality-gap-report.md).

## Presentation architecture

`CustomerPresentation.tsx` provides typed CustomerHeader, CustomerPanel, CustomerStatus, CustomerTime, CustomerCargo and CustomerTable. These compose existing PageHeader/Card/StatusBadge/DataTable, without importing Admin/Operations presentation components. There is no second theme or UI library.

`customer.css` scopes every product rule to Customer classes or the separately portaled Customer dialogs. The main entry imports this CSS. Fleet Simple tokens remain unchanged: light navigation/canvas, white surfaces, blue primary actions, restrained teal headings/context, semantic states and fine separators. Shared component implementation and other portals are untouched.

This is a composition change: the old enclosing white page frame is removed within Customer; headers, filters and content have distinct surfaces. Dashboard previews form two deliberate information groups, with a full-width recent-request list and balanced status/resource grids. Details separate identity, references, cargo, requested work, actual execution and evidence. Forms use a four-stage progress strip and grouped fields; cargo values have labels/units. Tables retain all authorized columns and reveal controlled local scrolling on smaller viewports.

CustomerTime formats authoritative instants in US English using the browser's current timezone, retaining canonical ISO in `dateTime` and tooltip. Missing instants remain unrecorded; no operational event is inferred. Date inputs, validators, payloads and times are unchanged.

## Route and state inventory

| Route | Completed presentation | Functional boundary retained |
|---|---|---|
| `/customer` | Customer identity, recent requests, six state previews, active deliveries, designated/unknown/OOS Trailers and recent accepted evidence | Same five-record query limits and filters; previews are never global totals; no new query or metric |
| `/customer/orders` | Action hierarchy, named filter group, request table, references, badges, readable timestamps, continuation | Same status/unplanned URL filters, server scope, pagination, capability-gated create |
| `/customer/orders/new` | Information / Cargo / Requested Stops / Review, field grid, labeled address fields, reorder controls, structured cargo/review, clear validation/uncertain feedback | Same four stages, explicit renewed review, current Customer draft, validators, optional Trailer eligibility, payload, non-retried submission and uncertain ACK fencing |
| `/customer/orders/:orderId` | References/timestamps, cargo and requested-itinerary columns, actual mapped delivery progress, Customer-specific history and relationships | Same capability/state/version/coherence checks and minimized progress; no internal Trip or Audit links |
| Order action dialogs | Edit, pre-execution/active cancellation, completion, reroute and alternate-Trailer decision; isolated destructive style and blue reviewed benign confirmation | Same confirmation, expected version, required reasons/instructions, scopes, requests, recovery and conflict semantics |
| `/customer/trailers` | Exact search, grouped state/location filters, complete designated inventory and independent state badges | Same explicit search, allowed server filters, cursor, active designation; no wider directory |
| `/customer/trailers/:trailerId` | Current/Last Known/condition summary, planning allocations and authorized movement history | UNKNOWN/IN_TRANSIT never become current Last Known location; planning is not physical load/coupling; redactions and designation-period cursor unchanged |
| `/customer/deliveries` | Filter group, destination/appointment/state/evidence table | Actual Order/Stop mappings and real timestamps; same cursor/filters |
| `/customer/deliveries/:orderId/:stopId` | Recorded milestone summary, distinct Order/Stop states, accepted evidence and relationship links | Stop completion != administrative Order completion; exact accepted-file association only |
| `/customer/documents` | Accepted document library with publication, acceptance and Order/Delivery links | UPLOADED != accepted POD; association-based list, same server filters/cursor |
| `/customer/documents/:documentId` | Metadata, related deliveries and prominent separate secure-download area | Same explicit fresh authorization, cancellation, memory-only grant, expiry, click-time check, unmount cleanup and storage-unavailable behavior |
| Customer/account context and feedback | Existing separate verification shell/selection, scoped typography/spacing, loading, empty, denied, not-found, error and conflict surfaces | No Outlet/provider move; no membership/context/selection/cache/auth change |

## Isolation and functionality

CustomerPortalArea, queries/orderQueries/readQueries, all contracts, context/workspaceMemory/useSubmission, routes, shared auth and API client are unchanged. No Backend/OpenAPI/database/Firebase/roles/grants/Flutter/offline/state-machine changes. No additional query or mutation is introduced. No internal Driver/Tractor, general Trip or Audit information is added.

An AST comparison against the base checks protected hook, API, validation, navigation and cache calls across sixteen Customer boundary files. This supplements the retained behavioral tests; it does not replace server-side security verification. All historical tests remain unmodified. New CustomerPresentation tests cover navigation/history, grouping/bounded reads, keyboard/review/reorder, validation, modal focus, cancellation conflict, lifecycle distinctions, foreign data rejection, Customer cache retirement and rejection of every demo write.

## Visual evidence and accessibility

No configured Firebase client or authorized live Backend/storage session was available. Actual application `/customer` screenshots preserve its real unconfigured sign-in state. Interior evidence uses the explicitly labeled **ISOLATED DEMO** development entry, actual React routes, synthetic CUSTOMER_DISPATCHER read fixtures and rejected commands. No synthetic read is presented as real customer data, a Firebase session or a Backend-confirmed operation.

`web/dev/customer.html` and `src/dev/customer.tsx` form a separate DEV-gated entry. `customer-fixtures.ts` rejects POST/PATCH/DELETE, including download authorization; no signed URL is produced by the harness. It is excluded from the normal production build. Run `cd web`, `npm ci`, `npm run dev`, then `/dev/customer.html?route=/customer`. Optional states: `contexts`, `loading`, `empty`, `401`, `403`, `404`, `500`, `denied`, `no-membership`, `conflict`, `page-error`.

Before/after use the exact base Web source and identical controlled fixtures/entry. The [evidence directory](evidence/issue122/) and browser manifest record each route, state, viewport, source revision, role, environment, data source and SHA-256. All ten route variants are captured at 390×844, 768×1024, 1280×800 and 1440×900, with extra stages/dialogs/state variants and representative before views. Additional 1920px, 320px and 720×450 CSS reflow cover wide/narrow/200%-equivalent layouts. Native browser zoom, screen readers and physical devices are not certified.

Keyboard checks cover step selection, Stop reorder, table region focus/scroll, modal/drawer Escape and focus restoration. Labels, semantic headings, independent status text and focus rings remain; motion respects the existing reduced-motion tokens. Contrast is measured on actual Fleet Simple token pairs. Screens are visually inspected for composition, density, alignment, responsive overflow and clarity; successful tests/build alone are not visual approval. Owner review of commercial quality remains the purpose of this Draft.

## Test and acceptance matrix

| Acceptance | Evidence |
|---|---|
| All Customer route families and workflows | Route table above, native-browser images and planning/dialog variants |
| Bounded truthful previews and statuses | Existing Customer tests, new regressions, actual rendered previews |
| Scope, cache and late responses | Historical CustomerPortal/Orders/Reads tests retained; added redesigned document retirement/foreign-file cases; AST proof |
| Mutations/version/conflict/uncertain ACK | Historical command tests plus modal/conflict/invalid-create checks; every isolated write rejects |
| Accepted files and ephemeral grants | Historical download expiry/cancel/late-response/logout tests unchanged; new rendering/negative-data tests; storage-unavailable native capture |
| Responsive/keyboard/contrast | Browser manifest, table-local scroll, drawers/dialogs, reflow and measured contrast |
| Admin/Operations compatibility | Their source untouched; pixel parity with Customer CSS explicitly loaded; full Web suite |
| No production demo access | DEV gating plus final production bundle scan; no route/auth override in product |
| Full Web validation | Fresh npm ci, typecheck, lint, full tests and build on the definitive PR HEAD; raw logs, counts and checksums in PR/delivery archive |
| Remote CI / Draft | Inspect actual final-SHA Actions; Billing/infrastructure blocked is NOT VERIFIED; no workflow changes, Ready or merge |

Final results and exact HEAD/base/Web tree are attached to the PR and delivery package after code/evidence freeze. No previous issue's test count is reused. Backend/OpenAPI/Flutter suites are not claimed because those systems are untouched.

## Limits and deferred work

The isolated harness cannot prove authorized live Firebase/R2 integration or a successful real command. Actual download expiry/cancellation are tested behaviorally, without manufacturing a working signed link in screenshots. Existing behavior on an additional Customer movement-page error replaces loaded rows with feedback until successful read refresh; this is preserved rather than changed in a visual issue. Raw domain state labels and the existing minimized Trip reference remain; no new privileged detail is exposed.

Shared shell/context duplication, global button semantics, cross-portal formatting and assistive-technology/physical-device review are documented as future UX-07 considerations in the Gap Report. This PR does not start UX-06, UX-07 or UX-08, create issues or claim production release certification.
