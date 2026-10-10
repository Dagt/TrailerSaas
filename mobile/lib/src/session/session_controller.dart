import 'dart:async';

import 'package:flutter/foundation.dart';

import '../persistence/session_cache.dart';
import '../platform/connectivity.dart';
import 'api_client.dart';
import 'auth_gateway.dart';
import 'contracts.dart';

enum SessionState {
  loading,
  signedOut,
  chooseTenant,
  ready,
  offline,
  unavailable,
  invalid,
}

class SessionController extends ChangeNotifier {
  SessionController(this.auth, this.api, this.cache, this.network);
  final AuthGateway auth;
  final ApiClient api;
  final SessionCache cache;
  final Connectivity network;
  SessionState state = SessionState.loading;
  UserContext? user;
  SessionSnapshot? snapshot;
  String? message;
  String? traceId;
  int _generation = 0;
  String? _uid;
  bool _disposed = false;
  bool _started = false;
  StreamSubscription<String?>? _authSubscription;
  StreamSubscription<ConnectivityStatus>? _networkSubscription;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int generation, String? uid) =>
      !_disposed && generation == _generation && uid == auth.uid;
  Future<void> start() async {
    if (_started) return;
    _started = true;
    _authSubscription = auth.changes.distinct().listen((uid) {
      if (uid != _uid) unawaited(_identity(uid));
    });
    _networkSubscription = network.changes.listen((status) {
      if (status == ConnectivityStatus.available &&
          state == SessionState.offline) {
        unawaited(refresh());
      }
      if (status == ConnectivityStatus.unavailable &&
          state == SessionState.ready) {
        state = SessionState.offline;
        _notify();
      }
    });
    await _identity(auth.uid);
  }

  Future<void> _identity(String? uid) async {
    final previous = _uid;
    _uid = uid;
    final generation = ++_generation;
    user = null;
    snapshot = null;
    message = null;
    traceId = null;
    state = SessionState.loading;
    _notify();
    if (previous != null && previous != uid) await cache.clear();
    if (!_current(generation, uid)) return;
    if (uid == null) {
      state = SessionState.signedOut;
      _notify();
      return;
    }
    await _bootstrap(generation, uid);
  }

  Future<void> signIn(String email, String password) async {
    final generation = ++_generation;
    snapshot = null;
    user = null;
    message = null;
    state = SessionState.loading;
    _notify();
    try {
      if (await network.currentStatus() == ConnectivityStatus.unavailable) {
        throw ApiUnavailable();
      }
      await auth.signIn(email, password);
      // The auth stream may already have initiated this identity's bootstrap.
      if (generation == _generation) await _identity(auth.uid);
    } catch (_) {
      if (generation != _generation) return;
      state = SessionState.signedOut;
      message = 'Sign-in requires connectivity and valid credentials.';
      _notify();
    }
  }

  Future<void> refresh() async {
    final uid = auth.uid;
    if (uid == null) {
      await _identity(null);
      return;
    }
    final generation = ++_generation;
    state = SessionState.loading;
    message = null;
    traceId = null;
    _notify();
    await _bootstrap(
      generation,
      uid,
      preferredTenant: snapshot?.membership.tenantId,
    );
  }

  Future<void> _bootstrap(
    int generation,
    String uid, {
    String? preferredTenant,
  }) async {
    try {
      if (await network.currentStatus() == ConnectivityStatus.unavailable) {
        throw ApiUnavailable();
      }
      final me = UserContext.fromJson(await api.get('me'));
      if (!_current(generation, uid)) return;
      user = me;
      snapshot = null;
      final preferred = me.memberships
          .where((m) => m.tenantId == preferredTenant)
          .firstOrNull;
      if (preferred != null || me.memberships.length == 1) {
        await _select(preferred ?? me.memberships.single, generation, uid);
      } else {
        await cache.clear();
        if (!_current(generation, uid)) return;
        state = SessionState.chooseTenant;
        if (me.memberships.isEmpty) {
          message = 'No active mobile access is available.';
        }
        _notify();
      }
    } on ApiUnavailable {
      final cached = await cache.restore(uid);
      if (!_current(generation, uid)) return;
      snapshot = cached;
      user = null;
      state = cached == null ? SessionState.unavailable : SessionState.offline;
      message = cached == null
          ? 'Connect to validate your session before using this device.'
          : 'Offline — downloaded work only. Permissions will be revalidated on reconnect.';
      _notify();
    } on ApiProblem catch (e) {
      await _failure(e, generation, uid);
    } catch (_) {
      if (_current(generation, uid)) {
        snapshot = null;
        state = SessionState.unavailable;
        message = 'The session could not be loaded.';
        _notify();
      }
    }
  }

  Future<void> selectTenant(Membership membership) async {
    // Only the freshly bootstrapped directory may supply a new Tenant.
    final me = user;
    if (me == null ||
        !me.memberships.contains(membership) ||
        auth.uid == null) {
      return;
    }
    final generation = ++_generation;
    snapshot = null;
    state = SessionState.loading;
    message = null;
    _notify();
    await cache.clear();
    if (!_current(generation, auth.uid)) return;
    try {
      await _select(membership, generation, auth.uid!);
    } on ApiUnavailable {
      if (_current(generation, auth.uid)) {
        state = SessionState.unavailable;
        message = 'Connect to validate the selected Tenant.';
        _notify();
      }
    }
  }

  Future<void> _select(
    Membership membership,
    int generation,
    String uid,
  ) async {
    try {
      DriverProfile? driver;
      final trips = <WorkTrip>[];
      if (membership.road) {
        driver = DriverProfile.fromJson(
          await api.get('tenants/${membership.tenantId}/driver/me'),
        );
        String? cursor;
        do {
          final page = await api.get(
            'tenants/${membership.tenantId}/driver/me/trips',
            {'limit': '50', 'cursor': ?cursor},
          );
          if (!_current(generation, uid)) return;
          trips.addAll(
            (page['items'] as List).cast<Map<String, dynamic>>().map(
              WorkTrip.new,
            ),
          );
          cursor = page['nextCursor'] as String?;
        } while (cursor != null);
      }
      if (!_current(generation, uid)) return;
      final next = SessionSnapshot(
        uid,
        user!.userId,
        membership,
        driver,
        DateTime.now().toUtc(),
        trips,
      );
      await cache.replace(next, () => _current(generation, uid));
      if (!_current(generation, uid)) return;
      snapshot = next;
      state = SessionState.ready;
      _notify();
    } on ApiProblem catch (e) {
      await _failure(e, generation, uid);
    } on ApiUnavailable {
      rethrow;
    }
  }

  Future<void> _failure(ApiProblem problem, int generation, String uid) async {
    if (!_current(generation, uid)) return;
    snapshot = null;
    message = problem.detail;
    traceId = problem.traceId;
    // Explicit denial is not a transport outage: no fallback to old permissions/work.
    if (problem.status == 401 ||
        problem.status == 403 ||
        problem.status == 404) {
      await cache.clear();
      if (!_current(generation, uid)) return;
      state = SessionState.invalid;
    } else {
      state = SessionState.unavailable;
    }
    _notify();
  }

  Future<void> signOut() async {
    ++_generation;
    _uid = null;
    snapshot = null;
    user = null;
    message = null;
    state = SessionState.signedOut;
    _notify();
    await cache.clear();
    await auth.signOut();
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    ++_generation;
    unawaited(_authSubscription?.cancel());
    unawaited(_networkSubscription?.cancel());
    super.dispose();
  }
}
