# UX-04 — Fleet Simple Transport Operations

Implementation record for [#121](https://github.com/Dagt/TrailerSaas/issues/121), under [#117](https://github.com/Dagt/TrailerSaas/issues/117). One Draft PR, **NO MERGE**. Branch: `issue-121-fleet-simple-transport-operations`.

## A — Baseline and audit

Fetched `origin/main`: **`87c1cd3922742df7297d1816870996e78dfeb9d3`**. PR #129 was confirmed merged at that commit; #118/#119/#120 were closed and #121 open. No open competing PR existed. Implementation began in a clean, separate worktree. The original checkout, its branch and all 112 local `demo-assets/` files were preserved using a recursive SHA-256 inventory.

Reviewed AGENTS/workflow/validation rules, the approved Fleet Simple design system, master plan, inventory, acceptance criteria, interaction/content standards, UX-01–03 records, Transport Operations functional UX, implemented contracts and the existing code/tests. Future concepts in the functional UX document do not authorize additional modules, metrics, filters or commands.

The audit found four real Plan Trip steps (Orders, Stops, Resources, Review), bounded Dashboard projections, exact-number Trailer search, separate paginated Trip/Trailer histories and shared Alerts. Existing Operations membership selection, queries, mutations, contracts, validation and state owners remain authoritative. Existing common type/validator/SignIn imports are retained; no Operations presentation component imports AdminPresentation or admin.css.

## B–G — Route and interaction coverage

| Route / view | Presentation changes | Preserved boundary |
|---|---|---|
| `/operations` | Compact preview strip, explicit limit note, generated timestamp, grouped Customer/Driver/Trip-status filters and quick search, Pending Orders, attention link/list, Active Trips | Counts are lengths of the returned collections, never Tenant totals; up to 20 Orders/Trips; no aggregate query, GPS, ETA or performance metric |
| `/operations/orders` | Named filter group, persistent selection feedback, primary Plan selected Orders, semantic status, readable PO/BOL, scrollable table, page control | Same eligible statuses, selection handlers, filter-reset behavior, cursor and query keys; no unsupported date/search controls |
| `/operations/orders/:orderId` | Customer/status/reference overview, requested-destination sequence and cargo section | Read-only; requested Stops do not imply executable Trip Stops; no edit/cancel/fulfillment/evidence feature added |
| `/operations/trips` | Clear primary planning entry, filter group, compact directory, status/assignment and Created/Updated columns | Same supported filters, resources, incomplete-assignment condition and cursor |
| `/operations/trips/new` | Four-step progress, named Trip plan form, selected-work review, Stop field grouping, separate frozen destination context, reorder/remove controls, resource selection, structured final review | Same four steps, `useForm`/field array/watch, selection signature, validators, draft Stops, payload, optional assignments, POST and uncertain-result block; no new stage |
| `/operations/trips/:tripId` | Status/header/back navigation, version/timestamps, assignment section, executable Stops, linked Order references, chronological timeline, distinct available actions | Same state-aware action predicates, current assignments, Stop sort and authorized data; Expected Trailer remains separate from physical coupling |
| Trip actions | Consistent modal width/spacing, clear context/fields/errors, blue assignment/reroute/substitution confirmation and destructive cancellation treatment | Driver/Tractor/Expected Trailer assignment, AUTHORIZE/REJECT substitution, reroute, pre-execution cancellation and active cancellation keep existing versions, reasons, confirmations, resource restrictions, uncertain/conflict blocks and callbacks |
| `/operations/trailers` | Exact search toolbar, primary Search/clear action, results section with semantic state badges and all existing columns | Same exact operational number and URL parameters, validation and explicit submission; no fuzzy search or general inventory fetch |
| `/operations/trailers/:trailerId` | Resource header/status and scoped spacing/typography for the existing shared read view | Shared TrailerReadView source stays unchanged; Current/Last Known, UNKNOWN/IN_TRANSIT, load/serviceability, physical coupling, current/last Driver/Tractor, planned Manifest allocations and movement history retain their meaning |
| `/operations/alerts`, `/:alertId` | Operations heading, filters, severity/status badges, recorded-condition table, metadata/actions, pagination and dialogs | ALERT_VIEW/ACKNOWLEDGE/RESOLVE, reviewed version, required reason, physical recovery rules, fresh authoritative result checks and cancellation/fencing stay unchanged; acknowledgement is not recovery; FCM acceptance is not phone delivery |

Directory identifiers and linked Orders now open their existing read-only detail routes directly. No new data contract, write, privilege or operational action is introduced. Missing resource names remain unavailable instead of fabricated.

## Components and design decisions

- `OperationsPresentation.tsx`: typed `OperationsHeader` and `OperationsStatus`, using shared PageHeader/StatusBadge and existing display labels. Status tones are presentation only; no permission or transition depends on color or copy.
- Shared `Card`: additive optional `actions` and `className` props support the same Fleet Simple section/table pattern already used by Admin. With neither prop, its original class names/markup remain unchanged. Admin-specific wrappers are not imported by Operations.
- Existing Button, Card, DataTable, DetailList, FormField, ConfirmDialog, LoadingState, EmptyState and ErrorBanner are reused. No UI/icon/font library or dependency/lockfile change.
- `operations.css` uses approved tokens and Operations-scoped selectors or unique Operations dialog/heading classes. It changes grouping, table density, filter distribution, summary density, action hierarchy and planning layout rather than just recoloring surfaces. No new palette or global theme change.
- `MasterDataStatus` groups existing loading/error feedback visually. Its queries and read retries remain unchanged.
- Shared Alerts selects the existing Admin presentation for Admin and Operations presentation for Operations. Admin element classes, labels, actions and dialog styling remain intact. Shared TrailerReadView remains untouched; its Operations styling is scoped to `.operations-area`.

## H — Responsive and accessibility

The UX-02 light sidebar, contextual header, native mobile drawer, skip link, Tenant selector, sign-out and focus ownership are reused unchanged. No authorized field/module is hidden to reduce density.

Desktop prioritizes 1280/1440px: compact previews, table sections and readable filter/assignment grids. At narrower widths, toolbars/forms wrap; planning progress becomes two columns; Stop instructions span the available grid; review sections stack. Tables remain semantic, named keyboard-focusable regions with local horizontal scrolling. Data/long identifiers wrap within cells and metadata; they never widen the document.

Browser review covers 390×844, 768×1024, 1280×800, 1440×900 and 1920×1080, plus 320px and a 720×450 CSS viewport equivalent to reflow at 200% on 1440×900. Headless Edge cannot certify native browser zoom or physical assistive-technology/device behavior. Keyboard checks cover table scrolling, visible focus, native dialog/drawer Escape and trigger restoration, Stop reordering and existing step/confirmation guards. Stop validation is associated with its named fieldset; existing field labels/errors stay linked. Reduced motion is inherited from Fleet Simple. Color contrast is measured against existing AA token targets; this is scenario QA, not a complete WCAG certification.

Existing date parsing/formatting remains browser-local. The generated time retains its canonical timestamp in the semantic time element/title. No timezone, ETA or physical execution timestamp is inferred.

## Functional protection and regression matrix

No changes to `queries.ts`, `mutations.ts`, `contracts.ts`, `operationsApi.ts`, `planning.ts`, `historyContracts.ts`, TransportOperationsArea, auth/session providers, routes, Tenant/Customer permissions or shared TrailerReadView. Backend, OpenAPI, PostgreSQL/RLS, movement ledger, offline commands, Flutter/Drift, workflows and Customer Portal are unchanged. No #122–#125 work or new issue/epic.

An AST comparison normalizes and compares protected query/mutation/auth/cache/form/API/validation/navigation calls against the base across 16 boundary files. It supplements behavioral tests and scope review; it does not certify security on its own. Full Web regression retains session, membership, Tenant/Customer switching, cancellation, cache retirement, late-response, auth and resource-denial coverage.

| Behavior | Existing/new evidence |
|---|---|
| Dashboard, bounded previews, filters, resources | Operations tests plus new explicit preview/authorization/field-group checks |
| Orders selection, eligibility, pagination, reset, direct detail | Existing Operations tests plus new selection-across-pages, terminal-row and read-only navigation checks |
| Planning four steps, reorder, mappings, resources, payload, invalid draft | Historical PlanTrip tests plus keyboard reorder/Enter guard, named error group and structured review checks |
| Assignment/substitution/reroute/cancellation/version/uncertain ACK | Historical Operations/PlanTrip tests plus new modal focus, conflict draft/block and uncertain-create reconciliation checks; native-browser confirmations |
| Trailer exact search, unknown vs Last Known, coupling, history | Existing TrailerViews/History tests, new route presentation/movement-page checks and browser matrix |
| Alerts permissions/version/recovery/reason/cursor/fencing | Historical Alerts tests, new Operations denied-read/resolve/focus checks and browser dialogs |
| Tenant switch/cache and access denial | Historical Operations/Customer/Admin tests plus new redesigned-interior switch/retirement test |
| Admin and Customer compatibility | All historical cases retained; pixel parity for Admin Alerts list/detail and shared Trailer view with Operations CSS loaded |
| Production isolation | New write-rejection/entry-isolation checks plus inspection of the final production build |

`OperationsPresentation.test.tsx` adds interaction/presentation regressions. The Operations-only case located in `AdminPresentation.test.tsx` previously required plain, unbadged Operations states because UX-04 had not started. It now explicitly expects the authorized Operations badge/tone and Operations surface, while retaining the no-Admin-surface checks and existing submit behavior assertion. No Admin behavior assertion or Customer test is removed/weakened; no test is skipped.

## I — Authentic visual evidence and environment

No Firebase client configuration, authorized credentials or live Backend access was available. Actual `/operations` captures retain the truthful unconfigured sign-in state. No authenticated application capture is claimed.

The explicitly allowed fallback is `/dev/operations.html`, a separate development-only entry rendering actual React routes. Its visible notice states **ISOLATED DEMO · Synthetic read fixtures · No Firebase session or Backend authorization · All writes rejected**. `operations-fixtures.ts` contains synthetic Orders/Trips/history and reuses the prior isolated synthetic read catalog; it is never a production API or customer dataset. Synthetic role: TRANSPORT_OPERATIONS. No token, Firebase initialization or Backend call is supplied. Every POST/PATCH/DELETE rejects (503, or selected 409), so no business action is portrayed as Backend-confirmed. The real production routes/auth remain unchanged; DEV gating and production-build exclusion are checked.

Run `cd web`, `npm ci`, `npm run dev`, then open `/dev/operations.html?route=/operations`. Review states include `loading`, `empty`, `401`, `403`, `404`, `500`, `denied`, `no-membership`, `conflict` and `page-error`. An existing Trip fixture has IN_TRANSIT state, another PLANNED and another CANCELLING; these are explicitly synthetic read projections, not actions performed during review.

Before/after use identical fixtures/harness on the exact base Web source and candidate. [Evidence directory](evidence/issue121/) contains representative baseline comparisons and all route families at the four requested viewport sizes, planning/dialog/history/state variants and actual environment captures. [Browser manifest](evidence/issue121/browser-review.json) records route/component, viewport, source revision, synthetic role, data source, state, environment, hashes, checks and browser errors. Independent final-HEAD recapture/hash verification is retained in the delivery archive and PR; reports do not embed a self-referential commit hash in their own commit.

No AI generation, image synthesis, Photoshop or marketing render is used. Captures come directly from running React in Edge. Requested screens are covered by the route captures and the planning/history/dialog variants, not by invented parallel screens. Source snapshots, capture scripts/logs and raw extra evidence remain in the local delivery archive instead of adding redundant build artifacts/binaries to Git.

The candidate review at `a75eb93242dffa81de6231f5cb8286a8752cacfd` produced **101 indexed PNG captures, 19 passing browser checks, zero console/page errors, ten passing contrast pairs and three byte-identical Admin parity scenarios**. All four requested viewport sizes cover all eleven route/entry variants; baseline comparisons cover every family at 1440px and selected mobile views. Successful Trip/Trailer pagination, transient error variants, every Trip action family, Alert confirmations, 409 and uncertain creation are represented. The AST review compares **215 protected calls across 16 files**. Final-HEAD recapture is compared against these committed PNGs, with exact revision/hash logs provided in the delivery package.

Representative before/after: [Dispatch before](evidence/issue121/before/dispatch-1440x900.png) / [after](evidence/issue121/after/dispatch-1440x900.png), [Trip before](evidence/issue121/before/trip-detail-1440x900.png) / [after](evidence/issue121/after/trip-detail-1440x900.png), [Trailer search before](evidence/issue121/before/trailer-search-1440x900.png) / [after](evidence/issue121/after/trailer-search-1440x900.png). Planning: [Stops](evidence/issue121/after/plan-stops-1440x900.png), [Review](evidence/issue121/after/plan-review-1440x900.png). [Mobile drawer](evidence/issue121/after/drawer-390x844.png).

## J — Definitive validation

After freezing code, evidence and documentation, execute fresh `npm ci`, `npm run typecheck`, `npm run lint`, complete `npm test` and `npm run build` from a Git archive of the definitive PR HEAD. Record actual test counts, failures/errors/skips, exact HEAD/base/Web tree, command logs, source/lock hashes, archive hash and production fixture exclusion. Any corrective source commit requires validation of its replacement HEAD. Final results are recorded in the PR and local delivery package rather than inferred from prior published counts.

Remote Actions is inspected on that same HEAD. Billing-blocked jobs are reported as **REMOTE CI NOT VERIFIED — BILLING BLOCKED**. Draft-deferred jobs are not successes. No workflow, permission, protection or check is altered. Backend/OpenAPI/Mobile suites are not run or reported as passing because those areas do not change. The PR stays Draft/unmerged; final inspection records conflict/review/thread state.

## Acceptance checkpoints and risks

| Checkpoint | Outcome / acceptance evidence |
|---|---|
| A — baseline/audit | Verified merge/base/dependencies, clean worktree, existing tests/security owners, preserved local resources |
| B — Dashboard | Bounded factual previews, generation context, filters, quick search, Orders/Trips/Attention and authorized Alerts |
| C — Orders | Filter/selection/page state preserved, readable table, existing read-only detail |
| D — Trips/Plan Trip | Compact directory, exact four-stage planning, unchanged forms/payload/recovery |
| E — Trip detail/actions/timeline | Version/resources/Stops/Orders/history/actions clear; exact predicates, cursor and chronology preserved |
| F — Trailers | Exact lookup, full truth-preserving read view, unchanged movement cursor and current/last-known distinction |
| G — Alerts | Operations presentation plus preserved Admin, capabilities and confirmed command semantics |
| H — responsive/accessibility | Viewport, keyboard/focus/dialog/contrast/reduced-motion evidence and stated native-zoom/device limits |
| I — authentic evidence/docs | Actual React captures; honest isolated provenance/environment; before/after and state matrix |
| J — complete Web validation | Exact definitive-HEAD logs/results/hashes supplied in PR/archive; remote status explicitly reported |

**Pre-existing functional limitation discovered during QA:** on a transient *additional Trip timeline page* failure, the current Trip UI replaces its loaded event list with ErrorBanner until a successful read retry. Its query retains the data. The shared Trailer history instead keeps loaded facts visible on a transient page failure. The exact Trip behavior is present in the base `87c1cd3` and is preserved here, as required by the no-unrelated-functional-fix boundary. Successful Trip/Trailer pagination and chronological ordering are verified; the differing failure states are captured for owner review. No new issue or silent business fix is introduced.

Other limits: the harness proves component rendering and rejection/recovery presentation, not an authorized live Firebase/Backend integration or a successful real write. Native browser zoom/screen-reader/physical-device certification remains release-environment work. Wide operational tables intentionally need local scrolling. Global totals, live GPS/ETA, new resource directories, backend search, editing Order workflows and new history reconstruction remain unavailable. Approval of this UI PR is not production release certification.
