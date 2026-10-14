import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqlite3/sqlite3.dart' as sqlite;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/persistence/session_cache.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';

import '../session/fixtures.dart';

void main() {
  SessionSnapshot snapshot(String uid, String user, String tenant) =>
      SessionSnapshot(
        uid,
        user,
        Membership(tenant, 'Tenant $tenant', {'TRIP_VIEW_ASSIGNED'}),
        DriverProfile('driver', 'Driver'),
        DateTime.utc(2026, 10, 10),
        [WorkTrip(work('trip-$tenant'))],
      );
  test('real SQLite v1 upgrades to twenty-six exact cache command media Shift and Yard tables', () async {
    final dir = await Directory.systemTemp.createTemp('mobile-v1-');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/v1.sqlite');
    // Seed the actual empty deployed v1 schema before Drift opens/version checks it.
    final v1 = sqlite.sqlite3.open(file.path);
    v1.execute('PRAGMA user_version=1');
    v1.close();
    final upgraded = AppDatabase(NativeDatabase(file));
    addTearDown(upgraded.close);
    await upgraded.initialize();
    expect(
      (await upgraded.customSelect('PRAGMA user_version').getSingle())
          .read<int>('user_version'),
      9,
    );
    final tables =
        (await upgraded
                .customSelect(
                  "select name from sqlite_master where type='table'",
                )
                .get())
            .map((r) => r.read<String>('name'))
            .toSet();
    expect(tables, {
      'local_session_context',
      'cached_trips',
      'cached_stops',
      'cached_trailers',
      'local_commands',
      'local_sync_issues',
      'local_media',
      'command_media_dependencies',
      'materialized_requests',
      'command_results',
      'command_result_dependencies',
      'inspection_drafts',
      'camera_captures',
      'road_drafts',
      'road_captures',
      'cached_driver_updates',
      'driver_update_cursors',
      'cached_yard_contexts',
      'yard_drafts',
      'yard_captures',
      'yard_conflict_reviews',
      'cached_yard_checks',
      'cached_yard_check_items',
      'yard_check_observations',
      'yard_check_reviews',
      'yard_check_directories',
    });
  });
  test('file reopen restores only matching Firebase User namespace', () async {
    final dir = await Directory.systemTemp.createTemp('mobile-cache-');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/cache.sqlite');
    final db = AppDatabase(NativeDatabase(file));
    final cache = SessionCache(db);
    await cache.replace(snapshot('firebase-A', 'user-A', 'A'), () => true);
    await db.close();
    final reopened = AppDatabase(NativeDatabase(file));
    addTearDown(reopened.close);
    final restored = SessionCache(reopened);
    expect(
      (await restored.restore('firebase-A'))!
          .trips
          .single
          .stops
          .single['siteName'],
      'Dock 17',
    );
    expect(await restored.restore('firebase-B'), isNull);
    expect(
      (await restored.restore('firebase-A'))!
          .trips
          .single
          .plannedTrailer!['operationalNumber'],
      'TR-123',
    );
  });
  test(
    'same resource IDs remain isolated by User/Tenant composite key',
    () async {
      final db = AppDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final cache = SessionCache(db);
      await cache.replace(snapshot('firebase-A', 'user-A', 'A'), () => true);
      await cache.replace(snapshot('firebase-B', 'user-B', 'B'), () => true);
      expect((await cache.restore('firebase-A'))!.membership.tenantId, 'A');
      expect((await cache.restore('firebase-B'))!.membership.tenantId, 'B');
      expect(await db.select(db.cachedTrailers).get().then((r) => r.length), 2);
      await cache.clear();
      expect(await cache.restore('firebase-A'), isNull);
      expect(await db.select(db.cachedStops).get(), isEmpty);
    },
  );
  test(
    'failed authoritative replacement rolls back the entire prior cache',
    () async {
      final db = AppDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final cache = SessionCache(db);
      await cache.replace(snapshot('firebase-A', 'user-A', 'A'), () => true);
      await db.customStatement(
        "CREATE TRIGGER fail_cache BEFORE INSERT ON cached_stops BEGIN SELECT RAISE(ABORT,'controlled'); END",
      );
      await expectLater(
        cache.replace(snapshot('firebase-A', 'user-A', 'A'), () => true),
        throwsA(isA<Exception>()),
      );
      expect((await cache.restore('firebase-A'))!.membership.tenantId, 'A');
      expect(await db.select(db.cachedTrips).get().then((r) => r.length), 1);
      expect(await db.select(db.cachedStops).get().then((r) => r.length), 1);
      expect(await db.select(db.cachedTrailers).get().then((r) => r.length), 1);
    },
  );
  test('stale generation cannot write any row and credentials have no storage column; only opaque public QR observation is allowed', () async {
    final db = AppDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final cache = SessionCache(db);
    await cache.replace(snapshot('firebase-A', 'user-A', 'A'), () => false);
    expect(await cache.restore('firebase-A'), isNull);
    for (final table in db.allTables) {
      expect(
        table.$columns
            .where(
              (c) =>
                  !(table.actualTableName == 'yard_check_observations' &&
                      c.$name == 'public_token'),
            )
            .map((c) => c.$name)
            .join(','),
        isNot(contains('token')),
      );
      expect(
        table.$columns.map((c) => c.$name).join(','),
        isNot(contains('password')),
      );
    }
  });
}
