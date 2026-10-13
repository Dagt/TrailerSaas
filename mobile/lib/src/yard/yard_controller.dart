import 'dart:async';
import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

import '../persistence/app_database.dart';
import '../platform/connectivity.dart';
import '../platform/native_qr_scanner.dart';
import '../platform/qr_scanner.dart';
import '../session/api_client.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../sync/replay_worker.dart';
import 'yard_commands.dart';
import 'yard_contracts.dart';

class YardModeController extends ChangeNotifier {
  YardModeController(
    this.session,
    this.worker,
    this.scanner, {
    DateTime Function()? clock,
  }) : now = clock ?? (() => DateTime.now().toUtc());
  final SessionController session;
  final ReplayWorker worker;
  final QrScanner scanner;
  final DateTime Function() now;
  AppDatabase get db => worker.store.db;
  List<YardSite> sites = [];
  String? selectedSiteId, message;
  YardTrailer? trailer;
  bool busy = false, loading = false, blocked = false;
  DateTime? refreshedAt;
  int _generation = 0;
  bool _disposed = false, _started = false, _wasReady = false;
  String? _ownerKey;
  YardSite? get selected =>
      sites.where((s) => s.id == selectedSiteId).firstOrNull;
  bool get usable =>
      !blocked &&
      !busy &&
      !loading &&
      selected != null &&
      selected!.nextLocationCursor == null;
  bool get canMove =>
      usable &&
      trailer?.movable == true &&
      trailer!.location?['siteId'] == selectedSiteId;
  String? get contextToken => key(session.snapshot) == null
      ? null
      : '${key(session.snapshot)}|$selectedSiteId|${trailer?.id}|${trailer?.data['version']}|$_generation';
  bool permits(String capability) =>
      usable &&
      session.snapshot?.membership.capabilities.contains(capability) == true;
  String? key(SessionSnapshot? s) =>
      s == null || session.auth.uid != s.firebaseUid
      ? null
      : '${s.firebaseUid}|${s.userId}|${s.membership.tenantId}|${s.driver?.id}';
  bool current(SessionSnapshot owner, int generation) =>
      !_disposed &&
      generation == _generation &&
      key(session.snapshot) == key(owner) &&
      session.auth.uid == owner.firebaseUid &&
      owner.membership.capabilities.contains('YARD_MOVE_EXECUTE') &&
      session.snapshot!.membership.capabilities.contains('YARD_MOVE_EXECUTE') &&
      {SessionState.ready, SessionState.offline}.contains(session.state);
  void _notify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  void start() {
    if (_started) {
      return;
    }
    _started = true;
    session.addListener(_sessionChanged);
    _sessionChanged();
  }

  void _sessionChanged() {
    final next = key(session.snapshot),
        ready = session.state == SessionState.ready;
    final changed = next != _ownerKey;
    if (changed ||
        !{SessionState.ready, SessionState.offline}.contains(session.state)) {
      ++_generation;
      if (scanner is CancellableQrScanner) {
        (scanner as CancellableQrScanner).cancel();
      }
      busy = false;
      loading = false;
      trailer = null;
      if (changed) {
        sites = [];
        selectedSiteId = null;
        blocked = false;
        message = null;
      }
    }
    _ownerKey = next;
    final loadNow = next != null && (changed || (ready && !_wasReady));
    _wasReady = ready;
    _notify();
    if (loadNow) {
      unawaited(load());
    }
  }

  Future<CachedYardContext?> cached(SessionSnapshot owner) =>
      (db.select(db.cachedYardContexts)..where(
            (r) =>
                r.userId.equals(owner.userId) &
                r.tenantId.equals(owner.membership.tenantId),
          ))
          .getSingleOrNull();
  Future<void> save(
    SessionSnapshot owner,
    int generation,
    List<YardSite> data,
    String? selection, {
    bool denied = false,
  }) => db.transaction(() async {
    if (!current(owner, generation)) {
      return;
    }
    await db
        .into(db.cachedYardContexts)
        .insertOnConflictUpdate(
          CachedYardContextsCompanion.insert(
            userId: owner.userId,
            tenantId: owner.membership.tenantId,
            projectionJson: jsonEncode(data.map((s) => s.toJson()).toList()),
            selectedSiteId: Value(selection),
            refreshedAt: now(),
            accessBlocked: Value(denied),
          ),
        );
  });
  Future<List<Map<String, dynamic>>> pages(
    String path,
    Map<String, String> query,
    SessionSnapshot owner,
    int generation,
    int maximum,
  ) async {
    final all = <Map<String, dynamic>>[], seen = <String>{};
    String? cursor;
    for (var page = 0; page < 20; page++) {
      final response = await session.api.get(path, {
        ...query,
        'cursor': ?cursor,
      });
      if (!current(owner, generation)) {
        throw const FormatException('Yard context changed');
      }
      if (response.keys.toSet().difference({
            'items',
            'nextCursor',
          }).isNotEmpty ||
          response['items'] is! List ||
          (response['items'] as List).length > maximum) {
        throw const FormatException('Invalid Yard page');
      }
      for (final item in response['items'] as List) {
        final row = item as Map<String, dynamic>;
        yardUuid(row['id']);
        if (!seen.add(row['id'] as String)) {
          throw const FormatException('Repeated Yard row');
        }
        all.add(row);
      }
      final next = response['nextCursor'];
      if (next == null) {
        return all;
      }
      yardUuid(next);
      if (next == cursor || !seen.contains(next)) {
        throw const FormatException('Invalid Yard continuation');
      }
      cursor = next as String;
    }
    throw const FormatException('Yard download requires too many pages');
  }

  Future<YardSite> completeSite(
    YardSite site,
    SessionSnapshot owner,
    int generation,
  ) async {
    if (site.nextLocationCursor == null) {
      return site;
    }
    final all = await pages(
      'tenants/${owner.membership.tenantId}/yard/sites/${site.id}/locations',
      {'limit': '200'},
      owner,
      generation,
      200,
    );
    return YardSite.fromJson({
      ...site.toJson(),
      'locations': all,
      'nextLocationCursor': null,
    });
  }

  Future<void> load({bool manual = false}) async {
    final owner = session.snapshot;
    if (owner == null || _disposed) {
      return;
    }
    final generation = ++_generation;
    if (scanner is CancellableQrScanner) {
      (scanner as CancellableQrScanner).cancel();
    }
    busy = false;
    loading = true;
    trailer = null;
    message = null;
    _notify();
    try {
      final old = await cached(owner);
      if (!current(owner, generation)) {
        return;
      }
      blocked = old?.accessBlocked ?? false;
      if (blocked && !manual) {
        sites = [];
        selectedSiteId = null;
        message = 'Yard access was denied. Refresh Yard after your access is restored.';
        return;
      }
      final offline =
          session.state == SessionState.offline ||
          await session.network.currentStatus() ==
              ConnectivityStatus.unavailable;
      if (!current(owner, generation)) {
        return;
      }
      if (offline) {
        if (blocked || old == null) {
          sites = [];
          selectedSiteId = null;
          message = 'Connect to download an authorized Yard context.';
          return;
        }
        sites = (jsonDecode(old.projectionJson) as List)
            .map((s) => YardSite.fromJson(s as Map<String, dynamic>))
            .toList();
        selectedSiteId = old.selectedSiteId;
        refreshedAt = old.refreshedAt;
        message = 'Cached Yard context — server authorization is checked when syncing.';
        return;
      }
      final downloaded = (await pages(
        'tenants/${owner.membership.tenantId}/yard/sites',
        {'limit': '50'},
        owner,
        generation,
        50,
      )).map(YardSite.fromJson).toList();
      final selection =
          downloaded.where((s) => s.id == old?.selectedSiteId).firstOrNull ??
          downloaded.firstOrNull;
      if (selection != null) {
        final complete = await completeSite(selection, owner, generation);
        downloaded[downloaded.indexOf(selection)] = complete;
      }
      if (!current(owner, generation)) {
        return;
      }
      await save(owner, generation, downloaded, selection?.id);
      if (!current(owner, generation)) {
        return;
      }
      sites = downloaded;
      selectedSiteId = selection?.id;
      blocked = false;
      refreshedAt = now();
    } on ApiProblem catch (e) {
      if (!current(owner, generation)) {
        return;
      }
      if ({401, 403}.contains(e.status)) {
        await save(owner, generation, const [], null, denied: true);
        if (!current(owner, generation)) {
          return;
        }
        blocked = true;
        sites = [];
        selectedSiteId = null;
      }
      message = '${e.code}: ${e.detail}';
    } catch (_) {
      if (current(owner, generation)) {
        sites = [];
        selectedSiteId = null;
        message = 'Yard context could not be refreshed. Retry without using a partial download.';
      }
    } finally {
      if (current(owner, generation)) {
        loading = false;
        _notify();
      }
    }
  }

  Future<void> selectSite(String id) async {
    final owner = session.snapshot,
        site = sites.where((s) => s.id == id).firstOrNull;
    if (owner == null || site == null || busy || loading || blocked) {
      return;
    }
    final generation = ++_generation;
    trailer = null;
    loading = true;
    message = null;
    _notify();
    try {
      if (site.nextLocationCursor != null &&
          session.state != SessionState.ready) {
        message = 'Connect to download all locations for this Site.';
        return;
      }
      final complete = await completeSite(site, owner, generation);
      if (!current(owner, generation)) {
        return;
      }
      final updated = sites.map((s) => s.id == id ? complete : s).toList();
      await save(owner, generation, updated, id);
      if (current(owner, generation)) {
        sites = updated;
        selectedSiteId = id;
      }
    } catch (_) {
      if (current(owner, generation)) {
        selectedSiteId = null;
        message = 'Site locations could not be loaded. Retry refresh.';
      }
    } finally {
      if (current(owner, generation)) {
        loading = false;
        _notify();
      }
    }
  }

  Future<void> scan() async {
    final owner = session.snapshot;
    if (owner == null || !usable) {
      return;
    }
    final generation = _generation;
    busy = true;
    trailer = null;
    message = null;
    _notify();
    try {
      final raw = await scanner.scan();
      if (!current(owner, generation) || raw == null) {
        return;
      }
      final token = trailerPublicToken(raw);
      if (token == null) {
        throw const FormatException('Invalid QR');
      }
      final offline =
          session.state == SessionState.offline ||
          await session.network.currentStatus() ==
              ConnectivityStatus.unavailable;
      if (!current(owner, generation)) {
        return;
      }
      YardTrailer result;
      if (offline) {
        final stored = await session.cache.identifiedTrailer(owner, token);
        if (!current(owner, generation)) {
          return;
        }
        if (stored == null || stored['yardProjection'] != true) {
          message = 'Scan this Trailer online once before using its recorded Yard projection offline.';
          return;
        }
        result = YardTrailer.fromJson({...stored}..remove('yardProjection'));
      } else {
        final qr = await session.api.get('trailer-qr/$token');
        if (!current(owner, generation)) {
          return;
        }
        yardUuid(qr['trailerId']);
        result = YardTrailer.fromJson(
          await session.api.get(
            'tenants/${owner.membership.tenantId}/yard/trailers/${qr['trailerId']}',
          ),
        );
        if (!current(owner, generation)) {
          return;
        }
        if (result.id != qr['trailerId']) {
          throw const FormatException('QR identity differs');
        }
        await session.cache.rememberIdentifiedTrailer(owner, token, {
          ...result.data,
          'yardProjection': true,
        }, () => current(owner, generation));
        if (!current(owner, generation)) {
          return;
        }
      }
      trailer = result;
      if (result.location?['siteId'] != selectedSiteId) {
        message = 'Recorded location is outside the selected Site. Select its Site; do not correct location here.';
      } else if (!result.movable) {
        message = 'This recorded Trailer state does not permit a Yard Move.';
      }
    } on ApiProblem catch (e) {
      if (!current(owner, generation)) {
        return;
      }
      if ({401, 403}.contains(e.status)) {
        await save(owner, generation, const [], null, denied: true);
        if (!current(owner, generation)) {
          return;
        }
        blocked = true;
        sites = [];
        selectedSiteId = null;
      }
      message = '${e.code}: ${e.detail}';
    } catch (_) {
      if (current(owner, generation)) {
        message = 'The Trailer could not be identified. Its recorded location was not changed.';
      }
    } finally {
      if (current(owner, generation)) {
        busy = false;
        _notify();
      }
    }
  }

  Future<String?> plannedOrigin() async {
    final owner = session.snapshot, asset = trailer;
    if (owner == null || asset == null) {
      return null;
    }
    try {
      final pending = await YardReplay(worker.store)
          .pending(owner.userId, owner.membership.tenantId, asset.id);
      if (pending.any((c) => c.commandType == 'OOS')) {
        return null;
      }
      return YardReplay(worker.store).plannedLocation(asset.data, pending);
    } catch (_) {
      return null;
    }
  }

  Future<YardTrailer?> refreshScannedTrailer() async {
    final owner = session.snapshot, asset = trailer;
    if (owner == null || asset == null || session.state != SessionState.ready) {
      return null;
    }
    final generation = _generation;
    Map<String, dynamic> data;
    try {
      data = await session.api.get(
        'tenants/${owner.membership.tenantId}/yard/trailers/${asset.id}',
      );
    } on ApiProblem catch (e) {
      if (current(owner, generation) && {401, 403}.contains(e.status)) {
        await save(owner, generation, const [], null, denied: true);
        if (current(owner, generation)) {
          blocked = true;
          sites = [];
          selectedSiteId = null;
          trailer = null;
          _notify();
        }
      } else if (current(owner, generation) && e.status == 404) {
        trailer = null;
        _notify();
      }
      rethrow;
    }
    final fresh = YardTrailer.fromJson(data);
    if (!current(owner, generation) || fresh.id != asset.id) {
      return null;
    }
    trailer = fresh;
    _notify();
    return fresh;
  }

  Future<bool> hasPhysicalConflict() async {
    final owner = session.snapshot, asset = trailer;
    if (owner == null || asset == null) {
      return false;
    }
    return (await YardReplay(
      worker.store,
    ).pending(owner.userId, owner.membership.tenantId, asset.id)).any(
      (c) => {'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(c.state),
    );
  }

  Future<void> confirmAction(
    String kind,
    Map<String, dynamic> fields,
    DateTime observedAt, {
    bool reviewedConflict = false,
  }) async {
    final owner = session.snapshot, asset = trailer;
    if (owner == null ||
        asset == null ||
        !permits(YardCommandIntent.capabilityFor(kind))) {
      return;
    }
    final generation = _generation;
    busy = true;
    message = null;
    _notify();
    try {
      await YardReplay(worker.store).captureAction(
        owner.userId,
        owner.membership.tenantId,
        asset.data,
        kind,
        fields,
        observedAt,
        reviewedConflict: reviewedConflict,
        stillCurrent: () =>
            current(owner, generation) &&
            session.snapshot!.membership.capabilities.contains(
              YardCommandIntent.capabilityFor(kind),
            ),
      );
      if (!current(owner, generation)) {
        return;
      }
      message = switch (kind) {
        'LOCATION_CORRECTION' =>
          'Location correction saved on this device — waiting to sync.',
        'LOAD_STATE_CHANGE' =>
          'Load correction saved on this device — waiting to sync.',
        'DISCREPANCY_REPORT' => 'Discrepancy report saved on this device — waiting for server confirmation.',
        _ => 'Saved on this device — waiting to sync.',
      };
      unawaited(worker.sync());
    } catch (_) {
      if (current(owner, generation)) {
        message = 'The action was not saved. Review the current Trailer and pending operations.';
      }
    } finally {
      if (current(owner, generation)) {
        busy = false;
        _notify();
      }
    }
  }

  Future<void> confirmMove(
    String destination,
    String reviewedOrigin, {
    DateTime? observedAt,
  }) async {
    final owner = session.snapshot, asset = trailer;
    if (owner == null ||
        asset == null ||
        !canMove ||
        !selected!.locations.any((l) => l.id == destination)) {
      return;
    }
    final generation = _generation;
    busy = true;
    message = null;
    _notify();
    try {
      final from = await plannedOrigin();
      if (!current(owner, generation)) {
        return;
      }
      if (from != reviewedOrigin || from == destination) {
        message =
            'Pending moves changed. Review the origin and destination again.';
        return;
      }
      await YardReplay(worker.store).capture(
        owner.userId,
        owner.membership.tenantId,
        asset.data,
        destination,
        observedAt ?? now(),
        reviewedOrigin: reviewedOrigin,
        stillCurrent: () => current(owner, generation),
      );
      if (!current(owner, generation)) {
        return;
      }
      message = 'Saved on this device — waiting to sync. The server has not confirmed this move.';
      unawaited(worker.sync());
    } catch (_) {
      if (current(owner, generation)) {
        message = 'The move could not be saved. Review the pending operations before retrying.';
      }
    } finally {
      if (current(owner, generation)) {
        busy = false;
        _notify();
      }
    }
  }

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    session.removeListener(_sessionChanged);
    if (scanner is CancellableQrScanner) {
      (scanner as CancellableQrScanner).cancel();
    }
    super.dispose();
  }
}
