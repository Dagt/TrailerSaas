# UX-05 — Visual Quality Gap Report

Owner feedback: UX-01–04 implementation appears more austere/basic than the approved conceptual direction. UX-05 must improve actual composition, hierarchy, proportions and daily usability while keeping Fleet Simple and all security/workflow boundaries. This report records scope-limited corrections and future consolidation considerations; it does not authorize or implement UX-07.

## Available comparison material

Approved written references: `docs/ux/design-system.md`, master plan, visual acceptance criteria and content standards. Actual UX-01–04 captures and this PR's exact-base before images are available. The original approved conceptual Option D image was **not found** in tracked assets or the preserved local demo assets. No pixel fidelity to that unavailable mockup is claimed. Prior implementation captures are evidence of baseline behavior, not a substitute conceptual approval.

## Corrected within UX-05

| Material gap in base | Correction | Rendered review target |
|---|---|---|
| One large enclosing white frame with unrelated card stacks | Customer canvas visible between distinct heading, tools and content regions | Dashboard, Orders and all read/detail routes |
| Twelve equally weighted preview cards | Two named activity/resource groups, full-width recent requests, compact state cells and balanced resource grid | Honest bounded dashboard, populated and empty |
| Raw ISO timestamps and low-value visual competition | US English CustomerTime with canonical instant/local-time tooltip; identifiers/status emphasized, dates secondary | Orders, progress, evidence and document metadata |
| Flat cargo text and long undifferentiated detail lists | Labeled quantity/pallet/weight blocks, reference summary, cargo/itinerary composition and separate actual delivery progress | Order detail and creation review |
| Creation-stage buttons and nested fieldsets visually indistinct | Numbered four-stage indicator, two-column grouped controls, readable address labels, renewed-review control and linked validation | Information, Cargo, Stops, Review and conflict/uncertain states |
| Generic red confirmation for every dialog | Customer-only blue benign confirmations; cancellation remains explicitly destructive | Edit, complete, reroute, substitution, cancellation |
| First Stop reorder control inherited misleading primary styling | Neutral reorder actions and clearly disabled boundary controls | Keyboard Stop order review |
| Narrow tables offer little indication of additional fields | Explicit narrow-screen scroll guidance plus named focusable regions, all columns retained | Mobile/tablet Orders, inventory, deliveries and documents |
| Native 1280px review exposed badges shrinking into vertical letters | Customer table badges keep complete labels on one line; table overflow stays local | Trailer inventory at all reviewed widths; checked again after correction |
| Mobile image inspection exposed a desktop grid span creating implicit narrow columns | Explicitly reset resource-cell spans at the narrow breakpoint; verify each preview occupies the full column | Mobile dashboard; structural browser check added in addition to document overflow |
| Accepted evidence and file access share one plain area | Separate publication/acceptance columns, metadata/association surfaces and secure authorization section | Documents and delivery details; R2-unavailable capture |

Changes use the existing palette/tokens, understated borders/shadows and system font; no alternate identity, graphics, unsupported counters, GPS or ETA. No new data fetching is used for visual polish.

## Shared design issues for future UX-07 consideration

| Observation | Scope decision / evidence |
|---|---|
| Shell repeats brand, workspace title, Tenant and verified context across several rows; consumes substantial mobile height | Retain security/context ownership. Customer spacing is tightened locally; review shared information hierarchy across portals later without moving protected Outlets |
| Global CSS makes the first `.actions` button blue and generic dialog confirms red independently of actual action meaning | Local Customer overrides correct affected workflows. A shared semantic API consolidation needs all-portal review, outside this issue |
| Enum labels and browser date formatting vary across earlier portal implementations | Customer dates now consistently US English and expose canonical instants. Raw state labels retain exact meaning; a common display-label/date policy needs broader review |
| Wide multi-dimensional operational tables require local horizontal scrolling on phones | All critical fields retained with visible guidance. A cross-portal responsive table policy should assess accessibility and information priority with real users |
| Dense historical identifiers and repeated truth/scope notices can compete with primary information | Grouped and visually subordinated within Customer. Do not remove authorized references or security-relevant distinctions merely for screenshots |

Admin/Operations sources and global tokens/primitives are not redesigned. Browser parity compares existing non-Customer routes with the candidate Customer CSS loaded explicitly. This does not constitute a whole-product UX-07 audit.

## Differences from approved direction and remaining limits

- The implementation follows the available light Fleet Simple specification. The unavailable conceptual reference prevents image-to-image fidelity measurement; owner commercial-quality review remains pending.
- Real React screenshot evidence is an **isolated synthetic read environment**, not a configured Firebase/Backend session. Actual product captures show the truthful unconfigured sign-in condition. No mockup or generated image is used as product evidence.
- Native zoom/assistive technology/physical devices and live authorized Firebase/R2 command completion remain unverified. CSS reflow, native Edge keyboard/modal behavior, contrast and overflow are checked separately.
- An existing additional-page read error can replace Customer movement rows with error feedback. It is documented without changing query/error semantics or silently expanding into functional work.
- Actual signed-link expiry is covered by historical tests, not by issuing an invented operational grant in the demo. BOL references, published files and accepted POD remain distinct.

The implementation record and browser manifest bind route coverage and visual checks to the reviewed source revision. Final SHA validation is published in the Draft PR/delivery archive. No future issue is started or created by this report.

## Rendered quality review

Reviewed native Edge candidate `0c4b61a2f292f431cebb80cf843012c6ddf2bbf6`: 113 indexed images, eighteen browser checks, zero console/page errors, ten measured passing contrast pairs. The implementation record links examples and provenance. Evaluation is against the available written specifications and exact-base screenshots; aesthetic owner approval remains pending in the Draft PR.

| Criterion | Observed result and practical limit |
|---|---|
| Composition and balance | Recent requests have primary weight; resource cells form balanced desktop groups and a verified full-width mobile stack |
| Typography and hierarchy | Distinct workspace/page/section levels; identifiers and status carry emphasis; timestamps and bounded-preview notes are secondary |
| Spacing and consistency | Token spacing, aligned toolbars and consistent panel headers; two-field creation/editor grids collapse cleanly |
| Table density/readability | All existing columns retained; complete badge labels verified at four widths; wide tables scroll locally |
| Navigation clarity | Existing five-section Customer navigation, selected state, direct relationship links and verified context retained |
| Status clarity | Exact domain text remains visible; Delivered/Completed, Current/Last Known, publication/acceptance and planning/coupling stay distinct |
| Form usability | Four real stages, labeled cargo/address fields, keyboard Stop ordering, disabled/review guards, version/conflict/uncertain feedback and dialog focus verified |
| Responsive behavior | All ten route variants captured at four viewports; all inspected again at 1920px, 320px and 200%-equivalent reflow; native zoom is unverified |
| Accessibility and contrast | Native keyboard/drawer/modal checks, focus/region semantics, reduced motion and ten contrast pairs pass; full assistive-technology certification remains separate |
| Perceived SaaS quality | Enclosing scaffold/card clutter, raw-date emphasis and undifferentiated form/detail composition are corrected; no fidelity claim to unavailable conceptual images or owner approval is implied |
