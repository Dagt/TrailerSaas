import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/media/media_dependencies.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';

import 'media_http_fixture.dart';
import 'media_storage_test.dart' show user, tenant, other;
import '../sync/command_store_test.dart' show intent;

void main() {
  late MediaHttpFixture f;
  late MediaDependencies bindings;
  setUp(() async {
    f = MediaHttpFixture();
    await f.start();
    bindings = MediaDependencies(f.worker.store);
  });
  tearDown(() => f.close());
  EvidenceCommandIntent draft(
    EvidenceCommandKind kind,
    Map<String, dynamic> refs, {
    String? id,
  }) => EvidenceCommandIntent(
    kind,
    OnlineCommand(
      id ?? operationUuid(),
      'trip-A',
      EvidenceCommandIntent.path(kind, tenant, 'trip-A', 'stop-trip-A'),
      {
        if (kind == EvidenceCommandKind.preTrip) ...{
          'shiftId': user,
          'tripId': 'trip-A',
          'tractorId': other,
          'odometer': 42,
          'checklist': {'items': []},
        },
        if (kind == EvidenceCommandKind.fuel) ...{
          'tripId': 'trip-A',
          'tractorId': other,
          'odometer': 42,
        },
        if (kind == EvidenceCommandKind.damage) ...{
          'category': 'EQUIPMENT_DAMAGE',
          'comment': 'Damage observed',
        },
        if (kind == EvidenceCommandKind.signedBol) 'evidenceMode': 'SIGNED_BOL',
        if (kind == EvidenceCommandKind.noReceiver) ...{
          'evidenceMode': 'NO_RECEIVER_AVAILABLE',
          'comment': 'No receiving staff',
        },
        ...refs,
        'occurredAt': DateTime.now()
            .toUtc()
            .subtract(const Duration(minutes: 1))
            .toIso8601String(),
      },
    ),
  );
  Future<LocalCommand> enqueue(EvidenceCommandIntent c) async {
    await bindings.enqueue(user, tenant, c);
    return (await f.worker.store.find(user, tenant, c.command.id))!;
  }

  test('four required photos block Pre-Trip until all finalized and future endpoint remains disabled', () async {
    final refs = <String, String>{};
    for (final entry in {
      'front': 'PRETRIP_FRONT',
      'rear': 'PRETRIP_REAR',
      'driverSide': 'PRETRIP_DRIVER_SIDE',
      'passengerSide': 'PRETRIP_PASSENGER_SIDE',
    }.entries) {
      refs[entry.key] = await f.capture(entry.value);
    }
    final c = await enqueue(
      draft(EvidenceCommandKind.preTrip, {'documentIds': refs}),
    );
    expect(await bindings.ready(c), isFalse);
    expect(await bindings.materialize(c), isNull);
    await f.worker.sync();
    expect(await bindings.ready(c), isTrue);
    expect(
      (await f.worker.store.find(user, tenant, c.clientOperationId))!.state,
      'BLOCKED',
    );
    expect(
      (await f.worker.store.find(
        user,
        tenant,
        c.clientOperationId,
      ))!.lastErrorCode,
      'FUTURE_WORKFLOW',
    );
    expect(f.businessEffects, 0);
    expect(f.posts.where((p) => p.path.endsWith('pre-trip')), isEmpty);
  });
  for (final kind in [
    EvidenceCommandKind.signedBol,
    EvidenceCommandKind.noReceiver,
    EvidenceCommandKind.fuel,
    EvidenceCommandKind.damage,
  ]) {
    test(
      '$kind resolves exact finalized IDs without modifying logical intent',
      () async {
        final refs = switch (kind) {
          EvidenceCommandKind.signedBol => {'bolDocumentId': await f.capture()},
          EvidenceCommandKind.noReceiver => {
            'bolDocumentId': await f.capture(),
            'deliveryPhotoDocumentId': await f.capture('DELIVERY_PHOTO'),
          },
          EvidenceCommandKind.fuel => {
            'receiptDocumentId': await f.capture('FUEL_RECEIPT'),
          },
          _ => {
            'documentIds': [await f.capture('DAMAGE_PHOTO')],
          },
        };
        final c = await enqueue(draft(kind, refs));
        await f.worker.sync();
        final body = (await bindings.materialize(c))!;
        expect(
          (await f.worker.store.find(
            user,
            tenant,
            c.clientOperationId,
          ))!.payloadJson,
          c.payloadJson,
        );
        for (final local in await f.media.list(user, tenant)) {
          expect(jsonEncode(body), contains(local.serverDocumentId!));
          expect(jsonEncode(body), isNot(contains(local.id)));
        }
        await expectLater(
          f.db.customStatement(
            "UPDATE materialized_requests SET payload_json='{}' WHERE client_operation_id=?",
            [c.clientOperationId],
          ),
          throwsA(isA<Exception>()),
        );
        expect(await bindings.materialize(c), body);
      },
    );
  }
  test('missing duplicate wrong-type or foreign media roll back intent and all edges', () async {
    final bol = await f.capture();
    for (final refs in [
      {'bolDocumentId': other, 'deliveryPhotoDocumentId': bol},
      {'bolDocumentId': bol, 'deliveryPhotoDocumentId': bol},
    ]) {
      await expectLater(
        bindings.enqueue(
          user,
          tenant,
          draft(EvidenceCommandKind.noReceiver, refs),
        ),
        throwsA(isA<Exception>()),
      );
    }
    await expectLater(
      bindings.enqueue(
        other,
        tenant,
        draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
      ),
      throwsA(isA<Exception>()),
    );
    await expectLater(
      bindings.enqueue(
        user,
        other,
        draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
      ),
      throwsA(isA<Exception>()),
    );
    expect(await f.worker.store.list(user, tenant), isEmpty);
    expect(await f.db.select(f.db.commandMediaDependencies).get(), isEmpty);
  });
  test('SQLite structural FK prevents cross-namespace edges even outside Dart validation', () async {
    final bol = await f.capture(), plain = intent(tenant: tenant);
    await f.worker.store.enqueue(other, tenant, plain);
    await expectLater(
      f.db
          .into(f.db.commandMediaDependencies)
          .insert(
            CommandMediaDependenciesCompanion.insert(
              clientOperationId: plain.command.id,
              userId: other,
              tenantId: tenant,
              mediaId: bol,
              fieldPath: 'bolDocumentId',
            ),
          ),
      throwsA(isA<Exception>()),
    );
  });
  test(
    'failed media blocks its chain while a separate Trip command continues',
    () async {
      final bol = await f.capture();
      final c = await enqueue(
        draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
      );
      final row = (await f.media.find(user, tenant, bol))!;
      await f.media.failed(row, 'FILE_MISSING', review: true);
      final independent = intent(
        tenant: tenant,
        trip: 'trip-B',
        stop: 'stop-trip-B',
      );
      await f.worker.store.enqueue(user, tenant, independent);
      await f.worker.sync();
      expect(
        (await f.worker.store.find(user, tenant, c.clientOperationId))!.state,
        'BLOCKED',
      );
      expect(
        f.posts.any((p) => p.path.endsWith('delivery-completions')),
        isFalse,
      );
      expect(f.posts.any((p) => p.path.contains('stop-trip-B')), isTrue);
      expect(
        (await f.worker.store.find(
          user,
          tenant,
          independent.command.id,
        ))!.state,
        'ACKNOWLEDGED',
      );
    },
  );
  test('lost business ACK replays exact frozen payload and key after media finalization', () async {
    final bol = await f.capture();
    final c = await enqueue(
      draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
    );
    await f.worker.shutdown();
    f.worker = f.makeWorker(futurePort: true);
    f.loseBusinessAck = true;
    await f.worker.sync();
    expect(f.businessEffects, 1);
    final frozen = (await bindings.materialize(c))!;
    f.now = f.now.add(const Duration(minutes: 2));
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, c.clientOperationId))!.state,
      'ACKNOWLEDGED',
    );
    expect(f.businessEffects, 1);
    expect(f.finalizeEffects, 1);
    expect(f.puts, 1);
    final posts = f.posts
        .where((p) => p.path.endsWith('delivery-completions'))
        .toList();
    expect(posts, hasLength(2));
    expect(posts[0].key, c.clientOperationId);
    expect(posts[1].key, c.clientOperationId);
    expect(posts[0].body, frozen);
    expect(posts[1].body, frozen);
  });
  test('crash after materialization before POST preserves request across another worker', () async {
    final bol = await f.capture();
    final c = await enqueue(
      draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
    );
    await f.worker.sync();
    final frozen = (await bindings.materialize(c))!;
    expect(f.businessEffects, 0);
    await f.worker.shutdown();
    f.worker = f.makeWorker(futurePort: true);
    await f.worker.sync();
    expect(f.posts.last.body, frozen);
    expect(f.posts.last.key, c.clientOperationId);
    expect(
      (await f.worker.store.find(user, tenant, c.clientOperationId))!.state,
      'ACKNOWLEDGED',
    );
  });
  test('frozen request prohibits later document identity and dependency replacement', () async {
    final bol = await f.capture();
    final c = await enqueue(
      draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
    );
    await f.worker.sync();
    await bindings.materialize(c);
    final row = (await f.media.find(user, tenant, bol))!;
    await expectLater(f.media.newAttempt(row), throwsA(isA<Exception>()));
    await expectLater(
      f.db.customStatement(
        'DELETE FROM command_media_dependencies WHERE client_operation_id=?',
        [c.clientOperationId],
      ),
      throwsA(isA<Exception>()),
    );
    await expectLater(
      f.db.customStatement(
        'UPDATE local_media SET server_document_id=? WHERE id=?',
        [other, bol],
      ),
      throwsA(isA<Exception>()),
    );
  });
  test('cleanup requires final confirmation ACK grace and explicit offline release', () async {
    final bol = await f.capture();
    final c = await enqueue(
      draft(EvidenceCommandKind.signedBol, {'bolDocumentId': bol}),
    );
    await f.worker.sync();
    f.now = f.now.add(const Duration(days: 8));
    expect(await f.media.cleanup(user, tenant), 0);
    await f.media.releaseOfflineUse(user, tenant, bol);
    expect(await f.media.cleanup(user, tenant), 0);
    await f.worker.store.result(c, CommandState.acknowledged);
    expect(await f.media.cleanup(other, tenant), 0);
    expect(await f.media.cleanup(user, tenant), 1);
    final row = (await f.media.find(user, tenant, bol))!;
    expect(row.state, 'FINALIZED');
    expect(row.fileState, 'REMOVED');
    expect(row.serverDocumentId, isNotNull);
    expect(await f.files.inventory(user, tenant), isEmpty);
    expect(await f.media.cleanup(user, tenant), 0);
  });
  test('cleanup interrupted after physical removal recovers tombstone without resubmitting', () async {
    final bol = await f.capture();
    await f.worker.sync();
    f.now = f.now.add(const Duration(days: 8));
    await f.media.releaseOfflineUse(user, tenant, bol);
    await f.db.customStatement(
      "UPDATE local_media SET file_state='REMOVING' WHERE id=?",
      [bol],
    );
    await f.files.remove(user, tenant, bol);
    final puts = f.puts;
    expect(await f.media.cleanup(user, tenant), 1);
    await f.worker.sync();
    expect(f.puts, puts);
    expect((await f.media.find(user, tenant, bol))!.fileState, 'REMOVED');
  });
}
