import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';
import 'package:trailersaas_mobile/src/yard/check_contracts.dart';
import 'package:trailersaas_mobile/src/yard/yard_commands.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory dir;
  late CheckFixture f;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('check-security-');
    f = CheckFixture(dir, 1);
    await f.open();
  });
  tearDown(() async {
    await f.close();
    await dir.delete(recursive: true);
  });
  test('reordered JSON object fields preserve deterministic review and immutable expected facts', () async {
    final item = checkItem(1), summary = checkSummary(1);
    final reordered = {
      for (final key in item.keys.toList().reversed) key: item[key],
    };
    reordered['recordedSnapshot'] = {
      for (final key
          in (item['recordedSnapshot'] as Map<String, dynamic>).keys
              .toList()
              .reversed)
        key: (item['recordedSnapshot'] as Map)[key],
    };
    expect(CheckItem(item).origin, CheckItem(reordered).origin);
    expect(
      CheckSnapshot(CheckSummary(summary), [CheckItem(item)]).review,
      CheckSnapshot(
        CheckSummary({
          for (final key in summary.keys.toList().reversed) key: summary[key],
        }),
        [CheckItem(reordered)],
      ).review,
    );
  });
  test(
    'a successful scan response for changed Check scope cannot produce an ACK',
    () async {
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
      f.custom = (r) async {
        final result = await f.handle(r);
        if (r.method == 'POST') {
          final raw = jsonDecode(result.body) as Map<String, dynamic>;
          raw['check'] = {
            ...raw['check'] as Map<String, dynamic>,
            'siteId': dockId,
          };
          return f.mobile.response(raw);
        }
        return result;
      };
      await f.worker.sync();
      expect(
        (await f.worker.store.list(checkUser, 'A')).single.state,
        'RETRYABLE_FAILURE',
      );
      expect(
        await f.mobile.db.select(f.mobile.db.commandResults).get(),
        isEmpty,
      );
    },
  );
  test('foreign or revoked QR remains a private failed observation and never submits fabricated identity', () async {
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
    f.custom = (r) async => r.url.path.contains('/trailer-qr/')
        ? f.mobile.response({'code': 'RESOURCE_NOT_FOUND'}, 404)
        : f.handle(r);
    await f.worker.sync();
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.state,
      'CONFLICT',
    );
    expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    expect(
      (await f.cache.observations(o, checkId)).single.resolvedTrailerId,
      isNull,
    );
    expect(
      (await f.cache.observations(o, checkId)).single.publicToken,
      qrToken,
    );
  });
  test('401 refresh retries same materialized scan UUID and body', () async {
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
    var denied = false;
    f.custom = (r) async {
      if (r.method == 'POST' && !denied) {
        denied = true;
        return f.mobile.response({'code': 'AUTHENTICATION_REQUIRED'}, 401);
      }
      return f.handle(r);
    };
    await f.worker.sync();
    final posts = f.mobile.requests.where((r) => r.method == 'POST').toList();
    expect(posts, hasLength(2));
    expect(posts[0].body, posts[1].body);
    expect(
      posts[0].headers['Idempotency-Key'],
      posts[1].headers['Idempotency-Key'],
    );
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.state,
      'ACKNOWLEDGED',
    );
    expect(f.summary['scannedCount'], 1);
  });
  test('two different unresolved tokens resolving same Trailer receive actual ALREADY_SCANNED receipt without double counting', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final first = await f.replay.scan(
      o,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    final second = await f.replay.scan(
      o,
      checkId,
      'BBBBBBBBBBBBBBBBBBBBBB',
      dockId,
      DateTime.now().toUtc(),
      () => true,
    );
    await f.worker.sync();
    expect(f.summary['scannedCount'], 1);
    final results = await f.mobile.db.select(f.mobile.db.commandResults).get();
    final receipt = jsonDecode(
      results.firstWhere((r) => r.clientOperationId == second).projectionJson,
    ) as Map;
    expect(receipt['outcome'], 'ALREADY_SCANNED');
    expect((receipt['item'] as Map)['clientOperationId'], first);
    expect(await f.cache.observations(o, checkId), hasLength(2));
  });
  test(
    'Check conflict does not starve an independent Yard Trailer movement',
    () async {
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
      await YardReplay(f.worker.store).capture(
        checkUser,
        'A',
        yardTrailer(id: secondTrailerId),
        dockId,
        DateTime.now().toUtc(),
      );
      f.custom = (r) async {
        if (r.method == 'POST' && r.url.path.endsWith('/scans')) {
          return f.mobile.response({'code': 'RESOURCE_CONFLICT'}, 409);
        }
        if (r.method == 'POST' && r.url.path.endsWith('/yard-moves')) {
          return f.mobile.response(
            acceptedMove(jsonDecode(r.body) as Map<String, dynamic>),
          );
        }
        return f.handle(r);
      };
      await f.worker.sync();
      final rows = await f.worker.store.list(checkUser, 'A');
      expect(rows.first.state, 'CONFLICT');
      expect(rows.last.state, 'ACKNOWLEDGED');
    },
  );
  test('two workers and two SQLite connections retain same-key receipt with no double inventory observation', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final op = await f.replay.scan(
      o,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    final entered = Completer<void>(), release = Completer<void>();
    var held = false;
    f.custom = (r) async {
      final receipt = await f.handle(r);
      if (r.method == 'POST' && !held) {
        held = true;
        entered.complete();
        await release.future;
      }
      return receipt;
    };
    final second = CheckFixture(dir, 1);
    second.custom = (r) => f.custom!(r);
    await second.open();
    try {
      final running = f.worker.sync();
      await entered.future.timeout(const Duration(seconds: 5));
      await second.worker.sync();
      release.complete();
      await running;
      expect(
        (await f.worker.store.list(checkUser, 'A')).single.state,
        'ACKNOWLEDGED',
      );
      expect(
        (await second.worker.store.list(
          checkUser,
          'A',
        )).single.clientOperationId,
        op,
      );
      expect(f.summary['scannedCount'], 1);
      expect(
        await f.mobile.db.select(f.mobile.db.commandResults).get(),
        hasLength(1),
      );
    } finally {
      if (!release.isCompleted) release.complete();
      await second.close();
    }
  });
  test('historical start ACK acknowledges without regressing a newer cached server Check', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final op = await f.replay.capture(o, 'YARD_CHECK_START', f.cache.root(o), {
      'siteId': yardSiteId,
      'locationId': null,
      '_capturedAt': DateTime.now().toUtc().toIso8601String(),
    });
    await f.cache.summary(o, CheckSummary({...f.summary, 'version': 1}));
    final c = (await f.worker.store.list(checkUser, 'A')).single;
    await f.replay.acknowledge(c, o, f.page(), {
      'siteId': yardSiteId,
      'locationId': null,
    });
    expect((await f.cache.find(o, checkId))!.serverVersion, 1);
    expect(
      (await f.worker.store.find(checkUser, 'A', op))!.state,
      'ACKNOWLEDGED',
    );
  });
  test('review metadata cannot exclude sending uncertain observations or cross-owner failures', () async {
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final op = await f.replay.scan(
      o,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    await expectLater(
      f.mobile.db.customStatement(
        "INSERT INTO yard_check_reviews VALUES(?,?,?,?,'review','{}',1)",
        [op, checkUser, 'A', checkId],
      ),
      throwsA(anything),
    );
    await expectLater(
      f.mobile.db.customStatement(
        "UPDATE yard_check_observations SET user_id='other-owner'",
      ),
      throwsA(anything),
    );
    expect((await f.worker.store.find(checkUser, 'A', op))!.state, 'QUEUED');
  });
  test('unresolved observation and account logout retain commands but cannot appear in another owner namespace', () async {
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
    await f.mobile.session.signOut();
    final other = SessionSnapshot(
      'firebase-B',
      'other-user',
      Membership('A', 'Tenant', {'YARD_CHECK_EXECUTE'}),
      null,
      DateTime.now(),
      [],
    );
    expect(await f.cache.list(other), isEmpty);
    expect(await f.cache.observations(other, checkId), isEmpty);
    expect(await f.worker.store.list(checkUser, 'A'), hasLength(1));
    expect(await f.cache.observations(o, checkId), hasLength(1));
  });
}
