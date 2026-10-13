import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/road/driver_updates_store.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/road/road_trip_controller.dart';

import '../session/fixtures.dart';
import 'arrival_assistance_test.dart';
import 'online_execution_test.dart' show MemoryJournal;

const activeTrip = '55555555-5555-4555-8555-555555555555';
void main() {
  late MobileFixture f;
  late DriverUpdatesStore updates;
  late RoadTripController controller;
  late FakeLocation location;
  late Map<String, dynamic> trip, target;
  late List<Map<String, dynamic>> instructions;
  late int reads;
  setUp(() async {
    trip = geoTrip()..['id'] = activeTrip;
    target = geoTarget()..['tripId'] = activeTrip;
    f = MobileFixture()
      ..trips = [trip]
      ..memberships = [
        membership(
          'A',
          capabilities: [
            'TRIP_VIEW_ASSIGNED',
            'TRIP_EXECUTE_ASSIGNED',
            'ARRIVAL_CONFIRM',
          ],
        ),
      ];
    await f.session.start();
    reads = 0;
    instructions = [];
    f.handler = (r) async {
      if (r.url.path.endsWith('/updates')) {
        return f.response({'items': instructions, 'nextCursor': null});
      }
      if (r.url.path.endsWith('/driver/me/trips/$activeTrip')) {
        reads++;
        return f.response({
          'trip': trip,
          'arrivalAssistance': target,
          'currentCoupling': null,
          'departurePrerequisitesSatisfied': false,
        });
      }
      if (r.url.path.endsWith('/api/v1/me')) {
        return f.response({'userId': 'user-A', 'memberships': f.memberships});
      }
      if (r.url.path.endsWith('/driver/me')) {
        return f.response({'id': 'driver', 'name': 'Jane', 'active': true});
      }
      return f.response({
        'items': [trip],
        'nextCursor': null,
      });
    };
    updates = DriverUpdatesStore(f.db, f.session);
    location = FakeLocation();
    controller = RoadTripController(
      f.session,
      activeTrip,
      MemoryJournal(),
      location: location,
      clock: () => geoNow,
      updates: updates,
    );
    await controller.load();
  });
  tearDown(() async {
    controller.dispose();
    await updates.shutdown();
    await f.close();
  });
  for (final mode in [
    'reroute',
    'instructions',
    'reassigned',
    'trailer',
    'cancellation',
    'completed',
    'workRemoved',
  ]) {
    test(
      '$mode download fences old suggestion and refreshes live authority without push',
      () async {
        await controller.assistance.check();
        expect(controller.assistance.suggestion, isNotNull);
        location.waiting = Completer();
        final lateReading = controller.assistance.check();
        await Future<void>.delayed(Duration.zero);
        trip = {...trip, 'version': 2};
        target = {...target, 'tripVersion': 2};
        switch (mode) {
          case 'reroute':
            trip['stops'] = [
              {
                ...(trip['stops'] as List).single as Map,
                'siteId': 'site-B',
                'version': 2,
              },
            ];
            target = {
              ...target,
              'siteId': 'site-B',
              'stopVersion': 2,
              'latitude': 42.0,
              'destinationName': 'New authorized dock',
            };
          case 'instructions':
            (trip['stops'] as List).single['instructions'] =
                'Call new dispatcher';
          case 'reassigned':
            trip['currentDriverId'] = 'new-driver';
            target['driverId'] = 'new-driver';
          case 'trailer':
            trip['plannedInitialTrailerId'] = 'new-trailer';
            target['expectedTrailerId'] = 'new-trailer';
          case 'cancellation':
            trip['status'] = 'CANCELLING';
          case 'completed':
            trip['status'] = 'COMPLETED';
          case 'workRemoved':
            f.session.snapshot!.trips.clear();
        }
        instructions.add({
          'id': operationUuid(),
          'tripId': activeTrip,
          'updateType': 'TRIP_REROUTED',
          'occurredAt': geoNow.toIso8601String(),
          'payload': {'instructions': 'Operations: $mode', 'tripVersion': 2},
        });
        await updates.refresh();
        location.waiting!.complete(geoFix());
        await lateReading;
        await Future<void>.delayed(const Duration(milliseconds: 20));
        expect(controller.assistance.suggestion, isNull);
        expect(location.cancels, greaterThan(0));
        await controller.confirmAssistedArrival();
        expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
        expect(reads, greaterThan(1));
        expect(
          (await updates.watch('user-A', 'A').first).single.projectionJson,
          contains('Operations: $mode'),
        );
        final before = reads;
        await updates.refresh();
        await Future<void>.delayed(Duration.zero);
        expect(
          reads,
          before,
          reason: 'historical tail dedup must not repeatedly reset assistance',
        );
      },
    );
  }
  test('current Tenant/account change fences late downloaded updates and callbacks', () async {
    location.waiting = Completer();
    final reading = controller.assistance.check();
    await Future<void>.delayed(Duration.zero);
    await f.session.signOut();
    location.waiting!.complete(geoFix());
    await reading;
    expect(controller.assistance.suggestion, isNull);
    expect(controller.canRecord, isFalse);
    await controller.confirmAssistedArrival();
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
  });
  test(
    'failed authoritative reload cannot restore previous geographic context',
    () async {
      await controller.assistance.check();
      expect(controller.assistance.suggestion, isNotNull);
      f.handler = (request) async => throw StateError('read unavailable');
      await controller.load();
      expect(controller.detail, isNull);
      expect(controller.assistance.target, isNull);
      await controller.confirmAssistedArrival();
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    },
  );
  test(
    'malformed new instruction does not publish success or advance cursor',
    () async {
      await controller.assistance.check();
      final events = <DriverOperationalChange>[];
      final subscription = updates.operationalChanges.listen(events.add);
      instructions.add({
        'id': 'malformed',
        'tripId': activeTrip,
        'payload': {},
      });
      await expectLater(updates.refresh(), throwsA(isA<FormatException>()));
      expect(events.where((event) => event.downloaded), isEmpty);
      expect(await f.db.select(f.db.driverUpdateCursors).get(), isEmpty);
      await subscription.cancel();
    },
  );
  test('success signal observes committed instruction and cursor, tail emits no duplicates', () async {
    final observations = <Future<bool>>[];
    final subscription = updates.operationalChanges.listen((event) {
      if (event.downloaded) {
        observations.add(() async {
          final rows = await f.db.select(f.db.cachedDriverUpdates).get();
          final cursors = await f.db.select(f.db.driverUpdateCursors).get();
          return rows.isNotEmpty && cursors.isNotEmpty;
        }());
      }
    });
    instructions.add({
      'id': operationUuid(),
      'tripId': activeTrip,
      'updateType': 'TRIP_REROUTED',
      'occurredAt': geoNow.toIso8601String(),
      'payload': {'instructions': 'New instruction'},
    });
    await updates.refresh();
    expect(await Future.wait(observations), [true]);
    await updates.refresh();
    expect(observations, hasLength(1));
    await subscription.cancel();
  });
}
