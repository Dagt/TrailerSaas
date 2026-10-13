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

class CachedYardContexts extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get projectionJson => text()();
  TextColumn get selectedSiteId => text().nullable()();
  DateTimeColumn get refreshedAt => dateTime()();
  BoolColumn get accessBlocked =>
      boolean().withDefault(const Constant(false))();
  @override
  Set<Column> get primaryKey => {userId, tenantId};
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
  TextColumn get tripId => text().nullable()();
  TextColumn get aggregateId => text().nullable()();
  TextColumn get aggregateType => text().withDefault(const Constant('TRIP'))();
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
    "CHECK (aggregate_type IN ('TRIP','SHIFT','INSPECTION','TRAILER'))",
    "CHECK (aggregate_type <> 'TRIP' OR (trip_id IS NOT NULL AND aggregate_id IS NOT NULL))",
    "CHECK (aggregate_type <> 'TRAILER' OR (trip_id IS NULL AND aggregate_id IS NOT NULL))",
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
  TextColumn get trailerId => text().nullable()();
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
    'UNIQUE(user_id,tenant_id,server_document_id)',
    "CHECK(file_key=id)",
    "CHECK(length(finalize_key)=36 AND finalize_key GLOB '????????-????-????-????-????????????' AND finalize_key NOT GLOB '*[^0-9a-f-]*')",
    "CHECK(server_document_id IS NULL OR (length(server_document_id)=36 AND server_document_id GLOB '????????-????-????-????-????????????' AND server_document_id NOT GLOB '*[^0-9a-f-]*'))",
    "CHECK(length(id)=36 AND id GLOB '????????-????-????-????-????????????' AND id NOT GLOB '*[^0-9a-f-]*')",
    'CHECK(size_bytes > 0 AND size_bytes <= 104857600)',
    'CHECK(length(checksum_sha256)=64)',
    "CHECK(checksum_sha256 NOT GLOB '*[^0-9a-f]*')",
    "CHECK(document_type IN ('BOL','POD','DELIVERY_PHOTO','PRETRIP_FRONT','PRETRIP_REAR','PRETRIP_DRIVER_SIDE','PRETRIP_PASSENGER_SIDE','DAMAGE_PHOTO','FUEL_RECEIPT'))",
    "CHECK(content_type IN ('image/jpeg','image/png','application/pdf'))",
    "CHECK(content_type<>'application/pdf' OR document_type IN ('BOL','POD'))",
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

class CommandResults extends Table {
  TextColumn get clientOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get resultKind => text()();
  TextColumn get serverId => text()();
  TextColumn get projectionJson => text()();
  DateTimeColumn get confirmedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {clientOperationId};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    "CHECK (result_kind IN ('SHIFT','INSPECTION','TRAILER'))",
  ];
}

class CommandResultDependencies extends Table {
  TextColumn get clientOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get sourceOperationId => text()();
  TextColumn get fieldPath => text()();
  @override
  Set<Column> get primaryKey => {clientOperationId, fieldPath};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    'FOREIGN KEY(source_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    "CHECK (field_path='shiftId')",
    'CHECK (client_operation_id<>source_operation_id)',
  ];
}

class InspectionDrafts extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get inspectionType => text()();
  TextColumn get shiftReferenceJson => text()();
  TextColumn get tractorId => text()();
  TextColumn get tripId => text().nullable()();
  TextColumn get odometer => text().withDefault(const Constant(''))();
  TextColumn get checklistJson =>
      text().withDefault(const Constant('{"items":[]}'))();
  TextColumn get photosJson => text().withDefault(const Constant('{}'))();
  TextColumn get damages => text().nullable()();
  TextColumn get failures => text().nullable()();
  TextColumn get comments => text().nullable()();
  TextColumn get commandId => text().nullable()();
  DateTimeColumn get updatedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'UNIQUE(id,user_id,tenant_id)',
    "CHECK (inspection_type IN ('PRE_TRIP','POST_TRIP'))",
    'FOREIGN KEY(command_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
  ];
}

class CameraCaptures extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get draftId => text()();
  TextColumn get position => text()();
  TextColumn get mediaId => text().nullable()();
  TextColumn get state => text().withDefault(const Constant('PENDING'))();
  DateTimeColumn get createdAt => dateTime()();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(draft_id,user_id,tenant_id) REFERENCES inspection_drafts(id,user_id,tenant_id)',
    'FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id)',
    "CHECK (position IN ('front','rear','driverSide','passengerSide'))",
    "CHECK (state IN ('PENDING','CAPTURED','CANCELLED','FAILED'))",
    "CHECK (state<>'CAPTURED' OR media_id IS NOT NULL)",
  ];
}

class RoadDrafts extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get tripId => text()();
  TextColumn get tractorId => text().nullable()();
  TextColumn get stopId => text().nullable()();
  TextColumn get purpose => text()();
  TextColumn get inputsJson => text().withDefault(const Constant('{}'))();
  TextColumn get mediaJson => text().withDefault(const Constant('{}'))();
  TextColumn get commandId => text().nullable()();
  DateTimeColumn get updatedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'UNIQUE(id,user_id,tenant_id)',
    "CHECK (purpose IN ('SIGNED_BOL','NO_RECEIVER_AVAILABLE','FUEL','PROBLEM'))",
    "CHECK (purpose<>'FUEL' OR tractor_id IS NOT NULL)",
    'FOREIGN KEY(command_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
  ];
}

class RoadCaptures extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get draftId => text()();
  TextColumn get documentType => text()();
  TextColumn get field => text()();
  TextColumn get mediaId => text().nullable()();
  TextColumn get state => text().withDefault(const Constant('PENDING'))();
  DateTimeColumn get createdAt => dateTime()();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(draft_id,user_id,tenant_id) REFERENCES road_drafts(id,user_id,tenant_id)',
    'FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id)',
    "CHECK (document_type IN ('BOL','DELIVERY_PHOTO','FUEL_RECEIPT','DAMAGE_PHOTO'))",
    "CHECK (state IN ('PENDING','CAPTURED','CANCELLED','FAILED'))",
    "CHECK (state<>'CAPTURED' OR media_id IS NOT NULL)",
  ];
}

class CachedDriverUpdates extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get driverId => text()();
  TextColumn get id => text()();
  TextColumn get tripId => text()();
  TextColumn get updateType => text()();
  TextColumn get projectionJson => text()();
  IntColumn get sequence => integer()();
  DateTimeColumn get downloadedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {userId, tenantId, driverId, id};
}

class DriverUpdateCursors extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get driverId => text()();
  TextColumn get cursor => text().nullable()();
  BoolColumn get accessBlocked =>
      boolean().withDefault(const Constant(false))();
  @override
  Set<Column> get primaryKey => {userId, tenantId, driverId};
}

class YardDrafts extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get trailerId => text()();
  TextColumn get siteId => text()();
  TextColumn get requestJson => text()();
  TextColumn get photosJson => text().withDefault(const Constant('[]'))();
  TextColumn get comment => text().withDefault(const Constant(''))();
  TextColumn get commandId => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'UNIQUE(id,user_id,tenant_id)',
    'FOREIGN KEY(command_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    'CHECK(json_valid(request_json) AND json_valid(photos_json))',
    'CHECK(length(comment)<=4000)',
  ];
}

class YardCaptures extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get draftId => text()();
  TextColumn get mediaId => text().nullable()();
  TextColumn get state => text().withDefault(const Constant('PENDING'))();
  DateTimeColumn get createdAt => dateTime()();
  @override
  Set<Column> get primaryKey => {id};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(draft_id,user_id,tenant_id) REFERENCES yard_drafts(id,user_id,tenant_id)',
    'FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id)',
    "CHECK(state IN ('PENDING','CAPTURED','FAILED','CANCELLED'))",
    "CHECK(state<>'CAPTURED' OR media_id IS NOT NULL)",
  ];
}

/// Explicit fresh human review starts a new action, never changes a failed request
/// or marks it ACKNOWLEDGED. Original descendants remain blocked.
class YardConflictReviews extends Table {
  TextColumn get failedOperationId => text()();
  TextColumn get replacementOperationId => text()();
  TextColumn get userId => text()();
  TextColumn get tenantId => text()();
  TextColumn get trailerId => text()();
  IntColumn get reviewedVersion => integer()();
  TextColumn get reviewedProjectionJson => text()();
  DateTimeColumn get reviewedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {failedOperationId};
  @override
  List<String> get customConstraints => [
    'FOREIGN KEY(failed_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    'FOREIGN KEY(replacement_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id)',
    'CHECK(failed_operation_id<>replacement_operation_id AND reviewed_version>=0)',
    'CHECK(json_valid(reviewed_projection_json))',
  ];
}

@DriftDatabase(
  tables: [
    LocalSessionContexts,
    CachedTrips,
    CachedStops,
    CachedTrailers,
    CachedYardContexts,
    LocalCommands,
    LocalSyncIssues,
    LocalMedia,
    CommandMediaDependencies,
    MaterializedRequests,
    CommandResults,
    CommandResultDependencies,
    InspectionDrafts,
    CameraCaptures,
    RoadDrafts,
    RoadCaptures,
    CachedDriverUpdates,
    DriverUpdateCursors,
    YardDrafts,
    YardCaptures,
    YardConflictReviews,
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
  int get schemaVersion => 8;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) async {
      await transaction(() async {
        await migrator.createAll();
        await _queueIndexesAndImmutability();
        await _mediaIndexesAndImmutability();
        await _shiftIndexesAndImmutability();
        await _roadIndexesAndImmutability();
        await _yardIndexesAndImmutability();
        await customStatement('PRAGMA user_version=8');
      });
    },
    onUpgrade: (migrator, from, to) async {
      // v1 was the empty scaffold. Its deployed schema remains an explicit upgrade source.
      if (from < 1 || from > 7 || to != 8) {
        throw StateError('No migration from schema $from to $to is defined.');
      }
      // Whole upgrade and version marker commit together. TableMigration alone
      // commits its rebuild, which would leave a non-recoverable half-v5 on crash.
      await customStatement('PRAGMA foreign_keys=OFF');
      try {
        await transaction(() async {
          if (from < 7) {
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
            if (from >= 3 && from < 5) {
              // Preserve all queue rows and dependent tables. Native SQLite opens with
              // FK enforcement off during the migration; beforeOpen restores it.
              await customStatement(
                'DROP TRIGGER IF EXISTS local_command_intent_immutable',
              );
              await migrator.alterTable(
                TableMigration(
                  localCommands,
                  newColumns: [localCommands.aggregateType],
                ),
              );
              await _queueIndexesAndImmutability();
            }
            if (from <= 3) {
              await migrator.createTable(localMedia);
              await customStatement(
                'CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
              );
              await migrator.createTable(commandMediaDependencies);
              await migrator.createTable(materializedRequests);
              await _mediaIndexesAndImmutability(createParentIndex: false);
            } else {
              await customStatement(
                'CREATE UNIQUE INDEX IF NOT EXISTS local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
              );
            }
            if (from < 5) {
              await migrator.createTable(commandResults);
              await migrator.createTable(commandResultDependencies);
              await migrator.createTable(inspectionDrafts);
              await migrator.createTable(cameraCaptures);
              await _shiftIndexesAndImmutability();
            }
            if (from < 6) {
              await migrator.createTable(roadDrafts);
              await migrator.createTable(roadCaptures);
              await migrator.createTable(cachedDriverUpdates);
              await migrator.createTable(driverUpdateCursors);
              await _roadIndexesAndImmutability();
            }
            if (from >= 5) {
              await customStatement(
                'DROP TRIGGER IF EXISTS local_command_intent_immutable',
              );
              await migrator.alterTable(TableMigration(localCommands));
              await _queueIndexesAndImmutability();
              await customStatement(
                'CREATE UNIQUE INDEX IF NOT EXISTS local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id)',
              );
              for (final operation in ['UPDATE', 'DELETE']) {
                await customStatement(
                  'DROP TRIGGER IF EXISTS command_results_${operation.toLowerCase()}_immutable',
                );
              }
              await migrator.alterTable(TableMigration(commandResults));
              for (final operation in ['UPDATE', 'DELETE']) {
                await customStatement(
                  'CREATE TRIGGER command_results_${operation.toLowerCase()}_immutable BEFORE $operation ON command_results BEGIN SELECT RAISE(ABORT,\'Confirmed identity and dependencies are immutable\'); END',
                );
              }
            }
            await migrator.createTable(cachedYardContexts);
          }
          // v7 -> v8 adds metadata only. Commands/frozen requests are not rebuilt.
          if (from >= 4) {
            await migrator.addColumn(localMedia, localMedia.trailerId);
          }
          await migrator.createTable(yardDrafts);
          await migrator.createTable(yardCaptures);
          await migrator.createTable(yardConflictReviews);
          await _yardIndexesAndImmutability();
          if ((await customSelect(
            'PRAGMA foreign_key_check',
          ).get()).isNotEmpty) {
            throw StateError('Migration cannot preserve foreign-key integrity');
          }
          await customStatement('PRAGMA user_version=8');
        });
      } finally {
        await customStatement('PRAGMA foreign_keys=ON');
      }
    },
    beforeOpen: (_) => customStatement('PRAGMA foreign_keys=ON'),
  );

  Future<void> _yardIndexesAndImmutability() async {
    await customStatement(
      'CREATE INDEX yard_drafts_scope ON yard_drafts(user_id,tenant_id,trailer_id,site_id,updated_at)',
    );
    await customStatement(
      "CREATE UNIQUE INDEX yard_camera_one_pending ON yard_captures(state) WHERE state='PENDING'",
    );
    await customStatement(
      '''CREATE TRIGGER yard_draft_context_immutable BEFORE UPDATE OF id,user_id,tenant_id,trailer_id,site_id,request_json,created_at ON yard_drafts BEGIN SELECT RAISE(ABORT,'Yard draft context is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER yard_draft_queued_immutable BEFORE UPDATE ON yard_drafts WHEN OLD.command_id IS NOT NULL BEGIN SELECT RAISE(ABORT,'Queued Yard draft is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER yard_capture_context_immutable BEFORE UPDATE OF id,user_id,tenant_id,draft_id,created_at ON yard_captures BEGIN SELECT RAISE(ABORT,'Yard capture context is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER yard_capture_before_queue BEFORE INSERT ON yard_captures WHEN NOT EXISTS(SELECT 1 FROM yard_drafts d WHERE d.id=NEW.draft_id AND d.user_id=NEW.user_id AND d.tenant_id=NEW.tenant_id AND d.command_id IS NULL) BEGIN SELECT RAISE(ABORT,'Yard draft is unavailable or queued');END''',
    );
    await customStatement(
      '''CREATE TRIGGER yard_queue_after_capture BEFORE UPDATE OF command_id ON yard_drafts WHEN NEW.command_id IS NOT NULL AND EXISTS(SELECT 1 FROM yard_captures c WHERE c.draft_id=OLD.id AND c.state='PENDING') BEGIN SELECT RAISE(ABORT,'Camera result still pending');END''',
    );
    await customStatement(
      '''CREATE TRIGGER yard_camera_exclusive BEFORE INSERT ON yard_captures WHEN NEW.state='PENDING' AND (EXISTS(SELECT 1 FROM camera_captures WHERE state='PENDING') OR EXISTS(SELECT 1 FROM road_captures WHERE state='PENDING')) BEGIN SELECT RAISE(ABORT,'A native capture is pending recovery in its original mode');END''',
    );
    for (final table in ['camera_captures', 'road_captures']) {
      await customStatement(
        "CREATE TRIGGER ${table}_yard_exclusive BEFORE INSERT ON $table WHEN NEW.state='PENDING' AND EXISTS(SELECT 1 FROM yard_captures WHERE state='PENDING') BEGIN SELECT RAISE(ABORT,'Yard camera is still active');END",
      );
    }
    await customStatement(
      '''CREATE TRIGGER local_media_trailer_identity BEFORE UPDATE OF trailer_id ON local_media BEGIN SELECT RAISE(ABORT,'Media Trailer identity is immutable');END''',
    );
    for (final operation in ['INSERT', 'UPDATE']) {
      await customStatement(
        "CREATE TRIGGER local_media_trailer_scope_${operation.toLowerCase()} BEFORE $operation ON local_media WHEN NEW.trailer_id IS NOT NULL AND (NEW.document_type<>'DAMAGE_PHOTO' OR NEW.trip_id IS NOT NULL OR length(NEW.trailer_id)<>36 OR NEW.trailer_id NOT GLOB '????????-????-????-????-????????????' OR NEW.trailer_id GLOB '*[^0-9a-f-]*') BEGIN SELECT RAISE(ABORT,'Invalid Trailer media scope');END",
      );
    }
    for (final operation in ['UPDATE', 'DELETE']) {
      await customStatement(
        "CREATE TRIGGER yard_reviews_${operation.toLowerCase()}_immutable BEFORE $operation ON yard_conflict_reviews BEGIN SELECT RAISE(ABORT,'Conflict review is immutable');END",
      );
    }
    await customStatement(
      '''CREATE TRIGGER yard_review_requires_failed_command BEFORE INSERT ON yard_conflict_reviews WHEN NOT EXISTS(
      SELECT 1 FROM local_commands failed JOIN local_commands replacement ON replacement.client_operation_id=NEW.replacement_operation_id
      WHERE failed.client_operation_id=NEW.failed_operation_id AND failed.user_id=NEW.user_id AND failed.tenant_id=NEW.tenant_id
      AND replacement.user_id=NEW.user_id AND replacement.tenant_id=NEW.tenant_id AND failed.aggregate_type='TRAILER' AND replacement.aggregate_type='TRAILER'
      AND failed.aggregate_id=NEW.trailer_id AND replacement.aggregate_id=NEW.trailer_id
      AND failed.command_type IN ('YARD_MOVE','LOCATION_CORRECTION','LOAD_STATE_CHANGE','OOS')
      AND replacement.command_type IN ('YARD_MOVE','LOCATION_CORRECTION','LOAD_STATE_CHANGE','OOS')
      AND failed.state IN ('CONFLICT','BLOCKED','PERMANENT_FAILURE') AND replacement.state='QUEUED' AND replacement.attempt_count=0)
      BEGIN SELECT RAISE(ABORT,'Only an explicit new Yard action can review its failed physical chain');END''',
    );
  }

  Future<void> _roadIndexesAndImmutability() async {
    await customStatement(
      'CREATE INDEX road_drafts_scope ON road_drafts(user_id,tenant_id,updated_at)',
    );
    await customStatement(
      'CREATE UNIQUE INDEX road_camera_one_pending ON road_captures(state) WHERE state=\'PENDING\'',
    );
    await customStatement(
      '''CREATE TRIGGER road_draft_context_immutable BEFORE UPDATE OF id,user_id,tenant_id,trip_id,tractor_id,stop_id,purpose ON road_drafts BEGIN SELECT RAISE(ABORT,'Draft context is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER road_draft_queued_immutable BEFORE UPDATE ON road_drafts WHEN OLD.command_id IS NOT NULL BEGIN SELECT RAISE(ABORT,'Queued draft is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER road_capture_context_immutable BEFORE UPDATE OF id,user_id,tenant_id,draft_id,document_type,field,created_at ON road_captures BEGIN SELECT RAISE(ABORT,'Capture context is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER road_capture_before_queue BEFORE INSERT ON road_captures WHEN EXISTS(SELECT 1 FROM road_drafts d WHERE d.id=NEW.draft_id AND d.command_id IS NOT NULL) BEGIN SELECT RAISE(ABORT,'Draft already queued');END''',
    );
    await customStatement(
      '''CREATE TRIGGER road_queue_after_capture BEFORE UPDATE OF command_id ON road_drafts WHEN NEW.command_id IS NOT NULL AND EXISTS(SELECT 1 FROM road_captures c WHERE c.draft_id=OLD.id AND c.state='PENDING') BEGIN SELECT RAISE(ABORT,'Capture still pending');END''',
    );
    await customStatement(
      '''CREATE TRIGGER road_camera_exclusive BEFORE INSERT ON road_captures WHEN NEW.state='PENDING' AND EXISTS(SELECT 1 FROM camera_captures WHERE state='PENDING') BEGIN SELECT RAISE(ABORT,'Camera still active');END''',
    );
    await customStatement(
      '''CREATE TRIGGER inspection_camera_exclusive BEFORE INSERT ON camera_captures WHEN NEW.state='PENDING' AND EXISTS(SELECT 1 FROM road_captures WHERE state='PENDING') BEGIN SELECT RAISE(ABORT,'Camera still active');END''',
    );
    await customStatement(
      'CREATE INDEX driver_updates_scope_order ON cached_driver_updates(user_id,tenant_id,driver_id,sequence)',
    );
  }

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
    await customStatement(
      '''CREATE TRIGGER materialized_request_matches_intent
      BEFORE INSERT ON materialized_requests
      WHEN NOT EXISTS(SELECT 1 FROM local_commands c WHERE c.client_operation_id=NEW.client_operation_id
        AND c.user_id=NEW.user_id AND c.tenant_id=NEW.tenant_id AND c.endpoint_path=NEW.endpoint_path AND c.http_method=NEW.http_method)
      BEGIN SELECT RAISE(ABORT, 'Materialized request must match original intent'); END''',
    );
  }

  Future<void> _queueIndexesAndImmutability() async {
    await customStatement(
      'CREATE INDEX IF NOT EXISTS local_commands_scope_state ON '
      'local_commands(user_id, tenant_id, state, created_at)',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS local_sync_issues_scope ON '
      'local_sync_issues(user_id, tenant_id, created_at)',
    );
    await customStatement('''CREATE TRIGGER local_command_intent_immutable
      BEFORE UPDATE OF client_operation_id,user_id,tenant_id,command_type,
      endpoint_path,http_method,payload_json,trip_id,aggregate_id,aggregate_type,
      resource_keys_json,dependencies_json,occurred_at,created_at,queue_sequence ON local_commands
      BEGIN SELECT RAISE(ABORT, 'Command intent is immutable'); END''');
  }

  Future<void> _shiftIndexesAndImmutability() async {
    await customStatement(
      '''CREATE TRIGGER capture_before_queue BEFORE INSERT ON camera_captures
      WHEN NOT EXISTS(SELECT 1 FROM inspection_drafts d WHERE d.id=NEW.draft_id AND d.user_id=NEW.user_id AND d.tenant_id=NEW.tenant_id AND d.command_id IS NULL AND d.inspection_type='PRE_TRIP')
      BEGIN SELECT RAISE(ABORT,'Only an editable Pre-Trip can capture');END''',
    );
    await customStatement('''CREATE TRIGGER queue_after_capture BEFORE UPDATE OF command_id ON inspection_drafts
      WHEN NEW.command_id IS NOT NULL AND EXISTS(SELECT 1 FROM camera_captures c WHERE c.draft_id=OLD.id AND c.state='PENDING')
      BEGIN SELECT RAISE(ABORT,'Resolve camera before queueing');END''');
    await customStatement(
      "CREATE UNIQUE INDEX camera_one_pending ON camera_captures(state) WHERE state='PENDING'",
    );
    await customStatement(
      '''CREATE TRIGGER inspection_draft_identity_immutable BEFORE UPDATE OF id,user_id,tenant_id,inspection_type,shift_reference_json,tractor_id,trip_id ON inspection_drafts BEGIN SELECT RAISE(ABORT,'Draft context is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER inspection_draft_queued_immutable BEFORE UPDATE ON inspection_drafts WHEN OLD.command_id IS NOT NULL BEGIN SELECT RAISE(ABORT,'Queued draft is immutable');END''',
    );
    await customStatement(
      '''CREATE TRIGGER camera_capture_identity_immutable BEFORE UPDATE OF id,user_id,tenant_id,draft_id,position,created_at ON camera_captures BEGIN SELECT RAISE(ABORT,'Capture context is immutable');END''',
    );
    await customStatement(
      'CREATE INDEX inspection_drafts_scope ON inspection_drafts(user_id,tenant_id,updated_at)',
    );
    await customStatement(
      'DROP TRIGGER IF EXISTS materialized_request_matches_intent',
    );
    await customStatement(
      '''CREATE TRIGGER materialized_request_matches_intent BEFORE INSERT ON materialized_requests
      WHEN NOT EXISTS(SELECT 1 FROM local_commands c WHERE c.client_operation_id=NEW.client_operation_id
        AND c.user_id=NEW.user_id AND c.tenant_id=NEW.tenant_id AND c.http_method=NEW.http_method
        AND (c.endpoint_path=NEW.endpoint_path OR (c.command_type='off-duty' AND c.aggregate_type='SHIFT'
          AND c.endpoint_path='tenants/'||c.tenant_id||'/driver/me/shifts/{shiftId}/off-duty'
          AND EXISTS(SELECT 1 FROM command_result_dependencies d JOIN command_results r ON r.client_operation_id=d.source_operation_id
            JOIN local_commands source ON source.client_operation_id=r.client_operation_id
            WHERE d.client_operation_id=c.client_operation_id AND d.user_id=c.user_id AND d.tenant_id=c.tenant_id
              AND r.user_id=c.user_id AND r.tenant_id=c.tenant_id AND r.result_kind='SHIFT' AND source.state='ACKNOWLEDGED'
              AND NEW.endpoint_path='tenants/'||c.tenant_id||'/driver/me/shifts/'||r.server_id||'/off-duty'))))
      BEGIN SELECT RAISE(ABORT,'Materialized request must match typed original intent'); END''',
    );
    for (final table in ['command_results', 'command_result_dependencies']) {
      for (final operation in ['UPDATE', 'DELETE']) {
        await customStatement(
          'CREATE TRIGGER ${table}_${operation.toLowerCase()}_immutable BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT,\'Confirmed identity and dependencies are immutable\'); END',
        );
      }
    }
    await customStatement(
      '''CREATE TRIGGER result_dependency_before_send BEFORE INSERT ON command_result_dependencies
      WHEN EXISTS(SELECT 1 FROM materialized_requests WHERE client_operation_id=NEW.client_operation_id)
        OR NOT EXISTS(SELECT 1 FROM local_commands c JOIN local_commands source ON source.client_operation_id=NEW.source_operation_id
          WHERE c.client_operation_id=NEW.client_operation_id AND c.user_id=NEW.user_id AND c.tenant_id=NEW.tenant_id
            AND c.state='QUEUED' AND c.attempt_count=0 AND source.user_id=c.user_id AND source.tenant_id=c.tenant_id
            AND source.command_type='on-duty' AND source.queue_sequence<c.queue_sequence)
      BEGIN SELECT RAISE(ABORT,'Generated identity dependency must precede sending'); END''',
    );
  }

  Future<void> initialize() async {
    await customSelect('SELECT 1').getSingle();
  }
}
