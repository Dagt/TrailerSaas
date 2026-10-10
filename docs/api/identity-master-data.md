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

Implemented in #30. Capability: `TENANT_SETTINGS_MANAGE` (Tenant Admin only)
for both GET and PATCH. No operational-client settings read is implemented.

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

V9 backfills existing Tenants with threshold 72 hours, nullable timezone and
version 0. The threshold must be a positive PostgreSQL integer; 72 is an initial
configuration value, not a universal domain constant. Non-null timezone must be
an IANA zone. PATCH requires a nonnegative integral expectedVersion, rejects
unknown fields, retains omitted fields and clears timezone on explicit null.
Optimistic conflicts return STALE_VERSION; a no-op keeps its version.
Runtime has SELECT/UPDATE only under Tenant RLS. Future Tenant creation must
create its settings row in that separate administrative flow.

The #30 Web workspace consumes only implemented master-data APIs. Customer-Site
associations have POST/DELETE but no complete GET directory, so Web offers
explicit association and removal of a server-confirmed pair during the current
visit; it never infers associations from addresses or Locations. Driver unlink
is supported; new linking waits for an approved account picker/directory.
Trailer metadata PATCH excludes designation and physical state. #40 implements
the audited designation command; #41 keeps the designation view read-only.
#41 enriched Trailer reads resolve current/last-known Location names directly,
without client-side per-row lookups. Site detail has its real Operational
Location list, including neutral versus Customer-context Locations.

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
locationState, currentLocationId, loadState, serviceabilityState, active, coupled. Filters
AND with each other and the mandatory Customer scope. Invalid/unknown filters
return 400; explicit outside-scope Customer filter returns 403 CUSTOMER_SCOPE_DENIED.

PATCH requires TRAILER_EDIT (Admin) and expectedVersion. Mutable fields only:
operationalNumber, trailerType, capabilityNotes, usagePurpose, active.
Omitted fields stay unchanged; nullable metadata clears with explicit null.
No-op retains version; stale/racing updates return STALE_VERSION, normalized
duplicate or invalid/cross-Tenant Customer FK returns safe RESOURCE_CONFLICT.
Generic PATCH cannot change designation or any physical state/provenance.

### Explicit operational commands — implemented by #40

These six commands return 200 with the authoritative Trailer DTO. Every body is
strict, requires expectedVersion >= 0 and rejects client actor/source/time hints.
Authorization is fresh ACTIVE membership plus the named capability. Trailer is
locked and refreshed before checking version; event/current row/version commit
atomically. Stale yields STALE_VERSION; invalid lifecycle action yields
INVALID_STATE_TRANSITION; same designation yields RESOURCE_CONFLICT.
Missing/invisible Trailer/Customer/Location yields safe RESOURCE_NOT_FOUND.
OQ-054 remains open; conservative role grants are unchanged.

History is durable trailer_movement_events, with server-derived HUMAN_USER,
USER_ACTION, authenticated User, occurredAt/server command time, recordedAt/server
chronology, reason and fixed before/after metadata. Generic Audit remains #61.
No V17, physical-state PATCH, second idempotency ledger or automatic retry exists.

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
  "comment": "Damage found"
}
```

### POST /trailers/{trailerId}/return-to-service

Capability: `TRAILER_RETURN_TO_SERVICE`.

Request:

```json
{"expectedVersion": 17, "reason": "Repairs completed and inspected"}
```

IN_SERVICE -> OUT_OF_SERVICE and OUT_OF_SERVICE -> IN_SERVICE are the only
serviceability transitions. Same-state actions reject. Metadata retains
previousServiceabilityState/newServiceabilityState. OOS makes normal planning
UNAVAILABLE and blocks a new Hook; it neither closes an existing coupling nor
blocks its Drop. Return removes only the serviceability barrier; inactive/scope
checks remain. OQ-054 final return authority is unresolved, with baseline mapping
preserved. Evidence/documentIds is deferred to #49/#53 after Documents #52;
documentIds is an unknown field in #40. No photo/upload placeholder is persisted.

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

This is a physically confirmed correction using the #39 engine with
physicalObservation=true: AT_LOCATION, new current/lastKnownLocationId and server
lastSeenAt, preserving last Driver when none is resolved. Canonical newLocationId
is required; observedLocationId is not an alias. The Location must be real and
same-Tenant. Prior history is retained.

### POST /trailers/{trailerId}/mark-location-unknown

Capability: TRAILER_LOCATION_CORRECT.

```json
{"expectedVersion": 14, "reason": "Trailer could not be physically located"}
```

AT_LOCATION/IN_TRANSIT -> LOCATION_UNKNOWN only; already unknown rejects.
currentLocationId becomes null. lastSeenAt never becomes command time: the last
physical observation remains. LOCATION_MARKED_UNKNOWN is the durable fact future
Alerts #57 will consume; no fake alert/outbox/notification is created.

Response preserves:

- lastKnownLocation;
- lastSeenAt;
- lastDriver.

### POST /trailers/{trailerId}/load-state-changes

Capability: TRAILER_STATE_CORRECT.

```json
{"expectedVersion": 15, "loadState": "EMPTY", "reason": "Confirmed after unloading"}
```

All six distinct EMPTY/LOADED/UNKNOWN transitions are allowed; same-state rejects
with RESOURCE_CONFLICT, without event/version change. LOAD_STATE_CHANGED retains previousLoadState and
newLoadState. No location/coupling/serviceability/Customer/Trip/Manifest side effect.
Customer reassignment requires a same-Tenant existing Customer, including inactive
master data; it records previousDesignatedCustomerId (including null) and
newDesignatedCustomerId and immediately changes fresh Customer-scoped visibility.
No implicit location/load/Order/Manifest/Trip/coupling change.

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
inactive/missing underlying Trailers, missing/suspended membership and outside
Customer scope (including unassigned) all return the same safe 404 RESOURCE_NOT_FOUND.
Deactivation does not revoke or change the QR; the same active QR resolves again
after Trailer reactivation. Invalid syntax is 400; missing/
invalid bearer is 401. QR tokens are redacted from error instances and HTTP logs.

### Operational read projection — implemented by #41

List items retain the original Trailer fields and add designatedCustomerName,
currentLocationName, lastKnownLocationName, couplingState, currentDriverId/name,
currentTractorId/operationalNumber, lastDriverName and lastTractorId/operationalNumber.
Exact number lookup uses the existing V8 tenant/normalized-number unique index.
`coupled=true` means an interval with dropped_at NULL exists; false means none.
There is no persisted coupled flag and no relationship to planned Trailer IDs.

Detail retains all original fields and adds designatedCustomer {id,name},
currentLocation/lastKnownLocation {id,name,type,siteId,siteName}, lastDriver,
currentTractor/lastTractor, currentCoupling, lastCoupling and openManifests.
Current location is the current physical assertion; last-known is the last
physical observation, never presented as current when unknown/in transit.
Current Driver/Tractor come only from the active physical coupling. Last Driver
uses trailers.last_driver_id; last Tractor comes from the greatest hooked_at/id
coupling. Current and last coupling can be the same interval.

OPEN Manifest preview is planning, not physically loaded cargo: at most 20
Manifests and 50 visible Orders total. Each Order exposes id, customerId,
customerName, orderNumber and status. Customer-scoped callers see only fresh
authorized Customers' Orders; empty visible Manifests are omitted. No hidden
counts/restriction flags are exposed. manifestSummaryTruncated indicates overflow
only for Tenant-wide callers; it is always false for scoped callers, whose fixed
preview bounds still apply.

The Trailers-owned native SQL read model joins same-Tenant display data without
Java internal-module dependencies, entity sharing or mutations. List uses one
projection query; detail uses one projection plus at most two Manifest queries;
timeline uses a visibility query plus one page query, in addition to fresh
authorization. A read-only REPEATABLE READ transaction provides a consistent
snapshot. V17 adds only coupling-history and Manifest-lookup indexes. V16's
movement-history index is reused; no duplicate operational-number index.

### Movement timeline — implemented by #41

### GET /trailers/{trailerId}/timeline

Accepts only cursor and limit (default 50, minimum 1, maximum 200). Opaque keyset
position uses occurredAt/id; it is never authorization. Every page rechecks fresh
membership and current designation; a cursor obtained before reassignment cannot
retain access and returns generic RESOURCE_NOT_FOUND afterward.

Returns {items,nextCursor}, ordered occurredAt DESC/id DESC. A late offline fact
keeps its original physical occurrence position despite a recent recordedAt.
This differs intentionally from #39 reconciliation's recordedAt ASC/id ASC
server chronology. Detail reads current projection; timeline never replays facts
to reconstruct it.

Items expose id, eventType, actorType, sourceType, occurredAt, recordedAt, nullable
reason, locationId/name, driverId/name, tractorId/operationalNumber, tripId,
tripStopId and a whitelist of known changes. No Tenant ID, actor User ID, client
operation ID, claims or raw metadata. Customer designation before/after values
are omitted. Scoped readers conservatively receive reason/tripId/tripStopId NULL
and no location-change IDs; Customer-owned Location references outside scope are
redacted from detail/list/timeline. Foreign/missing/out-of-scope Trailers are 404.
Generic Audit remains #61; evidence remains #49/#52/#53, Customer Portal #58+.
