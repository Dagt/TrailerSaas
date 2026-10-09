# TrailerSaaS Web

Issue #30 extends the #16 foundation with Firebase email/password authentication,
Tenant Admin master-data forms and Settings, using ADR-004/007/011/018/020.
The backend remains the sole authority for authorization and concurrency.

## Local development

Use Node.js 22.13+ (22.x) or 24+ and npm. Node.js 24 is recommended.
The committed lockfile makes installation reproducible.

From the repository root:

```sh
cd web
npm ci
cp .env.example .env.local
npm run dev
```

On Windows PowerShell use `Copy-Item .env.example .env.local` instead of `cp`
and `npm.cmd` if execution policy prevents running `npm.ps1`.

Vite binds to loopback. Use the URL it prints (normally
`http://127.0.0.1:5173`); if that port is busy Vite selects the next available port.
Stop with Ctrl+C. Sign-in requires the public Firebase configuration below;
Admin data requires the backend and a persisted ACTIVE TENANT_ADMIN membership.
An unconfigured build displays sign-in unavailable and makes no Firebase requests.

## Checks

These commands are non-interactive and run in the Web job of
[CI quality gates](../docs/development/ci.md):

```sh
npm run typecheck
npm run lint
npm test
npm run build
```

`npm test` runs once; `npm run test:watch` is for interactive development.
Vitest and Testing Library cover direct routes, navigation, unknown routes,
query-cache lifetime, environment validation, injected Firebase/HTTP boundaries,
Tenant selection/isolation, CRUD, QR management/labels and conflict/error UX.
Tests contact no Firebase/Google service. PostgreSQL integration is the Backend job.
`npm run build` typechecks before producing `dist/`; `npm run preview` serves
that build locally.

## Environment / API boundary

`VITE_APP_ENV` explicitly selects `development` (default), `staging` or
`production`, independently of Vite mode. The default API is loopback:
`http://127.0.0.1:8080/api/v1`. Staging/Production require an explicit absolute
HTTPS `VITE_API_BASE_URL`; missing or invalid settings never silently fall back.
URL credentials, query strings and fragments are rejected. Zod validates during
Vite startup/build and client initialization. There are no same-origin fallbacks.

`.env.example`, `.env.staging.example` and `.env.production.example` contain only
public placeholders. Vite loads the corresponding ignored local files, or process
variables (which take precedence). For an explicitly configured staging build:

```sh
VITE_APP_ENV=staging VITE_API_BASE_URL=https://api.staging.example.test/api/v1 npm run build -- --mode staging
```

PowerShell equivalent:

```powershell
$env:VITE_APP_ENV = "staging"
$env:VITE_API_BASE_URL = "https://api.staging.example.test/api/v1"
npm.cmd run build -- --mode staging
Remove-Item Env:VITE_APP_ENV
Remove-Item Env:VITE_API_BASE_URL
```

Never put credentials or secrets in `VITE_*` variables: Vite embeds them in
browser assets. `.env*` files are ignored except reviewed examples. Restart/rebuild
after changes. A normal optimized build still targets Development unless the
application environment is selected explicitly. Configure all four public values
to enable Firebase sign-in: VITE_FIREBASE_API_KEY, VITE_FIREBASE_AUTH_DOMAIN,
VITE_FIREBASE_PROJECT_ID, VITE_FIREBASE_APP_ID. All absent leaves sign-in disabled;
partial configuration fails validation. Use separate environment projects and
enable the email/password provider for already provisioned accounts. These values
identify the Web application, never Firebase Admin/service-account credentials.
No signup, password reset, MFA, social login or invitation UI is implemented.
Issue #21 optionally enables Sentry using `VITE_SENTRY_DSN`; empty/absent disables
the SDK. `VITE_SENTRY_ENVIRONMENT` must match the selected app environment.
See [observability/privacy](../docs/development/observability.md) and the
[central environment catalog](../docs/development/environment-configuration.md)
for Production selection, templates, variable classifications and resource isolation.

`src/shared/auth` isolates the official Firebase Web SDK (13.0.0), lazy initialized,
behind an injectable boundary: session observation, sign-in, ID token and sign-out.
No custom claims are read. The typed API client retrieves the current ID token,
sends bearer/Accept/X-Request-Id (crypto.randomUUID), Content-Type only for bodies,
and handles JSON/204/Problem Details without cookies or request/response logging.
401 offers explicit session refresh/sign-in; mutations are never automatically
replayed. Sentry keeps its existing allowlisted privacy boundary.

Backend WEB_ALLOWED_ORIGINS must allow the exact browser origin. Local backend
defaults to http://127.0.0.1:5173 and http://localhost:5173; other ports require an
explicit override. Staging/Production need an explicit HTTPS allowlist, no wildcard
or credentialed cookies. See [backend CORS](../backend/README.md#tenant-settings-and-web-cors-30).

## Structure

```text
src/
  app/                     providers, shared shell, route table, fallback
  areas/
    tenant-admin/          nested Admin routes, DTOs, forms, QR preview
    transport-operations/  /operations
    customer-portal/       /customer
  shared/
    api/                   typed bearer API client / Problem Details
    auth/                  official Firebase adapter and injectable session boundary
    ui/                    small accessible local components
    config/                typed, validated environment
    observability/         optional Sentry initialization and privacy boundary
  test/                    Testing Library setup
  main.tsx                 browser entry point
  styles.css               minimal layout, not the final design system
```

Admin routes: /admin, /admin/customers[/customerId], /admin/locations (Physical
Sites), /admin/sites/:siteId (Site + Locations), /admin/drivers[/driverId],
/admin/tractors[/tractorId], /admin/trailers[/trailerId], /admin/settings.
Operations is implemented by #37 as described below. Customer Portal remains a placeholder.
Local semantic HTML/CSS components provide labelled forms, keyboard focus,
native confirmation dialogs, responsive tables, status text and reduced motion.
TanStack Query owns server state; React Hook Form and Zod validate inputs without
changing display operational numbers or replacing backend validation.

Session restore/reload calls /me. Only ACTIVE memberships with TENANT-scoped
TENANT_ADMIN appear in the Admin selector; one selects automatically, multiple
require an explicit choice, none show unavailable access. Roles come from DB
through /me, never Firebase custom claims/local storage. This is UI presentation,
not backend authorization. Tenant query keys include Tenant/resource/ID/filters;
switching cancels/removes old Tenant queries and resets the workspace. Sign-out
clears the cache. Mutations invalidate/refetch, without optimistic writes.

Creates, metadata edits and activate/deactivate use existing APIs. Deactivate
requires confirmation and preserves history; reactivation is explicit. No master
data hard delete is exposed. STALE_VERSION disables saving, explains the conflict
and requires **Reload latest** before review/save; no silent version retry.
Known validation fields appear on inputs; generic conflicts/access/404/safe 5xx
show no foreign metadata, with traceId as a support reference and read retry.

A CREATE with a 5xx or lost response has an uncertain outcome. Keep the form and
error, disable resubmission of that creation, and use **Check latest records** to
refetch the list with GET only. Review the list before opening a new create form;
there is no POST replay or invented Idempotency-Key. Versioned PATCH retains
explicit retry; mutations never auto-retry.
New Sites require an explicitly entered IANA timezone, initially empty. New
Operational Locations require an explicit type selection, initially a placeholder.
Neither UTC/YARD nor Tenant settings supply operational form defaults.
Lists show available Site address/timezone/full-geofence configuration, Driver
license/certificate/account state, and Trailer designated Customer/states/last seen.
Customer names come from the existing catalog, without Driver/Location N+1 queries.

Deliberate API/UX gaps: no complete Customer-Site GET directory exists; associate
explicitly and remove only server-confirmed pairs during the visit. No account
directory/picker exists; Drivers show linked/unlinked and allow unlink only.
Site detail manages real neutral/Customer Operational Locations; addresses are
never duplicated by Customer. Current/last Trailer Location IDs have no direct
detail GET today; no fake names or extra endpoint is invented.

Trailer create may designate a Customer. Generic edit **never** sends
designatedCustomerId or physical-state fields. The disabled designation entry point
waits for #40's audited reassignment command. Location/load/serviceability are
read-only. All seven accepted Trailer filters are available; no coupling filter.
Only stale threshold (positive integer) and nullable IANA default timezone appear
in Settings. No retention/billing/users/access/invitations/audit UI in Admin.

## QR management and printing

Issue requires no active QR; revoke is confirmed and keeps history. Reissue is
explicit revoke then issue, never combined. Only an active QR can be printed.
Configure VITE_QR_PUBLIC_BASE_URL: absolute HTTPS in Staging/Production; Development
may use HTTP only on loopback. Missing configuration disables print with an
explanation; malformed configured URLs fail environment validation. The QR payload
is `<configured-base>/t/<publicToken>`, never the internal API URL.
The qrcode 1.5.4 library generates a client-side preview with a quiet zone. Browser
print/CSS prints only the QR and the original operational Trailer number, separately.
No Tenant/Customer/Trailer UUID is printed. No fixed stock dimensions, PDF/printer
integration, production host, public /t fallback or universal links are introduced.
QR is a public identifier, not authorization. Neither bearer nor QR/password
values are placed in application logs or Problem UX.

The npm override for @grpc/grpc-js >=1.13.6 resolves an advisory in Firebase's
unused Node/Firestore transitive dependency; Web imports only firebase/app/auth.

Production hosting must rewrite non-asset SPA paths such as `/operations`
to `index.html` to support deep links. No deployments or frontend authorization
shortcuts are included. There is no large component framework or full design system.


## Transport Operations (#37)

Operations uses the same Firebase session and `/me` directory; ACTIVE Tenant-wide
TRANSPORT_OPERATIONS or TENANT_ADMIN memberships select the workspace for UX only.
Backend authorization always remains authoritative. Routes: `/operations`,
`/operations/orders`, `/operations/trips`, `/operations/trips/new`,
`/operations/trips/:tripId`. Navigation is Dashboard/Orders/Trips.

Dashboard calls the bounded current Dispatch Board projection, not event replay.
Orders defaults to unplanned work and uses real keyset pagination; Trips also uses
keysets. The four-section plan flow preserves independent requested destinations,
frozen ad-hoc snapshots and explicit BASE_RETURN. Active resources are selected by
human labels, never manual UUID entry. ISO appointments require an explicit timezone.
Trip detail displays current assignments, Stops, linked Orders and persisted histories.
State-aware assignment/substitution/reroute/cancellation dialogs send explicit requests
and reconcile authoritative responses; no optimistic state-machine changes or mutation
retries. Stale commands require Reload latest and another review. Uncertain creates
preserve the draft and require read reconciliation, never a second blind POST.

Operations queries are Tenant-namespaced; switching cancels/removes old queries,
resets the workspace and returns to Dashboard. Identity changes/sign-out clear cache.
No sensitive localStorage or Firebase-claim authorization. Existing Admin remains intact.
Semantic HTML, captions, labelled fields, keyboard/native dialogs, visible focus and
responsive dense tables reuse the small local component set; no new UI dependencies.

Manual substitution decisions represent an operationally known discrepancy until
Driver scan execution exists. Expected Trailer never claims physical coupling/current
location. There are no status editors, fake records, execution bypass buttons, maps,
GPS, Alerts, POD, Hook/Drop, mobile flows, Customer Portal or generic Audit. See
[the exact API/UI boundary](../docs/api/orders-dispatch.md#issue-37-implementation-boundary).
