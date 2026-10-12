import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/shift/shift_commands.dart';
import 'package:trailersaas_mobile/src/shift/inspection_store.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant, other;

const tractor = '44444444-4444-4444-8444-444444444444';
void main() {
  late MediaHttpFixture f;
  setUp(() async {
    f = MediaHttpFixture()..shiftWorkflows = true;
    await f.start(fileDatabase: true);
  });
  tearDown(() => f.close());
  ShiftReplay getReplay() => f.worker.shifts!;
  Future<String> onDuty() async {
    final id = operationUuid();
    await getReplay().enqueue(
      user,
      tenant,
      ShiftCommandIntent(
        ShiftAction.onDuty,
        OnlineCommand(
          id,
          null,
          ShiftCommandIntent.path(ShiftAction.onDuty, tenant),
          {
            'occurredAt': f.now
                .subtract(const Duration(seconds: 10))
                .toIso8601String(),
          },
        ),
      ),
    );
    return id;
  }

  Future<Map<String, String>> photos() async {
    final result = <String, String>{};
    for (final entry in {
      'front': 'PRETRIP_FRONT',
      'rear': 'PRETRIP_REAR',
      'driverSide': 'PRETRIP_DRIVER_SIDE',
      'passengerSide': 'PRETRIP_PASSENGER_SIDE',
    }.entries) {
      result[entry.key] = await f.media.capture(
        f.session,
        entry.value,
        'image/jpeg',
        Stream.value([255, 216, 255, 1]),
      );
    }
    return result;
  }

  Future<String> pre(String source, Map<String, String> photos) async {
    final id = operationUuid();
    await getReplay().enqueue(
      user,
      tenant,
      ShiftPreTripIntent(
        OnlineCommand(
          id,
          null,
          ShiftCommandIntent.path(ShiftAction.preTrip, tenant),
          {
            'shiftId': ShiftReference.pending(source).toJson(),
            'tripId': null,
            'tractorId': tractor,
            'odometer': 100,
            'checklist': {'items': []},
            'documentIds': photos,
            'occurredAt': f.now
                .subtract(const Duration(seconds: 5))
                .toIso8601String(),
          },
        ),
        dependencies: [source],
        resources: ['tractor:$tractor'],
      ),
    );
    return id;
  }

  test('offline On Duty and four-photo Pre-Trip materialize a REAL confirmed Shift ID without Trip', () async {
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    expect(f.session.state, SessionState.offline);
    final source = await onDuty(),
        images = await photos(),
        inspection = await pre(source, images);
    await f.worker.sync();
    expect(f.posts, isEmpty);
    expect(f.serverShift, isNull);
    f.network.status = ConnectivityStatus.available;
    await f.worker.sync();
    final rows = await f.worker.store.list(user, tenant);
    expect(rows.map((c) => c.state), everyElement('ACKNOWLEDGED'));
    expect(rows.map((c) => c.tripId), everyElement(isNull));
    final result = (await f.db.select(f.db.commandResults).get()).singleWhere(
      (r) => r.clientOperationId == source,
    );
    final frozen = (await getReplay().frozen(
      rows.singleWhere((c) => c.clientOperationId == inspection),
    ))!;
    final body = jsonDecode(frozen.payloadJson) as Map;
    expect(body['shiftId'], result.serverId);
    expect(body['shiftId'], isNot(source));
    expect(
      (body['documentIds'] as Map).values,
      everyElement(isNot(isIn(images.values))),
    );
    expect(f.puts, 4);
    expect(f.finalizeEffects, 4);
    expect(f.serverInspections, hasLength(1));
    expect(jsonDecode(rows.last.payloadJson)['shiftId'], {
      'operationId': source,
    });
    expect(f.businessEffects, 2);
  });
  test('lost On Duty ACK keeps generated identity blocked until original key recovers', () async {
    final source = await onDuty(),
        inspection = await pre(source, await photos());
    f.loseBusinessAck = true;
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, source))!.state,
      'RETRYABLE_FAILURE',
    );
    expect(
      (await f.worker.store.find(user, tenant, inspection))!.state,
      'BLOCKED',
    );
    expect(f.serverInspections, isEmpty);
    expect(await f.db.select(f.db.commandResults).get(), isEmpty);
    final remote = f.serverShift!['id'];
    f.now = f.now.add(const Duration(seconds: 10));
    await f.restart();
    await f.worker.sync();
    expect(f.serverShift!['id'], remote);
    expect(f.businessEffects, 2);
    expect(f.serverInspections, hasLength(1));
    final posts = f.posts.where((p) => p.path.endsWith('/on-duty')).toList();
    expect(posts, hasLength(2));
    expect(posts.map((p) => p.key), everyElement(source));
    expect(posts[0].raw, posts[1].raw);
  });
  test('an incomplete media set blocks only its inspection and never claims completion', () async {
    final source = await onDuty(), images = await photos();
    final first = (await f.media.find(user, tenant, images['front']!))!;
    await f.media.failed(first, 'FILE_MISSING', review: true);
    final inspection = await pre(source, images);
    await f.worker.sync();
    expect(
      (await f.worker.store.find(user, tenant, source))!.state,
      'ACKNOWLEDGED',
    );
    expect(
      (await f.worker.store.find(user, tenant, inspection))!.state,
      'BLOCKED',
    );
    expect(f.serverInspections, isEmpty);
    expect(await f.media.find(user, tenant, images['front']!), isNotNull);
  });
  test('Pre-Trip lost ACK reuses exactly the frozen body Document IDs and key after restart', () async {
    final source = await onDuty();
    await f.worker.sync();
    final inspection = await pre(source, await photos());
    f.loseBusinessAck = true;
    await f.worker.sync();
    final c = (await f.worker.store.find(user, tenant, inspection))!,
        request = (await getReplay().frozen(c))!;
    expect(c.state, 'RETRYABLE_FAILURE');
    expect(f.serverInspections, hasLength(1));
    f.now = f.now.add(const Duration(seconds: 10));
    await f.restart();
    await f.worker.sync();
    expect(f.serverInspections, hasLength(1));
    expect(f.businessEffects, 2);
    expect(f.puts, 4);
    final sent = f.posts.where((p) => p.path.endsWith('/pre-trip')).toList();
    expect(sent, hasLength(2));
    expect(sent.map((p) => p.key), everyElement(inspection));
    expect(sent.map((p) => p.raw), everyElement(request.payloadJson));
    await expectLater(
      f.db.customStatement(
        "UPDATE materialized_requests SET payload_json='{}' WHERE client_operation_id=?",
        [inspection],
      ),
      throwsA(isA<Exception>()),
    );
  });
  test('Post-Trip and Off Duty offline wait for current dependencies without automatically closing', () async {
    final source = await onDuty();
    await pre(source, await photos());
    await f.worker.sync();
    f.noTrips = true;
    await f.session.refresh();
    f.network.status = ConnectivityStatus.unavailable;
    await f.session.refresh();
    final reference = ShiftReference.pending(source), post = operationUuid();
    await getReplay().enqueue(
      user,
      tenant,
      ShiftCommandIntent(
        ShiftAction.postTrip,
        OnlineCommand(
          post,
          null,
          ShiftCommandIntent.path(ShiftAction.postTrip, tenant),
          {
            'shiftId': reference.toJson(),
            'tripId': null,
            'tractorId': tractor,
            'odometer': 101,
            'damages': 'Door scratch',
            'failures': null,
            'comments': null,
            'occurredAt': f.now
                .subtract(const Duration(seconds: 2))
                .toIso8601String(),
          },
        ),
        dependencies: [source],
        resources: ['tractor:$tractor'],
      ),
    );
    final end = operationUuid();
    await getReplay().enqueue(
      user,
      tenant,
      ShiftCommandIntent(
        ShiftAction.offDuty,
        OnlineCommand(
          end,
          null,
          ShiftCommandIntent.path(ShiftAction.offDuty, tenant, reference),
          {
            'shiftId': reference.toJson(),
            'expectedShiftState': 'ON_DUTY',
            'occurredAt': f.now.toIso8601String(),
          },
        ),
        dependencies: [source, post],
      ),
    );
    await f.worker.sync();
    expect(f.serverShift, isNotNull);
    f.network.status = ConnectivityStatus.available;
    await f.worker.sync();
    expect(f.serverShift, isNull);
    expect(
      f.serverInspections.map((i) => i['inspectionType']),
      containsAll(['PRE_TRIP', 'POST_TRIP']),
    );
    final c = (await f.worker.store.find(user, tenant, end))!,
        request = (await getReplay().frozen(c))!;
    expect(c.state, 'ACKNOWLEDGED');
    expect(request.endpointPath, isNot(contains('{shiftId}')));
    expect(jsonDecode(request.payloadJson), isNot(contains('shiftId')));
    expect(c.endpointPath, contains('{shiftId}'));
  });
  for (final code in [403, 404]) {
    test(
      'revocation/reassignment $code retains inspection/photos as conflict',
      () async {
        final source = await onDuty();
        await f.worker.sync();
        final inspection = await pre(source, await photos());
        if (code == 403) {
          f.shiftRevoked = true;
        } else {
          f.reassigned = true;
        }
        await f.worker.sync();
        expect(
          (await f.worker.store.find(user, tenant, inspection))!.state,
          'CONFLICT',
        );
        expect(f.serverInspections, isEmpty);
        expect(await f.media.list(user, tenant), hasLength(4));
        expect(
          await f.worker.store.watchIssues(user, tenant).first,
          isNotEmpty,
        );
      },
    );
  }
  test(
    'a different namespace cannot bind the On Duty result or see its drafts',
    () async {
      final source = await onDuty();
      await f.worker.sync();
      final images = await photos();
      await expectLater(
        getReplay().enqueue(
          other,
          tenant,
          ShiftPreTripIntent(
            OnlineCommand(
              operationUuid(),
              null,
              ShiftCommandIntent.path(ShiftAction.preTrip, tenant),
              {
                'shiftId': {'operationId': source},
                'tripId': null,
                'tractorId': tractor,
                'odometer': 1,
                'checklist': {'items': []},
                'documentIds': images,
                'occurredAt': f.now.toIso8601String(),
              },
            ),
            dependencies: [source],
          ),
        ),
        throwsA(isA<Exception>()),
      );
      expect(await f.worker.store.list(other, tenant), isEmpty);
      expect(await f.worker.store.list(user, other), isEmpty);
    },
  );
  test('future delivery/Fuel endpoints remain unimplemented even with real Shift adapter', () async {
    final source = await onDuty();
    await f.worker.sync();
    expect(
      getReplay().supports((await f.worker.store.find(user, tenant, source))!),
      isFalse,
    );
    final store = InspectionStore(getReplay());
    final draft = await store.create(
      'PRE_TRIP',
      ShiftReference.server(f.serverShift!['id'] as String),
      tractor,
    );
    expect(draft.tripId, isNull);
    f.reassigned = true;
    await f.session.refresh();
    await expectLater(
      store.create(
        'PRE_TRIP',
        ShiftReference.server(f.serverShift!['id'] as String),
        tractor,
      ),
      throwsA(isA<FormatException>()),
    );
  });
}
