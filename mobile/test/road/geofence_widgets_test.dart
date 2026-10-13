import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/platform/location_assistance.dart';
import 'package:trailersaas_mobile/src/road/road_trip_screen.dart';

import '../session/fixtures.dart';
import 'online_execution_test.dart' show MemoryJournal, WidgetCache;
import 'arrival_assistance_test.dart'
    show FakeLocation, geoTrip, geoTarget, geoNow;

void main() {
  late MobileFixture f;
  late FakeLocation location;
  late DateTime now;
  late Map<String, dynamic> projection;
  late MemoryJournal journal;
  Future<void> start(WidgetTester tester) async {
    now = geoNow;
    location = FakeLocation();
    journal = MemoryJournal();
    f = MobileFixture(cacheFactory: WidgetCache.new)
      ..trips = [geoTrip()]
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
    await tester.runAsync(f.session.start);
    projection = {
      'trip': geoTrip(),
      'arrivalAssistance': geoTarget(),
      'currentCoupling': null,
      'tractorOperationalNumber': '12',
      'departurePrerequisitesSatisfied': false,
    };
    f.handler = (r) async {
      if (r.method == 'POST') {
        projection = {
          'trip': geoTrip()..['status'] = 'AT_STOP',
          'arrivalAssistance': null,
          'currentCoupling': null,
          'departurePrerequisitesSatisfied': false,
        };
      }
      return f.response(projection);
    };
    await tester.pumpWidget(
      MaterialApp(
        home: RoadTripScreen(
          session: f.session,
          cached: f.session.snapshot!.trips.single,
          journal: journal,
          location: location,
          clock: () => now,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(f.close);
  }

  testWidgets(
    'suggestion alone has no POST; conscious confirmation records exact source and evidence',
    (tester) async {
      await start(tester);
      expect(find.text('Confirm manual arrival'), findsOneWidget);
      await tester.tap(find.text('Check arrival location'));
      await tester.pumpAndSettle();
      expect(
        find.text('It looks like you arrived at Authorized dock.'),
        findsOneWidget,
      );
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      await tester.tap(find.text('Confirm Arrival'));
      await tester.pumpAndSettle();
      final request = f.requests.where((r) => r.method == 'POST').single;
      expect(jsonDecode(request.body), {
        'occurredAt': now.toIso8601String(),
        'source': 'GEOFENCE_CONFIRMED',
        'coordinates': {
          'latitude': 41.0,
          'longitude': -88.0,
          'accuracyM': 25.0,
        },
      });
      expect(request.url.path, endsWith('/stops/stop-A/arrivals'));
      expect(request.headers['Idempotency-Key'], isNotEmpty);
      expect(find.text('Confirm Arrival'), findsNothing);
      await close(tester);
    },
  );
  testWidgets('Not Yet suppresses prompt and keeps manual fallback', (
    tester,
  ) async {
    await start(tester);
    await tester.tap(find.text('Check arrival location'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Not Yet'));
    await tester.pumpAndSettle();
    expect(find.text('Confirm Arrival'), findsNothing);
    expect(find.text('Check arrival location'), findsNothing);
    expect(find.text('Confirm manual arrival'), findsOneWidget);
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    await close(tester);
  });
  testWidgets('double tap Confirm Arrival sends exactly one human command', (
    tester,
  ) async {
    await start(tester);
    await tester.tap(find.text('Check arrival location'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Confirm Arrival'));
    await tester.tap(find.text('Confirm Arrival'));
    await tester.pumpAndSettle();
    expect(
      f.requests.where((request) => request.method == 'POST'),
      hasLength(1),
    );
    await close(tester);
  });
  testWidgets(
    'expired UI confirmation never posts; manual still works without permission',
    (tester) async {
      await start(tester);
      await tester.tap(find.text('Check arrival location'));
      await tester.pumpAndSettle();
      now = now.add(const Duration(seconds: 31));
      await tester.tap(find.text('Confirm Arrival'));
      await tester.pumpAndSettle();
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      location.state = const LocationAccess(
        supported: true,
        servicesEnabled: false,
        permission: AssistancePermission.denied,
      );
      await tester.tap(find.text('Confirm manual arrival'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Confirm'));
      await tester.pumpAndSettle();
      expect(
        jsonDecode(
          f.requests.where((r) => r.method == 'POST').single.body,
        )['source'],
        'MANUAL',
      );
      await close(tester);
    },
  );
  testWidgets(
    'background discards suggestion; reopening Road does not reacquire GPS',
    (tester) async {
      await start(tester);
      await tester.tap(find.text('Check arrival location'));
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pumpAndSettle();
      expect(location.cancels, greaterThan(0));
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(find.text('Confirm Arrival'), findsNothing);
      expect(location.reads, 1);
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      await close(tester);
    },
  );
  testWidgets('account change hides old destination and fences confirmation', (
    tester,
  ) async {
    await start(tester);
    await tester.tap(find.text('Check arrival location'));
    await tester.pumpAndSettle();
    await tester.runAsync(f.session.signOut);
    await tester.pumpAndSettle();
    expect(find.text('Confirm Arrival'), findsNothing);
    expect(find.textContaining('Authorized dock'), findsNothing);
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    await close(tester);
  });
}
