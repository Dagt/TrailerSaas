# Documents and Evidence API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

Binary objects live in Cloudflare R2. The API authorizes metadata and signed URL issuance.

## Issue #49 implemented corrective and damage boundary

Yard Home now supports explicit Location Correction, Load State Correction,
Discrepancy Report and Report Damage / Mark OOS. The original Yard Move remains
unchanged. Yard Check, Missing reconciliation and notifications remain #50/#51/#55.
No capability or role grant changes: YARD_SPOTTER is Tenant-scoped, has the two
correction capabilities, and has no OOS grant; a currently authorized multi-role
user may use OOS. Selecting Site, QR scan, a local draft or an upload grants no
mutation authority. Return to Service and unresolved OQ-054 remain separate.

See [#49 contracts, acceptance, tests and release limitations](../mobile/issue-49-yard-discrepancy-oos.md).

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

## 8. Delivery evidence (implemented #45/#53)

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

The server inserts one immutable `delivery_evidence` record only when the evidence set is valid and the real Stop completion, event and fulfillment commit in the same transaction.

## 9. Pre-Trip evidence (implemented #43/#53)

Pre-Trip completion requires finalized Documents of types:

- PRETRIP_FRONT;
- PRETRIP_REAR;
- PRETRIP_DRIVER_SIDE;
- PRETRIP_PASSENGER_SIDE.

The API should reject completion when required images are missing.

## 10. Damage evidence (#45/#53) and implemented Yard OOS (#49)

The implemented Yard OOS command requires one to fifty distinct finalized own
DAMAGE_PHOTO documents bound exclusively to its Trailer. Driver damage remains
the separate assigned-work workflow; no arbitrary evidence type is accepted by OOS.

Server validates all Document IDs belong to the active Tenant and caller may link them to the Trailer/exception.

## 11. Fuel receipt (implemented #45/#53)

Fuel Event requires a finalized FUEL_RECEIPT Document.

## 12. Orphan cleanup

An initialized upload that is never finalized may be cleaned by a background job after a safe timeout.

Orphan cleanup must not remove an UPLOADED Document referenced by a valid business resource.

## Issue #52 implementation boundary

All five transport endpoints in sections 1–6 are implemented by #52. Requests
are strict; unknown fields/query parameters, invalid enums, numeric coercion and
client provenance are rejected. Binary PUT/GET goes directly to private R2.

### Issue #52 authority (superseded by the #53 type matrix below)

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

## Issue #53 implementation boundary

The upload transport remains #52; #53 adds type-specific authorization and a
synchronous, server-side `OperationalEvidencePolicy`. No inspection, delivery,
exception, Fuel or Yard command endpoint is implemented here.

| Type | Fresh upload/finalization capability | Supported attachment/context |
| --- | --- | --- |
| BOL, POD, DELIVERY_PHOTO | POD_UPLOAD + active own Driver | Current assigned TRIP_STOP; delivery validation accepts BOL, not generic POD as a signed-BOL substitute |
| Four PRETRIP positions | PRETRIP_CREATE + active own Driver | Current assigned TRIP; validator also checks the real current active Tractor |
| FUEL_RECEIPT | FUEL_EVENT_CREATE + active own Driver | Current assigned TRIP and real current active Tractor |
| DAMAGE_PHOTO (Driver) | EXCEPTION_REPORT + active own Driver | Current assigned TRIP or TRIP_STOP; never arbitrary Trailer authority |
| DAMAGE_PHOTO (OOS) | TRAILER_OOS_MARK in the concrete Trailer context | #49 scoped producer, exclusive TRAILER link and atomic MARKED_OOS evidence association |

Finalization resolves the persisted type before checking its capability, including
ledger replay. Unlinked uploader metadata management uses that type's current
authority. Linked metadata/download still require all-or-nothing current resource
access. Trip-only inspection/Fuel attachments have no Customer-scoped Trip read
grant; Customer read of delivery Stops retains all mapped-Customer checks.

Pre-Trip requires exactly four distinct UPLOADED references with the exact front,
rear, Driver-side and passenger-side types, same Tenant/uploader and exact Trip
attachment. #43 must additionally validate real Shift/inspection identity and
equipment workflow before marking an inspection complete; no such rows are faked.
Fuel requires one finalized own FUEL_RECEIPT in the authorized Trip/Tractor context.
Attachment-free upload-set variants support a NEW future inspection or Shift-only
Fuel Event when its owning domain has already authorized the real Shift/Tractor.
They reject already-linked uploads and validate files only; they neither approve
nor create an inspection/event. A Trip is not fabricated for nullable-trip workflows.
Damage requires own finalized DAMAGE_PHOTO in the exact assigned work context.
The separate OOS validator requires TRAILER_OOS_MARK and exact Trailer attachment;
it changes no Trailer state and does not grant Driver generic Trailer access.

SIGNED_BOL requires a finalized own BOL linked to the exact IN_SERVICE Delivery
Stop on the currently assigned AT_STOP Trip. NO_RECEIVER_AVAILABLE additionally
requires a distinct DELIVERY_PHOTO and a stripped nonblank comment. Validation
is structural, not OCR, signature recognition or evidence-image approval.

`recordDelivery` joins the owning workflow's transaction, validates first, invokes
its completion action, verifies the persisted COMPLETED Stop/version and matching
authenticated completion event, then inserts one immutable delivery_evidence row.
No-op/failing transitions and missing evidence roll back everything. #45 owns the
actual completion/fulfillment and existing ledger replay; repeated direct policy
calls conflict safely and cannot create a second evidence row.
Before claiming the ledger, #45 calls `validateDeliveryForReplay`: exclusive Trip
parent lock plus fresh full document/resource authority, including committed replay.
This avoids shared-lock upgrade/ledger deadlocks; the committed Stop and event must
belong to the same PostgreSQL transaction as new evidence persistence.

Errors: 409 EVIDENCE_REQUIRED or UPLOAD_NOT_COMPLETE; 400 VALIDATION_FAILED for
malformed sets/types/duplicates; safe 404 RESOURCE_NOT_FOUND for invisible context;
403 CAPABILITY_DENIED for missing authority; INVALID_STATE_TRANSITION or
RESOURCE_CONFLICT for incompatible execution/persistence. Infrastructure stays 5xx.
See [#53 policy, dependencies and transaction proof](issue-53-evidence-validation.md).

## Issue #43 inspection ownership extension

Validated Pre-Trip creation attaches four immutable INSPECTION references within
its own transaction, optionally alongside the exact Trip links. Generic HTTP link
requests still accept only TRIP_STOP/TRIP: clients cannot attach an arbitrary
inspection UUID. Own active Driver may read own inspection photographs; Tenant-wide
DOCUMENT_VIEW_ALL may read them. Customer scope grants no inspection access. If
other links coexist, all must still be currently authorized; historical uploader
identity cannot override a revoked Trip assignment. Unlinked upload management,
R2 final publication/checksum/immutable bytes and replay authorization stay intact.
The #43 extension does not add a Yard/OOS endpoint. The implemented #45 consumers are described below.

## Issue #45 consumers implemented

Road POD, Fuel and Driver exceptions now consume the #53 policy from their own
application transaction. Documents transport, all-or-nothing linked authorization,
private staging/publication, actual SHA-256 and finalized-object immutability are
unchanged. The mobile worker finalizes each required file, freezes its Document IDs,
then attaches to the exact authorized TRIP_STOP (POD/Stop damage) or TRIP (Fuel/Trip
damage) before the business command. A link alone never accepts evidence.
The production delivery endpoint uses validateDeliveryForReplay before the ledger
and recordDelivery around the real completion/fulfillment callback. Fuel/damage
have equivalent fresh policy checks for committed terminal replay; historical
uploader identity never overrides lost resource authorization. No receiver alerts,
Yard/OOS producer, retention or background jobs are implemented here.

### #49 scoped Trailer DAMAGE_PHOTO producer

POST /trailers/{trailerId}/damage-uploads requires TRAILER_OOS_MARK, ACTIVE Tenant/
membership and a real active own-Tenant Trailer; no Driver/Trip is required. The
existing closed Initialize DTO admits documentType=DAMAGE_PHOTO, image/jpeg or
image/png, positive deployment-bounded sizeBytes and optional SHA-256. Mobile
always captures and verifies a private byte copy/hash. It returns the existing
201 PENDING signed PUT reservation and persists an exclusive TRAILER link.
Initialization is not idempotent; lost ACK can leave an unused PENDING reservation.

The original upload-completion/metadata/download routes are reused. Completion
requires own uploader, fresh OOS capability and active Trailer, verifies bytes/
size/hash and the same-key ledger before UPLOADED. Generic links cannot relink a
Trailer-scoped photo into assigned Trip work. No permanent private URLs or R2
credentials enter SQLite. Metadata/download retain all-linked-resource scope;
no DOCUMENT_VIEW_ALL grant is added to Spotter. Upload/finalization alone is no
OOS event or accepted operational evidence: /yard/out-of-service validates and
associates the finalized set atomically to the actual event. Road/POD/Fuel/inspection
transport and evidence policy remain unchanged.

## Customer accepted-evidence reads (#60)

`GET /tenants/{tenant}/customers/{customer}/documents` is a bounded discovery
index of published files explicitly retained by an accepted Delivery receipt and
actually mapped to this Customer's Orders. Filters: documentType BOL/POD/
DELIVERY_PHOTO, orderId, stopId, documentId, limit20/max50 and cursor. Order is
uploadedAt DESC, then document/Order/Stop IDs ASC. Each row is a file/Order/Stop
association, not a unique-file count. No signed URLs or storage keys in the index.

V29 retains the validated BOL/photo IDs in the existing append-only receipt during
the real delivery transaction. Historical receipts remain unmodified with NULL
file references: their acceptance is real but cannot prove which later-linked
file was accepted. Generic uploaded POD has no current accepted operational
writer and is not inferred as proof. BOL reference text never becomes a file.

All linked work must be visible to the single selected Customer. Shared Stop/
file evidence is withheld even for additive Admin/multi-Customer roles. Pending,
unlinked and unrelated later uploads are not discoverable. Existing metadata
and download routes accept optional query `customerId`, requiring ACTIVE Tenant,
membership, Customer, current document capability, exact accepted association and
the original all-linked-resource checks. No new download endpoint or Customer
upload authority. Ordinary internal/Driver transport is unchanged.

Download remains an explicit fresh authenticated POST, returning a private
short-lived grant. Disabled R2 returns safe503 OBJECT_STORAGE_UNAVAILABLE;
authorized metadata/index reads remain available. React keeps grants only in
component memory and clears on expiry, cancellation, logout or scope change.
An issued URL can remain valid until expiry; instant revocation is not promised.
See [implementation, SQL/migration and tests](../web/issue-60-customer-inventory-deliveries-documents.md).
