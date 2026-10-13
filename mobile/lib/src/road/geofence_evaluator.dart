import 'dart:convert';
import 'dart:math';

import '../platform/location_assistance.dart';

class ArrivalTarget {
  ArrivalTarget._(this.data);
  final Map<String, dynamic> data;
  String get tripId => data['tripId'] as String;
  String get stopId => data['stopId'] as String;
  String get name => data['destinationName'] as String;
  double get latitude => (data['latitude'] as num).toDouble();
  double get longitude => (data['longitude'] as num).toDouble();
  double get radius => (data['geofenceRadiusM'] as num).toDouble();
  String get identity => jsonEncode(data);

  static ArrivalTarget? parse(
    Map<String, dynamic>? raw,
    Map<String, dynamic>? trip,
    String driver,
  ) {
    if (raw == null ||
        trip == null ||
        trip['status'] != 'IN_TRANSIT' ||
        trip['currentDriverId'] != driver) {
      return null;
    }
    final stops = trip['stops'];
    if (stops is! List) return null;
    if (stops.any((stop) => stop is! Map)) return null;
    for (final field in ['tripVersion', 'stopVersion', 'siteVersion']) {
      if (raw[field] is! int || (raw[field] as int) < 0) return null;
    }
    final next = stops
        .cast<Map>()
        .where((s) => !{'COMPLETED', 'CANCELLED'}.contains(s['status']))
        .firstOrNull;
    if (next == null ||
        next['status'] != 'EN_ROUTE' ||
        raw['stopStatus'] != 'EN_ROUTE' ||
        raw['tripId'] != trip['id'] ||
        raw['tripVersion'] != trip['version'] ||
        raw['driverId'] != driver ||
        raw['stopId'] != next['id'] ||
        raw['stopVersion'] != next['version'] ||
        raw['siteId'] == null ||
        raw['siteId'] != next['siteId'] ||
        raw['locationId'] != next['locationId'] ||
        raw['tractorId'] != trip['currentTractorId'] ||
        raw['expectedTrailerId'] != trip['plannedInitialTrailerId'] ||
        raw['destinationName'] is! String ||
        raw['siteVersion'] is! int) {
      return null;
    }
    for (final field in ['latitude', 'longitude', 'geofenceRadiusM']) {
      if (raw[field] is! num || !(raw[field] as num).isFinite) return null;
    }
    if (raw['geofenceRadiusM'] is! int ||
        (raw['latitude'] as num).abs() > 90 ||
        (raw['longitude'] as num).abs() > 180 ||
        (raw['geofenceRadiusM'] as num) <= 0) {
      return null;
    }
    // Stable key order; independent copies cannot retarget an existing suggestion.
    return ArrivalTarget._(
      Map.unmodifiable({
        for (final key in (raw.keys.toList()..sort())) key: raw[key],
      }),
    );
  }
}

class GeofenceEvaluator {
  static const maxAge = Duration(seconds: 30);
  static double distance(
    double lat,
    double lon,
    double otherLat,
    double otherLon,
  ) {
    final dLat = (otherLat - lat) * pi / 180,
        dLon = (otherLon - lon) * pi / 180;
    final a =
        pow(sin(dLat / 2), 2) +
        cos(lat * pi / 180) * cos(otherLat * pi / 180) * pow(sin(dLon / 2), 2);
    return 6371008.8 * 2 * asin(sqrt(a.clamp(0, 1)));
  }

  static bool probable(ArrivalTarget target, AssistanceFix fix, DateTime now) {
    final age = now.toUtc().difference(fix.capturedAt.toUtc());
    return !fix.approximate &&
        age >= Duration.zero &&
        age <= maxAge &&
        fix.latitude.isFinite &&
        fix.latitude.abs() <= 90 &&
        fix.longitude.isFinite &&
        fix.longitude.abs() <= 180 &&
        fix.accuracyMeters.isFinite &&
        fix.accuracyMeters > 0 &&
        fix.accuracyMeters <= target.radius / 2 &&
        distance(
                  target.latitude,
                  target.longitude,
                  fix.latitude,
                  fix.longitude,
                ) +
                fix.accuracyMeters <=
            target.radius;
  }
}
