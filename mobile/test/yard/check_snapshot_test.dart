import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/api_client.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';
import 'package:trailersaas_mobile/src/yard/check_contracts.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory dir;
  late CheckFixture f;
  Future<void> open(int n) async {
    dir = await Directory.systemTemp.createTemp('check-snapshot-');
    f = CheckFixture(dir, n);
    await f.open();
    addTearDown(() async {
      await f.close();
      await dir.delete(recursive: true);
    });
  }

  test('an empty HTTP page without the server first-page completeness marker is not offline readiness', () async {
    await open(0);
    f.custom = (r) async => r.url.path.endsWith('/$checkId')
        ? f.mobile.response({...f.page(), 'snapshotComplete': false})
        : f.handle(r);
    await expectLater(f.snapshot(), throwsFormatException);
    expect(await f.cache.find(f.mobile.session.snapshot!, checkId), isNull);
  });
  test('directory denial blocks previously downloaded Check without deleting evidence', () async {
    await open(1);
    await f.snapshot();
    final owner = f.mobile.session.snapshot!;
    f.custom = (r) async => r.url.path.endsWith('/yard-checks')
        ? f.mobile.response({'code': 'CAPABILITY_DENIED'}, 403)
        : f.handle(r);
    await expectLater(
      f.cache.directory(f.mobile.session, owner, () => true),
      throwsA(isA<ApiProblem>()),
    );
    expect((await f.cache.find(owner, checkId))!.accessBlocked, true);
    expect(await f.cache.items(owner, checkId), hasLength(1));
    expect(
      (await f.mobile.db.select(f.mobile.db.yardCheckDirectories).getSingle())
          .accessBlocked,
      true,
    );
  });

  for (final size in [0, 1, 200, 201, 206]) {
    test(
      'downloads every authoritative item for $size expected, then durably marks complete',
      () async {
        await open(size);
        await f.snapshot();
        final owner = f.mobile.session.snapshot!;
        expect((await f.cache.find(owner, checkId))!.snapshotDownloaded, true);
        expect(await f.cache.items(owner, checkId), hasLength(size));
        final pages = f.mobile.requests
            .where((r) => r.url.path.endsWith('/$checkId'))
            .toList();
        expect(pages, hasLength(size > 200 ? 2 : 1));
        if (size > 200) {
          expect(pages.last.url.queryParameters['expectedVersion'], '0');
        }
        expect(
          await f.mobile.db.customSelect('PRAGMA foreign_key_check').get(),
          isEmpty,
        );
      },
    );
  }
  test('partial, missing, duplicate and invalid cursor never commit an offline snapshot', () async {
    await open(206);
    for (final defect in [
      'partial',
      'duplicate',
      'cursor',
      'identity',
      'scope',
    ]) {
      f.custom = (r) async {
        if (!r.url.path.endsWith('/$checkId')) return f.handle(r);
        final page = f.page(r.url.queryParameters['cursor']);
        if (defect == 'partial') page['nextCursor'] = null;
        if (defect == 'duplicate') {
          page['items'] = [
            ...(page['items'] as List),
            ...(page['items'] as List).take(1),
          ];
        }
        if (defect == 'cursor') page['nextCursor'] = 'not-a-uuid';
        if (defect == 'identity') {
          page['check'] = {...f.summary, 'id': trailerId};
        }
        if (defect == 'scope' && r.url.queryParameters['cursor'] != null) {
          page['check'] = {...f.summary, 'siteId': dockId};
        }
        return f.mobile.response(page);
      };
      await expectLater(f.snapshot(), throwsA(anything));
      expect(await f.cache.find(f.mobile.session.snapshot!, checkId), isNull);
    }
  });
  test(
    'version race restarts the whole candidate and never combines versions',
    () async {
      await open(206);
      var raced = false;
      f.custom = (r) async {
        if (r.url.path.endsWith('/$checkId') &&
            r.url.queryParameters['cursor'] != null &&
            !raced) {
          raced = true;
          f.summary = {...f.summary, 'version': 1};
          return f.mobile.response({'code': 'STALE_VERSION'}, 409);
        }
        return f.handle(r);
      };
      await f.snapshot();
      expect(
        (await f.cache.find(
          f.mobile.session.snapshot!,
          checkId,
        ))!.serverVersion,
        1,
      );
      expect(
        await f.cache.items(f.mobile.session.snapshot!, checkId),
        hasLength(206),
      );
    },
  );
  test(
    'late account response cannot commit a snapshot into either namespace',
    () async {
      await open(1);
      var active = true;
      f.custom = (r) async {
        final response = await f.handle(r);
        if (r.url.path.endsWith('/$checkId')) active = false;
        return response;
      };
      await expectLater(
        f.cache.download(
          f.mobile.session,
          f.mobile.session.snapshot!,
          checkId,
          () => active,
        ),
        throwsStateError,
      );
      expect(
        await f.mobile.db.select(f.mobile.db.cachedYardChecks).get(),
        isEmpty,
      );
    },
  );
  test('failed replacement transaction keeps original immutable inventory and previous complete cache', () async {
    await open(1);
    await f.snapshot();
    final o = f.mobile.session.snapshot!;
    final changed = CheckItem({
      ...checkItem(1),
      'expectedLocationId': dockId,
      'recordedSnapshot': {
        ...checkItem(1)['recordedSnapshot'] as Map<String, dynamic>,
        'currentLocationId': dockId,
      },
    });
    await expectLater(
      f.cache.persist(
        o,
        CheckSnapshot(CheckSummary(f.summary), [changed]),
        () => true,
      ),
      throwsFormatException,
    );
    expect((await f.cache.items(o, checkId)).single.expectedLocationId, yardId);
    expect((await f.cache.find(o, checkId))!.snapshotDownloaded, true);
    final other = SessionSnapshot(
      o.firebaseUid,
      'other-user',
      Membership('other-tenant', 'Other', {'YARD_CHECK_EXECUTE'}),
      null,
      o.refreshedAt,
      [],
    );
    expect(await f.cache.items(other, checkId), isEmpty);
    expect(await f.cache.find(other, checkId), isNull);
  });
  test(
    '403 access denial retains snapshot but closes offline eligibility',
    () async {
      await open(1);
      await f.snapshot();
      f.custom = (r) async => r.url.path.endsWith('/$checkId')
          ? f.mobile.response({'code': 'CAPABILITY_DENIED'}, 403)
          : f.handle(r);
      await expectLater(f.snapshot(), throwsA(isA<ApiProblem>()));
      expect(
        (await f.cache.find(
          f.mobile.session.snapshot!,
          checkId,
        ))!.accessBlocked,
        true,
      );
      expect(
        await f.cache.items(f.mobile.session.snapshot!, checkId),
        hasLength(1),
      );
    },
  );
}
