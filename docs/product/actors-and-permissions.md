# Actors and Permissions

**Status:** Draft refined after second discovery round

Authorization is tenant-scoped and role-based. Customer users additionally require customer scope.

## Actor 1 — Tenant Administrator

Typical user: transportation-company administrator/operations manager.

Candidate capabilities:

- manage tenant/users/roles;
- manage drivers/tractors/trailers;
- manage customers/sites;
- view all tenant operations;
- correct operational data auditably;
- configure alerts;
- view audit history.

## Actor 2 — Transport Operations / Shipping

Typical user: transportation-company shipping/operations/dispatcher personnel.

Candidate capabilities:

- view customer-created Orders;
- plan Trips and Stops;
- assign driver/tractor/trailer;
- authorize trailer substitution;
- authorize destination change;
- reassign exceptional driver/tractor;
- monitor active operations;
- handle exceptions.

Some responsibilities may overlap with customer-side shipping in the design partner and require final permission matrix validation.

## Actor 3 — Customer Dispatcher / Shipping

Belongs to a manufacturing/customer company.

Confirmed design-partner behavior:

- can create Orders/deliveries directly;
- created work appears in operational queue automatically;
- can view its customer-scoped trailer inventory;
- can view own Orders/deliveries;
- can modify/cancel before permitted cutoff;
- can view permitted BOL/POD evidence.

Must never access another customer.

## Actor 4 — Yard / Spotter

Mobile-first yard role.

Capabilities:

- On Duty / Off Duty;
- Pre/Post-trip;
- Yard Mode;
- yard↔dock moves;
- QR scans;
- yard checks;
- view missing/unexpected trailers;
- correct permitted trailer location/state with audit;
- mark/report OOS/damage as permitted.

Design-partner explicitly selected Yard Spotter as a role allowed to correct trailer location.

## Actor 5 — Driver — Road Mode

Mobile-first.

Capabilities:

- On Duty / Off Duty;
- Pre/Post-trip;
- tractor photos/odometer;
- view assigned Trip;
- scan trailer;
- depart;
- confirm arrival;
- Loading/Unloading;
- Drop & Hook;
- multi-stop execution;
- BOL/POD photo;
- optional fuel event;
- report exception/damage.

Restrictions:

- cannot silently substitute trailer;
- does not have unrestricted location correction;
- mid-task handoff/reassignment is controlled by dispatch.

## Actor 6 — External Recipient

Usually no persistent account.

May participate through:

- physical BOL signature;
- receiver interaction captured by driver;
- future one-time confirmation if later required.

Standalone account/digital signature is not required for MVP.

## Permission matrix — revised draft

| Capability | Admin | Transport Ops | Customer Dispatcher | Yard/Spotter | Driver |
|---|---:|---:|---:|---:|---:|
| Manage users/roles | ✓ | — | customer contacts TBD | — | — |
| Manage drivers | ✓ | limited/TBD | — | — | — |
| Manage tractors | ✓ | ✓/TBD | — | read assigned | read assigned |
| Manage trailers | ✓ | ✓/TBD | scoped read | limited operational | scan/read |
| Create customer | ✓ | ✓/TBD | — | — | — |
| Manage sites | ✓ | ✓/TBD | customer-scoped TBD | limited | read assigned |
| Create Order | ✓ | ✓ | ✓ scoped | — | — |
| Plan/assign Trip | ✓ | ✓ | — unless later allowed | — | — |
| Authorize trailer substitution | ✓ | ✓ | — | — | — |
| Execute Road Trip | — | monitor | view scoped | — | ✓ |
| Yard move | — | override | — | ✓ | — |
| Yard Check | view | view | scoped/TBD | ✓ | — |
| Upload POD | view | view | view scoped | — | ✓ |
| Correct trailer location | ✓ | shipping/TBD | scoped shipping TBD | ✓ | — |
| Destination change authorization | ✓ | ✓ | request/edit until cutoff | — | — |
| View audit history | ✓ | scoped | own/scoped TBD | limited | own actions TBD |

## Authorization rules

1. Every protected entity belongs directly or indirectly to one tenant.
2. Customer access requires tenant + customer scope.
3. Server must enforce permissions.
4. Client-supplied actor/customer/tenant IDs are not authorization.
5. Corrections/reassignments/substitutions remain auditable.
6. Cross-tenant/customer identifiers must not leak unrelated records.

## Remaining permission questions

See `open-questions.md` for:

- exact distinction between customer shipping and tenant shipping correction authority;
- exact customer edit/cancel cutoff;
- who may return OOS trailer to service;
- whether customer dispatch can ever assign driver/trip directly.
