import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/yard/yard_contracts.dart';

import '../session/fixtures.dart';
import 'fixtures.dart';

void main() {
  late YardFixture f;
  setUp(() async {
    f = YardFixture();
    await f.open();
  });
  tearDown(() => f.close());
  test(
    'Yard-only does not invent Driver/Tractor and QR identifies without moving',
    () async {
      expect(f.mobile.session.snapshot!.driver, isNull);
      expect(
        f.mobile.requests.any((r) => r.url.path.contains('/driver/me')),
        isFalse,
      );
      await f.controller.scan();
      expect(f.controller.trailer!.number, 'T114');
      expect(f.controller.canMove, isTrue);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      expect((await f.worker.store.list('user-A', 'A')), isEmpty);
    },
  );
  test('a cached authorized Site and previously resolved QR support offline capture without changing projection', () async {
    await f.controller.scan();
    f.mobile.network.status = ConnectivityStatus.unavailable;
    await f.mobile.session.refresh();
    await f.controller.load();
    await f.controller.scan();
    final from = await f.controller.plannedOrigin();
    await f.controller.confirmMove(dockId, from!);
    await f.worker.sync();
    final rows = await f.worker.store.list('user-A', 'A');
    expect(rows, hasLength(1));
    expect(rows.single.state, 'QUEUED');
    expect(f.controller.trailer!.location!['id'], yardId);
    expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    expect(jsonDecode(rows.single.payloadJson)['fromLocationId'], yardId);
    expect(jsonDecode(rows.single.payloadJson)['expectedTrailerVersion'], 12);
  });
  test('unknown offline QR is not turned into authorized equipment', () async {
    f.mobile.network.status = ConnectivityStatus.unavailable;
    await f.mobile.session.refresh();
    await f.controller.load();
    await f.controller.scan();
    expect(f.controller.trailer, isNull);
    expect(f.controller.message, contains('online once'));
    expect(f.controller.canMove, isFalse);
  });
  test(
    'duplicate or malformed downloaded pages do not commit a partial context',
    () async {
      final old = await f.controller.cached(f.mobile.session.snapshot!);
      f.custom = (r) async => r.url.path.endsWith('/yard/sites')
          ? f.mobile.response({
              'items': [yardSite(), yardSite()],
              'nextCursor': null,
            })
          : f.reads(r);
      await f.controller.load(manual: true);
      expect(f.controller.sites, isEmpty);
      expect(f.controller.canMove, isFalse);
      final retained = await f.controller.cached(f.mobile.session.snapshot!);
      expect(retained!.projectionJson, old!.projectionJson);
    },
  );
  test('pages retain complete authorized locations and deduplicate neither silently nor infinitely', () async {
    final first = {
      ...yardSite(),
      'locations': [location(yardId)],
      'nextLocationCursor': yardId,
    };
    f.custom = (r) async {
      if (r.url.path.endsWith('/yard/sites')) {
        return f.mobile.response({
          'items': [first],
          'nextCursor': null,
        });
      }
      if (r.url.path.endsWith('/locations')) {
        return r.url.queryParameters['cursor'] == null
            ? f.mobile.response({
                'items': [location(yardId)],
                'nextCursor': yardId,
              })
            : f.mobile.response({
                'items': [location(dockId), location(dock2Id)],
                'nextCursor': null,
              });
      }
      return f.reads(r);
    };
    await f.controller.load(manual: true);
    expect(f.controller.selected!.locations, hasLength(3));
    expect(f.controller.selected!.nextLocationCursor, isNull);
  });
  for (final status in [401, 403]) {
    test(
      '$status persists Yard denial and cannot restore cached permission offline',
      () async {
        f.custom = (r) async => r.url.path.endsWith('/yard/sites')
            ? f.mobile.response({
                'code': 'CAPABILITY_DENIED',
                'detail': 'Denied',
              }, status)
            : f.reads(r);
        await f.controller.load(manual: true);
        expect(f.controller.blocked, isTrue);
        f.mobile.network.status = ConnectivityStatus.unavailable;
        await f.mobile.session.refresh();
        await f.controller.load();
        expect(f.controller.sites, isEmpty);
        expect(f.controller.usable, isFalse);
        expect(f.controller.blocked, isTrue);
      },
    );
  }
  test(
    'account switch fences late QR callbacks and does not create a command',
    () async {
      f.scanner.pending = Completer<String?>();
      final reading = f.controller.scan();
      await Future<void>.delayed(Duration.zero);
      f.mobile.auth.uid = 'firebase-B';
      await f.mobile.session.refresh();
      f.scanner.pending!.complete(qrToken);
      await reading;
      expect(f.controller.trailer, isNull);
      expect(await f.worker.store.list('user-A', 'A'), isEmpty);
      expect(await f.worker.store.list('user-B', 'A'), isEmpty);
    },
  );
  test(
    'Tenant change fences a late detail response and keeps namespaces separate',
    () async {
      final held = Completer<http.Response>();
      f.custom = (r) async =>
          r.url.path.contains('/yard/trailers/') ? held.future : f.reads(r);
      final reading = f.controller.scan();
      await Future<void>.delayed(const Duration(milliseconds: 20));
      f.mobile.memberships = [
        membership('B', capabilities: ['YARD_MOVE_EXECUTE']),
      ];
      await f.mobile.session.refresh();
      held.complete(f.mobile.response(yardTrailer()));
      await reading;
      expect(f.controller.trailer, isNull);
      expect(await f.worker.store.list('user-A', 'B'), isEmpty);
    },
  );
  test('double confirmation captures one operation and never fabricates a local move', () async {
    await f.controller.scan();
    f.mobile.network.status = ConnectivityStatus.unavailable;
    await f.mobile.session.refresh();
    await f.controller.load();
    await f.controller.scan();
    await Future.wait([
      f.controller.confirmMove(dockId, yardId),
      f.controller.confirmMove(dockId, yardId),
    ]);
    expect(await f.worker.store.list('user-A', 'A'), hasLength(1));
    expect(f.controller.trailer!.location!['id'], yardId);
  });
  test(
    'same destination and invalid/OOS/coupled/unknown projections are rejected',
    () async {
      await f.controller.scan();
      await f.controller.confirmMove(yardId, yardId);
      expect(await f.worker.store.list('user-A', 'A'), isEmpty);
      for (final field in [
        'serviceabilityState',
        'couplingState',
        'locationState',
      ]) {
        f.currentTrailer = {
          ...yardTrailer(),
          field: field == 'serviceabilityState'
              ? 'OUT_OF_SERVICE'
              : field == 'couplingState'
              ? 'COUPLED'
              : 'LOCATION_UNKNOWN',
        };
        if (field == 'locationState') {
          f.currentTrailer['currentLocation'] = null;
        }
        await f.controller.scan();
        expect(f.controller.canMove, isFalse);
      }
      expect(
        () => YardTrailer.fromJson({...yardTrailer(), 'actor_type': 'SYSTEM'}),
        throwsFormatException,
      );
    },
  );
}
