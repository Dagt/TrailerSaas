import 'dart:async';
import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

import '../persistence/app_database.dart';
import '../platform/connectivity.dart';
import '../platform/native_qr_scanner.dart';
import '../platform/qr_scanner.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../sync/replay_worker.dart';
import 'check_commands.dart';
import 'check_contracts.dart';
import 'check_store.dart';
import 'yard_contracts.dart';

class YardCheckController extends ChangeNotifier {
  YardCheckController(this.session, this.worker, this.scanner);
  final SessionController session;
  final ReplayWorker worker;
  final QrScanner scanner;
  CheckStore get cache => CheckStore(worker.store);
  CheckReplay get replay => CheckReplay(worker.store, session);
  List<CachedYardCheck> checks = [];
  CachedYardCheck? selected;
  List<CachedYardCheckItem> items = [];
  List<YardCheckObservation> observations = [];
  List<LocalCommand> commands = [];
  List<CommandResult> receipts = [];
  Map<String, String> cachedTrailerNumbers = {};
  Set<String> reviewed = {};
  String? message, lastOperation;
  bool busy = false, foreground = true;
  int generation = 0;
  int _localReadSequence = 0;
  bool disposed = false;
  String? _ownerKey;
  StreamSubscription<List<LocalCommand>>? _queue;
  StreamSubscription<List<CachedYardCheck>>? _checks;
  String? key(SessionSnapshot? o) =>
      o == null || session.auth.uid != o.firebaseUid
      ? null
      : '${o.firebaseUid}|${o.userId}|${o.membership.tenantId}|${o.driver?.id}';
  bool current(SessionSnapshot o, int g) =>
      !disposed &&
      foreground &&
      generation == g &&
      session.auth.uid == o.firebaseUid &&
      key(o) == key(session.snapshot) &&
      {SessionState.ready, SessionState.offline}.contains(session.state) &&
      session.snapshot!.membership.capabilities.contains('YARD_CHECK_EXECUTE');
  bool get canScan =>
      !busy &&
      foreground &&
      selected?.status == 'IN_PROGRESS' &&
      selected?.snapshotDownloaded == true &&
      selected?.accessBlocked == false;
  bool get canCorrect =>
      session.snapshot?.membership.capabilities.contains(
        'TRAILER_LOCATION_CORRECT',
      ) ==
      true;
  void notify() {
    if (!disposed) notifyListeners();
  }

  void start() {
    session.addListener(_sessionChanged);
    _sessionChanged();
  }

  void _sessionChanged() {
    final owner = session.snapshot, next = key(owner);
    if (next != _ownerKey) {
      generation++;
      cancelScanner();
      _queue?.cancel();
      _checks?.cancel();
      _ownerKey = next;
      checks = [];
      selected = null;
      items = [];
      observations = [];
      commands = [];
      receipts = [];
      cachedTrailerNumbers = {};
      reviewed = {};
      message = null;
      busy = false;
      if (owner != null && next != null) {
        _queue = worker.store
            .watch(owner.userId, owner.membership.tenantId)
            .listen((_) => unawaited(loadLocal()));
        _checks =
            (cache.db.select(cache.db.cachedYardChecks)..where(
                  (r) =>
                      r.userId.equals(owner.userId) &
                      r.tenantId.equals(owner.membership.tenantId),
                ))
                .watch()
                .listen((_) => unawaited(loadLocal()));
        unawaited(refresh());
      }
    }
    if ({
          SessionState.invalid,
          SessionState.signedOut,
          SessionState.unavailable,
        }.contains(session.state) ||
        owner?.membership.capabilities.contains('YARD_CHECK_EXECUTE') != true) {
      generation++;
      cancelScanner();
      busy = false;
    }
    // Queue notifications may arrive while the same-owner session is refreshing.
    // Re-read durable state once it is usable instead of retaining a stale pending label.
    if (owner != null &&
        next != null &&
        {SessionState.ready, SessionState.offline}.contains(session.state)) {
      unawaited(loadLocal());
    }
    notify();
  }

  void cancelScanner() {
    if (scanner is CancellableQrScanner) {
      (scanner as CancellableQrScanner).cancel();
    }
  }

  void invalidateScope() {
    generation++;
    cancelScanner();
    busy = false;
  }

  void suspend() {
    foreground = false;
    generation++;
    cancelScanner();
    busy = false;
    notify();
  }

  void resume() {
    foreground = true;
    notify();
    unawaited(refresh());
  }

  Future<void> loadLocal() async {
    final o = session.snapshot;
    if (o == null) return;
    final g = generation;
    final ticket = ++_localReadSequence;
    try {
      final data = await cache.list(o),
          rows = await worker.store.list(o.userId, o.membership.tenantId);
      final failures = await worker.store.reviewedYardFailures(
        o.userId,
        o.membership.tenantId,
      );
      final selection = selected?.id;
      final next = data.where((c) => c.id == selection).firstOrNull;
      final i = next == null
          ? <CachedYardCheckItem>[]
          : await cache.items(o, next.id);
      final obs = next == null
          ? <YardCheckObservation>[]
          : await cache.observations(o, next.id);
      final saved =
          (await (cache.db.select(cache.db.commandResults)..where(
                (r) =>
                    r.userId.equals(o.userId) &
                    r.tenantId.equals(o.membership.tenantId) &
                    r.resultKind.equals('YARD_CHECK'),
              ))
              .get());
      final trailers =
          await (cache.db.select(cache.db.cachedTrailers)..where(
                (r) =>
                    r.userId.equals(o.userId) &
                    r.tenantId.equals(o.membership.tenantId),
              ))
              .get();
      final names = <String, String>{};
      for (final row in trailers) {
        final data = checkMap(jsonDecode(row.projectionJson));
        if (data['yardProjection'] == true &&
            data['operationalNumber'] is String) {
          names[row.id] = data['operationalNumber'] as String;
        }
      }
      if (!current(o, g) || ticket != _localReadSequence) return;
      checks = data;
      commands = rows;
      receipts = saved;
      cachedTrailerNumbers = names;
      selected = next;
      items = i;
      observations = obs;
      reviewed = failures;
      notify();
    } catch (_) {
      if (current(o, g)) {
        selected = null;
        items = [];
        observations = [];
        message = 'Saved inventory could not be read. Work is retained; retry refresh.';
        notify();
      }
    }
  }

  Future<void> action(Future<void> Function(SessionSnapshot, int) work) async {
    final o = session.snapshot;
    if (o == null || busy) return;
    final g = generation;
    if (!current(o, g)) return;
    busy = true;
    notify();
    try {
      await work(o, g);
    } on FormatException {
      if (current(o, g)) message = 'The server response could not be verified. Saved work is retained. Refresh or retry sync.';
    } on StateError catch (e) {
      if (current(o, g)) message = e.message.toString();
    } catch (_) {
      if (current(o, g)) message = 'Could not confirm with the server. Saved observations are retained. Review or retry sync.';
    } finally {
      if (current(o, g)) {
        busy = false;
        await loadLocal();
        notify();
      }
    }
  }

  Future<void> refresh() => action((o, g) async {
    await loadLocal();
    if (await session.network.currentStatus() ==
        ConnectivityStatus.unavailable) {
      message = 'Offline — only complete downloaded snapshots are available. Server status has not been refreshed.';
      return;
    }
    await cache.directory(session, o, () => current(o, g));
    final id = selected?.id;
    if (id != null) await cache.download(session, o, id, () => current(o, g));
    if (current(o, g)) {
      message = 'Check status refreshed from the authorized server.';
    }
  });
  Future<void> select(String id) {
    generation++;
    cancelScanner();
    return action((o, g) async {
      selected = (await cache.list(o)).where((c) => c.id == id).firstOrNull;
      if (selected == null) return;
      await (cache.db.update(cache.db.cachedYardChecks)..where(
            (r) =>
                r.userId.equals(o.userId) &
                r.tenantId.equals(o.membership.tenantId) &
                r.id.equals(id),
          ))
          .write(const CachedYardChecksCompanion(associated: Value(true)));
      if (await session.network.currentStatus() ==
          ConnectivityStatus.available) {
        message = 'Downloading snapshot — offline scanning unavailable until complete';
        notify();
        await cache.download(session, o, id, () => current(o, g));
      }
      if (current(o, g)) {
        message = selected!.snapshotDownloaded
            ? 'Snapshot complete — available offline'
            : 'Download incomplete — offline scanning unavailable';
        busy = false;
        await loadLocal();
      }
    });
  }

  Future<void> begin(YardSite site, String? location) => action((o, g) async {
    if (await session.network.currentStatus() != ConnectivityStatus.available ||
        session.state != SessionState.ready) {
      throw StateError('Starting a Yard Check requires a server connection.');
    }
    if (site.nextLocationCursor != null ||
        location != null &&
            !site.locations.any(
              (l) => l.id == location && {'YARD', 'DOCK'}.contains(l.type),
            )) {
      throw StateError('Download the authorized Site and locations first.');
    }
    final unsettled = (await worker.store.list(o.userId, o.membership.tenantId))
        .where(
          (c) =>
              c.commandType == 'YARD_CHECK_START' && c.state != 'ACKNOWLEDGED',
        )
        .toList();
    if (unsettled.isNotEmpty) {
      throw StateError(
        'A start is uncertain. Retry its original saved operation in Sync before starting another Check.',
      );
    }
    message = 'Starting on server';
    notify();
    final op = await replay.capture(o, 'YARD_CHECK_START', cache.root(o), {
      'siteId': site.id,
      'locationId': location,
      '_capturedAt': worker.store.now().toUtc().toIso8601String(),
    }, current: () => current(o, g));
    lastOperation = op;
    await worker.sync();
    if (!current(o, g)) return;
    final result =
        await (cache.db.select(cache.db.commandResults)..where(
              (r) =>
                  r.clientOperationId.equals(op) &
                  r.userId.equals(o.userId) &
                  r.tenantId.equals(o.membership.tenantId),
            ))
            .getSingleOrNull();
    if (result == null) {
      message = 'Start failed or uncertain — review and retry the saved operation. No local Check was created.';
      return;
    }
    selected = await cache.find(o, result.serverId);
    message = 'Server accepted — downloading snapshot';
    notify();
    await cache.download(session, o, result.serverId, () => current(o, g));
    if (current(o, g)) message = 'Snapshot complete — available offline';
  });
  Future<void> scan(String location) async {
    final originalOwner = session.snapshot, epoch = generation;
    var captured = false;
    await action((o, g) async {
      final id = selected?.id;
      if (id == null ||
          selected?.snapshotDownloaded != true ||
          selected!.status != 'IN_PROGRESS' ||
          selected!.accessBlocked) {
        throw StateError('Download a complete active Check first.');
      }
      final text = await scanner.scan();
      if (!current(o, g) || selected?.id != id || text == null) return;
      final token = trailerPublicToken(text);
      if (token == null) {
        throw StateError(
          'This QR is not a valid Trailer QR. No observation was saved.',
        );
      }
      lastOperation = await replay.scan(
        o,
        id,
        token,
        location,
        worker.store.now(),
        () => current(o, g) && selected?.id == id,
      );
      if (!current(o, g)) return;
      message = 'Locally observed — waiting to sync';
      captured = true;
      notify();
    });
    // Return to the scanner after durable capture, without waiting for network ACK.
    // The one existing worker and its subscriptions display actual receipt progress.
    if (captured && originalOwner != null && current(originalOwner, epoch)) {
      unawaited(worker.sync());
    }
  }

  Future<CheckSnapshot?> reviewState() => Future<CheckSnapshot?>.sync(() async {
    final o = session.snapshot, id = selected?.id, g = generation;
    if (o == null ||
        id == null ||
        !current(o, g) ||
        !await replay.settled(o, id) ||
        await session.network.currentStatus() != ConnectivityStatus.available) {
      throw StateError(
        'Synchronize and explicitly review all pending or conflicted observations before completing or cancelling.',
      );
    }
    final snapshot = await cache.download(session, o, id, () => current(o, g));
    if (!current(o, g) || selected?.id != id) return null;
    if (snapshot.check.status != 'IN_PROGRESS') {
      throw StateError(
        'The server Check is already terminal. Refresh its findings.',
      );
    }
    await loadLocal();
    return snapshot;
  });
  Future<void> terminal(
    CheckSnapshot reviewedState, {
    String? reason,
  }) => action((o, g) async {
    final id = selected?.id;
    if (id != reviewedState.check.id || !await replay.settled(o, id!)) {
      throw StateError('Check context changed. Review again.');
    }
    final scans = (await worker.store.list(o.userId, o.membership.tenantId))
        .where(
          (r) =>
              r.aggregateId == id &&
              r.commandType == 'YARD_CHECK_SCAN' &&
              r.state == 'ACKNOWLEDGED',
        )
        .map((r) => r.clientOperationId)
        .toList();
    await replay.capture(
      o,
      reason == null ? 'YARD_CHECK_COMPLETE' : 'YARD_CHECK_CANCEL',
      '${cache.root(o)}/$id/${reason == null ? 'completion' : 'cancellation'}',
      {
        'review': reviewedState.review,
        if (reason != null) 'reason': reason.trim(),
        '_capturedAt': worker.store.now().toUtc().toIso8601String(),
      },
      dependencies: scans,
      current: () => current(o, g) && selected?.id == id,
    );
    message =
        'Terminal intention saved — only the server can confirm its state';
    notify();
    await worker.sync();
  });
  Future<void> reviewFailure(LocalCommand command, String reason) => action((
    o,
    g,
  ) async {
    if (reason.trim().isEmpty) throw StateError('A review reason is required.');
    final id = selected?.id;
    if (id == null || id != command.aggregateId) {
      throw StateError('Check context changed.');
    }
    final state = await cache.download(session, o, id, () => current(o, g));
    await cache.db.transaction(() async {
      if (!current(o, g)) throw StateError('Check context changed.');
      await cache.db
          .into(cache.db.yardCheckReviews)
          .insert(
            YardCheckReviewsCompanion.insert(
              failedOperationId: command.clientOperationId,
              userId: o.userId,
              tenantId: o.membership.tenantId,
              checkId: id,
              reason: reason.trim(),
              authorizedStateJson: state.review,
              reviewedAt: worker.store.now(),
            ),
          );
    });
    message = 'Failed historical observation retained and explicitly reviewed. It was not acknowledged or used to correct a Trailer.';
  });
  Future<YardTrailer> currentTrailer(String id) async {
    final o = session.snapshot, g = generation;
    if (o == null || !current(o, g) || !canCorrect) {
      throw StateError('Location correction permission unavailable.');
    }
    final trailer = YardTrailer.fromJson(
      await session.api.get(
        'tenants/${o.membership.tenantId}/yard/trailers/$id',
      ),
    );
    if (!current(o, g) || trailer.id != id) {
      throw StateError('Trailer context changed.');
    }
    return trailer;
  }

  Future<void> reconcile(
    CheckItem item,
    YardTrailer trailer,
    String reason,
  ) => action((o, g) async {
    final id = selected?.id;
    if (!canCorrect ||
        id == null ||
        selected?.status != 'COMPLETED' ||
        item.trailerId != trailer.id ||
        !await replay.settled(o, id)) {
      throw StateError(
        'Refresh the completed Check and correction permission first.',
      );
    }
    final missing = item.json['result'] == 'MISSING';
    if (!missing && item.json['result'] != 'LOCATION_MISMATCH') {
      throw StateError('This finding does not permit physical reconciliation.');
    }
    if ((await worker.store.list(o.userId, o.membership.tenantId)).any(
      (c) =>
          c.state != 'ACKNOWLEDGED' &&
          (jsonDecode(c.resourceKeysJson) as List).contains(
            'trailer:${trailer.id}',
          ),
    )) {
      throw StateError(
        'Resolve pending Trailer operations before reconciliation.',
      );
    }
    await replay.capture(
      o,
      missing ? 'YARD_CHECK_MISSING' : 'YARD_CHECK_CORRECTION',
      '${cache.root(o)}/$id/items/${item.id}/${missing ? 'mark-location-unknown' : 'location-correction'}',
      {
        'trailerId': trailer.id,
        'expectedTrailerVersion': trailer.data['version'],
        'reason': reason.trim(),
        if (!missing) 'newLocationId': item.json['observedLocationId'],
        '_capturedAt': worker.store.now().toUtc().toIso8601String(),
      },
      current: () => current(o, g) && selected?.id == id,
    );
    message = 'Explicit reconciliation saved — awaiting the real server event';
    await worker.sync();
  });
  @override
  void dispose() {
    disposed = true;
    generation++;
    cancelScanner();
    session.removeListener(_sessionChanged);
    _queue?.cancel();
    _checks?.cancel();
    super.dispose();
  }
}
