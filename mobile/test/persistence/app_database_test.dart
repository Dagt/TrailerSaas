import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';

void main() {
  test('opens SQLite and creates the exact version 8 cache command media Shift and Yard schema', () async {
    final database = AppDatabase(NativeDatabase.memory());
    addTearDown(database.close);

    await database.initialize();
    final version = await database
        .customSelect('PRAGMA user_version')
        .getSingle();
    expect(version.read<int>('user_version'), 8);
    final tables = await database
        .customSelect("SELECT name FROM sqlite_master WHERE type = 'table'")
        .get();
    expect(tables.map((row) => row.read<String>('name')).toSet(), {
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
    });
  });

  test('persists schema version across a file close and reopen', () async {
    final directory = await Directory.systemTemp.createTemp(
      'trailersaas-test-',
    );
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/scaffold.sqlite');
    final first = AppDatabase(NativeDatabase(file));
    try {
      await first.initialize();
    } finally {
      await first.close();
    }

    expect(await file.exists(), isTrue);
    final reopened = AppDatabase(NativeDatabase(file));
    addTearDown(reopened.close);
    await reopened.initialize();
    final version = await reopened
        .customSelect('PRAGMA user_version')
        .getSingle();
    expect(version.read<int>('user_version'), 8);
    expect(reopened.allTables.length, 21);
  });
}
