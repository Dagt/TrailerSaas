# Permission Capabilities v0.1

**Status:** Draft baseline  
**Related:** ADR-011, `tenancy-and-authorization.md`

The backend should authorize **capabilities**, not scatter role-name comparisons throughout controllers/services.

Roles map to capabilities centrally. Domain-state rules are evaluated after capability checks.

## Capability catalog

### Tenant / identity

- `TENANT_SETTINGS_MANAGE`
- `USER_INVITE`
- `USER_DISABLE`
- `ROLE_ASSIGN`
- `AUDIT_VIEW`

### Customer / location master data

- `CUSTOMER_CREATE`
- `CUSTOMER_EDIT`
- `CUSTOMER_VIEW`
- `SITE_CREATE`
- `SITE_EDIT`
- `SITE_VIEW`

### Driver / tractor / trailer master data

- `DRIVER_CREATE`
- `DRIVER_VIEW`
- `DRIVER_EDIT`
- `TRACTOR_CREATE`
- `TRACTOR_VIEW`
- `TRACTOR_EDIT`
- `TRAILER_CREATE`
- `TRAILER_EDIT`
- `TRAILER_CUSTOMER_REASSIGN`
- `TRAILER_OOS_MARK`
- `TRAILER_RETURN_TO_SERVICE`

### Orders / dispatch

- `ORDER_CREATE`
- `ORDER_EDIT`
- `ORDER_CANCEL`
- `ORDER_COMPLETE`
- `TRIP_PLAN`
- `TRIP_ASSIGN_DRIVER`
- `TRIP_ASSIGN_TRACTOR`
- `TRIP_ASSIGN_TRAILER`
- `TRIP_REROUTE`
- `TRAILER_SUBSTITUTE_AUTHORIZE`

### Driver execution

- `SHIFT_START`
- `SHIFT_END`
- `PRETRIP_CREATE`
- `POSTTRIP_CREATE`
- `TRIP_EXECUTE_ASSIGNED`
- `TRAILER_HOOK`
- `TRAILER_DROP`
- `ARRIVAL_CONFIRM`
- `DEPARTURE_CONFIRM`
- `POD_UPLOAD`
- `FUEL_EVENT_CREATE`
- `EXCEPTION_REPORT`

### Yard

- `YARD_MOVE_EXECUTE`
- `YARD_CHECK_EXECUTE`
- `TRAILER_LOCATION_CORRECT`
- `TRAILER_STATE_CORRECT`

### Read capabilities

- `TRAILER_VIEW_ALL`
- `TRAILER_VIEW_CUSTOMER_SCOPED`
- `ORDER_VIEW_ALL`
- `ORDER_VIEW_CUSTOMER_SCOPED`
- `TRIP_VIEW_ALL`
- `TRIP_VIEW_ASSIGNED`
- `DOCUMENT_VIEW_ALL`
- `DOCUMENT_VIEW_CUSTOMER_SCOPED`
- `ALERT_VIEW`

Issue #56 additionally declares separate `ALERT_ACKNOWLEDGE` and `ALERT_RESOLVE`
enforcement points. Neither capability has an approved role assignment; the
conservative mapping below intentionally grants neither. Read authority does not
imply write authority. Role grants require an explicit policy approval, not a
client-provided capability, QR, notification or recipient association.

## Initial role mapping

| Capability group | Tenant Admin | Transport Operations | Customer Dispatcher | Yard Spotter | Driver |
|---|---:|---:|---:|---:|---:|
| Tenant/user management | ✓ | — | — | — | — |
| Customer/site master data | ✓ | limited | scoped/TBD | — | — |
| Driver/tractor/trailer master data | ✓ | limited | — | limited operational | — |
| Order create | ✓ | ✓ | ✓ scoped | — | — |
| Order edit/cancel | ✓ | ✓ | ✓ scoped, state-limited | — | — |
| Trip planning | ✓ | ✓ | — | — | — |
| Assign driver | ✓ | ✓ | shared design-partner behavior | — | — |
| Assign tractor | ✓ | ✓ | — | — | — |
| Assign trailer | ✓ | ✓ | ✓ scoped in design-partner workflow | — | — |
| Authorize trailer substitution | ✓ | ✓ | ✓ scoped in design-partner workflow | — | — |
| Reroute | ✓ | ✓ | ✓ scoped/shared | — | — |
| Execute assigned trip | — | — | — | — | ✓ |
| Hook/drop | override | override | — | yard-only | ✓ assigned |
| Yard move/check | view/override | view | scoped view/TBD | ✓ | — |
| Correct trailer location | ✓ | shipping/TBD | customer-scoped shipping TBD | ✓ | — |
| POD upload | view | view | scoped view | — | ✓ |
| Audit view | ✓ | scoped | own/customer scoped TBD | limited | own actions TBD |

## Important: role mapping is not enough

The same capability can still be denied because of resource scope or domain state.

Examples:

- Customer Dispatcher has `ORDER_EDIT` but only for Orders under its Customer.
- Customer Dispatcher cannot directly edit an in-transit Order if the state machine requires a reroute flow.
- Driver has `TRAILER_HOOK` but only for the active assigned Trip and cannot silently substitute another Trailer.
- Yard Spotter has `TRAILER_LOCATION_CORRECT` but only through an auditable correction workflow.

## Implementation guidance

Prefer an API/service pattern conceptually similar to:

```text
authorize(capability, tenantContext, resourceContext)
domainPolicy.validate(command, currentState)
execute(command)
```

Do not build authorization logic from frontend route visibility.

## Implemented conservative mapping (#24)

This document remains **Draft baseline**. #24 implements only the explicit grants
below; the earlier matrix's limited/TBD/scoped-TBD/override entries remain deferred
and DENY by default. These grants indicate potential capability, not final
resource/domain-state permission.

| Role | Exact implemented capability set |
| --- | --- |
| TENANT_ADMIN | TENANT_SETTINGS_MANAGE, USER_INVITE, USER_DISABLE, ROLE_ASSIGN, AUDIT_VIEW, CUSTOMER_CREATE, CUSTOMER_EDIT, SITE_CREATE, SITE_EDIT, DRIVER_CREATE, DRIVER_EDIT, TRACTOR_CREATE, TRACTOR_EDIT, TRAILER_CREATE, TRAILER_EDIT, TRAILER_CUSTOMER_REASSIGN, TRAILER_OOS_MARK, TRAILER_RETURN_TO_SERVICE, ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_COMPLETE, TRIP_PLAN, TRIP_ASSIGN_DRIVER, TRIP_ASSIGN_TRACTOR, TRIP_ASSIGN_TRAILER, TRIP_REROUTE, TRAILER_SUBSTITUTE_AUTHORIZE, TRAILER_LOCATION_CORRECT, TRAILER_STATE_CORRECT, TRAILER_VIEW_ALL, ORDER_VIEW_ALL, TRIP_VIEW_ALL, DOCUMENT_VIEW_ALL, ALERT_VIEW |
| TRANSPORT_OPERATIONS | ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_COMPLETE, TRIP_PLAN, TRIP_ASSIGN_DRIVER, TRIP_ASSIGN_TRACTOR, TRIP_ASSIGN_TRAILER, TRIP_REROUTE, TRAILER_SUBSTITUTE_AUTHORIZE, TRAILER_VIEW_ALL, ORDER_VIEW_ALL, TRIP_VIEW_ALL, DOCUMENT_VIEW_ALL, ALERT_VIEW |
| CUSTOMER_DISPATCHER | ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_COMPLETE, TRIP_ASSIGN_DRIVER, TRIP_ASSIGN_TRAILER, TRIP_REROUTE, TRAILER_SUBSTITUTE_AUTHORIZE, TRAILER_VIEW_CUSTOMER_SCOPED, ORDER_VIEW_CUSTOMER_SCOPED, DOCUMENT_VIEW_CUSTOMER_SCOPED |
| YARD_SPOTTER | YARD_MOVE_EXECUTE, YARD_CHECK_EXECUTE, TRAILER_LOCATION_CORRECT, TRAILER_STATE_CORRECT |
| DRIVER | SHIFT_START, SHIFT_END, PRETRIP_CREATE, POSTTRIP_CREATE, TRIP_EXECUTE_ASSIGNED, TRAILER_HOOK, TRAILER_DROP, ARRIVAL_CONFIRM, DEPARTURE_CONFIRM, POD_UPLOAD, FUEL_EVENT_CREATE, EXCEPTION_REPORT, TRIP_VIEW_ASSIGNED |

All other role/capability combinations deny. In particular, scoped audit visibility,
Operations/Customer master-data rights, Yard location-scope extensions, Admin Yard
override semantics and Driver broad non-assigned reads are not resolved here.
Driver hook/drop and other execution capabilities still require future resource
and domain-state checks; their mapping alone is not permission to execute any Trip.

Authorization resolves ACTIVE membership and roles freshly from PostgreSQL for
the authenticated User + requested Tenant, not from Firebase custom claims or
Spring GrantedAuthorities. Tenant checks require TENANT scope; Customer checks
accept TENANT scope or the matching CUSTOMER UUID. Assignment-scoped Trip
capabilities require the explicit server-derived assigned-work condition.
No RLS/TenantContext, Customer existence lookup, schema changes or business
endpoints are introduced by #24. See [backend integration API](../../backend/README.md#capability-and-scope-authorization-24).

## Approved master-data read mapping (#27)

The owner explicitly approved CUSTOMER_VIEW and SITE_VIEW for #27, extending
the catalog from 53 to 55. This document remains **Draft baseline**; the #24
table above is its original conservative mapping, now extended only as follows:

| Role | CUSTOMER_VIEW | SITE_VIEW |
| --- | --- | --- |
| TENANT_ADMIN | Tenant-wide | Tenant-wide |
| TRANSPORT_OPERATIONS | Tenant-wide, read-only | Tenant-wide, read-only |
| CUSTOMER_DISPATCHER | Assigned Customers only | Associated Sites / own Customer Locations |
| YARD_SPOTTER | Deny | Deny |
| DRIVER | Deny | Deny |

CUSTOMER_CREATE, CUSTOMER_EDIT, SITE_CREATE and SITE_EDIT retain the #24
Admin-only mapping. Operations/Dispatcher writes and all unrelated TBD,
limited, scoped-audit, Yard-scope and override decisions remain deferred.

Identity exposes fresh `resolveCustomerScope`, returning Tenant-wide or an
immutable Customer UUID set without exposing role codes. Customers uses this
boundary to filter database queries. Shared Physical Sites are visible to a
Dispatcher through customer_sites; Locations require a matching customer_id.
Neutral NULL Locations are visible only to Tenant-wide readers. Customer-level
RLS is not introduced; transaction-local Tenant RLS remains defense-in-depth.
Customer-scoped roles now reference real Customers via the V5 Tenant-safe FK.

## Approved Driver/Tractor read mapping (#28)

The owner approved DRIVER_VIEW and TRACTOR_VIEW, extending the catalog from
55 to **57 capabilities**. These are the only additional grants:

| Role | DRIVER_VIEW | TRACTOR_VIEW |
| --- | --- | --- |
| TENANT_ADMIN | Tenant-wide | Tenant-wide |
| TRANSPORT_OPERATIONS | Tenant-wide, read-only | Tenant-wide, read-only |
| CUSTOMER_DISPATCHER | Deny | Deny |
| YARD_SPOTTER | Deny | Deny |
| DRIVER | Deny | Deny |

DRIVER_CREATE, DRIVER_EDIT, TRACTOR_CREATE and TRACTOR_EDIT remain Admin-only.
No generic Yard/Driver browse permission or Operations administrative write
permission is implied. All 57 capabilities are covered by exhaustive mapping tests.

Driver/User linking is authorized separately with DRIVER_EDIT. The target User
must have a fresh same-Tenant ACTIVE membership and TENANT-scoped DRIVER role
with customer_id NULL. Identity exposes only `DriverAccountLinkPolicy` eligibility
validation to Drivers; it does not expose roles or authorize the actor. Linking
creates no membership/role; unlinking removes neither identity nor access.

## Future custom roles

MVP may ship only with system roles.

The capability model should make future tenant-defined custom roles possible without changing domain services, but a custom-role UI is not required for MVP.

## Trailer read and QR identification boundary (#29)

The catalog remains 57 capabilities and all existing mappings are unchanged.
Admin: TRAILER_CREATE/EDIT/VIEW_ALL. Operations: VIEW_ALL only.
Dispatcher: VIEW_CUSTOMER_SCOPED with fresh Customer assignments; unassigned/
foreign designation is invisible. Driver/Yard have no generic Trailer browse.
Identity's public TrailerAccessPolicy exposes only Tenant-wide/Customer-ID scope,
never internal roles or Firebase claims. Explicit outside-scope Customer list
filter is CUSTOMER_SCOPE_DENIED; invisible direct resources return 404.

QR list/issue/revoke uses TRAILER_EDIT, Admin-only. Global authenticated resolution
derives Tenant from exact active QR under SELECT-only RLS, then checks fresh
ACTIVE membership and VIEW_ALL/scoped designation. YARD_MOVE_EXECUTE or
YARD_CHECK_EXECUTE and TRAILER_HOOK or TRAILER_DROP allow scan identification
only; future commands must enforce assignment/domain-state authorization.
QR identifier alone grants nothing. All QR authorization failures are safe 404.
No new OOS/correction/return-to-service authority is inferred; OQ-054 stays open.
