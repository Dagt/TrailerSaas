import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';

import 'location_assistance.dart';

class NativeLocationAssistance implements LocationAssistancePort {
  NativeLocationAssistance({
    GeolocatorPlatform? platform,
    TargetPlatform? device,
  }) : platform = platform ?? GeolocatorPlatform.instance,
       device = device ?? defaultTargetPlatform;
  final GeolocatorPlatform platform;
  final TargetPlatform device;
  StreamSubscription<Position>? _reading;
  Completer<AssistanceFix?>? _result;
  Timer? _timeout;
  static const acquisitionLimit = Duration(seconds: 10);

  @override
  Future<LocationAccess> access({bool request = false}) async {
    final supported =
        !kIsWeb &&
        {TargetPlatform.android, TargetPlatform.iOS}.contains(device);
    if (!supported) {
      return const LocationAccess(
        supported: false,
        servicesEnabled: false,
        permission: AssistancePermission.denied,
      );
    }
    final services = await platform.isLocationServiceEnabled();
    var permission = await platform.checkPermission();
    if (request && services && permission == LocationPermission.denied) {
      permission = await platform.requestPermission();
    }
    final granted = {
      LocationPermission.whileInUse,
      LocationPermission.always,
    }.contains(permission);
    final approximate =
        granted &&
        await platform.getLocationAccuracy() != LocationAccuracyStatus.precise;
    return LocationAccess(
      supported: true,
      servicesEnabled: services,
      approximate: approximate,
      permission: granted
          ? AssistancePermission.whileInUse
          : permission == LocationPermission.deniedForever
          ? AssistancePermission.deniedForever
          : AssistancePermission.denied,
    );
  }

  @override
  Future<AssistanceFix?> read() async {
    cancel();
    final result = Completer<AssistanceFix?>();
    _result = result;
    _timeout = Timer(acquisitionLimit, cancel);
    // Subscribe only until the first fix. Cancellation removes the native observer,
    // including on background/context change. No foreground service configuration.
    final settings = device == TargetPlatform.iOS
        ? AppleSettings(
            accuracy: LocationAccuracy.high,
            allowBackgroundLocationUpdates: false,
            showBackgroundLocationIndicator: false,
            timeLimit: acquisitionLimit,
          )
        : AndroidSettings(
            accuracy: LocationAccuracy.high,
            forceLocationManager: true,
            timeLimit: acquisitionLimit,
          );
    try {
      _reading = platform
          .getPositionStream(locationSettings: settings)
          .listen(
            (position) {
              if (!identical(_result, result) || result.isCompleted) return;
              result.complete(
                AssistanceFix(
                  latitude: position.latitude,
                  longitude: position.longitude,
                  accuracyMeters: position.accuracy,
                  capturedAt: position.timestamp.toUtc(),
                ),
              );
              cancel();
            },
            onError: (Object _) => cancel(),
            onDone: cancel,
          );
    } catch (_) {
      cancel();
    }
    return result.future;
  }

  @override
  void cancel() {
    final result = _result;
    _result = null;
    _timeout?.cancel();
    _timeout = null;
    unawaited(_reading?.cancel());
    _reading = null;
    if (result != null && !result.isCompleted) result.complete(null);
  }
}
