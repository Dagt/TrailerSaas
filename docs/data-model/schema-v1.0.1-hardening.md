# Relational schema v1.0.1 hardening candidate

Status: Proposed v1.0.1 hardening amendment

**Date:** 2026-10-05  
**Issue:** [#74](https://github.com/Dagt/TrailerSaas/issues/74)  
**Tracking:** [#66](https://github.com/Dagt/TrailerSaas/issues/66)  
**Baseline:** [Accepted schema v1.0](schema-v1.0.md)  
**Database:** PostgreSQL 18; PostgreSQL 17 remains the temporary fallback allowed by ADR-003.

This is a documentation-only amendment, not a replacement Accepted schema, DDL,
migration or implementation instruction. Read v1.0 plus the proposed deltas below
as the v1.0.1 candidate; all unmentioned columns, relationships and invariants
remain unchanged. Accepted v1.0 remains authoritative until explicit review and
approval. A merge of a proposal alone must not be interpreted as acceptance.
The existing technical V1 migration is outside this amendment and remains intact.
Do not start #22 or freeze identity/domain migrations from unresolved proposals.

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
  its matrix Draft and includes TBD permissions; no new role mapping is accepted here.
- Contracts: [conventions](../api/conventions.md),
  [identity/master data](../api/identity-master-data.md),
  [mobile execution](../api/mobile-execution.md),
  [orders/dispatch](../api/orders-dispatch.md), [yard](../api/yard.md),
  [alerts/devices/audit](../api/alerts-audit.md).
- Offline: [sync protocol](../mobile/offline-sync-protocol.md) and
  [local model](../mobile/local-offline-data-model.md).
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
Recommended amendment means a concrete option still requiring human acceptance.
Still open means the available sources do not support a complete decision.
Even mechanical deltas remain proposals until this amendment is approved.

| Item | Classification | Proposed change and reason |
|---|---|---|
| A | Mechanical hardening | Partial role uniqueness indexes cover nullable Customer scope; mirror for intended invitation roles. |
| B | Recommended amendment | Global User-owned installation registry; Tenant context belongs to notification authorization, not device identity. |
| C | Recommended amendment | Unique executable Stop/Order pair; optional requested Stop reference does not define identity. Confirm cardinality before removing the fourth unique-key column. |
| D | Recommended amendment | Nullable result before observation; retain the four accepted result values and assign MISSING only on completion. |
| E | Recommended amendment | Nullable audit actor membership with a tenant/user-consistent historical FK. |
| F | Still open | Preserve supported type/capability metadata; do not invent structured ownership or size categories. |
| G | Mechanical hardening | Separate delivered_at, completed_at and cancelled_at milestone timestamps from accepted Order transitions. |
| H | Recommended amendment | Propose minimal OPEN/CLOSED manifest lifecycle for review; no approved manifest state machine exists. |
| I | Recommended amendment | Generated normalized equipment number and Tenant-scoped uniqueness; original display value retained. |
| J | Recommended amendment | Normalized invitation email, one PENDING invitation per Tenant/email, authenticated verified-email matching. |
| K | Mechanical hardening | Parent-first FK dependency order; no reverse mutable coupling pointer; add optional event FKs after both endpoints exist. |
| L | Recommended amendment | Transaction-local PROCESSING claim plus unique key; business effect and terminal replay result commit atomically. |
| M | Recommended amendment | Separate responsible actor from causal source; keep coarse accepted values and explicit mobile arrival provenance. |
| N | Recommended amendment | SQL location/Site/Order consistency plus application snapshot and Customer-scope validation. |
| O | Recommended amendment | device_id references the global installation UUID, never an FCM token or hardware identifier; registration transport needs explicit approval. |
| P | Mechanical hardening | Confirm OQ-058 resolved; link its existing resolution to Accepted schema/API and this proposal. |
| Q | Recommended amendment | Adopt ADR-022 outbox_jobs with bounded retries, lease recovery and Tenant-safe claiming. |

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

Apply the same two indexes and scope checks to invitation_role_assignments,
substituting invitation_id for membership_id. This prevents duplication when
the intended set is materialized on acceptance. PostgreSQL NULLS NOT DISTINCT
is an equivalent supported alternative, but partial indexes state the two
approved scope cases directly. Application validation complements SQL, never
replaces it. No schema objects are created by this document.

### B. Global user_devices, Tenant-scoped notifications

ADR-011 makes User global and permits multiple memberships. Recommend removing
tenant_id from user_devices rather than duplicating one installation for every
Tenant. Proposed replacement shape (subject to B/O approval):

```text
id                 uuid PK -- the one technical installation/device identity
user_id            uuid FK users NOT NULL -- current authenticated owner binding
platform           varchar NOT NULL -- IOS / ANDROID; WEB only if separately required
fcm_token          varchar NULL -- mutable transport registration, not identity
active             boolean NOT NULL
last_seen_at       timestamptz NOT NULL
created_at         timestamptz NOT NULL
updated_at         timestamptz NOT NULL

UNIQUE (fcm_token) WHERE fcm_token IS NOT NULL
INDEX (user_id, active)
```

A global installation can exist before push permission/token registration;
NULL token therefore does not invalidate offline attribution. Rotation updates
the token on the same UUID. active refers to a usable owner registration, not
proof of current Tenant membership. Logout deactivates and clears the token;
rebind on an account switch only after authenticating the new owner and verifying
the installation binding. Preserve the UUID for the same installation; reinstall
or reset creates a new UUID. Historical ledger user_id remains immutable and is
not inferred from the current registry owner. Rebinding policy/transport is an
approval decision, not a new accepted security protocol.

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

### C. Executable Stop/Order identity

Recommend replacing the current four-column UNIQUE with:

```text
trip_stop_orders:
  UNIQUE (tenant_id, trip_stop_id, order_id)
  FK (tenant_id, trip_stop_id) -> trip_stops (tenant_id, id)
  FK (tenant_id, order_id) -> orders (tenant_id, id)
order_stops:
  UNIQUE (tenant_id, order_id, id)
trip_stop_orders:
  FK (tenant_id, order_id, order_stop_id) -> order_stops (tenant_id, order_id, id)
```

order_stop_id stays nullable using default MATCH SIMPLE: a missing optional
reference is legal, but a supplied reference must belong to that exact Order,
not just the Tenant. This blocks duplicate associations with NULL or different
requested Stop IDs. Orders-dispatch exposes orderIds per executable Stop and
does not define multiple requested-Stop mappings for the same Order at one visit.
Approval must confirm that one optional requested Stop is sufficient. If one
visit must consolidate several requested Stops for one Order, that cardinality
is Still open and requires an explicit design decision; do not silently discard
the extra reference or create a second business association.

### D. Yard Check pending observation

Recommend changing only result nullability to varchar NULL. No new PENDING
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
IN_PROGRESS is not yet a missing finding. API/local cached results need a defined
nullable representation for snapshot reads, which is part of explicit D approval;
no accepted scan-response enum changes here.

### E. Historical authorization context for audit

Recommend adding actor_membership_id uuid NULL to audit_events. Retain the global
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
The user's third-party case must remain a proposal: the reviewed accepted
requirements do not specify its taxonomy, permissions or relationship to control.
Do not add OWNED/LEASED/THIRD_PARTY or a boolean owner shortcut by assumption.
Do not add VIN, plates, axle counts, heights, payload ratings or lease accounting.

Still open: whether structured size is needed beyond trailer_type; if so, the
dimension/unit/nullability and mapping to existing API must be approved. Ownership
also needs separate definitions of legal ownership versus operational control,
allowed values and scope. These unresolved fields are excluded from the candidate
column set; F cannot be called resolved by inventing a classification.

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

### H. Load manifest lifecycle gap

v1.0 requires load_manifests.status but declares no values or transitions. The
conceptual manifest, WF-008/011 and #32 allocation intent establish grouping,
Trailer association and history, but not a complete lifecycle.

Recommended amendment for explicit approval only: OPEN means an operational
allocation period is still in use; CLOSED means its allocation period has ended
and it is retained as history. Proposed transitions are create -> OPEN and
explicit validated closure -> CLOSED, without reopening. These are not synonyms
for Trailer LOADED/EMPTY, Order DELIVERED/COMPLETED or Trip COMPLETED. No automatic
closure on Hook, Drop, departure or Off Duty is assumed.

Still open within H: exact closure trigger/authority and handling of partial
unloading, returns, cancelled remainder and allocation edits once execution starts.
Until those are approved, do not create a status CHECK or introduce new manifest
commands. No new timestamps/closed state semantics are Accepted here. Removing
the field rather than defining it would also require explicit review.

### I. Human equipment identifier normalization

Keep operational_number unchanged for display. Recommend a STORED generated
operational_number_normalized column on tractors and trailers with Tenant-scoped
UNIQUE (tenant_id, operational_number_normalized), replacing raw-number identity
uniqueness as the authoritative duplicate barrier. Proposed canonicalization:

1. Trim leading/trailing ASCII whitespace, including tabs/newlines.
2. Collapse internal ASCII whitespace runs to one ordinary space.
3. Fold ASCII a-z to A-Z with a deterministic immutable expression/collation.
4. Require a nonempty result. Retain punctuation, leading zeros and internal
   separators; do not strip all spaces or rewrite BH T# as BHT#.

Examples: ` t114 ` and `T114` collide; `bh  t114` and `BH T114` collide;
`T-114`, `T114` and `T0114` remain distinct. A generated expression plus index
prevents application bypass/drift. PostgreSQL expression functions must be immutable
and equality deterministic; document the exact expression/collation in the later
approved migration review. No new extension is needed.

The equivalence policy needs human approval because Accepted tenant uniqueness
does not specify case, separators or non-ASCII rules. Unicode equivalences and
punctuation removal remain Still open, not default assumptions. Normalize search
input using the same rule; UUID/QR identity and the displayed value remain unchanged.

### J. Invitation email, active uniqueness and linking

Recommend adding email_normalized varchar NOT NULL generated from email using
a documented trim/case-fold rule, retaining email for display. Do not remove dots,
plus suffixes or apply provider-specific aliases. Local-part case and international
email normalization policy need explicit review rather than assuming all identities
are interchangeable.

```text
membership_invitations:
  UNIQUE (tenant_id, email_normalized) WHERE status = 'PENDING'
  UNIQUE (token_hash)
  CHECK email_normalized is nonempty
```

Define active for this index as PENDING, not expires_at > now(). Time-dependent
predicates are unsuitable for immutable index membership. On re-invitation,
transactionally mark stale PENDING invitations EXPIRED or explicitly revoke/replace
them before inserting another; retain history and their intended role sets. One
pending invitation carries multiple roles/Customer scopes (A), not one invitation
per role. Accepted/revoked/expired invitations may coexist historically.

Acceptance in #25 must validate token hash, status, expiry, and authenticated
Firebase UID/email through server-validated identity. Recommend requiring the
authenticated email to be verified and to match the normalized invited email
before binding accepted_by_user_id, membership and roles. Client email or a token
alone is insufficient; mismatch/unverified/missing email never silently links an
account. Consume atomically under row lock; UNIQUE (tenant_id, user_id) continues
to protect membership creation and retries must not duplicate roles.

ADR-007 leaves email-verification policy open and the API leaves acceptance UX
unfinalized: verified-email requirement and pending-invitation policy are explicit
amendments for approval, not implementation of Firebase or onboarding.

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
4. Drivers/tractors/trailers and QR identities; proposed global user_devices after users.
5. Orders/order_stops/cargo; manifests and allocations after their parents, with H unresolved.
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

Recommend an uncommitted PROCESSING row as a transaction-local key claim:

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

An equivalent transaction advisory-lock + terminal-only insert can also serialize
keys, but must be separately reviewed; the recommended candidate uses unique-index
claiming, not process-local mutexes. Keep semantic physical-command preconditions
from ADR-012; this does not silently make mobile expectedVersion strict.

### M. actor_type versus source_type

Recommend consistently documenting actor_type as the responsible principal:
HUMAN_USER, SYSTEM or INTEGRATION. source_type records the causal origin of the
fact/evidence with the same coarse existing values where available; it is not an
authorization role or a fourth principal called MOBILE. The same value set does
not make the two fields interchangeable.

Examples: a human confirmation has actor HUMAN_USER/source HUMAN_USER; a system
projection derived from a human command may have actor SYSTEM/source HUMAN_USER
with causal command context; a scheduled detector has SYSTEM/SYSTEM. Future
integration provenance is INTEGRATION, without a fake human membership.

Preserve API arrival source MANUAL or GEOFENCE_CONFIRMED in structured event
metadata (arrivalSource); use an origin field WEB or MOBILE when known. A geofence
suggestion is not a completed arrival: confirmation remains a human command,
even if a mobile component automated the suggestion. These origin metadata keys
are recommended names for approval, not new accepted API enum values. No continuous
GPS or unconfirmed automatic arrival is introduced.

Retain documents.uploaded_by_user_id for human uploads and source_type for coarse
provenance. Retain movement source_type; recommend adding actor_type there and
source_type to audit_events/trip_stop_events so API audit source can be populated
without guessing. Each human-attributed row needs its User reference; non-human
records may leave it NULL. Audit membership follows E. Source/client fields cannot
grant permissions: the server validates provenance and derives the principal.
Review these column/metadata semantics explicitly; v1.0 defines different subsets
on different event tables and its audit API already expects source type.

### N. Master-data and ad-hoc Stop invariants

Apply to trip_stops and the equivalent order_stops shape. Recommended SQL:

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
not exclusive ownership of the physical address. Recommend verifying the matching
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

Potential contract limitation already present: ad-hoc Trip destinations can be
represented, but Trailer AT_LOCATION requires a real Operational Location and Drop
requires locationId. Do not create a fake location or relax that accepted invariant.
Arrival/service at an ad-hoc Stop and eventual physical Trailer projection must
be reconciled explicitly if no authorized reusable location exists. Whether to
normalize such a destination before those physical commands remains Still open
for N approval; this document adds no alternate location model or API shortcut.

### O. One device identity across ledger, installation and FCM

Under B, user_devices.id is the stable UUID of an app installation, not its
Tenant membership, push token, local command row ID, OS advertising/hardware ID,
or Firebase UID. processed_client_operations.device_id uuid NULL becomes a FK
to that global id. Keep the ledger's tenant_id/user_id as immutable command
attribution. Check ownership against the authenticated User when a device claim
is present; after account rebinding never infer old actor identity from the current
registry user_id. A composite FK tying historical user_id to the mutable current
binding would prevent safe rebinding, so it is deliberately not proposed.

Recommend an installation-generated UUID registered/validated by the server before
it is used for offline provenance. The optional registration input/receipt and
command transport carrying it require a reviewed API amendment: the accepted
POST /devices example has platform/fcmToken/deviceName but no installation UUID,
and the accepted local model has command/media UUIDs, not an installation registry.
No required new field/header or token-as-ID fallback is silently introduced.
Legacy requests lacking a device claim continue to have NULL device_id; idempotency
is still fully enforced by Tenant + clientOperationId.

Keep the tenant path on existing registration/deactivation APIs until separately
approved; interpret it as current authorization context, never physical device
ownership. Approve semantics that deactivation affects the User's global registration,
including other Tenant notifications, or choose an explicit different routing
design. Do not silently change DELETE behavior to per-Tenant subscription removal.
FCM rotation keeps the installation UUID; account switch revokes the old binding
and isolates cached queues/media per the accepted local model. Device identity
is provenance, never an authorization credential or a new idempotency key.

### P. OQ-058 resolved, no location redesign

The existing OQ-058 resolution is correct: one Physical Site may serve several
Customers through customer_sites, with customer-aware Operational Locations.
Accepted identity/master-data API explicitly creates these associations and
locations. Keep RESOLVED and add direct references; do not duplicate Sites per
Customer, force one Customer per address, or reopen this question. N strengthens
consistency around this model, not its cardinality.

### Q. Durable outbox/jobs

ADR-022 already accepts durable PostgreSQL work. Recommend its one-table model,
not separate new event-bus/scheduler frameworks. Candidate technical table:

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
are technical recommendations requiring approval; no worker/policy is implemented.

## Contradictions and gaps surfaced

1. v1.0 section 19 calls RLS a candidate needing a separate ADR; Accepted ADR-013
   has already made the decision. Candidate clarification: RLS is approved for
   tenant-owned production tables, implemented/tested later in #26; this document
   does not activate it. Global tables need separate User/access-pattern policies.
2. ADR-022 accepts durable outbox but its follow-up says add schema during scaffold;
   v1.0 omits it and #55 owns worker implementation. Q proposes the schema now;
   no retroactive scaffold migration or worker is silently added.
3. Authorization baseline lists suggested INVITED membership while v1.0 separates
   invitation state. Preserve the dedicated invitation table; do not add INVITED
   or silently rewrite that older suggestion. Explicit review should confirm this
   already-accepted schema/API interpretation.
4. Capability document remains Draft/TBD; conceptual domain still includes candidate
   UNKNOWN locations and older Trip/Stop states. Accepted state machines/schema
   define the persisted lifecycle, including no fake LOCATION_UNKNOWN location row
   and Fuel as an event. This amendment grants no unresolved permissions or new states.
5. Device registration says User/Tenant membership context, while a User may have
   many Tenants. B/O expose the global-registry interpretation and contract effects
   for approval; they do not assert that tenant-path DELETE already has global scope.
6. v1.0's nullable uniqueness, missing Yard pending representation, unspecified
   manifest status and terminal-only idempotency description leave concrete gaps;
   A/C/D/H/L make them reviewable, not silently Accepted.
7. Mobile Hook carries expectedTrailerVersion while ADR-012 favors semantic
   preconditions; the accepted offline protocol already flags this for finalization.
   Preserve the field and record the existing review obligation, not a contract edit.
8. Ad-hoc destinations versus mandatory Trailer AT_LOCATION/Drop location references
   need the N decision before implementing that combination; no invented projection.

## Compatibility review

| Boundary | Candidate compatibility and explicit review impact |
|---|---|
| Identity/master data | A/E preserve role DTOs, multi-role membership and historical User identity. I changes duplicate-equivalence validation, not original display numbers. F adds no unsupported fields. J leaves invitation acceptance unimplemented. |
| Devices/push | B/O require approval of global registration and deactivation semantics plus optional installation ID transport. Existing tenant paths and fcmToken stay unchanged in this PR. |
| Orders/dispatch | G follows exact accepted transitions; C assumes one Order association per executable Stop, requiring cardinality confirmation. H is not an approved new manifest API. |
| Yard/local model | D preserves snapshot immutability and scan enums; nullable unobserved results need documented DTO/cache handling on approval. P retains shared-address structure. |
| Mobile/idempotency | L keeps Idempotency-Key UUID and terminal replay semantics; PROCESSING is internal only. Media finalization and dependency sequencing remain unchanged. O device UUID is optional provenance, not command UUID. |
| Tenant/Customer scope | Composite FKs enforce tenant/Site/Order consistency; current membership/capability/customer checks remain mandatory. A/E/N do not authorize from client IDs. |
| State machines | No Order/Trip/Stop/Shift/Trailer/Yard lifecycle is redefined. H is explicitly an unapproved manifest lifecycle; M geofence arrival still requires confirmation. |
| Future RLS | ADR-013 governs tenant rows including ledger/outbox; global users/devices and NULL-Tenant jobs need separate controlled policy/access design. No RLS activation here. |
| Durable work | Q follows ADR-022 atomic outbox and at-least-once delivery; L transaction-local claim and Q durable lease have deliberately different crash semantics. |

## Decisions requiring explicit approval

This is the exact amendment decision register; unresolved policy details must
either be answered or explicitly excluded/deferred before the migration gate opens.

| Decision | Required human answer |
|---|---|
| B/O | Approve global User installation registry, nullable/rotating FCM token, owner rebinding, historical actor snapshots, optional installation UUID registration/command transport, and global effect of device deactivation. Agree separate global-registry authorization policy. |
| C | Approve one Order per executable Stop association and one optional requested Stop reference, or define the missing multiple-requested-Stop cardinality. |
| D | Approve NULL until observed/completed and nullable snapshot result representation in API/local cache; no new PENDING enum. |
| E | Approve nullable historical actor membership and tenant/user composite FK; preserve SYSTEM/INTEGRATION attribution. |
| F | Decide whether structured size/ownership is needed, its precise dimensions/units/categories/control meaning and API effect, or explicitly defer those fields. No unsupported enum is proposed as Accepted. |
| H | Approve/reject OPEN/CLOSED recommendation and define closure authority/trigger, partial unload/return/cancel behavior and allocation-edit rules, or explicitly defer the manifest status freeze. |
| I | Approve ASCII trim/whitespace collapse/case-fold equivalence, preserved punctuation/leading zeros, deterministic generated key; resolve/defer wider Unicode/formats. |
| J | Approve exact email normalization, one PENDING per Tenant/email, stale invitation replacement, and verified Firebase email match before binding. |
| L | Approve transaction-local PROCESSING claim, mandatory logical request hash, atomic terminal commit and retryable lock timeout behavior. |
| M | Approve actor/source distinction, coarse provenance and mobile arrival metadata mapping, and the specified event/audit column additions. |
| N | Approve SQL Site/location constraints, coexisting historical snapshots, snapshot validation and association use/removal checks; resolve/defer the ad-hoc destination/physical projection gap without relaxing Accepted Trailer invariants. |
| Q | Approve one outbox table, persisted attempt limit, lease/fencing/recovery, indexes, payload/reference strategy and isolated global-job path; numeric retry policy remains #55. |

Review also acknowledges the RLS/outbox supersession and older INVITED/candidate
wording above. Mechanical A/G/K/P do not create additional product decisions, but
are included in the overall amendment approval. No acceptance claim is made here.

## Documentation validation and later implementation evidence

This PR must pass git diff --check, internal-link/reference review, documentation-only
path checks and all four existing CI jobs (Backend, Web, Mobile, OpenAPI). Compare
the Accepted v1.0 file and technical V1 against main to prove they are unchanged.
No executable SQL, policy, worker, migration, entity or repository is delivered.

After approval, later bounded implementation issues should demonstrate with real
PostgreSQL tests: role/invitation NULL duplicate rejection; normalized collisions;
global device/token rotation and account isolation; cross-Tenant/Site/Order FK
rejection; Yard unobserved versus completed results; distinct Order milestones;
audit membership consistency; two simultaneous idempotency requests, rollback and
lost-response retry; and outbox claim/lease-expiry/crash/duplicate-effect safety.
Those are future verification obligations, not tests or code added by this PR.

PostgreSQL technical references used to check this proposal:
[unique NULL behavior](https://www.postgresql.org/docs/18/indexes-unique.html),
[constraints and composite FKs](https://www.postgresql.org/docs/18/ddl-constraints.html),
[locking and SKIP LOCKED](https://www.postgresql.org/docs/18/sql-select.html).
