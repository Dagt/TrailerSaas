import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';
import 'package:trailersaas_mobile/src/shift/inspection_store.dart';
import 'package:trailersaas_mobile/src/shift/shift_commands.dart';
import 'package:trailersaas_mobile/src/yard/check_controller.dart';
import 'package:trailersaas_mobile/src/yard/check_contracts.dart';
import 'package:trailersaas_mobile/src/yard/yard_contracts.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory dir;
  late CheckFixture f;
  late YardCheckController c;
  late FakeYardScanner scanner;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('check-controller-');
    f = CheckFixture(dir, 1);
    await f.open();
    await f.snapshot();
    scanner = FakeYardScanner();
    c = YardCheckController(f.mobile.session, f.worker, scanner);
    c.selected = await f.cache.find(f.mobile.session.snapshot!, checkId);
    await c.loadLocal();
  });
  tearDown(() async {
    if (!c.disposed) c.dispose();
    await f.close();
    await dir.delete(recursive: true);
  });
  test(
    'Yard-only user scans without a Driver, Tractor or location mutation',
    () async {
      expect(f.mobile.session.snapshot!.driver, isNull);
      await c.scan(yardId);
      await f.worker.sync();
      expect(
        (await f.worker.store.list(checkUser, 'A')).single.state,
        'ACKNOWLEDGED',
      );
      expect(
        f.mobile.requests.where(
          (r) => r.method == 'POST' && !r.url.path.endsWith('/scans'),
        ),
        isEmpty,
      );
    },
  );
  test(
    'scanner can durably capture next QR while the first server ACK is held',
    () async {
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
      try {
        await c.scan(yardId).timeout(const Duration(seconds: 2));
        await entered.future.timeout(const Duration(seconds: 2));
        scanner.value = 'BBBBBBBBBBBBBBBBBBBBBB';
        await c.scan(yardId).timeout(const Duration(seconds: 2));
        expect(
          await f.cache.observations(f.mobile.session.snapshot!, checkId),
          hasLength(2),
        );
        expect(c.busy, false);
        release.complete();
        await f.worker.sync();
        expect(f.summary['scannedCount'], 1);
      } finally {
        if (!release.isCompleted) release.complete();
      }
    },
  );
  for (final boundary in [
    'background',
    'account',
    'disposed',
    'mode',
    'site',
  ]) {
    test(
      'late native callback after $boundary creates no observation or HTTP mutation',
      () async {
        scanner.pending = Completer<String?>();
        final scan = c.scan(yardId);
        await Future<void>.delayed(const Duration(milliseconds: 10));
        if (boundary == 'account') {
          f.mobile.auth.uid = 'firebase-B';
        } else if (boundary == 'disposed') {
          c.dispose();
        } else if (boundary == 'site') {
          c.invalidateScope();
        } else {
          c.suspend();
        }
        scanner.pending!.complete(qrToken);
        await scan;
        expect(
          await f.mobile.db.select(f.mobile.db.yardCheckObservations).get(),
          isEmpty,
        );
        expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      },
    );
  }
  test('malformed QR and native cancel never generate a command', () async {
    scanner.value = 'https://evil.invalid/not-a-trailer';
    await c.scan(yardId);
    scanner.value = null;
    await c.scan(yardId);
    expect(await f.worker.store.list(checkUser, 'A'), isEmpty);
  });
  test('unresolved QR blocks completion and cancellation review', () async {
    await f.replay.scan(
      f.mobile.session.snapshot!,
      checkId,
      qrToken,
      yardId,
      DateTime.now().toUtc(),
      () => true,
    );
    await expectLater(c.reviewState(), throwsStateError);
    expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
  });
  test('online start signals only an actual server-created identity and complete snapshot', () async {
    c.selected = null;
    await c.begin(YardSite.fromJson(yardSite()), null);
    expect(f.starts, 1);
    expect(c.selected!.id, checkId);
    expect(c.selected!.snapshotDownloaded, true);
    expect(c.message, 'Snapshot complete — available offline');
  });
  test('Off Duty actual capture blocks known open Check across modes; confirmed terminal refresh releases it', () async {
    final old = f.mobile.session.snapshot!;
    final o = SessionSnapshot(
      old.firebaseUid,
      old.userId,
      Membership('A', 'Tenant', {'YARD_CHECK_EXECUTE', 'SHIFT_END'}),
      DriverProfile('driver', 'Driver'),
      old.refreshedAt,
      [],
    );
    f.mobile.session.snapshot = o;
    final store = InspectionStore(
      ShiftReplay(f.worker.store, f.mobile.session),
    );
    await expectLater(
      store.offDuty(const ShiftReference.server(checkId)),
      throwsStateError,
    );
    expect(await f.worker.store.list(checkUser, 'A'), isEmpty);
    await f.cache.summary(
      o,
      CheckSummary(checkSummary(1, version: 1, status: 'COMPLETED')),
    );
    final op = await store.offDuty(const ShiftReference.server(checkId));
    expect(
      (await f.worker.store.list(checkUser, 'A')).single.clientOperationId,
      op,
    );
    expect((await f.worker.store.list(checkUser, 'A')).single.state, 'QUEUED');
  });
  test(
    'old summary cannot regress a newer Check or reopen a completed Check',
    () async {
      final o = f.mobile.session.snapshot!;
      await f.cache.summary(
        o,
        CheckSummary(checkSummary(1, version: 2, status: 'COMPLETED')),
      );
      await expectLater(
        f.cache.summary(o, CheckSummary(checkSummary(1))),
        throwsA(anything),
      );
      expect((await f.cache.find(o, checkId))!.status, 'COMPLETED');
    },
  );
}
