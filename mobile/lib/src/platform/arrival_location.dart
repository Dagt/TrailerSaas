class LocationFix {
  const LocationFix({
    required this.latitude,
    required this.longitude,
    required this.accuracyMeters,
    required this.capturedAt,
  });

  final double latitude;
  final double longitude;
  final double accuracyMeters;
  final DateTime capturedAt;
}

class GeofenceRegion {
  const GeofenceRegion({
    required this.latitude,
    required this.longitude,
    required this.radiusMeters,
  });

  final double latitude;
  final double longitude;
  final double radiusMeters;
}

/// One-shot, best-effort signals for an explicitly active arrival context.
/// Null means unavailable. No tracking stream or automatic domain transition.
abstract interface class ArrivalLocation {
  Future<LocationFix?> currentPosition();
  Future<bool?> isInsideRegion(GeofenceRegion region);
}
