# Relational Data Model v0.1

**Status:** Draft physical-model baseline  
**Database:** PostgreSQL  
**Related ADRs:** ADR-003, ADR-006, ADR-011

This document is intentionally one level below the conceptual domain model and one level above executable Flyway migrations.

## Global conventions

### IDs

Use PostgreSQL `uuid` primary keys for business entities.

Human-readable identifiers such as trailer number, tractor number or order number are **not** primary keys.

### Time

Use `timestamptz` for event/transaction timestamps.

Store times in UTC and retain the relevant IANA timezone on operational Sites when local presentation/scheduling matters.

### Tenant scoping

Every tenant-owned aggregate root includes:

```text
tenant_id UUID NOT NULL
```

High-risk history/event tables also carry `tenant_id` directly.

### Soft lifecycle

Do not physically delete operational records as the normal workflow.

Use statuses / disabled timestamps and apply retention policies through explicit archival/deletion jobs only where approved.

---

# Identity / tenancy

## users

Global identity record.

```text
id                  uuid PK
firebase_uid        varchar UNIQUE NOT NULL
email               varchar
display_name        varchar
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL
```

A User by itself has no tenant access.

## tenants

```text
id                  uuid PK
name                varchar NOT NULL
status              varchar NOT NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL
```

Commercial subscription data should be separated from core operational rows when billing is implemented.

## tenant_memberships

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
user_id             uuid FK users NOT NULL
status              varchar NOT NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL

UNIQUE (tenant_id, user_id)
```

Suggested status:

- INVITED
- ACTIVE
- SUSPENDED
- DISABLED

## role_assignments

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
membership_id       uuid FK tenant_memberships NOT NULL
role_code           varchar NOT NULL
customer_id         uuid NULL
created_at          timestamptz NOT NULL
```

`customer_id` is set for customer-scoped roles such as CUSTOMER_DISPATCHER.

A membership can have multiple role assignments.

---

# Customers and locations

## customers

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
name                varchar NOT NULL
status              varchar NOT NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL

UNIQUE (tenant_id, name)
```

## physical_sites

Physical street-address-level facility.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
name                varchar NOT NULL
address_line1       varchar
address_line2       varchar
city                varchar
state_region        varchar
postal_code         varchar
country_code        varchar(2)
timezone            varchar NOT NULL
latitude            numeric NULL
longitude           numeric NULL
geofence_radius_m   integer NULL
active              boolean NOT NULL
```

A Site may serve multiple customer contexts.

## operational_locations

Business sublocation within a Site.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
site_id             uuid FK physical_sites NOT NULL
customer_id         uuid FK customers NULL
type                varchar NOT NULL
code                varchar NULL
name                varchar NOT NULL
active              boolean NOT NULL
```

Candidate types:

- BASE
- YARD
- DOCK
- CUSTOMER_AREA
- SHOP
- OTHER

Unknown location is represented by trailer state, not by creating a fake reusable Site row unless later required.

---

# People and equipment

## drivers

Operational driver profile.

```text
id                          uuid PK
tenant_id                   uuid FK tenants NOT NULL
user_id                     uuid FK users NULL
name                        varchar NOT NULL
license_number              varchar
medical_certificate_expires date
active                      boolean NOT NULL
created_at                  timestamptz NOT NULL
updated_at                  timestamptz NOT NULL
```

`user_id` may be null before mobile account activation.

## tractors

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
operational_number  varchar NOT NULL
active              boolean NOT NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL

UNIQUE (tenant_id, operational_number)
```

## trailers

Current-state row for fast operational reads.

```text
id                          uuid PK
tenant_id                   uuid FK tenants NOT NULL
operational_number          varchar NOT NULL
designated_customer_id      uuid FK customers NULL
trailer_type                varchar
capability_notes            varchar NULL
load_state                  varchar NOT NULL
operational_state           varchar NOT NULL
current_location_id         uuid FK operational_locations NULL
last_known_location_id      uuid FK operational_locations NULL
last_seen_at                timestamptz NULL
last_driver_id              uuid FK drivers NULL
out_of_service              boolean NOT NULL
active                      boolean NOT NULL
created_at                  timestamptz NOT NULL
updated_at                  timestamptz NOT NULL

UNIQUE (tenant_id, operational_number)
```

For the design partner, trailer operational numbers are tenant-unique.

A future SaaS tenant that needs another uniqueness rule should not require changing the internal UUID identity.

## trailer_qr_identities

Separates physical QR token from human-readable trailer number.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
trailer_id          uuid FK trailers NOT NULL
public_token        varchar UNIQUE NOT NULL
active              boolean NOT NULL
issued_at           timestamptz NOT NULL
revoked_at          timestamptz NULL
```

QR should encode a non-guessable public token/URL, not only the trailer number.

---

# Orders / manifests

## orders

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
customer_id         uuid FK customers NOT NULL
order_number        varchar NOT NULL
po_number           varchar NULL
shipment_number     varchar NULL
bol_reference       varchar NULL
status              varchar NOT NULL
created_by_user_id  uuid FK users NOT NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL
```

Order-number uniqueness must be defined by customer/tenant business rules rather than assumed globally.

Candidate index:

```text
(tenant_id, customer_id, order_number)
```

## order_cargo_lines

MVP cargo detail.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
order_id            uuid FK orders NOT NULL
description         varchar
quantity            numeric NULL
pallet_count        integer NULL
weight              numeric NULL
weight_unit         varchar NULL
created_at          timestamptz NOT NULL
```

## load_manifests

Represents a trailer load/manifest for an operational segment.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
trailer_id          uuid FK trailers NULL
status              varchar NOT NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL
```

## manifest_orders

Explicit many-to-many association.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
manifest_id         uuid FK load_manifests NOT NULL
order_id            uuid FK orders NOT NULL
created_at          timestamptz NOT NULL

UNIQUE (manifest_id, order_id)
```

---

# Trips / stops / assignments

## trips

Represents an operational run.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
status              varchar NOT NULL
current_driver_id   uuid FK drivers NULL
current_tractor_id  uuid FK tractors NULL
started_at          timestamptz NULL
completed_at        timestamptz NULL
created_at          timestamptz NOT NULL
updated_at          timestamptz NOT NULL
```

Do not tie Shift completion to Trip completion.

## trip_stops

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
trip_id             uuid FK trips NOT NULL
sequence_number     integer NOT NULL
stop_type           varchar NOT NULL
site_id             uuid FK physical_sites NULL
location_id         uuid FK operational_locations NULL
scheduled_at        timestamptz NULL
arrived_at          timestamptz NULL
departed_at         timestamptz NULL
status              varchar NOT NULL

UNIQUE (trip_id, sequence_number)
```

## trip_stop_orders

Associates Orders to specific Stops.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
trip_stop_id        uuid FK trip_stops NOT NULL
order_id            uuid FK orders NOT NULL

UNIQUE (trip_stop_id, order_id)
```

## trip_assignment_events

Audit/history of driver/tractor/trailer assignment changes.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
trip_id             uuid FK trips NOT NULL
type                varchar NOT NULL
driver_id           uuid FK drivers NULL
tractor_id          uuid FK tractors NULL
trailer_id          uuid FK trailers NULL
actor_user_id       uuid FK users NULL
occurred_at         timestamptz NOT NULL
reason              varchar NULL
```

Current driver/tractor live on `trips` for fast reads; assignment events preserve history.

---

# Operational events

## trailer_movement_events

Append-oriented trailer history.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
trailer_id          uuid FK trailers NOT NULL
trip_id             uuid FK trips NULL
trip_stop_id        uuid FK trip_stops NULL
driver_id           uuid FK drivers NULL
tractor_id          uuid FK tractors NULL
location_id         uuid FK operational_locations NULL
event_type          varchar NOT NULL
source_type         varchar NOT NULL
actor_user_id       uuid FK users NULL
occurred_at         timestamptz NOT NULL
recorded_at         timestamptz NOT NULL
client_operation_id uuid NULL
reason              varchar NULL
metadata            jsonb NULL
```

Candidate event types:

- HOOKED
- DROPPED
- ARRIVED
- DEPARTED
- YARD_MOVED
- LOCATION_CORRECTED
- LOCATION_MARKED_UNKNOWN
- LOAD_STATE_CHANGED
- MARKED_OOS
- RETURNED_TO_SERVICE

`client_operation_id` supports mobile idempotency.

Use a unique constraint/index appropriate to idempotent client operations, e.g. tenant + client operation ID when non-null.

---

# Driver duty / inspections / fuel

## shifts

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
driver_id           uuid FK drivers NOT NULL
on_duty_at          timestamptz NOT NULL
off_duty_at         timestamptz NULL
created_at          timestamptz NOT NULL
```

Do not calculate wages/payroll in MVP.

## inspections

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
driver_id           uuid FK drivers NOT NULL
tractor_id          uuid FK tractors NOT NULL
shift_id            uuid FK shifts NULL
trip_id             uuid FK trips NULL
inspection_type     varchar NOT NULL
odometer            numeric NULL
damages             text NULL
failures            text NULL
comments            text NULL
checklist_data      jsonb NULL
created_at          timestamptz NOT NULL
```

Pre-Trip uses checklist + four standard photos.

Post-Trip records final mileage, damage/failure/comment data.

## fuel_events

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
driver_id           uuid FK drivers NOT NULL
tractor_id          uuid FK tractors NOT NULL
trip_id             uuid FK trips NULL
odometer            numeric NULL
occurred_at         timestamptz NOT NULL
created_at          timestamptz NOT NULL
```

Receipt is linked through document/evidence records.

---

# Documents / evidence

## documents

Metadata only; object bytes live in R2.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
object_key          varchar NOT NULL
document_type       varchar NOT NULL
content_type        varchar
size_bytes          bigint
uploaded_by_user_id uuid FK users NULL
source_type         varchar NOT NULL
created_at          timestamptz NOT NULL
```

## document_links

Allows one document to be related to different business records without overloading one nullable-FK table.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
document_id         uuid FK documents NOT NULL
entity_type         varchar NOT NULL
entity_id           uuid NOT NULL
created_at          timestamptz NOT NULL
```

Application service validates that target entity belongs to same tenant.

Candidate document types:

- BOL
- POD
- DELIVERY_PHOTO
- PRETRIP_FRONT
- PRETRIP_REAR
- PRETRIP_DRIVER_SIDE
- PRETRIP_PASSENGER_SIDE
- DAMAGE_PHOTO
- FUEL_RECEIPT

---

# Yard checks

## yard_checks

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
site_id             uuid FK physical_sites NOT NULL
started_by_user_id  uuid FK users NOT NULL
started_at          timestamptz NOT NULL
completed_at        timestamptz NULL
status              varchar NOT NULL
```

## yard_check_items

Snapshot/reconciliation row per trailer.

```text
id                        uuid PK
tenant_id                 uuid FK tenants NOT NULL
yard_check_id             uuid FK yard_checks NOT NULL
trailer_id                uuid FK trailers NOT NULL
expected_location_id      uuid FK operational_locations NULL
observed_location_id      uuid FK operational_locations NULL
expected                  boolean NOT NULL
found                     boolean NOT NULL
result                    varchar NOT NULL
scanned_at                timestamptz NULL
scanned_by_user_id        uuid FK users NULL
```

Candidate result:

- MATCHED
- MISSING
- UNEXPECTED
- LOCATION_MISMATCH

A confirmed missing trailer can trigger LOCATION_UNKNOWN + Administration alert.

---

# Alerts

## alerts

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
alert_type          varchar NOT NULL
severity            varchar NOT NULL
status              varchar NOT NULL
related_entity_type varchar NULL
related_entity_id   uuid NULL
message_data        jsonb NULL
created_at          timestamptz NOT NULL
acknowledged_at     timestamptz NULL
acknowledged_by     uuid FK users NULL
```

Examples:

- STALE_TRAILER
- TRAILER_MISSING
- REROUTE
- OPERATIONAL_EXCEPTION

Push delivery is not the source of truth; alert row is.

---

# Audit

## audit_events

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
actor_type          varchar NOT NULL
actor_user_id       uuid FK users NULL
action_code         varchar NOT NULL
entity_type         varchar NOT NULL
entity_id           uuid NOT NULL
before_data         jsonb NULL
after_data          jsonb NULL
reason              varchar NULL
occurred_at         timestamptz NOT NULL
```

Actor type supports:

- HUMAN_USER
- SYSTEM
- INTEGRATION

---

# Mobile synchronization

## processed_client_operations

Used to provide idempotent command handling.

```text
id                  uuid PK
tenant_id           uuid FK tenants NOT NULL
client_operation_id uuid NOT NULL
user_id             uuid FK users NOT NULL
operation_type      varchar NOT NULL
processed_at        timestamptz NOT NULL
result_reference    jsonb NULL

UNIQUE (tenant_id, client_operation_id)
```

The final implementation may collapse this into domain-specific unique keys where appropriate, but the idempotency contract is mandatory.

---

# Indexing baseline

At minimum evaluate indexes for:

- every `tenant_id`;
- `(tenant_id, operational_number)` on tractors/trailers;
- `(tenant_id, customer_id, order_number)`;
- `(tenant_id, status)` on Orders/Trips/Alerts;
- `(tenant_id, trailer_id, occurred_at DESC)` on movement events;
- `(tenant_id, current_location_id)` on trailers;
- `(tenant_id, designated_customer_id)` on trailers;
- `(tenant_id, trip_id, sequence_number)` on Stops;
- `(tenant_id, created_at DESC)` for audit/history views.

Actual indexes should be finalized from query patterns and `EXPLAIN ANALYZE`, not guessed blindly.

# Deliberately not modeled yet

- billing/subscriptions;
- Samsara integration tables;
- Amtech integration tables;
- payroll;
- maintenance work orders;
- route GPS breadcrumb history;
- custom tenant-defined roles;
- API keys/service accounts;
- regulatory ELD/HOS records.

Those require separate scope/ADR decisions.
