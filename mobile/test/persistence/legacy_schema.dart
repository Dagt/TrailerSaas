import 'dart:io';

import 'package:trailersaas_mobile/src/persistence/app_database.dart';

final Map<String, String> publishedV7Tables = {
  for (final match in RegExp(
    r'CREATE TABLE\s+"?([a-z_]+)"?[\s\S]*?;',
    multiLine: true,
  ).allMatches(File('test/persistence/schema_v7.sql').readAsStringSync()))
    match.group(1)!: match.group(0)!,
};
Future<void> createPublishedV7Table(AppDatabase database, String name) =>
    database.customStatement(
      publishedV7Tables[name] ??
          (throw StateError('No published v7 table $name')),
    );
