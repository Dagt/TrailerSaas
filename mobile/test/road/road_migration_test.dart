import 'dart:io';

import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';

import '../shift/legacy_database.dart';
import '../persistence/legacy_schema.dart';
import '../sync/command_store_test.dart' show intent;

// The original intent guard and frozen request exist before the v6 migration.
class Version5Database extends AppDatabase {
  Version5Database(super.executor);
  @override
  int get schemaVersion => 5;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) async {
      for (final table in allTables.where(
        (t) => !{
          'road_drafts',
          'road_captures',
          'cached_driver_updates',
          'driver_update_cursors',
          'cached_yard_contexts',
          'yard_drafts',
          'yard_captures',
          'yard_conflict_reviews',
        }.contains(t.actualTableName),
      )) {
        await createPublishedV7Table(this, table.actualTableName);
      }
      await customStatement(
        'CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
      );
      await customStatement(
        "CREATE TRIGGER local_command_intent_immutable BEFORE UPDATE OF client_operation_id,user_id,tenant_id,payload_json,endpoint_path ON local_commands BEGIN SELECT RAISE(ABORT,'Intent immutable');END",
      );
      await customStatement(
        "CREATE TRIGGER materialized_requests_update_immutable BEFORE UPDATE ON materialized_requests BEGIN SELECT RAISE(ABORT,'Request immutable');END",
      );
    },
  );
}

void main() {
  test('v5 to v6 preserves populated pending command, frozen request and guards after reopening', () async {
    final directory = await Directory.systemTemp.createTemp('road-v5-');
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/device.sqlite');
    final old = Version5Database(NativeDatabase(file));
    await old.initialize();
    final operation = intent();
    final clock = DateTime.utc(2026, 10, 10);
    await seedLegacyCommand(
      old,
      'user-A',
      'A',
      operation,
      clock,
      state: 'SENDING',
    );
    final before =
        (await old.customSelect('SELECT * FROM local_commands').getSingle())
            .data;
    await old.customStatement(
      'INSERT INTO materialized_requests VALUES(?,?,?,?,?,?,?)',
      [
        operation.command.id,
        'user-A',
        'A',
        operation.command.path,
        'POST',
        before['payload_json'],
        clock.millisecondsSinceEpoch ~/ 1000,
      ],
    );
    await old.close();
    final db = AppDatabase(NativeDatabase(file));
    addTearDown(db.close);
    await db.initialize();
    expect(
      (await db.customSelect('PRAGMA user_version').getSingle())
          .data['user_version'],
      8,
    );
    expect(db.allTables, hasLength(21));
    expect(
      (await db.customSelect('SELECT * FROM local_commands').getSingle()).data,
      before,
    );
    expect(
      (await db.select(db.materializedRequests).getSingle()).payloadJson,
      before['payload_json'],
    );
    expect(await db.customSelect('PRAGMA foreign_key_check').get(), isEmpty);
    await expectLater(
      db.customStatement("UPDATE materialized_requests SET payload_json='{}'"),
      throwsA(isA<Exception>()),
    );
    await expectLater(
      db.customStatement("UPDATE local_commands SET endpoint_path='elsewhere'"),
      throwsA(isA<Exception>()),
    );
    await CommandStore(db).recoverSending('user-A', 'A');
    expect(
      (await CommandStore(db).list('user-A', 'A')).single.clientOperationId,
      operation.command.id,
    );
  });
  test(
    'late v6 migration failure rolls back newly created tables and version',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'road-v6-failure-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final file = File('${directory.path}/device.sqlite');
      final old = Version5Database(NativeDatabase(file));
      await old.initialize();
      await old.customStatement(
        'CREATE INDEX road_drafts_scope ON cached_trips(id)',
      );
      await old.close();
      final failing = AppDatabase(NativeDatabase(file));
      await expectLater(failing.initialize(), throwsA(isA<Exception>()));
      await failing.close();
      final recovered = Version5Database(NativeDatabase(file));
      await recovered.initialize();
      expect(
        (await recovered.customSelect('PRAGMA user_version').getSingle())
            .data['user_version'],
        5,
      );
      expect(
        await recovered
            .customSelect(
              "SELECT name FROM sqlite_master WHERE name='road_drafts'",
            )
            .get(),
        isEmpty,
      );
      await recovered.customStatement('DROP INDEX road_drafts_scope');
      await recovered.close();
      final db = AppDatabase(NativeDatabase(file));
      addTearDown(db.close);
      await db.initialize();
      expect(
        (await db.customSelect('PRAGMA user_version').getSingle())
            .data['user_version'],
        8,
      );
    },
  );
}
