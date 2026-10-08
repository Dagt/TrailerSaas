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

Implemented in #28. POST requires DRIVER_CREATE; PATCH requires DRIVER_EDIT.
Both writes remain Admin-only. GET/list/detail require DRIVER_VIEW, granted
Tenant-wide only to Tenant Admin and Transport Operations (read-only).

Create:

```json
{
  "name": "Driver Name",
  "licenseNumber": "...",
  "medicalCertificateExpires": "2027-04-01"
}
```

Server defaults active=true, version=0 and userId=null. userId, active and version
are not accepted in create. Name must be nonblank; licenseNumber and
medicalCertificateExpires are nullable. License numbers have no normalization
or uniqueness rule; expired certificate dates remain valid master data.

Responses contain id, userId, name, licenseNumber, medicalCertificateExpires,
active, version, createdAt and updatedAt. Lists return `{"items":[]}`, ordered by
name/id. No filters/pagination; unknown query parameters are rejected.

PATCH requires nonnegative expectedVersion and accepts only name, licenseNumber,
medicalCertificateExpires and active. Omission preserves a field; explicit null
clears the two nullable fields. active=false deactivates and active=true
reactivates. No hard delete or Trip-state rule. No-op PATCH retains version;
stale versions and concurrent optimistic races return 409 STALE_VERSION.
userId cannot change through create/PATCH.

### POST /drivers/{driverId}/user-link

Capability: `DRIVER_EDIT`.

Request:

```json
{
  "userId": "uuid",
  "expectedVersion": 3
}
```

Returns 200 Driver with incremented version. The server authorizes the actor,
locks the Tenant-scoped Driver, checks expectedVersion and freshly verifies
that the target User has a same-Tenant ACTIVE membership plus a TENANT DRIVER
role with customer_id NULL. The public identity eligibility boundary hides role
data from Drivers; Firebase claims never influence this validation.

Missing/ineligible User, an already-linked Driver (even to the same User), or
another Driver linked to that User in this Tenant returns generic 409
RESOURCE_CONFLICT. Replace only by unlinking first and using the refreshed
version. A database partial unique index enforces one Driver per User per Tenant;
the same User may link in different Tenants. Linking creates no membership/roles
and sends no invitation.

### DELETE /drivers/{driverId}/user-link

Capability: DRIVER_EDIT. Required JSON body:

```json
{"expectedVersion": 4}
```

Returns 204. Removes only user_id, preserving User, membership, roles and history.
An already-null link with matching version is an idempotent 204 without version
increment; removing an existing link increments version. Unknown fields are
rejected and stale versions return STALE_VERSION.

## 10. Tractors

### POST /tractors
### GET /tractors
### GET /tractors/{tractorId}
### PATCH /tractors/{tractorId}

Implemented in #28. POST requires TRACTOR_CREATE and PATCH requires TRACTOR_EDIT,
both Admin-only. GET/list/detail require TRACTOR_VIEW, granted Tenant-wide only
to Tenant Admin and Transport Operations (read-only).

Create accepts only operationalNumber:

```json
{"operationalNumber": " t114 "}
```

Server defaults active=true and version=0. Responses contain id,
operationalNumber, active, version, createdAt and updatedAt. The display value
is preserved exactly; the generated normalized key is never returned.

The Tenant-unique STORED key trims only ASCII U+0020 at either end, collapses
internal ASCII-space runs, and folds only ASCII a-z to A-Z. Punctuation, leading
zeros and non-ASCII are preserved. Tabs, CR/LF and C0/C1 controls are rejected;
an empty normalized key is invalid. Thus " t114 " collides with "T114" and
"bh  t114" with "BH T114", while "T-114", "T114" and "T0114" remain distinct.
PostgreSQL enforces this even if application validation is bypassed.

Lists return `{"items":[]}`, ordered by normalized key/id while displaying the
original number. No filters/pagination; unknown query parameters are rejected.
PATCH requires expectedVersion and accepts operationalNumber and active only.
Omitted fields remain unchanged; a no-op retains version. Deactivate/reactivate
uses active; no hard delete. Number edits revalidate controls and uniqueness.
Normalized collisions return 409 RESOURCE_CONFLICT; stale versions and
optimistic races return 409 STALE_VERSION.

For both modules, cross-Tenant/nonexistent resource IDs return 404
RESOURCE_NOT_FOUND after valid Tenant authorization. All requests use existing
401/403/correlation and safe Problem Details. Infrastructure failures remain
5xx; SQL/constraints, internal roles and cross-Tenant metadata are not returned.

## 11. Trailers

Implemented in #29 under the Tenant base. No hard delete, physical command or
movement history is implemented in this baseline.

### POST /trailers
### GET /trailers
### GET /trailers/{trailerId}
### PATCH /trailers/{trailerId}

POST requires TRAILER_CREATE (Admin). Only operationalNumber is required;
optional designatedCustomerId (same Tenant), trailerType, capabilityNotes and
usagePurpose are metadata. Designation is not ownership. Original number is
preserved; amendment I/V7's ASCII-only generated normalization enforces identity.
Controls/empty keys fail validation. UUID is independent from number.

Server initial state: LOCATION_UNKNOWN, null current/last-known Location, last
seen and Driver, UNKNOWN load, IN_SERVICE, active=true, version=0. Create cannot
supply those state fields. Responses contain id, display number, designation,
type/notes, locationState/currentLocationId/lastKnownLocationId/lastSeenAt/
lastDriverId, loadState, serviceabilityState, usagePurpose, active, version and
timestamps, never Tenant ID or normalized key.

Reads use TRAILER_VIEW_ALL for Admin/Operations, or fresh
TRAILER_VIEW_CUSTOMER_SCOPED Customer IDs for Dispatcher. Dispatcher sees only
matching designated Customers, excluding unassigned Trailers. Direct invisible
IDs return 404 RESOURCE_NOT_FOUND. Yard/Driver have no generic browse.

GET list returns items ordered by normalized number/id, without pagination.
Exact filters: operationalNumber (normalized exact match), designatedCustomerId,
locationState, currentLocationId, loadState, serviceabilityState, active. Filters
AND with each other and the mandatory Customer scope. Invalid/unknown filters
return 400; explicit outside-scope Customer filter returns 403 CUSTOMER_SCOPE_DENIED.

PATCH requires TRAILER_EDIT (Admin) and expectedVersion. Mutable fields only:
operationalNumber, trailerType, capabilityNotes, usagePurpose, active.
Omitted fields stay unchanged; nullable metadata clears with explicit null.
No-op retains version; stale/racing updates return STALE_VERSION, normalized
duplicate or invalid/cross-Tenant Customer FK returns safe RESOURCE_CONFLICT.
Generic PATCH cannot change designation or any physical state/provenance.

### Deferred operational commands

The following approved contracts are future implementation, not #29 endpoints.
They require their own domain/history/audit behavior. OQ-054 remains open.

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

Implemented in #29; management paths are Tenant-scoped and require TRAILER_EDIT
(Admin-only). No QR image/printing/labels/UI or production QR host is configured.

### GET /trailers/{trailerId}/qr-identities
Returns items with id, trailerId, publicToken, operationalNumber, active,
issuedAt, revokedAt, ordered issuedAt DESC/id. Retains revoked history; token and
display number are separate for future labels.

### POST /trailers/{trailerId}/qr-identities
No functional payload. Locks Trailer and returns 201 with a new public QR
identity: SecureRandom 16 bytes (128 bits), URL-safe Base64 without padding,
canonical 22 characters. Stores the public identifier directly (ADR-018).
One active QR per Trailer is DB-enforced; an existing active QR returns 409.
Reissue requires explicit revoke first, preserving Trailer UUID.

### POST /trailers/{trailerId}/qr-identities/{qrId}/revocation
No functional payload. Locks Trailer and exact QR, sets active=false and server
revokedAt, retaining history. Returns 204, including already revoked. Wrong pair/
Tenant returns 404. Full audit subsystem integration remains deferred.

### GET /api/v1/trailer-qr/{publicToken}
Global Firebase-authenticated endpoint, outside the Tenant base path.
Accepts no Tenant/Customer/role hints. Canonical token syntax is required.
An exact active QR SELECT-only transaction-local RLS context derives Tenant from
the stored QR. Normal Tenant RLS then loads Trailer and fresh ACTIVE membership/
capabilities authorize identification. Token alone never grants permission.

Admin/Operations use TRAILER_VIEW_ALL; Dispatcher requires the designated
Customer under TRAILER_VIEW_CUSTOMER_SCOPED. Yard may identify with
YARD_MOVE_EXECUTE or YARD_CHECK_EXECUTE; Driver with TRAILER_HOOK or TRAILER_DROP.
These scan permissions authorize no state mutation or physical command.

Scan summary contains only trailerId, operationalNumber, trailerType,
locationState, loadState, serviceabilityState and active. Unknown/revoked tokens,
missing/suspended membership and outside Customer scope (including unassigned)
all return the same safe 404 RESOURCE_NOT_FOUND. Invalid syntax is 400; missing/
invalid bearer is 401. QR tokens are redacted from error instances and HTTP logs.

### Deferred timeline

### GET /trailers/{trailerId}/timeline

Returns cursor-paginated customer/role-authorized movement history suitable for Trailer detail screens.
