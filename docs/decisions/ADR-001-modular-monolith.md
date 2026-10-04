# ADR-001 — Modular Monolith Architecture

**Status:** Accepted  
**Date:** 2026-10-04

## Context

TrailerSaaS has multiple business areas—tenancy, customers, orders, trips, drivers, tractors, trailers, yard operations, documents, alerts and audit—but the initial scale and team do not justify distributed microservices.

The system needs strong transactional consistency across operational workflows, low operational overhead and clear module boundaries so it can evolve into a SaaS.

## Decision

Use a **modular monolith** for the backend.

Use package/module boundaries by business capability and Spring Modulith to help document, verify and test module boundaries where useful.

Candidate modules:

- identity / tenancy
- customers
- drivers
- tractors
- trailers
- orders
- trips / dispatch
- yard
- inspections
- documents
- notifications
- audit

Cross-module communication should prefer explicit application services and domain/application events rather than direct access to another module's internals.

## Rationale

- Strong transactions are valuable for hook/drop, assignment, location and state changes.
- Current design-partner scale does not require microservices.
- One deployable backend is simpler to develop, test and operate.
- Module boundaries keep future extraction possible if scaling or organization demands it.
- Spring Modulith is specifically intended to support domain-driven modular Spring Boot applications.

## Consequences

### Positive

- simpler deployment and local development;
- lower infrastructure cost;
- easier transactional consistency;
- clear business boundaries;
- easier for Codex and human developers to reason about the complete system.

### Negative / trade-offs

- requires discipline to prevent modules from becoming tightly coupled;
- one deployable unit means a backend deployment affects all modules;
- later service extraction, if ever needed, will require work.

## Rejected for MVP

- microservices;
- Kubernetes-based service architecture;
- event-sourcing-only architecture.

## Follow-up

- define exact module map;
- define allowed dependencies between modules;
- add module-boundary tests;
- decide how application events are persisted/handled.
