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

## 8. Delivery evidence (future #45/#53)

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

## 9. Pre-Trip evidence (future #43/#53)

Pre-Trip completion requires finalized Documents of types:

- PRETRIP_FRONT;
- PRETRIP_REAR;
- PRETRIP_DRIVER_SIDE;
- PRETRIP_PASSENGER_SIDE.

The API should reject completion when required images are missing.

## 10. OOS/damage evidence (future #49/#53)

Damage/OOS commands may reference:

- DAMAGE_PHOTO;
- other approved evidence type.

Server validates all Document IDs belong to the active Tenant and caller may link them to the Trailer/exception.

## 11. Fuel receipt (future #45/#53)

Fuel Event requires a finalized FUEL_RECEIPT Document.

## 12. Orphan cleanup

An initialized upload that is never finalized may be cleaned by a background job after a safe timeout.

Orphan cleanup must not remove an UPLOADED Document referenced by a valid business resource.

## Issue #52 implementation boundary

All five transport endpoints in sections 1–6 are implemented by #52. Requests
are strict; unknown fields/query parameters, invalid enums, numeric coercion and
client provenance are rejected. Binary PUT/GET goes directly to private R2.

### Current authority

| Operation | Current fresh authority | Resource restrictions |
| --- | --- | --- |
| Initialize/finalize | `POD_UPLOAD`, active own Driver | BOL/POD/DELIVERY_PHOTO only; finalize requires uploader identity |
| Link | Same upload authority plus `TRIP_VIEW_ASSIGNED` | UPLOADED, own document, current Driver assignment to same-Tenant TRIP_STOP; maximum 50 links |
| Tenant read | `DOCUMENT_VIEW_ALL` | Existing same-Tenant linked Stops; metadata can inspect unlinked documents |
| Customer read | `DOCUMENT_VIEW_CUSTOMER_SCOPED` | Every linked Stop and all its mapped Customers must be within fresh scope; empty/ambiguous context denied |
| Driver read | `TRIP_VIEW_ASSIGNED`, active own Driver | Every linked Trip currently assigned; only a document with no actual links permits uploader metadata management with current `POD_UPLOAD` |
| Download | Read authority above | UPLOADED and valid authorized links mandatory; uploader alone is insufficient |

Read permissions never authorize uploading/linking. No roles, capabilities or
Firebase claims are added. Other upload types and resource targets remain blocked
until their workflow rules exist. In particular linking a photo does not approve
POD, complete a Stop or implement any evidence-set requirements from sections 8–11.

### Direct PUT and publication

Accepted MIME types are JPEG/PNG; PDF is also allowed for BOL/POD.
`DOCUMENT_ALLOWED_CONTENT_TYPES` may restrict the approved MIME set per deployment.
Send exact `Content-Type` and `Content-Length` to the signed PUT. Default maximum is 25 MiB
(configurable up to 100 MiB). Optional SHA-256 is 64 hexadecimal characters.
Content type is verified against stored object headers; bytes are streamed and
hashed by the backend, not trusted through client checksum metadata/ETag. This is
not antivirus, image decoding or evidence-content approval.

`upload-completion` requires a UUID `Idempotency-Key` and accepts only optional
nullable `checksumSha256`. It copies the temporary object to a fresh unpublished
private key, verifies that copy and commits its identity, SHA-256, UPLOADED and
server `uploadedAt` with the existing operation ledger. Reusing a pending PUT
cannot address the published key. Database protection also prevents rewriting a
finalized record. An already finalized document never changes bytes on retry.

Missing object: `409 UPLOAD_NOT_COMPLETE`. Size/MIME/hash mismatch:
`409 UPLOAD_CONTENT_MISMATCH`. Incompatible key reuse:
`409 IDEMPOTENCY_KEY_REUSED`. Provider failure: safe
`503 OBJECT_STORAGE_UNAVAILABLE`; database failure: safe 5xx. Reuse the same key
and body after uncertainty. Fresh authorization is checked before ledger replay.
Initialization itself has no replay guarantee; a URL is not proof of upload.

External storage and PostgreSQL do not share a transaction. A copied candidate
left after database failure is private and has no downloadable reference. A
retry verifies a new candidate; committed results replay without another copy.
Temporary objects and unpublished candidates are not deleted by #52.

### Downloads and cleanup

Metadata excludes object keys, Tenant/uploader identities and storage credentials.
Uploader metadata management applies only when the document has no actual links,
whether PENDING or UPLOADED. An empty authorized-link result is not proof that a
document is unlinked. Once linked, every resource must remain authorized; removing
or changing a Driver assignment denies metadata and download with safe 404.
Restoring assignment re-evaluates current permissions on the next request.
Completion also revalidates all existing links before returning an already
finalized result or replaying its ledger response; historical authorship and a
saved Idempotency-Key do not bypass current linked-resource authority.
`download-url` returns `{downloadUrl, expiresAt}` for an authorized published object.
URLs are temporary bearer grants: default download TTL 60 seconds (maximum 300),
upload TTL 300 (maximum 900). Already-issued URLs remain usable until expiry even
if membership is revoked. Keep them out of logs, analytics and shared messages.

`PendingUploadInspection` provides bounded old-PENDING candidates and an exclusive
row-locked revalidation of status, links and temporary-object existence. It is a
trusted per-Tenant Java hook, not HTTP, deletion permission or a lease. Future #55
jobs must revalidate under the same row lock at the moment of cleanup; an earlier
eligibility result never authorizes deletion. Published objects/history are excluded.
Unpublished copy candidates need future prefix enumeration plus reference checks.
No automatic cleanup is enabled and OQ-057 retention remains unresolved.

See [implementation/security traceability](issue-52-document-storage.md) and
[environment configuration](../development/environment-configuration.md).
