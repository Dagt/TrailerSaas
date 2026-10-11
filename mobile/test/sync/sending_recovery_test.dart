import 'dart:async';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart';
import 'command_store_test.dart' show intent;

void main() {
  late Directory directory;
  late MobileFixture f;
  late CommandStore store;
  late ReplayWorker worker;
  late DateTime now;

  Future<http.Response> reads(http.Request request) async {
    if (request.url.path.endsWith('/api/v1/me')) {
      return f.response({
        'userId': f.auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
        'memberships': f.memberships,
      });
    }
    if (request.url.path.endsWith('/driver/me')) {
      return f.response({'id': 'driver', 'name': 'Jane', 'active': true});
    }
    if (request.url.path.contains('/driver/me/trips/')) {
      final id = request.url.path.split('/').last;
      return f.response({
        'trip': work(id),
        'currentCoupling': null,
        'departurePrerequisitesSatisfied': false,
      });
    }
    return f.response({'items': f.trips, 'nextCursor': null});
  }

  Future<void> sessionReady() {
    if (f.session.state == SessionState.ready) return Future.value();
    final ready = Completer<void>();
    void changed() {
      if (f.session.state == SessionState.ready && !ready.isCompleted) {
        f.session.removeListener(changed);
        ready.complete();
      }
    }

    f.session.addListener(changed);
    addTearDown(() => f.session.removeListener(changed));
    return ready.future;
  }

  void disconnect() {
    f.network.status = ConnectivityStatus.unavailable;
    f.network.events.add(ConnectivityStatus.unavailable);
    expect(f.session.state, SessionState.offline);
  }

  Future<void> reconnect() async {
    final ready = sessionReady();
    f.network.status = ConnectivityStatus.available;
    f.network.events.add(ConnectivityStatus.available);
    await ready;
  }

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('sending-recovery-');
    now = DateTime.now().toUtc();
    f = MobileFixture(
      database: AppDatabase(
        NativeDatabase(File('${directory.path}/queue.sqlite')),
      ),
      apiClock: () => now,
    );
    await f.session.start();
    store = CommandStore(f.db, clock: () => now);
    worker = ReplayWorker(
      f.session,
      store,
      MemoryJournal(),
      scheduleTimers: false,
      jitter: () => 0.5,
    );
  });
  tearDown(() async {
    await worker.shutdown();
    await f.close();
    await directory.delete(recursive: true);
  });

  test('same-scope regression: lost commit response while offline must not strand SENDING', () async {
    final command = intent();
    await store.enqueue('user-A', 'A', command);
    final entered = Completer<void>(), response = Completer<http.Response>();
    final requests = <http.Request>[];
    var physicalFacts = 0;
    f.handler = (request) async {
      if (request.method == 'GET') return reads(request);
      requests.add(request);
      if (requests.length == 1) {
        physicalFacts++; // Test-only server ledger commits before response loss.
        entered.complete();
        return response.future;
      }
      return f.response({
        'trip': work('trip-A'),
      }); // Same recorded ledger result.
    };
    final first = worker.sync();
    await entered.future;
    final original = (await store.list('user-A', 'A')).single;
    expect(original.state, 'SENDING');
    final originalScope = worker.scope;
    disconnect();
    response.completeError(const SocketException('committed response lost'));
    await first;
    expect((await store.list('user-A', 'A')).single.state, 'SENDING');
    await reconnect();
    expect(worker.scope, originalScope);
    await worker.sync();
    final recovered = (await store.list('user-A', 'A')).single;
    expect(recovered.state, 'ACKNOWLEDGED');
    expect(recovered.clientOperationId, original.clientOperationId);
    expect(recovered.endpointPath, original.endpointPath);
    expect(recovered.httpMethod, original.httpMethod);
    expect(recovered.payloadJson, original.payloadJson);
    expect(requests, hasLength(2));
    expect(requests[1].headers['Idempotency-Key'], command.command.id);
    expect(requests[1].method, requests[0].method);
    expect(requests[1].url, requests[0].url);
    expect(requests[1].body, requests[0].body);
    expect(physicalFacts, 1);
  });

  test('automatic reconnect recovers exact key; dependencies wait and independent chains continue', () async {
    f.trips = [work('trip-A'), work('trip-B')];
    await f.session.refresh();
    final first = intent(),
        dependent = intent(dependencies: [first.command.id]),
        independent = intent(trip: 'trip-B', stop: 'stop-trip-B');
    for (final command in [first, dependent, independent]) {
      await store.enqueue('user-A', 'A', command);
    }
    final entered = Completer<void>(), response = Completer<http.Response>();
    final sends = <String>[], facts = <String>{}, attempts = <String, int>{};
    var active = 0, maxActive = 0;
    f.handler = (request) async {
      if (request.method == 'GET') return reads(request);
      final key = request.headers['Idempotency-Key']!;
      sends.add(key);
      attempts[key] = (attempts[key] ?? 0) + 1;
      active++;
      if (active > maxActive) maxActive = active;
      try {
        if (key == first.command.id && attempts[key] == 1) {
          facts.add(key); // Commit, then lose the acknowledgment.
          entered.complete();
          return await response.future;
        }
        if (key == first.command.id && attempts[key] == 2) {
          return f.response({'code': 'TEMPORARY'}, 503);
        }
        if (key == dependent.command.id) {
          expect(
            (await store.find('user-A', 'A', first.command.id))!.state,
            'ACKNOWLEDGED',
          );
        }
        facts.add(key);
        return f.response({
          'trip': work(key == independent.command.id ? 'trip-B' : 'trip-A'),
        });
      } finally {
        active--;
      }
    };
    worker.start();
    await entered.future;
    disconnect();
    final finishing = worker.sync(); // Join the existing send, no second POST.
    response.completeError(const SocketException('lost committed response'));
    await finishing;
    expect(
      (await store.find('user-A', 'A', first.command.id))!.state,
      'SENDING',
    );
    expect(
      (await store.find('user-A', 'A', dependent.command.id))!.state,
      'QUEUED',
    );
    await reconnect();
    await store
        .watch('user-A', 'A')
        .firstWhere(
          (rows) =>
              rows
                  .singleWhere(
                    (r) => r.clientOperationId == independent.command.id,
                  )
                  .state ==
              'ACKNOWLEDGED',
        );
    expect(
      (await store.find('user-A', 'A', first.command.id))!.state,
      'RETRYABLE_FAILURE',
    );
    expect(
      (await store.find('user-A', 'A', dependent.command.id))!.state,
      'QUEUED',
    );
    expect(sends, [first.command.id, first.command.id, independent.command.id]);
    now = now.add(const Duration(minutes: 1));
    f.network.events.add(
      ConnectivityStatus.available,
    ); // No manual sync/restart/timer.
    await store
        .watch('user-A', 'A')
        .firstWhere((rows) => rows.every((r) => r.state == 'ACKNOWLEDGED'));
    expect(sends, [
      first.command.id,
      first.command.id,
      independent.command.id,
      first.command.id,
      dependent.command.id,
    ]);
    expect(facts, {
      first.command.id,
      independent.command.id,
      dependent.command.id,
    });
    expect(attempts[first.command.id], 3);
    expect(maxActive, 1);
    final firstRequests = f.requests
        .where(
          (r) =>
              r.method == 'POST' &&
              r.headers['Idempotency-Key'] == first.command.id,
        )
        .toList();
    expect(firstRequests.map((r) => r.body).toSet(), {
      firstRequests.first.body,
    });
    expect(firstRequests.map((r) => r.url).toSet(), {firstRequests.first.url});
    expect(firstRequests.map((r) => r.method).toSet(), {'POST'});
  });

  for (final interruption in [
    'reconnect',
    'authoritative refresh',
    'Firebase identity renewal',
  ]) {
    test(
      '$interruption while HTTP is in flight cannot recover a live claim across workers',
      () async {
        final command = intent();
        await store.enqueue('user-A', 'A', command);
        final entered = Completer<void>(),
            response = Completer<http.Response>();
        var posts = 0, active = 0, maxActive = 0;
        f.handler = (request) async {
          if (request.method == 'GET') return reads(request);
          posts++;
          active++;
          if (active > maxActive) maxActive = active;
          if (!entered.isCompleted) entered.complete();
          try {
            return await response.future;
          } finally {
            active--;
          }
        };
        worker.start();
        await entered.future;
        final other = ReplayWorker(
          f.session,
          store,
          MemoryJournal(),
          scheduleTimers: false,
        );
        final queuedPass = other.sync();
        if (interruption == 'reconnect') {
          disconnect();
          await reconnect();
        } else if (interruption == 'authoritative refresh') {
          await f.session.refresh();
        } else {
          await f.auth.token(forceRefresh: true);
          f.auth.events.add(
            'firebase-A',
          ); // ID-token renewal is not a new User.
        }
        final requestedPass = worker.sync();
        expect((await store.list('user-A', 'A')).single.state, 'SENDING');
        expect((await store.list('user-A', 'A')).single.attemptCount, 1);
        expect(posts, 1);
        expect(active, 1);
        response.complete(f.response({'trip': work('trip-A')}));
        await Future.wait([queuedPass, requestedPass]);
        await other.shutdown();
        expect((await store.list('user-A', 'A')).single.state, 'ACKNOWLEDGED');
        expect(posts, 1);
        expect(maxActive, 1);
        expect(
          f.requests
              .where((r) => r.method == 'POST')
              .single
              .headers['Idempotency-Key'],
          command.command.id,
        );
      },
    );
  }

  test('lost confirmation during ready-loading-ready reconciliation recovers automatically', () async {
    final command = intent();
    await store.enqueue('user-A', 'A', command);
    final entered = Completer<void>(),
        response = Completer<http.Response>(),
        reading = Completer<void>(),
        finishRefresh = Completer<void>();
    var holdMe = false, posts = 0;
    f.handler = (request) async {
      if (request.method == 'GET') {
        if (holdMe && request.url.path.endsWith('/api/v1/me')) {
          reading.complete();
          await finishRefresh.future;
        }
        return reads(request);
      }
      posts++;
      if (posts == 1) {
        entered.complete();
        return response.future;
      }
      return f.response({'trip': work('trip-A')});
    };
    worker.start();
    await entered.future;
    final sending = worker.sync();
    holdMe = true;
    final refreshing = f.session.refresh();
    await reading.future;
    expect(f.session.state, SessionState.loading);
    response.completeError(TimeoutException('response lost during validation'));
    await sending;
    expect((await store.list('user-A', 'A')).single.state, 'SENDING');
    holdMe = false;
    finishRefresh.complete();
    await refreshing;
    await store
        .watch('user-A', 'A')
        .firstWhere((rows) => rows.single.state == 'ACKNOWLEDGED');
    expect(posts, 2);
    expect(
      f.requests
          .where((r) => r.method == 'POST')
          .map((r) => r.headers['Idempotency-Key'])
          .toSet(),
      {command.command.id},
    );
  });

  test('401 token-refresh attempt interrupted offline recovers original operation automatically', () async {
    final command = intent();
    await store.enqueue('user-A', 'A', command);
    final refreshedPost = Completer<void>(),
        response = Completer<http.Response>();
    var posts = 0, physicalFacts = 0;
    f.auth.forceRefreshes.clear();
    f.handler = (request) async {
      if (request.method == 'GET') return reads(request);
      posts++;
      if (posts == 1) {
        return f.response({'code': 'AUTHENTICATION_REQUIRED'}, 401);
      }
      if (posts == 2) {
        physicalFacts++;
        refreshedPost.complete();
        return response.future;
      }
      return f.response({'trip': work('trip-A')});
    };
    worker.start();
    await refreshedPost.future;
    expect(f.auth.forceRefreshes.take(2), [false, true]);
    disconnect();
    final finishing = worker.sync();
    response.completeError(
      const SocketException('lost renewed-token response'),
    );
    await finishing;
    expect((await store.list('user-A', 'A')).single.state, 'SENDING');
    await reconnect();
    await store
        .watch('user-A', 'A')
        .firstWhere((rows) => rows.single.state == 'ACKNOWLEDGED');
    expect(posts, 3);
    expect(physicalFacts, 1);
    expect(
      f.requests
          .where((r) => r.method == 'POST')
          .map((r) => r.headers['Idempotency-Key'])
          .toSet(),
      {command.command.id},
    );
  });

  for (final change in ['User', 'Tenant']) {
    test(
      '$change change during interruption cannot expose or replay the original namespace',
      () async {
        final command = intent();
        await store.enqueue('user-A', 'A', command);
        f.memberships = [membership('A'), membership('B')];
        final entered = Completer<void>(),
            response = Completer<http.Response>();
        var posts = 0;
        f.handler = (request) async {
          if (request.method == 'GET') return reads(request);
          posts++;
          if (posts == 1) {
            entered.complete();
            return response.future;
          }
          return f.response({'trip': work('trip-A')});
        };
        final sending = worker.sync();
        await entered.future;
        disconnect();
        f.network.status = ConnectivityStatus.available;
        if (change == 'User') {
          await f.session.signOut();
          f.auth.uid = 'firebase-B';
          await f.session.refresh();
          await f.session.selectTenant(f.session.user!.memberships.first);
        } else {
          await f.session.refresh();
          await f.session.selectTenant(f.session.user!.memberships.last);
        }
        final current = f.session.snapshot!;
        final waiting = worker
            .sync(); // Wait for old live POST; never reset it now.
        response.complete(f.response({'trip': work('trip-A')}));
        await Future.wait([sending, waiting]);
        expect(f.session.snapshot, same(current));
        expect(
          await store.list(current.userId, current.membership.tenantId),
          isEmpty,
        );
        expect(
          (await store.find('user-A', 'A', command.command.id))!.state,
          'SENDING',
        );
        expect(posts, 1);
        if (change == 'User') {
          await f.session.signOut();
          f.auth.uid = 'firebase-A';
          await f.session.refresh();
          await f.session.selectTenant(f.session.user!.memberships.first);
        } else {
          await f.session.selectTenant(f.session.user!.memberships.first);
        }
        await worker.sync();
        expect(
          (await store.find('user-A', 'A', command.command.id))!.state,
          'ACKNOWLEDGED',
        );
        expect(posts, 2);
        expect(
          f.requests
              .where((r) => r.method == 'POST')
              .every(
                (r) =>
                    r.url.path.contains('/tenants/A/') &&
                    r.headers['Idempotency-Key'] == command.command.id,
              ),
          isTrue,
        );
      },
    );
  }

  test('ACK survives failed refresh: no self-retry loop or dependent POST before reconnect', () async {
    final command = intent(), dependent = intent(dependencies: []);
    await store.enqueue('user-A', 'A', command);
    await store.enqueue(
      'user-A',
      'A',
      CommandIntent(dependent.command, dependencies: [command.command.id]),
    );
    var onlineReads = false, posts = 0, failedRefreshes = 0;
    f.handler = (request) async {
      if (request.method == 'GET') {
        if (!onlineReads) {
          failedRefreshes++;
          throw const SocketException('API unavailable');
        }
        return reads(request);
      }
      posts++;
      return f.response({'trip': work('trip-A')});
    };
    final idle = Completer<void>();
    var observedSending = false;
    void stopped() {
      if (worker.sending) observedSending = true;
      if (observedSending && !worker.sending && !idle.isCompleted) {
        idle.complete();
      }
    }

    worker.addListener(stopped);
    worker.start();
    await idle.future;
    worker.removeListener(stopped);
    expect(
      (await store.find('user-A', 'A', command.command.id))!.state,
      'ACKNOWLEDGED',
    );
    expect(
      (await store.find('user-A', 'A', dependent.command.id))!.state,
      'QUEUED',
    );
    expect(f.session.state, SessionState.offline);
    expect(posts, 1);
    expect(failedRefreshes, 1);
    onlineReads = true;
    f.network.events.add(ConnectivityStatus.available);
    await store
        .watch('user-A', 'A')
        .firstWhere((rows) => rows.every((r) => r.state == 'ACKNOWLEDGED'));
    expect(posts, 2);
    expect(
      f.requests
          .where((r) => r.method == 'POST')
          .map((r) => r.headers['Idempotency-Key']),
      [command.command.id, dependent.command.id],
    );
  });
}
