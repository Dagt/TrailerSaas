import 'dart:convert';
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/road/driver_updates_store.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/session/api_client.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant, other;

void main() {
  late MediaHttpFixture f;
  late DriverUpdatesStore store;
  setUp(() async {
    f = MediaHttpFixture()..roadWorkflows = true;
    await f.start(fileDatabase: true);
    store = DriverUpdatesStore(f.db, f.session);
  });
  tearDown(() => f.close());
  Map<String, dynamic> update(String type, String text) => {
    'id': operationUuid(),
    'tripId': MediaHttpFixture.roadTrip,
    'updateType': type,
    'occurredAt': f.now.toIso8601String(),
    'payload': {
      'instructions': text,
      'reason': 'Operations update',
      'status': 'CANCELLING',
      'actorUserId': 'must-not-cache',
      'claims': {'admin': true},
    },
  };
  test('incremental cursor retains the final anchor and tail deduplication across restart', () async {
    f.driverUpdates.addAll(
      List.generate(205, (i) => update('TRIP_REROUTED', 'Instruction $i')),
    );
    await store.refresh();
    expect(await f.db.select(f.db.cachedDriverUpdates).get(), hasLength(205));
    expect(
      (await f.db.select(f.db.driverUpdateCursors).getSingle()).cursor,
      '200',
    );
    f.driverUpdates.add(update('TRIP_CANCELLATION_STARTED', 'Regresa a base'));
    await store.refresh();
    expect(await f.db.select(f.db.cachedDriverUpdates).get(), hasLength(206));
    await f.restart();
    store = DriverUpdatesStore(f.db, f.session);
    await store.refresh();
    expect(await f.db.select(f.db.cachedDriverUpdates).get(), hasLength(206));
    final stored = await f.db.select(f.db.cachedDriverUpdates).get();
    expect(stored.map((r) => r.sequence).toSet(), hasLength(206));
    expect(
      stored.map((r) => r.projectionJson).join(),
      isNot(contains('actorUserId')),
    );
    expect(
      stored.map((r) => r.projectionJson).join(),
      isNot(contains('claims')),
    );
  });
  test('reassignment removes work but targeted instructions remain accessible without push', () async {
    f.driverUpdates.add(update('TRIP_CANCELLATION_STARTED', 'Return safely'));
    await store.refresh();
    f.noTrips = true;
    await f.session.refresh();
    expect(f.session.snapshot!.trips, isEmpty);
    await store.refresh();
    expect(
      (await store.watch(user, tenant).first).single.updateType,
      'TRIP_CANCELLATION_STARTED',
    );
    expect((await store.watch(other, tenant).first), isEmpty);
  });
  test('repeated cursor fails closed without dropping committed downloaded history', () async {
    f.driverUpdates.add(update('TRIP_REROUTED', 'Known instruction'));
    await store.refresh();
    f.repeatUpdatesCursor = true;
    await expectLater(store.refresh(), throwsA(isA<FormatException>()));
    expect(await f.db.select(f.db.cachedDriverUpdates).get(), hasLength(1));
  });
  test(
    'malformed response does not advance cursor or replace retained history',
    () async {
      f.driverUpdates.add(update('TRIP_REROUTED', 'Known'));
      await store.refresh();
      f.driverUpdates.add({'id': 'invalid', 'payload': {}});
      await expectLater(store.refresh(), throwsA(isA<FormatException>()));
      expect(await f.db.select(f.db.cachedDriverUpdates).get(), hasLength(1));
      expect(
        (await f.db.select(f.db.driverUpdateCursors).getSingle()).cursor,
        isNull,
      );
    },
  );
  test(
    'unknown fields are not raw administrative data in the local projection',
    () async {
      f.driverUpdates.add(update('TRIP_REROUTED', 'Drive to Yard B'));
      await store.refresh();
      final payload = jsonDecode(
        (await f.db.select(f.db.cachedDriverUpdates).getSingle())
            .projectionJson,
      ) as Map;
      expect((payload['payload'] as Map).keys.toSet(), {
        'instructions',
        'reason',
        'status',
      });
    },
  );
  test('late targeted response after account switch cannot populate the new namespace', () async {
    f.driverUpdates.add(update('TRIP_REROUTED', 'Old account instruction'));
    f.updatesEntered = Completer<void>();
    f.updatesRelease = Completer<void>();
    final pending = store.refresh();
    await f.updatesEntered!.future;
    f.switchUser = true;
    f.auth.uid = 'firebase-B';
    await f.session.refresh();
    final discarded = expectLater(
      pending,
      throwsA(
        isA<ApiProblem>()
            .having((e) => e.status, 'status', 401)
            .having((e) => e.code, 'code', 'AUTHENTICATION_REQUIRED'),
      ),
    );
    f.updatesRelease!.complete();
    await discarded;
    expect(await store.watch(other, tenant).first, isEmpty);
    expect(await f.db.select(f.db.cachedDriverUpdates).get(), isEmpty);
    expect(await f.db.select(f.db.driverUpdateCursors).get(), isEmpty);
  });
  test('403 durably hides downloaded instructions until authorized refresh even after restart', () async {
    f.driverUpdates.add(update('TRIP_REROUTED', 'Old authorized instruction'));
    await store.refresh();
    f.updatesStatus = 403;
    await expectLater(
      store.refresh(),
      throwsA(isA<ApiProblem>().having((e) => e.status, 'status', 403)),
    );
    expect(await store.watch(user, tenant).first, isEmpty);
    expect(
      (await f.db.select(f.db.driverUpdateCursors).getSingle()).accessBlocked,
      isTrue,
    );
    await f.restart();
    store = DriverUpdatesStore(f.db, f.session);
    expect(await store.watch(user, tenant).first, isEmpty);
    f.updatesStatus = null;
    await store.refresh();
    expect(await store.watch(user, tenant).first, hasLength(1));
    expect(
      (await f.db.select(f.db.driverUpdateCursors).getSingle()).accessBlocked,
      isFalse,
    );
  });
  test('a changed own Driver profile cannot reuse another Driver cursor or instruction cache', () async {
    f.driverUpdates.addAll(
      List.generate(205, (i) => update('TRIP_REROUTED', 'Old Driver $i')),
    );
    await store.refresh();
    expect(
      (await f.db.select(f.db.driverUpdateCursors).getSingle()).cursor,
      '200',
    );
    f.driverIdentityOverride = '99999999-9999-4999-8999-999999999999';
    f.driverUpdates.clear();
    f.driverUpdates.add(update('TRIP_REROUTED', 'Current Driver instruction'));
    await f.session.refresh();
    expect(await store.watch(user, tenant).first, isEmpty);
    await store.refresh();
    expect(
      (await store.watch(user, tenant).first).single.projectionJson,
      contains('Current Driver instruction'),
    );
    expect(await f.db.select(f.db.driverUpdateCursors).get(), hasLength(2));
    expect(await f.db.select(f.db.cachedDriverUpdates).get(), hasLength(206));
  });
}
