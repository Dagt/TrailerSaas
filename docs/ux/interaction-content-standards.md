# TrailerSaaS — UI Interaction and Content Standards

**Status:** proposal; applies to all redesign issues. Users: Tenant Admin, Transport Operations, Customer Dispatcher, Road Driver, Yard Spotter. User-facing content in **US English**; code enum values remain unchanged.

## 1. Content voice

Use direct, operational language: specific object, state and next action. Avoid excitement, marketing copy inside dispatch tools, obscure abbreviations and all-caps except recognized identifiers.

| Context | Preferred wording | Avoid |
|---|---|---|
| Trailer location | "Location unknown. Last seen at [site] on [time]." | "Trailer is at [last site]." |
| Trip assignment | "Expected Trailer" | "Attached Trailer" without coupling evidence |
| Physical coupling | "Coupled to Tractor" only with confirmed event | "Assigned" as evidence of physical hook |
| Delivery | "Delivered" / "Completed" based on true state | Synonyms hiding status distinction |
| Document | "POD uploaded; acceptance pending" | "POD complete" without acceptance |
| Offline mobile | "Saved on device. Waiting to sync." | "Delivered to server" |
| Push event | "Accepted by messaging service" | "Driver notified" without confirmation |
| Audit | "View change history" | "Undo" or "Restore" without an approved command |
| Customer scope | "You do not have access to this record." | Revealing another Customer's record existence |

State labels map display strings to existing enums; do not change backend strings for presentation. Timestamp formatting must show meaningful zone/offset or communicate the user's configured local zone; never falsely imply realtime.

## 2. Information architecture

- Primary navigation: role/task-focused, stable ordering. Secondary actions near current resource; dangerous actions separated from benign navigation.
- Search/filters: applied explicitly where current behavior is explicit; maintain search state where safe. Filters never broaden server scope.
- Detail views: contextual breadcrumb/back navigation; persistent relevant status and version where actions need expectedVersion.
- Dashboards: use real bounded previews and label incomplete counts. No full page client-side fetch merely to show a KPI.
- Tables: primary identifier first, status and time discoverable, row click labels explicit, destructive actions not hidden behind ambiguous icons.
- Mobile: one safe primary task at a time, clear action confirmation, offline indicator persistent where required.

## 3. Action and error behavior

- **Ready:** button available only when current capabilities, state and form allow it; server rechecks on submission.
- **Pending:** disable repeat submission; preserve cancel/navigation if safe.
- **Success:** report only after confirmed server response, never after solely optimistic animation.
- **Uncertain acknowledgement:** block duplicate unsafe retry and instruct read reconciliation; do not create a fake success state.
- **409/stale version:** explain record changed; refresh and review before re-submitting.
- **401/403:** appropriate sign-in/access messaging, hide private stale views.
- **404:** generic not found where required by no-existence-leak policy.
- **5xx/unavailable:** preserve safe context, offer appropriate retry without reissuing non-idempotent commands.
- **Offline:** differentiate queued command, pending media upload, conflict, retry and committed state. Design cannot alter the offline state machine.
- **Document download:** only authorized short-lived URL; never persist signed URL or include it in telemetry/screenshots.

## 4. Forms, feedback and dialog behavior

Always render visible labels and validation. Field errors use text and `aria-describedby`; no placeholders as the only label. Confirmation dialogs state the entity and consequence. Destructive operations use explicit destructive styling plus description, not red color alone. Restore keyboard focus to trigger after a dialog closes. Do not change existing reason requirements. QR and sensitive technical IDs may have copy controls only when permission and view permit.

## 5. Accessibility and localization

- Keyboard order follows reading order; avoid focus traps outside modal contexts.
- Honor reduced motion and text scaling, inclusive contrast and semantic status labels.
- Use the repository's existing time/date parsing; do not hard-code Chicago timezone or interpret absent ETA as zero.
- US English with consistent terms: **Trailer**, **Tractor**, **Driver**, **Trip**, **Stop**, **Order**, **Delivery**, **Yard**, **Customer**, **POD**, **BOL**, **Audit**.
- Never invent a profile/avatar name when Audit only has historical User ID. Represent unknown actor explicitly as "System"/"Integration" where the event says so.

## 6. Design review process

UX-01 proposes the shared component API and a small prototype gallery; each later issue includes actual before/after screenshots and a copy consistency check. Any change to permissions, navigation to foreign resources, retry logic, state meaning or command payload is a **functional change** and requires separate review, not automatic inclusion in visual polishing.

## 7. Commercial demo content rules

Commercial captions explain supported value: trailer visibility from recorded operational events, order/delivery progress, private POD/BOL documents, and Road/Yard workflows. Do not promise continuous GPS/ETA/real-time push. Clearly label sample tenants and data as DEMO. Clips and screenshots must originate from running app/emulator; concept renders are separately labeled as concepts. Never present completion of UI redesign as equivalent to release readiness.
