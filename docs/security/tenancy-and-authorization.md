# Tenancy and Authorization Model

**Status:** Approved baseline  
**Related ADR:** ADR-011

## Security hierarchy

```text
Firebase Identity
      │
      ▼
TrailerSaaS User
      │
      ▼
TenantMembership
      │
      ├── Tenant-wide RoleAssignment(s)
      │
      └── Customer-scoped RoleAssignment(s)
```

The **Tenant** is the primary isolation boundary.

A **Customer** is not a Tenant. It is a business entity inside a Tenant that may have scoped users.

Example:

```text
Tenant
└── 2 Fast Logistics
    ├── Internal Users
    │   ├── Tenant Admin
    │   ├── Transport Operations
    │   ├── Driver
    │   └── Yard Spotter
    │
    └── Customers
        ├── Hood Container
        │   └── Customer Dispatchers
        ├── Stanfast
        │   └── Customer Dispatchers
        └── Black Hawk
            └── Customer Dispatchers
```

## Why customers are not separate tenants

Hood Container participates in 2 Fast Logistics' operational network:

- its Orders enter the same operational queue;
- 2 Fast drivers and tractors execute the work;
- trailers are designated to the customer;
- both sides participate in operational changes.

Making Hood a separate tenant would create unnecessary cross-tenant workflows and duplicate shared business objects.

Instead, Hood users are customer-scoped members inside the 2 Fast Tenant.

## Initial authorization model

### TENANT_ADMIN

Scope: entire Tenant.

Typical capabilities:

- manage users and role assignments;
- manage Customers;
- manage Drivers/Tractors/Trailers;
- manage Locations;
- access all Orders/Trips/Yard data;
- correct data;
- view audit;
- manage settings.

### TRANSPORT_OPERATIONS

Scope: entire Tenant.

Typical capabilities:

- view pending Orders;
- create operational Trips/Runs;
- assign Drivers;
- assign Tractors;
- coordinate trailer changes;
- change/reroute active work where allowed;
- monitor current operations;
- resolve operational exceptions.

### CUSTOMER_DISPATCHER

Scope: one Customer.

For the initial Hood Container workflow:

- create Orders;
- define destinations/stops;
- assign/designate Trailer;
- participate in Driver assignment;
- cancel Orders;
- authorize alternate Trailer;
- participate in destination changes;
- participate in Order completion;
- view customer-scoped trailers/orders/evidence.

Cannot access another Customer's data.

### YARD_SPOTTER

Scope: Tenant, with actual actions constrained to relevant Sites/Yards.

Capabilities:

- Road-equipment duty flow as applicable;
- Yard Mode;
- Yard moves;
- Yard Checks;
- QR scanning;
- authorized location/state corrections.

### DRIVER

Scope: Tenant membership, but resource access is assignment/action constrained.

Capabilities:

- own shift;
- own Pre/Post-Trip;
- assigned Trips/Stops;
- QR Hook/Drop;
- arrival/departure;
- POD/BOL;
- Fuel Event;
- own operational exceptions.

Cannot browse or modify general tenant administration data.

## Permission strategy

Do not encode all authorization as UI visibility.

Frontend may hide disabled actions for UX, but backend remains authoritative.

Preferred backend pattern:

```text
authenticate
→ resolve internal User
→ resolve active TenantMembership
→ load role assignments
→ authorize capability
→ authorize resource customer/tenant scope
→ enforce domain-state rule
→ execute command
```

## Capability-oriented checks

Code should prefer meaningful authorization checks such as:

- `ORDER_CREATE`
- `ORDER_CANCEL`
- `TRIP_ASSIGN_DRIVER`
- `TRAILER_SUBSTITUTE_AUTHORIZE`
- `TRAILER_LOCATION_CORRECT`
- `YARD_CHECK_EXECUTE`
- `USER_MANAGE`

rather than scattering role-name comparisons throughout controllers.

Roles map to capabilities centrally.

This makes future custom roles possible without rewriting domain logic.

## Customer scope

A customer-scoped RoleAssignment includes a `customer_id`.

Authorization example:

```text
Role: CUSTOMER_DISPATCHER
Scope Customer: Hood Container

Requested Order.customer_id = Hood Container
→ allowed if capability exists

Requested Order.customer_id = Stanfast
→ denied
```

## Multiple customer scopes

The data model should permit multiple customer-scoped RoleAssignments for one membership even if the first customer users normally have one Customer.

Example:

```text
RoleAssignment CUSTOMER_DISPATCHER → Hood
RoleAssignment CUSTOMER_DISPATCHER → Stanfast
```

This costs little in the model and avoids a future migration.

## Suggested membership states

- INVITED
- ACTIVE
- SUSPENDED
- DISABLED

Deleting a membership should not erase historical actor references.

## Historical actors

Operational/audit records should reference immutable internal User IDs.

If a user is disabled later, prior events still display the historical actor.

## Service accounts / integrations

Future Samsara, Amtech or API integrations should not masquerade as human Users.

Use a distinct actor/source model such as:

- HUMAN_USER
- SYSTEM
- INTEGRATION

and an IntegrationCredential/IntegrationActor concept if/when integrations are implemented.

## Required authorization tests

At minimum:

1. Tenant A user cannot read Tenant B trailer by UUID.
2. Tenant A user cannot mutate Tenant B Order.
3. Hood Customer Dispatcher cannot read Stanfast Order.
4. Hood Customer Dispatcher cannot access Stanfast documents.
5. Driver cannot access unassigned administrative resources.
6. Yard Spotter cannot perform Tenant Admin actions.
7. Disabled membership cannot execute requests.
8. Role removal takes effect without relying on stale frontend state.
9. Cross-tenant object IDs do not reveal protected metadata.
10. Audit events preserve actor after user/membership disablement.
