# TrailerSaaS Mobile

## Current implementation — Issue #42

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
Manager API generation is disabled in `build.yaml` while no tables exist.
Later schema versions must introduce explicit migrations and migration tests.
The accepted command/media/cache tables are deliberately deferred; there are
no queues, cached Trips, tenant context, media files or synchronization.

## Platform boundaries

Secure storage and connectivity are Dart interfaces only, with no concrete
adapter, insecure in-memory production fallback or background listener.
Camera, QR and location/geofence are also interfaces only. No capability plugin
or permission is added for them.

Arrival location is a one-shot best-effort contract returning null when
unavailable. It defines no location stream, route history, background service
or automatic arrival transition. Future callers must supply an explicitly active
arrival context and preserve manual fallback under ADR-014.

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
