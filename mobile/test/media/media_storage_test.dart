import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/media/media_file_store.dart';
import 'package:trailersaas_mobile/src/media/media_store.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';

import '../sync/command_store_test.dart' show intent;
import '../shift/legacy_database.dart';

const user = '11111111-1111-4111-8111-111111111111';
const tenant = '22222222-2222-4222-8222-222222222222';
const other = '33333333-3333-4333-8333-333333333333';

class Version3 extends AppDatabase {
  Version3(super.executor);
  @override
  int get schemaVersion => 3;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) async {
      await m.createTable(localSessionContexts);
      await m.createTable(cachedTrips);
      await m.createTable(cachedStops);
      await m.createTable(cachedTrailers);
      await customStatement(legacyQueueSql);
      await m.createTable(localSyncIssues);
      await customStatement(
        'CREATE INDEX local_commands_scope_state ON local_commands(user_id,tenant_id,state,created_at)',
      );
      await customStatement(
        'CREATE INDEX local_sync_issues_scope ON local_sync_issues(user_id,tenant_id,created_at)',
      );
      await customStatement('''CREATE TRIGGER local_command_intent_immutable BEFORE UPDATE OF client_operation_id,user_id,tenant_id,command_type,
        endpoint_path,http_method,payload_json,trip_id,aggregate_id,resource_keys_json,dependencies_json,occurred_at,created_at,queue_sequence ON local_commands
        BEGIN SELECT RAISE(ABORT, 'Command intent is immutable'); END''');
    },
  );
}

void main() {
  late Directory dir;
  late PrivateMediaFileStore files;
  late AppDatabase db;
  late MediaStore store;
  late DateTime now;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('private-media-');
    files = PrivateMediaFileStore(() => Future.value(dir));
    db = AppDatabase(NativeDatabase.memory());
    now = DateTime.utc(2026, 10, 12);
    store = MediaStore(db, files, clock: () => now);
  });
  tearDown(() async {
    await db.close();
    await dir.delete(recursive: true);
  });
  Future<String> capture({
    String type = 'BOL',
    String mime = 'image/jpeg',
  }) async {
    final id = operationUuid();
    final f = await files.capture(
      user,
      tenant,
      id,
      Stream.fromIterable([
        [1, 2],
        [3, 4],
      ]),
      1024,
    );
    await store.register(user, tenant, id, type, mime, f);
    return id;
  }

  test('private capture hashes actual streamed bytes and immutable UUID references', () async {
    final id = await capture();
    final row = (await store.list(user, tenant)).single;
    expect(row.checksumSha256, sha256.convert([1, 2, 3, 4]).toString());
    expect(row.sizeBytes, 4);
    expect(row.state, 'LOCAL_ONLY');
    expect(await files.inventory(user, tenant), ['$id.bin']);
    expect(await files.inventory(other, tenant), isEmpty);
    expect(await store.list(other, tenant), isEmpty);
    expect(await store.list(user, other), isEmpty);
  });
  test(
    'SQLite immutable content rejects overwrite without losing original bytes',
    () async {
      final id = await capture();
      await expectLater(
        db.customStatement(
          "UPDATE local_media SET content_type='image/png' WHERE id=?",
          [id],
        ),
        throwsA(isA<Exception>()),
      );
      await expectLater(
        files.capture(user, tenant, id, Stream.value([9]), 100),
        throwsA(isA<MediaFileProblem>()),
      );
      await store.verify((await store.list(user, tenant)).single);
    },
  );
  for (final field in ['state', 'document_type', 'content_type']) {
    test('SQLite closed catalog rejects invalid $field', () async {
      final id = await capture();
      await expectLater(
        db.customStatement(
          "UPDATE local_media SET $field='INVALID' WHERE id=?",
          [id],
        ),
        throwsA(isA<Exception>()),
      );
    });
  }
  test(
    'empty and interrupted writes remain inspectable but never registered',
    () async {
      final id = operationUuid();
      await expectLater(
        files.capture(user, tenant, id, const Stream.empty(), 1024),
        throwsA(isA<MediaFileProblem>()),
      );
      final interrupted = operationUuid();
      await expectLater(
        files.capture(
          user,
          tenant,
          interrupted,
          Stream.error(const MediaFileProblem('CAPTURE_INTERRUPTED')),
          1024,
        ),
        throwsA(isA<MediaFileProblem>()),
      );
      expect(
        await files.inventory(user, tenant),
        containsAll(['$id.bin.part', '$interrupted.bin.part']),
      );
      expect(await store.list(user, tenant), isEmpty);
    },
  );
  test(
    'oversized stream stops before publishing and keeps private partial file',
    () async {
      final id = operationUuid();
      await expectLater(
        files.capture(user, tenant, id, Stream.value([1, 2, 3]), 2),
        throwsA(isA<MediaFileProblem>()),
      );
      expect(await files.inventory(user, tenant), ['$id.bin.part']);
    },
  );
  test('arbitrary keys namespaces traversal and symlinks are denied', () async {
    for (final key in ['../secret', '/tmp/file', 'https://host/file', 'bad']) {
      await expectLater(
        files.open(user, tenant, key),
        throwsA(isA<MediaFileProblem>()),
      );
    }
    await expectLater(
      files.inventory('../other', tenant),
      throwsA(isA<MediaFileProblem>()),
    );
  });
  test(
    'external removal or modification cannot be accepted as original evidence',
    () async {
      final id = await capture();
      final row = (await store.list(user, tenant)).single;
      await File('${dir.path}/private-media/$user/$tenant/$id.bin')
          .writeAsBytes([9]);
      await expectLater(store.verify(row), throwsA(isA<MediaFileProblem>()));
      await files.remove(user, tenant, id);
      await expectLater(store.verify(row), throwsA(isA<MediaFileProblem>()));
      expect(await store.find(user, tenant, id), isNotNull);
    },
  );
  test('SQLite insert failure retains file for explicit own namespace orphan recovery', () async {
    final id = operationUuid(),
        f = await files.capture(
          user,
          tenant,
          operationUuid(),
          Stream.value([1]),
          1024,
        );
    await expectLater(
      store.register(user, tenant, id, 'BOL', 'image/jpeg', f),
      throwsA(isA<MediaFileProblem>()),
    );
    expect(await files.inventory(user, tenant), ['${f.key}.bin']);
    await store.register(user, tenant, f.key, 'BOL', 'image/jpeg', f);
    expect((await store.list(user, tenant)).single.fileKey, f.key);
  });
  test(
    'v3 file migration retains commands issues cache and file after reopen',
    () async {
      final file = File('${dir.path}/v3.sqlite');
      final v3 = Version3(NativeDatabase(file));
      final c = intent(tenant: tenant);
      await seedLegacyCommand(v3, user, tenant, c, now);
      await v3
          .into(v3.localSyncIssues)
          .insert(
            LocalSyncIssuesCompanion.insert(
              id: c.command.id,
              clientOperationId: c.command.id,
              userId: user,
              tenantId: tenant,
              category: 'STATE',
              errorCode: 'RESOURCE_CONFLICT',
              relatedResource: c.command.tripId!,
              message: 'Review state',
              createdAt: now,
            ),
          );
      await v3
          .into(v3.cachedTrips)
          .insert(
            CachedTripsCompanion.insert(
              userId: user,
              tenantId: tenant,
              id: 'trip-A',
              serverVersion: 1,
              status: 'ASSIGNED',
              projectionJson: '{}',
              refreshedAt: now,
            ),
          );
      final before =
          (await v3.customSelect('SELECT * FROM local_commands').getSingle())
              .data;
      await v3.close();
      final v4 = AppDatabase(NativeDatabase(file));
      await v4.initialize();
      expect(
        (await v4.customSelect('PRAGMA user_version').getSingle())
            .data['user_version'],
        6,
      );
      expect(
        (await v4.customSelect('SELECT * FROM local_commands').getSingle())
            .data,
        {...before, 'aggregate_type': 'TRIP'},
      );
      expect(await v4.select(v4.localSyncIssues).get(), hasLength(1));
      await expectLater(
        v4.customStatement("UPDATE local_commands SET payload_json='{}'"),
        throwsA(isA<Exception>()),
      );
      expect(await v4.select(v4.cachedTrips).get(), hasLength(1));
      final id = operationUuid(),
          saved = await files.capture(
            user,
            tenant,
            operationUuid(),
            Stream.value([4, 5]),
            1024,
          );
      expect(id, isNotEmpty);
      await MediaStore(
        v4,
        files,
      ).register(user, tenant, saved.key, 'BOL', 'image/jpeg', saved);
      await v4.close();
      final reopened = AppDatabase(NativeDatabase(file));
      await MediaStore(
        reopened,
        files,
      ).verify((await MediaStore(reopened, files).list(user, tenant)).single);
      await reopened.close();
    },
  );
  test(
    'compare and set claim gives one winner and recovery preserves identity',
    () async {
      await capture();
      final original = (await store.list(user, tenant)).single;
      expect(
        await Future.wait([store.claim(original), store.claim(original)]),
        containsAll([true, false]),
      );
      await store.recover(user, tenant);
      final row = (await store.list(user, tenant)).single;
      expect(row.state, 'INIT_PENDING');
      expect(row.finalizeKey, original.finalizeKey);
      expect(row.fileKey, original.fileKey);
      expect(row.checksumSha256, original.checksumSha256);
    },
  );
  test('pending failed and unacknowledged files never clean up', () async {
    await capture();
    now = now.add(const Duration(days: 30));
    expect(await store.cleanup(user, tenant), 0);
    expect(await files.inventory(user, tenant), hasLength(1));
  });
}
