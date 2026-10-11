import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';

import '../persistence/app_database.dart';
import '../platform/connectivity.dart';
import '../road/online_command_journal.dart';
import '../session/api_client.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import 'command_store.dart';

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
  }) : jitter = jitter ?? Random.secure().nextDouble,
       timerFactory = timerFactory ?? ((delay, work) => Timer(delay, work));
  final SessionController session;
  final CommandStore store;
  final OnlineCommandJournal legacy;
  final double Function() jitter;
  final Timer Function(Duration, void Function()) timerFactory;
  final bool scheduleTimers;
  static final _lanes = Expando<Future<void>>();
  Future<void>? _running;
  Timer? _timer;
  StreamSubscription<ConnectivityStatus>? _network;
  bool _started = false, _disposed = false;
  bool _requested = false;
  String? _scope, _recoveredScope;
  bool _wasReady = false;
  String? message;
  bool get sending => _running != null;

  void start() {
    if (_started) return;
    _started = true;
    session.addListener(_sessionChanged);
    _network = session.network.changes.listen((status) {
      if (status == ConnectivityStatus.available) unawaited(sync());
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
      _scope = next;
      _recoveredScope = null;
      _timer?.cancel();
      message = null;
      if (!_disposed) notifyListeners();
    }
    final shouldSync =
        (ready || session.state == SessionState.offline) &&
        (changed || !_wasReady);
    _wasReady = ready;
    if (shouldSync) unawaited(sync());
  }

  bool _current(SessionSnapshot owner) =>
      !_disposed &&
      session.auth.uid == owner.firebaseUid &&
      session.state == SessionState.ready &&
      session.snapshot?.userId == owner.userId &&
      session.snapshot?.membership.tenantId == owner.membership.tenantId &&
      session.snapshot?.driver != null;

  Future<void> sync() {
    if (_disposed) return Future.value();
    if (_running != null) {
      _requested = true;
      return _running!;
    }
    _timer?.cancel();
    final previous = _lanes[store.db] ?? Future.value();
    final requestedScope = scope;
    final run = previous
        .catchError((Object _) {})
        .then((_) => _cycle())
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
      if (!_disposed) notifyListeners();
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
      if (!_current(owner)) return;
      if (await session.network.currentStatus() ==
              ConnectivityStatus.unavailable ||
          !_current(owner)) {
        return;
      }
      if (_recoveredScope != scope) {
        await store.recoverSending(owner.userId, owner.membership.tenantId);
        _recoveredScope = scope;
      }
      // Bound one foreground pass; another timer/manual/reconnect pass continues
      // a larger backlog. No unlimited mutation burst.
      for (var sent = 0; sent < 100 && _current(owner); sent++) {
        final rows = await store.list(owner.userId, owner.membership.tenantId);
        if (!_current(owner)) return;
        await _dependencies(rows);
        final fresh = await store.list(owner.userId, owner.membership.tenantId);
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
                  !_earlierSharedResource(c, fresh),
            )
            .firstOrNull;
        if (candidate == null) {
          _schedule(fresh);
          return;
        }
        if (!await store.claim(candidate)) continue;
        if (!_current(owner)) {
          return; // SENDING is recovered in its original namespace.
        }
        await _send(candidate, owner);
        if (!_current(owner)) return;
      }
      if (_current(owner)) {
        _schedule(
          await store.list(owner.userId, owner.membership.tenantId),
          more: true,
        );
      }
    } catch (_) {
      if (!_current(owner)) return;
      message = 'Synchronization could not access saved operations. They are retained. Retry sync.';
    }
  }

  static List<String> dependencies(LocalCommand c) =>
      List<String>.from(jsonDecode(c.dependenciesJson) as List);
  static Set<String> resources(LocalCommand c) =>
      Set<String>.from(jsonDecode(c.resourceKeysJson) as List);

  bool _earlierSharedResource(LocalCommand c, List<LocalCommand> rows) {
    for (final earlier in rows) {
      if (earlier.clientOperationId == c.clientOperationId) break;
      if (earlier.state != CommandState.acknowledged.value &&
          resources(earlier).intersection(resources(c)).isNotEmpty) {
        return true;
      }
    }
    return false;
  }

  Future<void> _dependencies(List<LocalCommand> rows) async {
    final byId = {for (final c in rows) c.clientOperationId: c};
    bool invalid(String id, Set<String> visiting) {
      if (!visiting.add(id)) return true;
      final c = byId[id];
      if (c == null) return true;
      for (final d in dependencies(c)) {
        if (invalid(d, Set.of(visiting))) return true;
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

  Future<void> _send(LocalCommand c, SessionSnapshot owner) async {
    OnlineCommand command;
    try {
      command = OnlineCommand(
        c.clientOperationId,
        c.tripId,
        c.endpointPath,
        jsonDecode(c.payloadJson) as Map<String, dynamic>,
      );
      CommandIntent(command).validate(c.tenantId);
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
        response = await session.api.post(
          command.path,
          command.body,
          command.id,
          forceRefresh: false,
        );
      } on ApiProblem catch (e) {
        if (e.status != 401 || !_current(owner)) rethrow;
        response = await session.api.post(
          command.path,
          command.body,
          command.id,
          forceRefresh: true,
        );
      }
      if (!_current(owner)) return;
      _validateResponse(c, response);
      await store.result(c, CommandState.acknowledged, status: 200);
      if (!_current(owner)) return;
      // Replayed responses are historical acknowledgments, never current projections.
      await session.refresh();
      await _refreshExecutionContext(c, owner);
      if (_current(owner)) {
        message = 'Confirmed operations reconciled with current server work.';
      }
    } on ApiProblem catch (e) {
      if (!_current(owner)) return;
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
      if (_current(owner)) {
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
      if (!_current(owner) || !identical(session.snapshot, current)) return;
      _validateResponse(c.copyWith(commandType: 'arrivals'), detail);
      await session.cacheConfirmedTrip(current, {
        ...detail['trip'] as Map<String, dynamic>,
        'executionContext': {
          'currentCoupling': detail['currentCoupling'],
          'tractorOperationalNumber': detail['tractorOperationalNumber'],
          'departurePrerequisitesSatisfied':
              detail['departurePrerequisitesSatisfied'],
        },
      });
    } on ApiProblem catch (e) {
      if (!_current(owner)) return;
      if (e.status == 401 || e.status == 403) {
        await session.rejectOperationalContext(e);
      } else if (e.status == 404) {
        await session.refresh();
      }
    } catch (_) {
      // The own-work directory was refreshed already. No stale physical context
      // is synthesized if the additional equipment read cannot be completed.
      if (_current(owner)) message = 'Assigned work refreshed; reconnect and reload physical equipment context.';
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
    if (_disposed || !scheduleTimers) return;
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
    if (_disposed) return;
    _disposed = true;
    _timer?.cancel();
    session.removeListener(_sessionChanged);
    unawaited(_network?.cancel());
    super.dispose();
  }
}
