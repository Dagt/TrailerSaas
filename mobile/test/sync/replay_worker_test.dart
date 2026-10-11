import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/sync/road_command_capture.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart';
import 'command_store_test.dart' show intent;

void main() {
  late MobileFixture f;
  late CommandStore store;
  late ReplayWorker worker;
  late DateTime now;
  Future<http.Response> reads(http.Request r) async {
    if (r.url.path.endsWith('/api/v1/me')) {
      return f.response({
        'userId': f.auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
        'memberships': f.memberships,
      });
    }
    if (r.url.path.endsWith('/driver/me')) {
      return f.response({'id': 'driver', 'name': 'Jane', 'active': true});
    }
    return f.response({'items': f.trips, 'nextCursor': null});
  }

  Map<String, dynamic> confirmed([String trip = 'trip-A']) => {
    'trip': work(trip),
    'tractorOperationalNumber': 'Truck 12',
    'currentCoupling': null,
    'departurePrerequisitesSatisfied': false,
  };
  Future<LocalCommand> queue({
    String trip = 'trip-A',
    List<String> dependencies = const [],
  }) async {
    final c = intent(trip: trip, dependencies: dependencies);
    await store.enqueue('user-A', 'A', c);
    return (await store.find('user-A', 'A', c.command.id))!;
  }

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

  for (final status in [408, 429, 500, 503]) {
    test(
      '$status persists retry/backoff and same key/path/body; no immediate blind retry',
      () async {
        final c = await queue();
        final posts = <http.Request>[];
        f.handler = (r) async {
          if (r.method == 'GET') return reads(r);
          posts.add(r);
          return posts.length == 1
              ? http.Response(
                  jsonEncode({
                    'code': 'TEMPORARY_FAILURE',
                    'detail': 'private-server-text',
                  }),
                  status,
                  headers: status == 429 ? {'retry-after': '120'} : {},
                )
              : f.response(confirmed());
        };
        await worker.sync();
        var row = (await store.list('user-A', 'A')).single;
        expect(row.state, 'RETRYABLE_FAILURE');
        expect(row.attemptCount, 1);
        expect(row.nextRetryAt, isNotNull);
        expect(row.payloadJson, c.payloadJson);
        expect(row.lastHttpStatus, status);
        if (status == 429) {
          expect(
            row.nextRetryAt!.isAfter(now.add(const Duration(seconds: 119))),
            isTrue,
          );
        }
        await worker.sync();
        expect(posts, hasLength(1));
        now = now.add(const Duration(minutes: 3));
        await worker.sync();
        row = (await store.list('user-A', 'A')).single;
        expect(row.state, 'ACKNOWLEDGED');
        expect(row.ackedAt, isNotNull);
        expect(posts, hasLength(2));
        expect(posts[0].headers['Idempotency-Key'], c.clientOperationId);
        expect(
          posts[1].headers['Idempotency-Key'],
          posts[0].headers['Idempotency-Key'],
        );
        expect(posts[1].body, posts[0].body);
        expect(posts[1].url, posts[0].url);
        expect(jsonEncode(row.toJson()), isNot(contains('private-bearer')));
        expect(
          jsonEncode(row.toJson()),
          isNot(contains('private-server-text')),
        );
      },
    );
  }
  for (final failure in [
    'transport',
    'timeout',
    'malformed-success',
    'malformed-status',
    'malformed-400',
  ]) {
    test(
      '$failure never acknowledges uncertain result or changes identity',
      () async {
        final c = await queue();
        var posts = 0;
        f.handler = (r) async {
          if (r.method == 'GET') return reads(r);
          posts++;
          if (failure == 'transport') throw const SocketException('lost');
          if (failure == 'timeout') throw TimeoutException('lost');
          if (failure == 'malformed-status') {
            return f.response({
              'trip': {...work('trip-A'), 'status': 'ANY'},
            });
          }
          return http.Response(
            failure == 'malformed-success' ? '{}' : '<html>proxy</html>',
            failure == 'malformed-400' ? 400 : 200,
          );
        };
        await worker.sync();
        final row = (await store.list('user-A', 'A')).single;
        expect(row.state, 'RETRYABLE_FAILURE');
        expect(row.ackedAt, isNull);
        expect(row.clientOperationId, c.clientOperationId);
        expect(row.payloadJson, c.payloadJson);
        expect(posts, 1);
      },
    );
  }
  test('lost response after commit replays exact key once and reconciles a NEW projection', () async {
    final c = await queue();
    final ledger = <String, Map<String, dynamic>>{};
    var physicalFacts = 0, attempts = 0;
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      attempts++;
      final key = r.headers['Idempotency-Key']!;
      if (!ledger.containsKey(key)) {
        ledger[key] = confirmed();
        physicalFacts++;
        final current = work('trip-A')
          ..['version'] = 99
          ..['status'] = 'AT_STOP';
        f.trips = [current];
        throw const SocketException('response lost after commit');
      }
      return f.response(ledger[key]!);
    };
    await worker.sync();
    now = now.add(const Duration(minutes: 1));
    await worker.sync();
    await worker.sync();
    expect(physicalFacts, 1);
    expect(attempts, 2);
    expect(ledger.keys, [c.clientOperationId]);
    expect((await store.list('user-A', 'A')).single.state, 'ACKNOWLEDGED');
    expect(f.session.snapshot!.trips.single.data['version'], 99);
    expect(f.session.snapshot!.trips.single.status, 'AT_STOP');
  });
  test('401 refreshes Firebase once and reuses exact request; no duplicate operation', () async {
    await queue();
    var posts = 0;
    f.auth.forceRefreshes.clear();
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts++;
      return posts == 1
          ? f.response({'code': 'AUTHENTICATION_REQUIRED'}, 401)
          : f.response(confirmed());
    };
    await worker.sync();
    expect(posts, 2);
    expect(f.auth.forceRefreshes.take(2), [false, true]);
    final requests = f.requests.where((r) => r.method == 'POST').toList();
    expect(
      requests[0].headers['Idempotency-Key'],
      requests[1].headers['Idempotency-Key'],
    );
    expect(requests[0].body, requests[1].body);
    expect((await store.list('user-A', 'A')).single.state, 'ACKNOWLEDGED');
  });
  for (final status in [401, 403, 404]) {
    test(
      '$status surfaces authorization conflict, revalidates and retains original intent',
      () async {
        final c = await queue();
        var posts = 0;
        f.handler = (r) async {
          if (r.method == 'GET') return reads(r);
          posts++;
          f.trips = [];
          return f.response({
            'code': status == 404 ? 'RESOURCE_NOT_FOUND' : 'CAPABILITY_DENIED',
            'detail': 'Safe denial',
          }, status);
        };
        await worker.sync();
        final row = (await store.list('user-A', 'A')).single;
        expect(row.state, 'CONFLICT');
        expect(row.clientOperationId, c.clientOperationId);
        expect(row.payloadJson, c.payloadJson);
        expect(posts, status == 401 ? 2 : 1);
        expect(f.session.snapshot!.trips, isEmpty);
        final issue = (await f.db.select(f.db.localSyncIssues).get()).single;
        expect(issue.category, 'AUTHORIZATION');
        expect(issue.resolvedAt, isNull);
        await worker.sync();
        expect(posts, status == 401 ? 2 : 1);
      },
    );
  }
  for (final code in [
    'INVALID_STATE_TRANSITION',
    'STALE_VERSION',
    'RESOURCE_CONFLICT',
    'IDEMPOTENCY_KEY_REUSED',
    'EVIDENCE_REQUIRED',
  ]) {
    test(
      '409 $code requires review, never deletes or silently changes payload',
      () async {
        final c = await queue();
        var posts = 0;
        f.handler = (r) async {
          if (r.method == 'GET') return reads(r);
          posts++;
          return f.response({'code': code}, 409);
        };
        await worker.sync();
        await worker.sync();
        final row = (await store.list('user-A', 'A')).single;
        expect(row.state, 'CONFLICT');
        expect(row.lastErrorCode, code);
        expect(row.payloadJson, c.payloadJson);
        expect(posts, 1);
        if (code == 'IDEMPOTENCY_KEY_REUSED') {
          await store.retryReviewed('user-A', 'A', c.clientOperationId);
          expect((await store.list('user-A', 'A')).single.state, 'CONFLICT');
        }
      },
    );
  }
  test('400 validation is visible permanent failure, not discarded', () async {
    await queue();
    f.handler = (r) async => r.method == 'GET'
        ? reads(r)
        : f.response({'code': 'VALIDATION_FAILED'}, 400);
    await worker.sync();
    expect((await store.list('user-A', 'A')).single.state, 'PERMANENT_FAILURE');
    expect(
      (await f.db.select(f.db.localSyncIssues).get()).single.category,
      'VALIDATION',
    );
  });
  test('dependent commands wait through uncertainty; independent chain can continue', () async {
    final a = await queue(),
        b = await queue(dependencies: [a.clientOperationId]);
    final independent = await queue(trip: 'trip-B');
    final sent = <String>[];
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      final id = r.headers['Idempotency-Key']!;
      sent.add(id);
      if (id == a.clientOperationId) {
        return f.response({'code': 'TEMPORARY'}, 503);
      }
      return f.response(
        confirmed(id == independent.clientOperationId ? 'trip-B' : 'trip-A'),
      );
    };
    await worker.sync();
    expect(sent, [a.clientOperationId, independent.clientOperationId]);
    expect(
      (await store.find('user-A', 'A', b.clientOperationId))!.state,
      'QUEUED',
    );
  });
  test('conflict blocks descendants but independent commands sync; explicit review can resolve chain', () async {
    final a = await queue(),
        b = await queue(dependencies: [a.clientOperationId]),
        c = await queue(dependencies: [b.clientOperationId]),
        independent = await queue(trip: 'trip-B');
    var fail = true;
    final sent = <String>[];
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      final id = r.headers['Idempotency-Key']!;
      sent.add(id);
      if (id == a.clientOperationId && fail) {
        return f.response({'code': 'RESOURCE_CONFLICT'}, 409);
      }
      return f.response(
        confirmed(id == independent.clientOperationId ? 'trip-B' : 'trip-A'),
      );
    };
    await worker.sync();
    expect(
      (await store.find('user-A', 'A', b.clientOperationId))!.state,
      'BLOCKED',
    );
    // Run a further dependency pass to propagate a multi-level block, without POSTs.
    await worker.sync();
    expect(
      (await store.find('user-A', 'A', c.clientOperationId))!.state,
      'BLOCKED',
    );
    expect(sent, [a.clientOperationId, independent.clientOperationId]);
    fail = false;
    await store.retryReviewed('user-A', 'A', a.clientOperationId);
    await worker.sync();
    expect(
      (await store.list('user-A', 'A')).every((r) => r.state == 'ACKNOWLEDGED'),
      isTrue,
    );
    expect(sent, [
      a.clientOperationId,
      independent.clientOperationId,
      a.clientOperationId,
      b.clientOperationId,
      c.clientOperationId,
    ]);
    expect(
      (await f.db.select(f.db.localSyncIssues).get()).every(
        (i) => i.resolvedAt != null,
      ),
      isTrue,
    );
  });
  test(
    'corrupt missing/cyclic dependency graph is detected and never sent',
    () async {
      final a = await queue(),
          b = await queue(dependencies: [a.clientOperationId]);
      await f.db.customStatement('DROP TRIGGER local_command_intent_immutable');
      await f.db.customStatement(
        'UPDATE local_commands SET dependencies_json=? WHERE client_operation_id=?',
        [
          jsonEncode([b.clientOperationId]),
          a.clientOperationId,
        ],
      );
      var posts = 0;
      f.handler = (r) async {
        if (r.method == 'POST') posts++;
        return reads(r);
      };
      await worker.sync();
      expect(posts, 0);
      expect(
        (await store.list('user-A', 'A')).every((r) => r.state == 'BLOCKED'),
        isTrue,
      );
      expect(
        (await store.list('user-A', 'A')).map((r) => r.lastErrorCode),
        everyElement('INVALID_DEPENDENCY'),
      );
    },
  );
  test('parallel workers on one SQLite file do not overlap physical resource sends', () async {
    await queue();
    final other = ReplayWorker(
      f.session,
      store,
      MemoryJournal(),
      scheduleTimers: false,
    );
    addTearDown(other.shutdown);
    var active = 0, maxActive = 0, posts = 0;
    final entered = Completer<void>(), release = Completer<void>();
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts++;
      active++;
      if (active > maxActive) maxActive = active;
      entered.complete();
      await release.future;
      active--;
      return f.response(confirmed());
    };
    final one = worker.sync();
    await entered.future;
    final two = other.sync();
    release.complete();
    await Future.wait([one, two]);
    expect(posts, 1);
    expect(maxActive, 1);
  });
  test('late old-Tenant response never acknowledges, refreshes or enables previous context', () async {
    final c = await queue(),
        arrived = Completer<void>(),
        release = Completer<void>();
    f.memberships = [membership('A'), membership('B')];
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      arrived.complete();
      await release.future;
      return f.response(confirmed());
    };
    final running = worker.sync();
    await arrived.future;
    await f.session.refresh();
    await f.session.selectTenant(f.session.user!.memberships.last);
    release.complete();
    await running;
    expect(f.session.snapshot!.membership.tenantId, 'B');
    expect(
      (await store.find('user-A', 'A', c.clientOperationId))!.state,
      'SENDING',
    );
    expect(await store.list('user-A', 'B'), isEmpty);
    final posts = f.requests.where((r) => r.method == 'POST');
    expect(posts, hasLength(1));
    expect(posts.single.url.path, contains('/tenants/A/'));
  });
  test('sign out hides preserved commands; another User never reads or replays them', () async {
    final c = await queue();
    await f.session.signOut();
    await worker.sync();
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    f.auth.uid = 'firebase-B';
    await f.session.refresh();
    await worker.sync();
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    expect(await store.list('user-B', 'A'), isEmpty);
    expect(
      (await store.find('user-A', 'A', c.clientOperationId))!.state,
      'QUEUED',
    );
  });
  test('SENDING restart uses same operation instead of new UUID', () async {
    final c = await queue();
    await store.claim(c);
    var posts = 0;
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      posts++;
      return f.response(confirmed());
    };
    await worker.sync();
    expect(posts, 1);
    expect(
      (await store.list('user-A', 'A')).single.clientOperationId,
      c.clientOperationId,
    );
    expect((await store.list('user-A', 'A')).single.state, 'ACKNOWLEDGED');
  });
  test('refresh failure after ACK pauses descendants without resending confirmed action', () async {
    final a = await queue(),
        b = await queue(dependencies: [a.clientOperationId]);
    var posts = 0;
    f.handler = (r) async {
      if (r.method == 'GET') throw const SocketException('offline');
      posts++;
      return f.response(confirmed());
    };
    await worker.sync();
    expect(posts, 1);
    expect(f.session.state, SessionState.offline);
    expect(
      (await store.find('user-A', 'A', a.clientOperationId))!.state,
      'ACKNOWLEDGED',
    );
    expect(
      (await store.find('user-A', 'A', b.clientOperationId))!.state,
      'QUEUED',
    );
  });
  test('first departure and Delivery evidence cannot be invented from downloaded work', () async {
    f.memberships = [
      membership(
        'A',
        capabilities: [
          'TRIP_VIEW_ASSIGNED',
          'TRIP_EXECUTE_ASSIGNED',
          'DEPARTURE_CONFIRM',
        ],
      ),
    ];
    await f.session.refresh();
    final owner = f.session.snapshot!;
    final capture = RoadCommandCapture(f.session, worker);
    await expectLater(
      capture.record(owner, 'trip-A', 'tenants/A/trips/trip-A/departures', {
        'nextStopId': 'stop-trip-A',
        'occurredAt': '2026-10-10T10:00:00Z',
      }),
      throwsStateError,
    );
    await expectLater(
      capture.record(
        owner,
        'trip-A',
        'tenants/A/stops/stop-trip-A/completions',
        {'occurredAt': '2026-10-10T10:00:00Z'},
      ),
      throwsStateError,
    );
    expect(await store.list('user-A', 'A'), isEmpty);
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
  });
  test('complete offline multi-Stop sequence stores intentions unchanged then authoritatively completes', () async {
    f.memberships = [
      membership(
        'A',
        capabilities: [
          'TRIP_VIEW_ASSIGNED',
          'TRIP_EXECUTE_ASSIGNED',
          'DEPARTURE_CONFIRM',
          'ARRIVAL_CONFIRM',
        ],
      ),
    ];
    final trip = work('trip-A')..['status'] = 'IN_TRANSIT';
    final first = (trip['stops'] as List).single as Map<String, dynamic>;
    first['status'] = 'EN_ROUTE';
    first['stopType'] = 'OTHER';
    final last = {
      ...first,
      'id': 'stop-return',
      'sequenceNumber': 7,
      'stopType': 'BASE_RETURN',
      'status': 'PENDING',
    };
    trip['stops'] = [first, last];
    f.trips = [trip];
    await f.session.refresh();
    final original = jsonEncode(f.session.snapshot!.trips.single.data);
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    final owner = f.session.snapshot!,
        capture = RoadCommandCapture(f.session, worker);
    final paths = [
      'stops/stop-trip-A/arrivals',
      'stops/stop-trip-A/service-starts',
      'stops/stop-trip-A/completions',
      'stops/stop-trip-A/departures',
      'stops/stop-return/arrivals',
      'stops/stop-return/service-starts',
      'stops/stop-return/completions',
    ];
    final ids = <String>[];
    for (var i = 0; i < paths.length; i++) {
      ids.add(
        await capture.record(owner, 'trip-A', 'tenants/A/${paths[i]}', {
          'occurredAt': DateTime.utc(2026, 10, 10, 10, 0, i).toIso8601String(),
          if (paths[i].endsWith('arrivals')) 'source': 'MANUAL',
          if (paths[i].endsWith('departures')) 'nextStopId': 'stop-return',
        }),
      );
    }
    expect(jsonEncode(f.session.snapshot!.trips.single.data), original);
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    final rows = await store.list('user-A', 'A');
    expect(rows, hasLength(7));
    for (var i = 1; i < rows.length; i++) {
      expect(ReplayWorker.dependencies(rows[i]), [ids[i - 1]]);
    }
    f.network.status = ConnectivityStatus.available;
    await f.session.refresh();
    var processed = 0;
    final keys = <String>[];
    f.handler = (r) async {
      if (r.method == 'GET') return reads(r);
      keys.add(r.headers['Idempotency-Key']!);
      expect(r.url.path, endsWith(paths[processed]));
      processed++;
      if (processed == 7) {
        trip['status'] = 'COMPLETED';
        last['status'] = 'COMPLETED';
        f.trips = [];
      }
      return f.response({
        'trip': {
          ...trip,
          'version': processed,
          'stops': [first, last],
        },
      });
    };
    await worker.sync();
    expect(processed, 7);
    expect(keys, ids);
    expect(
      (await store.list('user-A', 'A')).every((c) => c.state == 'ACKNOWLEDGED'),
      isTrue,
    );
    expect(f.session.snapshot!.trips, isEmpty);
  });
}
