import 'dart:convert';

import 'package:drift/drift.dart';

import '../media/media_file_store.dart';

import '../media/documents_client.dart';
import '../media/media_dependencies.dart';
import '../media/media_store.dart';
import '../persistence/app_database.dart';
import '../session/api_client.dart';
import '../session/session_controller.dart';
import '../shift/shift_commands.dart';
import 'online_command_journal.dart';

/// Only the implemented #43/#45 contracts, sharing the existing replay lane.
class RoadEvidenceReplay implements TypedEvidenceReplayPort {
  RoadEvidenceReplay(this.shifts, this.media);
  final ShiftReplay shifts;
  final MediaStore media;
  SessionController get session => shifts.session;
  @override
  bool supports(LocalCommand c) =>
      shifts.supports(c) ||
      {
            'delivery-completions',
            'fuel-events',
            'exceptions',
          }.contains(c.commandType) &&
          c.aggregateType == 'TRIP';
  bool current(LocalCommand c) =>
      session.state == SessionState.ready &&
      session.auth.uid == session.snapshot?.firebaseUid &&
      session.snapshot?.userId == c.userId &&
      session.snapshot?.membership.tenantId == c.tenantId &&
      session.snapshot?.driver != null;
  @override
  Future<Map<String, dynamic>> send(
    LocalCommand c,
    Map<String, dynamic> body,
  ) => sendAuthorized(c, body, forceRefresh: false);
  @override
  Future<Map<String, dynamic>> sendAuthorized(
    LocalCommand c,
    Map<String, dynamic> body, {
    required bool forceRefresh,
  }) async {
    if (shifts.supports(c)) return shifts.send(c, body);
    if (!supports(c) || !current(c)) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Actualiza la sesión para sincronizar.',
      );
    }
    final frozen =
        await (shifts.db.select(shifts.db.materializedRequests)..where(
              (r) =>
                  r.clientOperationId.equals(c.clientOperationId) &
                  r.userId.equals(c.userId) &
                  r.tenantId.equals(c.tenantId),
            ))
            .getSingleOrNull();
    if (frozen == null ||
        frozen.endpointPath != c.endpointPath ||
        frozen.payloadJson != jsonEncode(body)) {
      throw const FormatException('Immutable request required');
    }
    final kind = EvidenceCommandIntent.kindFor(c)!;
    EvidenceCommandIntent(
      kind,
      OnlineCommand(c.clientOperationId, c.tripId, c.endpointPath, body),
    ).validate(c.tenantId);
    final isDelivery = {'delivery-completions'}.contains(c.commandType);
    final target = isDelivery
        ? c.aggregateId!
        : body['stopId'] as String? ?? c.tripId!;
    final type = isDelivery || body['stopId'] != null ? 'TRIP_STOP' : 'TRIP';
    for (final edge in await MediaDependencies(shifts.store).edges(c)) {
      final row = await media.find(c.userId, c.tenantId, edge.mediaId);
      if (row == null || row.state != 'FINALIZED' || !current(c)) {
        throw const FormatException('Finalized own evidence required');
      }
      await DocumentsClient(session.api).link(row, type, target);
      if (!current(c)) {
        throw const ApiProblem(
          401,
          'AUTHENTICATION_REQUIRED',
          'La sesión cambió.',
        );
      }
    }
    if (!current(c)) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'La sesión cambió.',
      );
    }
    return session.api.post(
      frozen.endpointPath,
      body,
      c.clientOperationId,
      forceRefresh: forceRefresh,
    );
  }

  static bool instant(Object? v) =>
      v is String &&
      RegExp(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$')
          .hasMatch(v) &&
      DateTime.tryParse(v) != null;
  @override
  void validateResponse(
    LocalCommand c,
    Map<String, dynamic> r,
    Map<String, dynamic> body,
  ) {
    final keys = switch (c.commandType) {
      'delivery-completions' => {
        'stopId',
        'tripId',
        'stopStatus',
        'tripStatus',
        'stopVersion',
        'tripVersion',
        'completedAt',
      },
      'fuel-events' => {
        'id',
        'tripId',
        'tractorId',
        'odometer',
        'receiptDocumentId',
        'occurredAt',
        'createdAt',
      },
      'exceptions' => {
        'id',
        'tripId',
        'stopId',
        'category',
        'comment',
        'documentIds',
        'occurredAt',
        'recordedAt',
      },
      _ => <String>{},
    };
    bool valid =
        r['tripId'] == c.tripId &&
        r.keys.toSet().length == keys.length &&
        r.keys.every(keys.contains);
    if (c.commandType == 'delivery-completions') {
      valid =
          valid &&
          r['stopId'] == c.aggregateId &&
          r['stopStatus'] == 'COMPLETED' &&
          {'AT_STOP', 'COMPLETED'}.contains(r['tripStatus']) &&
          r['stopVersion'] is int &&
          (r['stopVersion'] as int) >= 1 &&
          r['tripVersion'] is int &&
          (r['tripVersion'] as int) >= 1 &&
          instant(r['completedAt']) &&
          DateTime.parse(r['completedAt'] as String)
              .isAtSameMomentAs(DateTime.parse(body['occurredAt'] as String));
    } else if (c.commandType == 'fuel-events') {
      valid =
          valid &&
          r['id'] is String &&
          uuidPattern.hasMatch(r['id'] as String) &&
          r['tractorId'] == body['tractorId'] &&
          r['receiptDocumentId'] == body['receiptDocumentId'] &&
          r['odometer'] == body['odometer'] &&
          instant(r['occurredAt']) &&
          instant(r['createdAt']) &&
          DateTime.parse(r['occurredAt'] as String)
              .isAtSameMomentAs(DateTime.parse(body['occurredAt'] as String));
    } else if (c.commandType == 'exceptions') {
      final expected = List<String>.from(body['documentIds'] as List)..sort();
      final actual = r['documentIds'] is List
          ? List<Object?>.from(r['documentIds'] as List)
          : null;
      valid =
          valid &&
          r['id'] is String &&
          uuidPattern.hasMatch(r['id'] as String) &&
          r['stopId'] == body['stopId'] &&
          r['category'] == body['category'] &&
          r['comment'] == (body['comment'] as String).trim() &&
          actual != null &&
          jsonEncode(actual) == jsonEncode(expected) &&
          instant(r['occurredAt']) &&
          instant(r['recordedAt']) &&
          DateTime.parse(r['occurredAt'] as String)
              .isAtSameMomentAs(DateTime.parse(body['occurredAt'] as String));
    } else {
      valid = false;
    }
    if (!valid) throw const FormatException('Unconfirmed operational result');
  }
}
