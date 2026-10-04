# ADR-011 — Multi-Tenancy, Memberships and Authorization

**Status:** Accepted  
**Date:** 2026-10-04

## Context

TrailerSaaS is a B2B SaaS where the paying company/tenant (for example 2 Fast Logistics) operates with:

- internal administrators;
- transport operations / dispatch;
- drivers;
- yard spotters;
- customer companies such as Hood Container, Stanfast and Black Hawk;
- customer-side dispatch/shipping users who actively create and modify operational work.

The authorization model therefore cannot be a simple global `role = ADMIN|DRIVER` field on the user.

We need to answer:

- which tenant the user is acting inside;
- whether the user is internal or customer-scoped;
- which business role(s) the user has;
- which customer data the user is allowed to access;
- whether a specific action is allowed in the current business state.

## Decision

Use a **shared multi-tenant application and database** with explicit tenant scoping.

Use the following identity/access concepts:

1. **User**
   - global TrailerSaaS user identity;
   - linked to Firebase Authentication UID;
   - does not by itself grant access to any tenant.

2. **Tenant**
   - subscribing transportation/logistics company;
   - billing and primary security boundary.

3. **TenantMembership**
   - links a User to a Tenant;
   - stores membership status;
   - a user may support multiple tenant memberships in the generic SaaS model.

4. **RoleAssignment**
   - assigns one or more system roles to a TenantMembership;
   - can be tenant-wide or scoped to a Customer.

5. **Customer**
   - company served by the Tenant;
   - customer-side users operate inside the Tenant but are restricted to their Customer scope.

## Initial system roles

### TENANT_ADMIN
Tenant-wide.

Can manage users, master data, configuration and all operational data.

### TRANSPORT_OPERATIONS
Tenant-wide.

For transportation-company dispatch/shipping/operations.

Can plan and monitor Trips, assign tractors/drivers, handle exceptions and perform authorized operational changes.

### CUSTOMER_DISPATCHER
Customer-scoped.

Can operate only on the assigned Customer scope, including creating Orders and performing the customer-side actions allowed by business rules.

### YARD_SPOTTER
Tenant-wide or location-scoped in a future extension.

Can perform Yard Mode, Yard Checks and authorized trailer location/state corrections.

### DRIVER
Tenant-wide membership but operational access is restricted primarily to assigned work and own actions.

## Role assignments, not a single role column

A membership may hold multiple role assignments.

Example:

```text
User: Jane
TenantMembership: 2 Fast Logistics
RoleAssignments:
  - TRANSPORT_OPERATIONS
  - YARD_SPOTTER
```

Customer example:

```text
User: Maria
TenantMembership: 2 Fast Logistics
RoleAssignment:
  - CUSTOMER_DISPATCHER
    customer_id = Hood Container
```

This avoids creating a separate identity system for each customer.

## Authorization layers

Authorization is evaluated in layers:

### Layer 1 — Authentication
Firebase Authentication proves the external identity.

### Layer 2 — Tenant membership
TrailerSaaS verifies the user has an active membership in the requested Tenant.

### Layer 3 — Role / permission
TrailerSaaS verifies the membership has the required role/capability.

### Layer 4 — Resource scope
For customer-scoped roles, TrailerSaaS verifies the target resource belongs to the permitted Customer.

### Layer 5 — Business-state authorization
Even a user with the right role may be blocked by domain state.

Examples:

- customer cannot silently replace a trailer after departure;
- driver cannot substitute an assigned trailer without authorization;
- completed/cancelled records may no longer be directly editable.

## Tenant context

Every authenticated API request that touches tenant data must execute in an explicit tenant context derived from:

- the authenticated User; and
- an active TenantMembership.

The backend must never authorize a request merely because the client sends a `tenant_id`.

## Database scoping

Every tenant-owned aggregate root must have a non-null `tenant_id`.

High-risk child/event/history tables should also carry `tenant_id` directly where this improves authorization, indexing and incident analysis, even when tenant could be inferred through a parent relationship.

Examples:

- customers
- users/memberships
- drivers
- tractors
- trailers
- physical_sites
- operational_locations
- orders
- trips
- stops
- manifests
- movement_events
- documents
- yard_checks
- alerts
- audit_events

## Tenant uniqueness

Human-readable identifiers are unique only within the appropriate tenant/business scope unless a stricter rule is explicitly defined.

Examples:

- tractor number: tenant-scoped;
- trailer operational number: tenant-scoped for the initial design partner;
- order number: may require customer + tenant scoping;
- email identity is global through Firebase but membership is tenant-specific.

## PostgreSQL Row Level Security

Use application-level authorization as the primary control.

**RLS is recommended as defense-in-depth but will be introduced only after the physical schema and connection-pooling strategy are defined and tested.**

Do not treat RLS as a replacement for application authorization.

## Cross-tenant behavior

The API should avoid leaking whether another tenant's resource exists.

For unauthorized/cross-tenant identifiers, prefer responses that do not expose unrelated resource metadata.

## Consequences

### Positive

- clean SaaS security boundary;
- customer users can participate without becoming separate tenants;
- future user membership in multiple tenants remains possible;
- supports multiple roles per user;
- business authorization remains under TrailerSaaS control rather than Firebase custom claims.

### Negative / trade-offs

- authorization checks require consistent tenant/customer context;
- role assignment model is more complex than one role column;
- tests must cover cross-tenant and cross-customer access extensively.

## Follow-up

- define permission/capability matrix;
- define TenantMembership and RoleAssignment tables;
- decide whether customer users can belong to multiple Customers;
- define invitation/onboarding flow;
- define RLS strategy after physical schema design.
