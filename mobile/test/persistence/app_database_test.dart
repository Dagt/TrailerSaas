import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';

void main() {
  test('opens SQLite and creates the exact version 2 cache schema', () async {
    final database = AppDatabase(NativeDatabase.memory());
    addTearDown(database.close);

    await database.initialize();
    final version = await database
        .customSelect('PRAGMA user_version')
        .getSingle();
    expect(version.read<int>('user_version'), 2);
    final tables = await database
        .customSelect("SELECT name FROM sqlite_master WHERE type = 'table'")
        .get();
    expect(tables.map((row) => row.read<String>('name')).toSet(), {
      'local_session_context',
      'cached_trips',
      'cached_stops',
      'cached_trailers',
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
    expect(version.read<int>('user_version'), 2);
    expect(reopened.allTables.length, 4);
  });
}
