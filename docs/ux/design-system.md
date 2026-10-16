# TrailerSaaS — Fleet Simple Design System

> **APPROVED VISUAL DIRECTION:** **Fleet Simple (Option D)** — explicitly chosen by the product owner on 2026-10-16. This supersedes earlier proposals A Fleet Command, B Industrial Signal and C Atlas Minimal.  
> **Baseline:** PR #116 merged; planning documentation PR #126. **UX-01 implementation remains pending** until the planning PR is reviewed/merged.  
> **Scope:** Visual design and presentation consistency for React Web (Tenant Admin, Transport Operations, Customer Portal) and Flutter (Road Driver, Yard Spotter), with no business behavior/API/auth/data-contract changes.

## 1. Design intent and non-negotiable character

Fleet Simple is **modern, clean, elegant, approachable and credible**. It should feel like finished commercial enterprise software, not an austere developer scaffold **and not** a noisy control-center concept. A US trucking-company dispatcher must scan dense operations quickly; Customers need a calm self-service view; mobile field workers need high-contrast actions and clear offline states.

Prioritize: **clarity > everyday usability > consistent identity > restrained polish**.

**Approved visual choices:**
- **Light sidebar** (soft slate / light gray), not a dominant black/navy panel; modern selected/hover states, compact tasteful icons and clear labels.
- **Soft light-gray canvas** and **white panels**; limited shadows, fine borders and ample but efficient spacing.
- **Blue primary actions**, **teal as restrained supporting accent**, semantic warning/success/danger colors *only where meaningful*.
- Simple typography, legible compact enterprise tables, clearly separated heading/filter/content areas, understated cards, controlled microinteractions.
- Consistent visual vocabulary across three Web workspaces and both Flutter roles; role-specific information density rather than independent themes.
- US English throughout. The actual data source and permission boundaries remain authoritative.

**Explicitly rejected:** dark/dominant sidebar from Option A, industrial heavy dark chrome from B, austere personality-free minimalist treatment from C, gratuitous dashboards/charts, glowing gradients, saturated shadows, excessive cards, heavy motion, emoji-based primary navigation, fabricated GPS/ETA/metrics.

## 2. Candidate production tokens — approved direction, adjust exact values for accessibility

The *direction and family* are approved. Hex values are initial implementation targets, **not an assertion of WCAG compliance until measured**. UX-01 may slightly adjust individual tones after contrast/browser testing; any material shift to a dark or visually busy theme requires owner approval.

| CSS/semantic intention | Candidate | Usage |
|---|---|---|
| `--color-canvas` | `#F5F7FA` | App background |
| `--color-surface` | `#FFFFFF` | Cards, tables, panels, dialogs |
| `--color-surface-muted` | `#F8FAFC` | Secondary regions and sidebar |
| `--color-sidebar` | `#F8FAFC` | **Light sidebar** |
| `--color-sidebar-active-bg` | `#DBEAFE` | Selected nav pill/block |
| `--color-sidebar-active-text` | `#1D4ED8` | Selected nav label |
| `--color-text` | `#1E293B` | Primary content |
| `--color-text-secondary` | `#475569` | Supporting descriptions |
| `--color-primary` | `#2563EB` | Primary CTA, links/focus family |
| `--color-primary-hover` | `#1D4ED8` | Primary hover/focus emphasis |
| `--color-accent` | `#0F766E` | Secondary teal accents |
| `--color-border` | `#E2E8F0` | Light separators |
| `--color-focus` | `#2563EB` | Visible focus ring (verify against adjacent surfaces) |

Text on primary/teal filled buttons should be white only when verified to satisfy 4.5:1; similarly each status-chip foreground against chip fill must pass contrast tests. Avoid displaying secondary gray microcopy that fails contrast.

Define semantic `status` tokens for `neutral`, `success`, `attention`, `danger`, `unknown` and `offline/pending`. Each needs text/icon/border/background. Never rely on color alone. **Do not use success green for UNKNOWN, pending upload or unsigned evidence.**

The UI system must have a single source of truth for CSS tokens and an equivalent Flutter `ThemeData` mapping in the later mobile implementation. Avoid independent ad hoc local palettes.

## 3. Typography, spacing, dimensions

- Use a crisp local/system sans-serif font stack initially; any bundled font requires documented license and bundle/size decision. No runtime third-party font network dependency.
- Suggested Web scale: page 24–28px, section 18–20px, body 14–16px, supporting 12–13px only where legible. Mobile normal text ideally >=14sp, primary actions >=16sp when practical.
- Use a consistent 4/8/12/16/20/24/32px spacing grid and conservative 6–12px radii; restrained shadows only for appropriate elevation.
- Primary panels white, with subtle borders; KPI cards should be **few and genuinely supported** (no empty fake metrics or a wall of cards).
- Dense desktop data views permit `comfortable` and `compact` table density, with keyboard/zoom-safe layouts. Customer views can use slightly roomier spacing. Mobile touch targets ~44 logical px or greater as a preferred target.
- Toolbars: obvious search, few well-chosen filters, primary action clearly distinguishable, limited decorations. Tables: clean headers, precise alignment, meaningful status chips, predictable actions, overflow without information loss.
- Prefer a desktop light sidebar and compact header; on narrow viewports use a discoverable collapsible drawer or stacked nav. Do not create a new authorization/navigation architecture in the design foundation PR.

## 4. React Web primitives (UX-01 ownership)

The initial Design System PR **only** provides reusable visual building blocks and representative examples, without performing the wholesale route redesign reserved for later issues:

1. Semantic global tokens and CSS reset/typography boundaries.
2. Button: primary / secondary / subtle / destructive, busy/disabled/focus.
3. Input / select / textarea: labels, hints, validation, error, disabled.
4. Card / panel / section headers / metric block (with factual metadata).
5. Data table: desktop density, empty/loading/responsive overflow.
6. Status badge: text + semantic palette + optional approved icon.
7. Detail list / entity reference / date formatting presentation.
8. Timeline: event/time/source when allowed, cursor/Load more styles.
9. Dialog: accessible destructive confirmations and focus return.
10. Error, denied, empty, loading, offline/pending states.
11. Optional icon-family recommendation with a license, accessibility and size decision; do **not** bulk-add dependencies without review.

Introduce token/component changes incrementally and backward-compatibly. The existing `web/src/shared/ui/components.tsx` and `web/src/styles.css` are starting points. Avoid forcing all old routes into the new markup during UX-01. A component showcase may be developer-only or test-only, clearly marked **concept/representative UI**, not a running operational business flow.

## 5. Flutter mapping (foundation only)

Prepare a documented mapping of the Fleet Simple design to Flutter `ColorScheme`, `ThemeData`, typography and spacing. UX-01 may implement a minimal theme foundation if it can be safely proven without changing existing functionality. **Complete Road/Yard screens belong to UX-06 (#123)**; no Mobile/Drift schema or offline-sync changes.

Field-specific requirements: sunny/outdoor legibility, readable status text, large explicit actions, consistent error/offline indicators, text-scale and safe-area support. Flutter may use subtle higher-contrast adjustments while keeping the same Fleet Simple family.

## 6. Layout examples to validate (not a new feature specification)

- **Admin sample:** light left nav; simple "Trailers" heading and primary action; fine-bordered white table, search, filters, text+status badge.
- **Operations sample:** slim status summary with only **real** metrics; clear Trips table, active exceptions and historical event timeline; compact but calm.
- **Customer sample:** clear Orders/Deliveries list and accepted document metadata; no internal Driver/Tractor/audit information.
- **Mobile sample:** Road/Yard task card and a disabled/offline state with genuine domain copy.

Screenshots from an actual app or component harness must say whether they show a *real route* or *static component demonstration*. **Sample counts/names cannot be represented as live customer records.**

## 7. Accessibility and motion

Target WCAG 2.2 AA: normal text >=4.5:1, large text and nontext indicators >=3:1 where applicable. Test actual color pairs in final components. Focus visible, logical tab flow, semantic headings, dialog focus/restoration, reduced motion and text scaling. Motion restrained to subtle ~120–180ms orientation transitions; no animation that looks like real-time positioning. Validate at Web 390×844, 768×1024, 1280×800, 1440×900, 1920×1080 and 200% zoom.

## 8. Source-of-truth restrictions

- **Current location** ≠ **Last known**; UNKNOWN ≠ currently at last known site.
- **Expected/Planned Trailer** ≠ **physical coupling**.
- **Uploaded POD** ≠ **accepted evidence**; pending/offline ≠ server-committed.
- **DELIVERED** ≠ **COMPLETED**; **Audit** ≠ **operational movements**.
- Never introduce a live GPS location, ETA, map or success state not supported by existing APIs.
- Never widen Customer/Tenant data visibility, alter Firebase/auth, persist signed URLs or change backend endpoints.

## 9. Review gates

**Gate 0 — APPROVED:** user has selected **Fleet Simple (Option D)**. Do not ask Codex to reselect another visual direction.

**Gate 1 — Pending:** planning PR #126 must be manually reviewed/merged before UX-01 starts on updated `main`.

**Gate 2 — UX-01 owner review:** component gallery and representative screenshots reflect light-sidebar Fleet Simple, contrast/accessibility evidence, and all affected regression tests pass. PR Draft, **NO MERGE**.

**Gate 3:** follow-on UX-02–08 adopt these tokens and preserve functionality. Owner approval required for any material visual direction change.

**Approval decision:** Fleet Simple selected and confirmed by product owner, 2026-10-16. Component details remain implementation-reviewable; the overall style is no longer undecided.
