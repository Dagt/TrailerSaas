import 'dart:convert';
import 'dart:async';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/platform/location_assistance.dart';
import 'package:trailersaas_mobile/src/road/road_trip_controller.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';

import '../session/fixtures.dart';
import '../road/online_execution_test.dart' show MemoryJournal;
import '../road/arrival_assistance_test.dart'
    show FakeLocation, geoTrip, geoTarget;

void main() {
  late Directory directory;
  late File file;
  late MobileFixture f;
  late ReplayWorker worker;
  late RoadTripController controller;
  late FakeLocation location;
  late DateTime now;
  String currentDriver = 'driver';
  bool noWork = false;
  Map<String, dynamic> detail() => {
    'trip': geoTrip(),
    'arrivalAssistance': geoTarget(),
    'currentCoupling': null,
    'tractorOperationalNumber': '12',
    'departurePrerequisitesSatisfied': false,
  };
  Future<http.Response> reads(http.Request r) async {
    if (r.url.path.endsWith('/api/v1/me')) {
      return f.response({'userId': 'user-A', 'memberships': f.memberships});
    }
    if (r.url.path.endsWith('/driver/me')) {
      return f.response({'id': currentDriver, 'name': 'Jane', 'active': true});
    }
    if (r.url.path.endsWith('/driver/me/trips/trip-A')) {
      return f.response(detail());
    }
    return f.response({
      'items': noWork ? [] : [geoTrip()],
      'nextCursor': null,
    });
  }

  Future<void> open() async {
    f =
        MobileFixture(
            database: AppDatabase(NativeDatabase(file)),
            apiClock: () => now,
          )
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
    await f.session.start();
    f.handler = reads;
    worker = ReplayWorker(
      f.session,
      CommandStore(f.db, clock: () => now),
      MemoryJournal(),
      scheduleTimers: false,
      jitter: () => 0.5,
    );
    controller = RoadTripController(
      f.session,
      'trip-A',
      MemoryJournal(),
      worker: worker,
      location: location,
      clock: () => now,
    );
    await controller.load();
    await Future<void>.delayed(const Duration(milliseconds: 20));
  }

  Future<void> offlineCapture() async {
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    await controller.load();
    location.fix = assistanceFixForTest(now);
    await controller.assistance.check();
    expect(controller.assistance.suggestion, isNotNull);
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    await controller.confirmAssistedArrival();
    expect((await worker.store.list('user-A', 'A')).single.state, 'QUEUED');
    expect(f.session.snapshot!.trips.single.status, 'IN_TRANSIT');
    expect(f.session.snapshot!.trips.single.stops.single['status'], 'EN_ROUTE');
  }

  setUp(() async {
    currentDriver = 'driver';
    noWork = false;
    directory = await Directory.systemTemp.createTemp('issue47-replay-');
    file = File('${directory.path}/road.sqlite');
    now = DateTime.now().toUtc();
    location = FakeLocation();
    await open();
  });
  tearDown(() async {
    controller.dispose();
    await worker.shutdown();
    await f.close();
    await directory.delete(recursive: true);
  });
  test('Driver rebind during HTTP cannot acknowledge or refresh another Driver context', () async {
    await offlineCapture();
    f.network.status = ConnectivityStatus.available;
    await f.session.refresh();
    final started = Completer<void>(), response = Completer<http.Response>();
    f.handler = (request) async {
      if (request.method == 'GET') {
        return reads(request);
      }
      started.complete();
      return response.future;
    };
    final send = worker.sync();
    await started.future;
    currentDriver = 'replacement-driver';
    noWork = true;
    await f.session.refresh();
    response.complete(f.response(detail()));
    await send;
    expect(f.session.snapshot!.driver!.id, 'replacement-driver');
    expect(f.session.snapshot!.trips, isEmpty);
    expect(
      (await worker.store.list('user-A', 'A')).single.state,
      isNot('ACKNOWLEDGED'),
    );
  });
  for (final status in [408, 429, 500, 503, 401, 403, 404, 409]) {
    test(
      'confirmed geofence $status uses durable existing classification and unchanged evidence',
      () async {
        await offlineCapture();
        final original = (await worker.store.list('user-A', 'A')).single;
        f.network.status = ConnectivityStatus.available;
        await f.session.refresh();
        final posts = <http.Request>[];
        f.handler = (r) async {
          if (r.method == 'GET') return reads(r);
          posts.add(r);
          return http.Response(
            jsonEncode({
              'code': status == 409
                  ? 'INVALID_STATE_TRANSITION'
                  : 'TEMPORARY_FAILURE',
              'detail': 'Unavailable',
            }),
            status,
            headers: status == 429 ? {'retry-after': '120'} : {},
          );
        };
        await worker.sync();
        final row = (await worker.store.list('user-A', 'A')).single;
        expect(
          row.state,
          status >= 500 || status == 408 || status == 429
              ? 'RETRYABLE_FAILURE'
              : 'CONFLICT',
        );
        expect(row.clientOperationId, original.clientOperationId);
        expect(row.payloadJson, original.payloadJson);
        expect(
          posts.every(
            (r) =>
                r.body == posts.first.body &&
                r.headers['Idempotency-Key'] == original.clientOperationId,
          ),
          isTrue,
        );
        expect(jsonDecode(posts.first.body)['source'], 'GEOFENCE_CONFIRMED');
      },
    );
  }
  test('lost ACK + real SQLite reopen preserves frozen request/UUID and produces one logical arrival', () async {
    await offlineCapture();
    final captured = (await worker.store.list('user-A', 'A')).single;
    final posts = <http.Request>[], serverOperations = <String>{};
    f.network.status = ConnectivityStatus.available;
    await f.session.refresh();
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts.add(r);
      serverOperations.add(r.headers['Idempotency-Key']!);
      throw const SocketException('ACK lost');
    };
    await worker.sync();
    final failed = (await worker.store.list('user-A', 'A')).single;
    expect(failed.state, 'RETRYABLE_FAILURE');
    controller.dispose();
    await worker.shutdown();
    await f.close();
    now = now.add(const Duration(minutes: 3));
    await open();
    final reopened = (await worker.store.list('user-A', 'A')).single;
    expect(reopened.payloadJson, captured.payloadJson);
    expect(reopened.clientOperationId, captured.clientOperationId);
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts.add(r);
      serverOperations.add(r.headers['Idempotency-Key']!);
      return f.response(detail());
    };
    await worker.sync();
    expect(
      (await worker.store.list('user-A', 'A')).single.state,
      'ACKNOWLEDGED',
    );
    await worker.sync();
    expect(posts, hasLength(2));
    expect(serverOperations, hasLength(1));
    expect(posts[1].body, posts[0].body);
    expect(posts[1].url, posts[0].url);
    expect(await f.db.select(f.db.localCommands).get(), hasLength(1));
    expect(await f.db.select(f.db.localSyncIssues).get(), isEmpty);
  });
  test('manual offline intent remains independent of GPS and keeps no coordinate evidence', () async {
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    await controller.load();
    location.fail = true;
    await controller.submit('tenants/A/stops/stop-A/arrivals', {
      'occurredAt': now.toIso8601String(),
      'source': 'MANUAL',
    });
    final row = (await worker.store.list('user-A', 'A')).single;
    expect(jsonDecode(row.payloadJson)['source'], 'MANUAL');
    expect(jsonDecode(row.payloadJson).containsKey('coordinates'), isFalse);
    expect(location.reads, 0);
  });
}

// Always capture test time explicitly, just as production confirms an operation.
AssistanceFix assistanceFixForTest(DateTime now) => AssistanceFix(
  latitude: 41,
  longitude: -88,
  accuracyMeters: 25,
  capturedAt: now,
);
