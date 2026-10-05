# TrailerSaaS

Working repository for the discovery, product definition, architecture and implementation of a SaaS platform for trailer / dry-van operational control.

> **Status:** Pre-implementation specification complete. Accepted architecture, state machines, UX, schema, API and offline-sync contracts are in place; the implementation backlog is ready.

## Product goal

Build a multi-tenant platform for U.S. trucking / transportation companies that need reliable control of:

- trailers / boxes and their current location;
- load and delivery status;
- driver, tractor and trailer relationships;
- dispatch and work assignments;
- hook / drop events;
- arrival / departure events;
- proof of delivery and documents;
- yard checks and inventory reconciliation;
- alerts, auditability and historical movements;
- customer visibility without exposing other tenants' data.

The initial design partner currently operates more than 100 movements per day with approximately 35 drivers, 33 tractors and 150 trailers.

## Repository purpose

This repository will be the **single source of truth** for the project before and during implementation.

The objective is that, when development begins, Codex or any engineer can understand:

1. what problem is being solved;
2. how the current operation works;
3. who the users are;
4. what the business rules are;
5. what is included in the MVP;
6. what is explicitly out of scope;
7. which questions remain unresolved;
8. which architecture decisions have been approved.

## Documentation

See [docs/README.md](docs/README.md) for the documentation map and [docs/backlog/README.md](docs/backlog/README.md) for the implementation backlog.

## Backend local development

The Issue #15 scaffold lives in `backend/`: one Maven project and one deployable
Spring Boot application, following ADR-001/002/006. Install JDK 25 and make
`java` available on PATH (or set `JAVA_HOME` to the JDK). The Maven Wrapper pins
Maven 3.9.11; its first run requires network access to download Maven/dependencies.
Issue #19 adds PostgreSQL 18, Flyway and JPA. Configure the separate runtime and
migration credentials as documented in [backend/README.md](backend/README.md)
before starting the app. Tests provision their own PostgreSQL with Testcontainers
and require Docker; they do not use those local credentials.

From the repository root, on Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

On macOS/Linux:

```sh
cd backend
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile listens on `127.0.0.1:8080`. Stop with Ctrl+C.
Health is available at `http://127.0.0.1:8080/actuator/health`, with
`/liveness` and `/readiness` subpaths. Only health is exposed over HTTP;
responses omit component details. Overall health includes the standard datasource
check; liveness/readiness still reflect application lifecycle only. Local startup
requires PostgreSQL and the documented database variables, not Production secrets.

`src/main/java/com/trailersaas` contains the application entry point and twelve
module packages declared through `package-info.java`: `identity`, `customers`,
`drivers`, `tractors`, `trailers`, `orders`, `trips`, `yard`, `inspections`,
`documents`, `notifications`, and `audit`. These follow ADR-001: identity includes
tenancy, trips includes dispatch, and notifications reserves alerts. Master data
uses the relevant customer/equipment packages rather than a shared catch-all module.
Module root packages form the future public APIs; nested packages are internal
by Spring Modulith convention. Explicit services/events and allowed dependency
lists can be added when later issues introduce actual interactions.

`src/test/java/com/trailersaas` provides JUnit tests for module discovery/boundary
verification and local-profile startup with real HTTP health/probe requests on a
random port. Future module tests belong under their matching capability package.
The scaffold contains no business features or authentication. Persistence has only
an infrastructure baseline: Flyway history, no domain tables/entities/repositories.

## Web local development

The Issue #16 foundation lives in `web/`: React 19, TypeScript, Vite 8,
TanStack Query, React Hook Form and Zod, following ADR-004. Use Node.js 24
(or supported Node.js 22.13+) and npm:

```sh
cd web
npm ci
npm run dev
```

Vite prints the local URL (normally `http://127.0.0.1:5173`).
For all checks run `npm run typecheck`, `npm run lint`, `npm test` and
`npm run build`. See [web/README.md](web/README.md) for environment setup,
PowerShell equivalents, the route structure and deployment notes.
The scaffold has no authentication, business screens or API calls.

## Mobile local development

The Issue #17 scaffold lives in `mobile/`: one Flutter Android/iOS application
with Road/Yard placeholders and an empty Drift/SQLite schema, following
ADR-005/012/014. Use Flutter 3.47.6 / Dart 3.13.5:

```sh
cd mobile
flutter pub get --enforce-lockfile
flutter analyze --fatal-infos
flutter test --reporter expanded
flutter run -d <device-id> --dart-define=APP_ENV=development
```

See [mobile/README.md](mobile/README.md) for code generation, API environment,
Android/iOS prerequisites, build commands and current validation limitations.
Platform capabilities are interfaces only; no authentication, operational
workflows, API calls, offline queues or GPS tracking are implemented.

## CI quality gates

Issue #18 adds GitHub Actions checks for PRs to `main` and pushes to `main`:
Backend, Web, Mobile and OpenAPI. They require no Production secrets or external
services and perform no deployments. See [CI quality gates](docs/development/ci.md)
for local commands, tool versions, safe dependency caches and validation scope.
Native Android/iOS builds and release signing are deferred.

Issue #20 defines safe Development defaults and explicit Staging/Production
selection. See the [environment catalog](docs/development/environment-configuration.md)
for each application's variables, public/secret classifications, ignored local
templates and reserved future integrations. No real providers or secrets are
configured, and normal development requires no Production access.

## Current phase

### Current implementation readiness
- [x] Discovery complete
- [x] MVP scope / business rules defined
- [x] State machines accepted
- [x] UX/User flows accepted
- [x] Architecture ADR-001 through ADR-022 accepted
- [x] PostgreSQL schema v1.0 accepted
- [x] API contracts v0.1 accepted
- [x] Mobile offline sync protocol accepted
- [x] GitHub MVP backlog created (#1–#66)
- [ ] Repository scaffold
- [ ] MVP implementation
- [ ] Production readiness

## Working principles

- Do not reproduce the current Google Sheet as the domain model.
- Model business events and derive current state from those events.
- Minimize manual driver input.
- Prefer QR + lightweight location automation over continuous GPS tracking for the MVP unless discovery proves otherwise.
- Keep customer and tenant data strictly isolated.
- Preserve an auditable history of operational changes.
- Separate confirmed requirements from assumptions and future ideas.

## Language

Product and technical documentation may be written in English when useful for implementation, while discovery notes may remain in Spanish. Domain terms used by operators (BOL, hook, drop, yard check, dispatch, shipping/receiving, etc.) should be preserved where they improve clarity.
