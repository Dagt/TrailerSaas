import 'dart:convert';

import 'package:drift/drift.dart';

import '../media/media_file_store.dart';
import '../persistence/app_database.dart';
import '../road/online_command_journal.dart';
import '../sync/command_store.dart';
import 'yard_contracts.dart';

/// An unresolved version is a typed dependency, never a guessed future number.
class YardMoveIntent extends CommandIntent {
  YardMoveIntent(super.command, {super.dependencies, super.resources});
  @override
  String get type => 'YARD_MOVE';
  @override
  String get aggregateType => 'TRAILER';
  @override
  String get aggregate => command.body['trailerId'] as String;
  @override
  DateTime get occurredAt =>
      DateTime.parse(command.body['observedAt'] as String).toUtc();
  static bool recognizes(LocalCommand c) =>
      c.commandType == 'YARD_MOVE' && c.aggregateType == 'TRAILER';
  @override
  void validate(String tenant) {
    final b = command.body;
    if (!uuidPattern.hasMatch(command.id) ||
        command.tripId != null ||
        command.path != 'tenants/$tenant/yard-moves' ||
        b.keys.toSet().difference({
          'trailerId',
          'fromLocationId',
          'toLocationId',
          'observedAt',
          'expectedTrailerVersion',
        }).isNotEmpty ||
        b.length != 5 ||
        dependencies.toSet().length != dependencies.length ||
        dependencies.contains(command.id)) {
      throw const FormatException('Invalid Yard Move intent');
    }
    for (final key in ['trailerId', 'fromLocationId', 'toLocationId']) {
      if (b[key] is! String || !uuidPattern.hasMatch(b[key] as String)) {
        throw const FormatException('Invalid Yard identity');
      }
    }
    if (b['fromLocationId'] == b['toLocationId'] ||
        b['observedAt'] is! String ||
        occurredAt.isAfter(DateTime.now().toUtc())) {
      throw const FormatException('Invalid Yard origin/time');
    }
    final version = b['expectedTrailerVersion'];
    if (version is int && version >= 0) return;
    if (version is Map &&
        version.length == 1 &&
        version['operationId'] is String &&
        uuidPattern.hasMatch(version['operationId'] as String) &&
        dependencies.contains(version['operationId'])) {
      return;
    }
    throw const FormatException('Invalid Trailer version reference');
  }
}

/// Uses the same command/result/frozen-request graph as Shift and Road replay.
class YardReplay {
  YardReplay(this.store);
  final CommandStore store;
  AppDatabase get db => store.db;
  Future<void> capture(
    String user,
    String tenant,
    Map<String, dynamic> trailer,
    String destination,
    DateTime observedAt, {
    String? reviewedOrigin,
    bool Function()? stillCurrent,
  }) => db.transaction(() async {
    if (stillCurrent != null && !stillCurrent()) return;
    if (trailer['active'] != true ||
        trailer['locationState'] != 'AT_LOCATION' ||
        trailer['couplingState'] != 'UNCOUPLED' ||
        trailer['serviceabilityState'] != 'IN_SERVICE') {
      throw const FormatException('Trailer cannot be moved');
    }
    final id = trailer['trailerId'] as String;
    final prior = (await store.list(user, tenant))
        .where(
          (r) =>
              r.state != 'ACKNOWLEDGED' &&
              (jsonDecode(r.resourceKeysJson) as List).contains('trailer:$id'),
        )
        .lastOrNull;
    if (stillCurrent != null && !stillCurrent()) return;
    if (prior != null && !YardMoveIntent.recognizes(prior)) {
      throw StateError(
        'Resolve pending Road operations before moving this Trailer',
      );
    }
    if (prior != null &&
        {'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(prior.state)) {
      throw StateError('Resolve the earlier move before capturing another');
    }
    final from = prior == null
        ? (trailer['currentLocation'] as Map)['id']
        : (jsonDecode(prior.payloadJson) as Map)['toLocationId'];
    if (reviewedOrigin != null && from != reviewedOrigin) {
      throw StateError('The reviewed origin changed');
    }
    final command = OnlineCommand(
      operationUuid(),
      null,
      'tenants/$tenant/yard-moves',
      {
        'trailerId': id,
        'fromLocationId': from,
        'toLocationId': destination,
        'observedAt': observedAt.toUtc().toIso8601String(),
        'expectedTrailerVersion': prior == null
            ? trailer['version']
            : {'operationId': prior.clientOperationId},
      },
    );
    await store.enqueue(
      user,
      tenant,
      YardMoveIntent(
        command,
        dependencies: [if (prior != null) prior.clientOperationId],
        resources: ['trailer:$id'],
      ),
      followResources: true,
    );
  });
  Future<MaterializedRequest?> frozen(LocalCommand c) =>
      (db.select(db.materializedRequests)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId),
          ))
          .getSingleOrNull();
  Future<MaterializedRequest?> materialize(LocalCommand c) =>
      db.transaction(() async {
        final old = await frozen(c);
        if (old != null) return old;
        if (!YardMoveIntent.recognizes(c) ||
            c.attemptCount != 0 ||
            c.state == 'SENDING') {
          throw const FormatException('Invalid pending Yard request');
        }
        final body = jsonDecode(c.payloadJson) as Map<String, dynamic>;
        final dependencies = List<String>.from(
          jsonDecode(c.dependenciesJson) as List,
        );
        YardMoveIntent(
          OnlineCommand(c.clientOperationId, null, c.endpointPath, body),
          dependencies: dependencies,
        ).validate(c.tenantId);
        final reference = body['expectedTrailerVersion'];
        if (reference is Map) {
          final source = await store.find(
            c.userId,
            c.tenantId,
            reference['operationId'] as String,
          );
          if (source == null ||
              !YardMoveIntent.recognizes(source) ||
              source.aggregateId != c.aggregateId ||
              !dependencies.contains(source.clientOperationId)) {
            throw const FormatException('Invalid Yard predecessor');
          }
          if (source.state != 'ACKNOWLEDGED') return null;
          final result =
              await (db.select(db.commandResults)..where(
                    (r) =>
                        r.clientOperationId.equals(source.clientOperationId) &
                        r.userId.equals(c.userId) &
                        r.tenantId.equals(c.tenantId) &
                        r.resultKind.equals('TRAILER'),
                  ))
                  .getSingleOrNull();
          if (result == null || result.serverId != c.aggregateId) return null;
          final response =
              jsonDecode(result.projectionJson) as Map<String, dynamic>;
          final request = await frozen(source);
          if (request == null) {
            throw const FormatException('Missing confirmed request');
          }
          validateResponse(
            source,
            response,
            jsonDecode(request.payloadJson) as Map<String, dynamic>,
          );
          final confirmed = response['trailer'] as Map;
          if ((confirmed['currentLocation'] as Map)['id'] !=
              body['fromLocationId']) {
            throw const FormatException(
              'Confirmed origin differs from planned origin',
            );
          }
          body['expectedTrailerVersion'] = confirmed['version'];
        }
        await db
            .into(db.materializedRequests)
            .insert(
              MaterializedRequestsCompanion.insert(
                clientOperationId: c.clientOperationId,
                userId: c.userId,
                tenantId: c.tenantId,
                endpointPath: c.endpointPath,
                httpMethod: c.httpMethod,
                payloadJson: jsonEncode(body),
                createdAt: store.now(),
              ),
            );
        return frozen(c);
      });
  static void validateResponse(
    LocalCommand c,
    Map<String, dynamic> response,
    Map<String, dynamic> request,
  ) {
    final trailer = response['trailer'], event = response['movementEvent'];
    if (trailer is! Map<String, dynamic>) {
      throw const FormatException('Invalid Yard acknowledgment');
    }
    YardTrailer.fromJson(trailer);
    if (trailer['trailerId'] != c.aggregateId ||
        trailer['active'] != true ||
        trailer['locationState'] != 'AT_LOCATION' ||
        trailer['currentLocation'] is! Map ||
        (trailer['currentLocation'] as Map)['id'] != request['toLocationId'] ||
        trailer['version'] is! int ||
        request['expectedTrailerVersion'] is! int ||
        trailer['version'] != (request['expectedTrailerVersion'] as int) + 1 ||
        trailer['couplingState'] != 'UNCOUPLED' ||
        trailer['serviceabilityState'] != 'IN_SERVICE' ||
        event is! Map ||
        event['id'] is! String ||
        !uuidPattern.hasMatch(event['id'] as String) ||
        event['eventType'] != 'YARD_MOVED' ||
        event['occurredAt'] is! String ||
        event['recordedAt'] is! String ||
        DateTime.parse(event['occurredAt'] as String).toUtc() !=
            DateTime.parse(request['observedAt'] as String).toUtc()) {
      throw const FormatException('Unverified Yard acknowledgment');
    }
    DateTime.parse(event['recordedAt'] as String);
  }

  Future<void> acknowledge(
    LocalCommand c,
    Map<String, dynamic> response,
    Map<String, dynamic> request,
  ) => db.transaction(() async {
    validateResponse(c, response, request);
    final old =
        await (db.select(db.commandResults)
              ..where((r) => r.clientOperationId.equals(c.clientOperationId)))
            .getSingleOrNull();
    if (old == null) {
      await db
          .into(db.commandResults)
          .insert(
            CommandResultsCompanion.insert(
              clientOperationId: c.clientOperationId,
              userId: c.userId,
              tenantId: c.tenantId,
              resultKind: 'TRAILER',
              serverId: c.aggregateId!,
              projectionJson: jsonEncode(response),
              confirmedAt: store.now(),
            ),
          );
    } else if (old.userId != c.userId ||
        old.tenantId != c.tenantId ||
        old.resultKind != 'TRAILER' ||
        old.projectionJson != jsonEncode(response)) {
      throw StateError('Confirmed result cannot change');
    }
    await store.result(c, CommandState.acknowledged, status: 200);
  });
}
