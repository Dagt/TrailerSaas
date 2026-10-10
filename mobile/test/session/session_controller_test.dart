import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/persistence/session_cache.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';

import 'fixtures.dart';

void main() {
  for (final failure in ['replace', 'restore', 'clear']) {
    test(
      'SQLite $failure failure leaves loading and recovers on explicit refresh',
      () async {
        late FaultingCache cache;
        final f = MobileFixture(
          cacheFactory: (db) => cache = FaultingCache(db),
        );
        addTearDown(f.close);
        await f.session.start();
        cache.failure = failure;
        if (failure == 'clear') {
          f.memberships = [membership('A'), membership('B')];
          await f.session.refresh();
          await f.session.selectTenant(f.session.user!.memberships.last);
        } else {
          await f.session.refresh();
        }
        expect(f.session.state, SessionState.unavailable);
        expect(f.session.snapshot, isNull);
        expect(f.session.message, contains('Local storage'));
        cache.failure = null;
        await f.session.refresh();
        expect(f.session.state, SessionState.ready);
        expect(f.session.snapshot!.userId, 'user-A');
      },
    );
  }
  test(
    'denial with failed SQLite cleanup cannot reuse cached permissions offline',
    () async {
      late FaultingCache cache;
      final f = MobileFixture(cacheFactory: (db) => cache = FaultingCache(db));
      addTearDown(f.close);
      await f.session.start();
      cache.failure = 'clear';
      f.handler = (_) async => f.response({'code': 'CAPABILITY_DENIED'}, 403);
      await f.session.refresh();
      expect(f.session.state, SessionState.unavailable);
      f.network.status = ConnectivityStatus.unavailable;
      await f.session.refresh();
      expect(f.session.snapshot, isNull);
      expect(f.session.state, isNot(SessionState.loading));
      cache.failure = null;
      f.network.status = ConnectivityStatus.available;
      f.handler = null;
      await f.session.refresh();
      expect(f.session.state, SessionState.ready);
    },
  );
  test('logout still clears Firebase identity when local cleanup fails and can retry', () async {
    late FaultingCache cache;
    final f = MobileFixture(cacheFactory: (db) => cache = FaultingCache(db));
    addTearDown(f.close);
    await f.session.start();
    cache.failure = 'clear';
    await f.session.signOut();
    expect(f.auth.uid, isNull);
    expect(f.session.snapshot, isNull);
    expect(f.session.state, SessionState.unavailable);
    cache.failure = null;
    await f.session.signOut();
    expect(f.session.state, SessionState.signedOut);
    expect(await cache.restore('firebase-A'), isNull);
  });
  test('online restart revalidates persisted multi-Tenant selection before showing work', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    f.memberships = [membership('A'), membership('B')];
    await f.session.start();
    await f.session.selectTenant(f.session.user!.memberships.last);
    await f.session.shutdown();
    final restored = SessionController(f.auth, f.api, f.cache, f.network);
    addTearDown(restored.shutdown);
    await restored.start();
    expect(restored.state, SessionState.ready);
    expect(restored.snapshot!.membership.tenantId, 'B');
    expect(
      f.requests
          .where(
            (r) =>
                r.url.path.endsWith('/me') && !r.url.path.contains('/driver/'),
          )
          .length,
      2,
    );
  });
  test(
    'partial download outage preserves entire prior downloaded cache',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      await f.session.start();
      var pages = 0;
      f.handler = (r) async {
        if (r.url.path.endsWith('/api/v1/me')) {
          return f.response({'userId': 'user-A', 'memberships': f.memberships});
        }
        if (r.url.path.endsWith('/driver/me')) {
          return f.response({
            'id': 'driver',
            'name': 'Driver',
            'active': true,
            'version': 0,
          });
        }
        if (pages++ == 0) {
          return f.response({
            'items': [work('partial')],
            'nextCursor': 'next',
          });
        }
        throw http.ClientException('offline');
      };
      await f.session.refresh();
      expect(f.session.state, SessionState.offline);
      expect(f.session.snapshot!.trips.single.id, 'trip-A');
      expect((await f.cache.restore('firebase-A'))!.trips.single.id, 'trip-A');
    },
  );
  for (final failure in [
    'repeated cursor',
    'duplicate trip',
    'page limit',
    'malformed',
  ]) {
    test(
      '$failure aborts download without replacing established cache',
      () async {
        final f = MobileFixture();
        addTearDown(f.close);
        await f.session.start();
        var pages = 0;
        f.handler = (r) async {
          if (r.url.path.endsWith('/api/v1/me')) {
            return f.response({
              'userId': 'user-A',
              'memberships': f.memberships,
            });
          }
          if (r.url.path.endsWith('/driver/me')) {
            return f.response({
              'id': 'driver',
              'name': 'Driver',
              'active': true,
              'version': 0,
            });
          }
          pages++;
          if (failure == 'malformed') {
            return f.response({'items': [], 'nextCursor': ''});
          }
          return f.response({
            'items': [
              work(failure == 'duplicate trip' ? 'duplicate' : 'trip-$pages'),
            ],
            'nextCursor': failure == 'page limit'
                ? 'cursor-$pages'
                : 'repeated',
          });
        };
        await f.session.refresh();
        expect(f.session.state, SessionState.unavailable);
        expect(f.session.snapshot, isNull);
        expect(pages, lessThanOrEqualTo(SessionController.maxWorkPages));
        expect(
          (await f.cache.restore('firebase-A'))!.trips.single.id,
          'trip-A',
        );
      },
    );
  }
  test('reconnect stream performs authoritative refresh', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    await f.session.start();
    f.network.status = ConnectivityStatus.unavailable;
    f.network.events.add(f.network.status);
    expect(f.session.state, SessionState.offline);
    f.trips = [];
    final ready = Completer<void>();
    f.session.addListener(() {
      if (f.session.state == SessionState.ready && !ready.isCompleted) {
        ready.complete();
      }
    });
    f.network.status = ConnectivityStatus.available;
    f.network.events.add(f.network.status);
    await ready.future;
    expect(f.session.snapshot!.trips, isEmpty);
  });
  test(
    'auth stream account switch hides and removes the prior identity namespace',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      await f.session.start();
      f.trips = [work('trip-B')];
      final ready = Completer<void>();
      f.session.addListener(() {
        if (f.session.state == SessionState.ready &&
            f.session.snapshot?.userId == 'user-B' &&
            !ready.isCompleted) {
          ready.complete();
        }
      });
      f.auth.uid = 'firebase-B';
      f.auth.events.add('firebase-B');
      expect(f.session.snapshot, isNull);
      await ready.future;
      expect(f.session.snapshot!.trips.single.id, 'trip-B');
      expect(await f.cache.restore('firebase-A'), isNull);
      expect((await f.cache.restore('firebase-B'))!.userId, 'user-B');
    },
  );
  test(
    'late old-Tenant work response cannot overwrite the newly selected Tenant',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      f.memberships = [membership('A'), membership('B')];
      await f.session.start();
      final entered = Completer<void>();
      final old = Completer<http.Response>();
      f.handler = (r) async {
        if (r.url.path.endsWith('/driver/me')) {
          return f.response({
            'id': 'driver',
            'name': 'Driver',
            'active': true,
            'version': 0,
          });
        }
        if (r.url.path.contains('/tenants/A/')) {
          entered.complete();
          return old.future;
        }
        return f.response({
          'items': [work('trip-B')],
          'nextCursor': null,
        });
      };
      final pending = f.session.selectTenant(f.session.user!.memberships.first);
      await entered.future;
      await f.session.selectTenant(f.session.user!.memberships.last);
      old.complete(
        f.response({
          'items': [work('trip-A')],
          'nextCursor': null,
        }),
      );
      await pending;
      expect(f.session.snapshot!.membership.tenantId, 'B');
      expect(f.session.snapshot!.trips.single.id, 'trip-B');
      expect((await f.cache.restore('firebase-A'))!.membership.tenantId, 'B');
      expect(
        await f.db
            .customSelect(
              "select count(*) n from cached_trips where tenant_id='A'",
            )
            .getSingle()
            .then((r) => r.read<int>('n')),
        0,
      );
    },
  );
  test(
    'restored Firebase identity bootstraps me and own Driver work with bearer',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      await f.session.start();
      expect(f.session.state, SessionState.ready);
      expect(f.session.snapshot!.userId, 'user-A');
      expect(f.session.snapshot!.driver!.name, 'Jane Driver');
      expect(f.session.snapshot!.trips.single.id, 'trip-A');
      expect(
        f.requests.every(
          (r) => r.headers['Authorization'] == 'Bearer private-bearer',
        ),
        isTrue,
      );
      expect(
        f.requests.every((r) => r.headers['X-Request-Id']?.isNotEmpty == true),
        isTrue,
      );
      expect(f.requests.every((r) => r.method == 'GET'), isTrue);
    },
  );
  test('multiple Tenants require explicit selection and switch replaces visible/cache work', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    f.memberships = [membership('A'), membership('B')];
    await f.session.start();
    expect(f.session.state, SessionState.chooseTenant);
    expect(f.session.snapshot, isNull);
    await f.session.selectTenant(f.session.user!.memberships.first);
    expect(f.session.snapshot!.membership.tenantId, 'A');
    f.trips = [work('trip-B')];
    await f.session.selectTenant(f.session.user!.memberships.last);
    expect(f.session.snapshot!.trips.single.id, 'trip-B');
    expect((await f.cache.restore('firebase-A'))!.membership.tenantId, 'B');
    expect(
      await f.db
          .customSelect(
            "select count(*) n from cached_trips where tenant_id='A'",
          )
          .getSingle()
          .then((r) => r.read<int>('n')),
      0,
    );
  });
  test('no active mobile memberships produce no context', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    f.memberships = [
      {...membership('A'), 'status': 'SUSPENDED'},
      membership('B', capabilities: []),
    ];
    await f.session.start();
    expect(f.session.state, SessionState.chooseTenant);
    expect(f.session.snapshot, isNull);
    expect(f.requests.length, 1);
  });
  test(
    'first-time offline login and restore cannot establish context',
    () async {
      final f = MobileFixture(uid: null);
      addTearDown(f.close);
      f.network.status = ConnectivityStatus.unavailable;
      await f.session.start();
      await f.session.signIn('driver@example.test', 'never-persist');
      expect(f.auth.uid, isNull);
      expect(f.session.snapshot, isNull);
      expect(f.requests, isEmpty);
      final restored = MobileFixture();
      addTearDown(restored.close);
      restored.network.status = ConnectivityStatus.unavailable;
      await restored.session.start();
      expect(restored.session.state, SessionState.unavailable);
      expect(restored.session.snapshot, isNull);
    },
  );
  test('established cache survives outage but explicit membership denial never uses it', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    await f.session.start();
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    expect(f.session.state, SessionState.offline);
    expect(f.session.snapshot!.trips.single.id, 'trip-A');
    f.network.status = ConnectivityStatus.available;
    f.handler = (_) async => f.response({
      'code': 'TENANT_ACCESS_DENIED',
      'detail': 'Access denied.',
      'traceId': 'safe-trace',
    }, 403);
    await f.session.refresh();
    expect(f.session.state, SessionState.invalid);
    expect(f.session.snapshot, isNull);
    expect(await f.cache.restore('firebase-A'), isNull);
  });
  test(
    '401 refreshes ID token once, repeated failure invalidates local session',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      f.handler = (_) async => f.response({
        'code': 'AUTHENTICATION_REQUIRED',
        'detail': 'Sign in.',
      }, 401);
      await f.session.start();
      expect(f.session.state, SessionState.invalid);
      expect(f.auth.forceRefreshes, [false, true]);
      expect(f.requests.length, 2);
    },
  );
  test('expired token is renewed and subsequent GET succeeds', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    var attempts = 0;
    f.handler = (r) async => attempts++ == 0
        ? f.response({'code': 'AUTHENTICATION_REQUIRED'}, 401)
        : f.response({'userId': 'user-A', 'memberships': []});
    await f.session.start();
    expect(f.session.state, SessionState.chooseTenant);
    expect(f.auth.forceRefreshes, [false, true]);
  });
  test('authoritative refresh replaces downloaded work and removes reassigned assets', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    await f.session.start();
    f.trips = [];
    await f.session.refresh();
    expect(f.session.snapshot!.trips, isEmpty);
    expect((await f.cache.restore('firebase-A'))!.trips, isEmpty);
    expect(await f.db.select(f.db.cachedStops).get(), isEmpty);
    expect(await f.db.select(f.db.cachedTrailers).get(), isEmpty);
  });
  test(
    'late response after logout cannot restore UI or repopulate cache',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      await f.session.start();
      final entered = Completer<void>();
      final response = Completer<http.Response>();
      f.handler = (_) {
        entered.complete();
        return response.future;
      };
      final pending = f.session.refresh();
      await entered.future;
      await f.session.signOut();
      response.complete(
        f.response({
          'userId': 'user-A',
          'memberships': [membership('A')],
        }),
      );
      await pending;
      expect(f.session.state, SessionState.signedOut);
      expect(f.session.snapshot, isNull);
      expect(await f.cache.restore('firebase-A'), isNull);
    },
  );
  test(
    'different restored account cannot read old User cache offline',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      await f.session.start();
      expect(await f.cache.restore('firebase-B'), isNull);
      f.auth.uid = 'firebase-B';
      f.network.status = ConnectivityStatus.unavailable;
      await f.session.refresh();
      expect(f.session.snapshot, isNull);
      expect(f.session.state, SessionState.unavailable);
    },
  );
  test(
    'Yard context has no fabricated Driver and makes no Driver directory calls',
    () async {
      final f = MobileFixture();
      addTearDown(f.close);
      f.memberships = [
        membership('Y', capabilities: ['YARD_CHECK_EXECUTE']),
      ];
      await f.session.start();
      expect(f.session.state, SessionState.ready);
      expect(f.session.snapshot!.driver, isNull);
      expect(f.requests.length, 1);
    },
  );
  test('safe 5xx keeps trace reference without treating server failure as authorization', () async {
    final f = MobileFixture();
    addTearDown(f.close);
    f.handler = (_) async => f.response({
      'detail': 'unsafe SQL private-bearer',
      'traceId': 'support-ref',
    }, 503);
    await f.session.start();
    expect(f.session.snapshot, isNull);
    expect(f.session.message, 'The server could not complete the request.');
    expect(f.session.traceId, 'support-ref');
  });
}

class FaultingCache extends SessionCache {
  FaultingCache(super.db);
  String? failure;
  @override
  Future<void> clear() async {
    if (failure == 'clear') throw StateError('controlled storage failure');
    await super.clear();
  }

  @override
  Future<SessionSnapshot?> restore(String uid) async {
    if (failure == 'restore') throw StateError('controlled storage failure');
    return super.restore(uid);
  }

  @override
  Future<void> replace(
    SessionSnapshot snapshot,
    bool Function() current,
  ) async {
    if (failure == 'replace') throw StateError('controlled storage failure');
    await super.replace(snapshot, current);
  }
}
