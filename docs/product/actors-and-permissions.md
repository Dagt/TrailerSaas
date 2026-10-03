# Actors and Permissions

**Status:** Draft

Authorization must be tenant-scoped and role-based. Final role names may change.

## Actor 1 — Tenant Administrator

Typical user: company administrator / operations manager.

Candidate capabilities:

- manage tenant settings;
- create/disable users;
- manage drivers;
- manage tractors;
- manage trailers;
- manage customers;
- manage locations;
- view all tenant operations;
- correct operational records through auditable actions;
- view audit history;
- configure alerts;
- manage role assignments.

## Actor 2 — Dispatcher / Shipping

Typical user: dispatcher, shipping lead or qualified shipping staff.

Candidate capabilities:

- create orders/jobs;
- edit unstarted work;
- assign/reassign drivers and tractors;
- associate trailers;
- modify active work where business rules allow;
- create customers/locations where authorized;
- view trailer inventory and availability;
- view delivery status;
- receive/handle exceptions.

Discovery confirms that shipping/dispatch currently assigns drivers and can change assignments.

## Actor 3 — Yard / Spotter

Typical user: yard spotter or shipping/receiving staff performing yard inventory.

Candidate capabilities:

- start/complete yard checks;
- scan trailer QR codes;
- record in-yard movement;
- flag damage/OOS;
- see missing/unexpected trailers for authorized yards;
- propose/carry out location correction according to permissions.

This role should **not automatically have unrestricted administrative correction power**.

## Actor 4 — Driver

Mobile-first role.

Candidate capabilities:

- start/end shift;
- view assigned jobs;
- accept/start job;
- scan trailer on hook/drop;
- confirm arrival/departure;
- upload photos/BOL/POD;
- report delay/problem/damage;
- view current work and limited recent history;
- hand off work if approved workflow supports it.

Drivers should not manage master data.

## Actor 5 — Customer User

Belongs to a customer account within a tenant.

Candidate capabilities:

- view only own company's permitted trailers;
- view current location/status/content;
- view own jobs/orders;
- request a movement if enabled;
- view permitted documents/evidence;
- support multiple contacts/users.

Customer users must never see another customer's operational data.

## Actor 6 — External Recipient

A third-party delivery recipient generally does not need a persistent account in the initial design.

If later required, confirmation may be handled through:

- driver-captured evidence;
- signed BOL;
- a one-time confirmation flow.

This is unresolved and should not be implemented without validation.

## Permission matrix — initial draft

| Capability | Admin | Dispatcher/Shipping | Yard/Spotter | Driver | Customer |
|---|---:|---:|---:|---:|---:|
| Manage users | ✓ | — | — | — | — |
| Manage drivers | ✓ | limited/TBD | — | — | — |
| Manage trailers | ✓ | read/TBD | limited | — | read scoped |
| Manage tractors | ✓ | read/TBD | — | read assigned | — |
| Manage customers | ✓ | ✓ | — | — | — |
| Manage locations | ✓ | ✓ | limited/TBD | — | read scoped |
| Create job | ✓ | ✓ | — | — | request only |
| Assign/reassign job | ✓ | ✓ | — | — | — |
| Start/execute job | — | monitor | — | ✓ | — |
| Hook/drop scan | — | override | ✓ in yard | ✓ | — |
| Upload POD | view | view | — | ✓ | view scoped |
| Yard check | view | view | ✓ | — | optional scoped |
| Correct operational data | ✓ auditable | limited auditable | limited | — | — |
| View audit history | ✓ | scoped/TBD | — | own/TBD | — |

## Authorization rules

1. Every protected entity must belong directly or indirectly to a tenant.
2. Customer-user access requires both tenant match and customer scope.
3. A user ID supplied by the client cannot be trusted as authorization.
4. Administrative corrections must preserve prior history.
5. Role checks should be enforced server-side.
6. Cross-tenant identifiers should not leak existence through error behavior where practical.

## Open permission questions

See:

- OQ-011 — who can correct trailer location?
- OQ-012 — customer request approval workflow
- OQ-013 — driver handoff permissions
