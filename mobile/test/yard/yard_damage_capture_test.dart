import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/yard/yard_contracts.dart';
import 'package:trailersaas_mobile/src/yard/yard_damage_store.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant, other;
import '../shift/camera_capture_test.dart' show ControlledCamera;
import 'fixtures.dart';

void main() {
  late MediaHttpFixture f;
  late ControlledCamera camera;
  late YardDamageStore damage;
  late File cameraFile;
  setUp(() async {
    f = MediaHttpFixture()..yardWorkflows = true;
    await f.start(fileDatabase: true);
    camera = ControlledCamera();
    cameraFile = File('${f.directory.path}/damage.jpg');
    await cameraFile.writeAsBytes([255, 216, 255, 4, 5], flush: true);
    camera.file = XFile(cameraFile.path);
    damage = YardDamageStore(f.session, f.worker, camera);
  });
  tearDown(() => f.close());
  Future<dynamic> draft() =>
      damage.begin(YardTrailer.fromJson(f.yardState), yardSiteId, () => true);

  test('real camera bytes persist in own Trailer namespace without Driver Trip or OOS transition', () async {
    final d = await draft();
    await damage.take(d, () => true);
    final saved = (await damage.find(user, tenant, d.id))!;
    final ids = List<String>.from(jsonDecode(saved.photosJson) as List);
    expect(ids, hasLength(1));
    final photo = (await f.media.find(user, tenant, ids.single))!;
    expect(photo.trailerId, trailerId);
    expect(photo.tripId, isNull);
    expect(photo.documentType, 'DAMAGE_PHOTO');
    expect(photo.state, 'LOCAL_ONLY');
    expect(photo.sizeBytes, 5);
    await f.media.verify(photo);
    expect(await f.worker.store.list(user, tenant), isEmpty);
    expect(f.businessEffects, 0);
    expect(f.yardState['serviceabilityState'], 'IN_SERVICE');
    await f.restart();
    damage = YardDamageStore(f.session, f.worker, camera);
    expect(
      (await damage.find(user, tenant, d.id))!.photosJson,
      saved.photosJson,
    );
    expect((await draft()).id, d.id);
  });

  test('upload completion is distinct from conscious OOS confirmation and duplicate confirm keeps one UUID', () async {
    final d = await draft();
    await damage.take(d, () => true);
    await damage.comment(d, 'Frame visibly cracked');
    await f.worker.sync();
    expect(f.finalizeEffects, 1);
    expect(f.businessEffects, 0);
    expect(f.yardState['serviceabilityState'], 'IN_SERVICE');
    final first = await damage.queue(
      d,
      YardTrailer.fromJson(f.yardState),
      () => true,
    );
    final second = await damage.queue(
      d,
      YardTrailer.fromJson(f.yardState),
      () => true,
    );
    expect(first, d.id);
    expect(second, first);
    expect(await f.worker.store.list(user, tenant), hasLength(1));
    await f.worker.sync();
    expect(f.businessEffects, 1);
    expect(f.yardState['serviceabilityState'], 'OUT_OF_SERVICE');
  });

  test(
    'camera cancellation retains editable draft without fabricating media',
    () async {
      final d = await draft();
      camera.file = null;
      await damage.take(d, () => true);
      expect((await damage.find(user, tenant, d.id))!.photosJson, '[]');
      expect(await f.media.list(user, tenant), isEmpty);
      expect(
        (await f.db.select(f.db.yardCaptures).getSingle()).state,
        'CANCELLED',
      );
    },
  );

  test('invalid image retains failed capture and never accepts arbitrary bytes as damage evidence', () async {
    final d = await draft();
    await cameraFile.writeAsBytes([1, 2, 3, 4]);
    await expectLater(
      damage.take(d, () => true),
      throwsA(isA<FormatException>()),
    );
    expect((await f.db.select(f.db.yardCaptures).getSingle()).state, 'FAILED');
    expect(await f.media.list(user, tenant), isEmpty);
  });

  test(
    'unfinished native acquisition cannot queue OOS or launch a second camera',
    () async {
      final d = await draft();
      await damage.comment(d, 'Crack');
      camera.pending = Completer<XFile?>();
      final taking = damage.take(d, () => true);
      await camera.started.future;
      await expectLater(damage.take(d, () => true), throwsStateError);
      await expectLater(
        damage.queue(d, YardTrailer.fromJson(f.yardState), () => true),
        throwsStateError,
      );
      await damage.recover();
      expect(camera.recoveries, 0);
      camera.pending!.complete(camera.file);
      await taking;
      expect(await f.db.select(f.db.yardCaptures).get(), hasLength(1));
      expect(await f.worker.store.list(user, tenant), isEmpty);
    },
  );

  test('late camera callback after account switch stays under original owner and cannot queue for new user', () async {
    final d = await draft();
    camera.pending = Completer<XFile?>();
    final taking = damage.take(d, () => true);
    await camera.started.future;
    f.switchUser = true;
    f.auth.uid = 'firebase-B';
    await f.session.refresh();
    camera.pending!.complete(camera.file);
    await taking;
    expect(await f.media.list(user, tenant), hasLength(1));
    expect(await f.media.list(other, tenant), isEmpty);
    expect(
      await damage.queue(d, YardTrailer.fromJson(f.yardState), () => true),
      isNull,
    );
    expect(f.businessEffects, 0);
  });

  test('revoked OOS capability cannot capture or edit draft even when Yard capability remains', () async {
    final d = await draft();
    f.yardOosDenied = true;
    await f.session.refresh();
    await expectLater(damage.take(d, () => true), throwsStateError);
    await expectLater(damage.comment(d, 'Crack'), throwsStateError);
    expect(
      await damage.queue(d, YardTrailer.fromJson(f.yardState), () => true),
      isNull,
    );
    expect(await f.media.list(user, tenant), isEmpty);
  });

  test(
    'closed Road Mode or stale owner callback refuses new native acquisition',
    () async {
      final d = await draft();
      await expectLater(damage.take(d, () => false), throwsStateError);
      expect(
        await damage.queue(d, YardTrailer.fromJson(f.yardState), () => false),
        isNull,
      );
      expect(await f.db.select(f.db.yardCaptures).get(), isEmpty);
    },
  );

  test('stale version requires new reviewed draft and explicitly retained photo identity', () async {
    final d = await draft();
    await damage.take(d, () => true);
    await damage.comment(d, 'Crack');
    final old = (await damage.find(user, tenant, d.id))!;
    final picture = (jsonDecode(old.photosJson) as List).single as String;
    f.yardState = {...f.yardState, 'version': 14};
    await expectLater(
      damage.queue(d, YardTrailer.fromJson(f.yardState), () => true),
      throwsStateError,
    );
    final replacement = await damage.begin(
      YardTrailer.fromJson(f.yardState),
      yardSiteId,
      () => true,
      fresh: true,
    );
    await damage.retainPhoto(replacement, picture);
    expect(
      (await damage.find(user, tenant, replacement.id))!.photosJson,
      old.photosJson,
    );
    expect((await damage.find(user, tenant, d.id))!.commandId, isNull);
    expect(await f.media.list(user, tenant), hasLength(1));
  });

  test('missing private photo blocks confirmation without deleting draft or historical media identity', () async {
    final d = await draft();
    await damage.take(d, () => true);
    final saved = (await damage.find(user, tenant, d.id))!;
    final picture = (jsonDecode(saved.photosJson) as List).single as String;
    final inventory = await f.files.inventory(user, tenant);
    expect(inventory, hasLength(1));
    await cameraFile.delete();
    final bin = await f.directory
        .list(recursive: true)
        .where((e) => e is File && e.path.endsWith('$picture.bin'))
        .first;
    await bin.delete();
    final fresh = await damage.begin(
      YardTrailer.fromJson(f.yardState),
      yardSiteId,
      () => true,
      fresh: true,
    );
    await expectLater(damage.retainPhoto(fresh, picture), throwsA(anything));
    expect(await damage.find(user, tenant, d.id), isNotNull);
    expect(await f.worker.store.list(user, tenant), isEmpty);
  });
  test(
    'process death recovery consumes only persisted own Yard capture context',
    () async {
      final d = await draft(), captureId = operationUuid();
      await f.db
          .into(f.db.yardCaptures)
          .insert(
            YardCapturesCompanion.insert(
              id: captureId,
              userId: user,
              tenantId: tenant,
              draftId: d.id,
              createdAt: f.now,
            ),
          );
      await f.restart();
      damage = YardDamageStore(f.session, f.worker, camera);
      await damage.recover();
      expect(camera.recoveries, 1);
      expect(
        (await damage.find(user, tenant, d.id))!.photosJson,
        jsonEncode([captureId]),
      );
      expect(
        (await f.media.find(user, tenant, captureId))!.trailerId,
        trailerId,
      );
      expect(
        (await f.db.select(f.db.yardCaptures).getSingle()).state,
        'CAPTURED',
      );
      expect(await f.worker.store.list(user, tenant), isEmpty);
    },
  );
}
