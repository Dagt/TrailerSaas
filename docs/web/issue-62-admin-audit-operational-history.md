# #62 — Admin Audit and operational history

## A — Verified baseline and implementation boundaries

Verified main: `8a8b50532e5216b973e1f476a24aca821b6ebf06` (merged PR115).
Issues37/41/61 are closed and62 is open. No prior PR for this branch exists.
The original checkout and its untracked `demo-assets/` remain untouched; this
issue uses a separate worktree and `issue-62-admin-audit-operational-history-views`.

Audit already has a closed24-action catalog, minimized snapshots, historical
actor IDs, mandatory AUDIT_VIEW, runtime RLS and descending keyset pagination.
This issue consumes those existing read ports; it does not change AuditWriter,
grants, migrations1–30, operational state machines or mobile/Drift9.

Trip timeline currently returns all assignment/operational events, ascending,
without a cursor. The Trip detail also embeds these full histories. The bounded
Web path will opt into SQL keyset pages and omit embedded histories; legacy
clients retain their original default responses. Equal timestamps require a
stable tie across both event tables. Each page reauthorizes TRIP_VIEW_ALL.

Trailer internal reads already have paginated enriched movement history.
Customer history uses a separate minimized projection: retain that boundary.
Expected Trailer assignment is planning, never evidence of physical coupling.

## Acceptance matrix (updated with executable evidence during implementation)

| Requirement | Planned verification |
|---|---|
| Audit navigation/direct access | AUDIT_VIEW gate; no request when absent |
| Real Audit list/detail and strict contracts | Existing API, closed action/entity snapshots, malformed response tests |
| Filters/date/cursor | Exact filters, inclusive RFC3339, real pages, invalid/repeated cursor tests |
| Deterministic comparison | Creation, missing/null, arrays, nested address, redacted field changes |
| Historical actors | Stable User/membership IDs; SYSTEM/INTEGRATION without fictional User |
| Tenant/session lifecycle | Identity-scoped cache, cancellation, revocation and late-response tests |
| Bounded Trip history | Opt-in SQL pagination, stable union order, backward compatibility and RLS |
| Trailer/Customer history | Existing pagination reused; safe Customer projection unchanged |
| Read-only | No write/Undo/restore endpoint or UI |
| Full validation | Final published HEAD, complete local logs/counts/hashes; hosted billing reported honestly |

## Scope and release boundary

No63–65 implementation, retention, new permissions, Customer Audit, live GPS,
invented data or generic Undo. No physical-device QA is claimed. Hosted Actions
billing remains an external limitation: blocked remote runs are NO VERIFICADO.
The PR remains Draft and unmerged. Prior Backend full validation took29m17s,
exceeding the current remote20-minute timeout; this risk is recorded without
changing workflow limits or weakening checks.
