import 'dart:async';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/media/documents_client.dart';
import 'package:trailersaas_mobile/src/media/media_store.dart';
import 'package:trailersaas_mobile/src/media/media_processor.dart';
import 'package:trailersaas_mobile/src/media/media_dependencies.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';

import 'media_http_fixture.dart';
import 'media_storage_test.dart' show user, tenant, other;
import '../road/online_execution_test.dart' show MemoryJournal;
import '../sync/command_store_test.dart' show intent;

void main() {
  late MediaHttpFixture f;
  setUp(() async {
    f = MediaHttpFixture();
    await f.start(fileDatabase: true);
  });
  tearDown(() => f.close());
  test(
    'explicit capture Trip context cannot be rebound to another Trip',
    () async {
      final id = await f.capture();
      final command = EvidenceCommandIntent(
        EvidenceCommandKind.signedBol,
        OnlineCommand(
          operationUuid(),
          'trip-B',
          EvidenceCommandIntent.path(
            EvidenceCommandKind.signedBol,
            tenant,
            'trip-B',
            'stop-trip-B',
          ),
          {
            'evidenceMode': 'SIGNED_BOL',
            'bolDocumentId': id,
            'occurredAt': DateTime.now()
                .toUtc()
                .subtract(const Duration(minutes: 1))
                .toIso8601String(),
          },
        ),
      );
      await expectLater(
        MediaDependencies(f.worker.store).enqueue(user, tenant, command),
        throwsA(isA<Exception>()),
      );
      expect(await f.worker.store.list(user, tenant), isEmpty);
    },
  );
  test('corrupt media edge on an ordinary command blocks rather than spinning or posting', () async {
    final id = await f.capture();
    await f.worker.sync();
    final command = intent(tenant: tenant);
    await f.worker.store.enqueue(user, tenant, command);
    await f.db
        .into(f.db.commandMediaDependencies)
        .insert(
          CommandMediaDependenciesCompanion.insert(
            clientOperationId: command.command.id,
            userId: user,
            tenantId: tenant,
            mediaId: id,
            fieldPath: 'bolDocumentId',
          ),
        );
    await f.worker.sync();
    final row = (await f.worker.store.find(user, tenant, command.command.id))!;
    expect(row.state, 'BLOCKED');
    expect(row.lastErrorCode, 'INVALID_MEDIA_DEPENDENCY');
    expect(row.attemptCount, 0);
    expect(f.businessEffects, 0);
  });
  test('actual SQLite close/reopen recovers final ACK with same key and no second PUT', () async {
    final id = await f.capture();
    f.loseFinalizeAck = true;
    await f.worker.sync();
    final saved = (await f.media.find(user, tenant, id))!;
    f.now = f.now.add(const Duration(minutes: 2));
    await f.restart();
    await f.worker.sync();
    final done = (await f.media.find(user, tenant, id))!;
    expect(done.state, 'FINALIZED');
    expect(done.serverDocumentId, saved.serverDocumentId);
    expect(done.finalizeKey, saved.finalizeKey);
    expect(f.puts, 1);
    expect(f.finalizeEffects, 1);
  });
  test(
    'two connections to same file cannot recover a genuinely live PUT',
    () async {
      final id = await f.capture();
      final secondDb = AppDatabase(
        NativeDatabase(File('${f.directory.path}/device.sqlite')),
      );
      final secondStore = MediaStore(secondDb, f.files, clock: () => f.now);
      final second = ReplayWorker(
        f.session,
        CommandStore(secondDb, clock: () => f.now),
        MemoryJournal(),
        scheduleTimers: false,
        media: MediaProcessor(
          secondStore,
          DocumentsClient(f.api, clock: () => f.now),
          R2SignedPutTransport(allowTestLoopback: true, clock: () => f.now),
        ),
      );
      f.putEntered = Completer();
      f.putRelease = Completer();
      final first = f.worker.sync();
      await f.putEntered!.future;
      final next = second.sync();
      expect(f.puts, 1);
      expect((await secondStore.find(user, tenant, id))!.state, 'UPLOADING');
      f.putRelease!.complete();
      f.putRelease = null;
      await first;
      await next;
      await second.shutdown();
      await secondDb.close();
      expect(f.puts, 1);
      expect(f.finalizeEffects, 1);
    },
  );
  test('partial persisted media graph cannot release no-receiver even if present edge is finalized', () async {
    final bol = await f.capture(), photo = await f.capture('DELIVERY_PHOTO');
    await f.worker.sync();
    final command = EvidenceCommandIntent(
      EvidenceCommandKind.noReceiver,
      OnlineCommand(
        operationUuid(),
        'trip-A',
        EvidenceCommandIntent.path(
          EvidenceCommandKind.noReceiver,
          tenant,
          'trip-A',
          'stop-trip-A',
        ),
        {
          'evidenceMode': 'NO_RECEIVER_AVAILABLE',
          'bolDocumentId': bol,
          'deliveryPhotoDocumentId': photo,
          'comment': 'Receiver unavailable',
          'occurredAt': DateTime.now()
              .toUtc()
              .subtract(const Duration(minutes: 1))
              .toIso8601String(),
        },
      ),
    );
    await f.worker.store.enqueue(user, tenant, command);
    await f.db
        .into(f.db.commandMediaDependencies)
        .insert(
          CommandMediaDependenciesCompanion.insert(
            clientOperationId: command.command.id,
            userId: user,
            tenantId: tenant,
            mediaId: bol,
            fieldPath: 'bolDocumentId',
          ),
        );
    final row = (await f.worker.store.find(user, tenant, command.command.id))!;
    final deps = MediaDependencies(f.worker.store);
    expect(await deps.ready(row), isFalse);
    expect(await deps.materialize(row), isNull);
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, command.command.id))!.state,
      'BLOCKED',
    );
    expect(f.businessEffects, 0);
  });
  test('uncertain identity cannot rotate through SQL or newAttempt without missing-upload proof', () async {
    final id = await f.capture();
    final row = (await f.media.find(user, tenant, id))!;
    await expectLater(
      f.db.customStatement('UPDATE local_media SET finalize_key=? WHERE id=?', [
        operationUuid(),
        id,
      ]),
      throwsA(isA<Exception>()),
    );
    await expectLater(f.media.newAttempt(row), throwsA(isA<Exception>()));
    expect(
      (await f.media.find(user, tenant, id))!.finalizeKey,
      row.finalizeKey,
    );
  });
  test('materialized endpoint cannot differ from immutable intent even through SQL', () async {
    final bol = await f.capture();
    final command = EvidenceCommandIntent(
      EvidenceCommandKind.signedBol,
      OnlineCommand(
        operationUuid(),
        'trip-A',
        EvidenceCommandIntent.path(
          EvidenceCommandKind.signedBol,
          tenant,
          'trip-A',
          'stop-trip-A',
        ),
        {
          'evidenceMode': 'SIGNED_BOL',
          'bolDocumentId': bol,
          'occurredAt': DateTime.now()
              .toUtc()
              .subtract(const Duration(minutes: 1))
              .toIso8601String(),
        },
      ),
    );
    await MediaDependencies(f.worker.store).enqueue(user, tenant, command);
    await expectLater(
      f.db
          .into(f.db.materializedRequests)
          .insert(
            MaterializedRequestsCompanion.insert(
              clientOperationId: command.command.id,
              userId: user,
              tenantId: tenant,
              endpointPath: 'https://evil.test',
              httpMethod: 'POST',
              payloadJson: '{}',
              createdAt: f.now,
            ),
          ),
      throwsA(isA<Exception>()),
    );
  });
  test(
    'media identity cannot be duplicated across a second finalized record',
    () async {
      final first = await f.capture(), second = await f.capture();
      await f.worker.sync();
      final row = (await f.media.find(user, tenant, first))!;
      await expectLater(
        f.db.customStatement(
          'UPDATE local_media SET server_document_id=? WHERE id=?',
          [row.serverDocumentId, second],
        ),
        throwsA(isA<Exception>()),
      );
    },
  );
  test('changed local bytes fail visibly and preserve file reference instead of uploading', () async {
    final id = await f.capture();
    await File('${f.directory.path}/private-media/$user/$tenant/$id.bin')
        .writeAsBytes([9]);
    await f.worker.sync();
    final row = (await f.media.find(user, tenant, id))!;
    expect(row.state, 'FAILED');
    expect(row.lastErrorCode, 'FILE_CHANGED');
    expect(f.puts, 0);
    expect(f.posts, isEmpty);
    expect(await f.files.inventory(user, tenant), ['$id.bin']);
  });
  test(
    'mismatched checksum after actual PUT cannot become FINALIZED',
    () async {
      final id = await f.capture();
      f.finalizeEntered = Completer();
      f.finalizeRelease = Completer();
      final run = f.worker.sync();
      await f.finalizeEntered!.future;
      f.objects.updateAll((key, bytes) => [9]);
      f.finalizeRelease!.complete();
      f.finalizeRelease = null;
      await run;
      final row = (await f.media.find(user, tenant, id))!;
      expect(row.state, 'FAILED');
      expect(row.lastErrorCode, 'UPLOAD_CONTENT_MISMATCH');
      expect(row.serverDocumentId, isNotNull);
      expect(f.finalizeEffects, 0);
    },
  );
  test(
    'same finalized file is pinned by default and cannot claim or resubmit',
    () async {
      final id = await f.capture();
      await f.worker.sync();
      f.now = f.now.add(const Duration(days: 30));
      final row = (await f.media.find(user, tenant, id))!;
      expect(await f.media.claim(row), isFalse);
      expect(await f.media.cleanup(user, tenant), 0);
      await f.worker.sync();
      expect(f.puts, 1);
      expect(f.finalizeEffects, 1);
      expect(await f.files.inventory(user, tenant), ['$id.bin']);
    },
  );
  test('a different Tenant cannot release offline use or remove another namespace file', () async {
    final id = await f.capture();
    await f.worker.sync();
    await f.media.releaseOfflineUse(user, other, id);
    f.now = f.now.add(const Duration(days: 30));
    expect((await f.media.find(user, tenant, id))!.retainOffline, isTrue);
    expect(await f.media.cleanup(user, other), 0);
    expect(await f.files.inventory(user, tenant), ['$id.bin']);
  });
}
