# Issue #48 — Yard Mode and Trailer Move

Verified base: `798d987b242dab74c3e58ec81eee8a2c61f8d049`, merged PR #104;
#47 closed. Clean tree and no stashes; prior branches preserved.
Branch: `issue-48-yard-mode-trailer-move`. No merge; #49–#51 are outside scope.

## Acceptance matrix

| Gate | Implementation and required evidence |
| --- | --- |
| A: Yard context | Fresh active Tenant/membership and YARD_MOVE_EXECUTE; bounded active Sites/Locations; same-Tenant joins and Customer association checks; scope/RLS tests |
| B: physical command | Strict request, actual origin, same active Site for both areas, correct version, active IN_SERVICE stationary uncoupled Trailer; existing yardMove engine |
| B/C: atomic history | Existing MobileOperationLedger, locked Trailer and live locations; HUMAN_USER/USER_ACTION; original observedAt, recordedAt, UUID, before/after; rollback/concurrency tests |
| C: conflicts | LOCATION_MISMATCH distinct from STALE_VERSION; no silent correction, no event/version on rejection; safe authorized current projection |
| D: usable Yard | Yard-only and multi-role entry, NativeQrScanner, real QR lookup followed by minimal authorized Yard detail; Site/destination review and explicit confirmation; widgets |
| E: durable intent | Same CommandStore/ReplayWorker, User/Tenant fencing, persist before send, stable UUID/path/body, actual SQLite reopen/lost ACK/retry tests |
| E: successive moves | Per-Trailer dependency; second intent retains planned origin, no invented version; materialize only from first verified ACK; block dependents on conflict, independent assets proceed |
| F: compatibility | Existing Road/Shift/POD/Fuel/geofence/update/media tests retained; OpenAPI and documentation match real endpoints |
| G: external review | Full local suites and four final exact-head CI jobs, real counts, open Ready PR only when gates pass; no merge |

## Authorization and scope decisions

Accepted hardening A and ADR-011 explicitly make YARD_SPOTTER Tenant-scoped;
future per-location assignments are outside the accepted MVP schema. Reuse
YARD_MOVE_EXECUTE without adding role grants, Site assignments, Driver or Tractor
requirements. Selecting a Site does not grant permission. Active normalized
locations from the same Site constrain a physical Yard move; Customer context is
not exclusive ownership of a shared Site. No Order/load/designation change occurs.

The existing global QR resolver identifies an asset only. A minimal Yard-owned
Trailer read, freshly gated by YARD_MOVE_EXECUTE, supplies recorded location,
Customer display, state, coupling and version without granting generic browse.
The client does not persist or trust a scanned URL as Backend authority.

## Offline design

Reuse existing local_commands and materialized_requests. A follow-up move stores
an explicit reference to an earlier operation and its intended origin/destination.
Its expected version is unresolved until the preceding same-Trailer ACK provides
an authoritative version and matching destination. The final HTTP body is frozen
before sending and never recomputed on retry. No future version, fictitious Trip,
second queue or second worker. Server current state and exact expected version
remain authoritative; a competing Spotter yields review, never silent rebasing.

## Checkpoint evidence

A: GET yard/sites includes bounded active Site/location projections (50 Sites,
200 locations per embedded page); continuation uses yard/sites/{id}/locations.
GET yard/trailers/{id} has fresh Yard capability and no listing surface. Eleven
PostgreSQL context/security tests pass with 0 failures/errors/skipped. No server
migration, role grant or RLS policy changed.

Further implementation, tests and native QA limitations will be recorded as each
recoverable checkpoint is published.
