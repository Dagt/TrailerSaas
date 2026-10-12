import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_store.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_replay.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant, other;
import '../shift/camera_capture_test.dart' show ControlledCamera;

void main() {
  late MediaHttpFixture f;
  late RoadEvidenceStore store;
  late ControlledCamera camera;
  setUp(() async {
    f = MediaHttpFixture()..roadWorkflows = true;
    await f.start(fileDatabase: true);
    camera = ControlledCamera();
    final file = File('${f.directory.path}/road-camera.jpg');
    await file.writeAsBytes([255, 216, 255, 4, 5], flush: true);
    camera.file = XFile(file.path);
    store = RoadEvidenceStore(
      f.worker.futureEvidence! as RoadEvidenceReplay,
      camera: camera,
    );
  });
  tearDown(() => f.close());
  Future<String> prepare(String purpose) async {
    var draft = await store.create(
      purpose,
      MediaHttpFixture.roadTrip,
      stop: purpose == 'FUEL' ? null : MediaHttpFixture.roadStop,
    );
    if (purpose == 'SIGNED_BOL' || purpose == 'NO_RECEIVER_AVAILABLE') {
      await store.take(draft, 'bol');
    }
    if (purpose == 'NO_RECEIVER_AVAILABLE') {
      await store.take(draft, 'deliveryPhoto');
      await store.save(draft, {'comment': 'No había receptor'});
    }
    if (purpose == 'FUEL') {
      await store.take(draft, 'receipt');
      await store.save(draft, {'odometer': 123});
    }
    if (purpose == 'PROBLEM') {
      await store.take(draft, 'damage0');
      await store.save(draft, {
        'category': 'EQUIPMENT_DAMAGE',
        'comment': 'Puerta dañada',
      });
    }
    draft = (await store.find(user, tenant, draft.id))!;
    return store.queue(draft);
  }

  for (final purpose in [
    'SIGNED_BOL',
    'NO_RECEIVER_AVAILABLE',
    'FUEL',
    'PROBLEM',
  ]) {
    test(
      '$purpose offline camera files wait for finalization and real typed server confirmation',
      () async {
        f.network.status = ConnectivityStatus.unavailable;
        await f.session.refresh();
        expect(f.session.state, SessionState.offline);
        final id = await prepare(purpose);
        await f.worker.sync();
        expect(f.businessEffects, 0);
        expect((await f.worker.store.find(user, tenant, id))!.state, 'QUEUED');
        f.network.status = ConnectivityStatus.available;
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'ACKNOWLEDGED',
        );
        expect(f.businessEffects, 1);
        final expected = purpose == 'NO_RECEIVER_AVAILABLE' ? 2 : 1;
        expect(f.puts, expected);
        expect(f.finalizeEffects, expected);
        final request = f.posts.lastWhere((p) => p.key == id);
        expect(request.key, id);
        expect(request.body.toString(), isNot(contains('localMedia')));
        for (final media in await f.media.list(user, tenant)) {
          expect(media.state, 'FINALIZED');
          expect(f.roadLinks[media.serverDocumentId], isNotEmpty);
        }
      },
    );
  }
  test('lost business ACK restarts with the same frozen request and no second PUT or effect', () async {
    final id = await prepare('SIGNED_BOL');
    f.loseBusinessAck = true;
    await f.worker.sync();
    final first = f.posts.lastWhere((p) => p.key == id);
    expect(f.businessEffects, 1);
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'RETRYABLE_FAILURE',
    );
    final puts = f.puts;
    await f.restart();
    f.now = f.now.add(const Duration(minutes: 10));
    await f.worker.sync();
    final last = f.posts.lastWhere((p) => p.key == id);
    expect(last.raw, first.raw);
    expect(last.path, first.path);
    expect(last.key, first.key);
    expect(f.puts, puts);
    expect(f.businessEffects, 1);
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'ACKNOWLEDGED',
    );
  });
  for (final status in [408, 429, 500, 503]) {
    test(
      'business HTTP $status durably retains exact request and honors retry eligibility',
      () async {
        final id = await prepare('FUEL');
        f.roadFailureStatuses.add(status);
        f.retryAfter = '120';
        await f.worker.sync();
        final first = f.posts.lastWhere((p) => p.key == id);
        final pending = (await f.worker.store.find(user, tenant, id))!;
        expect(pending.state, 'RETRYABLE_FAILURE');
        if (status == 429) {
          expect(
            pending.nextRetryAt!.isBefore(
              f.now.add(const Duration(seconds: 120)),
            ),
            isFalse,
          );
        }
        await f.worker.sync();
        expect(f.posts.where((p) => p.key == id), hasLength(1));
        f.now = f.now.add(const Duration(minutes: 10));
        await f.worker.sync();
        expect(f.posts.lastWhere((p) => p.key == id).raw, first.raw);
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'ACKNOWLEDGED',
        );
        expect(f.businessEffects, 1);
      },
    );
  }
  test(
    '401 refreshes Firebase once with identical business identity',
    () async {
      final id = await prepare('FUEL');
      f.roadFailureStatuses.add(401);
      await f.worker.sync();
      expect(f.auth.forceRefreshes.where((v) => v), hasLength(1));
      expect(
        f.posts.where((p) => p.key == id).map((p) => p.raw).toSet(),
        hasLength(1),
      );
      expect(
        (await f.worker.store.find(user, tenant, id))!.state,
        'ACKNOWLEDGED',
      );
    },
  );
  for (final status in [403, 404, 409]) {
    test(
      'HTTP $status keeps intended operation and files for review without false success',
      () async {
        final id = await prepare('PROBLEM');
        f.roadFailureStatuses.add(status);
        f.failCode = status == 409 ? 'RESOURCE_CONFLICT' : 'RESOURCE_NOT_FOUND';
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, id))!.state,
          'CONFLICT',
        );
        expect(f.businessEffects, 0);
        for (final row in await f.media.list(user, tenant)) {
          await f.media.verify(row);
          expect(row.state, 'FINALIZED');
        }
      },
    );
  }
  test(
    'malformed success never acknowledges and replays frozen business result',
    () async {
      final id = await prepare('FUEL');
      f.roadResponseOverride = {'id': 'fake'};
      await f.worker.sync();
      expect(
        (await f.worker.store.find(user, tenant, id))!.state,
        'RETRYABLE_FAILURE',
      );
      expect(f.businessEffects, 1);
      f.now = f.now.add(const Duration(minutes: 10));
      await f.worker.sync();
      expect(
        (await f.worker.store.find(user, tenant, id))!.state,
        'ACKNOWLEDGED',
      );
      expect(f.businessEffects, 1);
    },
  );
  test('missing second No Receiver photo and whitespace comment cannot release a command', () async {
    var draft = await store.create(
      'NO_RECEIVER_AVAILABLE',
      MediaHttpFixture.roadTrip,
      stop: MediaHttpFixture.roadStop,
    );
    await store.take(draft, 'bol');
    await store.save(draft, {'comment': '\u00a0\u2003'});
    draft = (await store.find(user, tenant, draft.id))!;
    await expectLater(store.queue(draft), throwsA(isA<FormatException>()));
    expect(await f.worker.store.list(user, tenant), isEmpty);
    expect((await store.find(user, tenant, draft.id))!.commandId, isNull);
  });
  test('nondamage problem has no invented photo requirement', () async {
    var draft = await store.create('PROBLEM', MediaHttpFixture.roadTrip);
    await store.save(draft, {
      'category': 'DELAY_TRAFFIC',
      'comment': 'Tráfico detenido',
    });
    draft = (await store.find(user, tenant, draft.id))!;
    final id = await store.queue(draft);
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, id))!.state,
      'ACKNOWLEDGED',
    );
    expect(f.puts, 0);
  });
  test(
    'changed file fails visibly and retains the immutable evidence command',
    () async {
      final id = await prepare('SIGNED_BOL');
      final row = (await f.media.list(user, tenant)).single;
      final path = File(
        '${f.directory.path}/private-media/$user/$tenant/${row.id}.bin',
      );
      await path.writeAsBytes([9], flush: true);
      await f.worker.sync();
      expect((await f.media.find(user, tenant, row.id))!.state, 'FAILED');
      expect((await f.worker.store.find(user, tenant, id))!.state, 'BLOCKED');
      expect(f.businessEffects, 0);
    },
  );
  test('account switch during native capture preserves bytes only for original owner', () async {
    final draft = await store.create(
      'SIGNED_BOL',
      MediaHttpFixture.roadTrip,
      stop: MediaHttpFixture.roadStop,
    );
    camera.pending = Completer<XFile?>();
    final pending = store.take(draft, 'bol');
    await camera.started.future;
    f.switchUser = true;
    await f.session.refresh();
    camera.pending!.complete(camera.file);
    await pending;
    expect(await store.find(other, tenant, draft.id), isNull);
    expect(await f.media.list(other, tenant), isEmpty);
    expect((await f.media.list(user, tenant)), hasLength(1));
    await expectLater(
      store.queue((await store.find(user, tenant, draft.id))!),
      throwsA(isA<FormatException>()),
    );
  });
  test(
    'restart repairs a complete private capture after SQLite association fails',
    () async {
      final draft = await store.create(
        'SIGNED_BOL',
        MediaHttpFixture.roadTrip,
        stop: MediaHttpFixture.roadStop,
      );
      await f.db.customStatement(
        "CREATE TRIGGER fail_road_draft BEFORE UPDATE ON road_drafts BEGIN SELECT RAISE(ABORT,'test-only');END",
      );
      await expectLater(store.take(draft, 'bol'), throwsA(isA<Exception>()));
      await f.db.customStatement('DROP TRIGGER fail_road_draft');
      await f.restart();
      store = RoadEvidenceStore(
        f.worker.futureEvidence! as RoadEvidenceReplay,
        camera: camera,
      );
      await store.recover();
      expect(
        (jsonDecode(
          (await store.find(user, tenant, draft.id))!.mediaJson,
        ) as Map).keys,
        contains('bol'),
      );
    },
  );
}
