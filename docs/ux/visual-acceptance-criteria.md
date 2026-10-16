# TrailerSaaS — Visual Acceptance and QA Criteria

**Approved presentation baseline:** Fleet Simple (Option D), selected 2026-10-16. Review **light sidebar**, white panel/table hierarchy, calm blue primary buttons, restrained teal accents and consistent semantic status colors on every affected Web route and representative Flutter surface. Dark heavy navigation or characterless sparse screens do not meet visual acceptance. See [design-system.md](design-system.md).


**Goal:** objective, repeatable review of every visual redesign PR, without weakening functional, privacy or operational correctness. Target: WCAG 2.2 AA for applicable Web accessibility, plus practical touch/legibility verification in Flutter.

## 1. Required acceptance checklist per route

- [ ] Approved tokens and component variants used; no arbitrary local palette.
- [ ] Hierarchy: clear page title, current Tenant/Customer/role, primary task and relevant action.
- [ ] Domain semantics preserved; no invented counts, GPS, ETA, proof, authorizations or transitions.
- [ ] Full data, long identifiers, empty/unknown/null and boundary states readable without ambiguous truncation.
- [ ] Loading, empty, 401, 403, 404, 409, 5xx, uncertain ACK and offline states preserved as relevant.
- [ ] Keyboard-only route/controls; visible focus; dialog management; no hover-only controls.
- [ ] Semantic HTML/Flutter semantics, labels, headings, tables and status text; WCAG 2.2 AA contrast checked.
- [ ] Zoom (up to 200%), smaller screen, longer English strings and system text scaling checked.
- [ ] `prefers-reduced-motion` honored; animation never implies live data.
- [ ] Role visibility, User/Tenant/Customer cache clearing, abort fencing and direct URL restrictions retained.
- [ ] No secrets, tokens, signed URLs or real customer personal information in screenshot assets.
- [ ] Existing tests retained, affected UX tests added, test evidence bound to exact final commit.

## 2. Viewports and devices

For Web capture actual application viewport of **390×844**, **768×1024**, **1280×800**, **1440×900**; inspect **1920×1080** for dense tables and **320px** edge case. Test horizontal overflow, responsive menu/drawer, overlay focus and scroll traps.

For Flutter test a small Android phone and common large phone, with landscape/tablet assessment where widgets support them. Use emulator evidence plus physical-device QA in issue #65. Check system font enlargement, low-connectivity/offline, on-screen keyboard obscuring fields, safe areas, touch targets (44px-equivalent recommended), and contrasting outdoor visibility.

## 3. Core domain scenarios to capture

| Surface | Positive state | Critical boundary |
|---|---|---|
| Operations Trailer | Real current location and load/service state | LOCATION_UNKNOWN, IN_TRANSIT, Last Known explicitly not Current |
| Operations Trip | Planned assignment and real timeline | Planned Trailer != physical coupling; timeline paginated |
| Customer Order | Customer scoped order/progress | DELIVERED != COMPLETED; shared-trip internals hidden |
| Customer Documents | Accepted document and authorized temporary download | Uploaded not accepted; unavailable R2; no exposed URL |
| Tenant Audit | Actor/time/entity and permitted before/after | Redacted values, read-only, revoked access |
| Alerts | Real attention and authorized action | FCM accepted is not notification delivered |
| Road Mobile | Assigned stops/action feedback | Offline queued vs server committed; uncertain acknowledgments |
| Yard Mobile | Yard physical movement/QR | No duplicate events, correct source/destination, conflict |
| QR and print | Readable QR/label in print | Printable contrast, no hidden admin secrets |

## 4. Screenshot and video evidence standards

Assets must come from the actual running Web build or Flutter emulator/device, never marketing renders disguised as product. Local test/demo tenant data must be explicitly labeled **DEMO DATA**, isolated from production and privacy-safe. If auth/R2 can't be configured, log the limitation and retain genuine UI evidence where available. Prefer exact PNG files, naming convention `uxNN-area-route-role-viewport-before|after.png`; for clips, 5–20 second MP4/GIF with real interaction and no fabricated capabilities.

Maintain `docs/ux/visual-evidence-index.md` or equivalent within implementation PRs: artifact path, route, role, viewport/device, SHA, data source, status, and short benefit. Never treat a passing jsdom test as visual browser QA.

## 5. Automated gates

**Web:** locked `npm ci`, typecheck, lint, full Vitest, production build. New component/route interactions tested with Testing Library; add visual screenshots from Playwright/browser automation only when executable and approved, not by silently rewriting CI.

**Flutter:** locked dependencies, same Drift generation (no schema changes), format, analyze and all tests. Golden tests may be added with deterministic layout/fonts/device sizes and reviewed baselines; they must not silently replace functional tests.

**Backend/OpenAPI:** should remain unchanged for UX-only issues. If a real contract bug arises, create separate authorized defect work; do not broaden scope invisibly.

**Remote CI:** GitHub-hosted CI remains blocked by owner Billing unless fixed. Local evidence is not hosted CI. Do not disable or falsify checks; leave PR Draft/NO MERGE pending owner decision.

## 6. Visual acceptance matrix

| PR class | Required evidence | Acceptance owner |
|---|---|---|
| UX-01 | Tokens, component preview, one Admin/Operations/Customer/Mobile representative state | Owner approval of direction |
| UX-02 | Shell/sign-in/context change across Web viewports; keyboard navigation | Owner + functional tests |
| UX-03 | All Admin route families and core dialogs, Audit privacy | Owner + tests |
| UX-04 | Ops board/orders/trips/trailers/alerts/timelines, data-dense screens | Owner + tests |
| UX-05 | Customer dashboards/orders/deliveries/trailers/docs and denial/scope changes | Owner + tests |
| UX-06 | Road and Yard main/tasks/offline/evidence in Flutter, small screen and text scaling | Owner + tests |
| UX-07 | Cross-portal parity, responsive/a11y deficit log and repairs | Owner |
| UX-08 | Real screenshot/video asset manifest, honest captions and commercial deck-ready selection | Owner |

## 7. Definition of Done for a UI/UX issue

The corresponding section of `screen-inventory.md` is accounted for; screenshots are reviewed, functional behavior is unchanged, relevant access tests pass, copy is US English, visual accessibility checks are documented, no unapproved dependency or server change was introduced, known limitations are listed, and the draft PR includes exact HEAD and verifiable test results. **No agent merge.**

Issues #63–#65 retain their independent production readiness responsibilities; UX-07/08 cannot declare production release readiness.
