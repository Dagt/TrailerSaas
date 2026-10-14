import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/yard/check_contracts.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory dir;
  late CheckFixture f;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('check-replay-');
    f = CheckFixture(dir, 1);
    await f.open();
  });
  tearDown(() async {
    await f.close();
    await dir.delete(recursive: true);
  });
  test('start uses server ID, durable original UUID and same-key lost ACK recovery', () async {
    final o = f.mobile.session.snapshot!;
    final id = await f.replay.capture(o, 'YARD_CHECK_START', f.cache.root(o), {
      'siteId': yardSiteId,
      'locationId': null,
      '_capturedAt': DateTime.now().toUtc().toIso8601String(),
    });
    expect(await f.cache.list(o), isEmpty);
    var lost = false;
    f.custom = (r) async {
      final result = await f.handle(r);
      if (r.method == 'POST' && !lost) {
        lost = true;
        throw const SocketException('lost ACK');
      }
      return result;
    };
    await f.worker.sync();
    expect(f.starts, 1);
    final first = (await f.worker.store.list(checkUser, 'A')).single;
    expect(first.state, 'RETRYABLE_FAILURE');
    await f.mobile.db.customStatement(
      'UPDATE local_commands SET next_retry_at=NULL',
    );
    await f.worker.sync();
    expect(f.starts, 1);
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.clientOperationId,
      id,
    );
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.state,
      'ACKNOWLEDGED',
    );
    expect((await f.cache.list(o)).single.id, checkId);
  });
  test('unknown QR is durable unresolved, never invents a match, then resolves with original time and UUID', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final at = DateTime.now().toUtc().subtract(const Duration(seconds: 1));
    f.mobile.network.status = ConnectivityStatus.unavailable;
    final id = await f.replay.scan(o, checkId, qrToken, yardId, at, () => true);
    expect(
      (await f.cache.observations(o, checkId)).single.localResult,
      'UNRESOLVED_QR',
    );
    expect(
      (await f.cache.observations(o, checkId)).single.identifiedTrailerId,
      isNull,
    );
    await f.worker.sync();
    expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    expect(
      await f.replay.scan(
        o,
        checkId,
        qrToken,
        dockId,
        at.add(const Duration(seconds: 1)),
        () => true,
      ),
      id,
    );
    f.mobile.network.status = ConnectivityStatus.available;
    await f.worker.sync();
    final c = (await f.worker.store.list(checkUser, 'A')).single;
    expect(c.clientOperationId, id);
    expect(c.state, 'ACKNOWLEDGED');
    final frozen =
        (await f.mobile.db.select(f.mobile.db.materializedRequests).get())
            .single;
    expect(jsonDecode(frozen.payloadJson), {
      'trailerId': trailerId,
      'observedLocationId': yardId,
      'scannedAt': at.toIso8601String(),
    });
    expect(
      (await f.cache.observations(o, checkId)).single.resolvedTrailerId,
      trailerId,
    );
    expect(f.summary['matchedCount'], 1);
  });
  test('verified owner-scoped token identifies locally; duplicates preserve first observation', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    await f.mobile.cache.rememberIdentifiedTrailer(o, qrToken, {
      ...yardTrailer(),
      'yardProjection': true,
    }, () => true);
    final id = await f.replay.scan(
      o,
      checkId,
      qrToken,
      dockId,
      DateTime.now().toUtc(),
      () => true,
    );
    expect(
      (await f.cache.observations(o, checkId)).single.localResult,
      'LOCATION_MISMATCH',
    );
    expect(
      await f.replay.scan(
        o,
        checkId,
        qrToken,
        yardId,
        DateTime.now().toUtc(),
        () => true,
      ),
      id,
    );
    expect(await f.worker.store.list(checkUser, 'A'), hasLength(1));
    expect(
      (await f.cache.observations(o, checkId)).single.observedLocationId,
      dockId,
    );
  });
  for (final code in [408, 429, 500, 503, 403, 404, 409]) {
    test(
      'scan $code keeps evidence and original identity, without ACK or physical mutation',
      () async {
        await f.snapshot();
        final o = f.mobile.session.snapshot!;
        final id = await f.replay.scan(
          o,
          checkId,
          qrToken,
          yardId,
          DateTime.now().toUtc(),
          () => true,
        );
        f.custom = (r) async => r.method == 'POST'
            ? f.mobile.response({
                'code': code == 409 ? 'RESOURCE_CONFLICT' : 'TEST_FAILURE',
              }, code)
            : f.handle(r);
        await f.worker.sync();
        final c = (await f.worker.store.list(checkUser, 'A')).single;
        expect(c.clientOperationId, id);
        expect(
          c.state,
          code >= 500 || code == 408 || code == 429
              ? 'RETRYABLE_FAILURE'
              : 'CONFLICT',
        );
        expect(
          await f.mobile.db.select(f.mobile.db.commandResults).get(),
          isEmpty,
        );
        expect(await f.cache.observations(o, checkId), hasLength(1));
        expect(f.summary['scannedCount'], 0);
      },
    );
  }
  test('malformed HTTP success is not an accepted scan ACK', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    await f.replay.scan(
      o,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    f.custom = (r) async => r.method == 'POST'
        ? f.mobile.response({'outcome': 'MATCHED'})
        : f.handle(r);
    await f.worker.sync();
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.state,
      'RETRYABLE_FAILURE',
    );
    expect(await f.mobile.db.select(f.mobile.db.commandResults).get(), isEmpty);
  });
  test('frozen scan and original observations survive actual SQLite reopen and lost ACK', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final id = await f.replay.scan(
      o,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    var lost = false;
    f.custom = (r) async {
      final result = await f.handle(r);
      if (r.method == 'POST' && !lost) {
        lost = true;
        throw const SocketException('lost ACK');
      }
      return result;
    };
    await f.worker.sync();
    final frozen =
        (await f.mobile.db.select(f.mobile.db.materializedRequests).getSingle())
            .payloadJson;
    await f.worker.shutdown();
    await f.mobile.db.close();
    final reopened = AppDatabase(
      NativeDatabase(File('${dir.path}/checks.sqlite')),
    );
    expect(
      (await reopened.select(reopened.localCommands).getSingle())
          .clientOperationId,
      id,
    );
    expect(
      (await reopened.select(reopened.materializedRequests).getSingle())
          .payloadJson,
      frozen,
    );
    expect(
      (await reopened.select(reopened.yardCheckObservations).getSingle())
          .operationId,
      id,
    );
    expect(
      await reopened.customSelect('PRAGMA foreign_key_check').get(),
      isEmpty,
    );
    await expectLater(
      reopened.customStatement(
        "UPDATE yard_check_observations SET scanned_at='2026-10-13T00:00:00Z'",
      ),
      throwsA(anything),
    );
    await reopened.close();
  });
  test('completion obtains actual version after scan ACK and leaves missing to server', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    await f.replay.scan(
      o,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    expect(await f.replay.settled(o, checkId), false);
    await f.worker.sync();
    expect(await f.replay.settled(o, checkId), true);
    final state = await f.cache.download(
      f.mobile.session,
      o,
      checkId,
      () => true,
    );
    await f.replay.capture(
      o,
      'YARD_CHECK_COMPLETE',
      '${f.cache.root(o)}/$checkId/completion',
      {
        'review': state.review,
        '_capturedAt': DateTime.now().toUtc().toIso8601String(),
      },
      dependencies: [
        (await f.worker.store.list(checkUser, 'A')).single.clientOperationId,
      ],
    );
    expect((await f.cache.find(o, checkId))!.status, 'IN_PROGRESS');
    await f.worker.sync();
    expect(f.summary['status'], 'COMPLETED');
    expect((await f.cache.find(o, checkId))!.status, 'COMPLETED');
    final posts = f.mobile.requests
        .where((r) => r.url.path.endsWith('/completion'))
        .toList();
    expect(jsonDecode(posts.single.body), {'expectedVersion': 1});
  });
  test(
    'changed review conflicts rather than guessing a new completion version',
    () async {
      await f.snapshot();
      final o = f.mobile.session.snapshot!;
      final state = CheckSnapshot(
        CheckSummary(f.summary),
        f.items.map(CheckItem.new).toList(),
      );
      await f.replay.capture(
        o,
        'YARD_CHECK_COMPLETE',
        '${f.cache.root(o)}/$checkId/completion',
        {
          'review': state.review,
          '_capturedAt': DateTime.now().toUtc().toIso8601String(),
        },
      );
      f.summary = {...f.summary, 'version': 1};
      await f.worker.sync();
      expect(
        (await f.worker.store.list(checkUser, 'A')).single.lastErrorCode,
        'REVIEW_REQUIRED',
      );
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      expect(
        (await f.cache.items(o, checkId)).single.serverJson,
        contains('"result":null'),
      );
    },
  );
  test('cancellation confirms real terminal state and retains original partial inventory', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final state = await f.cache.download(
      f.mobile.session,
      o,
      checkId,
      () => true,
    );
    await f.replay.capture(
      o,
      'YARD_CHECK_CANCEL',
      '${f.cache.root(o)}/$checkId/cancellation',
      {
        'review': state.review,
        'reason': 'Stopped safely',
        '_capturedAt': DateTime.now().toUtc().toIso8601String(),
      },
    );
    await f.worker.sync();
    expect((await f.cache.find(o, checkId))!.status, 'CANCELLED');
    expect(
      (await f.cache.items(o, checkId)).single.originalJson,
      contains('"result":null'),
    );
    expect(f.summary['missingCount'], 0);
  });
}
