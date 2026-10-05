# TrailerSaaS Web

Issue #16 implements only the web foundation accepted in ADR-004. Business
screens, authentication and API transport are deferred to later issues.

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
Stop with Ctrl+C. The scaffold does not require a running backend.

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
query-cache lifetime, environment validation and the API placeholder.
`npm run build` typechecks before producing `dist/`; `npm run preview` serves
that build locally.

## Environment / API boundary

`VITE_API_BASE_URL` is public build-time configuration validated with Zod.
Use an absolute HTTP(S) API URL including `/api/v1`, for example
`http://127.0.0.1:8080/api/v1` locally. Without an override, the value is
`/api/v1` for a same-origin deployment. A later deployment must route that
prefix to the backend or supply its explicit URL; this scaffold adds no proxy.
Vite loads `.env.local` and mode-specific files; restart the dev server after
changing them and rebuild for deployment. For example:

```sh
VITE_API_BASE_URL=https://api.example.test/api/v1 npm run build
```

PowerShell equivalent:

```powershell
$env:VITE_API_BASE_URL = "https://api.example.test/api/v1"
npm.cmd run build
Remove-Item Env:VITE_API_BASE_URL
```

Never put credentials or secrets in `VITE_*` variables: Vite embeds them in
browser assets. `.env*` files are ignored except the example.

`src/shared/api/client.ts` exposes immutable base-URL configuration only.
It performs no requests and does not implement token handling, endpoints,
tenant selection or authorization.

## Structure

```text
src/
  app/                     providers, shared shell, route table, fallback
  areas/
    tenant-admin/          /admin
    transport-operations/  /operations
    customer-portal/       /customer
  shared/
    api/                   API client placeholder
    config/                typed, validated environment
  test/                    Testing Library setup
  main.tsx                 browser entry point
  styles.css               minimal layout, not the final design system
```

The root route and each area render only a heading. All areas share one
TanStack Query provider/cache. React Hook Form, its Zod resolver and Zod
are installed for future forms; no business form is introduced.

React Router 7 provides the SPA routing shell. Route names are scaffold
conventions, not a new API or authorization contract. The three links are
unrestricted in this empty scaffold; role/capability-aware navigation belongs
to the identity issues. UI visibility must never replace server authorization
(ADR-011).

Production hosting must rewrite non-asset SPA paths such as `/operations`
to `index.html` to support deep links. No Firebase, business records, API calls,
component library or final visual design is included.
