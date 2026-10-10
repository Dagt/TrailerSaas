import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import 'fixtures.dart';

void main() {
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
