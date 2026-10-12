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

class LocalMedia extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get documentType => text()();
  TextColumn get contentType => text()();
  TextColumn get fileKey => text()();
  IntColumn get sizeBytes => integer()();
  TextColumn get checksumSha256 => text()();
  TextColumn get state => text()();
  TextColumn get serverDocumentId => text().nullable()();
  TextColumn get finalizeKey => text()();
  TextColumn get attemptHistoryJson =>
      text().withDefault(const Constant('[]'))();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextRetryAt => dateTime().nullable()();
  IntColumn get lastHttpStatus => integer().nullable()();
  TextColumn get lastErrorCode => text().nullable()();
  BoolColumn get reviewRequired =>
      boolean().withDefault(const Constant(false))();
  TextColumn get tripId => text().nullable()();
  DateTimeColumn get capturedAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();
  DateTimeColumn get finalizedAt => dateTime().nullable()();
  TextColumn get fileState => text().withDefault(const Constant('PRESENT'))();
  BoolColumn get retainOffline => boolean().withDefault(const Constant(true))();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'UNIQUE(id,user_id,tenant_id)',
    "CHECK(length(id)=36 AND id GLOB '????????-????-????-????-????????????' AND id NOT GLOB '*[^0-9a-f-]*')",
    'CHECK(size_bytes > 0 AND size_bytes <= 104857600)',
    'CHECK(length(checksum_sha256)=64)',
    "CHECK(document_type IN ('BOL','POD','DELIVERY_PHOTO','PRETRIP_FRONT','PRETRIP_REAR','PRETRIP_DRIVER_SIDE','PRETRIP_PASSENGER_SIDE','DAMAGE_PHOTO','FUEL_RECEIPT'))",
    "CHECK(content_type IN ('image/jpeg','image/png','application/pdf'))",
    "CHECK(state IN ('LOCAL_ONLY','INIT_PENDING','UPLOAD_PENDING','UPLOADING','UPLOADED','FINALIZE_PENDING','FINALIZED','FAILED'))",
    "CHECK(file_state IN ('PRESENT','REMOVING','REMOVED'))",
    'CHECK(attempt_count >= 0)',
    "CHECK(state <> 'FINALIZED' OR (server_document_id IS NOT NULL AND finalized_at IS NOT NULL))",
  ];
}

class CommandMediaDependencies extends Table {
  TextColumn get clientOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get mediaId => text()();
  TextColumn get fieldPath => text()();
  @override
  Set<Column> get primaryKey => {clientOperationId, fieldPath};
  @override
  List<String> get customConstraints => [
    'UNIQUE(client_operation_id,media_id)',
    'FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    'FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id)',
  ];
}

class MaterializedRequests extends Table {
  TextColumn get clientOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get endpointPath => text()();
  TextColumn get httpMethod => text()();
  TextColumn get payloadJson => text()();
  DateTimeColumn get createdAt => dateTime()();
  @override
  Set<Column> get primaryKey => {clientOperationId};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    "CHECK(http_method='POST')",
  ];
}

@DriftDatabase(
  tables: [
    LocalSessionContexts,
    CachedTrips,
    CachedStops,
    CachedTrailers,
    LocalCommands,
    LocalSyncIssues,
    LocalMedia,
    CommandMediaDependencies,
    MaterializedRequests,
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
  int get schemaVersion => 4;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) async {
      await migrator.createAll();
      await _queueIndexesAndImmutability();
      await _mediaIndexesAndImmutability();
    },
    onUpgrade: (migrator, from, to) async {
      // v1 was the empty scaffold. Its deployed schema remains an explicit upgrade source.
      if (from < 1 || from > 3 || to != 4) {
        throw StateError('No migration from schema $from to $to is defined.');
      }
      if (from == 1) {
        await migrator.createTable(localSessionContexts);
        await migrator.createTable(cachedTrips);
        await migrator.createTable(cachedStops);
        await migrator.createTable(cachedTrailers);
      }
      if (from <= 2) {
        await migrator.createTable(localCommands);
        await migrator.createTable(localSyncIssues);
        await _queueIndexesAndImmutability();
      }
      await migrator.createTable(localMedia);
      await customStatement(
        'CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
      );
      await migrator.createTable(commandMediaDependencies);
      await migrator.createTable(materializedRequests);
      await _mediaIndexesAndImmutability(createParentIndex: false);
    },
    beforeOpen: (_) => customStatement('PRAGMA foreign_keys=ON'),
  );

  Future<void> _mediaIndexesAndImmutability({
    bool createParentIndex = true,
  }) async {
    if (createParentIndex) {
      await customStatement(
        'CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
      );
    }
    await customStatement(
      'CREATE INDEX local_media_scope_retry ON local_media(user_id,tenant_id,state,next_retry_at)',
    );
    await customStatement('''CREATE TRIGGER local_media_content_immutable
      BEFORE UPDATE OF id,user_id,tenant_id,document_type,content_type,file_key,size_bytes,checksum_sha256,captured_at,trip_id ON local_media
      BEGIN SELECT RAISE(ABORT, 'Media content is immutable'); END''');
    await customStatement('''CREATE TRIGGER local_media_final_identity_immutable
      BEFORE UPDATE OF server_document_id,finalize_key,finalized_at,state ON local_media
      WHEN OLD.state='FINALIZED'
      BEGIN SELECT RAISE(ABORT, 'Finalized media is immutable'); END''');
    await customStatement('''CREATE TRIGGER local_media_attempt_identity_guard
      BEFORE UPDATE OF server_document_id,finalize_key ON local_media
      WHEN (OLD.server_document_id IS NOT NULL AND
          (NEW.server_document_id IS NOT OLD.server_document_id OR NEW.finalize_key<>OLD.finalize_key))
        OR (OLD.server_document_id IS NULL AND NEW.finalize_key<>OLD.finalize_key)
      BEGIN
        SELECT CASE WHEN NOT (OLD.server_document_id IS NOT NULL AND OLD.last_error_code='UPLOAD_NOT_COMPLETE'
          AND NEW.server_document_id IS NULL AND NEW.state='INIT_PENDING')
          THEN RAISE(ABORT, 'Uncertain upload identity is immutable') END;
      END''');
    for (final table in [
      'materialized_requests',
      'command_media_dependencies',
    ]) {
      for (final operation in ['UPDATE', 'DELETE']) {
        await customStatement(
          'CREATE TRIGGER ${table}_${operation.toLowerCase()}_immutable BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT, \'Materialized intent is immutable\'); END',
        );
      }
    }
    await customStatement('''CREATE TRIGGER media_dependency_before_send
      BEFORE INSERT ON command_media_dependencies
      WHEN EXISTS(SELECT 1 FROM materialized_requests WHERE client_operation_id=NEW.client_operation_id)
        OR EXISTS(SELECT 1 FROM local_commands WHERE client_operation_id=NEW.client_operation_id AND (attempt_count>0 OR state<>'QUEUED'))
        OR EXISTS(SELECT 1 FROM local_media WHERE id=NEW.media_id AND file_state<>'PRESENT')
      BEGIN SELECT RAISE(ABORT, 'Dependencies must precede sending'); END''');
  }

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
