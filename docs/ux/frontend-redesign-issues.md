# TrailerSaaS — UI/UX Redesign Delivery Roadmap

**Parent Epic:** [#117](https://github.com/Dagt/TrailerSaas/issues/117)  
**Planning baseline:** `f876debe35fbe5891dd0ff15f5f5b8190a581073`  
**Status:** initial plan; visual direction requires owner approval before UX-01.

| Phase | GitHub | Issue | Depends on | Outcome |
|---:|---|---|---|---|
| 0 | [#117](https://github.com/Dagt/TrailerSaas/issues/117) | Parent Epic/planning docs | PR #116 merged | Approval of design direction, screen and work boundaries |
| 1 | [#118](https://github.com/Dagt/TrailerSaas/issues/118) | UX-01 Design System | Owner direction approval | Shared tokens and components, Flutter theme mapping |
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

- **Plan status:** proposed, pending review.
- **Approved design direction:** **none yet**; options A Fleet Command (recommended), B Industrial Signal, C Atlas Minimal.
- **Implementation authorization:** not granted by creating these issues.
- **Backend/auth/storage changes:** not authorized.
- **Demo data:** isolated, non-sensitive, clearly labeled; never pretend a generated concept is a running system screenshot.

## Documentation index

1. [Master plan](frontend-redesign-master-plan.md)
2. [Design system](design-system.md)
3. [Screen inventory](screen-inventory.md)
4. [Visual acceptance criteria](visual-acceptance-criteria.md)
5. [Interaction / English copy standards](interaction-content-standards.md)
6. This roadmap and issue map

The owner should first review the documentation PR, select the visual direction, then instruct an implementation agent to work on **#118 only**; no agent is tasked with writing the Epic or planning issues.
