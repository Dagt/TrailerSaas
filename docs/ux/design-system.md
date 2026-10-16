# TrailerSaaS — Proposed Design System

**Status:** Proposal, pending owner approval. Baseline PR #116 in main. Applies to React Web and Flutter Mobile presentation only; does not change authorization, API contracts or domain semantics.

## Visual directions

| Direction | Look | Strength | Risk |
|---|---|---|---|
| A. **Fleet Command** (recommended) | White/cloud canvas, deep navy sidebar, cobalt primary buttons, restrained teal accents | Premium, readable data-dense enterprise logistics | Requires disciplined use of color |
| B. **Industrial Signal** | Steel/charcoal, high-contrast indicators | Strong yard/operations character | Can be visually heavy |
| C. **Atlas Minimal** | Light stone/white, graphite, understated blue | Clear and calm Customer Portal | Can under-emphasize exceptions |

**Approval gate:** select direction or modifications in writing before UX-01; reference screens: Admin list, Operations dashboard, Customer deliveries, mobile Road/Yard.

## Direction A candidate tokens (NOT final)

| Semantic token | Suggested | Use |
|---|---|---|
| canvas | `#F5F7FA` | Main background |
| surface | `#FFFFFF` | Cards/dialogs |
| surface subtle | `#EFF3F8` | Group headings |
| sidebar | `#122039` | Navigation |
| primary text | `#142238` | Main copy |
| secondary text | `#52647A` | Secondary copy |
| primary action | `#1D4ED8` | Main CTA |
| teal accent | `#087F8C` | Supporting emphasis |
| border | `#D9E1EB` | Outlines |
| focus ring | `#2563EB` | Keyboard focus |

Create semantic status palettes for neutral, success, warning, danger, offline/unknown. Each needs foreground/background/border/icon tokens; do not use color alone for meaning. Validate contrast before adoption: WCAG 2.2 AA target (normal text 4.5:1, large text and nontext indicator 3:1). No assumed compliance.

## Type, spacing, density

- System sans serif or a reviewed bundled font; avoid network-font runtime dependencies. Tabular numerals for IDs, dates and KPIs.
- Web: page title 24–28px, section 18–20px, body 14–16px; micro text should remain readable.
- Flutter: legible text scaled for device accessibility; primary actions >=16sp where feasible.
- Spacing scale: 4, 8, 12, 16, 20, 24, 32, 40px. Radius 6–12px.
- Density: desktop tables support compact and comfortable; mobile retains generous actionable space.
- Avoid overuse of gradients, shadows, decorative charts and artificially large whitespace.

## Layout

- Shared Web: skip link, persistent product identity, distinct Tenant/Customer context, role-aware sidebar, visible active section, responsive top bar and explicit sign-out.
- Admin: data-first master lists, details, editable forms, QR/print, Alerts and read-only Audit.
- Operations: high-density board, clear current states, filters, actions and operational timeline.
- Customer: lightweight secure dashboards, orders, deliveries, trailers and documents.
- Flutter: preserve distinct Road Driver and Yard Spotter workflows, offline actions, scan and evidence states. No attempt to turn the mobile app into desktop Web.

Test 390×844, 768×1024, 1280×800, 1440×900, 1920×1080 plus 200% zoom and 320px edge; CSS breakpoints are implementation decisions, not substitutes for these tests.

## Component contract

| Component | Essential states |
|---|---|
| Navigation | active, keyboard, narrow/drawer, no permission, Tenant switch |
| Button | primary, secondary, quiet, destructive, disabled, pending |
| Inputs | label, help, validation, error, long value, focus |
| Tables | empty, loading, row actions, scrolling, filters and pages |
| Cards/KPIs | real source labels, incomplete data, unknown value |
| Status badges | text + icon + palette, not color-only |
| Detail lists | null/unknown, IDs and dates |
| Timelines | actor/time/reason if authorized, pagination |
| Dialogs | accessible focus return, destructive confirmation, disabled actions |
| Feedback | denied, loading, offline, retry, uncertain ACK and failure |
| Mobile controls | >=44px recommended tap size, safe area, offline indicators |

## Interaction and motion

All meaningful controls keyboard accessible; focus states visible; dialog focus trapped/restored. Reduced-motion support; subtle 120–200ms transitions only for orientation, never animation suggesting live GPS. No action available solely on hover. Handle long IDs, screen readers and zoom. UI must remain fully **US English**.

## Domain-safe display invariants

**Current ≠ Last Known location; UNKNOWN ≠ at last site; expected Trailer ≠ coupled Trailer; uploaded file ≠ accepted POD; DELIVERED ≠ COMPLETED; audit ≠ physical movement timeline.** Do not simulate live GPS, ETA, missing shipments, accepted documents, or activity metrics.

## Implementation governance

No paid UI library. A dependency must have documented license, accessibility, bundle impact, theme/maintenance cost and approval. Migrate `web/src/styles.css` and `web/src/shared/ui/components.tsx` incrementally; do not rewrite business hooks, API clients, Firebase/RLS or Flutter Drift. Tokens map to both CSS and Flutter ThemeData after visual approval. Owner selects direction: **PENDING**.
