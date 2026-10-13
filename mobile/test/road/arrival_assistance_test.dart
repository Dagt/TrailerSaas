import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:trailersaas_mobile/src/platform/location_assistance.dart';
import 'package:trailersaas_mobile/src/platform/native_location_assistance.dart';
import 'package:trailersaas_mobile/src/road/arrival_assistance_controller.dart';
import 'package:trailersaas_mobile/src/road/geofence_evaluator.dart';

final geoNow = DateTime.utc(2026, 10, 12, 18);
Map<String, dynamic> geoTrip() => {
  'id': 'trip-A',
  'status': 'IN_TRANSIT',
  'version': 1,
  'currentDriverId': 'driver',
  'currentTractorId': 'tractor',
  'plannedInitialTrailerId': null,
  'stops': [
    {
      'id': 'stop-A',
      'version': 1,
      'status': 'EN_ROUTE',
      'siteId': 'site-A',
      'locationId': null,
      'sequenceNumber': 1,
    },
  ],
};
Map<String, dynamic> geoTarget() => {
  'tripId': 'trip-A',
  'tripVersion': 1,
  'driverId': 'driver',
  'tractorId': 'tractor',
  'expectedTrailerId': null,
  'stopId': 'stop-A',
  'stopVersion': 1,
  'stopStatus': 'EN_ROUTE',
  'siteId': 'site-A',
  'siteVersion': 1,
  'locationId': null,
  'destinationName': 'Authorized dock',
  'latitude': 41.0,
  'longitude': -88.0,
  'geofenceRadiusM': 250,
};
AssistanceFix geoFix({
  double latitude = 41,
  double accuracy = 25,
  DateTime? at,
  bool approximate = false,
}) => AssistanceFix(
  latitude: latitude,
  longitude: -88,
  accuracyMeters: accuracy,
  capturedAt: at ?? geoNow,
  approximate: approximate,
);

class FakeLocation implements LocationAssistancePort {
  LocationAccess state = const LocationAccess(
    supported: true,
    servicesEnabled: true,
    permission: AssistancePermission.whileInUse,
  );
  AssistanceFix? fix = geoFix();
  Completer<AssistanceFix?>? waiting;
  int reads = 0, cancels = 0, requests = 0;
  bool fail = false;
  Completer<LocationAccess>? permissionCheck;
  @override
  Future<LocationAccess> access({bool request = false}) async {
    if (request) requests++;
    if (fail) throw StateError('unavailable');
    return permissionCheck?.future ?? Future.value(state);
  }

  @override
  Future<AssistanceFix?> read() async {
    reads++;
    return waiting?.future ?? Future.value(fix);
  }

  @override
  void cancel() {
    cancels++;
  }
}

class FakeGeolocator extends GeolocatorPlatform {
  LocationPermission permission = LocationPermission.denied;
  bool services = true, reduced = false, cancelled = false;
  int requests = 0;
  LocationSettings? settings;
  late final positions = StreamController<Position>(
    onCancel: () {
      cancelled = true;
    },
  );
  @override
  Future<bool> isLocationServiceEnabled() async => services;
  @override
  Future<LocationPermission> checkPermission() async => permission;
  @override
  Future<LocationPermission> requestPermission() async {
    requests++;
    return permission = LocationPermission.whileInUse;
  }

  @override
  Future<LocationAccuracyStatus> getLocationAccuracy() async =>
      reduced ? LocationAccuracyStatus.reduced : LocationAccuracyStatus.precise;
  @override
  Stream<Position> getPositionStream({LocationSettings? locationSettings}) {
    settings = locationSettings;
    return positions.stream;
  }
}

void main() {
  late FakeLocation port;
  late ArrivalAssistanceController controller;
  late DateTime now;
  setUp(() {
    now = geoNow;
    port = FakeLocation();
    controller = ArrivalAssistanceController(port, clock: () => now);
    controller.update(ArrivalTarget.parse(geoTarget(), geoTrip(), 'driver'));
  });
  tearDown(() => controller.dispose());
  test('two confirmation taps consume exactly one suggestion', () async {
    await controller.check();
    port.permissionCheck = Completer();
    final first = controller.confirmedEvidence(),
        second = controller.confirmedEvidence();
    port.permissionCheck!.complete(port.state);
    expect(
      (await Future.wait([first, second])).whereType<ArrivalSuggestion>(),
      hasLength(1),
    );
  });
  test(
    'late confirmation cannot cancel a newer destination suggestion',
    () async {
      await controller.check();
      port.permissionCheck = Completer();
      final pending = port.permissionCheck!,
          oldConfirmation = controller.confirmedEvidence();
      controller.update(
        ArrivalTarget.parse(
          geoTarget()..['siteVersion'] = 2,
          geoTrip(),
          'driver',
        ),
      );
      port.permissionCheck = null;
      await controller.check();
      final fresh = controller.suggestion;
      expect(fresh, isNotNull);
      pending.complete(port.state);
      expect(await oldConfirmation, isNull);
      expect(controller.suggestion, same(fresh));
    },
  );
  test(
    'precise inside reading suggests only; human confirmation returns evidence',
    () async {
      await controller.check();
      expect(controller.suggestion, isNotNull);
      expect(port.requests, 1);
      final confirmed = await controller.confirmedEvidence();
      expect(confirmed!.fix.evidence, {
        'latitude': 41.0,
        'longitude': -88.0,
        'accuracyM': 25.0,
      });
      expect(controller.suggestion, isNull);
      expect(await controller.confirmedEvidence(), isNull);
    },
  );
  test(
    'Not Yet suppresses consecutive readings for same destination',
    () async {
      await controller.check();
      controller.notYet();
      await controller.check();
      controller.update(ArrivalTarget.parse(geoTarget(), geoTrip(), 'driver'));
      await controller.check();
      expect(port.reads, 1);
      expect(controller.suggestion, isNull);
      final changed = geoTarget()..['siteVersion'] = 2;
      controller.update(ArrivalTarget.parse(changed, geoTrip(), 'driver'));
      await controller.check();
      expect(port.reads, 2);
    },
  );
  for (final state in [
    const LocationAccess(
      supported: true,
      servicesEnabled: true,
      permission: AssistancePermission.denied,
    ),
    const LocationAccess(
      supported: true,
      servicesEnabled: true,
      permission: AssistancePermission.deniedForever,
    ),
    const LocationAccess(
      supported: true,
      servicesEnabled: false,
      permission: AssistancePermission.whileInUse,
    ),
    const LocationAccess(
      supported: false,
      servicesEnabled: false,
      permission: AssistancePermission.denied,
    ),
    const LocationAccess(
      supported: true,
      servicesEnabled: true,
      permission: AssistancePermission.whileInUse,
      approximate: true,
    ),
  ]) {
    test(
      'unavailable ${state.permission}/${state.servicesEnabled}/${state.supported}/${state.approximate} never reads',
      () async {
        port.state = state;
        await controller.check();
        expect(port.reads, 0);
        expect(controller.suggestion, isNull);
      },
    );
  }
  for (final fix in [
    geoFix(latitude: 42),
    geoFix(accuracy: 126),
    geoFix(accuracy: 0),
    geoFix(accuracy: double.nan),
    geoFix(accuracy: double.infinity),
    geoFix(at: geoNow.subtract(const Duration(seconds: 31))),
    geoFix(at: geoNow.add(const Duration(seconds: 1))),
    geoFix(approximate: true),
    null,
  ]) {
    test(
      'unsafe fix ${fix?.latitude}/${fix?.accuracyMeters}/${fix?.capturedAt}/${fix?.approximate} fails closed',
      () async {
        port.fix = fix;
        await controller.check();
        expect(controller.suggestion, isNull);
        expect(await controller.confirmedEvidence(), isNull);
      },
    );
  }
  test('freshness and uncertainty boundaries are reproducible', () {
    final target = controller.target!;
    expect(
      GeofenceEvaluator.probable(target, geoFix(accuracy: 125), now),
      isTrue,
    );
    expect(
      GeofenceEvaluator.probable(
        target,
        geoFix(at: now.subtract(const Duration(seconds: 30))),
        now,
      ),
      isTrue,
    );
    expect(
      GeofenceEvaluator.distance(0, 179.999, 0, -179.999),
      closeTo(222.39, 0.01),
    );
    expect(GeofenceEvaluator.distance(90, 0, 90, 90), lessThan(0.001));
    expect(GeofenceEvaluator.distance(0, 0, 0, 180), closeTo(20015114.44, 0.1));
  });
  test(
    'expires before confirmation; permission revoked also discards',
    () async {
      await controller.check();
      now = now.add(const Duration(seconds: 31));
      expect(await controller.confirmedEvidence(), isNull);
      now = geoNow;
      await controller.check();
      port.state = const LocationAccess(
        supported: true,
        servicesEnabled: true,
        permission: AssistancePermission.denied,
      );
      expect(await controller.confirmedEvidence(), isNull);
    },
  );
  test(
    'background cancels outstanding callback; foreground requires a new check',
    () async {
      port.waiting = Completer();
      final reading = controller.check();
      await Future<void>.delayed(Duration.zero);
      controller.setForeground(false);
      port.waiting!.complete(geoFix());
      await reading;
      expect(controller.suggestion, isNull);
      expect(port.cancels, greaterThan(0));
      controller.setForeground(true);
      expect(controller.suggestion, isNull);
    },
  );
  test('reroute and terminal context fence late location', () async {
    port.waiting = Completer();
    final reading = controller.check();
    await Future<void>.delayed(Duration.zero);
    controller.update(null);
    port.waiting!.complete(geoFix());
    await reading;
    expect(controller.suggestion, isNull);
  });
  test('errors leave other actions independent', () async {
    port.fail = true;
    await controller.check();
    expect(controller.busy, isFalse);
    expect(controller.message, isNotNull);
  });
  for (final field in ['latitude', 'longitude', 'geofenceRadiusM', 'siteId']) {
    test('missing $field disables only target', () {
      final raw = geoTarget()..remove(field);
      expect(ArrivalTarget.parse(raw, geoTrip(), 'driver'), isNull);
    });
  }
  for (final status in ['AT_STOP', 'CANCELLING', 'CANCELLED', 'COMPLETED']) {
    test('$status disables assistance', () {
      expect(
        ArrivalTarget.parse(
          geoTarget(),
          geoTrip()..['status'] = status,
          'driver',
        ),
        isNull,
      );
    });
  }
  test('wrong driver/stop/version/equipment never retargets suggestion', () {
    for (final field in [
      'driverId',
      'stopId',
      'tripId',
      'siteId',
      'tractorId',
      'expectedTrailerId',
      'stopVersion',
      'tripVersion',
    ]) {
      final raw = geoTarget()..[field] = 'other';
      expect(ArrivalTarget.parse(raw, geoTrip(), 'driver'), isNull);
    }
  });
  test(
    'native adapter requests foreground grant and detects reduced precision',
    () async {
      final native = FakeGeolocator(),
          adapter = NativeLocationAssistance(
            platform: FakeGeolocator(),
            device: TargetPlatform.android,
          );
      expect((await adapter.access()).usable, isFalse);
      adapter.cancel();
      final granted = NativeLocationAssistance(
        platform: native,
        device: TargetPlatform.iOS,
      );
      expect((await granted.access(request: true)).usable, isTrue);
      expect(native.requests, 1);
      native.reduced = true;
      expect((await granted.access()).approximate, isTrue);
      granted.cancel();
      unawaited(native.positions.close());
    },
  );
  for (final device in [TargetPlatform.android, TargetPlatform.iOS]) {
    test(
      'native $device stops after one reading and never configures background',
      () async {
        final native = FakeGeolocator(),
            adapter = NativeLocationAssistance(
              platform: FakeGeolocator(),
              device: device,
            );
        adapter.cancel();
        final real = NativeLocationAssistance(platform: native, device: device);
        final result = real.read();
        native.positions.add(
          Position(
            latitude: 41,
            longitude: -88,
            timestamp: geoNow,
            accuracy: 25,
            altitude: 0,
            altitudeAccuracy: 0,
            heading: 0,
            headingAccuracy: 0,
            speed: 0,
            speedAccuracy: 0,
          ),
        );
        expect((await result)!.latitude, 41);
        await Future<void>.delayed(Duration.zero);
        expect(native.cancelled, isTrue);
        if (native.settings is AppleSettings) {
          expect(
            (native.settings as AppleSettings).allowBackgroundLocationUpdates,
            isFalse,
          );
        }
        if (native.settings is AndroidSettings) {
          expect(
            (native.settings as AndroidSettings).foregroundNotificationConfig,
            isNull,
          );
        }
        real.cancel();
        unawaited(native.positions.close());
      },
    );
  }
  test(
    'native cancellation and errors complete without retaining a fix',
    () async {
      final native = FakeGeolocator(),
          adapter = NativeLocationAssistance(
            platform: FakeGeolocator(),
            device: TargetPlatform.android,
          );
      adapter.cancel();
      final real = NativeLocationAssistance(
        platform: native,
        device: TargetPlatform.android,
      );
      final result = real.read();
      real.cancel();
      expect(await result, isNull);
      await native.positions.close();
    },
  );
  testWidgets('native acquisition timeout removes its observer', (
    tester,
  ) async {
    final native = FakeGeolocator(),
        adapter = NativeLocationAssistance(
          platform: FakeGeolocator(),
          device: TargetPlatform.android,
        );
    adapter.cancel();
    final real = NativeLocationAssistance(
      platform: native,
      device: TargetPlatform.android,
    );
    final result = real.read();
    await tester.pump(const Duration(seconds: 10, milliseconds: 1));
    expect(await result, isNull);
    expect(native.cancelled, isTrue);
    unawaited(native.positions.close());
    real.cancel();
  });
  test(
    'native platform exception cancels observer without location evidence',
    () async {
      final native = FakeGeolocator(),
          real = NativeLocationAssistance(
            platform: FakeGeolocator(),
            device: TargetPlatform.iOS,
          );
      real.cancel();
      final adapter = NativeLocationAssistance(
        platform: native,
        device: TargetPlatform.iOS,
      );
      final result = adapter.read();
      native.positions.addError(StateError('native'));
      expect(await result, isNull);
      expect(native.cancelled, isTrue);
      await native.positions.close();
      adapter.cancel();
    },
  );
}
