# Implemented capabilities and honest claims

Review baseline: `fa0898e12cbdc358ea7cd7119c76a1e3a11e248c`. This inventory checks
actual routes/widgets and implementation records; old aspirational passages in
the root README and initial UX flow documents are not proof of functionality.

| Surface / problem | Implemented capability and source | What this package demonstrates | Benefit wording / boundary |
|---|---|---|---|
| Admin / disconnected equipment lists | Customers, Sites/Locations, Drivers, Tractors, Trailers, designation/QR metadata and Settings. `web/src/areas/tenant-admin/{AdminLayout,MasterDataPages}.tsx`; UX-03 record | Actual directories, Overview and Settings; synthetic records; no saved edits | Helps staff consult consistent equipment and facility records. No general Users/Memberships administration screen or health analytics is promised. |
| Dispatch / planning scattered across messages | Bounded Dispatch board, filters, exact Trailer search, Orders selection, four-step Trip planning; `BoardPages.tsx`, `PlanTrip.tsx`, `TrailerPages.tsx` in Operations | Read previews and planning UI; no Trip created | Helps dispatch review pending work and prepare executable Stops/resources. Board previews contain up to 20 Orders/Trips, never fleet totals. |
| Dispatch / unclear instructions | Assignments, executable Stops, linked Orders, reroute/cancel review and paginated operational timeline; `TripDetailPage.tsx`, `TripAction.tsx` | Synthetic assignment/history records and pagination | Makes the recorded plan and changes inspectable. Expected Trailer does not prove physical Hook or cargo loading. |
| Equipment / uncertain whereabouts | Exact operational-number search; distinct location/load/serviceability/coupling dimensions, Last Known and paginated movement history; `shared/trailers/TrailerReadView.tsx` | Unknown current location plus preserved Last Known observation | Helps staff distinguish known facts from missing observations. No GPS tracking, live map, ETA or inferred current position. |
| Follow-up / missed exceptions | Capability-filtered Alerts with acknowledge/resolve, reasons, versions and physical-recovery constraints; `areas/alerts/AlertsWorkspace.tsx` | Synthetic warning list, not a detector firing or a notification delivered | Supports authorized follow-up. Acknowledgement is not physical recovery; messaging-service acceptance is not delivery to a device. |
| Audit / reconstructing changes | Read-only, filtered, paginated administrative before/after history, actor/time and privacy redactions; `tenant-admin/audit/AuditWorkspace.tsx` | Synthetic Customer change event | Helps explain who changed a record and what changed. Administrative Audit is separate from physical movement history. |
| Customer / repeated status requests | Customer-bound dashboard, Orders/create/review/actions, deliveries, designated inventory and scoped history; `customer-portal/` and its existing providers | Actual read pages with synthetic Customer membership and continuous-verification context | Supports bounded customer self-service. Does not expose other Customers or internal Driver/Tractor/Audit data. No real authorization is established by this demo. |
| Documents / finding permitted evidence | Accepted association-based document list and explicit fresh protected-download authorization; `CustomerReadViews.tsx`, `readQueries.ts` | Synthetic accepted metadata and honest unavailable-download state | Helps authorized customers find associated evidence. No real document, signed grant or storage download is demonstrated. Upload/finalization alone is not business acceptance. |
| Road / field execution | Assigned Trips/Stops, reviewed QR, Hook/Drop, departure/arrival/service, evidence drafts, inspections/Shift and instruction/recovery states; `mobile/lib/src/{road,shift,session}/` | Real Road workspace and assigned-Trip widgets on Android; no accepted physical command | Gives drivers task context and explicit confirmation steps. Scan is identification, not authorization. Physical confirmations remain mandatory. |
| Yard / unrecorded physical moves | Site-scoped Trailer review, Move, corrections/discrepancy/OOS and Yard Check workflows; `mobile/lib/src/yard/` | Actual Yard workspace and destination/confirmation review with synthetic scan | Supports review of origin/destination before a physical action. No real move, correction, completed Yard Check or reconciled missing Trailer is claimed. Yard-only access does not invent a Road Shift grant. |
| Connectivity / uncertain field updates | Durable commands/media, idempotent replay, conflict/uncertain states, owner fencing; `mobile/lib/src/sync/`, `media/` and persistence; #46/#54 implementation records | Current sync interface and empty queue, not an offline/reconnect acceptance test | Keeps queued, sending, conflict, uncertain ACK and server-confirmed states distinct. An empty synthetic queue proves no real synchronization. |

## Language to use

“TrailerSaaS brings recorded Trailer state, dispatch planning, field workflows and
Customer visibility into role-specific workspaces. Today we are showing the actual
interfaces with synthetic data in an isolated environment.”

“This can help your staff reduce repeated lookups and status handoffs. We have not
measured savings in your operation. Which handoff causes the most rework today?”

## Claims excluded from the deck and walkthrough

No quantified savings, efficiency percentages, utilization or on-time performance;
no customer testimonials; no continuous GPS, predictive ETA, route optimization,
payroll, accounting, ELD or full fleet maintenance; no uptime/SLA, deployment date,
pricing commitment, production certification or claim that a notification arrived.
No promise that a demo click created a Trip, moved a Trailer, delivered an Order,
accepted a POD or granted access. `DELIVERED` remains distinct from `COMPLETED`.

Reference implementation records: [Admin](../../docs/web/issue-120-fleet-simple-tenant-admin.md),
[Operations](../../docs/web/issue-121-fleet-simple-transport-operations.md),
[Customer](../../docs/web/issue-122-fleet-simple-customer-portal.md),
[Mobile](../../docs/mobile/issue-123-fleet-simple-road-yard.md),
[UX-07 inventory](../../docs/ux/issue-124-visual-quality-audit.md).
