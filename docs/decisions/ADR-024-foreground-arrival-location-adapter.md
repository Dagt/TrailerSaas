# ADR-024 — Bounded foreground arrival location adapter

Status: Accepted for issue #47 under the explicitly authorized package selection.

ADR-014 remains the governing privacy/arrival decision. Use Baseflow Geolocator
14.0.1 (MIT) behind `LocationAssistancePort`, Android/iOS only. This exact facade
version avoids the incompatible Linux `dbus ^0.7` dependency introduced in 14.0.2;
the existing connectivity adapter needs `dbus ^0.8`. No dependency override or
connectivity downgrade. The lockfile selects current compatible native adapters
(`geolocator_android 5.1.1+1`, `geolocator_apple 2.3.14`, interface 4.4.0).

Checked the upstream [package](https://pub.dev/packages/geolocator), MIT license,
published dependency metadata and resolved native source. Android uses Flutter's
compile/min SDK settings and the project's AGP9/Java17 native toolchain. Apple
supports iOS11+, below the project's iOS15 deployment target, and Swift packages.
The project uses SPM; its permission handler requests WhenInUse when that sole
Info.plist purpose key exists. No Always key, background mode or stronger
permission is added. CocoaPods macro guidance does not justify adding a Podfile
to this SPM project. Hardware/build QA is recorded in #65.

One explicit check starts a native observer for the first reading only, with a
10-second bound. It cancels on the first result, error, timeout, background,
context change or Road exit. No foreground service options, OS registered
geofences, repeated sampling, timer-based location checks, maps, external
geolocation API or route trail. Existing Always grants are treated only as a
foreground grant; this adapter never requests an upgrade.

Coordinates and acquisition results are transient. Only expressly confirmed
Arrival evidence enters the existing offline queue. Permission/service/precision
are checked before acquisition and again before suggestion/confirmation.

The 30-second lifetime and radius-relative uncertainty policy are specified in
[issue #47](../mobile/issue-47-geofence-assisted-arrival.md); they are assistance
thresholds, not server authorization or proof of presence. No default Site radius.
