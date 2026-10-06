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
- `SITE_CREATE`
- `SITE_EDIT`

### Driver / tractor / trailer master data

- `DRIVER_CREATE`
- `DRIVER_EDIT`
- `TRACTOR_CREATE`
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

## Future custom roles

MVP may ship only with system roles.

The capability model should make future tenant-defined custom roles possible without changing domain services, but a custom-role UI is not required for MVP.
