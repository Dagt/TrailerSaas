# API Conventions v0.1

**Status:** Accepted v0.1

## 1. Authentication

Requests use a Firebase ID token:

```http
Authorization: Bearer <firebase-id-token>
```

Backend validates the token and resolves the internal TrailerSaaS User.

No role, Tenant or Customer authorization is trusted from token/client fields alone.

## 2. Tenant context

Tenant-owned resource paths use:

```text
/api/v1/tenants/{tenantId}
```

For every request the server validates:

1. authenticated User;
2. active TenantMembership;
3. required capability;
4. resource/customer scope;
5. current domain state.

Cross-tenant resource existence must not be leaked.

## 3. JSON naming

Use `camelCase`.

IDs are UUID strings.

Timestamps use ISO-8601 UTC strings, for example:

```json
"2026-10-04T20:15:30Z"
```

Site responses may also include their IANA timezone for local rendering.

## 4. Resource response baseline

Mutable aggregate responses should normally include:

```json
{
  "id": "uuid",
  "version": 7,
  "createdAt": "...",
  "updatedAt": "..."
}
```

Clients use `version` for optimistic concurrency when changing an existing aggregate.

## 5. Optimistic concurrency

Commands/updates that depend on mutable state include:

```json
{
  "expectedVersion": 7
}
```

If stale:

```http
409 Conflict
Content-Type: application/problem+json
```

with code `STALE_VERSION`.

The server does not silently apply a stale update.

## 6. Idempotency

Offline-capable / retry-sensitive commands require:

```http
Idempotency-Key: <uuid>
```

The same key + same logical request returns the previously recorded logical result.

Same key + incompatible request returns `409 IDEMPOTENCY_KEY_REUSED`.

Initially required for:

- On Duty / Off Duty;
- Pre/Post-Trip creation;
- Hook / Drop;
- Depart / Arrive;
- Stop service/complete actions;
- Fuel Event;
- Yard Move;
- Yard Check scan/complete;
- mobile OOS/damage commands;
- upload-finalization where retry is possible.

Web create commands may also send the header and should be safe to retry where documented.

## 7. Command endpoints

Do not expose:

```http
PATCH /trip/{id}
{"status":"COMPLETED"}
```

Instead expose an explicit action:

```http
POST /trips/{id}/cancellations
POST /stops/{id}/arrivals
POST /trailers/{id}/location-corrections
```

The service validates authorization and state transition.

## 8. Normal editable-resource updates

Master data and pre-execution descriptive edits may use `PATCH`.

Examples:

- Customer metadata;
- Site metadata;
- Driver profile;
- Trailer metadata;
- Order descriptive fields before physical execution.

`PATCH` requests include `expectedVersion`.

## 9. Success status codes

- `200 OK` — successful query/action returning existing/current resource.
- `201 Created` — new resource/action record created.
- `202 Accepted` — reserved for genuinely asynchronous processing; do not use simply because push delivery happens later.
- `204 No Content` — successful operation with no useful response body.

For operational commands, prefer returning the updated projection or command result rather than forcing an immediate second fetch.

## 10. Error format

Use Problem Details JSON plus a stable application code.

Example:

```json
{
  "type": "https://api.trailersaas.example/problems/invalid-state-transition",
  "title": "Invalid state transition",
  "status": 409,
  "code": "ORDER_ALREADY_IN_PROGRESS",
  "detail": "This Order can no longer be edited directly after execution has started.",
  "traceId": "..."
}
```

The public `detail` must not reveal cross-Tenant/Customer data.

## 11. Core error codes

Candidate shared codes:

- `AUTHENTICATION_REQUIRED`
- `TENANT_ACCESS_DENIED`
- `RESOURCE_NOT_FOUND`
- `CAPABILITY_DENIED`
- `CUSTOMER_SCOPE_DENIED`
- `VALIDATION_FAILED`
- `INVALID_STATE_TRANSITION`
- `STALE_VERSION`
- `IDEMPOTENCY_KEY_REUSED`
- `RESOURCE_CONFLICT`
- `EVIDENCE_REQUIRED`
- `UPLOAD_NOT_COMPLETE`
- `TRAILER_ALREADY_COUPLED`
- `TRACTOR_ALREADY_COUPLED`
- `TRAILER_SUBSTITUTION_NOT_AUTHORIZED`

## 12. Validation errors

For field validation, Problem Details may include:

```json
{
  "code": "VALIDATION_FAILED",
  "errors": [
    {
      "field": "orderNumber",
      "message": "must not be blank"
    }
  ]
}
```

## 13. Pagination

Use cursor pagination for potentially large operational/history lists.

Request:

```http
GET /orders?limit=50&cursor=...
```

Response:

```json
{
  "items": [],
  "nextCursor": "opaque-or-null"
}
```

Default `limit`: 50. Maximum proposed: 200.

Cursor contents are opaque to clients.

## 14. Filtering and sorting

Use explicit query parameters:

```text
?status=IN_PROGRESS
?customerId=...
?driverId=...
?from=...
?to=...
?sort=scheduledAt
?direction=asc
```

Unsupported filter fields should be rejected rather than silently ignored.

## 15. Date/time scheduling

Appointment timestamps are absolute timestamps where known.

Site timezone is returned separately so UIs can render operational local time.

Do not send ambiguous local timestamps without timezone context.

## 16. Deletes

Normal operational APIs should not physically DELETE referenced domain records.

Prefer explicit deactivate/disable commands or status changes for:

- Customer;
- Driver;
- Tractor;
- Trailer;
- membership.

## 17. Documents

Binary upload/download is not proxied through the normal JSON API when a signed R2 URL is appropriate.

Pattern:

1. create Document/upload session;
2. receive signed upload URL;
3. upload object;
4. finalize upload;
5. link Document to authorized entity;
6. request signed download URL when needed.

## 18. Response DTO boundaries

API DTOs must not expose:

- Firebase internal token details;
- raw R2 credentials/object secrets;
- internal authorization implementation;
- unrelated Customer/Tenant IDs;
- JPA lazy relationships.

## 19. Correlation

Backend should return a request/trace identifier on errors and log it with structured server logs.

Exact observability implementation is deferred to its ADR.
