import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';

/// Actual deployed v3/v4 queue DDL, not the current nullable v5 declaration.
const legacyQueueSql = '''CREATE TABLE local_commands (
 client_operation_id TEXT NOT NULL PRIMARY KEY,user_id TEXT NOT NULL,tenant_id TEXT NOT NULL,
 command_type TEXT NOT NULL,queue_sequence INTEGER NOT NULL,endpoint_path TEXT NOT NULL,http_method TEXT NOT NULL,
 payload_json TEXT NOT NULL,trip_id TEXT NOT NULL,aggregate_id TEXT NOT NULL,resource_keys_json TEXT NOT NULL,
 dependencies_json TEXT NOT NULL,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL,state TEXT NOT NULL,
 attempt_count INTEGER NOT NULL DEFAULT 0,next_retry_at INTEGER,last_http_status INTEGER,last_error_code TEXT,acked_at INTEGER,
 CHECK(state IN ('QUEUED','SENDING','ACKNOWLEDGED','RETRYABLE_FAILURE','CONFLICT','BLOCKED','PERMANENT_FAILURE')),
 CHECK(attempt_count>=0),CHECK(http_method='POST'))''';

Future<void> seedLegacyCommand(
  AppDatabase db,
  String user,
  String tenant,
  CommandIntent intent,
  DateTime now, {
  String state = 'CONFLICT',
}) async {
  final c = intent.command;
  await db.customStatement(
    '''INSERT INTO local_commands(client_operation_id,user_id,tenant_id,command_type,queue_sequence,endpoint_path,http_method,payload_json,trip_id,aggregate_id,resource_keys_json,dependencies_json,occurred_at,created_at,state,attempt_count)
    VALUES(?,?,?,?,?,?, 'POST',?,?,?,?,?,?,?, ?,0)''',
    [
      c.id,
      user,
      tenant,
      intent.type,
      1,
      c.path,
      jsonEncode(c.body),
      c.tripId,
      intent.aggregate,
      jsonEncode(['trip:${c.tripId}']),
      jsonEncode(intent.dependencies),
      intent.occurredAt.millisecondsSinceEpoch ~/ 1000,
      now.millisecondsSinceEpoch ~/ 1000,
      state,
    ],
  );
}

class Version4Database extends AppDatabase {
  Version4Database(super.executor);
  @override
  int get schemaVersion => 4;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) async {
      await m.createTable(localSessionContexts);
      await m.createTable(cachedTrips);
      await m.createTable(cachedStops);
      await m.createTable(cachedTrailers);
      await customStatement(legacyQueueSql);
      await m.createTable(localSyncIssues);
      await m.createTable(localMedia);
      await customStatement(
        'CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
      );
      await m.createTable(commandMediaDependencies);
      await m.createTable(materializedRequests);
      for (final table in [
        'materialized_requests',
        'command_media_dependencies',
      ]) {
        for (final operation in ['UPDATE', 'DELETE']) {
          await customStatement(
            'CREATE TRIGGER ${table}_${operation.toLowerCase()}_immutable BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT,\'Materialized intent is immutable\'); END',
          );
        }
      }
      await customStatement(
        'CREATE INDEX local_commands_scope_state ON local_commands(user_id,tenant_id,state,created_at)',
      );
      await customStatement(
        'CREATE INDEX local_sync_issues_scope ON local_sync_issues(user_id,tenant_id,created_at)',
      );
      await customStatement(
        'CREATE INDEX local_media_scope_retry ON local_media(user_id,tenant_id,state,next_retry_at)',
      );
      await customStatement(
        '''CREATE TRIGGER local_command_intent_immutable BEFORE UPDATE OF client_operation_id,user_id,tenant_id,command_type,endpoint_path,http_method,payload_json,trip_id,aggregate_id,resource_keys_json,dependencies_json,occurred_at,created_at,queue_sequence ON local_commands BEGIN SELECT RAISE(ABORT,'Command intent is immutable');END''',
      );
    },
  );
}
