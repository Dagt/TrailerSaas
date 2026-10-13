import 'dart:convert';

import 'package:drift/drift.dart';

import '../media/media_file_store.dart';
import '../persistence/app_database.dart';
import '../road/online_command_journal.dart';
import '../sync/command_store.dart';
import 'yard_contracts.dart';

/// Explicit workflow types on the original command queue; no generic Trailer editor.
class YardCommandIntent extends CommandIntent {
  YardCommandIntent(
    this.kind,
    super.command, {
    super.dependencies,
    super.resources,
  });
  final String kind;
  static const physicalTypes = {
    'YARD_MOVE',
    'LOCATION_CORRECTION',
    'LOAD_STATE_CHANGE',
    'OOS',
  };
  static bool recognizes(LocalCommand c) =>
      c.aggregateType == 'TRAILER' &&
      {...physicalTypes, 'DISCREPANCY_REPORT'}.contains(c.commandType);
  static bool changesVersion(LocalCommand c) =>
      recognizes(c) && physicalTypes.contains(c.commandType);
  static String capabilityFor(String kind) => switch (kind) {
    'LOCATION_CORRECTION' => 'TRAILER_LOCATION_CORRECT',
    'LOAD_STATE_CHANGE' => 'TRAILER_STATE_CORRECT',
    'OOS' => 'TRAILER_OOS_MARK',
    _ => 'YARD_MOVE_EXECUTE',
  };
  static String endpoint(String tenant, String kind) =>
      'tenants/$tenant/${switch (kind) {
        'YARD_MOVE' => 'yard-moves',
        'LOCATION_CORRECTION' => 'yard/location-corrections',
        'LOAD_STATE_CHANGE' => 'yard/load-state-changes',
        'OOS' => 'yard/out-of-service',
        'DISCREPANCY_REPORT' => 'yard/discrepancy-reports',
        _ => throw const FormatException('Unsupported Yard command'),
      }}';
  @override
  String get type => kind;
  @override
  String get aggregateType => 'TRAILER';
  @override
  String get aggregate => command.body['trailerId'] as String;
  @override
  DateTime get occurredAt =>
      DateTime.parse(command.body['observedAt'] as String).toUtc();
  @override
  void validate(String tenant) {
    final b = command.body;
    final keys = switch (kind) {
      'YARD_MOVE' => {
        'trailerId',
        'fromLocationId',
        'toLocationId',
        'observedAt',
        'expectedTrailerVersion',
      },
      'LOCATION_CORRECTION' => {
        'trailerId',
        'siteId',
        'newLocationId',
        'observedAt',
        'expectedTrailerVersion',
        'reason',
      },
      'LOAD_STATE_CHANGE' => {
        'trailerId',
        'siteId',
        'loadState',
        'observedAt',
        'expectedTrailerVersion',
        'reason',
      },
      'OOS' => {
        'trailerId',
        'siteId',
        'documentIds',
        'observedAt',
        'expectedTrailerVersion',
        'comment',
      },
      'DISCREPANCY_REPORT' => {
        'trailerId',
        'siteId',
        'recordedLocationId',
        'reportedLocationId',
        'observedTrailerVersion',
        'observedAt',
        'reason',
      },
      _ => throw const FormatException('Unsupported Yard command'),
    };
    if (!uuidPattern.hasMatch(command.id) ||
        command.tripId != null ||
        command.path != endpoint(tenant, kind) ||
        b.length != keys.length ||
        !b.keys.every(keys.contains) ||
        dependencies.toSet().length != dependencies.length ||
        dependencies.contains(command.id)) {
      throw const FormatException('Invalid Yard command');
    }
    for (final field in [
      'trailerId',
      'siteId',
      'newLocationId',
      'fromLocationId',
      'toLocationId',
      'reportedLocationId',
    ]) {
      if (keys.contains(field) &&
          (b[field] is! String || !uuidPattern.hasMatch(b[field] as String))) {
        throw const FormatException('Invalid Yard identity');
      }
    }
    if (b['observedAt'] is! String ||
        occurredAt.isAfter(DateTime.now().toUtc())) {
      throw const FormatException('Invalid observation time');
    }
    if (kind == 'YARD_MOVE' && b['fromLocationId'] == b['toLocationId']) {
      throw const FormatException('Origin and destination must differ');
    }
    if (kind == 'DISCREPANCY_REPORT') {
      if (b['recordedLocationId'] != null &&
          (b['recordedLocationId'] is! String ||
              !uuidPattern.hasMatch(b['recordedLocationId'] as String))) {
        throw const FormatException('Invalid historical location');
      }
      if (b['recordedLocationId'] == b['reportedLocationId']) {
        throw const FormatException('No discrepancy');
      }
      if (b['observedTrailerVersion'] is! int ||
          (b['observedTrailerVersion'] as int) < 0) {
        throw const FormatException('Invalid historical version');
      }
    } else {
      final version = b['expectedTrailerVersion'];
      if (!(version is int && version >= 0) &&
          !(version is Map &&
              version.length == 1 &&
              version['operationId'] is String &&
              uuidPattern.hasMatch(version['operationId'] as String) &&
              dependencies.contains(version['operationId']))) {
        throw const FormatException('Invalid Trailer version dependency');
      }
    }
    if (kind == 'LOAD_STATE_CHANGE' &&
        !{'EMPTY', 'LOADED', 'UNKNOWN'}.contains(b['loadState'])) {
      throw const FormatException('Invalid Load State');
    }
    for (final field in ['reason', 'comment']) {
      if (keys.contains(field) &&
          (b[field] is! String ||
              (b[field] as String).trim().isEmpty ||
              (b[field] as String).length > 4000)) {
        throw const FormatException('A reason is required');
      }
    }
    if (kind == 'OOS') {
      final photos = b['documentIds'];
      if (photos is! List ||
          photos.isEmpty ||
          photos.length > 50 ||
          photos.toSet().length != photos.length ||
          photos.any((id) => id is! String || !uuidPattern.hasMatch(id))) {
        throw const FormatException('Damage photographs are required');
      }
    }
  }
}

class YardMoveIntent extends YardCommandIntent {
  YardMoveIntent(OnlineCommand command, {super.dependencies, super.resources})
    : super('YARD_MOVE', command);
  static bool recognizes(LocalCommand c) =>
      c.commandType == 'YARD_MOVE' && c.aggregateType == 'TRAILER';
}

class YardReplay {
  YardReplay(this.store);
  final CommandStore store;
  AppDatabase get db => store.db;
  Future<List<LocalCommand>> pending(
    String user,
    String tenant,
    String trailer,
  ) async {
    final reviewed = await store.reviewedYardFailures(user, tenant);
    return (await store.list(user, tenant))
        .where(
          (c) =>
              c.state != 'ACKNOWLEDGED' &&
              !reviewed.contains(c.clientOperationId) &&
              (jsonDecode(c.resourceKeysJson) as List).contains(
                'trailer:$trailer',
              ),
        )
        .toList();
  }

  String? plannedLocation(
    Map<String, dynamic> trailer,
    List<LocalCommand> pending,
  ) {
    String? location = (trailer['currentLocation'] as Map?)?['id'] as String?;
    for (final c in pending) {
      if (!YardCommandIntent.recognizes(c) ||
          {'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(c.state)) {
        throw StateError('Review the earlier operation');
      }
      final body = jsonDecode(c.payloadJson) as Map;
      if (c.commandType == 'YARD_MOVE') {
        location = body['toLocationId'] as String;
      }
      if (c.commandType == 'LOCATION_CORRECTION') {
        location = body['newLocationId'] as String;
      }
    }
    return location;
  }

  Future<void> capture(
    String user,
    String tenant,
    Map<String, dynamic> trailer,
    String destination,
    DateTime observedAt, {
    String? reviewedOrigin,
    bool Function()? stillCurrent,
  }) => captureAction(
    user,
    tenant,
    trailer,
    'YARD_MOVE',
    {'toLocationId': destination},
    observedAt,
    reviewedOrigin: reviewedOrigin,
    stillCurrent: stillCurrent,
  ).then((_) {});

  Future<String?> captureAction(
    String user,
    String tenant,
    Map<String, dynamic> trailer,
    String kind,
    Map<String, dynamic> fields,
    DateTime observedAt, {
    String? id,
    String? reviewedOrigin,
    bool Function()? stillCurrent,
    bool reviewedConflict = false,
    Object? originalVersion,
  }) => db.transaction(() async {
    if (stillCurrent != null && !stillCurrent()) {
      return null;
    }
    YardTrailer.fromJson(trailer);
    final trailerId = trailer['trailerId'] as String;
    if (fields.keys.any(
      {
        'trailerId',
        'observedAt',
        'expectedTrailerVersion',
        'fromLocationId',
        'recordedLocationId',
        'observedTrailerVersion',
      }.contains,
    )) {
      throw const FormatException('Reserved Yard intent fields');
    }
    fields = {...fields};
    for (final key in ['reason', 'comment']) {
      if (fields[key] is String) {
        fields[key] = (fields[key] as String).trim();
      }
    }
    if (trailer['active'] != true) {
      throw const FormatException('Inactive Trailer');
    }
    var earlier = kind == 'DISCREPANCY_REPORT'
        ? <LocalCommand>[]
        : await pending(user, tenant, trailerId);
    if (stillCurrent != null && !stillCurrent()) {
      return null;
    }
    final reviews = <LocalCommand>[];
    if (reviewedConflict) {
      if (kind == 'DISCREPANCY_REPORT' ||
          earlier.isEmpty ||
          earlier.any(
            (c) =>
                !YardCommandIntent.changesVersion(c) ||
                !{'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(c.state),
          )) {
        throw StateError('Uncertain operations cannot be replaced');
      }
      reviews.addAll(earlier);
      earlier = [];
    }
    final from = kind == 'DISCREPANCY_REPORT'
        ? null
        : plannedLocation(trailer, earlier);
    if (kind == 'YARD_MOVE') {
      if (!YardTrailer.fromJson(trailer).movable ||
          earlier.any((c) => c.commandType == 'OOS') ||
          from == null ||
          from == fields['toLocationId'] ||
          reviewedOrigin != null && reviewedOrigin != from) {
        throw StateError(
          'The reviewed physical origin changed or is incompatible',
        );
      }
    }
    final predecessor = earlier
        .where(YardCommandIntent.changesVersion)
        .lastOrNull;
    final version =
        originalVersion ??
        (predecessor == null
            ? trailer['version']
            : {'operationId': predecessor.clientOperationId});
    final referenceId = version is Map
        ? version['operationId'] as String?
        : null;
    final operation = id ?? operationUuid();
    final body = <String, dynamic>{
      'trailerId': trailerId,
      ...fields,
      'observedAt': observedAt.toUtc().toIso8601String(),
      if (kind != 'DISCREPANCY_REPORT') 'expectedTrailerVersion': version,
      if (kind == 'DISCREPANCY_REPORT')
        'recordedLocationId': (trailer['currentLocation'] as Map?)?['id'],
      if (kind == 'DISCREPANCY_REPORT')
        'observedTrailerVersion': trailer['version'],
      if (kind == 'YARD_MOVE') 'fromLocationId': from,
    };
    final intent = YardCommandIntent(
      kind,
      OnlineCommand(
        operation,
        null,
        YardCommandIntent.endpoint(tenant, kind),
        body,
      ),
      dependencies: [?referenceId],
      resources: [
        kind == 'DISCREPANCY_REPORT'
            ? 'yard-observation:$trailerId'
            : 'trailer:$trailerId',
      ],
    );
    intent.validate(tenant);
    if (kind == 'OOS') {
      for (final localId in body['documentIds'] as List) {
        final media =
            await (db.select(db.localMedia)..where(
                  (r) =>
                      r.id.equals(localId as String) &
                      r.userId.equals(user) &
                      r.tenantId.equals(tenant),
                ))
                .getSingleOrNull();
        if (media == null ||
            media.trailerId != trailerId ||
            media.documentType != 'DAMAGE_PHOTO' ||
            media.fileState != 'PRESENT') {
          throw const FormatException('Unavailable Trailer photograph');
        }
      }
    }
    await store.enqueue(
      user,
      tenant,
      intent,
      followResources: kind != 'DISCREPANCY_REPORT' && !reviewedConflict,
    );
    if (kind == 'OOS') {
      final photos = body['documentIds'] as List;
      for (var n = 0; n < photos.length; n++) {
        await db
            .into(db.commandMediaDependencies)
            .insert(
              CommandMediaDependenciesCompanion.insert(
                clientOperationId: operation,
                userId: user,
                tenantId: tenant,
                mediaId: photos[n] as String,
                fieldPath: 'documentIds.$n',
              ),
            );
      }
    }
    for (final failed in reviews) {
      await db
          .into(db.yardConflictReviews)
          .insert(
            YardConflictReviewsCompanion.insert(
              failedOperationId: failed.clientOperationId,
              replacementOperationId: operation,
              userId: user,
              tenantId: tenant,
              trailerId: trailerId,
              reviewedVersion: trailer['version'] as int,
              reviewedProjectionJson: jsonEncode(trailer),
              reviewedAt: store.now(),
            ),
          );
      await (db.update(db.localSyncIssues)..where(
            (r) =>
                r.clientOperationId.equals(failed.clientOperationId) &
                r.userId.equals(user) &
                r.tenantId.equals(tenant),
          ))
          .write(LocalSyncIssuesCompanion(resolvedAt: Value(store.now())));
    }
    return operation;
  });
  Future<List<CommandMediaDependency>> mediaEdges(LocalCommand c) =>
      (db.select(db.commandMediaDependencies)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId),
          ))
          .get();
  Future<bool> mediaReady(LocalCommand c) async {
    final edges = await mediaEdges(c);
    if (c.commandType != 'OOS') {
      return edges.isEmpty;
    }
    final body = jsonDecode(c.payloadJson) as Map;
    final photos = body['documentIds'];
    if (photos is! List || edges.length != photos.length || photos.isEmpty) {
      throw const FormatException('Incomplete immutable photo set');
    }
    final serverIds = <String>{};
    for (var n = 0; n < photos.length; n++) {
      final edge = edges
          .where(
            (e) => e.fieldPath == 'documentIds.$n' && e.mediaId == photos[n],
          )
          .singleOrNull;
      if (edge == null) {
        throw const FormatException('Photo binding changed');
      }
      final row =
          await (db.select(db.localMedia)..where(
                (r) =>
                    r.id.equals(edge.mediaId) &
                    r.userId.equals(c.userId) &
                    r.tenantId.equals(c.tenantId),
              ))
              .getSingleOrNull();
      if (row == null ||
          row.trailerId != c.aggregateId ||
          row.documentType != 'DAMAGE_PHOTO') {
        throw const FormatException('Invalid Trailer photograph');
      }
      if (row.state != 'FINALIZED' || row.serverDocumentId == null) {
        return false;
      }
      if (!serverIds.add(row.serverDocumentId!)) {
        throw const FormatException('Duplicate finalized document');
      }
    }
    return true;
  }

  Future<MaterializedRequest?> frozen(LocalCommand c) =>
      (db.select(db.materializedRequests)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId),
          ))
          .getSingleOrNull();
  Future<MaterializedRequest?> materialize(
    LocalCommand c,
  ) => db.transaction(() async {
    final old = await frozen(c);
    if (old != null) {
      return old;
    }
    if (!YardCommandIntent.recognizes(c) ||
        c.attemptCount != 0 ||
        c.state == 'SENDING') {
      throw const FormatException('Invalid pending Yard request');
    }
    final body = jsonDecode(c.payloadJson) as Map<String, dynamic>;
    final dependencies = List<String>.from(
      jsonDecode(c.dependenciesJson) as List,
    );
    YardCommandIntent(
      c.commandType,
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
          !YardCommandIntent.changesVersion(source) ||
          source.aggregateId != c.aggregateId ||
          !dependencies.contains(source.clientOperationId)) {
        throw const FormatException('Invalid physical predecessor');
      }
      if (source.state != 'ACKNOWLEDGED') {
        return null;
      }
      final receipt =
          await (db.select(db.commandResults)..where(
                (r) =>
                    r.clientOperationId.equals(source.clientOperationId) &
                    r.userId.equals(c.userId) &
                    r.tenantId.equals(c.tenantId) &
                    r.resultKind.equals('TRAILER'),
              ))
              .getSingleOrNull();
      if (receipt == null || receipt.serverId != c.aggregateId) {
        return null;
      }
      final request = await frozen(source);
      if (request == null) {
        throw const FormatException('Missing confirmed request');
      }
      final response =
          jsonDecode(receipt.projectionJson) as Map<String, dynamic>;
      validateResponse(
        source,
        response,
        jsonDecode(request.payloadJson) as Map<String, dynamic>,
      );
      if (c.commandType == 'YARD_MOVE' &&
          ((response['trailer'] as Map)['currentLocation'] as Map?)?['id'] !=
              body['fromLocationId']) {
        throw const FormatException(
          'Confirmed origin differs from reviewed origin',
        );
      }
      body['expectedTrailerVersion'] = (response['trailer'] as Map)['version'];
    }
    if (!await mediaReady(c)) {
      return null;
    }
    if (c.commandType == 'OOS') {
      final resolved = <String>[];
      for (final edge
          in (await mediaEdges(c))..sort(
            (a, b) =>
                int.parse(a.fieldPath.split('.').last)
                    .compareTo(int.parse(b.fieldPath.split('.').last)),
          )) {
        final row =
            await (db.select(db.localMedia)..where(
                  (r) =>
                      r.id.equals(edge.mediaId) &
                      r.userId.equals(c.userId) &
                      r.tenantId.equals(c.tenantId),
                ))
                .getSingle();
        resolved.add(row.serverDocumentId!);
      }
      body['documentIds'] = resolved;
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
    final raw = response['trailer'];
    if (raw is! Map<String, dynamic>) {
      throw const FormatException('Invalid Yard acknowledgment');
    }
    YardTrailer.fromJson(raw);
    if (raw['trailerId'] != c.aggregateId || raw['active'] != true) {
      throw const FormatException('Wrong Trailer acknowledgment');
    }
    if (c.commandType == 'DISCREPANCY_REPORT') {
      final report = response['report'];
      if (response.length != 2 ||
          report is! Map ||
          report.length != 11 ||
          report['id'] is! String ||
          !uuidPattern.hasMatch(report['id'] as String) ||
          report['trailerId'] != c.aggregateId ||
          report['siteId'] != request['siteId'] ||
          report['recordedLocationId'] != request['recordedLocationId'] ||
          report['reportedLocationId'] != request['reportedLocationId'] ||
          report['observedTrailerVersion'] !=
              request['observedTrailerVersion'] ||
          report['actorUserId'] != c.userId ||
          report['reason'] != request['reason'] ||
          report['clientOperationId'] != c.clientOperationId ||
          DateTime.parse(report['observedAt'] as String).toUtc() !=
              DateTime.parse(request['observedAt'] as String).toUtc()) {
        throw const FormatException('Unverified nonmutating report');
      }
      DateTime.parse(report['recordedAt'] as String);
      return;
    }
    final event = response['movementEvent'];
    final expectedType = switch (c.commandType) {
      'YARD_MOVE' => 'YARD_MOVED',
      'LOCATION_CORRECTION' => 'LOCATION_CORRECTED',
      'LOAD_STATE_CHANGE' => 'LOAD_STATE_CHANGED',
      'OOS' => 'MARKED_OOS',
      _ => null,
    };
    if (request['expectedTrailerVersion'] is! int ||
        raw['version'] != (request['expectedTrailerVersion'] as int) + 1 ||
        event is! Map ||
        event['id'] is! String ||
        !uuidPattern.hasMatch(event['id'] as String) ||
        event['eventType'] != expectedType ||
        event['occurredAt'] is! String ||
        event['recordedAt'] is! String ||
        DateTime.parse(event['occurredAt'] as String).toUtc() !=
            DateTime.parse(request['observedAt'] as String).toUtc()) {
      throw const FormatException('Unverified physical acknowledgment');
    }
    DateTime.parse(event['recordedAt'] as String);
    if (c.commandType == 'YARD_MOVE' &&
        (raw['locationState'] != 'AT_LOCATION' ||
            (raw['currentLocation'] as Map?)?['id'] !=
                request['toLocationId'] ||
            raw['couplingState'] != 'UNCOUPLED' ||
            raw['serviceabilityState'] != 'IN_SERVICE')) {
      throw const FormatException('Unverified Yard Move');
    }
    if (c.commandType == 'LOCATION_CORRECTION' &&
        (raw['locationState'] != 'AT_LOCATION' ||
            (raw['currentLocation'] as Map?)?['id'] !=
                request['newLocationId'] ||
            raw['lastKnownLocationId'] != request['newLocationId'])) {
      throw const FormatException('Unverified location correction');
    }
    if (c.commandType == 'LOAD_STATE_CHANGE' &&
        raw['loadState'] != request['loadState']) {
      throw const FormatException('Unverified load correction');
    }
    if (c.commandType == 'OOS' &&
        (raw['serviceabilityState'] != 'OUT_OF_SERVICE' ||
            response['documentIds'] is! List ||
            (response['documentIds'] as List).toSet().length !=
                (request['documentIds'] as List).length ||
            !(response['documentIds'] as List).toSet().containsAll(
              request['documentIds'] as List,
            ))) {
      throw const FormatException('Unverified OOS/evidence acknowledgment');
    }
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
