# Relational schema v1.0.1 hardening amendment

Status: Accepted v1.0.1 hardening amendment

**Accepted date:** 2026-10-06

**Approval:** Explicit owner approval incorporated in [PR #75](https://github.com/Dagt/TrailerSaas/pull/75), following the [owner review](https://github.com/Dagt/TrailerSaas/pull/75#pullrequestreview-5421750407).

**Issue:** [#74](https://github.com/Dagt/TrailerSaas/issues/74)

**Tracking:** [#66](https://github.com/Dagt/TrailerSaas/issues/66)

**Baseline:** [Accepted schema v1.0](schema-v1.0.md)

**Database:** PostgreSQL 18; PostgreSQL 17 remains the temporary fallback allowed by ADR-003.

The Accepted schema baseline for implementation is schema-v1.0.md plus this
amendment. Where they conflict, this v1.0.1 amendment supersedes only the explicitly
listed sections below; all unmentioned columns, relationships and invariants
remain unchanged. The original Accepted v1.0 file remains physically intact.
This is documentation only: no DDL, migration or functional code is delivered.
The existing technical V1 migration is outside this amendment and remains intact.
The hardening gate is satisfied upon merge of #75; no #22 implementation is part
of this PR. The explicit implementation deferments below do not block #22.

## Sources and interpretation

- Architecture: [ADR-003](../decisions/ADR-003-primary-database.md),
  [ADR-006](../decisions/ADR-006-persistence-access.md),
  [ADR-007](../decisions/ADR-007-authentication.md),
  [ADR-011](../decisions/ADR-011-tenancy-and-authorization.md),
  [ADR-012](../decisions/ADR-012-mobile-offline-sync.md),
  [ADR-013](../decisions/ADR-013-postgresql-rls.md),
  [ADR-022](../decisions/ADR-022-postgresql-outbox-jobs.md).
- Authorization: [tenancy](../security/tenancy-and-authorization.md) and
  [capabilities](../security/permission-capabilities.md). The latter still labels
  its capability matrix Draft and includes TBD permissions; A fixes the existing
  MVP role/scope matrix without granting unresolved capabilities.
- Contracts: [conventions](../api/conventions.md),
  [endpoint catalog](../api/endpoint-catalog.md),
  [identity/master data](../api/identity-master-data.md),
  [mobile execution](../api/mobile-execution.md),
  [orders/dispatch](../api/orders-dispatch.md), [yard](../api/yard.md),
  [alerts/devices/audit](../api/alerts-audit.md).
- Offline: [sync protocol](../mobile/offline-sync-protocol.md) and
  [local model](../mobile/local-offline-data-model.md).
- Auditability: [non-functional requirements](../architecture/non-functional-requirements.md), especially NFR-041.
- Domain: [conceptual model](../domain/domain-model.md),
  [workflows](../workflows/core-workflows.md),
  [Order](../state-machines/order.md), [Trip](../state-machines/trip.md),
  [Stop](../state-machines/stop.md), [Trailer](../state-machines/trailer.md),
  [Yard Check](../state-machines/yard-check.md).
- Evidence: [requirements](../product/requirements-catalog.md) FR-014/015/016,
  [business rules](../product/business-rules.md),
  [discovery baseline](../discovery/design-partner-baseline.md),
  [answer matrix](../discovery/questionnaire-answer-matrix.md),
  [open questions](../product/open-questions.md).

Apply AGENTS.md source-of-truth priority. Discovery examples are evidence, not
approved generic enums. Conceptual candidate states do not supersede Accepted
state machines. No future code is used as design evidence.

## Changes from v1.0

Mechanical hardening means an unambiguous technical repair of accepted intent.
Accepted amendment means the owner has explicitly approved the design choice.
Explicit deferral means additional implementation/product detail is excluded
from this baseline and assigned to a later bounded Issue. No approval remains
pending for the schema decisions in A–Q.

| Item | Classification / disposition | Accepted change and reason |
|---|---|---|
| A | Mechanical hardening | Partial role uniqueness indexes cover nullable Customer scope; mirror for intended invitation roles. |
| B | Accepted amendment | Global User device registrations with immutable User binding; Tenant context belongs to notification authorization. |
| C | Mechanical hardening | Four-column UNIQUE NULLS NOT DISTINCT rejects duplicate generic/specific mappings while preserving multiple requested Stops. |
| D | Accepted amendment | Nullable result before observation; retain the four accepted result values and assign MISSING only on completion. |
| E | Accepted amendment | Nullable audit actor membership with a tenant/user-consistent historical FK. |
| F | Explicit deferral (#29) | Preserve trailer_type/capability_notes; structured ownership/size is excluded from v1.0.1. |
| G | Mechanical hardening | Separate delivered_at, completed_at and cancelled_at milestone timestamps from accepted Order transitions. |
| H | Accepted amendment; operational policy deferred (#32) | OPEN/CLOSED allocation-period lifecycle, no reopen; exact authority/edit/closure policy is deferred. |
| I | Accepted amendment | Conservative ASCII-space/case generated key, reject controls, preserve original display and Tenant uniqueness. |
| J | Accepted amendment | Trim + lowercase whole invitation email, one PENDING per Tenant/email, verified Firebase email matching without provider aliases. |
| K | Mechanical hardening | Parent-first FK dependency order; no reverse mutable coupling pointer; add optional event FKs after both endpoints exist. |
| L | Accepted amendment | Transaction-local PROCESSING claim plus unique key; business effect and terminal replay result commit atomically. |
| M | Accepted amendment | Three actor types and four causal source types explicitly satisfy NFR-041, including mobile automation. |
| N | Accepted amendment | SQL Site/location consistency and server-side scope validation; normalize ad-hoc destinations before physical transitions requiring OperationalLocation. |
| O | Accepted amendment; optional transport deferred | device_id references a User registration with composite User FK, never FCM/hardware identity or an idempotency key. |
| P | Mechanical hardening | Confirm OQ-058 resolved; link its existing resolution to Accepted schema/API and this amendment. |
| Q | Accepted amendment; numeric/type policy deferred (#55) | ADR-022 outbox_jobs with bounded retries, fenced leases/recovery and Tenant-safe claiming. |

### A. RoleAssignment nullable uniqueness

v1.0's five-column ordinary UNIQUE permits repeated tenant-wide assignments
because customer_id is NULL. Retain NOT NULL for tenant_id, membership_id,
role_code and scope_type. Replace that UNIQUE with two unique partial indexes:

```text
role_assignments:
  UNIQUE (tenant_id, membership_id, role_code) WHERE scope_type = 'TENANT'
  UNIQUE (tenant_id, membership_id, role_code, customer_id) WHERE scope_type = 'CUSTOMER'
  CHECK scope_type IN ('TENANT', 'CUSTOMER')
  CHECK (scope_type = 'TENANT' AND customer_id IS NULL)
     OR (scope_type = 'CUSTOMER' AND customer_id IS NOT NULL)
```

Keep the composite membership FK and add the composite Customer FK when its
parent exists, before Customer-scoped assignments can be written. ADR-011 roles
remain tenant-wide except CUSTOMER_DISPATCHER, which requires Customer scope;
the same closed role/scope matrix can be enforced in SQL without granting new
capabilities. Multiple different Customer assignments remain permitted.

The closed MVP role/scope matrix applies to both role_assignments and
invitation_role_assignments:

| Role | Scope |
|---|---|
| CUSTOMER_DISPATCHER | CUSTOMER |
| TENANT_ADMIN | TENANT |
| TRANSPORT_OPERATIONS | TENANT |
| YARD_SPOTTER | TENANT |
| DRIVER | TENANT |

Future location scope for Yard Spotter is outside this amendment/MVP schema.

Apply the same two indexes and scope checks to invitation_role_assignments,
substituting invitation_id for membership_id. This prevents duplication when
the intended set is materialized on acceptance. PostgreSQL NULLS NOT DISTINCT
is an equivalent supported alternative, but partial indexes state the two
approved scope cases directly. Application validation complements SQL, never
replaces it. No schema objects are created by this document.

### B. Global user_devices, Tenant-scoped notifications

ADR-011 makes User global and permits multiple memberships. Remove tenant_id
from user_devices: a registration belongs globally to one User, not one Tenant.
Its User binding is historical and immutable. Accepted replacement shape:

```text
id                 uuid PK -- User device registration identity
user_id            uuid FK users NOT NULL -- immutable authenticated User binding
platform           varchar NOT NULL -- IOS / ANDROID; WEB only if separately required
fcm_token          varchar NULL -- mutable transport registration, not identity
active             boolean NOT NULL
last_seen_at       timestamptz NOT NULL
created_at         timestamptz NOT NULL
updated_at         timestamptz NOT NULL

UNIQUE (id, user_id)
UNIQUE (fcm_token) WHERE fcm_token IS NOT NULL
INDEX (user_id, active)
```

A registration can exist without push permission/token; NULL token does not
invalidate offline attribution. Token rotation for the same User registration
keeps its UUID. active indicates registration availability, not Tenant membership.
Logout deactivates the registration and removes/invalidates its FCM token.
Another User signing in on the same installation creates a new registration
with a new UUID; never update the old row's user_id. If Firebase reuses the same
FCM token, transfer/rotate it atomically to the new registration and deactivate
the previous registration/remove its token. The unique token index prevents
simultaneous ownership. Retain old registration rows for historical provenance;
do not reconstruct a ledger actor from a different account's current session.

The accepted authenticated global endpoints are POST /devices and
DELETE /devices/{id}, as listed in the endpoint catalog. They are not Tenant paths.
Deactivation cuts push on that registration across all Tenants of its User;
it does not remove the User or its memberships.

Tenant ownership remains on alerts, alert_recipients and tenant-owned outbox
jobs. Resolve a recipient through current active membership and Customer/resource
scope in that Tenant, then select that User's active push registrations. Recheck
authorization at delivery; do not send unrelated Tenant data simply because a
token is registered. Push remains a hint and authoritative instructions remain
available via scoped APIs. No implicit tenant subscriptions or per-Tenant device
copies are introduced.

Global registration access is User-authorized. It must not blindly use the
tenant_id RLS template. Only a controlled registry lookup may resolve delivery
tokens; neither arbitrary Tenant queries nor a BYPASSRLS worker are justified.

### C. Executable Stop/Order/requested Stop identity

Mechanical hardening: preserve the four-column mapping cardinality and replace
ordinary UNIQUE with PostgreSQL 18 NULLS NOT DISTINCT:

```text
trip_stop_orders:
  UNIQUE NULLS NOT DISTINCT (tenant_id, trip_stop_id, order_id, order_stop_id)
  FK (tenant_id, trip_stop_id) -> trip_stops (tenant_id, id)
  FK (tenant_id, order_id) -> orders (tenant_id, id)
order_stops:
  UNIQUE (tenant_id, order_id, id)
trip_stop_orders:
  FK (tenant_id, order_id, order_stop_id) -> order_stops (tenant_id, order_id, id)
```

order_stop_id stays nullable using default MATCH SIMPLE: a missing optional
reference is legal, but a supplied reference must belong to that exact Order,
not just the Tenant. A generic Order/Stop mapping with NULL cannot be duplicated;
the same specific requested Stop cannot be duplicated either. Different requested
Stops of the same Order may map to the same executable Stop. Do not impose
UNIQUE (tenant_id, trip_stop_id, order_id) on the whole table: that would silently
restrict the accepted flexibility. The API's orderIds remains an Order-level view,
not an additional mapping-cardinality restriction.

NULLS NOT DISTINCT is supported by both PostgreSQL 18 and the ADR-003 temporary
17 fallback. Two equivalent partial indexes would be valid if needed:
unique (tenant_id, trip_stop_id, order_id) WHERE order_stop_id IS NULL, and
unique (tenant_id, trip_stop_id, order_id, order_stop_id) WHERE order_stop_id IS NOT NULL.
No reason to choose that alternative is identified; the accepted preference is
the single NULLS NOT DISTINCT constraint.

### D. Yard Check pending observation

Change only result nullability to varchar NULL. No new PENDING
result enum is introduced. In the expected snapshot initialize expected = true,
found = false, result = NULL, scanned_at/scanned_by_user_id = NULL. Expected
membership and expected_location_id never change during scanning.

On observation, persist found = true, scan timestamp/actor and MATCHED,
LOCATION_MISMATCH or UNEXPECTED. An unexpected Trailer creates expected = false,
found = true and UNEXPECTED. Duplicate scans do not create a second item;
ALREADY_SCANNED is an API outcome, not a fifth persisted result.

At completion only unobserved expected rows become MISSING. SQL row checks can
enforce: NULL or MISSING implies expected = true and found = false; MATCHED or
LOCATION_MISMATCH implies expected = true and found = true; UNEXPECTED implies
expected = false and found = true; found requires scan timestamp and scan actor.
The accepted result set is enforced whenever result is non-null. Application
transaction validation ensures COMPLETED checks have no NULL results and become
immutable. A cancelled check may retain unobserved NULL items without declaring
them missing. Cross-table lifecycle rules are not CHECK subqueries.

This follows WF-014 and the Yard Check state machine: absence of a scan during
IN_PROGRESS is not yet a missing finding. API/local cache must represent result
as nullable for unobserved snapshot reads;
no accepted scan-response enum changes here.

### E. Historical authorization context for audit

Add actor_membership_id uuid NULL to audit_events. Retain the global
actor_user_id and actor_type. Add a parent unique key and composite FK:

```text
tenant_memberships: UNIQUE (tenant_id, id, user_id)
audit_events:
  FK (actor_user_id) -> users (id)
  FK (tenant_id, actor_membership_id, actor_user_id)
    -> tenant_memberships (tenant_id, id, user_id)
  CHECK actor_membership_id IS NULL OR actor_user_id IS NOT NULL
  INDEX (tenant_id, actor_membership_id, occurred_at DESC)
```

New human Tenant-authorized actions record both authenticated User and the acting
membership; historical/system records need not invent a membership. HUMAN_USER
requires actor_user_id; SYSTEM/INTEGRATION allow both references NULL and must
not impersonate a User. Disable memberships instead of deleting/reassigning their
User identity; FKs RESTRICT preserve history. Membership status and role sets can
later change, so this FK alone does not prove the old permissions. Preserve the
relevant authorization facts in existing audit before/after data where the action
requires them; do not infer historical grants from today's roles. Applying this
column to every operational event is not silently included in E.

### F. Trailer ownership and physical metadata

FR-016 and the conceptual Trailer attributes support type/size/capability. Discovery
lists 53-foot and 48-foot dry vans, two low-profile 53-foot vans and heavy-load
specialization. Existing trailer_type and capability_notes, and the API example
53_DRY_VAN, already retain descriptive information without a new product taxonomy.
Keep these fields and do not reinterpret designated_customer_id as ownership.

The answer matrix says company-controlled, mostly leased. This is evidence of
control and lease arrangements, not mutually exclusive generic ownership enums.
The third-party case is not a defined structured category: the reviewed accepted
requirements do not specify its taxonomy, permissions or relationship to control.
Do not add OWNED/LEASED/THIRD_PARTY or a boolean owner shortcut by assumption.
Do not add VIN, plates, axle counts, heights, payload ratings or lease accounting.

Explicit deferral: structured ownership and structured size metadata are excluded
from MVP schema v1.0.1. Retain only trailer_type and capability_notes for this
metadata. #29 may reopen additional metadata only with an approved operational
requirement defining dimensions/units/categories and scope. This accepted deferral
does not block #22 and does not invent a classification.

### G. Order milestone timestamps

Add three nullable timestamptz columns to orders:

```text
delivered_at timestamptz NULL -- all required fulfillment/POD obligations satisfy DELIVERED
completed_at timestamptz NULL -- administrative DELIVERED -> COMPLETED close
cancelled_at timestamptz NULL -- direct or final CANCELLING -> CANCELLED resolution
```

Populate them atomically with their respective accepted transitions. COMPLETED
retains delivered_at; entering CANCELLING does not set cancelled_at. Partial
delivery does not populate delivered_at and cancellation retains completed
Stop/POD history. Do not equate these to Trip completion or Shift Off Duty.

For the accepted no-reopen lifecycle, SQL can enforce: DELIVERED/COMPLETED require
delivered_at; COMPLETED requires completed_at and delivered_at; CANCELLED requires
cancelled_at; other states do not carry these future milestones; completed_at
and cancelled_at cannot coexist. Timestamp ordering is about validated transition
occurrence, not raw device clocks. Record server receipt separately in existing
event history when relevant; do not lose offline occurredAt or trust it blindly.
No request/response field is made mandatory by adding persistence milestones.

### H. Load manifest lifecycle

v1.0 requires load_manifests.status but declares no values or transitions. The
conceptual manifest, WF-008/011 and #32 allocation intent establish grouping,
Trailer association and history, but not a complete lifecycle.

Accepted minimum lifecycle: OPEN means an operational
allocation period is still in use; CLOSED means its allocation period has ended
and it is retained as history. Transitions are create -> OPEN and
OPEN -> CLOSED, without reopening. These are not synonyms
for Trailer LOADED/EMPTY, Order DELIVERED/COMPLETED, Trip COMPLETED or Shift Off Duty. No automatic
closure on Hook, Drop, departure or Off Duty is assumed.

The OPEN/CLOSED schema status set is approved. Exact closure trigger/authority,
allocation-edit policy, partial unloading, returns and cancelled remainder are
explicitly deferred to #32 before manifest commands are implemented. #32 must
also review whether load_manifests needs optimistic version and/or closed_at;
neither field nor new API commands are added by this amendment. The operational
deferment does not block earlier identity/domain migrations.

### I. Human equipment identifier normalization

Keep operational_number unchanged for display. Use a STORED generated
operational_number_normalized column on tractors and trailers with Tenant-scoped
UNIQUE (tenant_id, operational_number_normalized), replacing raw-number identity
uniqueness as the authoritative duplicate barrier. Accepted canonicalization:

1. Trim leading/trailing ordinary ASCII spaces (U+0020) only.
2. Collapse internal runs of ordinary ASCII spaces to one ordinary space.
3. Fold ASCII a-z to A-Z with a deterministic immutable expression/collation.
4. Require a nonempty result. Retain punctuation, leading zeros and internal
   separators and non-ASCII characters without broad Unicode equivalences;
   do not strip all spaces or rewrite BH T# as BHT#.

Reject tabs, newlines and control characters as invalid input; do not normalize
them into spaces or silently remove them. Application validation and SQL input
constraints must preserve this rule when the equipment schema is implemented.

Examples: ` t114 ` and `T114` collide; `bh  t114` and `BH T114` collide;
`T-114`, `T114` and `T0114` remain distinct. A generated expression plus index
prevents application bypass/drift. PostgreSQL expression functions must be immutable
and equality deterministic; document the exact expression/collation in the later
approved migration review. No new extension is needed.

The owner approves this conservative equivalence policy. Wider Unicode
equivalences and punctuation removal are excluded. Normalize valid search input
using the same rule; UUID/QR identity and the displayed value remain unchanged.

### J. Invitation email, active uniqueness and linking

Add email_normalized varchar NOT NULL derived from email by trim followed by
lowercase of the whole email, retaining the original email for display. The
lowercase rule includes the local part. Do not remove dots or plus suffixes,
or apply Gmail/provider-specific aliases. Use the same rule for invited and
Firebase-authenticated email comparison.

```text
membership_invitations:
  UNIQUE (tenant_id, email_normalized) WHERE status = 'PENDING'
  UNIQUE (token_hash)
  CHECK email_normalized is nonempty
```

Define active for this index as PENDING, not expires_at > now(). Time-dependent
predicates are unsuitable for immutable index membership. On re-invitation,
transactionally mark a stale expired PENDING invitation EXPIRED before inserting
another; retain history and its intended role sets. One
pending invitation carries multiple roles/Customer scopes (A), not one invitation
per role. Accepted/revoked/expired invitations may coexist historically.

Acceptance in #25 must validate token hash, status, expiry, and authenticated
Firebase UID/email from the token verified by the backend. Require
email_verified = true and normalized Firebase email to match the invited email
before binding accepted_by_user_id, membership and roles. Client email or a token
alone is insufficient; mismatch/unverified/missing email never silently links an
account. Consume atomically under row lock; UNIQUE (tenant_id, user_id) continues
to protect membership creation and retries must not duplicate roles.

This approved amendment resolves ADR-007's invitation email-verification follow-up.
It implements neither Firebase nor invitation acceptance; #25 owns implementation.

### K. Couplings and FK construction order

The accepted column graph does not actually require a trailers -> couplings or
tractors -> couplings FK. Active coupling is derived from trailer_couplings;
trips.planned_initial_trailer_id is planning, not a mutable coupling pointer.
Keep this direction and do not add a redundant current_coupling_id projection.

Create equipment and its parents first, then Trips/Stops, then movement events
and couplings. Every referenced tenant key has UNIQUE (tenant_id, id) before
its child FK. Couplings have tenant-safe FKs to Trailer, Tractor, Driver and optional
Trip, plus two partial UNIQUE indexes on (tenant_id, trailer_id) and
(tenant_id, tractor_id) where dropped_at IS NULL. Preserve hook/drop interval history.

Movement events as specified in v1.0 do not point to couplings. Give movement
events UNIQUE (tenant_id, id) and create them before coupling hook_event_id and
drop_event_id FKs; alternatively add these optional FKs after both tables exist
in the same future migration. Event-to-coupling insertion order is then event
first, coupling second, inside one business transaction. Validate event type and
matching Trailer/Trip context in the application transaction; a tenant FK alone
does not validate event meaning. No disabled constraints or arbitrary deferred
integrity are needed. Do not introduce the reverse FK just for convenience.

Dependency groups, not migration filenames or version numbers:

1. Existing technical baseline remains first and unchanged.
2. Global users and tenants; tenant_settings and tenant_memberships.
3. Customers, physical_sites, customer_sites, operational_locations; then invitations
   and roles with their membership/Customer FKs.
4. Drivers/tractors/trailers and QR identities; global user_devices after users.
5. Orders/order_stops/cargo; manifests and allocations after their parents, with H's approved status set.
6. Trips/trip_stops, trip_stop_orders and assignment/Stop events.
7. Trailer movement events, then couplings with event references.
8. Shifts/corrections, inspections/fuel; documents/links and delivery_evidence.
9. Yard Checks/items; alerts/recipients; audit and processed-client-operation ledger.
10. outbox_jobs after tenants; it may move earlier before the first domain command
    requiring atomic durable side effects because aggregate references are not core FKs.

Create integrity constraints/indexes with each owning group before its data is
exposed, not in a final catch-all step that temporarily permits corruption.
No new migration number is allocated here; v1.0's sample V001... sequence must
not overwrite existing technical V1 or be copied as an actual future numbering plan.

### L. Concurrent offline replay

Use an uncommitted PROCESSING row as a transaction-local key claim:

```text
processed_client_operations:
  request_hash varchar NOT NULL
  status in (PROCESSING, SUCCEEDED, REJECTED)
  created_at timestamptz NOT NULL
  processed_at timestamptz NULL -- populated only for terminal status
  UNIQUE (tenant_id, client_operation_id) -- unchanged and authoritative
```

Authenticate and establish TenantContext before ledger access. Under a short
READ COMMITTED transaction, attempt to insert PROCESSING using the unique key
and conflict-safe insertion. The insert is the claim, not a prior existence query.
A concurrent duplicate waits on unique-index arbitration; after the winner commits,
read the terminal row in a new statement and compare request hash and authenticated
User. If the winner rolls back, another caller can claim. Hash a canonical logical
request including command type/target/business payload; exclude auth tokens, trace
IDs and incidental retry transport data. Do not exclude meaningful preconditions.

In the winner's same transaction: validate domain preconditions, write business
projection/history and required outbox rows, then write terminal response and
processed_at. Only commit a terminal row. PROCESSING must never be intentionally
committed separately as a durable lease; otherwise crash recovery would need a
different approved algorithm. SQL checks express timestamp/status consistency;
the transaction boundary and eventual integration tests must verify terminal-only
commit and exactly one effect. Do not pretend a row CHECK enforces commit timing.

Same authenticated User/key/logical request returns the saved logical result,
subject to current authorization and safe response scope; a different principal
cannot obtain another User's replay data. Different logical payload yields the
accepted 409 IDEMPOTENCY_KEY_REUSED. Stable domain rejection may commit REJECTED
without any domain mutation (use a clean transaction/savepoint boundary). Transient
DB/network/server failures roll back the claim and remain retryable, not REJECTED.
Bounded lock timeout must remain a retryable infrastructure response under the
accepted 408/429/5xx policy, not an invented permanent 409 conflict or a 202 API.

The accepted algorithm uses insert/unique-index arbitration, never a process-local
mutex. Keep semantic physical-command preconditions
from ADR-012; this does not silently make mobile expectedVersion strict.

### M. actor_type versus source_type

actor_type is the responsible principal/authority of the action:
HUMAN_USER | SYSTEM | INTEGRATION.

source_type is the causal mechanism/origin of the event:
USER_ACTION | MOBILE_AUTOMATION | SYSTEM_PROCESS | EXTERNAL_INTEGRATION.

The distinct source values explicitly satisfy NFR-041. They are not roles or
authorization claims and do not replace actor_type.

| Example | actor_type | source_type | Additional provenance |
|---|---|---|---|
| Manual Web/Mobile action | HUMAN_USER | USER_ACTION | Optional WEB/MOBILE channel |
| Driver-confirmed geofence suggestion | HUMAN_USER | MOBILE_AUTOMATION | arrivalSource = GEOFENCE_CONFIRMED |
| Scheduled detector | SYSTEM | SYSTEM_PROCESS | Detector/job reference when applicable |
| External integration | INTEGRATION | EXTERNAL_INTEGRATION | Integration reference when applicable |

The API's existing arrival source MANUAL/GEOFENCE_CONFIRMED is retained and
recorded as arrivalSource event metadata; it is distinct from source_type.
A geofence suggestion alone never produces Arrival. Confirmation remains a
Driver command. WEB/MOBILE may be diagnostic channel/origin metadata but never
substitute for source_type. No continuous GPS or unconfirmed arrival is introduced.

Use this source_type set consistently in trailer_movement_events and documents.
Add actor_type to movement events and source_type to audit_events/trip_stop_events
so event/audit provenance can be recorded without guessing. Retain documents'
uploaded_by_user_id for human uploads; non-human evidence never gets a fabricated
human uploader. Where an actor is recorded, use the actor_type set above and its
actual principal. HUMAN_USER requires the corresponding User reference;
SYSTEM/INTEGRATION can leave human references NULL. Audit membership follows E.
The server derives the principal and validates provenance; client source fields
cannot grant permission. These are schema/provenance decisions, not endpoint or
request-enum changes.

### N. Master-data and ad-hoc Stop invariants

Apply to trip_stops and the equivalent order_stops shape. Accepted SQL invariants:

```text
operational_locations: UNIQUE (tenant_id, site_id, id)
order_stops / trip_stops:
  FK (tenant_id, site_id) -> physical_sites (tenant_id, id)
  FK (tenant_id, site_id, location_id) -> operational_locations (tenant_id, site_id, id)
  CHECK location_id IS NULL OR site_id IS NOT NULL
  CHECK site_id IS NOT NULL OR site_snapshot IS NOT NULL
  CHECK site_id IS NOT NULL OR location_id IS NULL
```

Optional location uses MATCH SIMPLE together with the explicit Site-required
CHECK, so NULL cannot bypass a supplied location's parent Site. A normalized
Stop has site_id and optional location_id; a snapshot may also coexist as historical
frozen master-data context. An ad-hoc Stop has no Site/location references and a
validated nonempty site_snapshot. Do not require XOR between site_id and snapshot:
v1.0 explicitly preserves historical snapshots alongside changing master data.

Application validation defines required snapshot address/name/instruction shape,
valid timezone/coordinates when present, business suitability and authorization.
An empty JSON object is not a meaningful destination. Snapshot content is frozen
as the command's historical destination context; later master-data edits do not
silently overwrite it. A client cannot bypass scope by putting another Customer's
known dock into a free-text snapshot. Validate associations and resource access
server-side, not by string-equality of addresses.

For shared Sites, a customer_id on operational_locations is optional context,
not exclusive ownership of the physical address. Verify the matching
customer_sites association at location creation/use under the same transaction,
along with each linked Order's Customer scope. Do not impose a permanent FK from
historical Stops to a mutable customer_sites association: the Accepted API allows
safe association removal while preserving history. If an association is still in
active use, removal needs transactional application checks/locking, not cascade.

A multi-Customer Trip may use a neutral location when authorized. A Customer-specific
location must be compatible with the Orders actually serviced there; aggregate
Trip membership or Trailer designation alone is not authorization. FK C ensures
requested Stop belongs to the linked Order. These dynamic capability/customer
rules belong in application validation; tenant/Site/parent identity belongs in SQL.

An ad-hoc Order/Trip Stop may exist and execute with its frozen snapshot. Before
any Trailer state transition requiring an OperationalLocation, especially Drop
or ending in AT_LOCATION, Transport Operations must select, normalize or create
a real authorized Physical Site/Operational Location. Validate its Tenant,
Customer compatibility and operational suitability before the physical command.
Do not relax Trailer invariants, create a fake LOCATION_UNKNOWN location, or
implicitly create a Location without validation. Arrival/service with a snapshot
does not authorize a location-less physical Trailer projection. This rule is
Accepted; the corresponding UX/API detail belongs to the bounded Orders/Trips/
Trailer implementation Issues and creates no unresolved schema gate.

### O. User registration identity across ledger, installation and FCM

Under B, user_devices.id is the UUID of a global User device registration.
An installation may have successive registrations for different authenticated
Users; each registration's user_id is immutable. The UUID is not a permanent
cross-account installation identity, Tenant membership, FCM token, local command
row ID, hardware identifier or Firebase UID.

processed_client_operations.device_id uuid NULL identifies that registration:

```text
user_devices: UNIQUE (id, user_id)
processed_client_operations:
  FK (user_id) -> users (id)
  FK (device_id, user_id) -> user_devices (id, user_id)
```

Default MATCH SIMPLE permits absent device provenance. A supplied device_id must
belong to the same User who executed the command. Preserve ledger tenant_id/user_id
and registration rows with non-destructive FKs; disabling a registration does not
erase historical provenance. Check current authenticated ownership at submission,
never rebind a historical row to a different User. Rotation for the same
registration retains its UUID; account switch creates a new row and isolates
cached queues/media under the accepted local model.

POST /devices and DELETE /devices/{id} are authenticated global endpoints,
not Tenant paths. Notification authorization remains Tenant-scoped: resolve an
authorized recipient in the Tenant, obtain that User's active registrations,
then send only the permitted Tenant payload. Global deactivation cuts push for
all that User's Tenants on the registration, as accepted in B.

Mobile transport of optional deviceId (registration receipt/input and command
provenance) is deferred to registration/offline implementation. The current API
example has platform/fcmToken/deviceName; this amendment adds no required field,
header or installation identifier to it. Requests without device provenance keep
NULL device_id. Tenant + clientOperationId still provides full idempotency.
Device ID is provenance only, never authorization or an idempotency key.

### P. OQ-058 resolved, no location redesign

The existing OQ-058 resolution is correct: one Physical Site may serve several
Customers through customer_sites, with customer-aware Operational Locations.
Accepted identity/master-data API explicitly creates these associations and
locations. Keep RESOLVED and add direct references; do not duplicate Sites per
Customer, force one Customer per address, or reopen this question. N strengthens
consistency around this model, not its cardinality.

### Q. Durable outbox/jobs

ADR-022 already accepts durable PostgreSQL work. Adopt its one-table model,
without new event-bus/scheduler frameworks. Accepted technical table design:

```text
outbox_jobs
id                 uuid PK
tenant_id          uuid FK tenants NULL -- required for Tenant-owned work
job_type           varchar NOT NULL
aggregate_type     varchar NULL
aggregate_id       uuid NULL
payload            jsonb NOT NULL
status             varchar NOT NULL -- PENDING / PROCESSING / SUCCEEDED / DEAD
available_at       timestamptz NOT NULL
attempt_count      integer NOT NULL DEFAULT 0
max_attempts       integer NOT NULL
locked_by          uuid NULL -- claim/lease token, not a human User FK
locked_until       timestamptz NULL
last_error         text NULL
created_at         timestamptz NOT NULL
updated_at         timestamptz NOT NULL
processed_at       timestamptz NULL -- terminal success/dead time
```

SQL row checks: attempt_count >= 0, max_attempts > 0; aggregate_type/id are
both present or both absent; PROCESSING requires locked_by/locked_until and
nonterminal states have processed_at NULL; PENDING has no lease; SUCCEEDED/DEAD
have processed_at and no lease. The lifecycle is PENDING -> PROCESSING ->
SUCCEEDED, or retry -> PENDING with future available_at, or exhausted/permanent
failure -> DEAD. Claim increments attempts. Lease recovery must also honor the
attempt limit so crashed jobs cannot loop forever. No universal retry counts,
delays or business job-type enum are invented; #55 owns bounded policy/implementation.
DEAD work must surface in technical diagnostics/alerting rather than disappear;
retry/dead transitions preserve the latest sanitized error and attempt metadata.

Suggested B-tree indexes:

```text
(tenant_id, available_at, id) WHERE status = 'PENDING' AND tenant_id IS NOT NULL
(tenant_id, locked_until, id) WHERE status = 'PROCESSING' AND tenant_id IS NOT NULL
(tenant_id, status, created_at) -- monitoring/dead work
(available_at, id) WHERE status = 'PENDING' AND tenant_id IS NULL
(locked_until, id) WHERE status = 'PROCESSING' AND tenant_id IS NULL
```

Indexes filter stable state, never now(). Worker queries filter available_at <=
transaction time and establish TenantContext before selecting a bounded ordered
batch FOR UPDATE SKIP LOCKED. Update lease/status/attempts in the claim transaction,
then commit before external network work. Unlike L, this committed PROCESSING
state is intentional and recoverable. Success/retry/dead updates must match the
claim token, preventing an expired worker from overwriting a newer claim. Recovery
uses locking and fenced updates; external side effects remain at-least-once,
not exactly-once. Handler/external operation identifiers must tolerate duplicates.

Persist domain state and durable side-effect intent in the same transaction.
payload is a minimal versioned JSON object containing references and immutable
facts needed for the job, not arbitrary unvalidated input, binary evidence,
credentials, Firebase tokens or complete sensitive API bodies. aggregate references
are technical routing context, not a substitute for core FKs; handlers validate
Tenant/resource existence and scope before acting. Re-resolve token/authorization
for push, and recheck object/reference state before cleanup.
The payload's version discriminator and job-specific shape must be validated by
the producer/handler; last_error is sanitized too, never raw tokens or signed URLs.

Tenant-owned rows have non-null tenant_id; future closed job types must explicitly
declare whether global context is legal and enforce that mapping at write time.
No implicit rule permits a NULL-Tenant job to access all tenants. Under ADR-013,
Tenant workers claim/process only their Tenant's rows without BYPASSRLS; RLS
USING/WITH CHECK must cover tenant-owned jobs. NULL-Tenant maintenance follows a
separate controlled administrative path with distinct access/policy; deny runtime
access to those rows by default. Never implement `tenant_id IS NULL OR ...` as a
blanket permissive tenant policy. Lease design, global access and retry storage
are Accepted. Numeric retry/backoff and job-type policy are explicitly deferred
to #55; no worker/policy is implemented.

## Contradictions and gaps surfaced

1. v1.0 section 19 calls RLS a candidate needing a separate ADR; Accepted ADR-013
   has already made the decision. Accepted clarification: RLS is approved for
   tenant-owned production tables, implemented/tested later in #26; this document
   does not activate it. Global tables need separate User/access-pattern policies.
2. ADR-022 accepts durable outbox but its follow-up says add schema during scaffold;
   v1.0 omits it and #55 owns worker implementation. Q accepts the schema design now;
   no retroactive scaffold migration or worker is silently added.
3. Authorization baseline lists suggested INVITED membership while v1.0 separates
   invitation state. Preserve the dedicated invitation table; do not add INVITED
   or silently rewrite that older suggestion. Owner approval confirms this
   already-accepted schema/API interpretation.
4. Capability document remains Draft/TBD; conceptual domain still includes candidate
   UNKNOWN locations and older Trip/Stop states. Accepted state machines/schema
   define the persisted lifecycle, including no fake LOCATION_UNKNOWN location row
   and Fuel as an event. This amendment grants no unresolved permissions or new states.
5. The alerts/audit document has a Tenant base heading and mentions User/Tenant
   membership context, but its devices section and the endpoint catalog list
   authenticated POST /devices and DELETE /devices/{id}. The owner explicitly
   confirms these are global endpoints. B/O supersede the ambiguous device-ownership
   wording with global immutable User registrations and global deactivation;
   Tenant scoping applies to notification authorization, not the device path.
6. v1.0's nullable uniqueness, missing Yard pending representation, unspecified
   manifest status and terminal-only idempotency description leave concrete gaps;
   A/C/D/H/L resolve them under explicit owner approval recorded above.
7. Mobile Hook carries expectedTrailerVersion while ADR-012 favors semantic
   preconditions; the accepted offline protocol already flags this for finalization.
   Preserve the field and record the existing review obligation, not a contract edit.
8. N resolves ad-hoc destinations versus mandatory Trailer AT_LOCATION/Drop location
   references: normalize/select a real authorized Site/Operational Location before
   physical transitions requiring it; no invented projection or fake location.
9. v1.0 section 1.7's coarse provenance values do not satisfy NFR-041 if reused
   as source_type. M explicitly separates three principal types from four causal
   source types, retaining confirmed geofence Arrival semantics.

These discrepancies are documented and resolved by this accepted amendment or
the existing higher-priority ADRs. No remaining contradiction blocks #22 after
merge of #75. The existing physical-command version follow-up in item 7 belongs
to later Mobile command implementation, not this identity migration gate.

## Compatibility review

| Boundary | Accepted compatibility and implementation impact |
|---|---|
| Identity/master data | A/E preserve role DTOs, multi-role membership and historical User identity. I changes duplicate-equivalence validation, not original display numbers. F adds no unsupported fields. J leaves invitation acceptance unimplemented. |
| Devices/push | B/O use immutable global User registrations and global authenticated device endpoints. Token transfer is atomic; optional deviceId transport is deferred without making it mandatory. |
| Orders/dispatch | G follows exact accepted transitions; C preserves multiple requested Stops for the same Order/visit and removes NULL duplicates. H accepts OPEN/CLOSED schema status but adds no manifest API. |
| Yard/local model | D preserves snapshot immutability and scan enums; unobserved result is nullable in API/local cache. P retains shared-address structure. |
| Mobile/idempotency | L keeps Idempotency-Key UUID and terminal replay semantics; PROCESSING is internal only. Media finalization and dependency sequencing remain unchanged. O device UUID is optional provenance, not command UUID. |
| Tenant/Customer scope | Composite FKs enforce tenant/Site/Order consistency; current membership/capability/customer checks remain mandatory. A/E/N do not authorize from client IDs. |
| State machines | No Order/Trip/Stop/Shift/Trailer/Yard lifecycle is redefined. H accepts only minimal manifest allocation-period OPEN/CLOSED; M geofence arrival still requires confirmation. N preserves physical Trailer location invariants. |
| Future RLS | ADR-013 governs tenant rows including ledger/outbox; global users/devices and NULL-Tenant jobs need separate controlled policy/access design. No RLS activation here. |
| Durable work | Q follows ADR-022 atomic outbox and at-least-once delivery; L transaction-local claim and Q durable lease have deliberately different crash semantics. |

## Deferred implementation decisions

The owner has explicitly approved A–Q, including the following bounded
deferments. No schema approval remains pending and none of these blocks #22.

| Item | Deferred detail / owner Issue |
|---|---|
| F | Structured trailer ownership/size excluded from v1.0.1; #29 may reopen only against an approved operational requirement. |
| H | Exact manifest authority, closure trigger and edit/partial unload/return/cancelled-remainder policy before commands in #32; implementation review of optimistic version and/or closed_at, neither added here. |
| Q | Numeric bounded retry/backoff settings and job-type policy in #55. |
| O | Optional deviceId registration/offline provenance UX/API transport in the later Mobile/offline implementation Issue; no mandatory transport added here. |

## Documentation validation and later implementation evidence

This PR must pass git diff --check, internal-link/reference review, documentation-only
path checks and all four existing CI jobs (Backend, Web, Mobile, OpenAPI). Compare
the Accepted v1.0 file and technical V1 against main to prove they are unchanged.
No executable SQL, policy, worker, migration, entity or repository is delivered.

After merge of #75, later bounded implementation issues should demonstrate with real
PostgreSQL tests: role/invitation NULL duplicate rejection; normalized collisions;
global device/token rotation and account isolation; cross-Tenant/Site/Order FK
rejection; Yard unobserved versus completed results; distinct Order milestones;
audit membership consistency; two simultaneous idempotency requests, rollback and
lost-response retry; and outbox claim/lease-expiry/crash/duplicate-effect safety.
Those are future verification obligations, not tests or code added by this PR.

PostgreSQL technical references used to check this amendment:
[unique NULL behavior](https://www.postgresql.org/docs/18/indexes-unique.html),
[constraints and composite FKs](https://www.postgresql.org/docs/18/ddl-constraints.html),
[locking and SKIP LOCKED](https://www.postgresql.org/docs/18/sql-select.html).
