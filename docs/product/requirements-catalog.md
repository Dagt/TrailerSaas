# Functional Requirements Catalog

**Status:** Draft derived from discovery  
**Convention:** `FR-###`

This catalog is intentionally behavior-focused. It is not an API specification.

## Tenant, identity and access

- **FR-001** — The system shall support multiple tenant companies.
- **FR-002** — The system shall isolate data between tenants.
- **FR-003** — A tenant shall support multiple internal users with role-based permissions.
- **FR-004** — A tenant shall support customer users scoped to one customer account.
- **FR-005** — Customer users shall not access other customers' trailers/jobs/documents.

## Master data

- **FR-010** — Authorized users shall create and manage drivers.
- **FR-011** — Driver records shall support name, license number and medical certificate expiration.
- **FR-012** — Authorized users shall create and manage tractors.
- **FR-013** — Authorized users shall create and manage trailers.
- **FR-014** — Trailer records shall support tenant-scoped unique operational identifiers.
- **FR-015** — Trailer records shall support type/size and capability metadata.
- **FR-016** — Trailers shall be associable with a designated customer where applicable.
- **FR-017** — Authorized users shall create and manage customers.
- **FR-018** — Customers shall support multiple users/contacts.
- **FR-019** — Authorized users shall create and manage known operational locations.
- **FR-020** — Locations shall support address and geofence metadata.

## Orders and jobs

- **FR-030** — Authorized users shall create an order/job from customer shipping information.
- **FR-031** — Orders/jobs shall support order number, PO, shipment number and BOL references as applicable.
- **FR-032** — Jobs shall support origin, destination and appointment date/time.
- **FR-033** — Jobs shall be assignable to a driver.
- **FR-034** — Jobs shall be assignable to a tractor.
- **FR-035** — Jobs shall support trailer assignment when known.
- **FR-036** — A job may exist before the trailer is known.
- **FR-037** — Authorized users shall reassign work according to job state.
- **FR-038** — Reassignment shall be auditable.
- **FR-039** — Jobs shall support cancellation/exception handling.
- **FR-040** — The exact multi-stop/multi-order cardinality is pending validation and shall not be assumed.

## Driver mobile

- **FR-050** — Drivers shall view assigned work on mobile.
- **FR-051** — Drivers shall start an assigned job.
- **FR-052** — Drivers shall start/end a work shift if shift tracking remains in MVP.
- **FR-053** — Drivers shall record trailer hook by scanning trailer QR.
- **FR-054** — Drivers shall record trailer drop by scanning trailer QR.
- **FR-055** — Hook events shall associate trailer, driver, tractor, job and timestamp.
- **FR-056** — Drop events shall associate trailer, job, location and timestamp.
- **FR-057** — Drivers shall record/confirm arrival.
- **FR-058** — Drivers shall record/confirm departure.
- **FR-059** — Drivers shall upload photos/documents as delivery evidence.
- **FR-060** — Drivers shall report at least basic delay, damage or operational exception.
- **FR-061** — Critical driver actions shall support temporary offline capture and later synchronization.
- **FR-062** — Offline synchronization shall avoid duplicate business events.

## QR and trailer identity

- **FR-070** — Each operational trailer shall be assignable a durable QR identity.
- **FR-071** — QR scan shall resolve to a trailer within the current tenant context.
- **FR-072** — A mismatched trailer scan shall not silently overwrite the planned assignment.
- **FR-073** — The mismatch resolution policy is pending business validation.

## Trailer state and history

- **FR-080** — The system shall store trailer load state separately from operational state.
- **FR-081** — The system shall store trailer location separately from load/operational state.
- **FR-082** — The system shall support an out-of-service/damage condition.
- **FR-083** — The system shall preserve trailer movement history.
- **FR-084** — Users shall search trailers by operational identifier.
- **FR-085** — Trailer detail shall show current location.
- **FR-086** — Trailer detail shall show time/dwell at current location when derivable.
- **FR-087** — Trailer detail shall show last movement.
- **FR-088** — Trailer detail shall show last driver.
- **FR-089** — Trailer detail shall show last tractor where applicable.
- **FR-090** — Trailer detail shall show current/last relevant order/content.
- **FR-091** — Authorized corrections shall preserve who, when and what changed.

## Geolocation

- **FR-100** — Known locations may define geofences.
- **FR-101** — Mobile may use geofence events to assist arrival detection.
- **FR-102** — Mobile may use geofence events to assist departure detection.
- **FR-103** — The system shall provide a manual fallback/confirmation path when geofence behavior is unavailable or incorrect.
- **FR-104** — Continuous route tracking is not an MVP requirement.

## Yard check

- **FR-110** — Authorized users shall start a yard check for a location.
- **FR-111** — The system shall establish an expected trailer set for the yard check.
- **FR-112** — Users shall scan trailers by QR during a yard check.
- **FR-113** — The system shall identify expected trailers not found.
- **FR-114** — The system shall identify unexpected trailers found.
- **FR-115** — Yard-check completion shall be historized.
- **FR-116** — Reconciliation/correction after a yard check shall be auditable.

## Alerts

- **FR-120** — The system shall detect trailers without movement beyond a configured threshold.
- **FR-121** — Administration shall receive an in-app stale-trailer alert.
- **FR-122** — A default/example threshold of three days comes from discovery; final configuration policy remains to be approved.

## Customer portal

- **FR-130** — Customer users shall view authorized trailer inventory.
- **FR-131** — Customer users shall view current trailer location/status/content within their scope.
- **FR-132** — Customer users shall view their own jobs/orders.
- **FR-133** — Customer users shall view permitted delivery evidence/documents.
- **FR-134** — Customer users shall be able to request a movement if the final approval workflow includes this capability.
- **FR-135** — Customer requests shall not become executable work without whatever approval rule is ultimately adopted.

## Documents and evidence

- **FR-140** — Jobs/orders shall support BOL-related documents.
- **FR-141** — Delivery evidence shall support photos.
- **FR-142** — Evidence shall retain uploader, timestamp and related business entity.
- **FR-143** — Signed BOL shall be supported as evidence.
- **FR-144** — Standalone digital-signature capture is not confirmed.

## Audit

- **FR-150** — Important administrative changes shall be auditable.
- **FR-151** — Audit records shall identify actor and timestamp.
- **FR-152** — Operational corrections shall not erase the original event/history.
- **FR-153** — System-generated/geofence/integration events shall identify their source.

## Reporting

- **FR-160** — The system shall expose pending orders and undelivered loads to authorized operations users.
- **FR-161** — The system shall expose inventory views for empty, loaded, customer-located, in-transit and stale trailers when those states can be derived.
- **FR-162** — Advanced daily/weekly/monthly reports remain to be defined.
