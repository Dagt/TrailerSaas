import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../platform/native_qr_scanner.dart';
import '../road/online_command_journal.dart';
import '../session/api_client.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../sync/command_store.dart';
import 'check_contracts.dart';
import 'check_store.dart';
import 'yard_contracts.dart';

class CheckIntent extends CommandIntent {
  CheckIntent(this.kind, super.command, {super.dependencies, super.resources});
  final String kind;
  static const kinds = {
    'YARD_CHECK_START',
    'YARD_CHECK_SCAN',
    'YARD_CHECK_COMPLETE',
    'YARD_CHECK_CANCEL',
    'YARD_CHECK_MISSING',
    'YARD_CHECK_CORRECTION',
  };
  static bool recognizes(LocalCommand c) =>
      c.aggregateType == 'YARD_CHECK' && kinds.contains(c.commandType);
  @override
  String get type => kind;
  @override
  String get aggregateType => 'YARD_CHECK';
  @override
  String? get aggregate =>
      kind == 'YARD_CHECK_START' ? null : command.path.split('/')[3];
  @override
  DateTime get occurredAt =>
      DateTime.parse(command.body['_capturedAt'] as String).toUtc();
  @override
  void validate(String tenant) {
    yardUuid(command.id);
    if (!kinds.contains(kind) ||
        command.tripId != null ||
        command.path.split('/').take(3).join('/') !=
            'tenants/$tenant/yard-checks' ||
        dependencies.contains(command.id) ||
        dependencies.toSet().length != dependencies.length) {
      throw const FormatException('Invalid Check intent');
    }
    checkTime(command.body['_capturedAt']);
    if (occurredAt.isAfter(DateTime.now().toUtc())) {
      throw const FormatException('Future Check observation');
    }
    final allowed = switch (kind) {
      'YARD_CHECK_START' => {'siteId', 'locationId', '_capturedAt'},
      'YARD_CHECK_SCAN' => {'publicToken', 'observedLocationId', '_capturedAt'},
      'YARD_CHECK_COMPLETE' => {'review', '_capturedAt'},
      'YARD_CHECK_CANCEL' => {'review', 'reason', '_capturedAt'},
      'YARD_CHECK_MISSING' => {
        'trailerId',
        'expectedTrailerVersion',
        'reason',
        '_capturedAt',
      },
      'YARD_CHECK_CORRECTION' => {
        'trailerId',
        'expectedTrailerVersion',
        'newLocationId',
        'reason',
        '_capturedAt',
      },
      _ => <String>{},
    };
    if (command.body.keys.toSet().difference(allowed).isNotEmpty ||
        command.body.length != allowed.length) {
      throw const FormatException('Invalid Check fields');
    }
    if (kind == 'YARD_CHECK_START') {
      if (command.path != 'tenants/$tenant/yard-checks') {
        throw const FormatException('Invalid Check start path');
      }
      yardUuid(command.body['siteId']);
      if (command.body['locationId'] != null) {
        yardUuid(command.body['locationId']);
      }
    } else {
      yardUuid(aggregate);
      final suffix = switch (kind) {
        'YARD_CHECK_SCAN' => 'scans',
        'YARD_CHECK_COMPLETE' => 'completion',
        'YARD_CHECK_CANCEL' => 'cancellation',
        'YARD_CHECK_MISSING' => 'mark-location-unknown',
        _ => 'location-correction',
      };
      final parts = command.path.split('/');
      if (kind == 'YARD_CHECK_MISSING' || kind == 'YARD_CHECK_CORRECTION') {
        if (parts.length != 7 || parts[4] != 'items' || parts.last != suffix) {
          throw const FormatException('Invalid reconciliation path');
        }
        yardUuid(parts[5]);
        yardUuid(command.body['trailerId']);
        if (command.body['expectedTrailerVersion'] is! int ||
            (command.body['expectedTrailerVersion'] as int) < 0) {
          throw const FormatException('Invalid Trailer version');
        }
        if (kind == 'YARD_CHECK_CORRECTION') {
          yardUuid(command.body['newLocationId']);
        }
      } else if (parts.length != 5 || parts.last != suffix) {
        throw const FormatException('Invalid Check action path');
      }
    }
    if (kind == 'YARD_CHECK_SCAN') {
      final token = command.body['publicToken'];
      if (token is! String || trailerPublicToken(token) != token) {
        throw const FormatException('Invalid QR');
      }
      yardUuid(command.body['observedLocationId']);
    }
    if (allowed.contains('reason') &&
        (command.body['reason'] is! String ||
            (command.body['reason'] as String).trim().isEmpty ||
            (command.body['reason'] as String).length > 4000)) {
      throw const FormatException('A reason is required');
    }
    if (allowed.contains('review') && command.body['review'] is! String) {
      throw const FormatException('Check review missing');
    }
  }
}

/// Typed adapter on the original queue/worker. Never runs an independent send loop.
class CheckReplay {
  CheckReplay(this.commands, this.session);
  final CommandStore commands;
  final SessionController session;
  AppDatabase get db => commands.db;
  CheckStore get cache => CheckStore(commands);

  Future<String> capture(
    SessionSnapshot o,
    String kind,
    String path,
    Map<String, dynamic> body, {
    String? id,
    List<String> dependencies = const [],
    bool Function()? current,
  }) async {
    final operation = id ?? operationUuid();
    await db.transaction(() async {
      if (current != null && !current()) {
        throw StateError('Check context changed');
      }
      final check = kind == 'YARD_CHECK_START' ? null : path.split('/')[3];
      await commands.enqueue(
        o.userId,
        o.membership.tenantId,
        CheckIntent(
          kind,
          OnlineCommand(operation, null, path, body),
          dependencies: dependencies,
          resources: [if (check != null) 'yard-check:$check'],
        ),
      );
      if (current != null && !current()) {
        throw StateError('Check context changed');
      }
    });
    return operation;
  }

  Future<String> scan(
    SessionSnapshot o,
    String id,
    String token,
    String location,
    DateTime at,
    bool Function() current,
  ) async {
    if (trailerPublicToken(token) != token) {
      throw const FormatException('Invalid QR');
    }
    final binding = await session.cache.identifiedTrailer(o, token);
    final identified = binding?['yardProjection'] == true
        ? binding!['trailerId'] as String?
        : null;
    return db.transaction(() async {
      if (!current()) throw StateError('Check context changed');
      final check = await cache.find(o, id);
      if (check == null ||
          !check.snapshotDownloaded ||
          check.accessBlocked ||
          check.status != 'IN_PROGRESS' ||
          at.isBefore(DateTime.parse(check.startedAt)) ||
          (check.locationId != null && check.locationId != location)) {
        throw StateError('A complete active authorized snapshot is required');
      }
      final previous = await cache.observations(o, id);
      final duplicate = previous
          .where(
            (r) =>
                r.publicToken == token ||
                (identified != null &&
                    (r.identifiedTrailerId == identified ||
                        r.resolvedTrailerId == identified)),
          )
          .firstOrNull;
      if (duplicate != null) return duplicate.operationId;
      final operation = await capture(
        o,
        'YARD_CHECK_SCAN',
        '${cache.root(o)}/$id/scans',
        {
          'publicToken': token,
          'observedLocationId': location,
          '_capturedAt': at.toUtc().toIso8601String(),
        },
        current: current,
      );
      await db
          .into(db.yardCheckObservations)
          .insert(
            YardCheckObservationsCompanion.insert(
              operationId: operation,
              userId: o.userId,
              tenantId: o.membership.tenantId,
              checkId: id,
              publicToken: token,
              identifiedTrailerId: Value(identified),
              observedLocationId: location,
              scannedAt: at.toUtc().toIso8601String(),
              localResult: await classify(o, id, identified, location),
              createdAt: commands.now(),
            ),
          );
      if (!current()) throw StateError('Check context changed');
      return operation;
    });
  }

  Future<String> classify(
    SessionSnapshot o,
    String id,
    String? trailer,
    String location,
  ) async {
    if (trailer == null) return 'UNRESOLVED_QR';
    final expected = (await cache.items(
      o,
      id,
    )).where((i) => i.trailerId == trailer && i.expected).firstOrNull;
    return expected == null
        ? 'UNEXPECTED'
        : expected.expectedLocationId == location
        ? 'MATCHED'
        : 'LOCATION_MISMATCH';
  }

  Future<bool> settled(SessionSnapshot o, String id) async {
    final reviewed = await commands.reviewedYardFailures(
      o.userId,
      o.membership.tenantId,
    );
    return !(await commands.list(o.userId, o.membership.tenantId)).any(
      (c) =>
          c.aggregateType == 'YARD_CHECK' &&
          c.aggregateId == id &&
          c.state != 'ACKNOWLEDGED' &&
          !reviewed.contains(c.clientOperationId),
    );
  }

  Future<Map<String, dynamic>> materialize(
    LocalCommand c,
    SessionSnapshot o,
    bool Function() current,
  ) async {
    final frozen =
        await (db.select(db.materializedRequests)..where(
              (r) =>
                  r.clientOperationId.equals(c.clientOperationId) &
                  r.userId.equals(c.userId) &
                  r.tenantId.equals(c.tenantId),
            ))
            .getSingleOrNull();
    if (frozen != null) return checkMap(jsonDecode(frozen.payloadJson));
    final intent = checkMap(jsonDecode(c.payloadJson));
    final result = <String, dynamic>{};
    switch (c.commandType) {
      case 'YARD_CHECK_START':
        result.addAll({
          'siteId': intent['siteId'],
          'locationId': intent['locationId'],
        });
      case 'YARD_CHECK_SCAN':
        final obs =
            await (db.select(db.yardCheckObservations)..where(
                  (r) =>
                      r.operationId.equals(c.clientOperationId) &
                      r.userId.equals(c.userId) &
                      r.tenantId.equals(c.tenantId),
                ))
                .getSingle();
        final qr = await session.api.get('trailer-qr/${obs.publicToken}');
        yardUuid(qr['trailerId']);
        if (qr['active'] != true ||
            (qr['tenantId'] != null && qr['tenantId'] != c.tenantId)) {
          throw const ApiProblem(
            404,
            'RESOURCE_NOT_FOUND',
            'QR is not available in this Yard context.',
          );
        }
        final trailer = YardTrailer.fromJson(
          await session.api.get(
            'tenants/${c.tenantId}/yard/trailers/${qr['trailerId']}',
          ),
        );
        if (!current()) throw StateError('Check owner changed');
        if (trailer.id != qr['trailerId'] ||
            trailer.data['active'] != true ||
            (obs.identifiedTrailerId != null &&
                trailer.id != obs.identifiedTrailerId)) {
          throw const ApiProblem(
            409,
            'RESOURCE_CONFLICT',
            'The verified QR identity changed. Review the observation.',
          );
        }
        await session.cache.rememberIdentifiedTrailer(o, obs.publicToken, {
          ...trailer.data,
          'yardProjection': true,
        }, current);
        result.addAll({
          'trailerId': trailer.id,
          'observedLocationId': obs.observedLocationId,
          'scannedAt': obs.scannedAt,
        });
        await (db.update(
          db.yardCheckObservations,
        )..where((r) => r.operationId.equals(c.clientOperationId))).write(
          YardCheckObservationsCompanion(
            resolvedTrailerId: Value(trailer.id),
            localResult: Value(
              await classify(
                o,
                c.aggregateId!,
                trailer.id,
                obs.observedLocationId,
              ),
            ),
          ),
        );
      case 'YARD_CHECK_COMPLETE':
      case 'YARD_CHECK_CANCEL':
        // The current command is SENDING; every other unreviewed command must be ACKed.
        final reviewed = await commands.reviewedYardFailures(
          c.userId,
          c.tenantId,
        );
        if ((await commands.list(c.userId, c.tenantId)).any(
          (r) =>
              r.aggregateId == c.aggregateId &&
              r.clientOperationId != c.clientOperationId &&
              r.state != 'ACKNOWLEDGED' &&
              !reviewed.contains(r.clientOperationId),
        )) {
          throw const ApiProblem(
            409,
            'RESOURCE_CONFLICT',
            'Synchronize or review all Check observations first.',
          );
        }
        final fresh = await cache.download(session, o, c.aggregateId!, current);
        if (fresh.check.status != 'IN_PROGRESS' ||
            fresh.review != intent['review']) {
          throw const ApiProblem(
            409,
            'REVIEW_REQUIRED',
            'The Check changed. Review the current inventory again.',
          );
        }
        result['expectedVersion'] = fresh.check.version;
        if (c.commandType == 'YARD_CHECK_CANCEL') {
          result['reason'] = intent['reason'];
        }
      case 'YARD_CHECK_MISSING':
      case 'YARD_CHECK_CORRECTION':
        result.addAll({
          'expectedTrailerVersion': intent['expectedTrailerVersion'],
          'reason': intent['reason'],
          if (c.commandType == 'YARD_CHECK_CORRECTION')
            'newLocationId': intent['newLocationId'],
        });
      default:
        throw const FormatException('Unsupported Check operation');
    }
    if (!current()) throw StateError('Check owner changed');
    await db.transaction(() async {
      if (!current()) throw StateError('Check owner changed');
      await db
          .into(db.materializedRequests)
          .insert(
            MaterializedRequestsCompanion.insert(
              clientOperationId: c.clientOperationId,
              userId: c.userId,
              tenantId: c.tenantId,
              endpointPath: c.endpointPath,
              httpMethod: 'POST',
              payloadJson: jsonEncode(result),
              createdAt: commands.now(),
            ),
          );
    });
    return result;
  }

  static String validate(
    LocalCommand c,
    Map<String, dynamic> response,
    Map<String, dynamic> body,
  ) {
    validateFrozen(c, body);
    if (c.commandType == 'YARD_CHECK_START') {
      final detail = CheckDetail(response);
      if (detail.check.siteId != body['siteId'] ||
          detail.check.locationId != body['locationId'] ||
          detail.check.status != 'IN_PROGRESS' ||
          detail.check.version != 0 ||
          detail.check.json['startedByUserId'] != c.userId) {
        throw const FormatException('Invalid Check creation ACK');
      }
      return detail.check.id;
    }
    if (c.commandType == 'YARD_CHECK_SCAN') {
      final summary = CheckSummary(checkMap(response['check'])),
          item = CheckItem(checkMap(response['item']));
      if (summary.id != c.aggregateId ||
          item.trailerId != body['trailerId'] ||
          !item.found ||
          !{
            'MATCHED',
            'UNEXPECTED',
            'LOCATION_MISMATCH',
            'ALREADY_SCANNED',
          }.contains(response['outcome'])) {
        throw const FormatException('Invalid scan ACK');
      }
      if (response['outcome'] != 'ALREADY_SCANNED' &&
          (item.json['clientOperationId'] != c.clientOperationId ||
              item.json['scannedByUserId'] != c.userId ||
              DateTime.parse(item.json['scannedAt'] as String) !=
                  DateTime.parse(body['scannedAt'] as String) ||
              item.json['observedLocationId'] != body['observedLocationId'] ||
              item.json['result'] != response['outcome'])) {
        throw const FormatException('Scan ACK does not match observation');
      }
      return summary.id;
    }
    if (c.commandType == 'YARD_CHECK_COMPLETE' ||
        c.commandType == 'YARD_CHECK_CANCEL') {
      final summary = CheckSummary(response);
      final action = c.commandType == 'YARD_CHECK_COMPLETE'
          ? 'completed'
          : 'cancelled';
      if (summary.id != c.aggregateId ||
          summary.status !=
              (action == 'completed' ? 'COMPLETED' : 'CANCELLED') ||
          summary.version != (body['expectedVersion'] as int) + 1 ||
          summary.json['${action}ByUserId'] != c.userId ||
          (action == 'cancelled' &&
              summary.json['cancellationReason'] != body['reason'])) {
        throw const FormatException('Invalid terminal ACK');
      }
      return summary.id;
    }
    final original = checkMap(jsonDecode(c.payloadJson));
    final trailer = YardTrailer.fromJson(checkMap(response['trailer'])),
        event = checkMap(response['movementEvent']);
    final missing = c.commandType == 'YARD_CHECK_MISSING';
    for (final key in ['reconciliationId', 'yardCheckId', 'itemId']) {
      yardUuid(response[key]);
    }
    yardUuid(event['id']);
    checkTime(event['occurredAt']);
    checkTime(event['recordedAt']);
    if (response['yardCheckId'] != c.aggregateId ||
        response['itemId'] != c.endpointPath.split('/')[5] ||
        trailer.id != original['trailerId'] ||
        trailer.data['version'] !=
            (body['expectedTrailerVersion'] as int) + 1 ||
        event['eventType'] !=
            (missing ? 'LOCATION_MARKED_UNKNOWN' : 'LOCATION_CORRECTED') ||
        (missing
            ? (trailer.data['locationState'] != 'LOCATION_UNKNOWN' ||
                  trailer.data['currentLocation'] != null)
            : (trailer.data['currentLocation'] as Map?)?['id'] !=
                  body['newLocationId'])) {
      throw const FormatException('Invalid reconciliation ACK');
    }
    if (missing) {
      yardUuid(response['alertId']);
    } else if (response['alertId'] != null) {
      throw const FormatException('Unexpected alert');
    }
    return c.aggregateId!;
  }

  static void validateFrozen(LocalCommand c, Map<String, dynamic> body) {
    final intent = checkMap(jsonDecode(c.payloadJson));
    final keys = switch (c.commandType) {
      'YARD_CHECK_START' => {'siteId', 'locationId'},
      'YARD_CHECK_SCAN' => {'trailerId', 'observedLocationId', 'scannedAt'},
      'YARD_CHECK_COMPLETE' => {'expectedVersion'},
      'YARD_CHECK_CANCEL' => {'expectedVersion', 'reason'},
      'YARD_CHECK_MISSING' => {'expectedTrailerVersion', 'reason'},
      'YARD_CHECK_CORRECTION' => {
        'expectedTrailerVersion',
        'newLocationId',
        'reason',
      },
      _ => <String>{},
    };
    if (body.length != keys.length || !body.keys.every(keys.contains)) {
      throw const FormatException('Invalid frozen Check request');
    }
    for (final key in [
      'siteId',
      'locationId',
      'trailerId',
      'observedLocationId',
      'newLocationId',
    ]) {
      if (body[key] != null) yardUuid(body[key]);
    }
    for (final key in ['expectedVersion', 'expectedTrailerVersion']) {
      if (keys.contains(key) && (body[key] is! int || (body[key] as int) < 0)) {
        throw const FormatException('Invalid frozen Check version');
      }
    }
    if (c.commandType == 'YARD_CHECK_SCAN') {
      yardUuid(body['trailerId']);
      yardUuid(body['observedLocationId']);
      checkTime(body['scannedAt']);
      if (body['observedLocationId'] != intent['observedLocationId'] ||
          body['scannedAt'] != intent['_capturedAt']) {
        throw const FormatException('Frozen observation changed');
      }
    }
    for (final key in [
      'siteId',
      'locationId',
      'reason',
      'expectedTrailerVersion',
      'newLocationId',
    ]) {
      if (keys.contains(key) && body[key] != intent[key]) {
        throw const FormatException('Frozen Check intent changed');
      }
    }
    if (keys.contains('expectedVersion')) {
      final reviewed = jsonDecode(intent['review'] as String) as List;
      final summary = CheckSummary(checkMap(reviewed.first));
      if (summary.id != c.aggregateId ||
          summary.status != 'IN_PROGRESS' ||
          summary.version != body['expectedVersion']) {
        throw const FormatException('Completion version was not reviewed');
      }
    }
  }

  Future<String> acknowledge(
    LocalCommand c,
    SessionSnapshot o,
    Map<String, dynamic> response,
    Map<String, dynamic> body,
  ) async {
    final id = validate(c, response, body);
    if (c.commandType == 'YARD_CHECK_SCAN' ||
        c.commandType == 'YARD_CHECK_COMPLETE' ||
        c.commandType == 'YARD_CHECK_CANCEL') {
      final cached = await cache.find(o, id);
      final summary = CheckSummary(
        c.commandType == 'YARD_CHECK_SCAN'
            ? checkMap(response['check'])
            : response,
      );
      if (cached == null ||
          summary.origin !=
              CheckSummary(checkMap(jsonDecode(cached.summaryJson))).origin) {
        throw const FormatException(
          'ACK Check origin does not match authorized snapshot',
        );
      }
      if (c.commandType == 'YARD_CHECK_SCAN') {
        final item = CheckItem(checkMap(response['item']));
        final prior = (await cache.items(
          o,
          id,
        )).where((r) => r.trailerId == item.trailerId).firstOrNull;
        if ((prior != null &&
                CheckItem(checkMap(jsonDecode(prior.originalJson))).origin !=
                    item.origin) ||
            (prior == null && item.expected)) {
          throw const FormatException('ACK expected inventory changed');
        }
      }
    }
    await db.transaction(() async {
      await db
          .into(db.commandResults)
          .insert(
            CommandResultsCompanion.insert(
              clientOperationId: c.clientOperationId,
              userId: c.userId,
              tenantId: c.tenantId,
              resultKind: 'YARD_CHECK',
              serverId: id,
              projectionJson: jsonEncode(response),
              confirmedAt: commands.now(),
            ),
            mode: InsertMode.insertOrIgnore,
          );
      if (c.commandType == 'YARD_CHECK_START') {
        final historical = CheckSummary(checkMap(response['check']));
        final previous = await cache.find(o, id);
        if (previous == null) {
          await cache.summary(o, historical, associate: true);
        } else {
          if (CheckSummary(checkMap(jsonDecode(previous.summaryJson))).origin !=
              historical.origin) {
            throw const FormatException('Historical start identity changed');
          }
          await (db.update(db.cachedYardChecks)..where(
                (r) =>
                    r.userId.equals(o.userId) &
                    r.tenantId.equals(o.membership.tenantId) &
                    r.id.equals(id),
              ))
              .write(const CachedYardChecksCompanion(associated: Value(true)));
        }
      }
      await commands.result(c, CommandState.acknowledged, status: 200);
    });
    return id;
  }
}
