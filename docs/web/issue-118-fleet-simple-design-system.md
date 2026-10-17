# UX-01 — Fleet Simple Design System Foundation

Fleet Simple (Option D) is the owner-approved direction. PR #126 was merged at
`7c9a2bdc2fe276deabe1cdbee736a034e33cf754`, the verified implementation base.
Issue #118 remains bounded to presentation; #117–#125 already exist. No new
planning issues or epics are created. This PR remains Draft and unmerged.

## Component audit and migration matrix

| Surface | Baseline finding | Foundation / compatibility | Deferred |
|---|---|---|---|
| Global CSS | Two competing root/reset blocks; repeated local palette | One semantic palette and reset, old `--accent`/`--border` aliases | Feature-specific spacing/layout consolidation |
| Buttons | Native controls, submit/action selector, opacity-disabled | Native props retained; additive `Button` primary/secondary/subtle/danger, busy/disabled, compact/comfortable | Replace every feature button |
| Forms | Existing FormField, native inputs/selects/textareas | Existing IDs, labels, hint/error references and handlers unchanged; tokenized bounds, errors and disabled visuals | Rebuild feature forms |
| Tables | DataTable semantic caption/headers; overflow | Optional density, named focusable overflow region; existing signature preserved | Feature columns, sorting, pagination behavior |
| Cards / filters | Repeated dispatch-card and filters styles | Tokenize existing surfaces; additive Card/FilterPanel, optional density | Whole dashboard redesign |
| Badges | One neutral style for all statuses | Neutral remains default; explicit semantic tone never infers a business state | Mass-remap domain statuses |
| Details / timeline | DetailList and existing paged operational timelines | Retain old consumers; add Timeline/TimelineEvent visual primitives | Replace Trip/Trailer histories or their contracts |
| Dialogs | Native dialog, portal, focus restore, safe busy handling | Existing ConfirmDialog behavior untouched; refined visual treatment | New modal framework |
| States | Loading/empty/ErrorBanner; feature-local denied/offline text | Shared LoadingState, existing safe error logic, additive AccessDeniedState/OfflineState | Change retries, authorization or synchronization |
| Shell / navigation | Distinct role/context layouts | Token colors only; light sidebar reference in development harness | UX-02 #119 app shell and responsive drawer |
| Admin / Operations / Customer / Alerts | Shared primitives plus local markup | Receive compatible palette/control foundation; no hooks/queries/forms/routes modified | UX-03–05 #120–122 |
| Flutter | MaterialApp currently has no explicit ThemeData | Documented mapping only; no Dart, dependencies, Drift or queue edits | UX-06 #123 |

## Tokens and APIs

`web/src/shared/ui/tokens.css` owns colors, status palettes, local system sans
stack, 13/14/16/20/28px type scale, 4/8/12/16/20/24/32/48px spacing,
6/8/12px radii, borders, restrained elevation, focus, 44px comfortable and 36px
compact control density, table density, motion and breakpoint references.
`styles.css` retains the established feature layouts; `foundation.css` defines
additive shared primitives. Breakpoint variables are documented references:
CSS custom properties cannot be used in media-query conditions. New component
queries use narrow 34rem, tablet 53.125rem and wide 75rem; legacy feature
breakpoints remain until their scoped redesign.

- `Button`: native button attributes plus `variant`, `density`, `loading`.
  Defaults to type button; explicit submit remains supported. Busy is disabled
  with aria-busy and a decorative spinner; the caller supplies truthful text.
- `StatusBadge`: optional `tone` neutral/success/attention/danger/unknown/pending.
  Existing children are preserved verbatim. No automatic state classification.
- `DataTable`: existing headings/caption/children, optional compact/comfortable.
  The named, focusable region supports keyboard horizontal scrolling.
- `Card`, `FilterPanel`: semantic section/fieldset with heading/legend, density.
- `Timeline`, `TimelineEvent`: ordered events, supplied timestamp/label/source.
  No event fetching, reconstruction or pagination logic.
- Existing PageHeader, FormField, DetailList, ConfirmDialog, ErrorBanner and
  EmptyState remain compatible. AccessDeniedState and OfflineState only render
  explicit text, never authorize or enqueue anything.

No dependencies, lockfile, font/CDN or icon package added. System fonts avoid
network dependencies/licensing changes. Small decorative text glyphs in the
isolated gallery are aria-hidden; labels carry the meaning.

## Accessibility

Normal text pairs, filled action colors, semantic chip text and disabled text
are measured against 4.5:1. Focus and functional borders are measured against
3:1 using WCAG relative luminance. Decorative `#E2E8F0` separators remain;
functional control borders use `#64748B`. Text labels distinguish statuses
without color alone. Controls retain native semantics, keyboard activation,
visible focus, field labels/hints/errors and dialog focus restoration. Tables
scroll inside named keyboard-focusable regions. Reduced motion stops spinner
animation and all transitions. Comfortable targets are 44px; compact controls
are an explicit desktop density choice. No fixed component heights clip long
labels. Browser review is recorded separately from JSDOM assertions; these
checks are not a claim of a complete WCAG conformance audit.

## Development component demonstration

Run `cd web`, `npm ci`, `npm run dev`, then open
`http://127.0.0.1:5173/dev/fleet-simple.html`.

This is a **static component harness**, explicitly labeled DEMO DATA. It uses
real shared components without Firebase, Backend, Query providers, API calls or
business commands. Anchor navigation stays within the gallery. Preview
confirmation changes only gallery state. Admin inventory, Operations Trip and
timeline, Customer Orders/evidence and form/state examples demonstrate visual
components; they are not implemented feature redesigns or live metrics.
The Flutter reference is a Web-rendered mapping, not a Flutter screenshot.

The gallery HTML is not a Rollup production entry. Neither the production
entry nor appRoutes imports it; its React mount additionally requires DEV.
Deploy only `web/dist`, never the source tree or a Vite development server.
Production build inspection and regression tests verify gallery exclusion.

Screenshots must originate in the running harness, with viewport, source
revision and data provenance recorded in the final evidence. Baseline/new
comparisons must distinguish baseline shared components from any newly added
foundation examples. No existing commercial `demo-assets/` are reused or edited.

## Flutter mapping — reference only

| Web semantic value | Future Flutter mapping |
|---|---|
| canvas #F5F7FA | ThemeData.scaffoldBackgroundColor |
| surface #FFFFFF, text #1E293B | ColorScheme.surface / onSurface |
| primary #2563EB, white foreground | ColorScheme.primary / onPrimary |
| teal #0F766E | ColorScheme.secondary, restrained supporting emphasis |
| muted #F8FAFC | surfaceContainerLow / navigation surface |
| control border #64748B | ColorScheme.outline |
| decorative border #E2E8F0 | outlineVariant |
| danger #991B1B / #FEF2F2 | error / errorContainer with tested on-colors |
| status / density / spacing / radius | Small typed ThemeExtension in #123 |
| system typography and 44px targets | TextTheme, accessible tap targets; field text scaling |

Keep textScale, safe areas, outdoor legibility and native reduced-motion
preferences in #123. No production ThemeData changes are made in UX-01, so
Flutter/Drift generation and tests are not claimed as newly executed.

## Security and scope

No Backend, OpenAPI, Firebase, permissions, DTO, endpoint, business state,
Query/cache, route, command, synchronization, mobile or workflow edits.
Current versus Last Known, Expected versus Physical Coupling, uploaded versus
accepted POD, Delivered versus Completed, offline pending versus server ACK,
Audit versus operational history remain explicit. The component tone is a
presentation choice, never server authority. No GPS, metrics, Customer data
sharing or new functional product route is introduced.

## Validation and acceptance evidence

Focused development tests cover native button behavior/variants/busy state,
old component defaults, field semantics, table scrolling/density, safe stale
reload, truthful status text, timeline data, dialog confirm/cancel/Escape and
focus restoration, busy/disabled dialogs, gallery isolation and measured
contrast. Initial test-harness diagnostics (Vite HTTP import URLs used with
Node fs, and strict indexed-array typing) were corrected without changing any
historical assertion. All prior tests are retained.

Final exact-HEAD commands: `npm ci`, `npm run typecheck`, `npm run lint`,
`npm test`, `npm run build`. Full logs, test counts, source hashes, visual
review and evidence SHA-256 are published in the PR and accompanying evidence
archive after the candidate is frozen. A correction requires revalidation.
Backend/OpenAPI and Flutter code are unchanged; unexecuted suites are not
reported as passing. GitHub Actions Billing-blocked runs are **NOT VERIFIED**;
no workflow or protection change is permitted. The PR stays Draft.

| Acceptance | Evidence / boundary |
|---|---|
| Approved Fleet Simple | Light canvas/sidebar, white panels, blue actions, restrained teal |
| Central tokens / compatible components | tokens.css, foundation.css, components.tsx and regression tests |
| Real visual demonstration | Development-only running component harness; capture manifest in PR |
| Contrast / keyboard / reduced motion / responsive | Automated contrast/component tests plus browser evidence |
| Business behavior / privacy retained | No feature/auth/API/domain changes; full Web regressions |
| Flutter equivalent | Mapping above, no Dart or Drift changes |
| Complete applicable local gates | Exact final HEAD logs/counts/hashes in PR |
| Remote CI | Report actual run; Billing blocked means NOT VERIFIED |
| Deferred scope | #119–125, #63–65, whole-page redesign and native device QA |
| Delivery | One Draft PR for #118, owner review; NO MERGE |
