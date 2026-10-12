import 'dart:async';
import 'dart:io';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:trailersaas_mobile/src/shift/camera_capture.dart';
import 'package:trailersaas_mobile/src/shift/inspection_store.dart';
import 'package:trailersaas_mobile/src/shift/shift_commands.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant, other;
import 'shift_protocol_test.dart' show tractor;

class ControlledCamera implements InspectionCamera {
  Completer<XFile?>? pending;
  XFile? file;
  int recoveries = 0;
  final started = Completer<void>();
  @override
  Future<XFile?> takePhoto() {
    if (!started.isCompleted) started.complete();
    return pending?.future ?? Future.value(file);
  }

  @override
  Future<List<XFile>> recoverPhotos() async {
    recoveries++;
    return file == null ? [] : [file!];
  }
}

void main() {
  late MediaHttpFixture f;
  late InspectionStore store;
  late ControlledCamera camera;
  late InspectionCapture capture;
  setUp(() async {
    f = MediaHttpFixture()..shiftWorkflows = true;
    await f.start(fileDatabase: true);
    store = InspectionStore(f.worker.shifts!);
    await store.onDuty();
    await f.worker.sync();
    camera = ControlledCamera();
    final file = File('${f.directory.path}/camera.jpg');
    await file.writeAsBytes([255, 216, 255, 4, 5], flush: true);
    camera.file = XFile(file.path);
    capture = InspectionCapture(store, f.media, camera);
  });
  tearDown(() => f.close());
  Future<dynamic> draft() => store.create(
    'PRE_TRIP',
    ShiftReference.server(f.serverShift!['id'] as String),
    tractor,
  );
  test('four real camera files persist private photos and draft across restart without confirming inspection', () async {
    final d = await draft();
    for (final position in InspectionCapture.positions.keys) {
      await capture.take((await store.find(user, tenant, d.id))!, position);
    }
    final saved = (await store.find(user, tenant, d.id))!;
    final images = jsonDecode(saved.photosJson) as Map;
    expect(images.keys, containsAll(InspectionCapture.positions.keys));
    expect(await f.media.list(user, tenant), hasLength(4));
    expect(f.serverInspections, isEmpty);
    await f.restart();
    store = InspectionStore(f.worker.shifts!);
    expect(
      (await store.find(user, tenant, d.id))!.photosJson,
      saved.photosJson,
    );
    for (final row in await f.media.list(user, tenant)) {
      await f.media.verify(row);
      expect(row.state, 'LOCAL_ONLY');
    }
  });
  test('recovery never treats a legitimate native request as abandoned or permits queueing during capture', () async {
    final d = await draft();
    camera.pending = Completer<XFile?>();
    final sending = capture.take(d, 'front');
    await f.db.customSelect('SELECT 1').get();
    // Wait for the native call boundary without timers.
    await camera.started.future;
    await InspectionCapture(store, f.media, camera).recover();
    expect(camera.recoveries, 0);
    await store.save(d, odometer: '10', checklist: {'items': []});
    await expectLater(store.enqueue(d), throwsA(isA<FormatException>()));
    camera.pending!.complete(camera.file);
    await sending;
    expect(
      jsonDecode((await store.find(user, tenant, d.id))!.photosJson),
      contains('front'),
    );
  });
  test(
    'cancelled camera leaves draft editable and no fabricated photo',
    () async {
      final d = await draft();
      camera.file = null;
      await capture.take(d, 'front');
      expect(await f.media.list(user, tenant), isEmpty);
      expect(
        jsonDecode((await store.find(user, tenant, d.id))!.photosJson),
        isEmpty,
      );
      expect(
        (await f.db
                .customSelect('SELECT state FROM camera_captures')
                .getSingle())
            .read<String>('state'),
        'CANCELLED',
      );
    },
  );
  test('failed SQLite association repairs complete private bytes without recapture', () async {
    final d = await draft();
    await f.db.customStatement(
      "CREATE TRIGGER fail_photo BEFORE UPDATE OF photos_json ON inspection_drafts BEGIN SELECT RAISE(ABORT,'test-only');END",
    );
    await expectLater(capture.take(d, 'rear'), throwsA(isA<Exception>()));
    expect(await f.media.list(user, tenant), hasLength(1));
    await f.db.customStatement('DROP TRIGGER fail_photo');
    await capture.recover();
    expect(
      jsonDecode((await store.find(user, tenant, d.id))!.photosJson),
      contains('rear'),
    );
    expect(camera.recoveries, 0);
  });
  test('logout during capture preserves original namespace and never exposes files to next identity', () async {
    final d = await draft();
    camera.pending = Completer<XFile?>();
    final sending = capture.take(d, 'driverSide');
    await camera.started.future;
    await f.session.signOut();
    camera.pending!.complete(camera.file);
    await sending;
    expect(await f.media.list(user, tenant), hasLength(1));
    expect(await f.media.list(other, tenant), isEmpty);
    expect(await f.media.list(user, other), isEmpty);
    expect(f.serverInspections, isEmpty);
    await expectLater(store.enqueue(d), throwsA(isA<FormatException>()));
  });
  test('unreadable or non-photo bytes stay failed and cannot satisfy four-position evidence', () async {
    final d = await draft();
    final file = File('${f.directory.path}/not-image');
    await file.writeAsString('not an image');
    camera.file = XFile(file.path);
    await expectLater(
      capture.take(d, 'passengerSide'),
      throwsA(isA<FormatException>()),
    );
    expect(
      jsonDecode((await store.find(user, tenant, d.id))!.photosJson),
      isEmpty,
    );
    expect(await f.media.list(user, tenant), isEmpty);
  });
  test('no assigned Tractor blocks new inspection while existing offline draft remains after reassignment', () async {
    final d = await draft();
    await capture.take(d, 'front');
    f.reassigned = true;
    await f.session.refresh();
    await expectLater(draft(), throwsA(isA<FormatException>()));
    expect(await store.find(user, tenant, d.id), isNotNull);
    expect(await f.media.list(user, tenant), hasLength(1));
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    expect(await store.find(user, tenant, d.id), isNotNull);
  });
}
