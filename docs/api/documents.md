# Documents and Evidence API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

Binary objects live in Cloudflare R2. The API authorizes metadata and signed URL issuance.

## 1. Initialize upload

### POST /documents/uploads

Request:

```json
{
  "documentType": "BOL",
  "contentType": "image/jpeg",
  "sizeBytes": 2458102,
  "checksumSha256": "optional-hex"
}
```

Server:

1. checks capability/context;
2. creates Document with PENDING status;
3. assigns object key;
4. returns short-lived signed upload URL.

Response:

```json
{
  "documentId": "uuid",
  "uploadStatus": "PENDING",
  "uploadUrl": "signed-url",
  "expiresAt": "..."
}
```

Never return R2 account credentials.

## 2. Upload binary

Client sends binary directly to the signed R2 URL.

This is outside the JSON API.

## 3. Finalize upload

### POST /documents/{documentId}/upload-completion

Idempotency supported/required for mobile use.

Request:

```json
{
  "checksumSha256": "optional-hex"
}
```

Server verifies object existence/metadata as practical and transitions Document to UPLOADED.

If object is missing:

`409 UPLOAD_NOT_COMPLETE`.

## 4. Link Document to business resource

### POST /documents/{documentId}/links

Request:

```json
{
  "entityType": "TRIP_STOP",
  "entityId": "uuid"
}
```

Server validates:

- target exists;
- same Tenant;
- caller can access target;
- document type is compatible where business rules require it.

Document link is not an authorization boundary by itself.

## 5. Get document metadata

### GET /documents/{documentId}

Returns metadata only.

Customer-scoped callers must pass customer/resource authorization.

## 6. Download/view

### POST /documents/{documentId}/download-url

Returns a short-lived authorized URL.

Do not expose public permanent R2 objects for POD/BOL/evidence.

## 7. Delete/retention

Normal users do not physically delete evidence through MVP APIs.

Retention deletion is a controlled background/policy process and remains blocked until production retention policy is resolved.

## 8. Delivery evidence

Delivery completion uses the mobile execution endpoint:

`POST /stops/{stopId}/delivery-completions`

The referenced Document IDs must have `uploadStatus = UPLOADED`.

### SIGNED_BOL

Required:

- BOL/signed BOL Document.

### NO_RECEIVER_AVAILABLE

Required:

- BOL photo;
- DELIVERY_PHOTO;
- nonblank comment.

The server creates/updates the structured `delivery_evidence` record only when the evidence set is valid.

## 9. Pre-Trip evidence

Pre-Trip completion requires finalized Documents of types:

- PRETRIP_FRONT;
- PRETRIP_REAR;
- PRETRIP_DRIVER_SIDE;
- PRETRIP_PASSENGER_SIDE.

The API should reject completion when required images are missing.

## 10. OOS/damage evidence

Damage/OOS commands may reference:

- DAMAGE_PHOTO;
- other approved evidence type.

Server validates all Document IDs belong to the active Tenant and caller may link them to the Trailer/exception.

## 11. Fuel receipt

Fuel Event requires a finalized FUEL_RECEIPT Document.

## 12. Orphan cleanup

An initialized upload that is never finalized may be cleaned by a background job after a safe timeout.

Orphan cleanup must not remove an UPLOADED Document referenced by a valid business resource.
