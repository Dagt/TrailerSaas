import 'dart:convert';

import 'package:drift/drift.dart';

import '../media/media_dependencies.dart';
import '../media/media_file_store.dart';
import '../persistence/app_database.dart';
import '../road/online_command_journal.dart';
import '../session/api_client.dart';
import '../session/session_controller.dart';
import '../sync/command_store.dart';

enum ShiftAction { onDuty, preTrip, postTrip, offDuty }

/// Typed local reference; never an invented server Shift UUID.
class ShiftReference {
  const ShiftReference.server(this.id) : operationId = null;
  const ShiftReference.pending(this.operationId) : id = null;
  final String? id, operationId;
  Object toJson() => id ?? {'operationId': operationId!};
  factory ShiftReference.fromJson(Object? value) {
    if (value is String && uuidPattern.hasMatch(value)) {
      return ShiftReference.server(value);
    }
    if (value is Map &&
        value.length == 1 &&
        value['operationId'] is String &&
        uuidPattern.hasMatch(value['operationId'] as String)) {
      return ShiftReference.pending(value['operationId'] as String);
    }
    throw const FormatException('Invalid Shift reference');
  }
}

class ShiftCommandIntent extends CommandIntent {
  ShiftCommandIntent(
    this.action,
    super.command, {
    super.dependencies,
    super.resources,
  });
  final ShiftAction action;
  @override
  String get aggregateType =>
      action == ShiftAction.preTrip || action == ShiftAction.postTrip
      ? 'INSPECTION'
      : 'SHIFT';
  @override
  String? get aggregate => action == ShiftAction.onDuty
      ? null
      : ShiftReference.fromJson(command.body['shiftId']).id;
  static bool recognizes(LocalCommand c) =>
      {'SHIFT', 'INSPECTION'}.contains(c.aggregateType) &&
      {'on-duty', 'off-duty', 'pre-trip', 'post-trip'}.contains(c.commandType);
  static ShiftAction actionFor(LocalCommand c) => switch (c.commandType) {
    'on-duty' => ShiftAction.onDuty,
    'off-duty' => ShiftAction.offDuty,
    'pre-trip' => ShiftAction.preTrip,
    'post-trip' => ShiftAction.postTrip,
    _ => throw const FormatException(),
  };
  static String path(
    ShiftAction action,
    String tenant, [
    ShiftReference? shift,
  ]) => switch (action) {
    ShiftAction.onDuty => 'tenants/$tenant/driver/me/shifts/on-duty',
    ShiftAction.preTrip => 'tenants/$tenant/driver/me/inspections/pre-trip',
    ShiftAction.postTrip => 'tenants/$tenant/driver/me/inspections/post-trip',
    ShiftAction.offDuty =>
      'tenants/$tenant/driver/me/shifts/${shift!.id ?? '{shiftId}'}/off-duty',
  };
  @override
  void validate(String tenant) {
    if (!uuidPattern.hasMatch(command.id) ||
        dependencies.toSet().length != dependencies.length ||
        dependencies.contains(command.id)) {
      throw const FormatException('Invalid Shift operation');
    }
    final reference = action == ShiftAction.onDuty
        ? null
        : ShiftReference.fromJson(command.body['shiftId']);
    if (command.path != path(action, tenant, reference) ||
        command.body['occurredAt'] is! String ||
        occurredAt.isAfter(DateTime.now().toUtc())) {
      throw const FormatException('Invalid Shift endpoint/time');
    }
    final fields = switch (action) {
      ShiftAction.onDuty => {'occurredAt'},
      ShiftAction.offDuty => {'shiftId', 'occurredAt', 'expectedShiftState'},
      ShiftAction.preTrip => {
        'shiftId',
        'tripId',
        'tractorId',
        'odometer',
        'checklist',
        'documentIds',
        'occurredAt',
      },
      ShiftAction.postTrip => {
        'shiftId',
        'tripId',
        'tractorId',
        'odometer',
        'damages',
        'failures',
        'comments',
        'occurredAt',
      },
    };
    if (!command.body.keys.every(fields.contains) ||
        command.body['tripId'] != command.tripId) {
      throw const FormatException('Invalid Shift fields');
    }
    if (action == ShiftAction.onDuty || action == ShiftAction.offDuty) {
      if (command.tripId != null) {
        throw const FormatException('Shift is not a Trip');
      }
    }
    if (action == ShiftAction.offDuty &&
        command.body['expectedShiftState'] != 'ON_DUTY') {
      throw const FormatException('Invalid expected Shift state');
    }
    if (action == ShiftAction.preTrip || action == ShiftAction.postTrip) {
      final b = command.body;
      if (b['tractorId'] is! String ||
          !uuidPattern.hasMatch(b['tractorId'] as String) ||
          b['odometer'] is! num ||
          (b['odometer'] as num) < 0 ||
          !(b['odometer'] as num).isFinite) {
        throw const FormatException('Invalid equipment/odometer');
      }
      if (command.tripId != null && !uuidPattern.hasMatch(command.tripId!)) {
        throw const FormatException('Invalid Trip');
      }
      if (action == ShiftAction.preTrip) {
        if (b['checklist'] is! Map ||
            (b['checklist'] as Map).keys.toSet().difference({
              'items',
            }).isNotEmpty ||
            (b['checklist'] as Map)['items'] is! List ||
            b['documentIds'] is! Map) {
          throw const FormatException('Invalid inspection');
        }
      } else {
        for (final field in ['damages', 'failures', 'comments']) {
          if (b[field] != null &&
              (b[field] is! String || (b[field] as String).length > 4000)) {
            throw const FormatException('Invalid inspection text');
          }
        }
      }
    }
  }
}

/// Uses the existing media graph, retaining exactly its four-position/type gates.
class ShiftPreTripIntent extends EvidenceCommandIntent {
  ShiftPreTripIntent(
    OnlineCommand command, {
    super.dependencies,
    super.resources,
  }) : super(EvidenceCommandKind.preTrip, command);
  @override
  String get aggregateType => 'INSPECTION';
  @override
  String? get aggregate => ShiftReference.fromJson(command.body['shiftId']).id;
  @override
  void validate(String tenant) => ShiftCommandIntent(
    ShiftAction.preTrip,
    command,
    dependencies: dependencies,
    resources: resources,
  ).validate(tenant);
}

/// The only installed workflow adapter: #43. No #45 endpoint is supported.
class ShiftReplay implements EvidenceReplayPort {
  ShiftReplay(this.store, this.session);
  final CommandStore store;
  final SessionController session;
  ApiClient get api => session.api;
  AppDatabase get db => store.db;
  @override
  bool supports(LocalCommand intent) =>
      ShiftCommandIntent.recognizes(intent) && intent.commandType == 'pre-trip';
  Future<void> enqueue(String user, String tenant, CommandIntent intent) =>
      db.transaction(() async {
        final action = intent is ShiftCommandIntent
            ? intent.action
            : ShiftAction.preTrip;
        final reference = action == ShiftAction.onDuty
            ? null
            : ShiftReference.fromJson(intent.command.body['shiftId']);
        if (reference?.operationId != null) {
          final source = await store.find(
            user,
            tenant,
            reference!.operationId!,
          );
          if (source == null ||
              source.commandType != 'on-duty' ||
              source.aggregateType != 'SHIFT' ||
              !intent.dependencies.contains(source.clientOperationId)) {
            throw const FormatException('On Duty must precede the inspection');
          }
        }
        if (action == ShiftAction.preTrip) {
          if (intent is! ShiftPreTripIntent) throw const FormatException();
          await MediaDependencies(store).enqueue(user, tenant, intent);
        } else {
          await store.enqueue(user, tenant, intent, followResources: true);
        }
        if (reference?.operationId != null) {
          final exists =
              await (db.select(db.commandResultDependencies)..where(
                    (r) =>
                        r.clientOperationId.equals(intent.command.id) &
                        r.fieldPath.equals('shiftId'),
                  ))
                  .getSingleOrNull();
          if (exists != null) {
            if (exists.userId != user ||
                exists.tenantId != tenant ||
                exists.sourceOperationId != reference!.operationId) {
              throw const FormatException('Generated identity cannot change');
            }
            return;
          }
          await db
              .into(db.commandResultDependencies)
              .insert(
                CommandResultDependenciesCompanion.insert(
                  clientOperationId: intent.command.id,
                  userId: user,
                  tenantId: tenant,
                  sourceOperationId: reference!.operationId!,
                  fieldPath: 'shiftId',
                ),
                mode: InsertMode.insertOrIgnore,
              );
        }
      });
  Future<String?> shiftId(LocalCommand c) async {
    if (c.commandType == 'on-duty') return null;
    final reference = ShiftReference.fromJson(
      (jsonDecode(c.payloadJson) as Map)['shiftId'],
    );
    if (reference.id != null) return reference.id;
    final binding =
        await (db.select(db.commandResultDependencies)..where(
              (r) =>
                  r.clientOperationId.equals(c.clientOperationId) &
                  r.userId.equals(c.userId) &
                  r.tenantId.equals(c.tenantId) &
                  r.sourceOperationId.equals(reference.operationId!),
            ))
            .getSingleOrNull();
    if (binding == null ||
        !(jsonDecode(c.dependenciesJson) as List).contains(
          reference.operationId,
        )) {
      return null;
    }
    final source = await store.find(
      c.userId,
      c.tenantId,
      reference.operationId!,
    );
    if (source?.state != 'ACKNOWLEDGED' ||
        source?.commandType != 'on-duty' ||
        source?.aggregateType != 'SHIFT') {
      return null;
    }
    final result =
        await (db.select(db.commandResults)..where(
              (r) =>
                  r.clientOperationId.equals(reference.operationId!) &
                  r.userId.equals(c.userId) &
                  r.tenantId.equals(c.tenantId) &
                  r.resultKind.equals('SHIFT'),
            ))
            .getSingleOrNull();
    return result?.serverId;
  }

  Future<bool> ready(LocalCommand c) async {
    try {
      if (c.commandType != 'on-duty' && await shiftId(c) == null) return false;
      if (c.commandType == 'pre-trip' &&
          !await MediaDependencies(store).ready(c)) {
        return false;
      }
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<MaterializedRequest?> materialize(LocalCommand c) =>
      db.transaction(() async {
        final old = await frozen(c);
        if (old != null) return old;
        if (!await ready(c)) return null;
        if (c.attemptCount != 0 || c.state == 'SENDING') {
          throw StateError('Cannot change an attempted request');
        }
        final action = ShiftCommandIntent.actionFor(c),
            body = jsonDecode(c.payloadJson) as Map<String, dynamic>;
        ShiftCommandIntent(
          action,
          OnlineCommand(c.clientOperationId, c.tripId, c.endpointPath, body),
        ).validate(c.tenantId);
        final shift = await shiftId(c);
        if (shift != null) body['shiftId'] = shift;
        if (action == ShiftAction.preTrip) {
          for (final edge in await MediaDependencies(store).edges(c)) {
            final media =
                await (db.select(db.localMedia)..where(
                      (r) =>
                          r.id.equals(edge.mediaId) &
                          r.userId.equals(c.userId) &
                          r.tenantId.equals(c.tenantId),
                    ))
                    .getSingle();
            (body['documentIds'] as Map)[edge.fieldPath.split('.').last] =
                media.serverDocumentId;
          }
        }
        final endpoint = ShiftCommandIntent.path(
          action,
          c.tenantId,
          shift == null ? null : ShiftReference.server(shift),
        );
        if (action == ShiftAction.offDuty) body.remove('shiftId');
        await db
            .into(db.materializedRequests)
            .insert(
              MaterializedRequestsCompanion.insert(
                clientOperationId: c.clientOperationId,
                userId: c.userId,
                tenantId: c.tenantId,
                endpointPath: endpoint,
                httpMethod: c.httpMethod,
                payloadJson: jsonEncode(body),
                createdAt: store.now(),
              ),
            );
        return frozen(c);
      });
  Future<MaterializedRequest?> frozen(LocalCommand c) =>
      (db.select(db.materializedRequests)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId),
          ))
          .getSingleOrNull();
  @override
  Future<Map<String, dynamic>> send(
    LocalCommand intent,
    Map<String, dynamic> frozenBody,
  ) async {
    final request = await frozen(intent);
    if (request == null) throw StateError('Durable request required');
    bool current() =>
        session.state == SessionState.ready &&
        session.auth.uid == session.snapshot?.firebaseUid &&
        session.snapshot?.userId == intent.userId &&
        session.snapshot?.membership.tenantId == intent.tenantId;
    if (!current()) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Refresh the current session.',
      );
    }
    try {
      return await api.post(
        request.endpointPath,
        jsonDecode(request.payloadJson) as Map<String, dynamic>,
        intent.clientOperationId,
        forceRefresh: false,
      );
    } on ApiProblem catch (e) {
      if (e.status != 401 || !current()) rethrow;
      return api.post(
        request.endpointPath,
        jsonDecode(request.payloadJson) as Map<String, dynamic>,
        intent.clientOperationId,
        forceRefresh: true,
      );
    }
  }

  Future<void> acknowledge(
    LocalCommand c,
    Map<String, dynamic> response,
  ) => db.transaction(() async {
    final request = await frozen(c);
    if (request == null) throw const FormatException();
    final body = jsonDecode(request.payloadJson) as Map<String, dynamic>,
        action = ShiftCommandIntent.actionFor(c),
        id = response['id'];
    if (id is! String || !uuidPattern.hasMatch(id)) {
      throw const FormatException('Unconfirmed identity');
    }
    void instant(String field) {
      final v = response[field];
      if (v is! String ||
          !RegExp(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$')
              .hasMatch(v)) {
        throw const FormatException('Invalid server instant');
      }
      DateTime.parse(v);
    }

    instant('createdAt');
    if (action == ShiftAction.onDuty || action == ShiftAction.offDuty) {
      instant('updatedAt');
      instant('onDutyAt');
      if (response['state'] !=
          (action == ShiftAction.onDuty ? 'ON_DUTY' : 'OFF_DUTY')) {
        throw const FormatException('Unconfirmed Shift state');
      }
      if (action == ShiftAction.onDuty) {
        if (response['offDutyAt'] != null ||
            DateTime.parse(response['onDutyAt'] as String) !=
                DateTime.parse(body['occurredAt'] as String)) {
          throw const FormatException('Unconfirmed Shift');
        }
      } else {
        instant('offDutyAt');
        if (id != await shiftId(c) ||
            DateTime.parse(response['offDutyAt'] as String) !=
                DateTime.parse(body['occurredAt'] as String)) {
          throw const FormatException('Unconfirmed closure');
        }
      }
    } else {
      instant('occurredAt');
      if (response['shiftId'] != body['shiftId'] ||
          response['tractorId'] != body['tractorId'] ||
          response['tripId'] != body['tripId'] ||
          response['odometer'] != body['odometer'] ||
          response['inspectionType'] !=
              (action == ShiftAction.preTrip ? 'PRE_TRIP' : 'POST_TRIP') ||
          DateTime.parse(response['occurredAt'] as String) !=
              DateTime.parse(body['occurredAt'] as String)) {
        throw const FormatException('Unconfirmed inspection');
      }
    }
    final kind = action == ShiftAction.onDuty || action == ShiftAction.offDuty
        ? 'SHIFT'
        : 'INSPECTION';
    final old =
        await (db.select(db.commandResults)
              ..where((r) => r.clientOperationId.equals(c.clientOperationId)))
            .getSingleOrNull();
    if (old != null &&
        (old.serverId != id ||
            old.userId != c.userId ||
            old.tenantId != c.tenantId)) {
      throw StateError('Confirmed identity cannot change');
    }
    if (old == null) {
      await db
          .into(db.commandResults)
          .insert(
            CommandResultsCompanion.insert(
              clientOperationId: c.clientOperationId,
              userId: c.userId,
              tenantId: c.tenantId,
              resultKind: kind,
              serverId: id,
              projectionJson: jsonEncode(response),
              confirmedAt: store.now(),
            ),
          );
    }
    await store.result(c, CommandState.acknowledged, status: 200);
  });
}
