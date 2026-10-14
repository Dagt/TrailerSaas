# TrailerSaaS Mobile

## Issue #49 — Yard discrepancy, load correction and evidenced OOS


## #49 typed corrective graph and evidence recovery

YARD_MOVE, LOCATION_CORRECTION, LOAD_STATE_CHANGE and OOS share the existing
TRAILER resource lane, CommandStore, ReplayWorker, immutable request/result and
UUID/Idempotency-Key. The next physical intent references the previous physical
operation, never an invented version. It materializes only after a verified
same-User/Tenant/Trailer ACK result. OOS additionally waits for every own bound
photo to be FINALIZED; document IDs are resolved once before sending. A frozen
request never recalculates coordinates/location/version/photos during replay.

DISCREPANCY_REPORT is append-only observation with its historical downloaded
location/version; it does not increment Trailer version and cannot be a physical
predecessor. Its observation resource lane can sync even after a physical conflict.
Existing failed physical parents continue blocking old descendants; unrelated
Trailers continue normally. No operation is converted into another type.

Explicit conflict review first downloads current authorized Trailer state. Failed
reload remains closed. Only definitive failed same-Trailer Yard work may receive
an explicit new root action with a new UUID/version, recorded in immutable local
yard_conflict_reviews. Original requests/UUIDs/failed states remain preserved,
covered old retries are prohibited and old descendants never automatically run.
Uncertain queued/sending/retryable work cannot be replaced. No false ACK is forged.

Damage drafts/capture metadata persist before the native camera and survive restart.
Photos use existing private files/hash, LocalMedia, R2 processor and finalized
media edges, not a second queue/worker. Late camera results stay in their original
User/Tenant namespace; current account permission never transfers. Missing bytes,
denials, upload retries and partial sets retain draft/evidence for explicit review.
Network 408/429 Retry-After/5xx, 401 refresh, 403/404/409, lost ACK and two workers
retain the original same-key frozen request. Only a real server receipt is success.

## #49 deployed schema: Drift v8

Version 8 preserves the one v7 command/media graph and adds nullable immutable
local_media.trailer_id for DAMAGE_PHOTO without Trip. Three narrow metadata stores:
yard_drafts (owner, selected Site/Trailer, immutable observed version/ref/time,
comment, retained photo IDs, eventual command ID), yard_captures (persisted camera
ownership and PENDING/CAPTURED/FAILED/CANCELLED outcome), yard_conflict_reviews
(immutable old-failed/new-action links and reviewed authorized projection/version).
They are not queues. Existing local_commands/materialized_requests/command_results/
command_media_dependencies remain the only durable operation graph.

Scope/FK/content/queued-draft/late-camera and immutable review guards apply in
SQLite. One native camera acquisition across Inspection/Road/Yard contexts is
allowed. v7→v8 adds metadata without rebuilding commands/results/requests. A
frozen actual published v7 DDL fixture verifies migration and retained ACK bytes;
earlier v1–v6 migrations retain their historical constraints and data. Fresh schema
has 21 tables; all upgrades, FK checks, namespace and ledger guards are tested.
Read caches may be cleared on logout; pending commands/drafts/private bytes are
retained in their original owner namespace. There is no GPS/history store.

### #49 implemented review and damage UX

Scan → recorded Trailer → explicit action. Location uses current recorded value,
authorized destination, reason and Confirm Correction. Load State shows current
and new value/reason and preserves other dimensions. Without correction grant,
Report Discrepancy is available and explicitly creates no state change.

Damage uses the existing injected NativeInspectionCamera/ImagePicker: real image
bytes are privately copied/hash-verified, comment and all photo IDs retained before
explicit Confirm Mark OOS. Not Yet preserves drafts/photos and sends no OOS.
Upload/finalization is independently visible and never serviceability success.
Saved local, waiting evidence, queued/sending, server ACK and conflicts are distinct.
There is no Return-to-Service shortcut or new capability grant. Forms close on
User/Tenant/Driver/Site/Trailer/version/mode changes; double confirmation is fenced.

Conflict handling retains original commands and descendants. A fresh authorized
read and explicit review may create a new action; failed reload or uncertain ACK
does not permit rebasing. Old damaged evidence can be explicitly retained in a new
reviewed draft without changing its file/hash/identity. Physical Android/iOS camera,
permission/reduced access/background/restart/lost-data QA remains #65; injected
camera/Linux widget tests are not physical-device QA.

## Issue #48 — real Yard Mode

SessionView provides Yard-only entry and Road/Yard switching with the existing
worker. Download authorized Sites/Locations, reuse NativeQrScanner and actual QR
resolution, read minimal Yard Trailer state, select destination and consciously
confirm. Recorded/pending/planned/ACK states remain distinct. No fake Driver,
Tractor, Shift, correction, GPS or additional native permission. Backend requires
ACTIVE Tenant/membership and YARD_MOVE_EXECUTE; cached context grants no authority.

Drift v7 preserves the existing command/media/results/request graph and adds a
small User/Tenant Yard-context cache. Typed YARD_MOVE uses the same worker and
ledger. Consecutive offline moves wait for the first verified ACK to resolve the
second version; source/UUID/frozen request remain immutable. Parent conflicts
block descendants while independent Trailers continue. QR/confirmation routes
close on owner changes; explicit denial cannot restore cached Yard permission.
Correction/OOS UI is implemented in #49; #50 Backend and #51 mobile Yard Checks are implemented. Physical Android/iOS camera,
permission/lifecycle/process and release QA remains #65.

See [contracts, acceptance, offline recovery and tests](../docs/mobile/issue-48-yard-move.md).

## Issue #47 foreground arrival assistance

Road Mode offers a deliberate Check arrival location for the next authenticated
Stop's live Site. Geolocator 14.0.1 with locked compatible Android/iOS adapters
is behind LocationAssistancePort; [ADR-024](../docs/decisions/ADR-024-foreground-arrival-location-adapter.md)
documents the Linux dependency conflict avoided without downgrading connectivity.
Android uses COARSE/FINE only; iOS uses WhenInUse only. No background grant,
tracking loop, map or GPS history. One first-reading observer times out at 10 s;
precise readings up to 30 s old need accuracy <= radius/2 and distance+accuracy
inside the explicitly configured radius. Approximate/denied/disabled GPS falls
back to the existing manual arrival control.

Confirm Arrival consumes a valid suggestion and materializes GEOFENCE_CONFIRMED
through the same durable command/replay lane; Not Yet suppresses that context.
No Stop status changes before ACK. Driver Updates invalidates obsolete contexts
before download completes and refreshes live own work after commit. Offline/lost
ACK/reopen retains the original UUID/body; no automatic conversion to MANUAL.
Raw readings are never persisted; #47 left Drift v6 unchanged, and #48 evolves the
schema without changing this Arrival/privacy lane. See the [acceptance,
security and native QA matrix](../docs/mobile/issue-47-geofence-assisted-arrival.md).

Physical Android/iOS permission, reduced-accuracy, revocation, lifecycle/process
restart and release-build verification remain #65 work. Current automated
adapter/widget/SQLite tests are not physical-device evidence.

## Media foundation — Issue #54 (extended by #43/#45 below)

Drift v4 now persists private media and namespaced command dependencies alongside
the #46 command queue. The shared foreground/reconnect worker initializes Documents,
streams a direct R2 PUT without Firebase headers, confirms finalization with a stable
UUID and freezes dependent HTTP requests before sending. Road's sync panel shows
local/uploading/finalized/review states even without assigned Trips. The #43/#45
sections below describe the implemented Shift, inspection, POD/Fuel/exception and
camera consumers. See [media queue, recovery and security](../docs/mobile/issue-54-media-queue.md).

Use the same locked SDK and dependencies; crypto 3.0.7 was already locked transitively
and is now direct for streaming SHA-256. Files stay in application-support storage,
with UUID/User/Tenant paths. Android backup is disabled. Native encryption, iOS backup
and device lifecycle validation remain deployment checks; no custom encryption keys.
Cleanup requires server finalization, all dependent ACKs, explicit offline-use release
and a configurable seven-day default grace. No remote retention/deletion is added.

The historical implementation notes below remain useful for their original issues.

## Session implementation — Issue #42

Firebase sign-in/session restore, `/me`, Tenant selection, own Driver/work reads and persistent
namespaced Drift caches now replace the unauthenticated shell. Road is read-only downloaded
work; Yard is an authorized shell. No execution/replay. See the
[session/cache boundary](../docs/mobile/issue-42-session-cache.md).

Use the locked SDK/dependencies. New packages are Firebase Core/Auth, HTTP and connectivity;
unrelated scaffold dependencies remain locked. Configure ignored environment JSON with public
FIREBASE_ENVIRONMENT (must equal APP_ENV), FIREBASE_API_KEY, FIREBASE_PROJECT_ID,
FIREBASE_APP_ID, FIREBASE_MESSAGING_SENDER_ID and optional FIREBASE_IOS_BUNDLE_ID.
APP_ID must identify the registered Android/iOS app being built and match its package/bundle.
Register apps in the correct Firebase project and enable email/password. SDK initialization
uses explicit FirebaseOptions; no Admin credentials. Empty templates fail closed.
See [official Firebase Flutter Authentication setup](https://firebase.google.com/docs/auth/flutter/start).

```sh
flutter run --dart-define-from-file=config/development.json
flutter build apk --dart-define-from-file=config/staging.json
```

Use distinct Firebase/API environments; staging/production require HTTPS. Android main
manifest grants Internet, debug manifest permits cleartext only for local development. Use
HTTPS for iOS development; no blanket ATS bypass is shipped. Firebase SDK manages its own
native session storage; cache holds no password/bearer token. SQLite v2 explicitly upgrades
the empty v1; injected tests use real SQLite without Firebase network. Native validation
needs configured Android SDK/devices or macOS/Xcode; host tests do not replace it.

## Historical scaffold baseline (#17)

The remaining sections record the original scaffold and its original validation, rather
than the current authenticated #42 implementation.

Issue #17 supplies one Flutter app for Android and iOS, following
ADR-005/012/014. The two modes are navigable placeholders only.

## Toolchain and commands

Use Flutter stable 3.47.6 / Dart 3.13.5, recorded in `.flutter-version`.
The app lockfile and generated Drift code are committed.

From the repository root:

```sh
cd mobile
flutter pub get --enforce-lockfile
dart run build_runner build
dart format --output=none --set-exit-if-changed lib test
flutter analyze --fatal-infos
flutter test --reporter expanded
flutter run -d <device-id> --dart-define=APP_ENV=development
```

Use `flutter devices` to select a device. Android requires the Android SDK,
its accepted licenses and a device/emulator. iOS requires macOS and Xcode.
Stop the development run with `q`. A running API is not required by this scaffold.
These analyze/test commands are non-interactive and run in the Mobile job of
[CI quality gates](../docs/development/ci.md). Native builds remain deferred.

Build commands on a suitably configured host:

```sh
flutter build apk --debug --dart-define=APP_ENV=development
# macOS/Xcode only; unsigned validation build:
flutter build ios --no-codesign --dart-define=APP_ENV=development
```

Android keeps Flutter's generated debug signing configuration, including its
template release fallback. Production signing and deployment are not implemented.
The app IDs are the generated `com.trailersaas.trailersaas_mobile` identifiers.

## Configuration

Compile-time public configuration uses `--dart-define`:

- `APP_ENV`: `development` (default), `staging`, or `production`.
- `API_BASE_URL`: an absolute HTTP(S) URL including the accepted `/api/v1` prefix.

Development defaults to `http://localhost:8080/api/v1`. On an Android emulator,
the host computer is usually reached through `10.0.2.2`, so use:

```sh
flutter run -d <android-device-id> --dart-define=APP_ENV=development --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
```

On a physical device supply a reachable development-host URL instead.
Staging and production require an explicit HTTPS API URL, with no fallback:

```sh
flutter build apk --dart-define=APP_ENV=staging --dart-define=API_BASE_URL=https://api.staging.example.test/api/v1
```

Configuration rejects unknown environments and invalid URLs, embedded
credentials, query strings and fragments. Define values are compiled into the
app: never use them for tokens or secrets. No HTTP client or requests exist yet.
Native HTTP/security policy remains at the Flutter template defaults.

Issue #20 also provides public-only `config/development.example.json`,
`config/staging.example.json` and `config/production.example.json`. Copy the
matching example to an ignored `config/<environment>.json`, replace its public
URL and pass `--dart-define-from-file=config/<environment>.json` explicitly.
Flutter does not automatically load these or `.env` files. Production selection
is always explicit; build/release mode alone never changes the environment.
No Firebase/R2 provider requirement is added. Issue #21 optionally enables
`sentry_flutter` using `SENTRY_DSN`; empty/absent disables the SDK.
`SENTRY_ENVIRONMENT` must match APP_ENV. Public example JSONs keep DSNs empty.
See [observability/privacy](../docs/development/observability.md) and the
[central environment catalog](../docs/development/environment-configuration.md)
for commands, future public config, secret storage and isolated resources.

## Structure

```text
android/                  generated Android application
ios/                      generated iOS application
lib/
  main.dart               entry point, configuration and database creation
  src/
    app/                  app bootstrap, database lifetime and loading/error state
      navigation/         shared two-mode shell
    configuration/        validated compile-time environment
    observability/        optional Sentry bootstrap and privacy boundary
    persistence/          Drift database and generated code
    platform/             secure storage, connectivity, camera, QR, arrival contracts
    road_mode/            Road Mode placeholder
    yard_mode/            Yard Mode placeholder
test/
  app/                    startup and mode-navigation widget tests
  configuration/          environment unit tests
  persistence/            real SQLite open/schema/reopen tests
```

Road is the initial scaffold tab, not a role/permission decision. Future identity
issues determine authorized modes. The shell is not an authorization boundary.
No state-management framework, domain repositories or new offline architecture
are introduced.

## Local persistence

Drift/SQLite opens `trailersaas.sqlite` through `drift_flutter` in the
application-support directory. Schema version 1 has **zero application tables**.
Its initial migration calls `createAll()` over an empty schema; SQLite stores
the version in `PRAGMA user_version`. Startup executes `SELECT 1` to force a
real open, handles failure with a generic error/retry state, and closes the
database when the app root is disposed.

Tests inject native SQLite executors and check the actual schema and file reopen.
Manager API generation remains disabled in `build.yaml`; explicit typed repositories
use the generated Drift tables. Incremental schema v6 preserves cache, commands,
media, dependencies, frozen requests and Shift results and adds Road drafts/captures
and targeted instructions. Actual v1–v5 upgrade/reopen/rollback tests verify preservation.

## Platform boundaries

Secure storage, Firebase identity and connectivity use the #42 native adapters.
QR identification uses the #44 scanner; photographs use the #43 image_picker camera
adapter and private #54 storage. Tests inject adapters; production has no fake photo
or insecure credential fallback. #47 implements bounded foreground arrival assistance; no permanent background location service.

Issue #47 supplies the native bounded foreground location adapter and operational
caller under ADR-014. It returns one reading or unavailable, cancels the observer,
and keeps manual fallback. No route history, background service or automatic
arrival transition is enabled. The earlier ArrivalLocation scaffold is retained
as a compatibility contract; production assistance uses LocationAssistancePort.

No Firebase, login, tenant selection, operational workflows, Yard Check,
uploads, API calls, command/media queue or continuous GPS tracking are included.

## Validation environment

Implemented on Windows with Flutter 3.47.6 / Dart 3.13.5.
Validation performed for this scaffold:

- `flutter pub get --enforce-lockfile`: passed.
- Drift code generation: passed.
- Dart format check: passed, no changes required.
- `flutter analyze --fatal-infos`: passed, no issues.
- `flutter test --reporter expanded`: passed, 16 unit/widget tests.
- Android debug build: attempted; stopped with `No Android SDK found`.
- iOS build and native device startup: not executed on this Windows host.

The Android SDK is absent according to `flutter doctor`; Android packaging and
on-device execution require another configured host. iOS builds/device runs
require macOS/Xcode and cannot be executed here. Host widget tests exercise the
real app root/navigation with native SQLite; they do not replace device testing
of the application-support path or native platform integration.

## Road execution (#44)

[Implementation, isolation and foreground recovery](../docs/mobile/issue-44-road-execution.md). Open assigned Trip for authoritative Stops, Tractor, Expected Trailer and real coupling. Camera-only QR identifies equipment; commands require online confirmation. One durable app-private User/Tenant submission receipt protects uncertain outcomes; this is not #46 offline queue/replay. Required Shift/Pre-Trip and finalized POD integrations remain blocked until their owning issues. mobile_scanner 7.4.2 adds the native QR adapter; iOS camera usage text is explicit, with no gallery/GPS permission.

## Issue #46 command queue

Production Road records immutable intent in Drift schema v3 (`local_commands` and
`local_sync_issues`) and replays through existing explicit command endpoints. Cache
projections stay unchanged until authoritative refresh. Sync runs at foreground
startup/resume, reconnect and manual Sync now. Prior #97 file receipts are imported
crash-safely with the original UUID/path/body; session cache cleanup preserves queue
rows while hiding other identities/Tenants. No OS background guarantee, media queue,
first-departure prerequisite bypass or Delivery evidence bypass. See
[implementation, migration, security and retry policy](../docs/mobile/issue-46-command-replay.md).

## Driver Shift and inspections (#43)

Road home exposes real Shift context, On/Off Duty and native four-photo Pre-Trip /
Post-Trip forms. image_picker 1.2.4 (flutter.dev maintained) is the native camera
adapter; no gallery upload workflow or fake photos. UUID private file storage and
Drift v5 drafts/capture contexts survive restart. Android lost camera data is
recovered via retrieveLostData; ambiguous/partial results require retake. iOS camera
usage text is configured. No audio/GPS permission is introduced. Camera metadata is
not required. OS camera permissions, process death and iOS/Android physical device
behavior still need device QA; host tests exercise injected camera plus real files,
SQLite and HTTP. Native encryption/backup limitations remain as documented in #54.
Locked dependencies: flutter pub get --enforce-lockfile. Generate with dart run
build_runner build, format/analyze/test using existing CI. All older cache/command/
media records are preserved; final requests freeze after real Shift/photo IDs resolve.
See [Shift architecture and safety](../docs/mobile/issue-43-shift-inspections.md); implemented #45 consumers follow below.

## Issue #45 POD / Fuel / problem forms and instructions

Road uses the existing image_picker camera adapter, private streaming file store,
Drift v6 durable forms and the same replay/media lane. Four new local tables preserve
unfinished captures and targeted Driver Updates. Existing migrations/queues remain.
Only finalized server Document IDs enter frozen business requests. Local saving,
upload finalization and server acceptance are distinct states; no fake success,
new Firebase authority or extra worker is introduced. Instructions refresh without
push and survive reassignment away. Native permissions/process-death behavior needs
Android/iOS device QA; CI covers controlled camera + real files/SQLite/HTTP/widgets.
Use locked dependencies, Drift generation/diff, format, fatal-info analyze and all
tests as before. See docs/mobile/issue-45-pod-fuel-exceptions.md.
# Issue #50 Backend handoff (completed by #51 mobile)

The Yard Check Backend now generates immutable expected snapshots and provides
bounded version-fenced pages, scans, terminal commands and explicit reconciliation.
The Backend issue made no Flutter/Drift/queue/native changes. #51 now implements the requirement to
download the full authorized server snapshot before offline use, retain its ID/
version/scope/counts, replay original scan UUIDs/times and wait for every scan ACK
before completion. A partial page or historical receipt is not current authority.
See [the Backend contract and handoff](../docs/mobile/issue-50-yard-check-backend.md).


## #51 Yard Check mobile and Drift v9

YardChecksPanel/YardCheckController provide real Start/Resume/scan/progress/review/
Complete/Cancel/reconciliation on existing APIs. CheckStore validates the entire
paginated version-fenced inventory before atomic offline readiness. CheckIntent/
CheckReplay extend the existing CommandStore/ReplayWorker. Unknown QR observations
remain private and durable until trusted online identity resolution; requests and
UUIDs freeze before submission. No fabricated IDs/inventory/ACKs, second camera
plugin, queue, worker or automatic physical reconciliation exists.

Drift v9 incrementally adds five cache/observation/review tables; 26 total. Original
facts/times/ownership are guarded; real file-backed v8 migration, rollback/reopen,
two connections/workers and older upgrade regressions preserve existing data.
Typed server receipts distinguish local pending from accepted results. Off Duty
has the documented local Check guard and unknown/stale warning; Backend guard is
not implied. Read [#51 acceptance/test/security record](../docs/mobile/issue-51-yard-check-mobile-offline.md).
Native physical Android/iOS QA remains #65. Keep the PR Draft until all four
final-HEAD CI jobs pass; NO MERGE.
