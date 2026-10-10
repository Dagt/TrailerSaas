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
  bool _cleanupRequired = false;
  bool _storageBlocked = false;
  String? _preferredTenant;
  // Bounded read download, not the command replay worker. Never loop forever on a cursor.
  static const maxWorkPages = 20;
  StreamSubscription<String?>? _authSubscription;
  StreamSubscription<ConnectivityStatus>? _networkSubscription;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int generation, String? uid) =>
      !_disposed && generation == _generation && uid == auth.uid;
  Future<void> _clearCache() async {
    _cleanupRequired = true;
    await cache.clear();
    _cleanupRequired = false;
    _storageBlocked = false;
  }

  void _storageFailure(int generation, String? uid) {
    if (!_current(generation, uid)) return;
    _storageBlocked = true;
    snapshot = null;
    state = SessionState.unavailable;
    message = 'Local storage could not be accessed. Retry refresh or sign out.';
    traceId = null;
    _notify();
  }

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
    try {
      if (previous != uid) _preferredTenant = null;
      if (uid == null ||
          (previous != null && previous != uid) ||
          _cleanupRequired) {
        await _clearCache();
      }
      if (!_current(generation, uid)) return;
      if (uid == null) {
        state = SessionState.signedOut;
        _notify();
        return;
      }
      await _bootstrap(generation, uid);
    } catch (_) {
      _storageFailure(generation, uid);
    }
  }

  Future<void> signIn(String email, String password) async {
    final generation = ++_generation;
    snapshot = null;
    user = null;
    message = null;
    state = SessionState.loading;
    _notify();
    try {
      if (_cleanupRequired) await _clearCache();
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
    try {
      if (_cleanupRequired) await _clearCache();
      await _bootstrap(generation, uid, preferredTenant: _preferredTenant);
    } catch (_) {
      _storageFailure(generation, uid);
    }
  }

  Future<void> _bootstrap(
    int generation,
    String uid, {
    String? preferredTenant,
  }) async {
    try {
      // Selection is a hint only: it must still occur in the fresh /me directory.
      final cached = _storageBlocked ? null : await cache.restore(uid);
      preferredTenant ??= cached?.membership.tenantId;
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
        await _clearCache();
        if (!_current(generation, uid)) return;
        state = SessionState.chooseTenant;
        if (me.memberships.isEmpty) {
          message = 'No active mobile access is available.';
        }
        _notify();
      }
    } on ApiUnavailable {
      try {
        final cached = _storageBlocked ? null : await cache.restore(uid);
        if (!_current(generation, uid)) return;
        snapshot = cached;
        user = null;
        state = cached == null
            ? SessionState.unavailable
            : SessionState.offline;
        message = cached == null
            ? 'Connect to validate your session before using this device.'
            : 'Offline — downloaded work only. Permissions will be revalidated on reconnect.';
        _notify();
      } catch (_) {
        _storageFailure(generation, uid);
      }
    } on ApiProblem catch (e) {
      await _failure(e, generation, uid);
    } catch (_) {
      _storageFailure(generation, uid);
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
    final uid = auth.uid!;
    snapshot = null;
    state = SessionState.loading;
    message = null;
    _notify();
    try {
      if (await network.currentStatus() == ConnectivityStatus.unavailable) {
        throw ApiUnavailable();
      }
      await _clearCache();
      if (!_current(generation, uid)) return;
      await _select(membership, generation, uid);
    } on ApiUnavailable {
      if (_current(generation, uid)) {
        state = SessionState.unavailable;
        message = 'Connect to validate the selected Tenant.';
        _notify();
      }
    } catch (_) {
      _storageFailure(generation, uid);
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
        final seenCursors = <String>{};
        final seenTrips = <String>{};
        var pages = 0;
        do {
          final page = await api.get(
            'tenants/${membership.tenantId}/driver/me/trips',
            {'limit': '50', 'cursor': ?cursor},
          );
          if (!_current(generation, uid)) return;
          final items = (page['items'] as List).cast<Map<String, dynamic>>();
          if (items.length > 50) throw const FormatException();
          for (final item in items) {
            final trip = WorkTrip(item);
            if (!seenTrips.add(trip.id)) throw const FormatException();
            // Validate the complete cache shape before attempting a replacement.
            trip.stops;
            trips.add(trip);
          }
          cursor = page['nextCursor'] as String?;
          pages++;
          if (cursor != null &&
              (cursor.isEmpty ||
                  !seenCursors.add(cursor) ||
                  pages >= maxWorkPages)) {
            throw const FormatException();
          }
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
      _preferredTenant = membership.tenantId;
      _storageBlocked = false;
      state = SessionState.ready;
      _notify();
    } on ApiProblem catch (e) {
      await _failure(e, generation, uid);
    } on ApiUnavailable {
      rethrow;
    } on FormatException {
      if (_current(generation, uid)) {
        snapshot = null;
        state = SessionState.unavailable;
        message = 'Assigned work could not be loaded completely. Refresh to try again.';
        _notify();
      }
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
      try {
        await _clearCache();
      } catch (_) {
        _storageFailure(generation, uid);
        return;
      }
      if (!_current(generation, uid)) return;
      state = SessionState.invalid;
    } else {
      state = SessionState.unavailable;
    }
    _notify();
  }

  Future<void> signOut() async {
    final generation = ++_generation;
    _uid = null;
    snapshot = null;
    user = null;
    _preferredTenant = null;
    message = null;
    state = SessionState.signedOut;
    _notify();
    var failed = false;
    try {
      await _clearCache();
    } catch (_) {
      failed = true;
      _storageBlocked = true;
    }
    try {
      await auth.signOut();
    } catch (_) {
      failed = true;
    }
    if (!_disposed && generation == _generation && failed) {
      state = SessionState.unavailable;
      message = 'Sign-out cleanup could not be completed. Retry sign out.';
      _notify();
    }
  }

  Future<void> shutdown() async {
    dispose();
    await _authSubscription?.cancel();
    await _networkSubscription?.cancel();
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
