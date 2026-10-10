# Issue #42 — mobile identity and read cache

Firebase authenticates identity (ADR-007); TrailerSaaS/PostgreSQL remains authority for
membership, capability, linked active Driver and assigned work (ADR-011/012). Custom claims
are never read. SDK-managed Firebase sessions are restored; passwords/ID tokens are not
stored in Drift, configuration files or application logs. Auth/HTTP/connectivity boundaries
are injected in tests without Firebase network.

## Online flow and bounded API gap

Firebase User -> `/api/v1/me` -> ACTIVE eligible membership -> Tenant selection -> own Driver
-> downloaded assigned work. Multiple eligible Tenants require selection by name. `/me`
adds a fresh Tenant-scope `capabilities` summary from persisted role assignments, empty for
inactive memberships. It is a navigation hint; every endpoint still authorizes independently.
Catalog/mappings are unchanged. Road requires linked active Driver; Yard has no fabricated
Driver and exposes only its shell, no Yard workflows.

Accepted `/driver/me` and `/driver/me/trips` were absent at baseline. #42 implements their
minimum read subset: own id/name/active/version and currently assigned nonterminal Trips,
Stops and relevant planned Trailer context. Fresh ACTIVE membership and TRIP_VIEW_ASSIGNED
are mandatory. No User/Driver ID query selector, generic directory, administrative histories
or actor identities. Cursor/limit only, default50/max200, createdAt DESC/id DESC. Every page
reauthorizes; active/date remain unsupported. Queries use normal Tenant RLS, repeatable-read
and fixed batch label/resource lookups. Planning expectation never asserts physical coupling.
Richer Driver home, Shift and singular active Trip calculation remain future work.

The GET client force-refreshes ID token at most once after 401. Repeated 401/403/404 clears
operational context and cannot fall back to cached permissions. 5xx stays safe with support
trace. Network failure differs from explicit denial. There is no mutation/replay client.

## SQLite migration and isolation

Explicit empty-v1 -> v2 migration creates exactly local_session_context, cached_trips,
cached_stops and cached_trailers. Session binds Firebase UID to internal User/Tenant, cached
non-authoritative capability hints, own Driver and last validated refresh. Every resource
primary key includes User/Tenant/id; projections retain only needed server versions/status,
destination snapshots, schedules/instructions, Order references and planned asset context.
No full server replication. No commands/media/Yard Check tables in this issue.

Successful complete download replaces its namespace in one SQLite transaction and removes
reassigned work/assets. Failure rolls back the whole replacement. Cleanup/writes serialize;
session generation guards discard late old-account/Tenant responses. Logout/account switch
hides old data immediately and clears read caches. Tenant switch clears before validating and
showing the new namespace. App-private OS storage protects files; encrypted-vault or
compromised-device protection is not claimed. No token/password columns exist.

## Offline continuity

First-time offline login/onboarding is prohibited. Only a Firebase-restored identity with a
previously validated matching cache may display downloaded work. The UI labels it cached,
shows last refresh and never claims current permission. New Tenant selection requires online
validation. Connectivity reports network link, not API reachability. Reconnect/explicit refresh
revalidates membership, Driver and work. No invented universal offline authorization expiry.

## Deferred and native setup

#46 owns local command queue, dependencies, replay/backoff, sync conflicts and reauthorization
on replay. Read-cache eviction must not erase future pending commands/conflicts. #43/#44
workflows, #54 media, Yard, QR/camera/location, Documents, push and Audit remain deferred.
No fake data or execution controls. No PostgreSQL migration; V1–V17 unchanged.

Public Firebase options require an operator-owned Android/iOS app per environment with
email/password enabled. Host tests do not prove native integration. Android device build
requires Android SDK; iOS requires macOS/Xcode. See mobile README for configuration.
