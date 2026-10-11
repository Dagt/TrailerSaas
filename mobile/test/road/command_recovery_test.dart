import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/road/road_trip_controller.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../session/fixtures.dart';
import 'online_execution_test.dart' show MemoryJournal, WidgetCache, detail;

http.Response reads(MobileFixture f, http.Request r) {
  if (r.url.path.endsWith('/api/v1/me')) {
    return f.response({
      'userId': f.auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
      'memberships': f.memberships,
    });
  }
  if (r.url.path.endsWith('/driver/me')) {
    return f.response({
      'id': 'driver',
      'name': 'Jane Driver',
      'active': true,
      'version': 0,
    });
  }
  if (r.url.path.endsWith('/driver/me/trips')) {
    return f.response({'items': f.trips, 'nextCursor': null});
  }
  return f.response(detail(status: 'AT_STOP', stopStatus: 'ARRIVED'));
}

void main() {
  for (final status in [408, 429, 500, 503]) {
    test(
      '$status preserves immutable receipt until explicit same-key recovery',
      () async {
        final f = MobileFixture();
        await f.session.start();
        final journal = MemoryJournal();
        final c = RoadTripController(f.session, 'trip-A', journal);
        addTearDown(() async {
          c.dispose();
          await f.close();
        });
        final posts = <http.Request>[];
        var commits = 0;
        f.handler = (r) async {
          if (r.method != 'POST') return reads(f, r);
          posts.add(r);
          // Model a committed operation whose first acknowledgement is lost/replaced.
          if (commits == 0) commits++;
          return posts.length == 1
              ? f.response({
                  'code': 'UNCERTAIN',
                  'detail': 'Not confirmed',
                  'traceId': 'support-ref',
                }, status)
              : f.response(detail());
        };
        await c.load();
        await c.submit('tenants/A/stops/s/arrivals', {
          'occurredAt': '2026-10-10T10:00:00Z',
          'source': 'MANUAL',
        });
        final original = (await journal.read('user-A', 'A'))!;
        expect(posts, hasLength(1));
        expect(c.traceId, 'support-ref');
        expect(c.pending!.toJson(), original.toJson());
        await c.recover();
        expect(posts, hasLength(2));
        expect(posts.last.headers['Idempotency-Key'], original.id);
        expect(posts.last.url.path, posts.first.url.path);
        expect(posts.last.body, posts.first.body);
        expect(commits, 1);
        expect(
          c.trip!['status'],
          'AT_STOP',
        ); // Fresh GET, not the saved POST projection.
        expect(await journal.read('user-A', 'A'), isNull);
      },
    );
  }

  for (final value in ['120', 'Sat, 10 Oct 2099 10:00:00 GMT']) {
    test(
      '429 Retry-After $value blocks explicit POST until eligible, never retries automatically',
      () async {
        final f = MobileFixture();
        await f.session.start();
        final journal = MemoryJournal();
        final c = RoadTripController(f.session, 'trip-A', journal);
        addTearDown(() async {
          c.dispose();
          await f.close();
        });
        var posts = 0;
        f.handler = (r) async {
          if (r.method != 'POST') return reads(f, r);
          posts++;
          return http.Response('{}', 429, headers: {'retry-after': value});
        };
        await c.load();
        await c.submit('tenants/A/stops/s/arrivals', {});
        expect(c.retryNotBefore, isNotNull);
        expect(c.canRecover, isFalse);
        await c.recover();
        // Reopening the foreground surface cannot bypass an in-memory server limit.
        final reopened = RoadTripController(
          f.session,
          '',
          journal,
          recoveryOnly: true,
        );
        await reopened.load();
        await reopened.recover();
        reopened.dispose();
        expect(posts, 1);
        expect(await journal.read('user-A', 'A'), isNotNull);
      },
    );
  }

  for (final status in [401, 403]) {
    test(
      '$status invalidates cache before refresh; outage cannot restore denied permissions',
      () async {
        final f = MobileFixture();
        await f.session.start();
        final journal = MemoryJournal();
        final c = RoadTripController(f.session, 'trip-A', journal);
        addTearDown(() async {
          c.dispose();
          await f.close();
        });
        f.handler = (r) async {
          if (r.method == 'POST') {
            return f.response({
              'code': 'CAPABILITY_DENIED',
              'detail': 'Access denied.',
            }, status);
          }
          if (r.url.path.endsWith('/api/v1/me')) {
            throw http.ClientException('refresh outage');
          }
          return reads(f, r);
        };
        await c.load();
        await c.submit('tenants/A/stops/s/arrivals', {});
        expect(f.session.snapshot, isNull);
        expect(f.session.state, SessionState.unavailable);
        expect(await f.cache.restore('firebase-A'), isNull);
        expect(await journal.read('user-A', 'A'), isNotNull);
        expect(c.detail, isNull);
        expect(c.sending, isFalse);
      },
    );
  }

  for (final code in [
    'RESOURCE_CONFLICT',
    'INVALID_STATE_TRANSITION',
    'STALE_VERSION',
  ]) {
    test(
      '409 $code is a definitive application rejection and reloads current work',
      () async {
        final f = MobileFixture();
        await f.session.start();
        final journal = MemoryJournal();
        final c = RoadTripController(f.session, 'trip-A', journal);
        addTearDown(() async {
          c.dispose();
          await f.close();
        });
        f.handler = (r) async => r.method == 'POST'
            ? f.response({
                'code': code,
                'detail': 'The action is not permitted.',
              }, 409)
            : reads(f, r);
        await c.load();
        await c.submit('tenants/A/stops/s/arrivals', {});
        await c.load();
        expect(await journal.read('user-A', 'A'), isNull);
        expect(c.pending, isNull);
        expect(
          f.requests.where((r) => r.url.path.endsWith('/api/v1/me')),
          hasLength(2),
        );
        expect(f.requests.where((r) => r.method == 'POST'), hasLength(1));
        expect(c.trip!['status'], 'AT_STOP');
      },
    );
  }

  for (final status in [400, 404, 409, 422]) {
    test(
      'unrecognized/malformed $status cannot erase an uncertain receipt',
      () async {
        final f = MobileFixture();
        await f.session.start();
        final journal = MemoryJournal();
        final c = RoadTripController(f.session, 'trip-A', journal);
        addTearDown(() async {
          c.dispose();
          await f.close();
        });
        f.handler = (r) async => r.method == 'POST'
            ? http.Response('proxy response', status)
            : reads(f, r);
        await c.load();
        await c.submit('tenants/A/stops/s/arrivals', {});
        expect(c.pending, isNotNull);
        expect(await journal.read('user-A', 'A'), isNotNull);
      },
    );
  }

  test('key reuse conflict retains original receipt rather than inventing a new operation', () async {
    final f = MobileFixture();
    await f.session.start();
    final journal = MemoryJournal();
    final c = RoadTripController(f.session, 'trip-A', journal);
    addTearDown(() async {
      c.dispose();
      await f.close();
    });
    f.handler = (r) async => r.method == 'POST'
        ? f.response({
            'code': 'IDEMPOTENCY_KEY_REUSED',
            'detail': 'Conflict.',
          }, 409)
        : reads(f, r);
    await c.load();
    await c.submit('tenants/A/stops/s/arrivals', {});
    final key = c.pending!.id;
    await c.recover();
    expect(c.pending!.id, key);
    expect(
      f.requests
          .where((r) => r.method == 'POST')
          .map((r) => r.headers['Idempotency-Key']),
      [key, key],
    );
  });

  test('empty assigned work still recovers after sign-out and restart; other User/Tenant cannot read or send it', () async {
    final f = MobileFixture();
    f.trips = [];
    f.memberships = [membership('A'), membership('B')];
    await f.session.start();
    await f.session.selectTenant(f.session.user!.memberships.first);
    final directory = await Directory.systemTemp.createTemp('road-recovery-');
    addTearDown(() => directory.delete(recursive: true));
    final journal = FileOnlineCommandJournal(() async => directory);
    final saved = OnlineCommand(
      operationUuid(),
      'gone-trip',
      'tenants/A/stops/gone-stop/arrivals',
      {'source': 'MANUAL'},
    );
    await journal.save('user-A', 'A', saved);
    var c = RoadTripController(f.session, '', journal, recoveryOnly: true);
    await c.load();
    expect(c.pending!.id, saved.id);
    await f.session.selectTenant(f.session.user!.memberships.last);
    expect(c.pending, isNull);
    await c.recover();
    expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    c.dispose();
    await f.session.signOut();
    await f.close();
    final restarted = MobileFixture();
    restarted.trips = [];
    await restarted.session.start();
    c = RoadTripController(restarted.session, '', journal, recoveryOnly: true);
    addTearDown(() async {
      c.dispose();
      await restarted.close();
    });
    final posts = <http.Request>[];
    restarted.handler = (r) async {
      if (r.method == 'POST') {
        posts.add(r);
        return restarted.response({
          'code': 'RESOURCE_NOT_FOUND',
          'detail': 'The resource was not found.',
        }, 404);
      }
      return reads(restarted, r);
    };
    await c.load();
    await c.recover();
    await c.load();
    expect(posts.single.headers['Idempotency-Key'], saved.id);
    expect(posts.single.url.path, endsWith(saved.path));
    expect(jsonDecode(posts.single.body), saved.body);
    expect(restarted.session.snapshot!.trips, isEmpty);
    expect(
      c.pending!.id,
      saved.id,
    ); // Denial precedes replay; earlier outcome remains uncertain.
    restarted.auth.uid = 'firebase-B';
    restarted.auth.events.add('firebase-B');
    await restarted.session.refresh();
    expect(c.pending, isNull);
    await c.recover();
    expect(posts, hasLength(1));
  });

  test(
    'offline recovery can display the current namespace but cannot send',
    () async {
      final f = MobileFixture();
      await f.session.start();
      final journal = MemoryJournal();
      await journal.save(
        'user-A',
        'A',
        OnlineCommand(
          operationUuid(),
          'gone',
          'tenants/A/stops/s/arrivals',
          {},
        ),
      );
      f.network.events.add(ConnectivityStatus.unavailable);
      final c = RoadTripController(f.session, '', journal, recoveryOnly: true);
      addTearDown(() async {
        c.dispose();
        await f.close();
      });
      await c.load();
      expect(c.pending, isNotNull);
      expect(c.canRecover, isFalse);
      await c.recover();
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    },
  );

  test(
    'a saved endpoint for another Tenant or a traversal is never sent',
    () async {
      final f = MobileFixture();
      await f.session.start();
      final journal = MemoryJournal();
      final c = RoadTripController(f.session, '', journal, recoveryOnly: true);
      addTearDown(() async {
        c.dispose();
        await f.close();
      });
      for (final path in [
        'tenants/B/stops/s/arrivals',
        'tenants/A/../B/stops/s/arrivals',
      ]) {
        await journal.save(
          'user-A',
          'A',
          OnlineCommand(operationUuid(), 'gone', path, {}),
        );
        await c.load();
        await c.recover();
        expect(c.pending, isNull);
        expect(await journal.read('user-A', 'A'), isNotNull);
      }
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
    },
  );

  test('late recovery acknowledgement after Tenant switch cannot clear or publish old work', () async {
    final f = MobileFixture();
    f.memberships = [membership('A'), membership('B')];
    await f.session.start();
    await f.session.selectTenant(f.session.user!.memberships.first);
    final journal = MemoryJournal();
    final saved = OnlineCommand(
      operationUuid(),
      'gone',
      'tenants/A/stops/s/arrivals',
      {},
    );
    await journal.save('user-A', 'A', saved);
    final c = RoadTripController(f.session, '', journal, recoveryOnly: true);
    addTearDown(() async {
      c.dispose();
      await f.close();
    });
    await c.load();
    final submitted = Completer<void>();
    final response = Completer<http.Response>();
    f.handler = (r) async {
      if (r.method == 'POST') {
        submitted.complete();
        return response.future;
      }
      return reads(f, r);
    };
    final recovery = c.recover();
    await submitted.future;
    f.trips = [];
    await f.session.selectTenant(f.session.user!.memberships.last);
    response.complete(f.response(detail()));
    await recovery;
    expect(f.session.snapshot!.membership.tenantId, 'B');
    expect(f.session.snapshot!.trips, isEmpty);
    expect(c.pending, isNull);
    expect(c.message, isNull);
    expect((await journal.read('user-A', 'A'))!.id, saved.id);
  });

  test(
    'late acknowledgement cannot delete a newer receipt in the same namespace',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'road-receipt-clear-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final journal = FileOnlineCommandJournal(() async => directory);
      final old = OnlineCommand(
        operationUuid(),
        'trip',
        'tenants/A/stops/s/arrivals',
        {},
      );
      final next = OnlineCommand(
        operationUuid(),
        'trip',
        'tenants/A/stops/s/service-starts',
        {},
      );
      await journal.save('u', 'A', old);
      await journal.clear('u', 'A', operationId: old.id);
      await journal.save('u', 'A', next);
      await journal.clear('u', 'A', operationId: old.id);
      expect((await journal.read('u', 'A'))!.toJson(), next.toJson());
    },
  );

  testWidgets(
    'Road recovery remains visible with zero Trips and requires explicit confirmation',
    (tester) async {
      final f = MobileFixture(cacheFactory: WidgetCache.new);
      f.trips = [];
      await f.session.start();
      final journal = MemoryJournal();
      final saved = OnlineCommand(
        operationUuid(),
        'gone',
        'tenants/A/stops/s/arrivals',
        {'source': 'MANUAL'},
      );
      await journal.save('user-A', 'A', saved);
      final refreshResponse = Completer<http.Response>();
      f.handler = (r) async {
        if (r.method == 'POST') return f.response(detail());
        if (r.url.path.endsWith('/api/v1/me')) return refreshResponse.future;
        return reads(f, r);
      };
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox());
        await tester.runAsync(f.close);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: SessionView(session: f.session, journal: journal),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('No assigned work downloaded.'), findsOneWidget);
      expect(find.text('Reconcile previous operation'), findsOneWidget);
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      await tester.tap(find.text('Reconcile previous operation'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Recover'));
      await tester.pump();
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      refreshResponse.complete(
        f.response({'userId': 'user-A', 'memberships': f.memberships}),
      );
      await tester.pumpAndSettle();
      expect(
        f.requests
            .where((r) => r.method == 'POST')
            .single
            .headers['Idempotency-Key'],
        saved.id,
      );
      expect(
        find.text('Confirmed by server. Assigned work has been refreshed.'),
        findsOneWidget,
      );
      expect(find.text('Reconcile previous operation'), findsNothing);
    },
  );
}
