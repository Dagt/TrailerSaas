import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/road/road_trip_controller.dart';
import 'package:trailersaas_mobile/src/road/road_trip_screen.dart';
import 'package:trailersaas_mobile/src/platform/native_qr_scanner.dart';
import 'package:trailersaas_mobile/src/platform/qr_scanner.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../session/fixtures.dart';

import 'package:trailersaas_mobile/src/persistence/session_cache.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';

class MemoryJournal implements OnlineCommandJournal {
  final rows = <String, OnlineCommand>{};
  bool fail = false;
  @override
  Future<OnlineCommand?> read(String u, String t) async => rows['$u|$t'];
  @override
  Future<void> save(String u, String t, OnlineCommand c) async {
    if (fail) throw StateError('storage');
    rows['$u|$t'] = c;
  }

  @override
  Future<void> clear(String u, String t) async {
    rows.remove('$u|$t');
  }
}

// Widget boundary is synchronous; the cache integration tests above exercise real SQLite.
class WidgetCache extends SessionCache {
  WidgetCache(super.db);
  SessionSnapshot? saved;
  @override
  Future<void> clear() async {
    saved = null;
  }

  @override
  Future<SessionSnapshot?> restore(String uid) async =>
      saved?.firebaseUid == uid ? saved : null;
  @override
  Future<void> replace(SessionSnapshot next, bool Function() current) async {
    if (current()) saved = next;
  }
}

class Scan implements QrScanner {
  Scan(this.value);
  final String value;
  @override
  Future<String?> scan() async => value;
}

Map<String, dynamic> detail({
  String status = 'ASSIGNED',
  String stopStatus = 'PENDING',
  String type = 'OTHER',
}) {
  final t = work('trip-A');
  t['status'] = status;
  (t['stops'] as List).single['status'] = stopStatus;
  (t['stops'] as List).single['stopType'] = type;
  return {
    'trip': t,
    'currentCoupling': null,
    'tractorOperationalNumber': 'Truck 12',
    'startedAt': null,
    'completedAt': null,
    'departurePrerequisitesSatisfied': false,
  };
}

void main() {
  late MobileFixture f;
  late MemoryJournal journal;
  late RoadTripController c;
  Future<void> start() async {
    f = MobileFixture();
    await f.session.start();
    journal = MemoryJournal();
    c = RoadTripController(f.session, 'trip-A', journal);
    f.handler = (r) async => http.Response(jsonEncode(detail()), 200);
    await c.load();
  }

  Future<void> close() async {
    c.dispose();
    await f.close();
  }

  test('UUID keys are canonical random distinct operations', () {
    final a = operationUuid(), b = operationUuid();
    expect(
      a,
      matches(
        RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      ),
    );
    expect(a, isNot(b));
  });
  test(
    'journal survives file reopen and isolates User/Tenant without tokens',
    () async {
      final dir = await Directory.systemTemp.createTemp('online-receipt-');
      addTearDown(() => dir.delete(recursive: true));
      final store = FileOnlineCommandJournal(() async => dir);
      final cmd = OnlineCommand(
        operationUuid(),
        'trip',
        'tenants/A/stops/s/arrivals',
        {'source': 'MANUAL', 'occurredAt': '2026-10-10T10:00:00Z'},
      );
      await store.save('u', 'A', cmd);
      final reopened = FileOnlineCommandJournal(() async => dir);
      expect((await reopened.read('u', 'A'))!.id, cmd.id);
      expect(await reopened.read('v', 'A'), isNull);
      expect(await reopened.read('u', 'B'), isNull);
      expect(
        await dir
            .list()
            .where((f) => f is File)
            .cast<File>()
            .first
            .then((f) => f.readAsString()),
        isNot(contains('private-bearer')),
      );
      await reopened.clear('u', 'A');
      expect(await store.read('u', 'A'), isNull);
    },
  );
  test('failed durable recording sends no POST', () async {
    await start();
    addTearDown(close);
    journal.fail = true;
    var posts = 0;
    f.handler = (r) async {
      if (r.method == 'POST') posts++;
      return http.Response('{}', 200);
    };
    await c.submit('tenants/A/stops/s/arrivals', {'source': 'MANUAL'});
    expect(posts, 0);
    expect(c.sending, isFalse);
    expect(c.message, contains('safely recorded'));
  });
  test(
    'lost response is recovered explicitly with same key and immutable payload',
    () async {
      await start();
      addTearDown(close);
      final keys = <String>[], bodies = <String>[];
      var first = true;
      f.handler = (r) async {
        if (r.method == 'POST') {
          keys.add(r.headers['Idempotency-Key']!);
          bodies.add(r.body);
          if (first) {
            first = false;
            throw http.ClientException('lost response');
          }
          return http.Response(
            jsonEncode(detail(status: 'AT_STOP', stopStatus: 'ARRIVED')),
            200,
          );
        }
        return http.Response(
          jsonEncode(detail(status: 'AT_STOP', stopStatus: 'ARRIVED')),
          200,
        );
      };
      await c.submit('tenants/A/stops/s/arrivals', {
        'source': 'MANUAL',
        'occurredAt': '2026-10-10T10:00:00Z',
      });
      expect(keys.length, 1);
      expect(c.pending, isNotNull);
      expect(c.trip!['status'], 'ASSIGNED');
      await c.recover();
      expect(keys.length, 2);
      expect(keys[1], keys[0]);
      expect(bodies[1], bodies[0]);
      expect(c.pending, isNull);
      expect(c.trip!['status'], 'AT_STOP');
      expect(
        (await f.cache.restore('firebase-A'))!.trips
            .where((t) => t.id == 'trip-A')
            .single
            .status,
        'AT_STOP',
      );
    },
  );
  test('5xx exposes safe reference without automatic retry', () async {
    await start();
    addTearDown(close);
    var posts = 0;
    f.handler = (r) async {
      posts++;
      return http.Response(
        '{"code":"FAILURE","detail":"unsafe","traceId":"ref"}',
        503,
      );
    };
    await c.submit('tenants/A/stops/s/completions', {});
    expect(posts, 1);
    expect(c.message, 'The server could not complete the request.');
    expect(c.traceId, 'ref');
    expect(c.pending, isNotNull);
  });
  test(
    'another Trip receipt can be reconciled explicitly after assignment loss',
    () async {
      await start();
      addTearDown(close);
      final saved = OnlineCommand(
        operationUuid(),
        'previous-trip',
        'tenants/A/stops/previous-stop/arrivals',
        {'source': 'MANUAL'},
      );
      await journal.save('user-A', 'A', saved);
      await c.load();
      expect(c.pending!.tripId, 'previous-trip');
      final posts = <http.Request>[];
      f.handler = (request) async {
        if (request.method == 'POST') {
          posts.add(request);
          return http.Response(
            '{"code":"RESOURCE_NOT_FOUND","detail":"The resource was not found."}',
            404,
          );
        }
        if (request.url.path.endsWith('/api/v1/me')) {
          return f.response({'userId': 'user-A', 'memberships': f.memberships});
        }
        if (request.url.path.endsWith('/driver/me')) {
          return f.response({
            'id': 'driver',
            'name': 'Jane Driver',
            'active': true,
            'version': 0,
          });
        }
        if (request.url.path.endsWith('/driver/me/trips')) {
          return f.response({'items': f.trips, 'nextCursor': null});
        }
        return f.response(detail());
      };
      expect(posts, isEmpty);
      await c.recover();
      await c.load();
      expect(posts.length, 1);
      expect(posts.single.url.path, endsWith('/stops/previous-stop/arrivals'));
      expect(posts.single.headers['Idempotency-Key'], saved.id);
      expect(jsonDecode(posts.single.body), saved.body);
      expect(await journal.read('user-A', 'A'), isNull);
      expect(f.session.snapshot!.trips.single.id, 'trip-A');
      expect(c.pending, isNull);
      expect(c.sending, isFalse);
    },
  );
  test('offline never submits or confirms a command', () async {
    await start();
    addTearDown(close);
    f.network.events.add(ConnectivityStatus.unavailable);
    var posts = 0;
    f.handler = (r) async {
      posts++;
      return http.Response('{}', 200);
    };
    await c.submit('path', {});
    expect(posts, 0);
    expect(c.detail, isNull);
    expect(journal.rows, isEmpty);
  });
  test(
    'late response from old identity never appears under new identity',
    () async {
      await start();
      addTearDown(close);
      final response = Completer<http.Response>();
      final started = Completer<void>();
      f.handler = (r) {
        if (!started.isCompleted) started.complete();
        return response.future;
      };
      final send = c.submit('path', {});
      await started.future;
      f.auth.uid = 'firebase-B';
      f.auth.events.add('firebase-B');
      response.complete(
        http.Response(jsonEncode(detail(status: 'COMPLETED')), 200),
      );
      await send;
      expect(c.detail, isNull);
      expect(c.sending, isFalse);
      expect(journal.rows.keys, contains('user-A|A'));
    },
  );
  test('a pending submission blocks a second operation', () async {
    await start();
    addTearDown(close);
    var posts = 0;
    f.handler = (r) async {
      posts++;
      throw http.ClientException('offline');
    };
    await c.submit('path', {});
    await c.submit('other', {});
    expect(posts, 1);
    expect(c.pending!.path, 'path');
  });
  test(
    'QR adapter accepts only token or HTTPS label and never arbitrary URL',
    () {
      const token = 'abcdefghijklmnopqrstuv';
      expect(trailerPublicToken(token), token);
      expect(trailerPublicToken('https://labels.example/t/$token'), token);
      expect(trailerPublicToken('http://evil.example/t/$token'), isNull);
      expect(trailerPublicToken('https://example/t/$token?secret=x'), isNull);
      expect(trailerPublicToken('manual-trailer-number'), isNull);
    },
  );
  testWidgets(
    'authoritative arrival UI has human destination and no Delivery bypass',
    (tester) async {
      late MobileFixture fixture;
      late MemoryJournal store;
      await tester.runAsync(() async {
        fixture = MobileFixture();
        await fixture.session.start();
        store = MemoryJournal();
        fixture.session.snapshot!.membership.capabilities.addAll({
          'TRIP_EXECUTE_ASSIGNED',
          'ARRIVAL_CONFIRM',
        });
        fixture.handler = (r) async => http.Response(
          jsonEncode(
            detail(
              status: 'IN_TRANSIT',
              stopStatus: 'EN_ROUTE',
              type: 'DELIVERY',
            ),
          ),
          200,
        );
      });
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox());
        await tester.runAsync(fixture.close);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: RoadTripScreen(
            session: fixture.session,
            cached: fixture.session.snapshot!.trips.first,
            journal: store,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Confirm manual arrival'), findsOneWidget);
      expect(find.text('Active destination: Dock 17'), findsOneWidget);
      expect(find.text('Complete non-delivery Stop'), findsNothing);
      expect(find.text('Expected Trailer: TR-123'), findsOneWidget);
      expect(find.textContaining('Truck 12'), findsOneWidget);
    },
  );
  testWidgets('session identity change hides all prior operational labels', (
    tester,
  ) async {
    late MobileFixture fixture;
    await tester.runAsync(() async {
      fixture = MobileFixture();
      await fixture.session.start();
      fixture.handler = (r) async => http.Response(jsonEncode(detail()), 200);
    });
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox());
      await tester.runAsync(fixture.close);
    });
    await tester.pumpWidget(
      MaterialApp(
        home: RoadTripScreen(
          session: fixture.session,
          cached: fixture.session.snapshot!.trips.first,
          journal: MemoryJournal(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.runAsync(fixture.session.signOut);
    await tester.pumpAndSettle();
    expect(find.textContaining('Dock 17'), findsNothing);
    expect(find.textContaining('TR-123'), findsNothing);
    expect(
      find.text('Session changed. Return to current assigned work.'),
      findsOneWidget,
    );
  });

  test(
    'concurrent journal saves retain exactly one immutable receipt',
    () async {
      final dir = await Directory.systemTemp.createTemp('receipt-concurrency-');
      addTearDown(() => dir.delete(recursive: true));
      final a = FileOnlineCommandJournal(() async => dir),
          b = FileOnlineCommandJournal(() async => dir);
      final outcomes = await Future.wait([
        a
            .save(
              'u',
              'A',
              OnlineCommand(
                operationUuid(),
                'trip',
                'tenants/A/stops/s/arrivals',
                {'source': 'MANUAL'},
              ),
            )
            .then((_) => true, onError: (_) => false),
        b
            .save(
              'u',
              'A',
              OnlineCommand(
                operationUuid(),
                'trip',
                'tenants/A/stops/s/arrivals',
                {'source': 'MANUAL'},
              ),
            )
            .then((_) => true, onError: (_) => false),
      ]);
      expect(outcomes.where((v) => v).length, 1);
      expect(await a.read('u', 'A'), isNotNull);
    },
  );
  test(
    'confirmed terminal Trip disappears from persisted assigned work',
    () async {
      await start();
      addTearDown(close);
      f.handler = (r) async => http.Response(
        jsonEncode(detail(status: 'COMPLETED', stopStatus: 'COMPLETED')),
        200,
      );
      await c.submit('tenants/A/stops/s/completions', {
        'occurredAt': '2026-10-10T10:00:00Z',
      });
      expect(c.trip!['status'], 'COMPLETED');
      expect(f.session.snapshot!.trips, isEmpty);
      expect((await f.cache.restore('firebase-A'))!.trips, isEmpty);
    },
  );
  testWidgets('departure is blocked without verified duty and inspection', (
    tester,
  ) async {
    late MobileFixture fixture;
    await tester.runAsync(() async {
      fixture = MobileFixture();
      await fixture.session.start();
      fixture.session.snapshot!.membership.capabilities.addAll({
        'TRIP_EXECUTE_ASSIGNED',
        'DEPARTURE_CONFIRM',
      });
      fixture.handler = (r) async => http.Response(jsonEncode(detail()), 200);
    });
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox());
      await tester.runAsync(fixture.close);
    });
    await tester.pumpWidget(
      MaterialApp(
        home: RoadTripScreen(
          session: fixture.session,
          cached: fixture.session.snapshot!.trips.first,
          journal: MemoryJournal(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('On Duty and Pre-Trip'), findsOneWidget);
    expect(find.text('Confirm first departure'), findsNothing);
  });

  for (final scenario in [
    ('AT_STOP', 'ARRIVED', 'Start service', 'service-starts', 'IN_SERVICE'),
    (
      'AT_STOP',
      'IN_SERVICE',
      'Complete non-delivery Stop',
      'completions',
      'COMPLETED',
    ),
    ('IN_TRANSIT', 'EN_ROUTE', 'Confirm manual arrival', 'arrivals', 'ARRIVED'),
  ]) {
    testWidgets(
      'explicit confirmation submits ${scenario.$4} and reconciles server state',
      (tester) async {
        final fixture = MobileFixture(cacheFactory: WidgetCache.new);
        await fixture.session.start();
        fixture.session.snapshot!.membership.capabilities.addAll({
          'TRIP_EXECUTE_ASSIGNED',
          'ARRIVAL_CONFIRM',
        });
        var projection = detail(status: scenario.$1, stopStatus: scenario.$2);
        final posts = <http.Request>[];
        fixture.handler = (r) async {
          if (r.method == 'POST') {
            posts.add(r);
            projection = detail(
              status: scenario.$5 == 'COMPLETED' ? 'COMPLETED' : 'AT_STOP',
              stopStatus: scenario.$5,
            );
          }
          return http.Response(jsonEncode(projection), 200);
        };
        addTearDown(() async {
          await tester.pumpWidget(const SizedBox());
          await tester.runAsync(fixture.close);
        });
        await tester.pumpWidget(
          MaterialApp(
            home: RoadTripScreen(
              session: fixture.session,
              cached: fixture.session.snapshot!.trips.first,
              journal: MemoryJournal(),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final button = find.text(scenario.$3);
        await tester.ensureVisible(button);
        await tester.tap(button);
        await tester.pumpAndSettle();
        expect(posts, isEmpty);
        fixture.auth.forceRefreshes.clear();
        await tester.tap(find.widgetWithText(FilledButton, 'Confirm'));
        await tester.pumpAndSettle();
        expect(posts.length, 1);
        expect(
          posts.single.url.path,
          endsWith('/stops/stop-trip-A/${scenario.$4}'),
        );
        expect(posts.single.headers['Idempotency-Key'], isNotEmpty);
        expect(posts.single.headers['X-Request-Id'], isNotEmpty);
        expect(fixture.auth.forceRefreshes, [true, false]);
        expect(find.text('Confirmed by server.'), findsOneWidget);
        expect(
          find.textContaining(
            'Trip · ${scenario.$5 == 'COMPLETED' ? 'COMPLETED' : 'AT_STOP'}',
          ),
          findsOneWidget,
        );
        if (scenario.$5 == 'COMPLETED') {
          expect(
            find.text(
              'Trip completion confirmed by server. Shift remains separate.',
            ),
            findsOneWidget,
          );
        }
      },
    );
  }
  testWidgets('QR mismatch blocks Hook without a manual ID bypass', (
    tester,
  ) async {
    final fixture = MobileFixture(cacheFactory: WidgetCache.new);
    await fixture.session.start();
    fixture.session.snapshot!.membership.capabilities.addAll({
      'TRIP_EXECUTE_ASSIGNED',
      'TRAILER_HOOK',
    });
    fixture.handler = (r) async => r.url.path.contains('trailer-qr')
        ? http.Response(
            '{"trailerId":"different","operationalNumber":"TR-OTHER","version":0}',
            200,
          )
        : http.Response(jsonEncode(detail()), 200);
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox());
      await tester.runAsync(fixture.close);
    });
    await tester.pumpWidget(
      MaterialApp(
        home: RoadTripScreen(
          session: fixture.session,
          cached: fixture.session.snapshot!.trips.first,
          journal: MemoryJournal(),
          scanner: Scan('abcdefghijklmnopqrstuv'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Scan Trailer and confirm Hook'));
    await tester.pumpAndSettle();
    expect(find.text('Trailer not authorized'), findsOneWidget);
    expect(find.textContaining('Contact dispatch'), findsOneWidget);
    expect(fixture.requests.where((r) => r.method == 'POST'), isEmpty);
    expect(find.byType(TextField), findsNothing);
  });
  testWidgets(
    'QR identifies the physical Trailer before explicit Hook payload',
    (tester) async {
      final fixture = MobileFixture(cacheFactory: WidgetCache.new);
      await fixture.session.start();
      fixture.session.snapshot!.membership.capabilities.addAll({
        'TRIP_EXECUTE_ASSIGNED',
        'TRAILER_HOOK',
      });
      var projection = detail();
      http.Request? post;
      fixture.handler = (r) async {
        if (r.url.path.contains('trailer-qr')) {
          return http.Response(
            '{"trailerId":"trailer","operationalNumber":"TR-123","version":7}',
            200,
          );
        }
        if (r.method == 'POST') {
          post = r;
          projection['currentCoupling'] = {
            'trailerId': 'trailer',
            'operationalNumber': 'TR-123',
            'tractorId': 'tractor',
            'driverId': 'driver',
          };
        }
        return http.Response(jsonEncode(projection), 200);
      };
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox());
        await tester.runAsync(fixture.close);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: RoadTripScreen(
            session: fixture.session,
            cached: fixture.session.snapshot!.trips.first,
            journal: MemoryJournal(),
            scanner: Scan('abcdefghijklmnopqrstuv'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Scan Trailer and confirm Hook'));
      await tester.pumpAndSettle();
      expect(post, isNull);
      expect(find.text('Confirm Hook TR-123'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Confirm'));
      await tester.pumpAndSettle();
      final payload = jsonDecode(post!.body) as Map;
      expect(
        payload.keys,
        unorderedEquals([
          'occurredAt',
          'trailerId',
          'tractorId',
          'driverId',
          'expectedTrailerVersion',
        ]),
      );
      expect(payload['expectedTrailerVersion'], 7);
      expect(payload['driverId'], 'driver');
      expect(find.text('Physical coupling: TR-123'), findsOneWidget);
      expect(find.text('Scan Trailer and confirm Hook'), findsNothing);
    },
  );
  testWidgets(
    'Drop of existing coupling survives QR denial; replacement Hook requires QR',
    (tester) async {
      final fixture = MobileFixture(cacheFactory: WidgetCache.new);
      await fixture.session.start();
      fixture.session.snapshot!.membership.capabilities.addAll({
        'TRIP_EXECUTE_ASSIGNED',
        'TRAILER_DROP',
        'TRAILER_HOOK',
      });
      final projection = detail(
        status: 'AT_STOP',
        stopStatus: 'IN_SERVICE',
        type: 'DROP_HOOK',
      );
      (projection['trip'] as Map)['plannedInitialTrailerId'] = 'replacement';
      projection['currentCoupling'] = {
        'trailerId': 'original',
        'operationalNumber': 'TR-OLD',
      };
      ((projection['trip'] as Map)['stops'] as List).single['locationId'] =
          'dock';
      final posts = <http.Request>[];
      fixture.handler = (request) async {
        if (request.url.path.contains('trailer-qr')) {
          final afterDrop = posts.isNotEmpty;
          if (!afterDrop)
            return http.Response('{"code":"RESOURCE_NOT_FOUND"}', 404);
          return http.Response(
            jsonEncode({
              'trailerId': afterDrop ? 'replacement' : 'original',
              'operationalNumber': afterDrop ? 'TR-NEW' : 'TR-OLD',
              'version': afterDrop ? 9 : 7,
            }),
            200,
          );
        }
        if (request.method == 'POST') {
          posts.add(request);
          projection['currentCoupling'] = request.url.path.endsWith('/drops')
              ? null
              : {'trailerId': 'replacement', 'operationalNumber': 'TR-NEW'};
        }
        return http.Response(jsonEncode(projection), 200);
      };
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox());
        await tester.runAsync(fixture.close);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: RoadTripScreen(
            session: fixture.session,
            cached: fixture.session.snapshot!.trips.first,
            journal: MemoryJournal(),
            scanner: Scan('abcdefghijklmnopqrstuv'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Scan Trailer and confirm Hook'), findsNothing);
      final drop = find.text('Confirm Drop of coupled Trailer');
      await tester.ensureVisible(drop);
      await tester.tap(drop);
      await tester.pumpAndSettle();
      expect(posts, isEmpty);
      expect(find.text('Confirm Drop TR-OLD'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Confirm'));
      await tester.pumpAndSettle();
      expect(posts.length, 1);
      expect(posts.single.url.path, endsWith('/trips/trip-A/drops'));
      final dropped = jsonDecode(posts.single.body) as Map;
      expect(
        dropped.keys,
        unorderedEquals(['occurredAt', 'trailerId', 'stopId', 'locationId']),
      );
      expect(dropped['trailerId'], 'original');
      expect(dropped['locationId'], 'dock');
      expect(
        fixture.requests.where((r) => r.url.path.contains('trailer-qr')),
        isEmpty,
      );
      expect(find.text('Confirm Drop of coupled Trailer'), findsNothing);
      final hook = find.text('Scan Trailer and confirm Hook');
      await tester.ensureVisible(hook);
      await tester.tap(hook);
      await tester.pumpAndSettle();
      expect(posts.length, 1);
      expect(find.text('Confirm Hook TR-NEW'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Confirm'));
      await tester.pumpAndSettle();
      expect(posts.length, 2);
      expect(posts.last.url.path, endsWith('/trips/trip-A/hooks'));
      final hooked = jsonDecode(posts.last.body) as Map;
      expect(hooked['trailerId'], 'replacement');
      expect(hooked['expectedTrailerVersion'], 9);
      expect(
        posts.first.headers['Idempotency-Key'],
        isNot(posts.last.headers['Idempotency-Key']),
      );
      expect(find.text('Physical coupling: TR-NEW'), findsOneWidget);
    },
  );
}
