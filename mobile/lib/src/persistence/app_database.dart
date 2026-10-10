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

@DriftDatabase(
  tables: [LocalSessionContexts, CachedTrips, CachedStops, CachedTrailers],
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
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) => migrator.createAll(),
    onUpgrade: (migrator, from, to) async {
      // v1 was the empty scaffold. Its deployed schema remains an explicit upgrade source.
      if (from == 1 && to == 2) {
        await migrator.createTable(localSessionContexts);
        await migrator.createTable(cachedTrips);
        await migrator.createTable(cachedStops);
        await migrator.createTable(cachedTrailers);
      } else {
        throw StateError('No migration from schema $from to $to is defined.');
      }
    },
  );

  Future<void> initialize() async {
    await customSelect('SELECT 1').getSingle();
  }
}
