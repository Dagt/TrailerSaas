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
- #30 Tenant settings/Admin master-data Web — merged/complete in [PR #84](https://github.com/Dagt/TrailerSaas/pull/84)

### File/job infrastructure
- #52 R2 document transport — complete via PR #99; #53 evidence validation complete via PR #100; #54 mobile media complete via PR #101
- #55 PostgreSQL outbox/job worker

Exit: equipment/locations exist and durable evidence/background infrastructure is available.

## Wave 3 — Core logistics domain

### Orders
- #31 → #32 → #33

#31 is merged/complete via [PR #85](https://github.com/Dagt/TrailerSaas/pull/85).
#32 is merged/complete via [PR #86](https://github.com/Dagt/TrailerSaas/pull/86).
#33 is merged/complete via [PR #87](https://github.com/Dagt/TrailerSaas/pull/87).
Generic durable Audit persistence/query remains #61.

### Trips
- #34 → #35 → #36 → #37

#34 is merged/complete via [PR #88](https://github.com/Dagt/TrailerSaas/pull/88).
#35 is merged/complete via [PR #89](https://github.com/Dagt/TrailerSaas/pull/89).
#36 is merged/complete via [PR #90](https://github.com/Dagt/TrailerSaas/pull/90).
#37 is merged/complete via [PR #91](https://github.com/Dagt/TrailerSaas/pull/91),
closing the Trips/dispatch Web wave.

### Trailer movement
- #38 → #39 → #40 → #41

#38 is merged/complete via [PR #92](https://github.com/Dagt/TrailerSaas/pull/92).
#39 is merged/complete via [PR #93](https://github.com/Dagt/TrailerSaas/pull/93).
#40 is merged/complete via [PR #94](https://github.com/Dagt/TrailerSaas/pull/94).
#41 is merged/complete via [PR #95](https://github.com/Dagt/TrailerSaas/pull/95).
The Trailer movement/read surface wave is complete. #42 is merged/complete via [PR #96](https://github.com/Dagt/TrailerSaas/pull/96). #44 is merged/complete via [PR #97](https://github.com/Dagt/TrailerSaas/pull/97); #43 is merged/complete via PR #102; #45 is merged via PR #103. #47 is merged/complete via [PR #104](https://github.com/Dagt/TrailerSaas/pull/104), baseline `798d987b242dab74c3e58ec81eee8a2c61f8d049` (2,019 Backend / 389 Mobile / 244 Web / 18 OpenAPI). #48 is the current bounded Yard Mode/Trailer Move slice, with A–F published and [PR #105](https://github.com/Dagt/TrailerSaas/pull/105) opened as Draft for final CI; its [acceptance and validation record](../mobile/issue-48-yard-move.md) tracks the G publication and external review gates. Physical Android/iOS QA remains for #65. The historical #48 record is retained; the current authorized bounded slice is #51.

### Evidence/alerts support
- #53 after #52
- #56 after #55
- #57 after Trailer projection + Alerts

These streams may overlap according to each Issue's explicit dependencies.

#52 is merged/complete via [PR #99](https://github.com/Dagt/TrailerSaas/pull/99).
#53 is merged/complete via [PR #100](https://github.com/Dagt/TrailerSaas/pull/100).
#43/#45/#49 consume its synchronous policies and still own their respective
operational workflows. #54 is complete via PR #101; #43 is complete via PR #102; #45 is the current bounded Road implementation;
#55 is merged via PR #109 (`ec7c73f4ebac0f9dd8be52e2d24d9a5f67ccc4ef`);
see [architecture and acceptance](../backend/issue-55-postgresql-outbox-worker.md).
#56 implements Alerts/global devices/FCM and the expressly authorized Draft/Ready
CI process on [PR #110](https://github.com/Dagt/TrailerSaas/pull/110); see
[acceptance and policy limits](../backend/issue-56-alerts-fcm-delivery.md).
#57 implements explicitly enabled per-Tenant durable stale detection, SYSTEM
Alerts/recovery and API/Web integration on [PR #111](https://github.com/Dagt/TrailerSaas/pull/111);
see [acceptance/configuration](../backend/issue-57-stale-trailer-detector.md).
OQ-059 recipients/escalation and human write-role grants remain pending; zero
automatic production recipients. #61/retention remain deferred; #58 shell implementation is tracked below.

## Wave 4 — Mobile synchronization foundation

- #42 Mobile auth/cache shell — complete via PR #96
- #46 Offline command queue/replay — merged/complete via [PR #98](https://github.com/Dagt/TrailerSaas/pull/98)
- #54 Mobile media queue — complete via PR #101

These should be implemented before relying on offline behavior in completed Road/Yard workflows.

## Wave 5 — Road Driver

- #43 Shift/inspections — merged/complete via PR #102
- #44 Trip execution — merged/complete via PR #97; real #43 duty/inspection integration is complete via PR #102
- #45 POD/Fuel/exceptions — current; no merge or completion claimed
- #47 geofence-assisted arrival/updates

Validate full online and offline sequences on real devices.

## Wave 6 — Yard

- #48 Yard Move — merged via PR #105 (baseline `82a48132b9153aaa6445ac890040e219f82a24f3`)
- #49 discrepancy/OOS — merged via [PR #106](https://github.com/Dagt/TrailerSaas/pull/106), baseline `7d2a9d7a1cbaea002f8a051e03658348554c307d`
- #50 Yard Check backend — merged via [PR #107](https://github.com/Dagt/TrailerSaas/pull/107), baseline `9446576d6d9a26a1f8f4f11005ba90a360ed0f95`
- #51 Yard Check mobile/offline — merged via PR #108; [acceptance and final CI gates](../mobile/issue-51-yard-check-mobile-offline.md)

Validate snapshot behavior and physical-state conflicts carefully.

## Wave 7 — Customer/Audit surfaces

- #58 Customer portal shell merged through [PR #112](https://github.com/Dagt/TrailerSaas/pull/112); [architecture/evidence](../web/issue-58-customer-portal-shell.md).
- #59 Customer Order/commercial workflows implemented on Draft [PR #113](https://github.com/Dagt/TrailerSaas/pull/113); [scope, V28, acceptance and validation](../web/issue-59-customer-orders-workflows.md). Remote Full CI remains unverified while owner-deferred Billing prevents execution; not merged or release-approved.
- #60 remains deferred: Customer inventory, delivery catalogs and evidence views.
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
