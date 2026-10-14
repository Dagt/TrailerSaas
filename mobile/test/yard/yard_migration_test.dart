import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';

import '../persistence/legacy_schema.dart';

/// The actual v6 queue/result constraints, not the new v7 Table definitions.
class Version6Database extends AppDatabase {
  Version6Database(super.executor);
  @override
  int get schemaVersion => 6;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) async {
      await customStatement('''CREATE TABLE local_commands (
      client_operation_id TEXT NOT NULL PRIMARY KEY,user_id TEXT NOT NULL,tenant_id TEXT NOT NULL,
      command_type TEXT NOT NULL,queue_sequence INTEGER NOT NULL,endpoint_path TEXT NOT NULL,http_method TEXT NOT NULL,
      payload_json TEXT NOT NULL,trip_id TEXT,aggregate_id TEXT,aggregate_type TEXT NOT NULL DEFAULT 'TRIP',
      resource_keys_json TEXT NOT NULL,dependencies_json TEXT NOT NULL,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL,
      state TEXT NOT NULL,attempt_count INTEGER NOT NULL DEFAULT 0,next_retry_at INTEGER,last_http_status INTEGER,last_error_code TEXT,acked_at INTEGER,
      CHECK(state IN ('QUEUED','SENDING','ACKNOWLEDGED','RETRYABLE_FAILURE','CONFLICT','BLOCKED','PERMANENT_FAILURE')),
      CHECK(attempt_count>=0),CHECK(aggregate_type IN ('TRIP','SHIFT','INSPECTION')),
      CHECK(aggregate_type<>'TRIP' OR (trip_id IS NOT NULL AND aggregate_id IS NOT NULL)),
      UNIQUE(user_id,tenant_id,queue_sequence),CHECK(http_method='POST'))''');
      await customStatement(
        'CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
      );
      await customStatement('''CREATE TABLE command_results(client_operation_id TEXT NOT NULL PRIMARY KEY,user_id TEXT NOT NULL,tenant_id TEXT NOT NULL,
      result_kind TEXT NOT NULL,server_id TEXT NOT NULL,projection_json TEXT NOT NULL,confirmed_at INTEGER NOT NULL,
      FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id),
      CHECK(result_kind IN ('SHIFT','INSPECTION')))''');
      for (final table in allTables.where(
        (t) => !{
          'local_commands',
          'command_results',
          'cached_yard_contexts',
          'yard_drafts',
          'yard_captures',
          'yard_conflict_reviews',
          'cached_yard_checks',
          'cached_yard_check_items',
          'yard_check_observations',
          'yard_check_reviews',
          'yard_check_directories',
        }.contains(t.actualTableName),
      )) {
        await createPublishedV7Table(this, table.actualTableName);
      }
      await customStatement(
        "CREATE TRIGGER local_command_intent_immutable BEFORE UPDATE OF payload_json ON local_commands BEGIN SELECT RAISE(ABORT,'immutable'); END",
      );
      for (final operation in ['UPDATE', 'DELETE']) {
        await customStatement(
          "CREATE TRIGGER command_results_${operation.toLowerCase()}_immutable BEFORE $operation ON command_results BEGIN SELECT RAISE(ABORT,'immutable'); END",
        );
      }
    },
    beforeOpen: (_) => customStatement('PRAGMA foreign_keys=ON'),
  );
}

void main() {
  test('real v6 file preserves queue, ACK results, frozen requests and immutable namespace graph through v8', () async {
    final directory = await Directory.systemTemp.createTemp('yard-v6-');
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/device.sqlite');
    final old = Version6Database(NativeDatabase(file));
    await old.initialize();
    await old.customStatement(
      "INSERT INTO local_commands(client_operation_id,user_id,tenant_id,command_type,queue_sequence,endpoint_path,http_method,payload_json,aggregate_type,resource_keys_json,dependencies_json,occurred_at,created_at,state) VALUES('00000000-0000-4000-8000-000000000099','u','t','on-duty',1,'tenants/t/driver/me/shifts/on-duty','POST','{\"occurredAt\":\"2026-10-10T10:00:00Z\"}','SHIFT','[]','[]',1,1,'ACKNOWLEDGED')",
    );
    await old.customStatement(
      "INSERT INTO command_results VALUES('00000000-0000-4000-8000-000000000099','u','t','SHIFT','00000000-0000-4000-8000-000000000098','{\"id\":\"00000000-0000-4000-8000-000000000098\"}',1)",
    );
    await old.customStatement(
      "INSERT INTO materialized_requests VALUES('00000000-0000-4000-8000-000000000099','u','t','tenants/t/driver/me/shifts/on-duty','POST','{\"occurredAt\":\"2026-10-10T10:00:00Z\"}',1)",
    );
    final command =
        (await old.customSelect('SELECT * FROM local_commands').getSingle())
            .data;
    final result =
        (await old.customSelect('SELECT * FROM command_results').getSingle())
            .data;
    final request =
        (await old
                .customSelect('SELECT * FROM materialized_requests')
                .getSingle())
            .data;
    expect(
      (await old.customSelect('PRAGMA user_version').getSingle())
          .data['user_version'],
      6,
    );
    await old.close();
    final upgraded = AppDatabase(NativeDatabase(file));
    addTearDown(upgraded.close);
    await upgraded.initialize();
    expect(
      (await upgraded.customSelect('PRAGMA user_version').getSingle())
          .data['user_version'],
      9,
    );
    expect(
      (await upgraded.customSelect('SELECT * FROM local_commands').getSingle())
          .data,
      command,
    );
    expect(
      (await upgraded.customSelect('SELECT * FROM command_results').getSingle())
          .data,
      result,
    );
    expect(
      (await upgraded
              .customSelect('SELECT * FROM materialized_requests')
              .getSingle())
          .data,
      request,
    );
    expect(
      await upgraded.customSelect('PRAGMA foreign_key_check').get(),
      isEmpty,
    );
    await expectLater(
      upgraded.customStatement("UPDATE local_commands SET payload_json='{}'"),
      throwsA(isA<Exception>()),
    );
    await expectLater(
      upgraded.customStatement(
        "UPDATE command_results SET projection_json='{}'",
      ),
      throwsA(isA<Exception>()),
    );
    expect(await upgraded.select(upgraded.cachedYardContexts).get(), isEmpty);
  });
}
