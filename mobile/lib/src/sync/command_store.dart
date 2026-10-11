import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../road/online_command_journal.dart';

enum CommandState {
  queued('QUEUED'),
  sending('SENDING'),
  acknowledged('ACKNOWLEDGED'),
  retryableFailure('RETRYABLE_FAILURE'),
  conflict('CONFLICT'),
  blocked('BLOCKED'),
  permanentFailure('PERMANENT_FAILURE');

  const CommandState(this.value);
  final String value;
}

/// Immutable, explicit endpoint intent. Neither tokens nor arbitrary request fields
/// enter SQLite. Authorization and semantic eligibility remain server decisions.
class CommandIntent {
  CommandIntent(
    this.command, {
    this.dependencies = const [],
    this.resources = const [],
  });
  final OnlineCommand command;
  final List<String> dependencies, resources;
  String get type => command.path.split('/').last;
  String get aggregate => command.path.split('/')[3];
  DateTime get occurredAt =>
      DateTime.parse(command.body['occurredAt'] as String).toUtc();

  void validate(String tenant) {
    if (!RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
        ).hasMatch(command.id) ||
        !command.belongsTo(tenant) ||
        dependencies.toSet().length != dependencies.length ||
        dependencies.contains(command.id)) {
      throw const FormatException('Invalid command');
    }
    final allowed = switch (type) {
      'hooks' => {
        'trailerId',
        'tractorId',
        'driverId',
        'expectedTrailerVersion',
        'occurredAt',
      },
      'drops' => {'trailerId', 'stopId', 'locationId', 'occurredAt'},
      'arrivals' => {'source', 'coordinates', 'occurredAt'},
      'departures' => {'fromStopId', 'nextStopId', 'occurredAt'},
      'service-starts' => {'occurredAt'},
      'completions' => {'notes', 'occurredAt'},
      _ => <String>{},
    };
    if (!command.body.keys.every(allowed.contains) ||
        !command.body.containsKey('occurredAt') ||
        occurredAt.isAfter(DateTime.now().toUtc())) {
      throw const FormatException('Invalid payload');
    }
    final required = switch (type) {
      'hooks' => {
        'trailerId',
        'tractorId',
        'driverId',
        'expectedTrailerVersion',
      },
      'drops' => {'trailerId', 'stopId', 'locationId'},
      'departures' => {'nextStopId'},
      'arrivals' => {'source'},
      _ => <String>{},
    };
    if (required.any((k) => command.body[k] == null) ||
        (type == 'arrivals' && command.body['source'] != 'MANUAL')) {
      throw const FormatException('Unsupported command payload');
    }
    // Geofence/coordinates capture remains #47. Preserve legacy manual coordinates
    // only as typed, bounded evidence, never authority.
    final coordinates = command.body['coordinates'];
    if (coordinates != null &&
        (coordinates is! Map ||
            !coordinates.keys.every(
              {'latitude', 'longitude', 'accuracyM'}.contains,
            ))) {
      throw const FormatException('Invalid coordinates');
    }
  }
}

class CommandStore {
  CommandStore(this.db, {DateTime Function()? clock})
    : now = clock ?? (() => DateTime.now().toUtc());
  final AppDatabase db;
  final DateTime Function() now;

  Future<List<LocalCommand>> list(String user, String tenant) =>
      (db.select(db.localCommands)
            ..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))
            ..orderBy([(r) => OrderingTerm.asc(r.queueSequence)]))
          .get();
  Stream<List<LocalCommand>> watch(String user, String tenant) =>
      (db.select(db.localCommands)
            ..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))
            ..orderBy([(r) => OrderingTerm.asc(r.queueSequence)]))
          .watch();
  Future<LocalCommand?> find(String user, String tenant, String id) =>
      (db.select(db.localCommands)..where(
            (r) =>
                r.userId.equals(user) &
                r.tenantId.equals(tenant) &
                r.clientOperationId.equals(id),
          ))
          .getSingleOrNull();

  Stream<List<LocalSyncIssue>> watchIssues(String user, String tenant) =>
      (db.select(db.localSyncIssues)
            ..where(
              (r) =>
                  r.userId.equals(user) &
                  r.tenantId.equals(tenant) &
                  r.resolvedAt.isNull(),
            )
            ..orderBy([(r) => OrderingTerm.desc(r.createdAt)]))
          .watch();

  Future<void> enqueue(
    String user,
    String tenant,
    CommandIntent intent, {
    bool followResources = false,
  }) => db.transaction(() async {
    intent.validate(tenant);
    final c = intent.command;
    final existing = await (db.select(
      db.localCommands,
    )..where((r) => r.clientOperationId.equals(c.id))).getSingleOrNull();
    if (existing != null) {
      if (existing.userId != user ||
          existing.tenantId != tenant ||
          existing.endpointPath != c.path ||
          existing.tripId != c.tripId ||
          existing.payloadJson != jsonEncode(c.body) ||
          existing.dependenciesJson != jsonEncode(intent.dependencies)) {
        throw StateError('Operation identity cannot be replaced');
      }
      return;
    }
    final prior = await list(user, tenant);
    final keys = {
      'trip:${c.tripId}',
      ...intent.resources,
      if (c.body['trailerId'] != null) 'trailer:${c.body['trailerId']}',
      if (c.body['tractorId'] != null) 'tractor:${c.body['tractorId']}',
    };
    final deps = intent.dependencies.toSet();
    if (followResources) {
      for (final key in keys) {
        final previous = prior
            .where(
              (r) =>
                  r.state != CommandState.acknowledged.value &&
                  (jsonDecode(r.resourceKeysJson) as List).contains(key),
            )
            .lastOrNull;
        if (previous != null) deps.add(previous.clientOperationId);
      }
    }
    for (final dependency in deps) {
      if (await find(user, tenant, dependency) == null) {
        throw StateError('Dependency must already exist in this namespace');
      }
    }
    // Only earlier immutable edges are accepted: this construction cannot create a cycle.
    await db
        .into(db.localCommands)
        .insert(
          LocalCommandsCompanion.insert(
            clientOperationId: c.id,
            userId: user,
            tenantId: tenant,
            commandType: intent.type,
            queueSequence: prior.isEmpty ? 1 : prior.last.queueSequence + 1,
            endpointPath: c.path,
            httpMethod: 'POST',
            payloadJson: jsonEncode(c.body),
            tripId: c.tripId,
            aggregateId: intent.aggregate,
            resourceKeysJson: jsonEncode(keys.toList()),
            dependenciesJson: jsonEncode(deps.toList()),
            occurredAt: intent.occurredAt,
            createdAt: now(),
            state: CommandState.queued.value,
          ),
        );
  });

  /// Commit and read back before deleting the old receipt. Repeated imports and
  /// crashes after either commit are harmless; ACKNOWLEDGED rows never resurrect.
  Future<void> importLegacy(
    String user,
    String tenant,
    OnlineCommandJournal legacy,
  ) async {
    final old = await legacy.read(user, tenant);
    if (old == null) return;
    await enqueue(user, tenant, CommandIntent(old));
    final row = await find(user, tenant, old.id);
    if (row == null ||
        row.endpointPath != old.path ||
        row.payloadJson != jsonEncode(old.body)) {
      throw StateError('Receipt migration was not verified');
    }
    await legacy.clear(user, tenant, operationId: old.id);
  }

  Future<void> recoverSending(String user, String tenant) =>
      (db.update(db.localCommands)..where(
            (r) =>
                r.userId.equals(user) &
                r.tenantId.equals(tenant) &
                r.state.equals(CommandState.sending.value),
          ))
          .write(
            LocalCommandsCompanion(
              state: Value(CommandState.retryableFailure.value),
              nextRetryAt: Value(now()),
              lastErrorCode: const Value('INTERRUPTED'),
            ),
          );

  Future<bool> claim(LocalCommand c) async =>
      await (db.update(db.localCommands)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId) &
                r.state.equals(c.state),
          ))
          .write(
            LocalCommandsCompanion(
              state: Value(CommandState.sending.value),
              attemptCount: Value(c.attemptCount + 1),
              nextRetryAt: const Value(null),
            ),
          ) ==
      1;

  Future<void> result(
    LocalCommand c,
    CommandState state, {
    int? status,
    String? code,
    DateTime? retryAt,
    String? category,
  }) => db.transaction(() async {
    code = safeCode(code);
    await (db.update(db.localCommands)..where(
          (r) =>
              r.clientOperationId.equals(c.clientOperationId) &
              r.userId.equals(c.userId) &
              r.tenantId.equals(c.tenantId),
        ))
        .write(
          LocalCommandsCompanion(
            state: Value(state.value),
            lastHttpStatus: Value(status),
            lastErrorCode: Value(code),
            nextRetryAt: Value(retryAt),
            ackedAt: Value(state == CommandState.acknowledged ? now() : null),
          ),
        );
    if (category != null) {
      await db
          .into(db.localSyncIssues)
          .insertOnConflictUpdate(
            LocalSyncIssuesCompanion.insert(
              id: c.clientOperationId,
              clientOperationId: c.clientOperationId,
              userId: c.userId,
              tenantId: c.tenantId,
              category: category,
              errorCode: code ?? 'REVIEW_REQUIRED',
              relatedResource: c.tripId,
              message: safeMessage(category),
              createdAt: now(),
            ),
          );
    } else if (state == CommandState.acknowledged) {
      await (db.update(db.localSyncIssues)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId),
          ))
          .write(LocalSyncIssuesCompanion(resolvedAt: Value(now())));
    }
  });

  /// Explicit review retries the SAME immutable operation, never changes payload
  /// or labels a rejected physical action as performed. Server must re-authorize.
  Future<void> retryReviewed(String user, String tenant, String id) =>
      db.transaction(() async {
        final c = await find(user, tenant, id);
        if (c == null ||
            !{
              CommandState.conflict.value,
              CommandState.permanentFailure.value,
            }.contains(c.state) ||
            c.lastErrorCode == 'IDEMPOTENCY_KEY_REUSED' ||
            c.lastErrorCode == 'INVALID_DEPENDENCY') {
          return;
        }
        if (c.nextRetryAt != null && now().isBefore(c.nextRetryAt!)) return;
        await result(c, CommandState.queued);
      });

  static String? safeCode(String? code) => code == null
      ? null
      : {
          'AUTHENTICATION_REQUIRED',
          'CAPABILITY_DENIED',
          'TENANT_ACCESS_DENIED',
          'CUSTOMER_SCOPE_DENIED',
          'RESOURCE_NOT_FOUND',
          'VALIDATION_FAILED',
          'INVALID_STATE_TRANSITION',
          'STALE_VERSION',
          'RESOURCE_CONFLICT',
          'IDEMPOTENCY_KEY_REUSED',
          'EVIDENCE_REQUIRED',
          'TRAILER_SUBSTITUTION_NOT_AUTHORIZED',
          'LOCATION_MISMATCH',
          'TRAILER_ALREADY_COUPLED',
          'TRACTOR_ALREADY_COUPLED',
          'UNEXPECTED_RESPONSE',
          'UNEXPECTED_FAILURE',
          'RESULT_UNCERTAIN',
          'INVALID_COMMAND',
          'INVALID_DEPENDENCY',
          'DEPENDENCY_FAILED',
          'INTERRUPTED',
          'RATE_LIMITED',
        }.contains(code)
      ? code
      : 'UNEXPECTED_FAILURE';

  static String safeMessage(String category) => switch (category) {
    'AUTHORIZATION' => 'Access or assignment changed. Refresh work and contact dispatch before retrying.',
    'DEPENDENCY' => 'Blocked by a previous operation that requires review.',
    'INVALID_SEQUENCE' =>
      'This operation has an invalid dependency. Contact support.',
    'IDENTITY' => 'Operation identity conflicts with the server. Do not repeat the physical action; contact support.',
    'VALIDATION' =>
      'This recorded action cannot be accepted. Review with dispatch.',
    _ => 'Server or physical state changed. Review current work before retrying the recorded action.',
  };
}
