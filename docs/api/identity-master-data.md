# Identity and Master Data API v0.1

**Status:** Accepted v0.1

All Tenant-owned paths use:

`/api/v1/tenants/{tenantId}`

## 1. Current identity/context

### GET /api/v1/me

Returns the authenticated internal User summary and available memberships.

Response example:

```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "displayName": "Jane Smith",
  "memberships": [
    {
      "tenantId": "uuid",
      "tenantName": "2 Fast Logistics",
      "status": "ACTIVE",
      "roles": [
        {
          "roleCode": "TRANSPORT_OPERATIONS",
          "scopeType": "TENANT",
          "customerId": null
        }
      ]
    }
  ]
}
```

No client-supplied membership claim bypasses backend lookup.

## 2. Membership invitations

### POST /invitations

Capability: `USER_INVITE`

Request:

```json
{
  "email": "dispatcher@example.com",
  "displayName": "Maria",
  "roles": [
    {
      "roleCode": "CUSTOMER_DISPATCHER",
      "scopeType": "CUSTOMER",
      "customerId": "uuid"
    }
  ]
}
```

Response: `201` invitation summary with `acceptanceToken` returned **once**, only
at creation. The server generates a 256-bit random URL-safe token, stores only
SHA-256 (lowercase hex), and sets `expiresAt = createdAt + 7 days`. Clients cannot
choose/extend expiry. Email normalization is trim plus lowercase of the complete
email (stable locale), without provider aliases, dot removal or plus removal.
One live PENDING invitation per Tenant/normalized email; a stale PENDING is
marked EXPIRED transactionally before re-inviting. History is retained.
No email delivery or outbox is implemented in #25; the Admin can copy the token.

### GET /invitations

Capability: `USER_INVITE`. Optional `status` filter: PENDING/ACCEPTED/EXPIRED/REVOKED.
Response: `{"items": [...]}`, with intended roles, ordered by createdAt DESC then id.
Unsupported query parameters are rejected. List/revoke never return token/hash.

### POST /invitations/{invitationId}/revoke

Capability: `USER_INVITE`.

Revocation preserves the invitation and intended roles. PENDING becomes REVOKED,
or EXPIRED if already expired; repeated revocation is idempotent. ACCEPTED cannot
be revoked (`409 RESOURCE_CONFLICT`).

### POST /api/v1/invitations/accept

Global authenticated endpoint: Firebase ID token plus JSON body `{"token":"..."}`.
No Tenant, email, User or role hints are accepted. Tenant is derived exclusively
from the stored, locked invitation. The **current cryptographically verified
Firebase token email** must exist, be verified and normalize to the invited email;
stored users.email is metadata and is never used for acceptance authorization.

Acceptance atomically creates one ACTIVE membership, copies the exact intended
roles and marks ACCEPTED with accepted User/time. It does not merge into an
existing membership. Concurrent/repeated acceptance by the same User returns
`200 {invitationId, tenantId, membershipId, accepted:true}` without changing roles
or reactivating access. Refresh `/api/v1/me` to read the new membership.

Invalid request/token encoding: `400 VALIDATION_FAILED`. Any syntactically valid
but unacceptable invitation: `409 INVITATION_NOT_ACCEPTABLE`, with the same
generic detail `The invitation cannot be accepted.` No existence, email, role,
Customer, token/hash or accepted-User information is disclosed.

## 3. Tenant memberships

### GET /memberships

Capability: `ROLE_ASSIGN` or Admin access.

Filters:

- status;
- roleCode;
- customerId.

### PATCH /memberships/{membershipId}

Admin actions such as status change.

Request:

```json
{
  "expectedVersion": 3,
  "status": "SUSPENDED"
}
```

If membership table does not carry `version` in final implementation, membership mutation must still use an equivalent concurrency guard/audit pattern.

### PUT /memberships/{membershipId}/roles

Capability: `ROLE_ASSIGN`.

Replaces the role assignment set atomically.

Request:

```json
{
  "roles": [
    {
      "roleCode": "TRANSPORT_OPERATIONS",
      "scopeType": "TENANT",
      "customerId": null
    }
  ]
}
```

Role changes are audited.

## 4. Tenant settings

### GET /settings

Capability: `TENANT_SETTINGS_MANAGE` for full configuration; read access may be narrower for operational clients as needed.

Returns only implemented/approved settings.

Initial fields:

```json
{
  "staleTrailerThresholdHours": 72,
  "defaultTimezone": "America/Chicago",
  "version": 2
}
```

### PATCH /settings

Capability: `TENANT_SETTINGS_MANAGE`.

Request:

```json
{
  "expectedVersion": 2,
  "staleTrailerThresholdHours": 72,
  "defaultTimezone": "America/Chicago"
}
```

Do not expose unresolved retention/customer-edit policy settings until those product policies are approved.

## 5. Customers

### POST /customers

Capability: `CUSTOMER_CREATE`.

Request:

```json
{
  "name": "Customer Name"
}
```

### GET /customers
### GET /customers/{customerId}

Customer-scoped users receive only permitted Customer data.

### PATCH /customers/{customerId}

Capability: `CUSTOMER_EDIT`.

Request:

```json
{
  "expectedVersion": 4,
  "name": "Updated Name",
  "status": "ACTIVE"
}
```

No hard delete in MVP.

## 6. Sites

### POST /sites
### GET /sites
### GET /sites/{siteId}
### PATCH /sites/{siteId}

Capabilities: `SITE_CREATE`, `SITE_EDIT`.

Create request:

```json
{
  "name": "710 Kimberly Dr",
  "address": {
    "line1": "710 Kimberly Dr",
    "city": "Carol Stream",
    "stateRegion": "IL",
    "postalCode": "...",
    "countryCode": "US"
  },
  "timezone": "America/Chicago",
  "latitude": 41.0,
  "longitude": -88.0,
  "geofenceRadiusM": 250
}
```

Coordinates/geofence are optional.

## 7. Customer ↔ Site associations

### POST /customers/{customerId}/sites

Capability: `CUSTOMER_EDIT` and appropriate Site access.

Request:

```json
{
  "siteId": "uuid"
}
```

Creates the explicit customer-site association used by shared-address facilities.

### DELETE /customers/{customerId}/sites/{siteId}

Removes the master-data association only when safe; it must not erase historical Order/Trip site snapshots or operational history.

This is one of the few safe DELETE-style association endpoints because it removes a current association rather than deleting the underlying Customer/Site.

## 8. Operational locations

### POST /sites/{siteId}/locations
### GET /sites/{siteId}/locations
### PATCH /locations/{locationId}

Request:

```json
{
  "name": "Stanfast Dock 14",
  "type": "DOCK",
  "code": "14",
  "customerId": "uuid"
}
```

The API supports multiple Customer contexts under one Physical Site.

## 9. Drivers

### POST /drivers
### GET /drivers
### GET /drivers/{driverId}
### PATCH /drivers/{driverId}

Capabilities: `DRIVER_CREATE`, `DRIVER_EDIT`.

Create:

```json
{
  "name": "Driver Name",
  "licenseNumber": "...",
  "medicalCertificateExpires": "2027-04-01"
}
```

Optional account linking/invitation is a separate identity workflow.

### POST /drivers/{driverId}/user-link

Capability: `DRIVER_EDIT`.

Request:

```json
{
  "userId": "uuid",
  "expectedVersion": 3
}
```

Links an existing authenticated User to the Driver record after invitation/account creation. The server validates the User has appropriate active Tenant membership and that no conflicting Driver link exists.

### DELETE /drivers/{driverId}/user-link

Unlinks the current login identity from the Driver profile without deleting either historical entity.

## 10. Tractors

### POST /tractors
### GET /tractors
### GET /tractors/{tractorId}
### PATCH /tractors/{tractorId}

Capabilities: `TRACTOR_CREATE`, `TRACTOR_EDIT`.

## 11. Trailers

### POST /trailers
### GET /trailers
### GET /trailers/{trailerId}
### PATCH /trailers/{trailerId}

Create request:

```json
{
  "operationalNumber": "T114",
  "designatedCustomerId": "uuid",
  "trailerType": "53_DRY_VAN",
  "capabilityNotes": "..."
}
```

List filters include:

- operationalNumber;
- designatedCustomerId;
- locationState;
- currentLocationId;
- loadState;
- serviceabilityState;
- active.

Trailer detail returns current projection plus recent/linked operational summaries, not the entire unbounded movement history.

### POST /trailers/{trailerId}/customer-designation-changes

Capability: `TRAILER_CUSTOMER_REASSIGN`.

Request:

```json
{
  "expectedVersion": 12,
  "newCustomerId": "uuid",
  "reason": "Administrative reassignment"
}
```

The change is audited and does not implicitly change location/load/Order.

### POST /trailers/{trailerId}/out-of-service

Capability: `TRAILER_OOS_MARK`.

Request:

```json
{
  "expectedVersion": 12,
  "comment": "Damage found",
  "documentIds": ["uuid"]
}
```

### POST /trailers/{trailerId}/return-to-service

Capability: `TRAILER_RETURN_TO_SERVICE`.

Authority/role mapping remains OQ-054; the endpoint exists at capability level without deciding which role owns that capability.

### POST /trailers/{trailerId}/location-corrections

Capability: `TRAILER_LOCATION_CORRECT`.

Request:

```json
{
  "expectedVersion": 13,
  "newLocationId": "uuid",
  "reason": "Confirmed physical location"
}
```

Preserves prior location/history.

### POST /trailers/{trailerId}/mark-location-unknown

Used by authorized Yard reconciliation/operations flow.

Response preserves:

- lastKnownLocation;
- lastSeenAt;
- lastDriver.

## 12. Trailer QR

### GET /trailers/{trailerId}/qr-identities

Admin/authorized equipment management view of active/revoked QR identities.

### POST /trailers/{trailerId}/qr-identities

Issues a new non-guessable QR identity.

### POST /trailers/{trailerId}/qr-identities/{qrId}/revocation

Revokes a compromised/replaced QR identity.

QR issuance/revocation is audited.

### GET /trailer-qr/{publicToken}

Requires authenticated Tenant context.

Returns a Trailer scan summary if the Trailer belongs to the active Tenant and caller has permission.

The token is an identifier, not an authorization secret.

### GET /trailers/{trailerId}/timeline

Returns cursor-paginated customer/role-authorized movement history suitable for Trailer detail screens.
