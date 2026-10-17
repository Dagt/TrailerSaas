# UX-02 — Fleet Simple shared Web shell and navigation

Issue #119, parent #117. One Draft PR, **NO MERGE**. Fleet Simple approved Option D
is applied using the existing #118 tokens; no palette or UI dependencies added.

## Baseline and scope

- Base: `975a2be589193758d5de59be41045a3992739914`, confirmed merged PR #127.
- #119 was open, with no open implementation PR, before work began.
- Branch: `issue-119-fleet-simple-web-shell-navigation`.
- The original checkout contains untracked `demo-assets/`. Its recursive SHA-256
  inventory is preserved outside Git; implementation uses a clean, separate
  worktree. No other local branch is changed.
- Only Web presentation, directly related tests and this implementation/evidence
  record are in scope. Backend, OpenAPI, Firebase adapter/session providers,
  permission catalogs, query/cache functions and Flutter/Drift are unchanged.
- #120–125 and #63–65 remain deferred. Internal feature pages retain their actions,
  routes, forms, tables and business state handling.

## Architecture and visual structure

`shared/ui/WorkspaceShell.tsx` provides typed presentation components:

| Component | Responsibility |
| --- | --- |
| `WorkspaceHeader` | Workspace title, actual user display name/email, existing context controls and actions |
| `WorkspaceNavigation` | Supplied links, active route, light sidebar, mobile modal drawer |
| `NavigationLinks` (private) | Readable NavLinks and small decorative inline SVG; no icon library |
| `WorkspaceState` | Consistent titled loading/error/not-found surface |
| `AuthLayout` | Brand, introduction and centered/proportioned sign-in surface |

`shared/ui/workspace.css`, loaded after the existing styles in the production
entry, scopes chrome, layout and responsive behavior. All colors reference the
Fleet Simple tokens. Existing Button, LoadingState, ErrorBanner, EmptyState,
FormField and PageHeader remain compatible. AppShell retains its existing
membership-filtered area navigation and focusable main/skip link. Home explains
workspace entry; NotFound offers a clear return home.
The existing missing favicon produced a real browser 404 during review; the
production entry now uses a small local SVG matching the Fleet Simple brand.
The unchanged before-build's favicon 404 is recorded separately as baseline
evidence, never suppressed from the new application's error result.

The presentation layer never receives an API client, membership or cache. Each
portal supplies only links it already authorizes; it retains context selection,
state ownership and provider placement.

## Portal navigation and security preservation

| Portal | Navigation and unchanged authorization boundary |
| --- | --- |
| Tenant Admin | Overview links to existing `/admin`; Customers, Locations, Drivers, Tractors, Trailers, Settings. Alerts requires `ALERT_VIEW`; Audit retains `tenantActive !== false` and `AUDIT_VIEW`. ACTIVE Tenant Admin/TENANT memberships and AdminIdentity/AdminTenant providers are unchanged. |
| Operations | Existing Dashboard, Orders, Trips, Trailers; Alerts still requires `ALERT_VIEW`. Existing ACTIVE Tenant Admin or Transport Operations/TENANT role filter and OperationsTenant provider are unchanged. |
| Customer | Existing Dashboard, Orders, Trailers, Deliveries, Documents. Only verified Customer links; no Admin/Operations shortcuts added. Existing Tenant/Customer selectors, actual user identity, verified scope banner, Refresh access and Sign out remain. |

Admin's Tenant/audit cancellation and removal, Operations' Tenant query
cancellation/removal, and all identity-change/logout cache clearing remain exactly
in their existing owners. No query keys, timers, invalidation rules, assignment
filters, capability checks or request semantics change.

CustomerDirectory still revalidates on pathname/focus, and ContextPicker still
retires protected views while fetching or paused. Its assignment-version key,
workspace memory reset, Tenant/Customer change cancellation/removal, directory
checks and read-revision remounts are unchanged. CustomerPicker and
VerifiedCustomer remain responsible for current authorized Customer verification;
the Outlet remains inside PortalContext. Cleanup, signal cancellation, owner
fencing and late-response protection are untouched. The navigation dialog closes
and releases native modal inertness when these protected owners unmount.

## Authentication and access states

SignIn continues to use the existing Firebase boundary and form validation. It
adds Fleet Simple Button variants, busy/disabled presentation, an explicit expired
session notice, visible authentication errors and linked invalid-email feedback.
Refresh session calls the same existing function; a busy visual prevents repeat
clicks while it is pending. No registration, recovery, provider, role or demo
authentication path is added. Missing Firebase configuration displays its real
unconfigured state without presenting a working login form.

Session verification, directory loading/error, missing membership, missing Tenant,
missing Customer, unavailable Customer, denied API, missing resource and global
404 remain distinct. Existing shared ErrorBanner retains its 401/403/404/409/5xx
messages and safe retry behavior. Real access does not depend on visual states.

## Responsive and accessibility strategy

- Above 850 CSS px: full 240px light sidebar, readable labels and flexible content.
- At/below 850px: sidebar replaced by a discoverable Open navigation control and
  native modal `<dialog>`. All supplied modules remain available. Header/context
  controls wrap without overlaying the content; native selectors remain labeled.
- Native showModal provides background inertness and keyboard containment.
  Autofocus starts on Close navigation. Escape, Close, backdrop click, a link,
  Back/Forward or external route navigation closes it and restores the trigger.
  Resizing to desktop closes it and focuses the active visible desktop link.
  If verification/logout removes its trigger, focus falls back to the surviving
  main landmark after the protected owner retires; no security owner is retained.
- Drawer event handlers are scoped to the dialog, with type=button controls;
  no global key handler intercepts forms, feature dialogs or internal navigation.
- Skip to content still focuses main. Semantic page headings, active
  `aria-current`, visible focus, reduced motion and text labels are retained.
- Viewports: 390×844, 768×1024, 1280×800, 1440×900, plus 1920×1080 and 320px.
  720×450 checks the reflow equivalent of 1440×900 at 200%; native browser-zoom
  and full screen-reader/WCAG certification are explicitly not claimed.
- Computed new-shell color pairs pass 4.5:1; the active link measures 5.49:1.
  Unchanged foundation contrast tests continue to cover tokens and controls.

## Visual evidence and honest environment boundary

The actual local product has no configured Firebase client or authorized account;
the existing demo environment documents Backend startup blocked by Firebase ADC.
Its old preview injects identity/API fixtures. That preview is **not reused**.
No credentials, memberships, business data or permissions are created or changed.

Actual application captures cover Home, all three unconfigured sign-in routes
and NotFound. Before/after comparisons run the real base Web source extracted
from the base SHA, and the implemented production entry. Authenticated Admin,
Operations and Customer screenshots, real Tenant/Customer selectors and real
API-derived denied/loading/empty/error scenarios are **blocked by environment**.

`/dev/web-shell.html` is a separate development-only component entry, visibly
marked **DEMO DATA · Isolated component layout · No authenticated session or
business data**. It imports only shared presentation components and MemoryRouter;
it has no AuthProvider, fake identity, API fixtures, protected Outlet or product
route. Its shell/selector/drawer/feedback examples are supplementary layout proof,
never authenticated application evidence. It is absent from the production build.

Captures and provenance: [evidence/issue119](evidence/issue119/). The manifest
records route, kind, role, viewport, code source revision, source hash and PNG
hashes. Final-HEAD recapture and validation logs are retained in the external
evidence package reported in the PR. Captures are visually reviewed in Edge.

## Test and acceptance matrix

All historical tests are retained without changed assertions. New tests are in
`app/WebShell.test.tsx` and `shared/ui/WorkspaceShell.test.tsx`.

| Requested checks | Evidence |
| --- | --- |
| 1–7 AppShell, three portals, active sidebar, direct URL, Back/Forward | Existing routes tests; new real-route WebShell tests and Edge history check |
| 8–11 Responsive, drawer opening/closing, focus, keyboard | WorkspaceShell unit tests; native Edge modal autofocus/inertness/Tab/Escape/close/link/resize checks at 390 and 768 |
| 12–14 Sign-in, expiry, refresh | Historical Admin/Customer auth tests and new invalid/pending/error/expiry/refresh WebShell tests |
| 15 No membership | Existing Admin/Operations/Customer role and membership tests |
| 16–18 Tenant Admin/Operations/Customer change | Existing AdminLayout, Operations and CustomerPortal context-switch tests |
| 19–21 Verification, late responses, private caches | Existing CustomerPortal/CustomerReads owner-fencing, revoke, signal and cache tests |
| 22 Logout | Existing Admin/Operations/Customer logout/identity-change tests; new modal unmount cleanup |
| 23–24 Alerts and Audit | New capability combinations and existing Alerts/Audit tests, including inactive Tenant denial |
| 25 Denied | Existing API/UI ErrorBanner, Audit, Customer role/resource denial tests |
| 26 Firebase unconfigured | Real-route WebShell test and real product screenshots |
| 27–29 Loading/error/empty | Historical portal and shared component tests; shared layout examples explicitly isolated |
| 30 NotFound | Existing routing tests and real product captures |
| 31–33 Responsive/reduced-motion/Fleet compatibility | Edge widths, token tests, shared component tests, stylesheet checks and computed browser colors |
| 34 Existing routes/actions | Full historical Web suite; no route contract or feature-action changes |

| Checkpoint | Outcome |
| --- | --- |
| A Baseline/routes/authorization | Verified base and security owners; clean issue worktree, original assets preserved |
| B–E Shared/Admin/Operations/Customer shell | Implemented; presentation shares no security state |
| F Authentication and access states | Implemented using existing boundaries/components |
| G Responsive/keyboard/focus | Automated native-browser and unit evidence; native zoom/screen reader limitation disclosed |
| H Security/cache regressions | Historical isolation, switch, revocation, late-response and logout coverage retained |
| I Captures/documentation | Actual public/unconfigured product evidence plus isolated layout review; authenticated evidence blocked |
| J Final Web validation | Exact final-HEAD commands, counts, logs and hashes recorded in the PR evidence package |

## Final candidate validation and risks

After committing the complete candidate, run npm ci, npm run typecheck,
npm run lint, npm test and npm run build, retaining logs and full JSON results.
Verify package-lock.json unchanged, no omitted tests or unnecessary dependency,
and production output excludes development gallery fixtures. Recapture final HEAD
and compare PNG hashes. No source commits follow final validation unless all
affected final checks are rerun for the replacement candidate.

Backend/OpenAPI/Flutter suites are not executed or reported as passing for this
Web-only change. Current GitHub Actions status and actual Billing annotations are
reported in the PR. Draft-deferred suites or Billing-blocked jobs are **NOT
VERIFIED**. Workflows/protection remain unchanged. The PR stays Draft and unmerged.

Remaining limitations: authenticated visual evidence requires a real authorized
local Firebase/Backend environment; native device, screen-reader and native zoom
certification remain unperformed. Internal-page redesign belongs to #120–125.
