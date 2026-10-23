# UX-07 — cross-platform visual quality and accessibility audit

Current M1/L1 closure evidence: [final corrections](evidence/issue124/final-fixes/README.md). Earlier assessments below are historical snapshots; final local counts, exact HEAD and owner Billing exception are published in PR133 after final validation.

Issue #124, Epic #117. Fleet Simple Option D remains authoritative. Implemented presentation refinements and reviewed evidence; owner approval and hosted complete CI remain separate gates. Unexecuted manual checks are not passes. One Draft PR; **NO MERGE / no UX-08**.

## Baseline and evidence method

Fetched base: `cb08d3da85ade73ada7bc5bcf38cc7178c9e6b4a`, the owner-merged PR #132. GitHub confirmed #118–123 closed, PR #127–132 merged, #124 open and no competing PR. Clean branch `issue-124-fleet-simple-visual-quality-accessibility` uses a separate worktree. Original branches/worktrees and 112 local demo assets are preserved, with SHA256 inventory outside Git.

The initial browser audit rendered 37 actual route/scenario variants: 1440×900 and 390×844 for all, plus 768×1024, 1280×800 and 1920×1080 for representative dashboards/directories/forms (101 images). Baseline has zero recorded document overflows, page errors or failed captures. This alone is not visual acceptance: screenshots were inspected for composition and interaction checks are separate. The before manifest records exact base, route, synthetic role, viewport, data fixture, source tree and unmodified PNG hash.

Authenticated interior rendering currently uses the existing separate DEV-gated entries: actual React routes with a visible ISOLATED DEMO notice, synthetic read fixtures, no Firebase/Backend session and writes rejected. Real unconfigured bootstrap and Home are separate scenarios. No real customer data, generated imagery or fictional metrics. Old conceptual references are unavailable; fidelity to them is not claimed.

The existing native Android before evidence retains its actual capture source `e820b83182000a09cc68238da4757d7d17052054`; base and that capture have the identical complete Mobile tree `43fcaec88b35773da54cd6b62faf5a064b085ef8`. Those are emulator captures with an isolated fixture banner, not physical-device or real-auth certification. Subsequent native evidence must identify its own actual source/APK. Widget-test renders are indexed separately.

## Actual inventory and traceability

Ready-state browser paths below refer to `issue124-browser/before/browser-review.json` and the eventual after manifest. Automated regression references are existing suites, retained intact, plus new targeted UI/a11y tests. Each family also requires relevant boundary-state review; an unrendered boundary is not implied by a Ready screenshot.

| Role / implemented route or widget | Actual surfaces / states | Regression boundary |
|---|---|---|
| Shared `/`, auth in each protected area, global/customer `*` | Home, role entry, SignIn, expired/loading/unconfigured/denied/not-found, drawer | AppShell/WorkspaceShell/auth tests; existing providers and filtered links |
| Admin `/admin` | Overview: existing master data and capability-filtered controls | AdminPresentation and layout tests |
| Admin `/admin/customers`, `/:customerId` | Directory, create/edit, partial Site associations, deactivate dialog | MasterDataPages/AdminPresentation tests; versioned writes |
| Admin `/admin/locations`, `/admin/sites/:siteId` | Physical Sites, address/timezone/geofence, Operational Locations and forms | Existing master-data/resource validation tests |
| Admin `/admin/drivers`, `/:driverId` | Driver identity/license/certificate, linked account/unlink, deactivate | Existing protected account/record tests |
| Admin `/admin/tractors`, `/:tractorId` | Equipment directory/detail/create/edit/deactivate | Existing master-data tests |
| Admin `/admin/trailers`, `/:trailerId` | Filters, metadata/designation, shared read view, movement history, QR history/preview/print/revoke | Existing Trailer/QR/master-data tests |
| Admin `/admin/settings` | Approved configuration, validation, stale-version reload | Existing settings/conflict tests |
| Admin `/admin/alerts`, `/:alertId` | Filters, severity/status/detail, ACK/Resolve dialogs, reason, recovery block | Alerts permissions/version/recovery tests |
| Admin `/admin/audit`, `/:auditEventId` | URL filters, cursor history, actor/time, read-only before/after and withheld fields | Audit privacy/cursor/role tests |
| Operations `/operations` | Bounded Dispatch previews, filters/search, Pending/Active/Attention | Board/OperationsPresentation tests; no total or GPS inference |
| Operations `/operations/orders`, `/:orderId` | Planning queue/selection/pagination, read-only references/Stops/cargo | Existing selection/filter/cache/read tests |
| Operations `/operations/trips`, `/new` | Directory, four planning steps, reorder/resources/review | Existing PlanTrip/idempotency/conflict/uncertain-ACK tests |
| Operations `/operations/trips/:tripId` | Assignment, executable Stops, linked Orders, actions, paginated timeline | TripDetail/actions/history tests |
| Operations `/operations/trailers`, `/:trailerId` | Exact search, Current/Last Known, coupling vs expected, paginated movements | Shared TrailerViews tests; no fuzzy/list-all behavior |
| Operations `/operations/alerts`, `/:alertId` | Shared authorized Alerts, actions/confirmations | Same permission/recovery/version boundaries |
| Customer `/customer` | Bounded request/delivery/equipment/evidence previews | CustomerPresentation/CustomerReadViews tests |
| Customer `/customer/orders`, `/new`, `/:orderId` | Filters/pages, four-step creation, edit/cancel/reroute/substitution/completion dialogs | Customer workflows, payload/ACK/version and scope retirement tests |
| Customer `/customer/trailers`, `/:trailerId` | Confidential inventory/detail and paginated current-designation history | Customer read/privacy/cache tests |
| Customer `/customer/deliveries`, `/:orderId/:stopId` | Read-only milestones, linked accepted evidence | Customer read/foreign-resource tests |
| Customer `/customer/documents`, `/:documentId` | Metadata, explicit protected download, unavailable/expired grants | Protected download cancellation/scope tests |
| Mobile `SessionView` | Sign-in/access/Tenant selection, Road/Yard, cached-vs-current verification, refresh/sign-out | Existing session controller/view/cache tests |
| Road `ShiftHomePanel`, `InspectionScreen` | On/Off Duty, Pre/Post-Trip, camera/odometer/evidence validation | Shift protocol/photo/queue tests |
| Road `RoadTripScreen`, native scanner | Assigned work/Stops, QR/Hook/Drop, arrival/service/departure and confirmation | Road execution/QR/arrival/manual confirmation tests |
| Road `RoadEvidencePanel` | BOL/POD/No Receiver, Fuel, problems, retained drafts | Evidence/media acceptance/payload tests |
| Road `DriverUpdatesPanel`, `CommandRecoveryPanel` | Changed instructions, cancellation, uncertain command recovery | Existing owner fencing and recovery tests |
| Shared Mobile `SyncPanel`, `MediaSyncPanel`, `NotificationsPanel` | Queued/sending/ACK/conflict/uncertain; finalized media vs business acceptance; push transport | Queue/replay/media/notification suites |
| Yard `YardHomePanel`, `YardActionsPanel` | Site, scan/read, physical move review, correction/discrepancy/damage | Yard scope/QR/confirm/version/OOS tests |
| Yard `YardChecksPanel` | Start/resume/scan, snapshot progress, review/cancel/complete/reconcile, offline/uncertain | Check integrity/history/reconciliation/queue tests |

Tenant CRUD, a general Users/Memberships directory and broad Admin health metrics are **not implemented screens**. Existing membership/context selectors and Driver account controls are the actual access surfaces. No new routes or capabilities are invented. Road/Yard placeholder classes are not counted as implemented screens.

## Severity-ranked defect register

Criteria abbreviations: H hierarchy/density; R responsive/reflow; A accessibility; C consistency; T operational truth. See the [actual before/after index](evidence/issue124/README.md), [per-image provenance](evidence/issue124/provenance.json) and [route-role-state matrix](evidence/issue124/route-role-state-matrix.csv). All listed corrections were visually inspected; exact final full-suite results are published after source freeze in the PR/delivery.

| ID / severity | Actual observed defect / role / scenario | Before | Correction / verification status | Criterion |
|---|---|---|---|---|
| V01 / Medium | Mobile shared chrome repeats context in tall stacked bands; Customer create title starts low in 390px viewport | `customer-create-order-390x844.png` | Compact shared header while retaining labels, all actions and authority; corrected image inspected; actual drawer/focus/reflow checks pass | H/R/C |
| V02 / Medium | Same generic icon for every sidebar module weakens scanning | All three desktop dashboards | Small local decorative SVG cues by destination; visible labels and filtered links remain authoritative | H/C/A |
| V03 / Medium | Wide tables scroll but Admin/Operations have no visible or screen-reader instruction | `operations-orders-390x844.png` | Actual-overflow ResizeObserver hint, associated description, native keyboard scrolling; reflow/cleanup tests pass | R/A |
| V04 / Medium | Raw ISO Admin metadata, browser-language Operations dates and a different Customer format lack consistent visible offset | Driver/Trip/Alert detail and Dispatch | Shared US English display formatter with actual browser offset; API/filter/input instants unchanged; propagated to Admin metadata, Audit, Operations, Alerts, shared Trailer and Customer time; seconds preserved and same-minute regression passes | C/T/A |
| V05 / Medium | Admin Overview link blocks have weak task grouping/cues across otherwise similar white panels | `admin-overview-1440x900.png` | Existing module directory gains aligned task icons, clear labels and restrained interactive surfaces; no counters/new modules | H/C |
| V06 / Medium | Native Tenant fact + verification notice + timestamp consume much of landscape/large-text workspace before any work | Original native Yard landscape / text-scale captures | One naturally sized context block; same supplied authorization/offline messages; six new reflow/semantics/actual button-access cases pass; native before/after inspected | H/R/A/T |

| V07 / Medium | Two independently failed Customer queries render identical error/Retry blocks | `qa-before/customer-500-390.png` | Named alerts distinguish Order actions from Order records; both original retries retained, tested and visually inspected | H/A/C |
| V08 / Low / deferred | Some Admin/Customer badges retain technical enum labels while Operations uses friendly labels | Directory/dashboard captures | Domain labels remain explicit; broad copy remapping deferred to owner decision, not silently treated as a business change | C/T |

The new shared table hint initially duplicated Customer's existing portal hint. Visual inspection caught this; the redundant portal hint was removed and an integration regression now enforces one shared hint. Exploratory duplicate-hint captures are superseded, not final acceptance evidence. No unresolved confirmed Critical/High visual/a11y defect was found in the reviewed scenarios; that is not blanket WCAG/device certification. Enlarged native context/time metadata still takes vertical space; it remains readable/scrollable rather than hidden or truncated.

## Scoring rubric and limits

Qualitative scale: **1** obstructed/unsafe, **2** substantial inconsistency, **3** usable with visible refinement deficits, **4** coherent and reviewed with minor disclosed limits, **5** exceptional verified execution. Use identical scenarios before/after, explain each score, and do not convert subjective scores to percentage improvements.

| Dimension | Initial reviewed assessment | Final assessment |
|---|---|---|
| Visual hierarchy | 3: work competes with tall context bands | 4: less mobile chrome and stronger task grouping; actual geometry and native comparisons |
| Design consistency | 3: same palette, repeated icons and inconsistent time presentation | 4: shared navigation cues, single overflow instruction and unified display time |
| Typography | 3: readable, date language/offset inconsistency | 4: US English, actual offset and seconds; original instants retained |
| Spacing / density | 3: usable desktop tables, mobile/context density can improve | 4: measured chrome reduction; no claim of smaller content at every text scale |
| Responsive usability | 3: local scrolling works; discovery/context height weak | 4: visible/accessibly described scrolling, required widths and native enlarged layouts reviewed |
| Accessibility | 3: semantic controls/focus exist; manual/automated review incomplete | 4 within automated scope:16 contrast pairs, keyboard/focus,12 AX name snapshots and Flutter semantics; manual limits remain |
| Action clarity | 3: existing actions retained, navigation cues weak | 4: distinct module cues and separately named failure recovery |
| Operational status clarity | 4 in reviewed examples: bounded previews and separate physical/server states | 4: original status meanings and protected code retained; no fabricated outcome |
| Empty / error quality | 3 after baseline boundary review: repeated indistinguishable query errors | 4: contextual error titles/retries, loading/empty/denied reviewed |
| Commercial presentation | 3: cohesive palette, composition still needs refinement | 4: auditor assessment from actual comparisons; owner commercial approval pending |

Scores are the auditor's explained assessment, not WCAG certification or owner approval. Physical devices, TalkBack/VoiceOver and live Firebase/Backend/storage remain separate, explicitly unverified checks. Native browser zoom must be distinguished from CSS/reflow-equivalent testing. Hosted complete CI is required for merge approval; local success does not substitute for it. No Backend/API/auth/policy/state machine/replay/Drift/native integration changes are authorized.


## Verified results, protection and explicit limits

Visual browser review: 101 after route/viewport captures,24 before boundary captures,46 passing browser checks plus12 dialog-title observations,12 accessible-tree name snapshots with zero unnamed controls in tested listings,16 measured text/control/status contrast pairs and12 before/after geometry observations. Additional Plan Trip stages and QR preview/print-media checks pass in both source trees. Page-error counts are zero for these recorded scenarios. A separate54-scenario console replay reports zero console/page errors in the recorded cases; [console evidence](evidence/issue124/console-review.json) and [boundary audit](evidence/issue124/boundary-verification.json) retain their actual code snapshot. Complete suite counts are recorded after the final freeze in PR/delivery; automation success alone was not visual acceptance.

At390px, the Customer create heading moved from568px to451.5px; Operations Orders from436px to348.5px; Admin Overview from457px to404.5px. Same synthetic scenario/viewport; no arbitrary percentage improvement or performance claim. All context/actions remain. At1440px these headings moved18px earlier. At1.8/2.0 native text, natural wrapping and scrolling remain; no minimum-height claim is inferred from fixture text lengths.

Web AST comparison preserves550 protected calls,131 operational guards and176 complete event handlers across59 feature/auth/shared-Trailer production files. Mobile AST comparison preserves203 protected calls and624 original operational guards across67 non-presentation production Dart files, plus exact bootstrap/lifecycle/QR detector functions. The modified SessionView alone retains all3 protected calls and10 guards. These are supplementary evidence; original behavioral suites remain required. Authorization/query/cache/contracts/controllers/replay/state machines/Drift/native projects/lockfiles remain unchanged; source integrity and generated-output identity are checked in final validation. One existing Trailer timeline assertion was updated to the new explicit display format, preserving the original dateTime assertion and adding GMT-offset verification; no test is deleted/skipped/weakened.

Only presentation, directly related regression tests and this audit/evidence change. No Backend, Flyway, OpenAPI, Firebase/policy, API/DB schema, QR identity, physical movement, offline idempotency/Drift or UX-08 implementation. No new framework/dependency. SafeArea, native plugins and scanner/confirmation behavior remain. Normal APK review-marker exclusion passes; DEV browser entries are excluded from normal production output and audited again in final validation.

Full frozen-revision validation commands: Web `npm ci`, typecheck, lint, complete Vitest and production build; Flutter locked install, raw Drift regeneration identity, format, fatal-info analyze and complete tests. Exact final HEAD, measured counts/errors/skips, command exits/log hashes, production exclusion, demo-asset integrity and actual GitHub Actions state are recorded in the final PR/delivery after freezing this documentation commit. APK/capture source revisions remain immutable and are bridged by complete equal Web/Mobile Git trees, not relabeled. No Backend/Web/API test result is inferred from another subsystem.

Remaining manual/environment limits: no actual authorized Firebase/Backend/R2 session; interiors use explicit isolated fixtures and reject writes/download grants. No real accepted business operation. No physical QR/printer/camera, iOS, TalkBack/VoiceOver or full WCAG certification. Browser zoom chrome is unavailable in headless review;720×450 reflow is explicitly equivalent coverage rather than a real200% browser-zoom claim. Native hardware/peripheral release checks remain #65. Configured Firebase login browser success is unverified; real unconfigured states and existing SignIn/component tests are retained. Final merge approval requires actual current-revision hosted complete CI; if Billing still blocks runners, report REMOTE CI NOT VERIFIED — BILLING BLOCKED and keep Draft. Never bypass checks, mark Ready or merge on this task.

## Commercial visual quality assessment

### Owner feedback and baseline

The owner found the first pass too austere. Its earlier qualitative scores and technical passes did not constitute owner commercial acceptance. This second pass starts from **f5031b2fd6185340720bbf904f372f33f28b8acc** on the same PR133 branch; fetched main remains cb08d3d. PR130/131/132 merges, open Issue124 and the sole Draft PR133 were reverified. No history rewrite, merge, new PR or Issue125 work. Existing evidence and all112 local demo assets remain preserved. Exact original concepts are unavailable; fidelity to those images is not claimed.

[New before/after comparisons](evidence/issue124/refinement/README.md), [full source/hash provenance](evidence/issue124/refinement/provenance.json) and [actual matrix](evidence/issue124/refinement/route-role-state-matrix.csv) distinguish current evidence from the preserved historical309-image pass.

### Deficiencies and corrections

| Finding / severity | Actual initial deficiency | Applied refinement / verification |
|---|---|---|
| R01 / Medium | Shared navigation and workspace bands read as independent stacked lists; active module had weak visual grouping | Compact role/context band, a named visual navigation section, consistent icon wells and a white active tile. All authorized links, context, Refresh/Sign out, drawer and focus behavior remain. |
| R02 / Medium | Admin's equally wide Master data and Tenant controls panels left unstructured space and weak priorities | Main task directory plus narrower control rail at wide desktop; single reading-order column below1200px. Existing module icon/label/description/link and permission predicates retained. |
| R03 / Medium | Dispatch repeated Pending, Attention and Active sections vertically with equal weight | Pending planning work with an Attention rail above a full-width Active Trips table. Bounded preview captions, actual records, filtering and DOM reading order retained. Stacks below1280px; no totals or invented metrics. |
| R04 / Medium | Customer recency, six state previews and resource previews formed a wall of equally treated text | Recent requests alongside a compact2×3 state preview group, with one clear parent bounded-preview disclaimer; remove only repeated state-card helper copy. All seven previews and all six state query filters are explicitly regression-checked. Resource groups and actual linked evidence remain. A taller exploratory layout was rejected after visual inspection. |
| R05 / Medium | Tables lacked row/identifier emphasis; record metadata and histories appeared as text dumps | Restrained alternate row surfaces, stronger identifiers, consistent compact headers, square semantic chips, ruled fact groups, distinct timeline entries and clearer section/action separation. No value, state meaning, history order or pagination changed. |
| R06 / Medium | Plan stages lacked consistent visual progression; an initial refinement duplicated numerals and split Order IDs | One decorative numeral plus original accessible step name; original enabled/disabled/state handlers unchanged. Planning identifiers remain intact inside the horizontally scrollable table. Bad exploratory captures retained externally and superseded. |
| R07 / Medium | Flutter headings, work records and status/action boundaries lacked visual structure | Teal heading rule, consistent blue section icon wells, naturally sized Stop/record surfaces, compact status badges, separated Road status/action/expected Trailer, subtle AppBar elevation and consistent form rhythm. No hidden fields, fixed text heights, suppressed scaling, callback or guard changes. |

Reviewed priority screens: Admin Overview/Customers/Trailers/Audit; Operations Dispatch/Trip Detail/Trailer Detail/Plan Trip; Customer Dashboard/Orders/Deliveries/Trailer Detail/Documents. Each has actual before/after390/768/1440 browser renders, with relevant1280/1920 variants. Android before/after uses actual Road Home, Assigned Trip, synchronization, Yard Home and Yard movement entry; additional after captures cover recorded Trailer state, canceled move review, large phone and landscape. Actual offline/conflict/queued-state visuals use explicitly labeled widget-test-engine renders, **not emulator screenshots**.

### Commercial quality matrix

Scores use the existing qualitative1–5 rubric:1 unusable,2 visibly rough,3 functional/basic,4 intentionally composed and reviewable,5 exceptional and owner validated. They are audit aids, not percentage gains or owner approval. The baseline is the first-pass PR, not pre-redesign main.

| Dimension | Initial assessment | Current assessment / evidence |
|---|---|---|
| Visual hierarchy |3: similarly weighted panels |4: Admin control rail, Dispatch Attention rail, Customer recency/state hierarchy |
| Design consistency |3: mixed table/heading/action rhythm |4: shared quality layer, retained approved Fleet palette and analogous native section cues |
| Typography |3: weak identifier/metadata distinction |4: differentiated headings, labels, tabular figures and quieter readable metadata |
| Spacing / density |3: long equal-width stacks |4: purposeful desktop composition, compact rows; native natural height may still require scrolling |
| Responsive usability |3: functional stacking |4: all13 priority Web routes reviewed at three required widths, controlled table scrolling, actual large/landscape native review |
| Accessibility |4: earlier scoped checks |4: focus/drawer/labels/contrast and scaling retained; no certification claim |
| Action clarity |3: status/action adjacency and plain action links |4: separate primary/secondary/danger hierarchy and Road status/action spacing |
| Operational truth |4: existing explicit meanings |4: preserved physical/expected, current/last-known, queued/ACK, uploaded/accepted distinctions |
| Empty/error states |4: contextual earlier corrections |4: retained messages/retries with current boundary-state review |
| Commercial presentation |3: owner found first pass austere |4: auditor assessment from actual comparisons; owner decision explicitly pending |

### Constrained Sync regression found during complete validation
+
+The first complete candidate7ac7f6e produced671 passes and1 failure: the standalone Sync panel overflowed by4px after the new record spacing. This was a genuine presentation regression, not attributed to infrastructure or hidden. The original test remains intact and is strengthened to reflow at320×480 and scroll the Review action into view. FleetScrollSurface now scrolls only when the panel has bounded height; embedded panels keep natural height and the parent workspace scroll. No command, state predicate, query, callback, retry or owner guard changed. The corrected focused Sync/context run passed12 cases. Native APKs and after evidence were regenerated from the corrected immutable source; the complete final suite is repeated on the new frozen HEAD. Failed-candidate logs remain preserved separately.
+
+The first complete Web run on6e483e6 had644 passes and1 stale presentation-copy assertion: it required the same bounded-preview disclaimer seven times. The shared disclaimer intentionally replaces those repeated paragraphs. The unchanged test is adapted to verify all7 scoped articles, all7 actual preview reads, limit5 and exact Customer on every read, the shared no-total disclaimer and retained Recent preview copy. Other original privacy/no-dispatch checks remain; the focused Customer suites pass66 cases. The failed full-run logs are preserved and the full final validation is repeated. No assertion about scope, state or limits is removed to gain a pass.
+
+### Protection, tests, risks and pending work

The Web AST audit still preserves550 protected calls,131 operational guards and176 complete event handlers across59 production feature/auth/shared-Trailer files. Flutter presentation changes preserve operational AST checks and bootstrap/lifecycle/QR functions; complete final regression results, log hashes and exact final SHA are published in the updated PR/delivery after this evidence-only freeze. Focused Web150 and Flutter99 cases passed; these do not replace full final suites. Both normal and isolated Android debug x86_64 builds succeeded from an immutable202-file Mobile archive; normal APK fixture markers are absent. Actual source-tree equality bridges the native1c05a97 and browser79d4c6f captures to the final documentation-only HEAD without relabeling capture metadata.

Browser replay:49 before and49 after priority captures,50 further boundary/dialog/reflow images,46 PASS checks plus12 dialog observations,12 accessible-name snapshots,16 contrast pairs and30 console visits with no recorded page/console errors. Drawer Escape/focus return, keyboard scrolling, action dialogs and unchanged accessible Plan step names are reviewed. Additional isolated widget captures exercise actual offline and sync-state production widgets with local fixtures. Three early native navigation attempts and an inherited-geometry batch were excluded after inspection; visible-control bounds and semantic route assertions were added before accepted native comparisons. They are not counted as successful route evidence.

No Backend/OpenAPI/Firebase/role/RLS/query/cache/API contract/offline replay/command/state machine/movement/Drift/native project/dependency changes. No real operational write, QR authorization, physical movement or protected download is claimed. Confirmation was canceled with Not Yet. Existing suites are retained, with stronger assertions for all Customer preview scopes/states. Git diff whitespace warnings from the first pass are cleaned up. Prior native/plugin warnings and actual CI annotations remain visible, not suppressed.

Pending/limitations: owner commercial review; Low technical-enum copy consistency remains an owner decision. Actual browser-chrome200% zoom, real Firebase/Backend/R2 authentication/download grants, physical device/camera/QR/printer, iOS and TalkBack/VoiceOver remain unverified.720×450 reflow is equivalent coverage only. Existing Vite development-tool security finding is separate from this visual refinement; dependencies remain unchanged. Native keyboard/camera guards and SafeArea remain unchanged, with existing tests/evidence preserved; this pass does not assert fresh physical integration certification. Hosted full current-HEAD CI is required for merge approval; the latest actual run and Billing status are reported in the PR. No Ready action, check bypass, merge or UX08 work.

## Final M1/L1 correction record

The owner authorized closing the two remaining in-scope findings while GitHub Actions remains unavailable. The prior independent review found M1 Medium (initially clipped cancellation heading at390×844) and L1 Low (technical Customer state labels). Both are corrected in the final source indexed by [the correction manifest](evidence/issue124/final-fixes/provenance.json); this section supersedes their earlier unresolved/deferred status, including V08.

M1 uses only Operations dialog CSS: the heading stays visible and its opaque surface covers the top inset while the existing content scrolls. Scroll padding keeps keyboard-focused form controls below the heading; long existing field errors wrap. The shared dialog lifecycle, initial Cancel focus, Escape/busy behavior, focus restoration, required reason/instructions, physical confirmation, API payload/version/idempotency and Trip state machine remain untouched. No command was confirmed in visual fixtures.

L1 introduces a pure Customer presentation label map used by badges, existing state/kind filters, record facts and contextual copy. Examples: In progress, Partially delivered, Canceling, Canceled, Location unknown, In transit, Out of service, Uploaded and Signed BOL. Explicit option values remain the original enums; unknown future values remain explicit rather than receiving an invented interpretation. BOL/POD identifiers remain recognizable. Tone and authorization predicates remain exactly as before: Uploaded is not accepted, Delivered is not Completed, planned equipment is not physical coupling, and Unknown is not success. No query, contract, cache, permission, download or mutation logic changes.

Regression additions retain existing semantic assertions and add22 state-label/tone cases plus one named cancellation validation/focus case (focused148PASS across the four affected suites). The optional real-browser regression verifies22 cases and26 actual screenshots at the immutable capture source; broader replay46PASS plus12OBSERVED dialog observations and16 contrast pairs are separately recorded. Screenshot observations led to wrapping the existing long validation message and covering the sticky heading's top inset. Browser helper readiness corrections are disclosed; assertions/deadlines were not suppressed to obtain success.

### Correction acceptance snapshot

PASS means a check actually executed locally. FAIL means a confirmed remaining defect. BLOCKED identifies unavailable infrastructure/external dependencies. NOT VERIFIED identifies checks not executed; it is never a substitute for PASS.

| Requirement | Status at capture source | Evidence / boundary |
|---|---|---|
| M1 title at390×844 and narrow reflow | PASS | Seven viewport cases, heading geometry, form focus visibility, required-field feedback, wrapping and Escape/Cancel restoration; actual before/after screenshots |
| L1 Customer labels with unchanged state meaning | PASS |28 explicit label/tone cases, all five Customer route families at390/768/1440; Uploaded/Delivered/Unknown retain original tones |
| Original filters and operational commands | PASS focused regression | Actual request assertions retain raw enums, exact Customer scope and bounded previews; command handlers and guards unchanged |
| Current browser keyboard/labels/motion/contrast | PASS scoped |22 focused +46 broader checks,15+12 AX name snapshots, reduced-motion replay,16 contrast pairs; not full accessibility certification |
| Required complete local Web/Flutter/CI-policy gates | NOT VERIFIED in this capture snapshot | Final evidence-only HEAD is validated after this source/evidence freeze. The final PR report supplies actual commands, counts, failures/skips, hashes and exact HEAD. No earlier counts are reused as final results. |
| Actual browser-chrome200% zoom / manual assistive technology | NOT VERIFIED |720×450 equivalent reflow and automated Flutter/CDP semantics are separate evidence, not manual certification |
| Live Firebase/Backend/R2 authorization/grants | BLOCKED | Environment not configured; synthetic fixture writes rejected, no real protected download claimed |
| Hosted GitHub Actions | NOT VERIFIED — BILLING BLOCKED | Owner constraint; no attempt to resolve Billing, rerun/bypass policies, disable workflows or change branch protection |
| Owner visual/merge decision | BLOCKED | External owner review still required; keep the same PR Draft and unmerged |

The complete final acceptance matrix in PR133 supersedes the capture-time validation status after the final local gates execute. The existing native evidence remains bridged through the unchanged Mobile tree; this correction adds real browser images, not new emulator or widget screenshots.

### Pre-existing dependency advisory (separate scope)

Vite8.3.2 remains unchanged from main. The known High Windows launch-editor advisory and related dev-server advisories require separate owner security review; the maintainer identifies8.3.3 as a patched8.3 version. No dependency/lockfile upgrade or clean security-audit claim is made in UX07. See https://github.com/vitejs/launch-editor/security/advisories/GHSA-587h-7wmj-h5pm . Complete final local results do not resolve this pre-existing advisory or confer staging/production readiness.
