import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../session/contracts.dart';
import '../road/online_command_journal.dart';
import '../sync/command_store.dart';
import 'shift_commands.dart';
import 'contracts.dart';
import '../yard/check_store.dart';

class InspectionStore {
  InspectionStore(this.replay);
  final ShiftReplay replay;
  AppDatabase get db => replay.db;
  SessionController get session => replay.session;
  SessionSnapshot owner() {
    final s = session.snapshot;
    if (s == null ||
        s.driver == null ||
        session.auth.uid != s.firebaseUid ||
        !{SessionState.ready, SessionState.offline}.contains(session.state)) {
      throw const FormatException('Refresh the authorized Driver session.');
    }
    return s;
  }

  Future<InspectionDraft> create(
    String type,
    ShiftReference shift,
    String tractor, {
    String? trip,
  }) async {
    final s = owner(), home = s.driver!.shiftContext;
    final cap = type == 'PRE_TRIP' ? 'PRETRIP_CREATE' : 'POSTTRIP_CREATE';
    if (!{'PRE_TRIP', 'POST_TRIP'}.contains(type) ||
        !s.membership.capabilities.contains(cap)) {
      throw const FormatException('Inspection permission unavailable.');
    }
    final candidates = type == 'PRE_TRIP'
        ? home?.tractors
        : await usedTractors();
    if (candidates == null || !candidates.any((e) => e.id == tractor)) {
      throw const FormatException(
        'No tienes un Tractor asignado. Contacta a operaciones.',
      );
    }
    if (trip != null &&
        !s.trips.any(
          (t) => t.id == trip && t.data['currentTractorId'] == tractor,
        )) {
      throw const FormatException('Refresh the current Tractor assignment.');
    }
    final id = operationUuid();
    await db
        .into(db.inspectionDrafts)
        .insert(
          InspectionDraftsCompanion.insert(
            id: id,
            userId: s.userId,
            tenantId: s.membership.tenantId,
            inspectionType: type,
            shiftReferenceJson: jsonEncode(shift.toJson()),
            tractorId: tractor,
            tripId: Value(trip),
            updatedAt: replay.store.now(),
          ),
        );
    return (await find(s.userId, s.membership.tenantId, id))!;
  }

  Future<InspectionDraft?> find(String user, String tenant, String id) =>
      (db.select(db.inspectionDrafts)..where(
            (r) =>
                r.id.equals(id) &
                r.userId.equals(user) &
                r.tenantId.equals(tenant),
          ))
          .getSingleOrNull();

  /// Local inspection intent identifies used equipment, never server approval.
  Future<List<ShiftTractor>> usedTractors() async {
    final s = owner(), home = s.driver!.shiftContext;
    final rows = await replay.store.list(s.userId, s.membership.tenantId);
    final candidates = <String, ShiftTractor>{
      for (final t in home?.usedTractors ?? <ShiftTractor>[]) t.id: t,
    };
    for (final c in rows.where(
      (c) => c.commandType == 'pre-trip' && c.aggregateType == 'INSPECTION',
    )) {
      final body = jsonDecode(c.payloadJson) as Map;
      final ref = ShiftReference.fromJson(body['shiftId']);
      final matches = ref.id != null
          ? ref.id == home?.current?.id
          : rows.any(
              (source) =>
                  source.clientOperationId == ref.operationId &&
                  source.commandType == 'on-duty' &&
                  source.state != 'ACKNOWLEDGED',
            );
      if (!matches) continue;
      for (final t in home?.tractors ?? <ShiftTractor>[]) {
        if (t.id == body['tractorId']) candidates[t.id] = t;
      }
    }
    return candidates.values.toList();
  }

  Future<List<InspectionDraft>> drafts(String user, String tenant) =>
      (db.select(db.inspectionDrafts)
            ..where(
              (r) =>
                  r.userId.equals(user) &
                  r.tenantId.equals(tenant) &
                  r.commandId.isNull(),
            )
            ..orderBy([(r) => OrderingTerm.desc(r.updatedAt)]))
          .get();
  Future<void> save(
    InspectionDraft draft, {
    required String odometer,
    required Map<String, dynamic> checklist,
    String? damages,
    String? failures,
    String? comments,
  }) async {
    final s = owner();
    if (s.userId != draft.userId ||
        s.membership.tenantId != draft.tenantId ||
        draft.commandId != null) {
      throw const FormatException(
        'This draft is not editable in the current context.',
      );
    }
    await (db.update(db.inspectionDrafts)..where(
          (r) =>
              r.id.equals(draft.id) &
              r.userId.equals(s.userId) &
              r.tenantId.equals(draft.tenantId) &
              r.commandId.isNull(),
        ))
        .write(
          InspectionDraftsCompanion(
            odometer: Value(odometer),
            checklistJson: Value(jsonEncode(checklist)),
            damages: Value(damages),
            failures: Value(failures),
            comments: Value(comments),
            updatedAt: Value(replay.store.now()),
          ),
        );
  }

  Future<String> enqueue(InspectionDraft draft) => db.transaction(() async {
    final s = owner();
    if (s.userId != draft.userId || s.membership.tenantId != draft.tenantId) {
      throw const FormatException('Wrong draft namespace');
    }
    final latest = await find(draft.userId, draft.tenantId, draft.id);
    if (latest == null) throw const FormatException('Missing draft');
    if (latest.commandId != null) return latest.commandId!;
    if (await (db.select(db.cameraCaptures)..where(
              (r) =>
                  r.draftId.equals(latest.id) &
                  r.userId.equals(latest.userId) &
                  r.tenantId.equals(latest.tenantId) &
                  r.state.equals('PENDING'),
            ))
            .getSingleOrNull() !=
        null) {
      throw const FormatException('Finish the camera capture before queueing.');
    }
    final odometer = num.tryParse(latest.odometer);
    if (odometer == null || !odometer.isFinite || odometer < 0) {
      throw const FormatException('Introduce un odómetro válido.');
    }
    final pre = latest.inspectionType == 'PRE_TRIP',
        shift = ShiftReference.fromJson(jsonDecode(latest.shiftReferenceJson));
    final id = operationUuid(),
        action = pre ? ShiftAction.preTrip : ShiftAction.postTrip;
    final body = <String, dynamic>{
      'shiftId': shift.toJson(),
      'tripId': latest.tripId,
      'tractorId': latest.tractorId,
      'odometer': odometer,
      'occurredAt': DateTime.now().toUtc().toIso8601String(),
      if (pre) ...{
        'checklist': jsonDecode(latest.checklistJson),
        'documentIds': jsonDecode(latest.photosJson),
      } else ...{
        'damages': latest.damages,
        'failures': latest.failures,
        'comments': latest.comments,
      },
    };
    final command = OnlineCommand(
          id,
          latest.tripId,
          ShiftCommandIntent.path(action, latest.tenantId),
          body,
        ),
        dependencies = [if (shift.operationId != null) shift.operationId!];
    final resources = ['tractor:${latest.tractorId}'];
    final CommandIntent intent = pre
        ? ShiftPreTripIntent(
            command,
            dependencies: dependencies,
            resources: resources,
          )
        : ShiftCommandIntent(
            action,
            command,
            dependencies: dependencies,
            resources: resources,
          );
    await replay.enqueue(latest.userId, latest.tenantId, intent);
    await (db.update(db.inspectionDrafts)..where(
          (r) =>
              r.id.equals(latest.id) &
              r.userId.equals(latest.userId) &
              r.tenantId.equals(latest.tenantId) &
              r.commandId.isNull(),
        ))
        .write(
          InspectionDraftsCompanion(
            commandId: Value(id),
            updatedAt: Value(replay.store.now()),
          ),
        );
    return id;
  });
  Future<String> onDuty() async {
    final s = owner();
    if (!s.membership.capabilities.contains('SHIFT_START')) {
      throw const FormatException('Shift permission unavailable.');
    }
    final id = operationUuid();
    await replay.enqueue(
      s.userId,
      s.membership.tenantId,
      ShiftCommandIntent(
        ShiftAction.onDuty,
        OnlineCommand(
          id,
          null,
          ShiftCommandIntent.path(ShiftAction.onDuty, s.membership.tenantId),
          {'occurredAt': DateTime.now().toUtc().toIso8601String()},
        ),
        resources: ['driver:${s.driver!.id}:shift'],
      ),
    );
    return id;
  }

  Future<String> offDuty(ShiftReference shift) async {
    final s = owner();
    if (!s.membership.capabilities.contains('SHIFT_END')) {
      throw const FormatException('Shift permission unavailable.');
    }
    if (await CheckStore(replay.store)
        .hasUnfinished(s.userId, s.membership.tenantId)) {
      throw StateError(
        'Complete or cancel your unfinished Yard Check on the server before Off Duty. An uncertain start or revoked Check also requires review.',
      );
    }
    final pending = (await replay.store.list(s.userId, s.membership.tenantId))
        .where((r) => r.state != 'ACKNOWLEDGED')
        .map((r) => r.clientOperationId)
        .toSet();
    if (shift.operationId != null) pending.add(shift.operationId!);
    final id = operationUuid();
    await replay.enqueue(
      s.userId,
      s.membership.tenantId,
      ShiftCommandIntent(
        ShiftAction.offDuty,
        OnlineCommand(
          id,
          null,
          ShiftCommandIntent.path(
            ShiftAction.offDuty,
            s.membership.tenantId,
            shift,
          ),
          {
            'shiftId': shift.toJson(),
            'expectedShiftState': 'ON_DUTY',
            'occurredAt': DateTime.now().toUtc().toIso8601String(),
          },
        ),
        dependencies: pending.toList(),
        resources: ['driver:${s.driver!.id}:shift'],
      ),
    );
    return id;
  }
}
