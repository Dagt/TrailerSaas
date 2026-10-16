# TrailerSaaS — UI/UX Redesign Delivery Roadmap

**Parent Epic:** [#117](https://github.com/Dagt/TrailerSaas/issues/117)  
**Planning baseline:** `f876debe35fbe5891dd0ff15f5f5b8190a581073`  
**Status:** **Fleet Simple (Option D) owner-approved on 2026-10-16**; documentation PR #126 still requires manual merge before Codex starts UX-01.

| Phase | GitHub | Issue | Depends on | Outcome |
|---:|---|---|---|---|
| 0 | [#117](https://github.com/Dagt/TrailerSaas/issues/117) | Parent Epic/planning docs | PR #116 merged | **Fleet Simple selected**, documentation PR #126 awaits manual review/merge |
| 1 | [#118](https://github.com/Dagt/TrailerSaas/issues/118) | UX-01 Design System | PR #126 merged; Fleet Simple approved | Light-sidebar Web tokens/components and Flutter theme mapping |
| 2 | [#119](https://github.com/Dagt/TrailerSaas/issues/119) | UX-02 Web shell | #118 | Responsive role-aware navigation and login states |
| 3 | [#120](https://github.com/Dagt/TrailerSaas/issues/120) | UX-03 Tenant Admin | #118, #119 | Master data, equipment, QR, settings, alerts, Audit |
| 4 | [#121](https://github.com/Dagt/TrailerSaas/issues/121) | UX-04 Transport Operations | #118, #119 | Dispatch, orders, trips, trailers, timelines |
| 5 | [#122](https://github.com/Dagt/TrailerSaas/issues/122) | UX-05 Customer Portal | #118, #119 | Order, delivery, trailer, document self-service UI |
| 6 | [#123](https://github.com/Dagt/TrailerSaas/issues/123) | UX-06 Flutter Road/Yard | #118 | Mobile field UI, offline/error/media/QR states |
| 7 | [#124](https://github.com/Dagt/TrailerSaas/issues/124) | UX-07 Visual QA/accessibility | #120–#123 | Cross-platform consistency and screenshots |
| 8 | [#125](https://github.com/Dagt/TrailerSaas/issues/125) | UX-08 Commercial demo assets | #124 | Actual product PNGs/optional recordings and English captions |

**Implementation order:** UX-01 → UX-02 → UX-03/UX-04/UX-05 (each separately reviewed); UX-06 can be developed independently in parallel after UX-01. UX-07 then UX-08.

**One issue = one Draft PR; no automatic merge.** Each feature PR gets owner review, screenshots, accessibility evidence, exact SHA tests and a clearly marked blocked/unverified CI result as necessary. Do not close/repurpose #63, #64, #65, which govern backup/recovery, staging/deploy/observability and production QA respectively.

## Decision log

- **Design direction:** **Fleet Simple (Option D) APPROVED**, 2026-10-16; prior A/B/C directions superseded.
- **Style guardrails:** light gray sidebar, gray canvas, white surfaces, blue primary actions, restrained teal accent; avoid dark-heavy and over-minimal styles.
- **Planning-PR gate:** manual merge of #126 required before #118 implementation from `main`.
- **Implementation authorization:** #118 may begin only after #126 is merged; all code PRs remain Draft/NO MERGE.
- **Backend/auth/storage changes:** not authorized.
- **Demo data:** isolated, non-sensitive, clearly labeled; never pretend a generated concept is a running system screenshot.

## Documentation index

1. [Master plan](frontend-redesign-master-plan.md)
2. [Design system](design-system.md)
3. [Screen inventory](screen-inventory.md)
4. [Visual acceptance criteria](visual-acceptance-criteria.md)
5. [Interaction / English copy standards](interaction-content-standards.md)
6. This roadmap and issue map

The owner has **selected Fleet Simple**. After reviewing and manually merging documentation PR **#126**, an implementation agent may start **#118 only**, branching from the updated `main`. No agent should recreate the already published Epic or issues.
