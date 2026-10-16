# TrailerSaaS — Frontend Redesign Master Plan

> **Status:** VISUAL DIRECTION APPROVED — **Fleet Simple (Option D)** chosen by the product owner on 2026-10-16. Planning PR #126 must still be reviewed/merged before UX-01 starts.  
> **Baseline:** `main` at `f876debe35fbe5891dd0ff15f5f5b8190a581073` (PR #116 merged, 2026-10-16).  
> **Delivery:** documentation PR only; implementation requires individually reviewed issue branches and PRs. **NO MERGE by agent.**

## 1. Intent and definition of success

Turn the already-functional TrailerSaaS MVP into a coherent, polished, trustworthy **enterprise logistics SaaS** for US carrier operations and their customers. The redesign includes React Web (Tenant Admin, Transport Operations, Customer Portal and the common shell) and Flutter Mobile (Road Driver and Yard Spotter). Make it practical for busy dispatchers, readable on tablets, reliable for field use and compelling in truthful sales demonstrations.

Success is measured against a **complete route/screen inventory**, approved design direction, visual screenshots at required viewports, accessibility checks, functional-regression evidence, and a consistent English content vocabulary—not the number of CSS rules or a subjective "looks premium" statement.

**Visual-only contract:** no business-rule changes, API/RLS/Firebase permission changes, database migrations, event semantics, GPS claims, upload/download behavior, offline/Drift schema changes or speculative features. All operational data must come from real, authorized current APIs. No broadening of Customer/Driver/Yard data visibility.

## 2. Existing product and constraints

- Stack: React 19 + React Router 7 + TypeScript + TanStack Query + Vite, a small local shared UI layer and a large `web/src/styles.css`; Flutter with distinct Road/Yard UX, session/shift/offline functionality.
- Existing shared UI: `web/src/shared/ui/components.tsx`; Web areas: `web/src/areas/tenant-admin`, `transport-operations`, `customer-portal`, `alerts`.
- Baseline routes: `web/src/app/routes.tsx`; Flutter screen inventory must be finalized by walking `mobile/lib/src` and its navigation.
- Contract sources: `docs/ux/{tenant-admin-web,transport-operations-web,customer-dispatcher-web,road-driver-mobile,yard-spotter-mobile}.md`, API specs, issue #58–#62 acceptance docs.
- Preserve exact domain distinctions: **current vs last-known location**; **planned trailer vs physically coupled trailer**; **uploaded vs accepted POD**; **DELIVERED vs COMPLETED**; **manual observations vs live GPS**; **Audit vs operational timeline**.
- Preserve customer scope isolation, current/query-cache invalidation, access-gated navigation, error/unknown states, real read-only Audit and movement histories.
- English (US) throughout user-facing UI; use configured timezone and locale formatting as allowed by current data, never hard-code Chicago as the only timezone.

## 3. Brand and design review gate

**APPROVED — Fleet Simple (Option D)** supersedes the three prior directions. The owner preferred a light, uncluttered, polished enterprise SaaS and rejected the darker/complex Fleet Command and Industrial Signal directions as well as overly sparse Atlas Minimal. The official family has a **light gray sidebar**, soft gray canvas, white content surfaces, cobalt/blue primary actions, restrained teal accents, readable tables and subtle status chips. It must feel premium **without** heavy or visually complex control panels. See `design-system.md` for starter tokens and exact implementation limits.

The *visual identity decision is complete*; Codex should not reopen a theme-selection exercise. Owner still reviews the documentation Draft PR and the UX-01 component proof before any merge. No reliance on unattributed third-party screenshots/mockups. Any concept visuals are marked *concept*, never screenshots of working features.


## Approved visual direction — implementation guardrail

**Fleet Simple (Option D) has been selected.** Visual priority: simple light sidebar, restrained white cards, coherent blue CTAs, teal supporting accent, legible clean tables and minimal decoration. **Not** a dark/navy industrial control room; **not** an anonymous austere all-white mockup. Use the concrete tokens and state palette from [design-system.md](design-system.md). React and Flutter must share a design language without altering role-specific interaction semantics. Exact colors can be tuned for WCAG contrast, but a materially different concept requires owner approval.

**Execution precondition:** the owner must manually merge the documentation-only PR #126 before Codex branches from `main` for #118. Codex must not create/revise the Epic or issues; those already exist as #117–#125. Each UX issue is one Draft PR and must remain unmerged pending review.

## 4. Work breakdown and order

| Phase | Issue | Increment / gating dependency |
|---|---|---|
| 0 | This planning PR + parent Epic | Fleet Simple selected; owner reviews/merges documentation PR #126 |
| 1 | UX-01 | Implement **approved Fleet Simple** tokens, components, Flutter theme mapping and representative examples; **not** whole-app page redesign |
| 2 | UX-02 | Shared Web shell/navigation/auth/error/responsive, no auth contract changes; UX-01 |
| 3 | UX-03 | Tenant Admin (including Alerts and Audit); UX-01/02 |
| 4 | UX-04 | Transport Operations/dispatch/timelines; UX-01/02 |
| 5 | UX-05 | Customer Portal/orders/deliveries/trailers/documents; UX-01/02 |
| 6 | UX-06 | Flutter Road + Yard design implementation; UX-01, can parallelize with UX-03/04/05 after approval |
| 7 | UX-07 | Cross-platform visual/a11y/viewport regression, polish and consistency fixes; UX-03–06 |
| 8 | UX-08 | Honest demo capture package, captions and commercial presentation polish; UX-07 |

**No mass redesign PR.** Each UX issue is a separate branch, PR Draft, route inventory + affected test matrix, screenshots of the *actual running app* (where test environment supports it), and an explicit owner merge review. Keep #63–#65 independent and open. Issue #65 remains responsible for real staged integrations/physical-device release verification; UX-07 does not imply production release approval.

## 5. PR strategy and code boundaries

- Prefer tokenized scoped CSS/components; migrate `styles.css` gradually and remove dead rules only with tests. Don't change CSS class names relied on by tests unless tests are updated for equivalent semantics.
- Build design primitives around semantic HTML and typed props, retaining API hooks and server-state; avoid introducing parallel API clients, authorization adapters or cache layers.
- Incrementally modernize tables, card grids, forms, dialogs, empty/denied/loading states, compact filters, status/timeline UI, and mobile action targets.
- Don't add a UI framework or icon library without decision record comparing bundle, accessibility, theming, license and maintainability. Keep existing app secure/no CDN dependency necessary.
- When visual review exposes a genuine functional bug: log a **separate defect issue** with reproduction, do not silently widen the UX issue.
- Commit lockfiles only if a reviewed dependency change warrants it; no new paid provider dependency.

## 6. Screen inventory and coverage

`screen-inventory.md` is the acceptance baseline; each implementation PR marks affected rows and supplies desktop/tablet/mobile proof as applicable. Include nested routes, states, dialogs, lists, details, forms, role variants and pagination, not just dashboards. Flutter Road/Yard must include offline, retry and failed-capture states. Inventory rows with **VERIFY** require source inspection, not invented behavior.

## 7. Acceptance and evidence

Each UX PR must demonstrate:
1. Approved direction + consistent typography/spacing/color/interaction hierarchy.
2. State matrix: ready, no data, loading, error, denied, stale/offline where applicable.
3. Real screenshot of each materially changed route in the app (test/dummy data explicitly labeled and isolated) or an honest unmet-evidence disclosure; no generated renders masquerading as product screenshots.
4. Keyboard, focus, reduced motion, responsive and contrast checks guided by WCAG 2.2 AA target.
5. Functional parity for commands, disabled states, exact status and unknown-location semantics.
6. Role/Tenant/Customer permission and cache isolation retained; no PII, tokens or signed URLs in visual artifacts.
7. Regression: Web `npm ci`, typecheck, lint, tests, production build; Flutter locked install, Drift reproducibility, format, analyze and tests for Mobile modifications. Backend/OpenAPI untouched unless bugfix is separately approved.
8. Report exact HEAD, test commands/counts, failures/errors/skips and evidence hashes. Remote GitHub Actions billing issues are **NOT VERIFIED**, never reported as green.
9. PR remains Draft and unmerged until owner explicitly approves.

For commercial review measure: information discoverability, quick lookup latency subjectively in local test, table scan ergonomics, tap targets, long text behavior and screenshot consistency. Avoid unsourced numeric targets for app performance; measure before specifying a performance budget.

## 8. Change freeze and checkpoints

**Checkpoint 0 — Fleet Simple APPROVED:** product owner selected Fleet Simple (Option D), 2026-10-16. Planning documentation PR #126 still requires review/merge prior to UX-01.  
**Checkpoint 1:** UX-01 component showcase must be visually consistent with **light-sidebar Fleet Simple**, tested and owner-reviewed.  
**Checkpoint 2:** UX-02 shell approved in desktop/tablet/mobile.  
**Checkpoint 3:** admin/operations/customer screens independently accepted.  
**Checkpoint 4:** mobile roles accepted with offline accessibility checks.  
**Checkpoint 5:** UX-07 cross-platform visual QA and UX-08 truthful client-facing artifacts.

A rejected design checkpoint blocks dependent visual work; it does **not** justify changing business rules or bypassing existing CI.

## 9. Explicit exclusions / risk register

- No features such as continuous GPS, dynamic ETA, live maps, customer-shared internal Audit or arbitrary POD download.
- No pretend delivery state, fake counts or seeded tenant in production.
- No database/Flyway/RLS/Firebase/CI workflow/Drift version changes.
- Risks: global CSS regression; dense dispatch layouts at 1280px; content truncation; accidentally exposing privileged information; auth/caching after Tenant switches; offline mobile readability; overdesign slowing dispatch throughput.
- Mitigate through scoped tokens, visual snapshots and route-by-route regression, with **server authority unchanged**.
- #63 (backup/restore), #64 (environments/observability) and #65 (release QA) are separately gated; UI/UX completion is **not** production readiness.

## 10. Supporting specifications

- [Approved Fleet Simple design system](design-system.md)
- [Screen inventory and ownership](screen-inventory.md)
- [Visual acceptance and QA matrix](visual-acceptance-criteria.md)
- [Interaction and copy standards](interaction-content-standards.md)

Parent GitHub Epic and eight linked issues contain all scoped requirements, acceptance tests and explicit NO MERGE instructions.
