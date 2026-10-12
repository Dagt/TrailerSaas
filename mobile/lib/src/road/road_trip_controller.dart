import 'package:flutter/foundation.dart';

import 'dart:async';
import 'dart:convert';

import '../session/session_controller.dart';
import '../session/contracts.dart';
import '../session/api_client.dart';
import 'online_command_journal.dart';
import '../sync/replay_worker.dart';
import '../sync/road_command_capture.dart';
import '../persistence/app_database.dart';
import '../sync/command_store.dart';

class RoadTripController extends ChangeNotifier {
  RoadTripController(
    this.session,
    this.tripId,
    this.journal, {
    this.recoveryOnly = false,
    this.worker,
  }) {
    _snapshot = session.snapshot;
    _owner = session.snapshot;
    session.addListener(_sessionChanged);
    hasQueued =
        worker !=
        null; // Fail closed until the initial persisted queue read completes.
    if (worker != null && _owner != null) {
      _queueSubscription = worker!.store
          .watch(_owner.userId, _owner.membership.tenantId)
          .listen(
            (rows) {
              if (_disposed ||
                  session.auth.uid != _owner.firebaseUid ||
                  session.snapshot?.userId != _owner.userId ||
                  session.snapshot?.membership.tenantId !=
                      _owner.membership.tenantId) {
                return;
              }
              hasQueued = rows.any(
                (c) =>
                    c.tripId == tripId &&
                    c.state != CommandState.acknowledged.value,
              );
              final tractor = session.snapshot?.trips
                  .where((t) => t.id == tripId)
                  .firstOrNull
                  ?.data['currentTractorId'];
              pendingPreTrip = rows.any(
                (c) =>
                    c.commandType == 'pre-trip' &&
                    c.aggregateType == 'INSPECTION' &&
                    c.state != 'ACKNOWLEDGED' &&
                    (c.tripId == null || c.tripId == tripId) &&
                    (jsonDecode(c.payloadJson) as Map)['tractorId'] == tractor,
              );
              _notify();
            },
            onError: (Object _) {
              hasQueued =
                  true; // Storage uncertainty must not open duplicate controls.
              message = 'Saved operations could not be read. Retry sync before acting.';
              _notify();
            },
          );
    }
  }
  final SessionController session;
  final String tripId;
  final bool recoveryOnly;
  final ReplayWorker? worker;
  StreamSubscription<List<LocalCommand>>? _queueSubscription;
  bool hasQueued = false;
  bool pendingPreTrip = false;
  bool get canRecord =>
      session.snapshot?.driver != null &&
      {SessionState.ready, SessionState.offline}.contains(session.state);
  DateTime? retryNotBefore;
  Timer? _retryTimer;
  bool get canRecover =>
      online &&
      !sending &&
      pending != null &&
      (retryNotBefore == null ||
          !DateTime.now().toUtc().isBefore(retryNotBefore!));
  final OnlineCommandJournal journal;
  Map<String, dynamic>? detail;
  OnlineCommand? pending;
  bool sending = false;
  String? message, traceId;
  int _generation = 0;
  bool _disposed = false;
  SessionSnapshot? _snapshot;
  late final SessionSnapshot? _owner;
  bool get online =>
      session.state == SessionState.ready && session.snapshot?.driver != null;
  Map<String, dynamic>? get trip => detail?['trip'] as Map<String, dynamic>?;
  bool _current(int generation, SessionSnapshot s) =>
      !_disposed &&
      generation == _generation &&
      identical(session.snapshot, s) &&
      session.auth.uid == s.firebaseUid &&
      online;
  bool _readCurrent(int generation, SessionSnapshot s) =>
      !_disposed &&
      generation == _generation &&
      identical(session.snapshot, s) &&
      session.auth.uid == s.firebaseUid;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _sessionChanged() {
    if (identical(_snapshot, session.snapshot) && online) return;
    _snapshot = session.snapshot;
    ++_generation;
    detail = null;
    pending = null;
    sending = false;
    message = null;
    traceId = null;
    _retryTimer?.cancel();
    _notify();
    if ((online || (worker != null && canRecord)) &&
        _snapshot?.userId == _owner?.userId &&
        _snapshot?.firebaseUid == _owner?.firebaseUid &&
        _snapshot?.membership.tenantId == _owner?.membership.tenantId) {
      unawaited(load());
    }
  }

  Future<void> load() async {
    final s = session.snapshot;
    if (worker != null && s != null && !online && canRecord) {
      final cached = s.trips.where((t) => t.id == tripId).firstOrNull;
      if (cached != null) {
        detail = {
          'trip': cached.data,
          ...(cached.data['executionContext'] as Map<String, dynamic>? ?? {}),
        };
        _notify();
      }
      return;
    }
    if (s == null || (!online && !recoveryOnly)) return;
    final generation = ++_generation;
    sending = true;
    message = null;
    _notify();
    try {
      final saved = worker == null
          ? await journal.read(s.userId, s.membership.tenantId)
          : null;
      if (saved != null && !saved.belongsTo(s.membership.tenantId)) {
        throw const FormatException();
      }
      if (!_readCurrent(generation, s)) return;
      pending = saved;
      if (!recoveryOnly) {
        final data = await session.api.get(
          'tenants/${s.membership.tenantId}/driver/me/trips/$tripId',
        );
        if (!_readCurrent(generation, s)) return;
        detail = data;
        if (worker != null) {
          await session.cacheConfirmedTrip(s, {
            ...data['trip'] as Map<String, dynamic>,
            'executionContext': {
              'currentCoupling': data['currentCoupling'],
              'tractorOperationalNumber': data['tractorOperationalNumber'],
              'departurePrerequisitesSatisfied':
                  data['departurePrerequisitesSatisfied'],
            },
          });
        }
      }
      if (saved != null) {
        message = saved.tripId == tripId
            ? 'A previous submission needs recovery before another action.'
            : 'Another Trip has an uncertain submission. Recover that saved submission before another action.';
      }
    } on ApiProblem catch (e) {
      if (_readCurrent(generation, s)) {
        message = e.detail;
        traceId = e.traceId;
        if (e.status == 401 || e.status == 403 || e.status == 404) {
          await session.refresh();
        }
      }
    } catch (_) {
      if (_readCurrent(generation, s)) {
        message =
            'Work could not be refreshed. Connect and reload before acting.';
      }
    } finally {
      if (_readCurrent(generation, s)) {
        sending = false;
        _notify();
      }
    }
  }

  Future<Map<String, dynamic>?> identify(String token) async {
    final s = session.snapshot;
    if (worker != null && s != null && canRecord && !online) {
      final identified = await session.cache.identifiedTrailer(s, token);
      if (!_readCurrent(_generation, s)) return null;
      if (identified == null) {
        message = 'This QR identity was not downloaded. Connect to identify and authorize the Trailer.';
        _notify();
      }
      return identified;
    }
    if (s == null || !online || sending) return null;
    final generation = _generation;
    try {
      final resolved = await session.api.get('trailer-qr/$token');
      if (!_current(generation, s)) return null;
      if (worker != null) {
        await session.cache.rememberIdentifiedTrailer(
          s,
          token,
          resolved,
          () => _current(generation, s),
        );
      }
      return resolved;
    } on ApiProblem catch (e) {
      if (_current(generation, s)) {
        message = e.detail;
        traceId = e.traceId;
        _notify();
      }
    } catch (_) {
      if (_current(generation, s)) {
        message = 'Trailer identification could not be confirmed.';
        _notify();
      }
    }
    return null;
  }

  Future<void> submit(String path, Map<String, dynamic> body) async {
    final s = session.snapshot;
    if (worker != null && s != null && canRecord && !sending) {
      sending = true;
      _notify();
      try {
        await RoadCommandCapture(
          session,
          worker!,
        ).record(s, tripId, path, body);
        if (_readCurrent(_generation, s)) message = 'Saved on this device — pending synchronization. Server state is unchanged until confirmed.';
      } catch (_) {
        if (_readCurrent(_generation, s)) message = 'The action could not be safely recorded. Refresh work or review pending operations.';
      } finally {
        sending = false;
        _notify();
      }
      return;
    }
    if (s == null || !online || sending || pending != null || detail == null) {
      return;
    }
    final command = OnlineCommand(operationUuid(), tripId, path, Map.of(body));
    final generation = ++_generation;
    sending = true;
    message = null;
    _notify();
    try {
      await journal.save(s.userId, s.membership.tenantId, command);
      if (!_current(generation, s)) return;
      pending = command;
      await _send(command, s, generation);
    } catch (_) {
      if (_current(generation, s)) message = 'The submission could not be safely recorded. Reload before trying again.';
    } finally {
      if (_current(generation, s)) {
        sending = false;
        _notify();
      }
    }
  }

  Future<void> recover() async {
    final s = session.snapshot, command = pending;
    if (s == null || command == null || !canRecover) {
      return;
    }
    final generation = ++_generation;
    sending = true;
    _notify();
    try {
      await _send(command, s, generation);
    } catch (_) {
      if (_current(generation, s)) message = 'Recovery could not be completed. The saved submission is retained.';
    } finally {
      if (_current(generation, s)) {
        sending = false;
        _notify();
      }
    }
  }

  Future<void> _send(
    OnlineCommand command,
    SessionSnapshot s,
    int generation,
  ) async {
    try {
      if (!command.belongsTo(s.membership.tenantId)) {
        message = 'The saved submission does not match this Tenant or an accepted command. No request was sent.';
        return;
      }
      await session.api.post(command.path, command.body, command.id);
      // The saved receipt belongs to its original namespace, even if identity changed during HTTP.
      if (!_current(generation, s)) return;
      await journal.clear(
        s.userId,
        s.membership.tenantId,
        operationId: command.id,
      );
      if (!_current(generation, s)) return;
      pending = null;
      message = 'Confirmed by server.';
      if (recoveryOnly || command.tripId != tripId) {
        // Reconcile the entire assigned-work set after recovering another Trip.
        // Its saved result must never replace the currently displayed Trip.
        await _reconcile(
          s,
          'Confirmed by server. Assigned work has been refreshed.',
        );
        return;
      }
      try {
        // A saved replay response can describe an earlier state. Re-read before enabling
        // another action or replacing offline work, including after Hook/Drop.
        final refreshed = await session.api.get(
          'tenants/${s.membership.tenantId}/driver/me/trips/$tripId',
        );
        if (!_current(generation, s)) return;
        if ((refreshed['trip'] as Map)['id'] != tripId) {
          throw const FormatException();
        }
        if (!await session.cacheConfirmedTrip(
          s,
          refreshed['trip'] as Map<String, dynamic>,
        )) {
          return;
        }
        if (!_current(generation, s)) return;
        detail = refreshed;
      } catch (_) {
        if (!_current(generation, s)) return;
        detail = null;
        message = 'The command was confirmed. Refresh assigned work before another action.';
        await session.refresh();
      }
    } on ApiProblem catch (e) {
      if (!_current(generation, s)) return;
      message = e.detail;
      traceId = e.traceId;
      // Only a recognized application denial proves that this submission was rejected.
      // Proxy/malformed responses and key-reuse conflicts cannot establish its outcome.
      final authorizationDenied = e.status == 401 || e.status == 403;
      final definitive =
          (e.status == 400 && e.code == 'VALIDATION_FAILED') ||
          (e.status == 409 &&
              {
                'INVALID_STATE_TRANSITION',
                'RESOURCE_CONFLICT',
                'STALE_VERSION',
                'EVIDENCE_REQUIRED',
              }.contains(e.code));
      if (e.status == 429) {
        retryNotBefore = e.retryAfter;
        _retryTimer?.cancel();
        final wait = retryNotBefore?.difference(DateTime.now().toUtc());
        if (wait != null && wait > Duration.zero) {
          // UI eligibility only: this timer never submits a command.
          _retryTimer = Timer(wait, _notify);
        }
        message =
            'The server limited this request. The saved submission is retained. '
            'Recover explicitly after the retry time.';
      }
      final assignmentDenied =
          e.status == 404 && e.code == 'RESOURCE_NOT_FOUND';
      if (authorizationDenied || assignmentDenied) {
        // Authorization precedes ledger replay on the server: denial cannot prove
        // whether an earlier uncertain submission committed. Keep its identity.
        detail = null;
        if (authorizationDenied) {
          await session.rejectOperationalContext(e);
        } else {
          await session.refresh();
        }
        _resultAfterRefresh(
          s,
          'Recovery was denied: ${e.detail} '
          'Assigned work was revalidated. The earlier outcome remains unconfirmed; its receipt is retained.',
          e.traceId,
        );
      } else if (definitive) {
        await journal.clear(
          s.userId,
          s.membership.tenantId,
          operationId: command.id,
        );
        if (!_current(generation, s)) return;
        pending = null;
        detail = null;
        await _reconcile(
          s,
          'The command was rejected: ${e.detail} Assigned work has been refreshed.',
          e.traceId,
        );
      }
    } catch (_) {
      if (_current(generation, s)) message = 'Result not confirmed. Reload and recover the same submission; do not repeat the physical action.';
    }
  }

  bool _sameOwner(SessionSnapshot s) =>
      !_disposed &&
      session.auth.uid == s.firebaseUid &&
      session.snapshot?.userId == s.userId &&
      session.snapshot?.membership.tenantId == s.membership.tenantId;

  Future<void> _reconcile(
    SessionSnapshot s,
    String result, [
    String? trace,
  ]) async {
    detail = null;
    await session.refresh();
    _resultAfterRefresh(s, result, trace);
  }

  void _resultAfterRefresh(SessionSnapshot s, String result, String? trace) {
    session.reportOperationalResult(s, result, trace);
    if (!_sameOwner(s)) return;
    message = result;
    traceId = trace;
    _notify();
  }

  @override
  void dispose() {
    unawaited(_queueSubscription?.cancel());
    _retryTimer?.cancel();
    _disposed = true;
    ++_generation;
    session.removeListener(_sessionChanged);
    super.dispose();
  }
}
