# Product Vision

**Status:** Proposed product definition based on confirmed discovery

## Working product statement

TrailerSaaS is a multi-tenant operational platform for trucking companies that manage trailers across yards, customer locations and delivery routes.

It connects dispatchers, drivers, yard personnel and customer shipping teams so that every trailer movement can be associated with a job, driver, tractor, location, load state and evidence.

## Problem statement

The target operation currently depends on messages, documents and a shared spreadsheet. This causes:

- stale or incorrect trailer locations;
- no trustworthy hook/drop history;
- unclear trailer dwell time;
- weak attribution of who changed operational data;
- repeated manual yard checks;
- time spent searching for equipment and orders;
- low confidence in customer trailer inventory;
- fragmented delivery evidence.

## Product promise

At any moment, an authorized user should be able to answer:

1. Where is trailer X?
2. What is in it?
3. What operational state is it in?
4. Which order/job is related to it?
5. Who moved it last?
6. Which tractor was involved?
7. When did it arrive at its current location?
8. What evidence exists for the delivery/movement?
9. Which trailers should be in this yard right now?
10. Which operational records are stale, inconsistent or require attention?

## Product principles

### 1. Capture events close to where they happen

Drivers and yard personnel should record operational events through simple mobile actions rather than relying on administrators to reconstruct events later.

### 2. Minimize driver input

Prefer:

- QR scan;
- one-tap confirmation;
- pre-filled assignment data;
- geofence-assisted arrival/departure;
- photo/document upload;

over manual typing.

### 3. History is first-class

Current state is important, but the system must preserve who/what/when for meaningful operational events.

### 4. Location is operational, not surveillance-first

The MVP should know when work reaches/leaves known operational locations. It does not require a live map of every driver's route unless later validated.

### 5. SaaS from the start

The product is intended for multiple transportation companies. Tenant isolation, configurable customer relationships and per-tenant master data must be part of the core model.

### 6. Customers are scoped users, not global operators

A transportation company's customer may have portal access to its own inventory, jobs and documents, but never to unrelated customer or tenant data.

## Primary product surfaces

### Web operations console

For administrators, dispatchers, shipping personnel and supervisors.

### Mobile driver application

For drivers performing jobs, hook/drop, arrivals/departures, evidence upload and issue reporting.

### Mobile/handheld yard workflow

Can initially be delivered through the same mobile app with role-specific UX.

### Customer portal

For approved customer shipping/receiving users.

## Explicit non-goals for MVP

The MVP is not intended to replace:

- full TMS/accounting;
- ELD/HOS compliance platforms;
- payroll systems;
- complete maintenance/fleet management systems;
- telematics providers;
- route-optimization engines;
- fuel-card systems.

Integrations may come later.

## Success metrics to define before production

Candidate metrics:

- percentage of active trailers with trustworthy current location;
- percentage of trailer movements captured without administrator correction;
- average time required for yard check;
- missing/unexpected trailer count per yard check;
- percentage of jobs with complete proof-of-delivery evidence;
- stale trailer count;
- average time spent searching for a trailer;
- on-time appointment performance.

Targets are not yet approved.
