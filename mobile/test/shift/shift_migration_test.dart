import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';

import '../sync/command_store_test.dart' show intent;
import 'legacy_database.dart';

void main() {
  test('failed v4 migration rolls back rebuild, rows and version so retry is recoverable', () async {
    final dir = await Directory.systemTemp.createTemp(
      'shift-migration-failure-',
    );
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/device.sqlite');
    var old = Version4Database(NativeDatabase(file));
    final c = intent();
    await seedLegacyCommand(old, 'user-A', 'A', c, DateTime.utc(2026, 10, 12));
    final before =
        (await old.customSelect('SELECT * FROM local_commands').getSingle())
            .data;
    await old.customStatement(
      'CREATE INDEX camera_one_pending ON local_media(id)',
    );
    await old.close();
    final failed = AppDatabase(NativeDatabase(file));
    try {
      await expectLater(failed.initialize(), throwsA(isA<Exception>()));
    } finally {
      await failed.close();
    }
    old = Version4Database(NativeDatabase(file));
    await old.initialize();
    expect(
      (await old.customSelect('PRAGMA user_version').getSingle())
          .data['user_version'],
      4,
    );
    expect(
      (await old.customSelect('SELECT * FROM local_commands').getSingle()).data,
      before,
    );
    expect(
      (await old.customSelect('PRAGMA table_info(local_commands)').get()).map(
        (r) => r.data['name'],
      ),
      isNot(contains('aggregate_type')),
    );
    await old.customStatement('DROP INDEX camera_one_pending');
    await old.close();
    final db = AppDatabase(NativeDatabase(file));
    await db.initialize();
    expect(
      (await CommandStore(db).list('user-A', 'A')).single.clientOperationId,
      c.command.id,
    );
    await db.close();
  });
  test('actual v4 queue survives nullable aggregate migration with its body identity and graph', () async {
    final directory = await Directory.systemTemp.createTemp('shift-v4-');
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/device.sqlite'),
        old = Version4Database(
          NativeDatabase(File('${directory.path}/device.sqlite')),
        );
    final c = intent(), now = DateTime.utc(2026, 10, 12);
    await seedLegacyCommand(old, 'user-A', 'A', c, now, state: 'SENDING');
    final before =
        (await old.customSelect('SELECT * FROM local_commands').getSingle())
            .data;
    await old.customStatement(
      'INSERT INTO materialized_requests VALUES(?,?,?,?,?,?,?)',
      [
        c.command.id,
        'user-A',
        'A',
        c.command.path,
        'POST',
        before['payload_json'],
        now.millisecondsSinceEpoch ~/ 1000,
      ],
    );
    await old.close();
    final db = AppDatabase(NativeDatabase(file));
    addTearDown(db.close);
    await db.initialize();
    expect(
      (await db.customSelect('SELECT * FROM local_commands').getSingle()).data,
      {...before, 'aggregate_type': 'TRIP'},
    );
    expect(
      (await db.select(db.materializedRequests).get()).single.payloadJson,
      before['payload_json'],
    );
    expect((await db.customSelect('PRAGMA foreign_key_check').get()), isEmpty);
    expect(
      (await db.customSelect('PRAGMA user_version').getSingle())
          .data['user_version'],
      9,
    );
    await expectLater(
      db.customStatement('UPDATE local_commands SET trip_id=NULL'),
      throwsA(isA<Exception>()),
    );
    await expectLater(
      db.customStatement("UPDATE materialized_requests SET payload_json='{}'"),
      throwsA(isA<Exception>()),
    );
    await CommandStore(db).recoverSending('user-A', 'A');
    expect(
      (await CommandStore(db).list('user-A', 'A')).single.clientOperationId,
      c.command.id,
    );
  });
}
