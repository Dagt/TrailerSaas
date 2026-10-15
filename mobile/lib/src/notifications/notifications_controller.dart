import 'dart:async';

import 'package:flutter/foundation.dart';

import '../session/session_controller.dart';
import '../session/api_client.dart';
import '../platform/connectivity.dart';
import 'messaging_port.dart';
import 'device_registration_store.dart';

class NotificationsController extends ChangeNotifier {
  NotificationsController(this.session, this.messaging, this.store);
  final SessionController session;
  final MessagingPort messaging;
  final DeviceRegistrationStore store;
  PushPermission permission = PushPermission.unknown;
  String status =
      'Notifications are optional. Operational updates remain available without push.';
  PushHint? hint;
  Map<String, dynamic>? detail;
  bool busy = false;
  bool _closed = false,
      _started = false,
      _signingOut = false,
      _pending = false,
      _request = false,
      _resetToken = false;
  bool _foreground = true;
  String? _user, _uid, _tenant, _registration;
  int _epoch = 0;
  Future<void> _running = Future.value();
  StreamSubscription<String>? _tokens;
  StreamSubscription<PushHint>? _hints;
  StreamSubscription<ConnectivityStatus>? _network;
  bool _current(int epoch, String user, String uid) =>
      !_closed &&
      _foreground &&
      !_signingOut &&
      epoch == _epoch &&
      _user == user &&
      session.auth.uid == uid &&
      session.user?.userId == user;
  void _notify() {
    if (!_closed) notifyListeners();
  }

  Future<void> start() async {
    if (_started || _closed) return;
    _started = true;
    session.addListener(_sessionChanged);
    session.beforeSignOut = beforeSignOut;
    // Refresh reads the current native token; an old stream value is never trusted.
    _tokens = messaging.tokenChanges.listen((_) {
      unawaited(refresh());
    }, onError: (Object _) {});
    _hints = messaging.hints.listen(_received, onError: (Object _) {});
    _network = session.network.changes.distinct().listen((s) {
      if (s == ConnectivityStatus.available) unawaited(refresh());
    });
    _sessionChanged();
    final uid = session.auth.uid;
    try {
      final initial = await messaging.initialHint();
      if (!_closed && uid == session.auth.uid && initial != null)
        _received(initial);
    } catch (_) {}
    await _running;
  }

  void _sessionChanged() {
    final user = session.user?.userId,
        uid = session.auth.uid,
        tenant = session.snapshot?.membership.tenantId;
    if (user != _user || uid != _uid) {
      if (_uid != null && uid != _uid) _resetToken = true;
      _user = user;
      _uid = uid;
      _registration = null;
      ++_epoch;
      hint = null;
      detail = null;
      if (user != null) _signingOut = false;
      if (user != null && uid != null) unawaited(refresh());
    }
    if (tenant != _tenant) {
      _tenant = tenant;
      detail = null;
      if (hint?.tenantId != tenant) hint = null;
    }
    if (!(session.snapshot?.membership.capabilities.contains('ALERT_VIEW') ??
        false)) {
      hint = null;
      detail = null;
    }
    _notify();
  }

  Future<void> refresh({bool requestPermission = false}) {
    if (_closed ||
        !_foreground ||
        _signingOut ||
        _user == null ||
        _uid == null ||
        messaging.platform == null)
      return Future.value();
    _pending = true;
    _request = _request || requestPermission;
    if (busy) return _running;
    busy = true;
    _notify();
    _running = _drain();
    return _running;
  }

  Future<void> _drain() async {
    try {
      while (_pending && !_closed && !_signingOut) {
        _pending = false;
        final ask = _request;
        _request = false;
        final user = _user, uid = _uid, epoch = _epoch;
        if (user == null || uid == null) continue;
        try {
          if (_resetToken) {
            _resetToken = false;
            await messaging.enabled(false);
            await messaging.deleteToken();
          }
          if (!_current(epoch, user, uid)) continue;
          final allowed = await messaging.permission(request: ask);
          if (!_current(epoch, user, uid)) continue;
          permission = allowed;
          final granted =
              allowed == PushPermission.authorized ||
              allowed == PushPermission.provisional;
          await messaging.enabled(granted);
          if (!_current(epoch, user, uid)) continue;
          final token = granted ? await messaging.token() : null;
          if (!_current(epoch, user, uid)) continue;
          if (granted && (token == null || token.isEmpty))
            throw StateError('Token unavailable.');
          final id = await store.ensure(user);
          if (!_current(epoch, user, uid)) continue;
          _registration = id;
          final receipt = await session.api.post('devices', {
            'deviceId': id,
            'platform': messaging.platform,
            'fcmToken': token,
          }, null);
          if (!_current(epoch, user, uid)) continue;
          if (receipt['id'] != id ||
              receipt['userId'] != user ||
              receipt['active'] != true ||
              receipt['platform'] != messaging.platform ||
              receipt['pushRegistered'] != granted)
            throw const FormatException();
          status = granted
              ? 'Push registration confirmed by server. Delivery is not guaranteed.'
              : 'Device registered without push. You can continue without notifications.';
        } catch (_) {
          if (_current(epoch, user, uid)) status = 'Push registration is unavailable. Retry when connected; operational work remains available.';
        }
        _notify();
      }
    } finally {
      busy = false;
      _notify();
    }
  }

  void _received(PushHint value) {
    if (_closed || _signingOut || _user == null || session.auth.uid != _uid)
      return;
    final membership = session.snapshot?.membership;
    if (membership?.tenantId != value.tenantId ||
        !membership!.capabilities.contains('ALERT_VIEW'))
      return;
    hint = value;
    detail = null;
    _notify();
  }

  Future<void> reviewHint() async {
    final current = hint, user = _user, uid = _uid, epoch = _epoch;
    if (current == null ||
        user == null ||
        uid == null ||
        !_current(epoch, user, uid) ||
        session.snapshot?.membership.tenantId != current.tenantId ||
        !(session.snapshot?.membership.capabilities.contains('ALERT_VIEW') ??
            false))
      return;
    try {
      final response = await session.api.get(
        'tenants/${current.tenantId}/alerts/${current.alertId}',
      );
      if (!_current(epoch, user, uid) ||
          hint != current ||
          session.snapshot?.membership.tenantId != current.tenantId)
        return;
      if (response['id'] != current.alertId ||
          response['tenantId'] != current.tenantId ||
          !{'OPEN', 'ACKNOWLEDGED', 'RESOLVED'}.contains(response['status']) ||
          response['relatedResource'] is! Map<String, dynamic>)
        throw const FormatException();
      detail = Map.unmodifiable(response);
      status = 'Current authorized Alert retrieved from server.';
    } on ApiProblem catch (_) {
      if (_current(epoch, user, uid)) {
        detail = null;
        status = 'This Alert is no longer available to your account. Refresh your session.';
      }
    } catch (_) {
      if (_current(epoch, user, uid)) status = 'Connect to retrieve the current Alert. Push alone does not confirm any operation.';
    }
    _notify();
  }

  Future<void> beforeSignOut() async {
    final user = _user, uid = _uid;
    _signingOut = true;
    ++_epoch;
    _pending = false;
    hint = null;
    detail = null;
    try {
      await _running;
      if (user != null && uid != null && session.auth.uid == uid) {
        final id = _registration ?? await store.ensure(user);
        if (session.auth.uid == uid) await session.api.delete('devices/$id');
      }
    } catch (_) {
      /* Offline logout cannot claim server deactivation. No other-account retry. */
    }
    if (session.auth.uid != uid) return;
    try {
      await messaging.enabled(false);
      if (session.auth.uid == uid) await messaging.deleteToken();
    } catch (_) {}
  }

  void suspend() {
    _foreground = false;
    ++_epoch;
    _pending = false;
    detail = null;
    _notify();
  }

  Future<void> resume() {
    _foreground = true;
    return refresh();
  }

  Future<void> shutdown() async {
    dispose();
    await _tokens?.cancel();
    await _hints?.cancel();
    await _network?.cancel();
    await _running;
  }

  @override
  void dispose() {
    if (_closed) return;
    _closed = true;
    ++_epoch;
    _pending = false;
    session.removeListener(_sessionChanged);
    session.beforeSignOut = null;
    unawaited(_tokens?.cancel());
    unawaited(_hints?.cancel());
    unawaited(_network?.cancel());
    super.dispose();
  }
}
