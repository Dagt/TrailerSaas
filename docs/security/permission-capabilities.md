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

## Future custom roles

MVP may ship only with system roles.

The capability model should make future tenant-defined custom roles possible without changing domain services, but a custom-role UI is not required for MVP.
