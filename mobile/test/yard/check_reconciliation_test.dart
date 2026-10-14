import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';
import 'package:trailersaas_mobile/src/yard/check_contracts.dart';
import 'package:trailersaas_mobile/src/yard/check_controller.dart';
import 'package:trailersaas_mobile/src/yard/yard_contracts.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory dir;
  late CheckFixture f;
  late YardCheckController c;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('check-reconciliation-');
    f = CheckFixture(dir, 1);
    await f.open();
    c = YardCheckController(f.mobile.session, f.worker, FakeYardScanner());
  });
  tearDown(() async {
    c.dispose();
    await f.close();
    await dir.delete(recursive: true);
  });
  Future<CheckItem> completed(bool missing) async {
    f.summary = checkSummary(
      1,
      status: 'COMPLETED',
      version: 2,
      mismatch: missing ? 0 : 1,
    );
    f.items.single.addAll({
      'result': missing ? 'MISSING' : 'LOCATION_MISMATCH',
      'found': !missing,
      if (!missing) ...{
        'observedLocationId': dockId,
        'scannedAt': '2026-10-13T12:00:00Z',
        'recordedAt': '2026-10-13T12:00:00Z',
        'scannedByUserId': checkUser,
        'clientOperationId': inventoryId(99),
      },
    });
    await f.snapshot();
    c.selected = await f.cache.find(f.mobile.session.snapshot!, checkId);
    await c.loadLocal();
    return CheckItem(f.items.single);
  }

  Map<String, dynamic> receipt(bool missing) => {
    'reconciliationId': inventoryId(90),
    'yardCheckId': checkId,
    'itemId': inventoryId(1),
    'trailer': {
      ...yardTrailer(current: missing ? yardId : dockId, version: 13),
      if (missing) ...{
        'locationState': 'LOCATION_UNKNOWN',
        'currentLocation': null,
      },
    },
    'movementEvent': {
      'id': inventoryId(91),
      'eventType': missing ? 'LOCATION_MARKED_UNKNOWN' : 'LOCATION_CORRECTED',
      'occurredAt': '2026-10-14T12:00:00Z',
      'recordedAt': '2026-10-14T12:00:00Z',
    },
    'alertId': missing ? inventoryId(92) : null,
  };
  for (final missing in [true, false]) {
    test(
      'explicit ${missing ? 'Missing' : 'Location mismatch'} reconciliation freezes actual current version and validates real event/alert receipt',
      () async {
        final item = await completed(missing),
            original = (await f.cache.items(
              f.mobile.session.snapshot!,
              checkId,
            )).single.originalJson;
        expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
        f.custom = (r) async {
          if (r.method == 'POST') {
            f.items.single['reconciliationId'] = inventoryId(90);
            return f.mobile.response(receipt(missing));
          }
          return f.handle(r);
        };
        await c.reconcile(
          item,
          YardTrailer.fromJson(yardTrailer()),
          'Verified physical observation',
        );
        final rows = await f.worker.store.list(checkUser, 'A');
        expect(rows.single.state, 'ACKNOWLEDGED');
        final posts = f.mobile.requests
            .where((r) => r.method == 'POST')
            .toList();
        expect(posts, hasLength(1));
        expect(jsonDecode(posts.single.body), {
          'expectedTrailerVersion': 12,
          'reason': 'Verified physical observation',
          if (!missing) 'newLocationId': dockId,
        });
        final saved = jsonDecode(
          (await f.mobile.db.select(f.mobile.db.commandResults).getSingle())
              .projectionJson,
        ) as Map;
        expect(saved['alertId'], missing ? inventoryId(92) : null);
        expect(
          (await f.cache.items(
            f.mobile.session.snapshot!,
            checkId,
          )).single.originalJson,
          original,
        );
        expect(
          (await f.cache.items(
            f.mobile.session.snapshot!,
            checkId,
          )).single.serverJson,
          contains(inventoryId(90)),
        );
      },
    );
  }
  test(
    'QR/Check membership does not grant location correction capability',
    () async {
      await completed(true);
      final old = f.mobile.session.snapshot!;
      f.mobile.session.snapshot = SessionSnapshot(
        old.firebaseUid,
        old.userId,
        Membership('A', 'Tenant', {'YARD_CHECK_EXECUTE'}),
        null,
        old.refreshedAt,
        [],
      );
      expect(c.canCorrect, false);
      await expectLater(c.currentTrailer(trailerId), throwsStateError);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    },
  );
  test('stale Trailer reconciliation remains conflicted and never changes original finding', () async {
    final item = await completed(true),
        original = (await f.cache.items(
          f.mobile.session.snapshot!,
          checkId,
        )).single.originalJson;
    f.custom = (r) async => r.method == 'POST'
        ? f.mobile.response({'code': 'STALE_VERSION'}, 409)
        : f.handle(r);
    await c.reconcile(item, YardTrailer.fromJson(yardTrailer()), 'Recheck');
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.state,
      'CONFLICT',
    );
    expect(
      (await f.cache.items(
        f.mobile.session.snapshot!,
        checkId,
      )).single.originalJson,
      original,
    );
    expect(await f.mobile.db.select(f.mobile.db.commandResults).get(), isEmpty);
  });
  test('invalid alert/event success response cannot confirm physical reconciliation', () async {
    final item = await completed(true);
    f.custom = (r) async => r.method == 'POST'
        ? f.mobile.response({...receipt(true), 'alertId': null})
        : f.handle(r);
    await c.reconcile(item, YardTrailer.fromJson(yardTrailer()), 'Recheck');
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.state,
      'RETRYABLE_FAILURE',
    );
    expect(await f.mobile.db.select(f.mobile.db.commandResults).get(), isEmpty);
  });
}
