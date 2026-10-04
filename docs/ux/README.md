# UX / User Flow Specifications

**Status:** Proposed v0.1  
**Date:** 2026-10-04

This section translates approved requirements and state machines into deterministic screen-level behavior before API and UI implementation.

## Initial priority flows

- [Transport Operations Web](transport-operations-web.md)
- [Road Driver Mobile](road-driver-mobile.md)

## Later flows

- Tenant Admin Web
- Customer Dispatcher Web
- Yard / Spotter Mobile

## UX principles

1. Operational actions should be explicit and state-aware.
2. The interface should show the next valid action rather than expose arbitrary status editing.
3. Backend authorization and domain rules remain authoritative.
4. Mobile critical actions must tolerate temporary offline operation.
5. High-risk actions require confirmation and clear consequences.
6. History/audit must remain visible where operationally useful.
7. Driver UI should minimize typing and favor large, obvious actions.
8. The mobile app must not depend on continuous background location tracking.
9. Customer-specific names/examples from discovery must not be hard-coded into generic SaaS UI.
10. Accessibility and one-handed mobile use should be considered during visual design.

## Status labels

- **Required** — directly supported by approved product requirements.
- **Proposed** — recommended UX behavior that does not change the underlying business rule.
- **Open UX detail** — does not block domain design and can be finalized during visual design/usability testing.
