package com.trailersaas.trips.internal;

import java.util.Map;

/** Assistance envelope, never a presence/authentication assertion. ADR-014. */
final class GeofenceEvidence {
  static boolean inside(ArrivalAssistanceContext site, Map<String,Object> fix) {
    if (fix == null) return false;
    double accuracy = ((Number)fix.get("accuracyM")).doubleValue();
    return accuracy > 0 && accuracy <= site.geofenceRadiusM()/2.0
        && distance(site.latitude(),site.longitude(),((Number)fix.get("latitude")).doubleValue(),
            ((Number)fix.get("longitude")).doubleValue()) + accuracy <= site.geofenceRadiusM();
  }

  static double distance(double latitude,double longitude,double otherLatitude,double otherLongitude) {
    double lat = Math.toRadians(otherLatitude-latitude), lon = Math.toRadians(otherLongitude-longitude);
    double a = Math.pow(Math.sin(lat/2),2) + Math.cos(Math.toRadians(latitude))
        * Math.cos(Math.toRadians(otherLatitude))*Math.pow(Math.sin(lon/2),2);
    return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.max(0,Math.min(1,a))));
  }
}
