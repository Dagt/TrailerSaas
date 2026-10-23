# Run the demonstration locally

Use a disposable local browser profile and dedicated Android emulator. No
production infrastructure, Firebase credentials, PostgreSQL, Backend, object
storage or real carrier data is required for this **isolated presentation mode**.
Do not seed a production Tenant. Fixtures are not an alternative login method.

## Web (required)

Prerequisites: Git, Node.js 24 (or supported 22.13+), npm and a modern browser.
From the repository root:

```sh
cd web
npm ci
npm run dev -- --port 5350 --strictPort
```

Keep the server bound to `127.0.0.1`. Open:

- Admin: `http://127.0.0.1:5350/dev/tenant-admin.html?route=/admin`
- Operations: `http://127.0.0.1:5350/dev/operations.html?route=/operations`
- Customer: `http://127.0.0.1:5350/dev/customer.html?route=/customer`

The separate `dev/*.html` entries use the real route components through a memory
router. Click internal navigation normally. The outer URL does not change as the
memory router navigates; reload or reopen the entry URL to reset the demonstration.
Do not navigate through normal Home links expecting an authenticated session.
Normal `/admin`, `/operations`, `/customer` still use real authentication and
correctly show Firebase unconfigured without environment credentials.

For a specific screen, set `route` to its path from the asset index. To show a
boundary, append `&state=empty`, `loading`, `500`, `403`, or `no-membership` where
the existing fixture supports it. Reopen the initial entry to reset. Filters and
read pagination work against fixture data; mutation requests reject. Do not submit
create/cancel/resolve actions as a claimed business success. Customer downloads
show storage unavailable, not a fabricated PDF.

Stop with Ctrl+C. Do not expose this development server publicly. The lockfile's
pre-existing Vite development-server security advisory is documented separately
in limitations; UX-08 makes no dependency change.

## Native Android (optional local SDK; actual Flutter widgets)

Install Flutter **3.47.6 / Dart 3.13.5**, a compatible Android SDK/JDK and an
Android emulator using the normal Flutter setup. Our evidence used AOSP Android
15/API 35 x86_64, WHPX/software GPU, 720×1600 at 320 dpi (360×800 logical),
font scale 1.0. This is not physical-device QA or iOS validation.

```sh
cd mobile
flutter pub get --enforce-lockfile
dart run build_runner build
flutter devices
flutter run -d <dedicated-emulator-id> -t test/native_review/main.dart --dart-define=UX06_NATIVE_REVIEW=true
```

The explicit target and debug define are both required. The existing review entry
throws outside debug/review mode and is not imported by `lib/main.dart`. It shows
`ISOLATED ANDROID REVIEW`, synthetic `MockClient` reads and an in-memory fixture.
Non-GET HTTP requests and the Road review command journal reject writes. This
runner is not a production authentication path and must not be distributed as the
normal application. A local Yard draft, if opened, is not a server command ACK.

From the review menu:

1. **Assigned Trip native QR** opens actual `RoadTripScreen`; Back returns.
2. **Road and Yard workspace** opens actual `SessionView`; select Road/Yard as
   available in this synthetic dual-role context. Scroll for synchronization.
3. **Yard movement review fixture** opens actual Yard controls with a synthetic
   scanner. Select the available Site, scan using that fixture, review Trailer
   and destination. Open review if desired; use **Not Yet**, never claim a move.
4. Native camera review is separate; a generated/synthetic QR is not an authorized
   operation. Permission denial/scan rejection never authorizes a command.

Reset by force stopping/restarting the isolated review application. Uninstall
only this dedicated emulator's demo application if a complete local reset is
needed; never remove a real worker's data. No Firebase sign-in is required.

## Recapture assets (optional tooling)

The app source under `web/` must be clean. Capture output must be a **new path**;
scripts refuse to overwrite existing evidence. Node needs `playwright` available
outside the product lockfile (or set `PLAYWRIGHT_MODULE` to an existing module).
Install its compatible browser in that separate tooling environment, or set
`BROWSER_EXECUTABLE` to a local Chromium/Edge executable. No product dependencies
need to change. With the Web server running, from repository root:

```sh
DEMO_OUTPUT=/absolute/new-capture/web node demo-assets/tools/capture-web.cjs
```

PowerShell equivalent:

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/path/to/tooling/node_modules/playwright'
$env:BROWSER_EXECUTABLE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
$env:DEMO_OUTPUT = 'C:/path/to/new-capture/web'
node demo-assets/tools/capture-web.cjs
```

For Android, first build the isolated APK with the same target/define, then use
`python demo-assets/tools/capture-android.py --help`. It captures the currently
displayed screen with ADB, checks installed APK identity and records geometry,
source trees and artifact hashes. Navigate to the named widget before capture;
the script does not invent UI states. Keep actual build logs alongside your
new manifest. Never substitute widget-test images for emulator evidence.

Verify committed assets: `python demo-assets/tools/verify-package.py`.
The presentation's editable authoring source and content are in `tools/` and
`notes/presentation-content.json`; the supplied PPTX can be reused without any
SDK. The authoring script additionally requires the documented Artifact Tool
runtime; it is optional and has no application import.

To regenerate the deck in a Codex environment with the Presentations skill and
bundled dependencies, set `ARTIFACT_TOOL_IMPORT` to the bundled Artifact Tool
module file URL (or provide the package), `RUNTIME_NODE_MODULES` to its dependency
directory, `PRESENTATIONS_SKILL_DIR` to the installed presentations skill,
`RUNTIME_PYTHON` to its Python executable, and `DECK_WORKSPACE` to a **new** output
directory. Run `node demo-assets/tools/build-presentation.mjs` with that runtime's
Node. These are authoring-tool paths, not application settings. The finalizer
must succeed and every preview must be visually reviewed before replacing or
publishing a deck. Without this optional runtime, edit the supplied PPTX directly;
do not install artifact authoring packages into Web or Mobile.
