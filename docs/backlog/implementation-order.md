# Implementation Order and Dependency Waves

**Status:** Active plan  
**Tracking:** #66

This ordering is designed to keep Codex/implementation work bounded while enabling safe parallelism.

## Wave 0 — Repository foundation

Execute first:

- #15 Backend scaffold
- #16 Web scaffold
- #17 Mobile scaffold

Then:

- #18 CI/OpenAPI validation
- #19 PostgreSQL/Flyway/Testcontainers
- #20 Environment/secrets templates
- #21 Observability baseline

Exit: all applications build/test and the database/test/config foundations exist.

## Wave 1 — Security boundary

Gate: **Schema v1.0.1 hardening approval before first domain Flyway migrations**
([#74](https://github.com/Dagt/TrailerSaas/issues/74),
[Accepted amendment](../data-model/schema-v1.0.1-hardening.md)).
**Schema v1.0.1 hardening gate satisfied upon merge of
[#75](https://github.com/Dagt/TrailerSaas/pull/75); first identity/domain migrations
may then proceed (#22/#23 onward).** The gate is not satisfied before merge.
Implementation baseline: Accepted v1.0 plus the v1.0.1 amendment; the original
v1.0 file and existing technical V1 remain physically unchanged. The amendment's
explicit #29/#32/#55 and optional Mobile deviceId deferments do not block #22.

- #22 Firebase/internal User
- #23 Tenant membership/roles
- #24 capability/customer scope
- #25 invitation/onboarding
- #26 TenantContext/RLS

**Wave 1 completed** with [PR #81](https://github.com/Dagt/TrailerSaas/pull/81).
Integrated sequence: **#22 -> #23 -> #24 -> #26 -> #27 -> #25**.
Customer-scoped onboarding in #25 is implemented after #27 because Customers
and their tenant-safe FK must exist first. #25 is merged and closed.
The rest of the roadmap is unchanged.

Exit: every later Tenant-owned feature has an enforceable security foundation.

## Wave 2 — Shared operational foundations

Can proceed with controlled parallelism after Wave 1:

### Master data
- #27 Customers/Sites/Locations
- #28 Drivers/Tractors — merged/complete in [PR #82](https://github.com/Dagt/TrailerSaas/pull/82)
- #29 Trailers/QR — merged/complete in [PR #83](https://github.com/Dagt/TrailerSaas/pull/83)
- #30 Tenant settings/Admin master-data Web — current bounded implementation; complete only after its PR merges

### File/job infrastructure
- #52 R2 document transport
- #55 PostgreSQL outbox/job worker

Exit: equipment/locations exist and durable evidence/background infrastructure is available.

## Wave 3 — Core logistics domain

### Orders
- #31 → #32 → #33

### Trips
- #34 → #35 → #36 → #37

### Trailer movement
- #38 → #39 → #40 → #41

### Evidence/alerts support
- #53 after #52
- #56 after #55
- #57 after Trailer projection + Alerts

These streams may overlap according to each Issue's explicit dependencies.

## Wave 4 — Mobile synchronization foundation

- #42 Mobile auth/cache shell
- #46 Offline command queue/replay
- #54 Mobile media queue

These should be implemented before relying on offline behavior in completed Road/Yard workflows.

## Wave 5 — Road Driver

- #43 Shift/inspections
- #44 Trip execution
- #45 POD/Fuel/exceptions
- #47 geofence-assisted arrival/updates

Validate full online and offline sequences on real devices.

## Wave 6 — Yard

- #48 Yard Move
- #49 discrepancy/OOS
- #50 Yard Check backend
- #51 Yard Check mobile/offline

Validate snapshot behavior and physical-state conflicts carefully.

## Wave 7 — Customer/Audit surfaces

- #58 → #59 → #60 Customer portal
- #61 → #62 Audit/history

These rely heavily on the stable domain/API implemented earlier.

## Wave 8 — Production hardening

- #63 Backup/restore
- #64 Production deployment gates/monitoring
- #65 Final readiness gate

No destructive retention cleanup may be enabled until OQ-057 is resolved.

## Parallelism rule

Parallel work is safe only when Issues do not concurrently redefine the same accepted contract/schema boundary.

When two Issues touch the same aggregate or migration sequence, coordinate changes through dependency/order rather than accepting contradictory parallel implementations.
