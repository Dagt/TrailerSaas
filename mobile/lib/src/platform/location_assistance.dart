import 'arrival_location.dart';

enum AssistancePermission { denied, deniedForever, whileInUse }

class LocationAccess {
  const LocationAccess({
    required this.supported,
    required this.servicesEnabled,
    required this.permission,
    this.approximate = false,
  });
  final bool supported, servicesEnabled, approximate;
  final AssistancePermission permission;
  bool get usable =>
      supported &&
      servicesEnabled &&
      permission == AssistancePermission.whileInUse &&
      !approximate;
}

class AssistanceFix extends LocationFix {
  const AssistanceFix({
    required super.latitude,
    required super.longitude,
    required super.accuracyMeters,
    required super.capturedAt,
    this.approximate = false,
  });
  final bool approximate;
  Map<String, double> get evidence => {
    'latitude': latitude,
    'longitude': longitude,
    'accuracyM': accuracyMeters,
  };
}

/// One cancellable bounded observation. No persistent service or location history.
abstract interface class LocationAssistancePort {
  Future<LocationAccess> access({bool request = false});
  Future<AssistanceFix?> read();
  void cancel();
}
