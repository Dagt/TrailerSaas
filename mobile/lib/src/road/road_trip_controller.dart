import 'package:flutter/foundation.dart';

import '../session/session_controller.dart';
import '../session/contracts.dart';
import '../session/api_client.dart';
import 'online_command_journal.dart';

class RoadTripController extends ChangeNotifier {
  RoadTripController(this.session, this.tripId, this.journal) {
    _snapshot = session.snapshot;
    session.addListener(_sessionChanged);
  }
  final SessionController session;
  final String tripId;
  final OnlineCommandJournal journal;
  Map<String, dynamic>? detail;
  OnlineCommand? pending;
  bool sending = false;
  String? message, traceId;
  int _generation = 0;
  bool _disposed = false;
  SessionSnapshot? _snapshot;
  bool get online =>
      session.state == SessionState.ready && session.snapshot?.driver != null;
  Map<String, dynamic>? get trip => detail?['trip'] as Map<String, dynamic>?;
  bool _current(int generation, SessionSnapshot s) =>
      !_disposed &&
      generation == _generation &&
      identical(session.snapshot, s) &&
      online;
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
    _notify();
  }

  Future<void> load() async {
    final s = session.snapshot;
    if (s == null || !online) return;
    final generation = ++_generation;
    sending = true;
    message = null;
    _notify();
    try {
      final saved = await journal.read(s.userId, s.membership.tenantId);
      if (saved != null &&
          !saved.path.startsWith('tenants/${s.membership.tenantId}/')) {
        throw const FormatException();
      }
      final data = await session.api.get(
        'tenants/${s.membership.tenantId}/driver/me/trips/$tripId',
      );
      if (!_current(generation, s)) return;
      detail = data;
      pending = saved;
      if (saved != null) {
        message = saved.tripId == tripId
            ? 'A previous submission needs recovery before another action.'
            : 'Another Trip has an uncertain submission. Open that Trip to recover it.';
      }
    } on ApiProblem catch (e) {
      if (_current(generation, s)) {
        message = e.detail;
        traceId = e.traceId;
        if (e.status == 401 || e.status == 403 || e.status == 404) {
          await session.refresh();
        }
      }
    } catch (_) {
      if (_current(generation, s)) {
        message =
            'Work could not be refreshed. Connect and reload before acting.';
      }
    } finally {
      if (_current(generation, s)) {
        sending = false;
        _notify();
      }
    }
  }

  Future<Map<String, dynamic>?> identify(String token) async {
    final s = session.snapshot;
    if (s == null || !online || sending) return null;
    final generation = _generation;
    try {
      final resolved = await session.api.get('trailer-qr/$token');
      if (!_current(generation, s)) return null;
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
    if (s == null ||
        command == null ||
        command.tripId != tripId ||
        !online ||
        sending) {
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
      final result = await session.api.post(
        command.path,
        command.body,
        command.id,
      );
      // The saved receipt belongs to its original namespace, even if identity changed during HTTP.
      if (!_current(generation, s)) return;
      await journal.clear(s.userId, s.membership.tenantId);
      if (!_current(generation, s)) return;
      pending = null;
      message = 'Confirmed by server.';
      try {
        final refreshed =
            result['trip'] is Map && (result['trip'] as Map)['stops'] is List
            ? result
            : await session.api.get(
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
      if (e.status >= 400 && e.status < 500) {
        await journal.clear(s.userId, s.membership.tenantId);
        if (!_current(generation, s)) return;
        pending = null;
        if (e.status == 401 || e.status == 403 || e.status == 404) {
          await session.refresh();
        }
      }
    } catch (_) {
      if (_current(generation, s)) message = 'Result not confirmed. Reload and recover the same submission; do not repeat the physical action.';
    }
  }

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    session.removeListener(_sessionChanged);
    super.dispose();
  }
}
