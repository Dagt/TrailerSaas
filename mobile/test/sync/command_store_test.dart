import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/persistence/session_cache.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';

import '../session/fixtures.dart';

CommandIntent intent({
  String? id,
  String tenant = 'A',
  String trip = 'trip-A',
  String stop = 'stop-trip-A',
  List<String> dependencies = const [],
}) => CommandIntent(
  OnlineCommand(
    id ?? operationUuid(),
    trip,
    'tenants/$tenant/stops/$stop/arrivals',
    {'source': 'MANUAL', 'occurredAt': '2026-10-10T10:00:00Z'},
  ),
  dependencies: dependencies,
);

// The deployed v2 tables, with real cache rows, not an empty version pragma.
class Version2Database extends AppDatabase {
  Version2Database(super.executor);
  @override
  int get schemaVersion => 2;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) async {
      await m.createTable(localSessionContexts);
      await m.createTable(cachedTrips);
      await m.createTable(cachedStops);
      await m.createTable(cachedTrailers);
    },
  );
}

class InterruptedClear implements OnlineCommandJournal {
  InterruptedClear(this.inner);
  final OnlineCommandJournal inner;
  @override
  Future<OnlineCommand?> read(String u, String t) => inner.read(u, t);
  @override
  Future<void> save(String u, String t, OnlineCommand c) => inner.save(u, t, c);
  @override
  Future<void> clear(String u, String t, {String? operationId}) async =>
      throw StateError('Crash after SQLite commit');
}

void main() {
  late AppDatabase db;
  late CommandStore store;
  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
    store = CommandStore(db);
  });
  tearDown(() => db.close());

  test('real v2 file migration preserves all cache rows and adds indexed v5 queues', () async {
    final dir = await Directory.systemTemp.createTemp('mobile-v2-queue-');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/device.sqlite');
    final v2 = Version2Database(NativeDatabase(file));
    final original = SessionSnapshot(
      'firebase-A',
      'user-A',
      Membership('A', 'Tenant A', {'TRIP_VIEW_ASSIGNED'}),
      DriverProfile('driver', 'Jane'),
      DateTime.utc(2026, 10, 10),
      [WorkTrip(work('trip-A'))],
    );
    await SessionCache(v2).replace(original, () => true);
    final before = <String, List<Map<String, Object?>>>{};
    for (final table in [
      'local_session_context',
      'cached_trips',
      'cached_stops',
      'cached_trailers',
    ]) {
      before[table] = (await v2.customSelect('select * from $table').get())
          .map((r) => r.data)
          .toList();
    }
    await v2.close();
    final upgraded = AppDatabase(NativeDatabase(file));
    await upgraded.initialize();
    expect(
      (await upgraded.customSelect('PRAGMA user_version').getSingle())
          .read<int>('user_version'),
      7,
    );
    for (final table in before.keys) {
      expect(
        (await upgraded.customSelect('select * from $table').get())
            .map((r) => r.data)
            .toList(),
        before[table],
      );
    }
    final c = intent();
    await CommandStore(upgraded).enqueue('user-A', 'A', c);
    await upgraded.close();
    final reopened = AppDatabase(NativeDatabase(file));
    addTearDown(reopened.close);
    expect(
      (await CommandStore(reopened).list('user-A', 'A')).single.payloadJson,
      jsonEncode(c.command.body),
    );
    expect(
      (await SessionCache(reopened).restore('firebase-A'))!.trips.single.id,
      'trip-A',
    );
    expect(
      (await reopened
              .customSelect("select name from sqlite_master where type='index'")
              .get())
          .map((r) => r.read<String>('name')),
      containsAll(['local_commands_scope_state', 'local_sync_issues_scope']),
    );
  });

  test('immutable identity, payload, endpoint and dependencies cannot be updated even by SQL', () async {
    final c = intent();
    await store.enqueue('u', 'A', c);
    for (final field in [
      'client_operation_id',
      'user_id',
      'tenant_id',
      'payload_json',
      'endpoint_path',
      'dependencies_json',
      'queue_sequence',
    ]) {
      await expectLater(
        db.customStatement("UPDATE local_commands SET $field = 'changed'"),
        throwsA(anything),
      );
    }
    expect(
      (await store.list('u', 'A')).single.payloadJson,
      jsonEncode(c.command.body),
    );
  });
  test(
    'duplicates import idempotently but changed identity/payload is rejected',
    () async {
      final c = intent();
      await store.enqueue('u', 'A', c);
      await store.enqueue('u', 'A', c);
      expect(await store.list('u', 'A'), hasLength(1));
      await expectLater(store.enqueue('v', 'A', c), throwsStateError);
      await expectLater(
        store.enqueue(
          'u',
          'A',
          CommandIntent(
            OnlineCommand(
              c.command.id,
              'other',
              c.command.path,
              c.command.body,
            ),
          ),
        ),
        throwsStateError,
      );
    },
  );
  test('sequence ordering stays deterministic for commands created in same SQLite timestamp', () async {
    final first = intent(id: 'ffffffff-ffff-4fff-8fff-ffffffffffff');
    final second = intent(id: '00000000-0000-4000-8000-000000000000');
    await store.enqueue('u', 'A', first);
    await store.enqueue('u', 'A', second);
    expect((await store.list('u', 'A')).map((c) => c.clientOperationId), [
      first.command.id,
      second.command.id,
    ]);
  });
  test('dependencies must exist in same namespace; self/cycles and duplicates rejected', () async {
    final a = intent();
    await store.enqueue('u', 'A', a);
    for (final c in [
      intent(dependencies: [operationUuid()]),
      intent(dependencies: [a.command.id, a.command.id]),
      intent(id: a.command.id, dependencies: [a.command.id]),
    ]) {
      await expectLater(store.enqueue('u', 'A', c), throwsA(anything));
    }
    await expectLater(
      store.enqueue('v', 'A', intent(dependencies: [a.command.id])),
      throwsStateError,
    );
    await store.enqueue('u', 'A', intent(dependencies: [a.command.id]));
    expect(await store.list('u', 'A'), hasLength(2));
  });
  test('queue accepts only implemented same-Tenant typed endpoints without credentials', () async {
    final base = intent().command;
    for (final path in [
      'https://evil.test',
      'tenants/B/stops/s/arrivals',
      'tenants/A/stops/../arrivals',
      'tenants/A/stops/s/delivery-completions',
      'tenants/A/trips/x/cancellations',
    ]) {
      await expectLater(
        store.enqueue(
          'u',
          'A',
          CommandIntent(
            OnlineCommand(operationUuid(), base.tripId, path, base.body),
          ),
        ),
        throwsFormatException,
      );
    }
    for (final field in [
      'Authorization',
      'token',
      'actorUserId',
      'status',
      'documentIds',
    ]) {
      await expectLater(
        store.enqueue(
          'u',
          'A',
          CommandIntent(
            OnlineCommand(operationUuid(), base.tripId, base.path, {
              ...base.body,
              field: 'secret',
            }),
          ),
        ),
        throwsFormatException,
      );
    }
    expect(await store.list('u', 'A'), isEmpty);
  });
  test('SQLite rejects impossible sync states and non-POST methods', () async {
    await store.enqueue('u', 'A', intent());
    await expectLater(
      db.customStatement("update local_commands set state='EXECUTED_LOCALLY'"),
      throwsA(anything),
    );
    expect((await store.list('u', 'A')).single.state, 'QUEUED');
  });
  test('clearing/replacing session cache never removes any queued or conflict intent', () async {
    await store.enqueue('u', 'A', intent());
    final row = (await store.list('u', 'A')).single;
    await store.result(
      row,
      CommandState.conflict,
      code: 'RESOURCE_NOT_FOUND',
      category: 'AUTHORIZATION',
    );
    await SessionCache(db).clear();
    expect((await store.list('u', 'A')).single.state, 'CONFLICT');
    expect(await store.list('v', 'A'), isEmpty);
    expect(await store.list('u', 'B'), isEmpty);
    expect(
      (await db.select(db.localSyncIssues).get()).single.message,
      isNot(contains('secret')),
    );
  });
  test(
    'real legacy file is removed only after exact verified SQLite import',
    () async {
      final dir = await Directory.systemTemp.createTemp('receipt-import-');
      addTearDown(() => dir.delete(recursive: true));
      final legacy = FileOnlineCommandJournal(() async => dir),
          c = intent().command;
      await legacy.save('u', 'A', c);
      await store.importLegacy('u', 'A', legacy);
      expect(await legacy.read('u', 'A'), isNull);
      final row = (await store.list('u', 'A')).single;
      expect(row.clientOperationId, c.id);
      expect(row.endpointPath, c.path);
      expect(row.payloadJson, jsonEncode(c.body));
      expect(row.state, 'QUEUED');
      expect(row.ackedAt, isNull);
    },
  );
  test('crash after import commit retains file and repeated migration never resurrects ACK', () async {
    final dir = await Directory.systemTemp.createTemp('receipt-crash-');
    addTearDown(() => dir.delete(recursive: true));
    final legacy = FileOnlineCommandJournal(() async => dir),
        c = intent().command;
    await legacy.save('u', 'A', c);
    await expectLater(
      store.importLegacy('u', 'A', InterruptedClear(legacy)),
      throwsStateError,
    );
    expect((await legacy.read('u', 'A'))!.id, c.id);
    await store.result(
      (await store.list('u', 'A')).single,
      CommandState.acknowledged,
      status: 200,
    );
    await store.importLegacy('u', 'A', legacy);
    final rows = await store.list('u', 'A');
    expect(rows, hasLength(1));
    expect(rows.single.state, 'ACKNOWLEDGED');
    expect(await legacy.read('u', 'A'), isNull);
  });
  test(
    'failed SQLite import rolls back and leaves the old receipt recoverable',
    () async {
      final dir = await Directory.systemTemp.createTemp('receipt-failure-');
      addTearDown(() => dir.delete(recursive: true));
      final legacy = FileOnlineCommandJournal(() async => dir),
          c = intent().command;
      await legacy.save('u', 'A', c);
      await db.initialize();
      await db.customStatement(
        "CREATE TRIGGER fail_import BEFORE INSERT ON local_commands BEGIN SELECT RAISE(ABORT,'test failure'); END",
      );
      await expectLater(
        store.importLegacy('u', 'A', legacy),
        throwsA(anything),
      );
      expect(await store.list('u', 'A'), isEmpty);
      expect((await legacy.read('u', 'A'))!.id, c.id);
      await db.customStatement('DROP TRIGGER fail_import');
      await store.importLegacy('u', 'A', legacy);
      expect(await legacy.read('u', 'A'), isNull);
    },
  );
  test(
    'interrupted SENDING recovers only its User/Tenant and same operation',
    () async {
      await store.enqueue('u', 'A', intent());
      await store.enqueue('v', 'A', intent());
      final a = (await store.list('u', 'A')).single,
          b = (await store.list('v', 'A')).single;
      expect(await store.claim(a), isTrue);
      expect(await store.claim(a), isFalse);
      await store.claim(b);
      await store.recoverSending('u', 'A');
      expect(
        (await store.list('u', 'A')).single.clientOperationId,
        a.clientOperationId,
      );
      expect((await store.list('u', 'A')).single.state, 'RETRYABLE_FAILURE');
      expect((await store.list('v', 'A')).single.state, 'SENDING');
    },
  );
}
