import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqlite3/sqlite3.dart' as sqlite;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';

void main() {
  test('fresh v9 creates narrow Yard metadata and preserves one command/media graph', () async {
    final db = AppDatabase(NativeDatabase.memory());
    expect(
      (await db.customSelect('PRAGMA user_version').getSingle())
          .data
          .values
          .single,
      9,
    );
    final tables = await db
        .customSelect(
          "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
        )
        .get();
    expect(tables.length, 26);
    expect(
      tables.map((r) => r.data['name']),
      containsAll([
        'local_commands',
        'materialized_requests',
        'local_media',
        'yard_drafts',
        'yard_captures',
        'yard_conflict_reviews',
      ]),
    );
    expect(await db.customSelect('PRAGMA foreign_key_check').get(), isEmpty);
    await db.close();
  });
  test('frozen deployed v8 file upgrade retains exact ACK result and frozen request with narrow command enum evolution', () async {
    final dir = await Directory.systemTemp.createTemp('yard-v9-migration-');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/v8.sqlite');
    final raw = sqlite.sqlite3.open(file.path);
    raw.execute(File('test/persistence/schema_v8.sql').readAsStringSync());
    const operation = '11111111-1111-4111-8111-111111111111',
        trailer = '22222222-2222-4222-8222-222222222222';
    final payload = jsonEncode({
      'trailerId': trailer,
      'fromLocationId': '33333333-3333-4333-8333-333333333333',
      'toLocationId': '44444444-4444-4444-8444-444444444444',
      'observedAt': '2026-01-01T00:00:00Z',
      'expectedTrailerVersion': 12,
    });
    raw.execute(
      "INSERT INTO local_commands(client_operation_id,user_id,tenant_id,command_type,endpoint_path,http_method,payload_json,trip_id,aggregate_type,aggregate_id,resource_keys_json,dependencies_json,state,occurred_at,created_at,queue_sequence) VALUES(?,?,?,?,?,'POST',?,NULL,'TRAILER',?,?,?,'ACKNOWLEDGED',1,1,1)",
      [
        operation,
        'user-A',
        'A',
        'YARD_MOVE',
        'tenants/A/yard-moves',
        payload,
        trailer,
        jsonEncode(['trailer:$trailer']),
        '[]',
      ],
    );
    raw.execute(
      "INSERT INTO materialized_requests(client_operation_id,user_id,tenant_id,endpoint_path,http_method,payload_json,created_at) VALUES(?,?,?,?,'POST',?,1)",
      [operation, 'user-A', 'A', 'tenants/A/yard-moves', payload],
    );
    raw.execute(
      "INSERT INTO command_results(client_operation_id,user_id,tenant_id,result_kind,server_id,projection_json,confirmed_at) VALUES(?,?,?,'TRAILER',?,'{\"version\":13}',1)",
      [operation, 'user-A', 'A', trailer],
    );
    raw.close();
    final db = AppDatabase(NativeDatabase(file));
    expect(
      (await db.customSelect('PRAGMA user_version').getSingle())
          .data
          .values
          .single,
      9,
    );
    expect(
      (await db.select(db.localCommands).getSingle()).payloadJson,
      payload,
    );
    expect(
      (await db.select(db.materializedRequests).getSingle()).payloadJson,
      payload,
    );
    expect(
      (await db.select(db.commandResults).getSingle()).projectionJson,
      '{"version":13}',
    );
    expect(
      (await db
              .customSelect(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='local_commands'",
              )
              .getSingle())
          .data['sql'],
      allOf(contains("YARD_CHECK"), contains("TRAILER")),
    );
    expect(await db.customSelect('PRAGMA foreign_key_check').get(), isEmpty);
    await expectLater(
      db.customStatement("UPDATE local_commands SET payload_json='{}'"),
      throwsA(anything),
    );
    await expectLater(
      db.customStatement("DELETE FROM materialized_requests"),
      throwsA(anything),
    );
    await db.close();
    final reopened = AppDatabase(NativeDatabase(file));
    expect(
      (await reopened.select(reopened.localCommands).getSingle()).payloadJson,
      payload,
    );
    expect(
      await reopened.customSelect('PRAGMA foreign_key_check').get(),
      isEmpty,
    );
    await reopened.close();
  });
}
