# ADR-002 — Backend Platform: Java 25 + Spring Boot 4

**Status:** Proposed  
**Date:** 2026-10-04

## Context

The backend must support:

- transactional operational workflows;
- REST API;
- role/tenant authorization;
- PostgreSQL persistence;
- file-upload orchestration;
- background jobs/alerts;
- offline-mobile synchronization;
- audit/event history.

The project owner already has Java backend experience, which materially improves maintainability and code review.

## Decision

Use:

- **Java 25 LTS**
- **Spring Boot 4.1.x**
- **Spring Framework 7**
- **Spring Modulith 2.1.x** where it provides useful module-boundary/event tooling
- Maven as the default build tool unless implementation work reveals a reason to choose Gradle

## Rationale

As of the architecture decision date:

- Java 25 is the current LTS Java release.
- Spring Boot 4.1 supports Java 25.
- Spring Boot provides a mature ecosystem for web APIs, validation, security, persistence, scheduling, observability and testing.
- Java is familiar to the project owner.

## API style

Use REST/JSON for the initial product.

Use OpenAPI-generated/maintained API documentation.

Do not introduce GraphQL unless a concrete product need appears.

## Consequences

### Positive

- mature ecosystem;
- strong typing;
- excellent transactional support;
- good tooling and test ecosystem;
- maintainable by the project owner;
- good fit for a modular monolith.

### Negative / trade-offs

- higher memory footprint than some smaller runtimes;
- careful DTO/entity boundaries are required;
- mobile offline conflict logic still needs explicit design.

## Follow-up

- choose persistence access strategy (Spring Data JPA, jOOQ or hybrid);
- define Spring Security/auth provider integration;
- define API error and idempotency conventions.
