import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';
import 'package:path_provider/path_provider.dart';

part 'app_database.g.dart';

class LocalSessionContexts extends Table {
  @override
  String get tableName => 'local_session_context';
  TextColumn get firebaseUid => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get membershipJson => text()();
  TextColumn get driverJson => text().nullable()();
  DateTimeColumn get refreshedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {userId, tenantId};
}

class CachedTrips extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get id => text()();
  IntColumn get serverVersion => integer()();
  TextColumn get status => text()();
  TextColumn get projectionJson => text()();
  DateTimeColumn get refreshedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {userId, tenantId, id};
}

class CachedStops extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get id => text()();
  TextColumn get tripId => text()();
  IntColumn get sequenceNumber => integer()();
  TextColumn get projectionJson => text()();
  @override
  Set<Column> get primaryKey => {userId, tenantId, id};
}

class CachedTrailers extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get id => text()();
  IntColumn get serverVersion => integer()();
  TextColumn get projectionJson => text()();
  DateTimeColumn get refreshedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {userId, tenantId, id};
}

class LocalCommands extends Table {
  TextColumn get clientOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get commandType => text()();
  IntColumn get queueSequence => integer()();
  TextColumn get endpointPath => text()();
  TextColumn get httpMethod => text()();
  TextColumn get payloadJson => text()();
  TextColumn get tripId => text()();
  TextColumn get aggregateId => text()();
  TextColumn get resourceKeysJson => text()();
  TextColumn get dependenciesJson => text()();
  DateTimeColumn get occurredAt => dateTime()();
  DateTimeColumn get createdAt => dateTime()();
  TextColumn get state => text()();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextRetryAt => dateTime().nullable()();
  IntColumn get lastHttpStatus => integer().nullable()();
  TextColumn get lastErrorCode => text().nullable()();
  DateTimeColumn get ackedAt => dateTime().nullable()();
  @override
  Set<Column> get primaryKey => {clientOperationId};
  @override
  List<String> get customConstraints => [
    "CHECK (state IN ('QUEUED','SENDING','ACKNOWLEDGED','RETRYABLE_FAILURE','CONFLICT','BLOCKED','PERMANENT_FAILURE'))",
    'CHECK (attempt_count >= 0)',
    'UNIQUE (user_id,tenant_id,queue_sequence)',
    "CHECK (http_method = 'POST')",
  ];
}

class LocalSyncIssues extends Table {
  TextColumn get id => text()();
  TextColumn get clientOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get category => text()();
  TextColumn get errorCode => text()();
  TextColumn get relatedResource => text()();
  TextColumn get message => text()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get resolvedAt => dateTime().nullable()();
  @override
  Set<Column> get primaryKey => {id};
}

@DriftDatabase(
  tables: [
    LocalSessionContexts,
    CachedTrips,
    CachedStops,
    CachedTrailers,
    LocalCommands,
    LocalSyncIssues,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor])
    : super(
        executor ??
            driftDatabase(
              name: 'trailersaas',
              native: const DriftNativeOptions(
                databaseDirectory: getApplicationSupportDirectory,
              ),
            ),
      );

  @override
  int get schemaVersion => 3;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) async {
      await migrator.createAll();
      await _queueIndexesAndImmutability();
    },
    onUpgrade: (migrator, from, to) async {
      // v1 was the empty scaffold. Its deployed schema remains an explicit upgrade source.
      if (from == 1 && to == 3) {
        await migrator.createTable(localSessionContexts);
        await migrator.createTable(cachedTrips);
        await migrator.createTable(cachedStops);
        await migrator.createTable(cachedTrailers);
      } else if (from != 2 || to != 3) {
        throw StateError('No migration from schema $from to $to is defined.');
      }
      await migrator.createTable(localCommands);
      await migrator.createTable(localSyncIssues);
      await _queueIndexesAndImmutability();
    },
  );

  Future<void> _queueIndexesAndImmutability() async {
    await customStatement(
      'CREATE INDEX local_commands_scope_state ON '
      'local_commands(user_id, tenant_id, state, created_at)',
    );
    await customStatement(
      'CREATE INDEX local_sync_issues_scope ON '
      'local_sync_issues(user_id, tenant_id, created_at)',
    );
    await customStatement('''CREATE TRIGGER local_command_intent_immutable
      BEFORE UPDATE OF client_operation_id,user_id,tenant_id,command_type,
      endpoint_path,http_method,payload_json,trip_id,aggregate_id,
      resource_keys_json,dependencies_json,occurred_at,created_at,queue_sequence ON local_commands
      BEGIN SELECT RAISE(ABORT, 'Command intent is immutable'); END''');
  }

  Future<void> initialize() async {
    await customSelect('SELECT 1').getSingle();
  }
}
