# ADR-005 — Mobile Platform: Flutter with Local Offline Queue

**Status:** Proposed  
**Date:** 2026-10-04

## Context

The mobile app must support both iOS and Android and includes:

- Road Driver Mode;
- Yard / Spotter Mode;
- QR scanning;
- camera/photos;
- geofence-assisted arrival;
- manual departure;
- On Duty / Off Duty;
- Pre/Post-trip;
- POD/BOL evidence;
- temporary offline operation;
- retry/synchronization.

Drivers generally use personal phones, so the MVP must not depend on continuous background location tracking.

## Decision

Use **Flutter** for a single iOS/Android application.

Use a local SQLite-based persistence layer for offline operational commands/events. Drift is the preferred Flutter persistence candidate unless implementation testing shows a better fit.

The app shall use an explicit pending-sync queue for critical operations.

## Offline/idempotency model

Each offline-capable operation receives a client-generated immutable operation/event UUID.

Example:

- HOOK trailer;
- DROP trailer;
- ARRIVAL;
- DEPARTURE;
- POD upload metadata;
- Yard move.

On reconnect, the mobile client retries the operation with the same idempotency key.

The backend must return the same business result rather than create duplicates.

## Location behavior

- no continuous route recording requirement;
- geofence/location may suggest arrival;
- driver confirms arrival;
- manual arrival fallback;
- departure is manual;
- permissions should be limited to operational need.

## Rationale

Flutter currently supports both Android and iOS from one codebase and provides access to camera, QR and location capabilities needed by TrailerSaaS.

A single mobile app can provide role-specific Road and Yard experiences while sharing authentication, sync, QR and media infrastructure.

## Consequences

### Positive

- one mobile codebase;
- strong UI consistency;
- good native-device integration;
- offline-first behavior can be designed centrally;
- simpler than maintaining Swift + Kotlin applications.

### Negative / trade-offs

- iOS builds require macOS/Xcode in CI/release workflow;
- background/geofence behavior differs between iOS and Android and must be tested on real devices;
- offline synchronization adds meaningful complexity and must be designed before UI implementation.

## Follow-up

- validate QR and geofence plugins on target OS versions;
- select SQLite/Drift package versions;
- define sync protocol;
- define media upload retry behavior;
- define conflict resolution rules.
