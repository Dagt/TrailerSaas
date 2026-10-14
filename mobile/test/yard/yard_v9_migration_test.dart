import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqlite3/sqlite3.dart' as sqlite;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';

void main() {
  test('v8 file preserves pending commands, finalized media, Yard drafts and capture FKs; failed v9 upgrade rolls back and retries', () async {
    final dir = await Directory.systemTemp.createTemp('yard-v9-recovery-');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/v8.sqlite');
    final raw = sqlite.sqlite3.open(file.path);
    raw.execute(File('test/persistence/schema_v8.sql').readAsStringSync());
    const operation = '11111111-1111-4111-8111-111111111111',
        trailer = '22222222-2222-4222-8222-222222222222',
        media = '33333333-3333-4333-8333-333333333333',
        draft = '44444444-4444-4444-8444-444444444444';
    raw.execute(
      "INSERT INTO local_commands(client_operation_id,user_id,tenant_id,command_type,endpoint_path,http_method,payload_json,trip_id,aggregate_type,aggregate_id,resource_keys_json,dependencies_json,state,occurred_at,created_at,queue_sequence) VALUES(?,'user-A','A','YARD_MOVE','tenants/A/yard-moves','POST','{}',NULL,'TRAILER',?,'[]','[]','RETRYABLE_FAILURE',1,1,1)",
      [operation, trailer],
    );
    raw.execute(
      "INSERT INTO local_media(id,user_id,tenant_id,document_type,content_type,file_key,size_bytes,checksum_sha256,state,server_document_id,finalize_key,captured_at,updated_at,finalized_at,trailer_id) VALUES(?,'user-A','A','DAMAGE_PHOTO','image/jpeg',?,1,?,'FINALIZED',?,?,1,1,1,?)",
      [media, media, 'a' * 64, media, media, trailer],
    );
    raw.execute(
      "INSERT INTO yard_drafts(id,user_id,tenant_id,trailer_id,site_id,request_json,created_at,updated_at) VALUES(?,'user-A','A',?,?,'{}',1,1)",
      [draft, trailer, draft],
    );
    raw.execute(
      "INSERT INTO yard_captures(id,user_id,tenant_id,draft_id,media_id,state,created_at) VALUES(?,'user-A','A',?,?,'CAPTURED',1)",
      [operation, draft, media],
    );
    final originals = {
      for (final table in [
        'local_commands',
        'local_media',
        'yard_drafts',
        'yard_captures',
      ])
        table: raw
            .select('SELECT * FROM $table')
            .map((r) => Map<String, Object?>.from(r))
            .toList(),
    };
    // Force a late v9 index creation failure after all five new tables were created.
    raw.execute('CREATE INDEX yard_check_scope ON local_commands(user_id)');
    raw.close();
    final interrupted = AppDatabase(NativeDatabase(file));
    await expectLater(interrupted.initialize(), throwsA(anything));
    await interrupted.close();
    final verify = sqlite.sqlite3.open(file.path);
    expect(verify.select('PRAGMA user_version').single.values.single, 8);
    expect(
      verify.select(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='cached_yard_checks'",
      ),
      isEmpty,
    );
    for (final table in originals.keys) {
      expect(
        verify
            .select('SELECT * FROM $table')
            .map((r) => Map<String, Object?>.from(r))
            .toList(),
        originals[table],
      );
    }
    verify.execute('DROP INDEX yard_check_scope');
    verify.close();
    final recovered = AppDatabase(NativeDatabase(file));
    await recovered.initialize();
    expect(
      (await recovered.customSelect('PRAGMA user_version').getSingle())
          .data
          .values
          .single,
      9,
    );
    for (final table in originals.keys) {
      expect(
        (await recovered.customSelect('SELECT * FROM $table').get())
            .map((r) => r.data)
            .toList(),
        originals[table],
      );
    }
    expect(
      await recovered.customSelect('PRAGMA foreign_key_check').get(),
      isEmpty,
    );
    await recovered.close();
  });
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
