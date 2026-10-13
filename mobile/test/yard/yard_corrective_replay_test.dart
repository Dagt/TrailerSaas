import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/yard/yard_commands.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant;
import '../road/online_execution_test.dart' show MemoryJournal;
import 'fixtures.dart';

void main() {
  late MediaHttpFixture f;
  setUp(() async {
    f = MediaHttpFixture()..yardWorkflows = true;
    await f.start(fileDatabase: true);
  });
  tearDown(() => f.close());
  Future<String> action(
    String kind,
    Map<String, dynamic> fields, {
    bool reviewed = false,
  }) async => (await YardReplay(f.worker.store).captureAction(
    user,
    tenant,
    {...f.yardState},
    kind,
    fields,
    f.now,
    reviewedConflict: reviewed,
  ))!;
  Future<String> photo() => f.media.capture(
    f.session,
    'DAMAGE_PHOTO',
    'image/jpeg',
    Stream.value([0xff, 0xd8, 0xff, 0x01]),
    trailerId: trailerId,
  );
  Map<String, dynamic> fields(String kind) => switch (kind) {
    'LOCATION_CORRECTION' => {
      'siteId': yardSiteId,
      'newLocationId': dockId,
      'reason': 'Observed in Dock 17',
    },
    'LOAD_STATE_CHANGE' => {
      'siteId': yardSiteId,
      'loadState': 'EMPTY',
      'reason': 'Unload physically verified',
    },
    _ => {
      'siteId': yardSiteId,
      'reportedLocationId': dockId,
      'reason': 'Recorded origin differs from observation',
    },
  };
  Future<void> offline() async {
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
  }

  Future<void> online() async {
    f.network.status = ConnectivityStatus.available;
    await f.session.refresh();
    await f.worker.sync();
  }

  test('driverless Yard correction is durable before POST and preserves location until ACK', () async {
    expect(f.session.snapshot!.driver, isNull);
    await offline();
    final id = await action(
      'LOCATION_CORRECTION',
      fields('LOCATION_CORRECTION'),
    );
    final original = (await f.worker.store.find(user, tenant, id))!;
    expect(original.state, 'QUEUED');
    expect(f.posts, isEmpty);
    expect(f.yardState['currentLocation']['id'], yardId);
    await f.restart();
    expect(
      (await f.worker.store.find(user, tenant, id))!.payloadJson,
      original.payloadJson,
    );
    await online();
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'ACKNOWLEDGED',
    );
    expect(f.yardState['currentLocation']['id'], dockId);
    expect(f.yardState['version'], 13);
    expect(f.yardState['loadState'], 'LOADED');
    expect(f.yardState['serviceabilityState'], 'IN_SERVICE');
    expect(f.businessEffects, 1);
  });

  test('mixed offline move correction load and evidenced OOS derive versions only from predecessor ACK', () async {
    await offline();
    final picture = await photo();
    final move = await action('YARD_MOVE', {'toLocationId': dockId});
    final correction = await action('LOCATION_CORRECTION', {
      ...fields('LOCATION_CORRECTION'),
      'newLocationId': yardId,
    });
    final load = await action('LOAD_STATE_CHANGE', fields('LOAD_STATE_CHANGE'));
    final oos = await action('OOS', {
      'siteId': yardSiteId,
      'comment': 'Cracked frame',
      'documentIds': [picture],
    });
    final intents = await f.worker.store.list(user, tenant);
    expect(jsonDecode(intents[1].payloadJson)['expectedTrailerVersion'], {
      'operationId': move,
    });
    expect(jsonDecode(intents[2].payloadJson)['expectedTrailerVersion'], {
      'operationId': correction,
    });
    expect(jsonDecode(intents[3].payloadJson)['expectedTrailerVersion'], {
      'operationId': load,
    });
    for (final c in intents.skip(1)) {
      expect(await YardReplay(f.worker.store).materialize(c), isNull);
    }
    expect(f.posts, isEmpty);
    expect(f.yardState['version'], 12);
    await f.restart();
    await online();
    final physical = f.posts
        .where((p) => p.key != null && !p.path.endsWith('upload-completion'))
        .toList();
    expect(physical.map((p) => p.body['expectedTrailerVersion']), [
      12,
      13,
      14,
      15,
    ]);
    expect(physical.last.key, oos);
    expect(physical.last.body['documentIds'], [
      (await f.media.find(user, tenant, picture))!.serverDocumentId,
    ]);
    expect(
      (await f.worker.store.list(user, tenant)).map((c) => c.state),
      everyElement('ACKNOWLEDGED'),
    );
    expect(f.yardState['version'], 16);
    expect(f.yardState['currentLocation']['id'], yardId);
    expect(f.yardState['loadState'], 'EMPTY');
    expect(f.yardState['serviceabilityState'], 'OUT_OF_SERVICE');
    expect(f.finalizeEffects, 1);
    expect(f.businessEffects, 4);
  });

  test(
    'report is an immutable observation and never a version predecessor',
    () async {
      await offline();
      await action('DISCREPANCY_REPORT', fields('DISCREPANCY_REPORT'));
      final load = await action(
        'LOAD_STATE_CHANGE',
        fields('LOAD_STATE_CHANGE'),
      );
      final intent = (await f.worker.store.find(user, tenant, load))!;
      expect(jsonDecode(intent.payloadJson)['expectedTrailerVersion'], 12);
      expect(jsonDecode(intent.dependenciesJson), isEmpty);
      await online();
      expect(
        (await f.worker.store.list(user, tenant)).map((c) => c.state),
        everyElement('ACKNOWLEDGED'),
      );
      expect(f.yardState['version'], 13);
      expect(f.yardState['currentLocation']['id'], yardId);
    },
  );

  for (final kind in [
    'LOCATION_CORRECTION',
    'LOAD_STATE_CHANGE',
    'DISCREPANCY_REPORT',
  ]) {
    test(
      '$kind lost ACK survives SQLite reopen with identical request and UUID',
      () async {
        final id = await action(kind, fields(kind));
        f.loseBusinessAck = true;
        await f.worker.sync();
        expect(f.businessEffects, 1);
        final before = (await f.worker.store.find(user, tenant, id))!;
        expect(before.state, 'RETRYABLE_FAILURE');
        final frozen = await YardReplay(f.worker.store).frozen(before);
        await f.restart();
        f.now = f.now.add(const Duration(minutes: 2));
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'ACKNOWLEDGED',
        );
        final attempts = f.posts.where((p) => p.key == id).toList();
        expect(attempts, hasLength(2));
        expect(attempts[0].raw, attempts[1].raw);
        expect(attempts[1].raw, frozen!.payloadJson);
        expect(f.businessEffects, 1);
        expect(f.yardState['version'], kind == 'DISCREPANCY_REPORT' ? 12 : 13);
      },
    );
  }

  test('OOS waits for every finalized photo and lost ACK never consumes evidence twice', () async {
    final picture = await photo();
    final id = await action('OOS', {
      'siteId': yardSiteId,
      'comment': 'Frame crack',
      'documentIds': [picture],
    });
    final intent = (await f.worker.store.find(user, tenant, id))!;
    expect(await YardReplay(f.worker.store).materialize(intent), isNull);
    expect(f.businessEffects, 0);
    f.loseBusinessAck = true;
    await f.worker.sync();
    expect(f.businessEffects, 1);
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'RETRYABLE_FAILURE',
    );
    await f.restart();
    f.now = f.now.add(const Duration(minutes: 2));
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'ACKNOWLEDGED',
    );
    expect(
      f.posts.where((p) => p.key == id).map((p) => p.raw).toSet(),
      hasLength(1),
    );
    expect(f.businessEffects, 1);
    expect(f.finalizeEffects, 1);
    expect(f.puts, 1);
    expect(f.yardConsumedDocuments, hasLength(1));
  });

  for (final status in [408, 429, 500, 503]) {
    test(
      'correction HTTP $status retries frozen request without an invented version',
      () async {
        final id = await action(
          'LOCATION_CORRECTION',
          fields('LOCATION_CORRECTION'),
        );
        f.failNext = status;
        if (status == 429) {
          f.retryAfter = '120';
        }
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'RETRYABLE_FAILURE',
        );
        expect(f.businessEffects, 0);
        if (status == 429) {
          await f.worker.sync();
          expect(f.posts.where((p) => p.key == id), hasLength(1));
        }
        f.now = f.now.add(const Duration(minutes: 3));
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'ACKNOWLEDGED',
        );
        expect(
          f.posts.where((p) => p.key == id).map((p) => p.raw).toSet(),
          hasLength(1),
        );
      },
    );
  }

  for (final status in [403, 404, 409]) {
    test(
      'correction HTTP $status retains original and blocks dependent physical changes',
      () async {
        final id = await action(
          'LOCATION_CORRECTION',
          fields('LOCATION_CORRECTION'),
        );
        final child = await action(
          'LOAD_STATE_CHANGE',
          fields('LOAD_STATE_CHANGE'),
        );
        f.failNext = status;
        f.failCode = status == 409 ? 'STALE_VERSION' : 'RESOURCE_NOT_FOUND';
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'CONFLICT',
        );
        expect(
          (await f.worker.store.find(user, tenant, child))!.state,
          'BLOCKED',
        );
        expect(f.posts.where((p) => p.key == child), isEmpty);
        expect(f.yardState['version'], 12);
        await action('DISCREPANCY_REPORT', fields('DISCREPANCY_REPORT'));
        await f.worker.sync();
        expect(f.yardState['version'], 12);
        expect(
          (await f.worker.store.list(user, tenant)).last.state,
          'ACKNOWLEDGED',
        );
      },
    );
  }

  test('only explicit fresh conflict review creates a new root and never retries the old chain', () async {
    final id = await action(
      'LOCATION_CORRECTION',
      fields('LOCATION_CORRECTION'),
    );
    final child = await action(
      'LOAD_STATE_CHANGE',
      fields('LOAD_STATE_CHANGE'),
    );
    f.failNext = 409;
    f.failCode = 'STALE_VERSION';
    await f.worker.sync();
    final old = (await f.worker.store.find(user, tenant, id))!;
    final replacement = await action(
      'LOCATION_CORRECTION',
      fields('LOCATION_CORRECTION'),
      reviewed: true,
    );
    final root = (await f.worker.store.find(user, tenant, replacement))!;
    expect(jsonDecode(root.dependenciesJson), isEmpty);
    expect(jsonDecode(root.payloadJson)['expectedTrailerVersion'], 12);
    expect(await f.db.select(f.db.yardConflictReviews).get(), hasLength(2));
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, replacement))!.state,
      'ACKNOWLEDGED',
    );
    expect(
      (await f.worker.store.find(user, tenant, id))!.payloadJson,
      old.payloadJson,
    );
    expect((await f.worker.store.find(user, tenant, id))!.state, 'CONFLICT');
    expect((await f.worker.store.find(user, tenant, child))!.state, 'BLOCKED');
    expect(f.posts.where((p) => p.key == child), isEmpty);
    expect(f.businessEffects, 1);
    await expectLater(
      f.db.customStatement('DELETE FROM yard_conflict_reviews'),
      throwsA(anything),
    );
  });

  test('pending uncertain correction cannot be silently replaced by a reviewed root', () async {
    await offline();
    await action('LOCATION_CORRECTION', fields('LOCATION_CORRECTION'));
    await expectLater(
      action('LOAD_STATE_CHANGE', fields('LOAD_STATE_CHANGE'), reviewed: true),
      throwsStateError,
    );
    expect(await f.worker.store.list(user, tenant), hasLength(1));
  });

  test(
    'native upload permission loss blocks OOS without losing private bytes',
    () async {
      final picture = await photo();
      final id = await action('OOS', {
        'siteId': yardSiteId,
        'comment': 'Crack',
        'documentIds': [picture],
      });
      f.yardOosDenied = true;
      await f.worker.sync();
      expect(f.businessEffects, 0);
      expect(f.yardState['serviceabilityState'], 'IN_SERVICE');
      expect((await f.media.find(user, tenant, picture))!.fileState, 'PRESENT');
      expect((await f.worker.store.find(user, tenant, id))!.state, 'BLOCKED');
    },
  );
  test('401 refresh retries same correction identity only after fresh session validation', () async {
    final id = await action('LOAD_STATE_CHANGE', fields('LOAD_STATE_CHANGE'));
    f.failNext = 401;
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'ACKNOWLEDGED',
    );
    expect(f.posts.where((p) => p.key == id), hasLength(2));
    expect(
      f.posts.where((p) => p.key == id).map((p) => p.raw).toSet(),
      hasLength(1),
    );
    expect(f.businessEffects, 1);
  });
  test('two workers on independent SQLite connections never duplicate a corrective effect', () async {
    final id = await action('LOAD_STATE_CHANGE', fields('LOAD_STATE_CHANGE'));
    final otherDb = AppDatabase(
      NativeDatabase(File('${f.directory.path}/device.sqlite')),
    );
    final otherWorker = ReplayWorker(
      f.session,
      CommandStore(otherDb, clock: () => f.now),
      MemoryJournal(),
      scheduleTimers: false,
    );
    try {
      await Future.wait([f.worker.sync(), otherWorker.sync()]);
      expect(
        (await f.worker.store.find(user, tenant, id))!.state,
        'ACKNOWLEDGED',
      );
      expect(f.businessEffects, 1);
      expect(f.posts.where((p) => p.key == id), hasLength(1));
      expect(f.yardState['version'], 13);
    } finally {
      await otherWorker.shutdown();
      await otherDb.close();
    }
  });
}
