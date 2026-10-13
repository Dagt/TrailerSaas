import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../platform/connectivity.dart';
import '../road/online_command_journal.dart';
import '../session/api_client.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import 'command_store.dart';
import '../media/media_processor.dart';
import '../media/media_dependencies.dart';
import '../shift/shift_commands.dart';
import '../yard/yard_commands.dart';
import '../yard/yard_contracts.dart';

/// Foreground/reconnect worker. A single serialized lane for this SQLite database
/// prevents overlapping Trip/Trailer/Tractor sends, including independent chains.
/// No OS background guarantee, client state-machine replay or generic /sync API.
class ReplayWorker extends ChangeNotifier {
  ReplayWorker(
    this.session,
    this.store,
    this.legacy, {
    double Function()? jitter,
    Timer Function(Duration, void Function())? timerFactory,
    this.scheduleTimers = true,
    this.media,
    this.futureEvidence,
    this.shifts,
  }) : jitter = jitter ?? Random.secure().nextDouble,
       timerFactory = timerFactory ?? ((delay, work) => Timer(delay, work));
  final SessionController session;
  final CommandStore store;
  final OnlineCommandJournal legacy;
  final double Function() jitter;
  final Timer Function(Duration, void Function()) timerFactory;
  final bool scheduleTimers;
  final MediaProcessor? media;
  final EvidenceReplayPort? futureEvidence;
  final ShiftReplay? shifts;
  MediaDependencies get bindings => MediaDependencies(store);
  static final _lanes = Expando<Future<void>>();
  Future<void>? _running;
  Timer? _timer;
  DateTime? _scheduledAt;
  StreamSubscription<ConnectivityStatus>? _network;
  bool _started = false, _disposed = false;
  bool _requested = false;
  String? _scope;
  bool _wasReady = false;
  String? message;
  bool get sending => _running != null;

  void start() {
    if (_started) {
      return;
    }
    _started = true;
    session.addListener(_sessionChanged);
    _network = session.network.changes.listen((status) {
      if (status == ConnectivityStatus.available) {
        unawaited(sync());
      }
    });
    _sessionChanged();
  }

  String? get scope {
    final s = session.snapshot;
    return s == null || session.auth.uid != s.firebaseUid
        ? null
        : '${s.firebaseUid}|${s.userId}|${s.membership.tenantId}';
  }

  void _sessionChanged() {
    final next = scope;
    final ready = session.state == SessionState.ready;
    final changed = next != _scope;
    if (changed) {
      media?.forgetGrants();
      _scope = next;
      _timer?.cancel();
      message = null;
      if (!_disposed) {
        notifyListeners();
      }
    }
    // Same-scope offline notifications pause sending, not request another pass.
    // Otherwise a failed authoritative refresh on an available network link can
    // repeatedly schedule itself. Reconnect/resume/manual sync retries explicitly;
    // initial offline namespace activation still imports its legacy receipt.
    final shouldSync =
        (ready && (changed || !_wasReady)) ||
        (session.state == SessionState.offline && changed);
    _wasReady = ready;
    if (shouldSync) {
      unawaited(sync());
    }
  }

  bool _current(SessionSnapshot owner) =>
      !_disposed &&
      session.auth.uid == owner.firebaseUid &&
      session.state == SessionState.ready &&
      session.snapshot?.userId == owner.userId &&
      session.snapshot?.membership.tenantId == owner.membership.tenantId &&
      session.snapshot?.driver?.id == owner.driver?.id &&
      (session.snapshot?.driver != null ||
          session.snapshot!.membership.capabilities.contains(
            'YARD_MOVE_EXECUTE',
          ));

  Future<void> sync() {
    if (_disposed) {
      return Future.value();
    }
    if (_running != null) {
      _requested = true;
      return _running!;
    }
    _timer?.cancel();
    _scheduledAt = null;
    // This lane includes the entire previous cycle and every awaited POST, even
    // when its session becomes offline/loading or changes identity mid-request.
    // A reconnect/resume can request another pass, but cannot overtake live HTTP.
    final previous = _lanes[store.db] ?? Future.value();
    final requestedScope = scope;
    final run = previous
        .catchError((Object _) {})
        .then(
          (_) =>
              media == null ? _cycle() : media!.store.files.exclusive(_cycle),
        )
        .catchError((Object _) {
          if (!_disposed && scope == requestedScope) {
            message = 'Synchronization could not access saved operations or connectivity. They are retained. Retry sync.';
          }
        });
    _lanes[store.db] = run.catchError((Object _) {});
    _running = run;
    notifyListeners();
    return run.whenComplete(() {
      _running = null;
      if (!_disposed) {
        notifyListeners();
      }
      if (_requested && !_disposed) {
        _requested = false;
        unawaited(sync());
      }
    });
  }

  Future<void> _cycle() async {
    if (!_disposed &&
        session.auth.uid != null &&
        {
          SessionState.offline,
          SessionState.unavailable,
        }.contains(session.state) &&
        await session.network.currentStatus() == ConnectivityStatus.available) {
      await session.refresh();
    }
    final owner = session.snapshot;
    if (owner == null ||
        _disposed ||
        session.auth.uid != owner.firebaseUid ||
        !{SessionState.ready, SessionState.offline}.contains(session.state)) {
      return;
    }
    try {
      await store.importLegacy(owner.userId, owner.membership.tenantId, legacy);
      if (!_current(owner)) {
        return;
      }
      if (await session.network.currentStatus() ==
              ConnectivityStatus.unavailable ||
          !_current(owner)) {
        return;
      }
      // We now own the database lane: all prior sends have settled or timed out.
      // Any remaining SENDING in this fresh namespace is abandoned, including a
      // same-scope offline/loading interruption. Recover on EVERY pass, not just
      // namespace activation; never reset claims from session/network callbacks.
      // The immutable request and backend ledger resolve an uncertain commit.
      await store.recoverSending(owner.userId, owner.membership.tenantId);
      if (media != null) {
        final before = (await media!.store.list(
          owner.userId,
          owner.membership.tenantId,
        )).where((r) => r.state == 'FINALIZED').length;
        await media!.process(session, owner, () => _current(owner));
        if (!_current(owner)) {
          return;
        }
        final after = (await media!.store.list(
          owner.userId,
          owner.membership.tenantId,
        )).where((r) => r.state == 'FINALIZED').length;
        if (after > before) {
          await session.refresh();
          if (!_current(owner)) {
            return;
          }
        }
      }
      // Bound one foreground pass; another timer/manual/reconnect pass continues
      // a larger backlog. No unlimited mutation burst.
      for (var sent = 0; sent < 100 && _current(owner); sent++) {
        final rows = await store.list(owner.userId, owner.membership.tenantId);
        if (!_current(owner)) {
          return;
        }
        await _dependencies(rows);
        await _mediaDependencies(rows);
        await _shiftDependencies(rows);
        final fresh = await store.list(owner.userId, owner.membership.tenantId);
        final reviewed = await store.reviewedYardFailures(
          owner.userId,
          owner.membership.tenantId,
        );
        final byId = {for (final r in fresh) r.clientOperationId: r};
        final candidate = fresh
            .where(
              (c) =>
                  {
                    CommandState.queued.value,
                    CommandState.retryableFailure.value,
                  }.contains(c.state) &&
                  (c.nextRetryAt == null ||
                      !store.now().isBefore(c.nextRetryAt!)) &&
                  dependencies(c).every(
                    (id) => byId[id]?.state == CommandState.acknowledged.value,
                  ) &&
                  !_earlierSharedResource(c, fresh, reviewed),
            )
            .firstOrNull;
        if (candidate == null) {
          _schedule(fresh);
          await _scheduleMedia(owner);
          return;
        }
        final isShift = ShiftCommandIntent.recognizes(candidate);
        final isYard = YardCommandIntent.recognizes(candidate);
        if (isYard
            ? !owner.membership.capabilities.contains('YARD_MOVE_EXECUTE') ||
                  !owner.membership.capabilities.contains(
                    YardCommandIntent.capabilityFor(candidate.commandType),
                  )
            : owner.driver == null) {
          await store.result(
            candidate,
            CommandState.conflict,
            code: 'CAPABILITY_DENIED',
            category: 'AUTHORIZATION',
          );
          continue;
        }
        if (isYard) {
          try {
            final request = await YardReplay(store).materialize(candidate);
            if (!_current(owner)) {
              return;
            }
            if (request == null) {
              await store.result(
                candidate,
                CommandState.blocked,
                code: 'RESULT_PENDING',
                category: 'DEPENDENCY',
              );
              continue;
            }
            if (!await store.claim(candidate)) {
              continue;
            }
            if (!_current(owner)) {
              return;
            }
            await _send(
              candidate,
              owner,
              jsonDecode(request.payloadJson) as Map<String, dynamic>,
            );
          } on FormatException {
            if (_current(owner)) {
              await store.result(
                candidate,
                CommandState.permanentFailure,
                code: 'INVALID_COMMAND',
                category: 'VALIDATION',
              );
            }
          }
          continue;
        }
        final shiftRequest = isShift
            ? await shifts?.materialize(candidate)
            : null;
        final body = isShift
            ? (shiftRequest == null
                  ? null
                  : jsonDecode(shiftRequest.payloadJson)
                        as Map<String, dynamic>)
            : await bindings.materialize(candidate);
        if (body == null || !_current(owner)) {
          continue;
        }
        if (!await store.claim(candidate)) {
          continue;
        }
        if (!_current(owner)) {
          return; // SENDING is recovered in its original namespace.
        }
        if (isShift) {
          await _sendShift(candidate, owner, body);
        } else {
          await _send(candidate, owner, body);
        }
        if (!_current(owner)) {
          return;
        }
      }
      if (_current(owner)) {
        _schedule(
          await store.list(owner.userId, owner.membership.tenantId),
          more: true,
        );
      }
    } catch (_) {
      if (!_current(owner)) {
        return;
      }
      message = 'Synchronization could not access saved operations. They are retained. Retry sync.';
    }
  }

  Future<void> _mediaDependencies(List<LocalCommand> rows) async {
    for (final c in rows) {
      if (ShiftCommandIntent.recognizes(c)) {
        continue;
      }
      if (YardCommandIntent.recognizes(c)) {
        if ({
          'ACKNOWLEDGED',
          'SENDING',
          'CONFLICT',
          'PERMANENT_FAILURE',
        }.contains(c.state)) {
          continue;
        }
        try {
          final ready = await YardReplay(store).mediaReady(c);
          if (!ready) {
            await store.result(
              c,
              CommandState.blocked,
              code: 'EVIDENCE_PENDING',
              category: 'MEDIA',
            );
          } else if (c.state == 'BLOCKED' &&
              c.lastErrorCode == 'EVIDENCE_PENDING') {
            await store.result(c, CommandState.queued);
          }
        } on FormatException {
          await store.result(
            c,
            CommandState.permanentFailure,
            code: 'INVALID_MEDIA_DEPENDENCY',
            category: 'INVALID_SEQUENCE',
          );
        }
        continue;
      }
      if ({
        'ACKNOWLEDGED',
        'SENDING',
        'CONFLICT',
        'PERMANENT_FAILURE',
      }.contains(c.state)) {
        continue;
      }
      if (EvidenceCommandIntent.kindFor(c) == null) {
        if ((await bindings.edges(c)).isNotEmpty) {
          await store.result(
            c,
            CommandState.blocked,
            code: 'INVALID_MEDIA_DEPENDENCY',
            category: 'INVALID_SEQUENCE',
          );
        }
        continue;
      }
      final ready = await bindings.ready(c);
      final code = !ready
          ? 'EVIDENCE_PENDING'
          : futureEvidence == null || !futureEvidence!.supports(c)
          ? 'FUTURE_WORKFLOW'
          : null;
      if (code != null) {
        await store.result(
          c,
          CommandState.blocked,
          code: code,
          category: 'MEDIA',
        );
      } else if (c.state == 'BLOCKED' &&
          {'EVIDENCE_PENDING', 'FUTURE_WORKFLOW'}.contains(c.lastErrorCode)) {
        await store.result(c, CommandState.queued);
      }
    }
  }

  Future<void> _shiftDependencies(List<LocalCommand> rows) async {
    for (final c in rows) {
      if (!ShiftCommandIntent.recognizes(c) ||
          {
            'ACKNOWLEDGED',
            'SENDING',
            'CONFLICT',
            'PERMANENT_FAILURE',
          }.contains(c.state)) {
        continue;
      }
      String? code;
      try {
        ShiftCommandIntent(
          ShiftCommandIntent.actionFor(c),
          OnlineCommand(
            c.clientOperationId,
            c.tripId,
            c.endpointPath,
            jsonDecode(c.payloadJson) as Map<String, dynamic>,
          ),
        ).validate(c.tenantId);
        if (shifts == null) {
          code = 'FUTURE_WORKFLOW';
        } else if (c.commandType == 'pre-trip' && !await bindings.ready(c)) {
          code = 'EVIDENCE_PENDING';
        } else if (!await shifts!.ready(c)) {
          code = 'RESULT_PENDING';
        }
      } catch (_) {
        code = 'INVALID_COMMAND';
      }
      if (code != null) {
        await store.result(
          c,
          CommandState.blocked,
          code: code,
          category: 'DEPENDENCY',
        );
      } else if (c.state == 'BLOCKED' &&
          {
            'EVIDENCE_PENDING',
            'RESULT_PENDING',
            'FUTURE_WORKFLOW',
          }.contains(c.lastErrorCode)) {
        await store.result(c, CommandState.queued);
      }
    }
  }

  Future<void> _sendShift(
    LocalCommand c,
    SessionSnapshot owner,
    Map<String, dynamic> body,
  ) async {
    try {
      final result = await shifts!.send(c, body);
      if (!_current(owner)) {
        return;
      }
      await shifts!.acknowledge(c, result);
      if (!_current(owner)) {
        return;
      }
      await session.refresh();
      await _refreshExecutionContext(c, owner);
      if (_current(owner)) {
        message =
            'Confirmed Shift/inspection reconciled with current server work.';
      }
    } on ApiProblem catch (e) {
      if (!_current(owner)) {
        return;
      }
      final current = await store.find(
        c.userId,
        c.tenantId,
        c.clientOperationId,
      );
      if (current?.state == 'ACKNOWLEDGED') {
        return;
      }
      if ({401, 403, 404, 409}.contains(e.status)) {
        await store.result(
          c,
          CommandState.conflict,
          status: e.status,
          code: e.code,
          category: e.status == 409 ? 'STATE' : 'AUTHORIZATION',
        );
        if (e.status == 401 || e.status == 403) {
          await session.rejectOperationalContext(e);
        } else {
          await session.refresh();
        }
      } else if (e.status == 408 ||
          e.status == 429 ||
          e.status >= 500 ||
          e.code == 'UNEXPECTED_RESPONSE' ||
          e.code == 'UNEXPECTED_FAILURE') {
        await store.result(
          c,
          CommandState.retryableFailure,
          status: e.status,
          code: e.code,
          retryAt: _backoff(c, e.retryAfter),
        );
      } else {
        await store.result(
          c,
          CommandState.permanentFailure,
          status: e.status,
          code: e.code,
          category: 'VALIDATION',
        );
      }
    } on FormatException {
      if (_current(owner)) {
        await store.result(
          c,
          CommandState.retryableFailure,
          code: 'UNEXPECTED_RESPONSE',
          retryAt: _backoff(c, null),
        );
      }
    } catch (_) {
      if (!_current(owner)) {
        return;
      }
      final current = await store.find(
        c.userId,
        c.tenantId,
        c.clientOperationId,
      );
      if (current?.state != 'ACKNOWLEDGED') {
        await store.result(
          c,
          CommandState.retryableFailure,
          code: 'RESULT_UNCERTAIN',
          retryAt: _backoff(c, null),
        );
      }
    }
  }

  Future<void> _scheduleMedia(SessionSnapshot owner) async {
    if (media == null || !scheduleTimers || !_current(owner)) {
      return;
    }
    final at = await media!.nextRetry(owner.userId, owner.membership.tenantId);
    if (at != null && (_scheduledAt == null || at.isBefore(_scheduledAt!))) {
      final delay = at.difference(store.now());
      // Wake both queues at the earlier eligibility. Persisted retry dates keep
      // an early wake from sending too soon; there is no self-retry on offline.
      _timer?.cancel();
      _scheduledAt = at;
      _timer = timerFactory(
        delay > Duration.zero ? delay : const Duration(seconds: 1),
        () => unawaited(sync()),
      );
    }
  }

  static List<String> dependencies(LocalCommand c) =>
      List<String>.from(jsonDecode(c.dependenciesJson) as List);
  static Set<String> resources(LocalCommand c) =>
      Set<String>.from(jsonDecode(c.resourceKeysJson) as List);

  bool _earlierSharedResource(
    LocalCommand c,
    List<LocalCommand> rows,
    Set<String> reviewed,
  ) {
    for (final earlier in rows) {
      if (earlier.clientOperationId == c.clientOperationId) {
        break;
      }
      if (earlier.state != CommandState.acknowledged.value &&
          !(reviewed.contains(earlier.clientOperationId) &&
              {
                'CONFLICT',
                'BLOCKED',
                'PERMANENT_FAILURE',
              }.contains(earlier.state)) &&
          resources(earlier).intersection(resources(c)).isNotEmpty) {
        return true;
      }
    }
    return false;
  }

  Future<void> _dependencies(List<LocalCommand> rows) async {
    final byId = {for (final c in rows) c.clientOperationId: c};
    bool invalid(String id, Set<String> visiting) {
      if (!visiting.add(id)) {
        return true;
      }
      final c = byId[id];
      if (c == null) {
        return true;
      }
      for (final d in dependencies(c)) {
        if (invalid(d, Set.of(visiting))) {
          return true;
        }
      }
      return false;
    }

    for (final c in rows) {
      if ({
        CommandState.acknowledged.value,
        CommandState.sending.value,
      }.contains(c.state)) {
        continue;
      }
      if (invalid(c.clientOperationId, {})) {
        await store.result(
          c,
          CommandState.blocked,
          code: 'INVALID_DEPENDENCY',
          category: 'INVALID_SEQUENCE',
        );
        byId[c.clientOperationId] = c.copyWith(
          state: CommandState.blocked.value,
        );
      } else if (dependencies(c).any(
        (id) => {
          CommandState.conflict.value,
          CommandState.permanentFailure.value,
          CommandState.blocked.value,
        }.contains(byId[id]?.state),
      )) {
        await store.result(
          c,
          CommandState.blocked,
          code: 'DEPENDENCY_FAILED',
          category: 'DEPENDENCY',
        );
        byId[c.clientOperationId] = c.copyWith(
          state: CommandState.blocked.value,
        );
      } else if (c.state == CommandState.blocked.value &&
          c.lastErrorCode == 'DEPENDENCY_FAILED' &&
          dependencies(
            c,
          ).every((id) => byId[id]?.state == CommandState.acknowledged.value)) {
        await store.result(c, CommandState.queued);
        byId[c.clientOperationId] = c.copyWith(
          state: CommandState.queued.value,
        );
      }
    }
  }

  Future<void> _send(
    LocalCommand c,
    SessionSnapshot owner,
    Map<String, dynamic> frozenBody,
  ) async {
    OnlineCommand command;
    try {
      command = OnlineCommand(
        c.clientOperationId,
        c.tripId,
        c.endpointPath,
        frozenBody,
      );
      final kind = EvidenceCommandIntent.kindFor(c);
      if (YardCommandIntent.recognizes(c)) {
        YardCommandIntent(c.commandType, command).validate(c.tenantId);
        if (c.commandType != 'DISCREPANCY_REPORT' &&
            command.body['expectedTrailerVersion'] is! int) {
          throw const FormatException('Yard request must already be frozen');
        }
      } else if (kind == null) {
        CommandIntent(command).validate(c.tenantId);
      } else {
        if (futureEvidence == null) {
          throw const FormatException('Workflow not implemented');
        }
        EvidenceCommandIntent(kind, command).validate(c.tenantId);
      }
    } catch (_) {
      if (_current(owner)) {
        await store.result(
          c,
          CommandState.permanentFailure,
          code: 'INVALID_COMMAND',
          category: 'VALIDATION',
        );
      }
      return;
    }
    try {
      Map<String, dynamic> response;
      try {
        response =
            futureEvidence is TypedEvidenceReplayPort &&
                EvidenceCommandIntent.kindFor(c) != null
            ? await (futureEvidence! as TypedEvidenceReplayPort).sendAuthorized(
                c,
                frozenBody,
                forceRefresh: false,
              )
            : futureEvidence != null && EvidenceCommandIntent.kindFor(c) != null
            ? await futureEvidence!.send(c, frozenBody)
            : await session.api.post(
                command.path,
                command.body,
                command.id,
                forceRefresh: false,
              );
      } on ApiProblem catch (e) {
        if (e.status != 401 || !_current(owner)) {
          rethrow;
        }
        response =
            futureEvidence is TypedEvidenceReplayPort &&
                EvidenceCommandIntent.kindFor(c) != null
            ? await (futureEvidence! as TypedEvidenceReplayPort).sendAuthorized(
                c,
                frozenBody,
                forceRefresh: true,
              )
            : futureEvidence != null && EvidenceCommandIntent.kindFor(c) != null
            ? await futureEvidence!.send(c, frozenBody)
            : await session.api.post(
                command.path,
                command.body,
                command.id,
                forceRefresh: true,
              );
      }
      if (!_current(owner)) {
        return;
      }
      if (futureEvidence is TypedEvidenceReplayPort &&
          EvidenceCommandIntent.kindFor(c) != null) {
        (futureEvidence! as TypedEvidenceReplayPort).validateResponse(
          c,
          response,
          frozenBody,
        );
      } else {
        if (YardCommandIntent.recognizes(c)) {
          YardReplay.validateResponse(c, response, frozenBody);
        } else {
          _validateResponse(c, response);
        }
      }
      if (YardCommandIntent.recognizes(c)) {
        await YardReplay(store).acknowledge(c, response, frozenBody);
      } else {
        await store.result(c, CommandState.acknowledged, status: 200);
      }
      if (!_current(owner)) {
        return;
      }
      // Replayed responses are historical acknowledgments, never current projections.
      await session.refresh();
      if (YardCommandIntent.recognizes(c)) {
        await _refreshYardTrailer(c, owner);
      } else {
        await _refreshExecutionContext(c, owner);
      }
      if (_current(owner)) {
        message = 'Confirmed operations reconciled with current server work.';
      }
    } on ApiProblem catch (e) {
      if (!_current(owner)) {
        return;
      }
      if ((await store.find(
            c.userId,
            c.tenantId,
            c.clientOperationId,
          ))?.state ==
          'ACKNOWLEDGED') {
        return;
      }
      if ({401, 403, 404}.contains(e.status)) {
        await store.result(
          c,
          CommandState.conflict,
          status: e.status,
          code: e.code,
          category: 'AUTHORIZATION',
        );
        if (e.status == 401 || e.status == 403) {
          await session.rejectOperationalContext(e);
        } else {
          await session.refresh();
        }
      } else if (e.status == 409) {
        // Even an unfamiliar/malformed 409 is retained for review. It must not
        // silently become a fresh physical action or a blind retry loop.
        await store.result(
          c,
          CommandState.conflict,
          status: 409,
          code: e.code,
          category: e.code == 'IDEMPOTENCY_KEY_REUSED' ? 'IDENTITY' : 'STATE',
        );
        await session.refresh();
        if (YardCommandIntent.recognizes(c)) {
          await _refreshYardTrailer(c, owner);
        }
      } else if (e.status == 408 ||
          e.status == 429 ||
          e.status >= 500 ||
          e.code == 'UNEXPECTED_RESPONSE' ||
          e.code == 'UNEXPECTED_FAILURE') {
        await store.result(
          c,
          CommandState.retryableFailure,
          status: e.status,
          code: e.code,
          retryAt: _backoff(c, e.retryAfter),
        );
      } else {
        await store.result(
          c,
          CommandState.permanentFailure,
          status: e.status,
          code: e.code,
          category: 'VALIDATION',
        );
      }
    } on FormatException {
      if (_current(owner)) {
        if ((await store.find(
              c.userId,
              c.tenantId,
              c.clientOperationId,
            ))?.state ==
            'ACKNOWLEDGED') {
          return;
        }
        await store.result(
          c,
          CommandState.retryableFailure,
          code: 'UNEXPECTED_RESPONSE',
          retryAt: _backoff(c, null),
        );
      }
    } catch (_) {
      if (_current(owner)) {
        if ((await store.find(
              c.userId,
              c.tenantId,
              c.clientOperationId,
            ))?.state ==
            'ACKNOWLEDGED') {
          return;
        }
        await store.result(
          c,
          CommandState.retryableFailure,
          code: 'RESULT_UNCERTAIN',
          retryAt: _backoff(c, null),
        );
      }
    }
  }

  void _validateResponse(LocalCommand c, Map<String, dynamic> response) {
    final trip = response['trip'];
    if (trip is! Map ||
        trip['id'] != c.tripId ||
        !{
          'PLANNED',
          'ASSIGNED',
          'IN_TRANSIT',
          'AT_STOP',
          'CANCELLING',
          'COMPLETED',
          'CANCELLED',
        }.contains(trip['status']) ||
        trip['version'] is! int ||
        (trip['version'] as int) < 0) {
      throw const FormatException();
    }
    if ({'hooks', 'drops'}.contains(c.commandType)) {
      final movement = response['movementEvent'],
          coupling = response['coupling'];
      if (movement is! Map ||
          movement['id'] is! String ||
          coupling is! Map ||
          coupling['id'] is! String ||
          response['trailer'] is! Map) {
        throw const FormatException();
      }
    } else if (trip['stops'] is! List) {
      throw const FormatException();
    }
  }

  Future<void> _refreshYardTrailer(
    LocalCommand c,
    SessionSnapshot owner,
  ) async {
    if (!_current(owner)) {
      return;
    }
    try {
      final fresh = YardTrailer.fromJson(
        await session.api.get(
          'tenants/${c.tenantId}/yard/trailers/${c.aggregateId}',
        ),
      );
      if (!_current(owner) || fresh.id != c.aggregateId) {
        return;
      }
      final cached =
          await (store.db.select(store.db.cachedTrailers)..where(
                (r) =>
                    r.userId.equals(c.userId) &
                    r.tenantId.equals(c.tenantId) &
                    r.id.equals(c.aggregateId!),
              ))
              .getSingleOrNull();
      if (!_current(owner)) {
        return;
      }
      final token = cached == null
          ? null
          : (jsonDecode(cached.projectionJson) as Map)['qrPublicToken'];
      await store.db
          .into(store.db.cachedTrailers)
          .insertOnConflictUpdate(
            CachedTrailersCompanion.insert(
              userId: c.userId,
              tenantId: c.tenantId,
              id: fresh.id,
              serverVersion: fresh.data['version'] as int,
              projectionJson: jsonEncode({
                ...fresh.data,
                'yardProjection': true,
                'qrPublicToken': ?token,
              }),
              refreshedAt: store.now(),
            ),
          );
    } on ApiProblem catch (e) {
      if (!_current(owner)) {
        return;
      }
      if ({401, 403}.contains(e.status)) {
        await session.rejectOperationalContext(e);
      } else if (e.status == 404) {
        await (store.db.delete(store.db.cachedTrailers)..where(
              (r) =>
                  r.userId.equals(c.userId) &
                  r.tenantId.equals(c.tenantId) &
                  r.id.equals(c.aggregateId!),
            ))
            .go();
      }
    } catch (_) {
      if (_current(owner)) {
        message = 'Yard operation retained; reconnect and refresh the recorded Trailer location.';
      }
    }
  }

  Future<void> _refreshExecutionContext(
    LocalCommand c,
    SessionSnapshot owner,
  ) async {
    if (!_current(owner) ||
        !session.snapshot!.trips.any((t) => t.id == c.tripId)) {
      return;
    }
    final current = session.snapshot!;
    try {
      final detail = await session.api.get(
        'tenants/${c.tenantId}/driver/me/trips/${c.tripId}',
      );
      if (!_current(owner) || !identical(session.snapshot, current)) {
        return;
      }
      _validateResponse(c.copyWith(commandType: 'arrivals'), detail);
      await session.cacheConfirmedTrip(current, {
        ...detail['trip'] as Map<String, dynamic>,
        'executionContext': {
          'currentCoupling': detail['currentCoupling'],
          'tractorOperationalNumber': detail['tractorOperationalNumber'],
          'departurePrerequisitesSatisfied':
              detail['departurePrerequisitesSatisfied'],
          'arrivalAssistance': detail['arrivalAssistance'],
        },
      });
    } on ApiProblem catch (e) {
      if (!_current(owner)) {
        return;
      }
      if (e.status == 401 || e.status == 403) {
        await session.rejectOperationalContext(e);
      } else if (e.status == 404) {
        await session.refresh();
      }
    } catch (_) {
      // The own-work directory was refreshed already. No stale physical context
      // is synthesized if the additional equipment read cannot be completed.
      if (_current(owner)) {
        message = 'Assigned work refreshed; reconnect and reload physical equipment context.';
      }
    }
  }

  DateTime _backoff(LocalCommand c, DateTime? retryAfter) {
    final seconds = min(300, 2 * pow(2, min(c.attemptCount, 8))).toDouble();
    final candidate = store.now().add(
      Duration(milliseconds: (seconds * (0.5 + jitter()) * 1000).round()),
    );
    return retryAfter != null && retryAfter.isAfter(candidate)
        ? retryAfter
        : candidate;
  }

  void _schedule(List<LocalCommand> rows, {bool more = false}) {
    if (_disposed || !scheduleTimers) {
      return;
    }
    final retryTimes =
        rows
            .where(
              (c) =>
                  c.state == CommandState.retryableFailure.value &&
                  c.nextRetryAt != null,
            )
            .map((c) => c.nextRetryAt!)
            .toList()
          ..sort();
    final delay = more
        ? const Duration(seconds: 1)
        : retryTimes.isEmpty
        ? null
        : retryTimes.first.difference(store.now());
    if (delay != null) {
      _scheduledAt = store.now().add(
        delay > Duration.zero ? delay : const Duration(seconds: 1),
      );
      _timer = timerFactory(
        delay > Duration.zero ? delay : const Duration(seconds: 1),
        () => unawaited(sync()),
      );
    }
  }

  Future<void> shutdown() async {
    dispose();
    await _network?.cancel();
    await _running;
  }

  @override
  void dispose() {
    if (_disposed) {
      return;
    }
    _disposed = true;
    media?.forgetGrants();
    _timer?.cancel();
    session.removeListener(_sessionChanged);
    unawaited(_network?.cancel());
    super.dispose();
  }
}
