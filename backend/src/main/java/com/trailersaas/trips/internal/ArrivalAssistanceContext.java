package com.trailersaas.trips.internal;

import java.util.UUID;

/** Live normalized destination, only projected after own-Trip authorization. */
record ArrivalAssistanceContext(
    UUID tripId, long tripVersion, UUID driverId, UUID tractorId, UUID expectedTrailerId,
    UUID stopId, long stopVersion, String stopStatus, UUID siteId, long siteVersion,
    UUID locationId, String destinationName, double latitude, double longitude, int geofenceRadiusM) {
  static boolean configured(double latitude, double longitude, int radius) {
    return Double.isFinite(latitude) && Math.abs(latitude) <= 90
        && Double.isFinite(longitude) && Math.abs(longitude) <= 180 && radius > 0;
  }
}
