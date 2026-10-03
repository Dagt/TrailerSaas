# Business Rules

**Status:** Draft  
**Convention:** `BR-###`

## Identity and tenancy

- **BR-001** — Every operational record belongs to exactly one tenant.
- **BR-002** — A customer user can only access data explicitly associated with that customer inside the same tenant.
- **BR-003** — Operational identifiers such as trailer numbers must not be assumed globally unique across all tenants.

## Trailer state

- **BR-010** — Trailer load state and operational state are independent concepts.
- **BR-011** — Trailer location is independent of trailer load state.
- **BR-012** — Trailer purpose (delivery, shuttle, storage, etc.) must not be used as a substitute for load state or location.
- **BR-013** — A trailer may be marked out of service.
- **BR-014** — Returning a trailer from OOS to service must be an explicit auditable action.
- **BR-015** — Current trailer state should be derivable from authoritative events and explicit corrections rather than destructive spreadsheet-style overwrites.

## Hook/drop

- **BR-020** — A hook event must identify the trailer and driver.
- **BR-021** — When a tractor is known, hook must also capture tractor.
- **BR-022** — When hook is part of a job, it must be associated with that job/order context.
- **BR-023** — A drop event must capture a location or a valid unresolved-location exception.
- **BR-024** — Scanning a trailer different from the planned trailer is an exception, not a silent assignment replacement.

## Jobs

- **BR-030** — A job may be created before a trailer is known.
- **BR-031** — Driver/tractor/trailer assignments may change where permitted.
- **BR-032** — Reassignments must be auditable.
- **BR-033** — Appointment time is operationally significant and must be represented.
- **BR-034** — A rejected load or closed destination can create a return-to-base exception flow.
- **BR-035** — Tractor failure can require another tractor/driver to continue or recover a load; job history must be able to represent handoff/recovery without losing prior work.

## Yard check

- **BR-040** — A yard check compares an expected set with physically observed trailers.
- **BR-041** — Missing and unexpected trailers are reconciliation results, not automatic destructive corrections.
- **BR-042** — Reconciliation corrections must identify the responsible user.

## Evidence

- **BR-050** — Evidence belongs to a tenant and one or more relevant business records.
- **BR-051** — Delivery evidence must preserve upload timestamp and actor/source.
- **BR-052** — A signed BOL can satisfy signature evidence unless a separate digital-signature requirement is later approved.

## History and correction

- **BR-060** — Administrators must not “fix” history by deleting/replacing prior operational facts as the normal workflow.
- **BR-061** — Corrections must preserve original value/event, corrected value and actor.
- **BR-062** — Event timestamps should represent business occurrence time when known, while the system should also be able to preserve record/sync time where necessary for offline use.

## Mobile/offline

- **BR-070** — Replayed mobile actions after offline use must be idempotent.
- **BR-071** — Offline actions must retain original device/business timestamp when safely available.
- **BR-072** — The server remains authoritative for authorization and conflict resolution.

## Alerts

- **BR-080** — “No movement for 3 days” is a discovery baseline, not a universal hard-coded constant.
- **BR-081** — Stale thresholds should be configurable at the appropriate tenant/customer level if product validation supports it.

## Payroll boundary

- **BR-090** — Shift start/end timestamps may be captured.
- **BR-091** — Wage calculation, taxes, payroll rules and payroll disbursement are outside the MVP unless scope changes.
