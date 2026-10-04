# Identity and Master Data API v0.1

**Status:** Proposed

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

Response: `201` invitation summary.

### GET /invitations

Admin list with status filter.

### POST /invitations/{invitationId}/revoke

Capability: `USER_INVITE`.

Invitation acceptance/auth linking is part of onboarding and will be represented in the eventual OpenAPI after Firebase invitation UX is finalized.

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

## 4. Customers

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

## 5. Sites

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

## 6. Operational locations

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

## 7. Drivers

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

## 8. Tractors

### POST /tractors
### GET /tractors
### GET /tractors/{tractorId}
### PATCH /tractors/{tractorId}

Capabilities: `TRACTOR_CREATE`, `TRACTOR_EDIT`.

## 9. Trailers

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

## 10. Trailer QR resolution

### GET /trailer-qr/{publicToken}

Requires authenticated Tenant context.

Returns a Trailer scan summary if the Trailer belongs to the active Tenant and caller has permission.

The token is an identifier, not an authorization secret.
