import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/sync/road_command_capture.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart';
import 'command_store_test.dart' show intent;

class ManualTimer implements Timer {
  ManualTimer(this.callback);
  final void Function() callback;
  bool active = true;
  @override
  bool get isActive => active;
  @override
  int get tick => active ? 0 : 1;
  @override
  void cancel() {
    active = false;
  }

  void fire() {
    if (active) {
      active = false;
      callback();
    }
  }
}

void main() {
  late MobileFixture f;
  late CommandStore store;
  late ReplayWorker worker;
  late DateTime now;
  Future<http.Response> reads(http.Request r) async {
    if (r.url.path.endsWith('/api/v1/me')) {
      return f.response({'userId': 'user-A', 'memberships': f.memberships});
    }
    if (r.url.path.endsWith('/driver/me')) {
      return f.response({'id': 'driver', 'name': 'Jane', 'active': true});
    }
    if (r.url.path.contains('/driver/me/trips/')) {
      return f.response({
        'trip': f.trips.single,
        'currentCoupling': {
          'trailerId': 'trailer',
          'operationalNumber': 'TR-123',
        },
        'tractorOperationalNumber': 'Truck 12',
        'departurePrerequisitesSatisfied': false,
      });
    }
    return f.response({'items': f.trips, 'nextCursor': null});
  }

  Map<String, dynamic> confirmed() => {'trip': work('trip-A')};
  setUp(() async {
    now = DateTime.now().toUtc();
    f = MobileFixture(apiClock: () => now);
    await f.session.start();
    store = CommandStore(f.db, clock: () => now);
    worker = ReplayWorker(
      f.session,
      store,
      MemoryJournal(),
      jitter: () => 0.5,
      scheduleTimers: false,
    );
  });
  tearDown(() async {
    await worker.shutdown();
    await f.close();
  });

  test('foreground start/reconnect validate session then automatically replay persisted work', () async {
    await store.enqueue('user-A', 'A', intent());
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    var posts = 0;
    final confirmedPost = Completer<void>();
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts++;
      confirmedPost.complete();
      return f.response(confirmed());
    };
    worker.start();
    await worker.sync();
    expect(posts, 0);
    f.network.status = ConnectivityStatus.available;
    f.network.events.add(ConnectivityStatus.available);
    await confirmedPost.future;
    await store
        .watch('user-A', 'A')
        .firstWhere((r) => r.single.state == 'ACKNOWLEDGED');
    await worker.sync();
    expect(posts, 1);
    expect(
      f.requests.where((r) => r.method == 'GET' && r.url.path.endsWith('/me')),
      isNotEmpty,
    );
  });
  test(
    'backoff timer safely retries original key without OS background execution',
    () async {
      await worker.shutdown();
      ManualTimer? timer;
      Duration? delay;
      worker = ReplayWorker(
        f.session,
        store,
        MemoryJournal(),
        jitter: () => 0.5,
        timerFactory: (wait, callback) {
          delay = wait;
          timer = ManualTimer(callback);
          return timer!;
        },
      );
      final c = intent();
      await store.enqueue('user-A', 'A', c);
      var posts = 0;
      f.handler = (r) async {
        if (r.method == 'GET') return reads(r);
        posts++;
        return posts == 1
            ? f.response({'code': 'TEMPORARY'}, 503)
            : f.response(confirmed());
      };
      await worker.sync();
      expect(posts, 1);
      expect(delay!.inMilliseconds, greaterThanOrEqualTo(1000));
      now = now.add(delay!);
      timer!.fire();
      await worker.sync();
      expect(posts, 2);
      expect(
        (await store.list('user-A', 'A')).single.clientOperationId,
        c.command.id,
      );
      expect((await store.list('user-A', 'A')).single.state, 'ACKNOWLEDGED');
    },
  );
  test('Retry-After HTTP date persists across worker recreation', () async {
    await store.enqueue('user-A', 'A', intent());
    final retryAt = now.add(const Duration(minutes: 4));
    var posts = 0;
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts++;
      return http.Response(
        jsonEncode({'code': 'RATE_LIMITED'}),
        429,
        headers: {'retry-after': HttpDate.format(retryAt)},
      );
    };
    await worker.sync();
    final row = (await store.list('user-A', 'A')).single;
    expect(
      row.nextRetryAt!.difference(retryAt).inSeconds.abs(),
      lessThanOrEqualTo(1),
    );
    await worker.shutdown();
    worker = ReplayWorker(
      f.session,
      store,
      MemoryJournal(),
      scheduleTimers: false,
    );
    await worker.sync();
    expect(posts, 1);
    expect(row.attemptCount, 1);
  });
  test('malformed authorization denial clears continuity, retains original command', () async {
    await store.enqueue('user-A', 'A', intent());
    f.handler = (r) async {
      if (r.method == 'GET') throw const SocketException('cannot refresh');
      return http.Response('<html>denied</html>', 403);
    };
    await worker.sync();
    expect((await store.list('user-A', 'A')).single.state, 'CONFLICT');
    expect(f.session.snapshot, isNull);
    expect(await f.cache.restore('firebase-A'), isNull);
  });
  test('offline Drop -> authorized replacement Hook -> completion retains exact physical intent', () async {
    f.memberships = [
      membership(
        'A',
        capabilities: [
          'TRIP_VIEW_ASSIGNED',
          'TRIP_EXECUTE_ASSIGNED',
          'TRAILER_DROP',
          'TRAILER_HOOK',
        ],
      ),
    ];
    final trip = work('trip-A')
      ..['status'] = 'AT_STOP'
      ..['plannedInitialTrailerId'] = 'replacement';
    (trip['stops'] as List).single.addAll({
      'status': 'IN_SERVICE',
      'stopType': 'DROP_HOOK',
      'locationId': 'dock',
    });
    trip['executionContext'] = {
      'currentCoupling': {'trailerId': 'old-trailer'},
    };
    f.trips = [trip];
    await f.session.refresh();
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    final owner = f.session.snapshot!,
        capture = RoadCommandCapture(f.session, worker);
    final drop = await capture.record(
      owner,
      'trip-A',
      'tenants/A/trips/trip-A/drops',
      {
        'trailerId': 'old-trailer',
        'stopId': 'stop-trip-A',
        'locationId': 'dock',
        'occurredAt': '2026-10-10T10:00:00Z',
      },
    );
    final hook = await capture.record(
      owner,
      'trip-A',
      'tenants/A/trips/trip-A/hooks',
      {
        'trailerId': 'replacement',
        'tractorId': 'tractor',
        'driverId': 'driver',
        'expectedTrailerVersion': 8,
        'occurredAt': '2026-10-10T10:00:01Z',
      },
    );
    final complete = await capture.record(
      owner,
      'trip-A',
      'tenants/A/stops/stop-trip-A/completions',
      {'occurredAt': '2026-10-10T10:00:02Z'},
    );
    expect(
      ReplayWorker.dependencies((await store.find('user-A', 'A', hook))!),
      [drop],
    );
    expect(
      ReplayWorker.dependencies((await store.find('user-A', 'A', complete))!),
      [hook],
    );
    expect(
      (jsonDecode((await store.find('user-A', 'A', hook))!.payloadJson)
          as Map)['expectedTrailerVersion'],
      8,
    );
    expect(
      (owner.trips.single.data['executionContext'] as Map)['currentCoupling'],
      {'trailerId': 'old-trailer'},
    );
    expect(owner.trips.single.stops.single['status'], 'IN_SERVICE');
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    await expectLater(
      capture.record(owner, 'trip-A', 'tenants/A/trips/trip-A/hooks', {
        'trailerId': 'unauthorized',
        'tractorId': 'tractor',
        'driverId': 'driver',
        'expectedTrailerVersion': 8,
        'occurredAt': '2026-10-10T10:00:03Z',
      }),
      throwsStateError,
    );
  });
  test('old uncertain receipt imports before new offline dependency', () async {
    final legacy = MemoryJournal(), old = intent().command;
    await legacy.save('user-A', 'A', old);
    await worker.shutdown();
    worker = ReplayWorker(f.session, store, legacy, scheduleTimers: false);
    f.memberships = [
      membership(
        'A',
        capabilities: ['TRIP_VIEW_ASSIGNED', 'TRIP_EXECUTE_ASSIGNED'],
      ),
    ];
    await f.session.refresh();
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    final owner = f.session.snapshot!;
    final next = await RoadCommandCapture(f.session, worker).record(
      owner,
      'trip-A',
      'tenants/A/stops/stop-trip-A/service-starts',
      {'occurredAt': '2026-10-10T10:00:01Z'},
    );
    expect(await legacy.read('user-A', 'A'), isNull);
    expect(
      ReplayWorker.dependencies((await store.find('user-A', 'A', next))!),
      [old.id],
    );
    expect((await store.find('user-A', 'A', old.id))!.state, 'QUEUED');
  });
  test('authoritative equipment context is re-read, never copied from saved POST response', () async {
    await store.enqueue('user-A', 'A', intent());
    f.handler = (r) async => r.method == 'GET'
        ? reads(r)
        : f.response({
            ...confirmed(),
            'currentCoupling': {'trailerId': 'stale'},
          });
    await worker.sync();
    final context =
        f.session.snapshot!.trips.single.data['executionContext'] as Map;
    expect((context['currentCoupling'] as Map)['trailerId'], 'trailer');
    expect(context['departurePrerequisitesSatisfied'], isFalse);
  });
  test('concurrent resource-related capture adds durable dependencies transactionally', () async {
    await Future.wait([
      for (var i = 0; i < 2; i++)
        store.enqueue('user-A', 'A', intent(), followResources: true),
    ]);
    final rows = await store.list('user-A', 'A');
    expect(rows, hasLength(2));
    expect(ReplayWorker.dependencies(rows.last), [
      rows.first.clientOperationId,
    ]);
    expect(rows.map((r) => r.queueSequence), [1, 2]);
  });
  test('connectivity adapter failure retains intent, ends sending and supports explicit recovery', () async {
    await store.enqueue('user-A', 'A', intent());
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    f.network.error = StateError('native adapter failed');
    await worker.sync();
    expect(worker.sending, isFalse);
    expect(worker.message, contains('retained'));
    expect((await store.list('user-A', 'A')).single.state, 'QUEUED');
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    f.network.error = null;
    f.network.status = ConnectivityStatus.available;
    f.handler = (r) async =>
        r.method == 'GET' ? reads(r) : f.response(confirmed());
    await worker.sync();
    expect((await store.list('user-A', 'A')).single.state, 'ACKNOWLEDGED');
  });
  test('fresh revocation in same User/Tenant invalidates a previously open capture context', () async {
    f.memberships = [
      membership(
        'A',
        capabilities: ['TRIP_VIEW_ASSIGNED', 'TRIP_EXECUTE_ASSIGNED'],
      ),
    ];
    await f.session.refresh();
    final old = f.session.snapshot!;
    f.memberships = [
      membership('A', capabilities: ['TRIP_VIEW_ASSIGNED']),
    ];
    f.trips = [];
    await f.session.refresh();
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    await expectLater(
      RoadCommandCapture(f.session, worker).record(
        old,
        'trip-A',
        'tenants/A/stops/stop-trip-A/service-starts',
        {'occurredAt': '2026-10-10T10:00:00Z'},
      ),
      throwsStateError,
    );
    expect(await store.list('user-A', 'A'), isEmpty);
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
  });
}
