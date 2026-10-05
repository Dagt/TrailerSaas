# Baseline Observability and Correlation

Issue #21 implements the technical baseline in ADR-015 (NFR-070 through NFR-073).
It adds no domain APIs, schema migration, business audit, identity or offline
workflow. Technical logs/traces/errors do not replace operational audit history.

## Backend Logs and Context

Spring Boot 4.1.1's native `logstash` console formatter produces one JSON object
per application log line on stdout. The startup banner is disabled. JVM/Maven
toolchain warnings are not application logs. No external encoder is installed.

| Field | Meaning |
| --- | --- |
| `@timestamp` | Native timestamp |
| `level`, `logger_name`, `message` | Native log severity, source and description |
| `requestId` | Validated/generated interaction ID, returned in X-Request-Id |
| `traceId` | Micrometer/OTel technical trace ID; requestId fallback if tracing is absent |
| `spanId` | Current technical span when tracing exists |
| `http.method` | HTTP method |
| `http.path` | Request URI, never query string |
| `http.status` | HTTP response status in completion log |
| `durationMs` | Dispatch duration in milliseconds in completion log |

`clientOperationId` and `jobId` are future fields, not emitted by this baseline.
No tenant/user/customer identifiers are invented. HTTP completion logs carry
status/correlation without copying exception text or bodies. Other framework
logs carry native fields and MDC when inside the observed request scope.

`X-Request-Id` accepts only 1-64 ASCII letters, digits, underscore or hyphen.
Missing/invalid values are replaced with a server UUID. A client ID is untrusted
diagnostic input, never authentication or authorization. Request dispatches reuse
the request attribute, return the header, and restore the outer MDC in finally,
including exceptions and async/error redispatches. Error dispatches retain the
original technical IDs even after the observation scope closes; a request emits
only one completion record. Async redispatch completion
uses its dispatch duration; end-to-end HTTP duration comes from Micrometer.

The correlation filter runs inside Spring's HTTP observation filter.
`requestId` describes this interaction and may originate from a client;
`traceId`/`spanId` belong to the real technical trace managed by Micrometer.
Do not use requestId as a fake OTel trace. The fallback is only for diagnostics
when no active tracing context exists.

## Traces, Metrics and Errors

The Boot-managed OpenTelemetry starter supplies Micrometer tracing and its OTel
bridge. Native HTTP observations produce real local trace context without a
remote collector. Trace export and OTLP log export are disabled explicitly.
Baggage is disabled: no arbitrary client baggage is copied into diagnostics.
Boot's default tracing sampling policy is retained. No telemetry credentials,
collector, metrics server, dashboards or alert routing are configured.

Actuator's MeterRegistry records standard JVM/application/HTTP meters, including
`http.server.requests`. No public metrics, environment, loggers or trace endpoint
is exposed. Exporters and domain/job metrics belong to later bounded issues.

Spring 7's central ErrorResponse interceptor only adds/updates `traceId`,
preserving approved problem types, titles, safe details and extensions such as
`code` and `errors`. Domain handlers must construct safe public ProblemDetails
and use an explicit order higher than the infrastructure fallback.
The infrastructure advice runs at `Ordered.LOWEST_PRECEDENCE`. Its shared MVC
exception path constructs a fresh generic problem rather than forwarding
untrusted framework bodies/reasons; unexpected exceptions and servlet error
dispatch use the same `safeInfrastructureProblem` factory.
These responses use `application/problem+json`, standard status/title/detail and `traceId`.
Unexpected exceptions return a generic 500. Messages do not copy exception
classes, SQL, local paths, input values, credentials or stack traces.
When tracing exists, the response traceId matches request log traceId; otherwise
it uses the safe requestId. Domain-specific codes/mappings remain deferred.
Controllers that deliberately fail exist only in test sources.

## Health

Only these Actuator endpoints remain public:

- `/actuator/health`: aggregate health.
- `/actuator/health/liveness`: process/application lifecycle, not DB availability.
- `/actuator/health/readiness`: readinessState plus required PostgreSQL `db` health.

Health components and details are never shown. PostgreSQL failure makes readiness
DOWN/503 while liveness remains UP/200, avoiding DB-triggered restart loops.
A dedicated Testcontainers fixture verifies this without stopping shared tests'
databases. Existing real PostgreSQL 18/Flyway/JPA/no-H2 tests remain unchanged.

## Optional Sentry Clients

Official SDKs: `@sentry/react` for Web and `sentry_flutter` for Mobile. Neither
initializes without a DSN. Web imports the SDK dynamically only when enabled,
keeping it outside the initial app chunk. Initialization is awaited before React rendering / runApp.
The existing app shells, navigation and SQLite behavior remain intact.

| Application | Optional DSN | Environment |
| --- | --- | --- |
| Web | `VITE_SENTRY_DSN` | `VITE_SENTRY_ENVIRONMENT`, otherwise VITE_APP_ENV |
| Mobile | `SENTRY_DSN` via Dart define | `SENTRY_ENVIRONMENT`, otherwise APP_ENV |

Use the ignored local templates described in the
[environment catalog](environment-configuration.md). All committed templates
have empty DSNs, so normal development and CI need no Sentry project.
Supply a reviewed HTTPS public ingestion DSN locally to enable; rebuild/restart
the client. Explicit environment mismatches fail at configuration validation.
Development stays the default even in an optimized/release build.
There are no real DSNs in the repository and tests mock SDK initialization.

Staging and Production must use isolated Sentry projects/configuration, not just
different labels on shared Production data. URL syntax validation cannot verify
project ownership. No SENTRY_AUTH_TOKEN, source-map upload, release automation
or backend Sentry Java integration is implemented. The standard SDK can retain
available app release/build/platform metadata; no release IDs are invented.

## Privacy Boundary

Allowed baseline fields are the backend table above and client event ID/time,
environment, SDK-provided release/platform, exception category and code stack
frames (source filename, function, line, column). URLs omit userinfo, queries
and fragments. Clients rebuild events from an allowlist rather than forwarding
arbitrary messages or trying to redact every possible secret string.

Never record/send Authorization, Firebase ID/admin tokens, cookies, DB/migration
passwords, R2 keys, Sentry administrative tokens, environment secrets,
request/response bodies, document/BOL/media contents, signed URL signatures,
SQLite contents, command payloads or location payloads.

Web SDK 11 uses explicit `dataCollection` opt-outs (equivalent to the former
`sendDefaultPii=false`): user info, headers, cookies, bodies, queries, variables
and source context lines are disabled. Only error handlers and deduplication
integrations are enabled. Breadcrumbs, extra/tags/user/context payloads,
attachments and arbitrary error messages are excluded; logs/metrics callbacks
drop events. No browser tracing, replay, session tracking or profiling is added.
React root error callbacks use the same sanitized SDK path.

Mobile explicitly sets `sendDefaultPii=false`. Breadcrumbs, sessions, native
crashes/ANRs/app hangs, performance tracing, screenshots, logs and metrics are
disabled. Native crash collection is deferred because it bypasses the Dart
beforeSend privacy boundary. No replay integration is enabled. BeforeSend drops
attachments and constructs a new event without request/user/context/command/
location/media data or arbitrary exception messages. Stack frame variables and
source context are not forwarded. The small injectable bootstrap callback allows
tests to exercise options and startup without platform initialization/network.

These controls cover the points owned by this issue, not universal string
redaction. Future diagnostic fields and integrations require explicit privacy
review. Distributed Web/Mobile config can be extracted and is never a secret store.

## Verification

Use the commands in [CI quality gates](ci.md), unchanged:

- Backend: `./mvnw -B -ntp clean verify` with working Docker.
- Web: `npm ci`, typecheck, lint, tests, build.
- Mobile: enforced lockfile install, Drift generation, format, analyze, tests.
- OpenAPI: locked install, validate, tests.
- Repository: `git diff --check` and manual secret/schema/dependency review.

Tests cover native JSON, valid/absent/unsafe IDs, MDC cleanup, safe ProblemDetails,
real trace/log correlation, synthetic Authorization/query privacy, MeterRegistry/
HTTP instrumentation and isolated PostgreSQL readiness loss. Client tests use
synthetic DSNs on `example.test`, not real ingestion endpoints.

If local Docker is unavailable, full backend verification must fail, not skip,
mock PostgreSQL or use H2. Run the non-Docker tests separately and obtain full
PostgreSQL verification from the required GitHub Actions Backend job.
Offline command metadata, job IDs, FCM/R2 diagnostics, alert thresholds and
business audit remain future work.

References: [Boot native logging](https://docs.spring.io/spring-boot/reference/features/logging.html),
[Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html),
[Spring ProblemDetails](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
