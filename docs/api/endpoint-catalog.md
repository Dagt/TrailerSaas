# MVP Endpoint Catalog v0.1

**Status:** Accepted v0.1

This is the consolidated implementation checklist for Phase 4.

Base Tenant path:

`/api/v1/tenants/{tenantId}`

## Identity / access

- `GET /api/v1/me`
- `POST /invitations`
- `GET /invitations`
- `POST /invitations/{id}/revoke`
- `POST /api/v1/invitations/accept` — global authenticated onboarding, token in
  body; Tenant derives from the locked invitation, not client input. #25 returns
  raw acceptanceToken once at Tenant-scoped creation only; no email delivery.
- `GET /memberships`
- `PATCH /memberships/{id}`
- `PUT /memberships/{id}/roles`
- `GET /settings`
- `PATCH /settings`

## Customers / locations

- `POST /customers`
- `GET /customers`
- `GET /customers/{id}`
- `PATCH /customers/{id}`
- `POST /customers/{customerId}/sites`
- `DELETE /customers/{customerId}/sites/{siteId}`
- `POST /sites`
- `GET /sites`
- `GET /sites/{id}`
- `PATCH /sites/{id}`
- `POST /sites/{siteId}/locations`
- `GET /sites/{siteId}/locations`
- `PATCH /locations/{id}`

## Drivers / equipment

Drivers/Tractors are implemented in #28 under the Tenant base path. DRIVER_VIEW
and TRACTOR_VIEW allow Tenant Admin/Transport Operations reads; create/edit/link/
unlink remain Admin-only through the existing write capabilities. Driver targets
require fresh same-Tenant ACTIVE membership + TENANT DRIVER role. Lists have no
filters/pagination; unknown parameters are rejected. PATCH/link commands require
expectedVersion. DELETE user-link also requires an explicit JSON body
`{"expectedVersion": 4}`; it returns 204 and deletes no identity/access.
Trailer endpoints below remain future #29 scope.

- `POST /drivers`
- `GET /drivers`
- `GET /drivers/{id}`
- `PATCH /drivers/{id}`
- `POST /drivers/{driverId}/user-link`
- `DELETE /drivers/{driverId}/user-link`
- `POST /tractors`
- `GET /tractors`
- `GET /tractors/{id}`
- `PATCH /tractors/{id}`
- `POST /trailers`
- `GET /trailers`
- `GET /trailers/{id}`
- `PATCH /trailers/{id}`
- `POST /trailers/{id}/customer-designation-changes`
- `POST /trailers/{id}/out-of-service`
- `POST /trailers/{id}/return-to-service`
- `POST /trailers/{id}/location-corrections`
- `POST /trailers/{id}/mark-location-unknown`
- `POST /trailers/{id}/load-state-changes`
- `GET /trailers/{id}/qr-identities`
- `POST /trailers/{id}/qr-identities`
- `POST /trailers/{id}/qr-identities/{qrId}/revocation`
- `GET /trailer-qr/{publicToken}`
- `GET /trailers/{id}/timeline`

## Orders

- `POST /orders`
- `GET /orders`
- `GET /orders/{id}`
- `PATCH /orders/{id}`
- `POST /orders/{id}/cancellations`
- `POST /orders/{id}/completion`

## Trips / dispatch

- `POST /trips`
- `GET /trips`
- `GET /trips/{id}`
- `POST /trips/{id}/driver-assignments`
- `POST /trips/{id}/tractor-assignments`
- `POST /trips/{id}/planned-trailer-assignments`
- `POST /trips/{id}/trailer-substitution-decisions`
- `POST /trips/{id}/reroutes`
- `POST /trips/{id}/cancellations`
- `GET /trips/{id}/timeline`
- `GET /dispatch/board`

## Driver mobile

- `GET /driver/me`
- `GET /driver/me/trips`
- `GET /driver/me/updates`
- `POST /driver/me/shifts/on-duty`
- `POST /driver/me/shifts/{id}/off-duty`
- `POST /driver/me/inspections/pre-trip`
- `POST /driver/me/inspections/post-trip`
- `POST /trips/{id}/hooks`
- `POST /trips/{id}/drops`
- `POST /trips/{id}/departures`
- `POST /stops/{id}/arrivals`
- `POST /stops/{id}/service-starts`
- `POST /stops/{id}/delivery-completions`
- `POST /stops/{id}/completions`
- `POST /stops/{id}/departures`
- `POST /driver/me/fuel-events`
- `POST /trips/{id}/exceptions`

## Yard mobile

- `GET /yard/sites`
- `POST /yard-moves`
- `POST /yard-checks`
- `GET /yard-checks`
- `GET /yard-checks/{id}`
- `POST /yard-checks/{id}/scans`
- `POST /yard-checks/{id}/completion`
- `POST /yard-checks/{id}/cancellation`
- `POST /yard-checks/{id}/items/{itemId}/mark-location-unknown`
- `POST /yard-checks/{id}/items/{itemId}/location-correction`

## Documents / evidence

- `POST /documents/uploads`
- `POST /documents/{id}/upload-completion`
- `POST /documents/{id}/links`
- `GET /documents/{id}`
- `POST /documents/{id}/download-url`

## Alerts / devices

- `GET /alerts`
- `GET /alerts/{id}`
- `POST /alerts/{id}/acknowledgement`
- `POST /alerts/{id}/resolution`
- `POST /devices`
- `DELETE /devices/{id}`

## Audit

- `GET /audit-events`
- `GET /audit-events/{id}`

## Explicitly not in MVP API

- billing;
- payroll;
- continuous GPS breadcrumbs;
- route optimization;
- maintenance work orders;
- Samsara ingestion;
- Amtech ingestion;
- accounting;
- custom-role builder;
- public recipient accounts/signature API.

## Open-policy impact

The following endpoint contracts can exist before final policy decisions, but authorization/default behavior remains open:

- `POST /trailers/{id}/return-to-service` — OQ-054 role authority;
- base-return exact Yard/Dock handling — OQ-055;
- official payroll/timekeeping reliance — OQ-056;
- destructive retention/admin APIs — blocked by OQ-057;
- broader tenant configuration endpoints — OQ-059.

No endpoint should silently invent those policies during implementation.
