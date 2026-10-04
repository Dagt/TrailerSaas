# ADR-014 — Geofence-Assisted Arrival Without Continuous Tracking

**Status:** Accepted  
**Date:** 2026-10-04

## Context

The product needs:

- optional geofence-assisted arrival;
- manual arrival fallback;
- known Site coordinates/radius;
- personal Driver phones;
- no continuous route tracking requirement;
- no dependency on always-on background GPS.

## Decision

Use location only as a **best-effort arrival-assistance signal**.

The MVP does not collect continuous GPS breadcrumbs.

For an active Trip:

- cache coordinates/radius for the current/next known Site;
- when permission and platform behavior allow, use device geofence/location services to detect probable arrival;
- prompt the Driver to confirm arrival;
- allow manual arrival at all times.

Geofence detection by itself does not transition the Stop state.

The authoritative command remains:

`POST /stops/{stopId}/arrivals`

with source `MANUAL` or `GEOFENCE_CONFIRMED`.

## Active-region strategy

Do not register every Customer Site on the device.

Register/evaluate only the operationally relevant next active Site(s), keeping the active region set intentionally small.

This avoids platform-region-limit coupling and reduces unnecessary background location use.

## Permissions

Request the least intrusive permission that still supports the chosen platform behavior.

Do not require permanent/always-on location merely to use the application if manual arrival satisfies the workflow.

If stronger background permission is ever required for a specific platform implementation, it must be justified in mobile UX/privacy documentation before release.

## Server behavior

Server may store:

- arrival coordinates;
- accuracy;
- source;
- occurrence time

as event evidence when supplied.

It does not store a route trail.

Basic Site radius checks do not require PostGIS for MVP. Latitude/longitude + radius and ordinary distance calculation are sufficient.

## Failure behavior

If:

- location permission denied;
- GPS unavailable;
- geofence event not delivered;
- device is offline;

the Driver uses manual arrival.

No operational workflow may become impossible solely because location automation failed.

## Privacy

Do not use arrival geolocation to create hidden employee monitoring.

Location collection must correspond to an explicit operational action/active Trip context.

## Consequences

### Positive

- lower battery/privacy cost;
- simpler iOS/Android permission model;
- consistent with real requirements;
- no large GPS data store;
- graceful manual fallback.

### Negative / trade-offs

- geofence notification is not guaranteed;
- arrival still requires Driver confirmation;
- device/platform testing remains necessary.

## Follow-up

- select/test Flutter location/geofence packages during scaffold;
- test on supported iOS/Android versions and real devices;
- document permission text/privacy disclosures before production release.
