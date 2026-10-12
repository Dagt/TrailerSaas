import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../media/media_file_store.dart';

/// Targeted server instructions survive removal of a Trip from current assigned work.
class DriverUpdatesStore {
  DriverUpdatesStore(this.db, this.session);
  final AppDatabase db;
  final SessionController session;
  bool _closed = false;
  static final _lanes = Expando<Future<void>>();
  bool current(SessionSnapshot s) =>
      !_closed &&
      session.state == SessionState.ready &&
      session.auth.uid == s.firebaseUid &&
      session.snapshot?.userId == s.userId &&
      session.snapshot?.membership.tenantId == s.membership.tenantId &&
      session.snapshot?.driver != null &&
      session.snapshot!.membership.road;
  Stream<List<CachedDriverUpdate>> watch(String user, String tenant) =>
      (db.select(db.cachedDriverUpdates)
            ..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))
            ..orderBy([(r) => OrderingTerm.desc(r.sequence)])
            ..limit(200))
          .watch();
  Future<void> refresh() {
    final owner = session.snapshot;
    if (owner == null || !current(owner)) return Future.value();
    final future = (_lanes[db] ?? Future.value())
        .catchError((Object _) {})
        .then((_) => _refresh(owner));
    _lanes[db] = future.catchError((Object _) {});
    return future;
  }

  Future<void> shutdown() async {
    _closed = true;
    await _lanes[db]?.catchError((Object _) {});
  }

  Future<void> _refresh(SessionSnapshot owner) async {
    if (!current(owner)) return;
    final user = owner.userId, tenant = owner.membership.tenantId;
    var cursor =
        (await (db.select(db.driverUpdateCursors)..where(
                  (r) => r.userId.equals(user) & r.tenantId.equals(tenant),
                ))
                .getSingleOrNull())
            ?.cursor;
    final seen = <String>{?cursor};
    for (var page = 0; page < 10 && current(owner); page++) {
      final response = await session.api.get(
        'tenants/$tenant/driver/me/updates',
        {'limit': '200', 'cursor': ?cursor},
      );
      if (!current(owner)) return;
      final items = response['items'], next = response['nextCursor'];
      if (items is! List ||
          items.length > 200 ||
          next != null &&
              (next is! String ||
                  next.isEmpty ||
                  next.length > 2048 ||
                  seen.contains(next))) {
        throw const FormatException('Página de instrucciones inválida.');
      }
      final rows = <Map<String, dynamic>>[];
      for (final item in items) {
        if (item is! Map<String, dynamic> ||
            item['id'] is! String ||
            !uuidPattern.hasMatch(item['id'] as String) ||
            item['tripId'] is! String ||
            !uuidPattern.hasMatch(item['tripId'] as String) ||
            item['updateType'] is! String ||
            item['occurredAt'] is! String ||
            DateTime.tryParse(item['occurredAt'] as String) == null ||
            item['payload'] is! Map) {
          throw const FormatException('Instrucción no confirmada.');
        }
        final payload = item['payload'] as Map;
        final safe = <String, dynamic>{
          for (final field in [
            'instructions',
            'reason',
            'status',
            'resolutionType',
            'affectedStopId',
            'siteSnapshot',
            'siteId',
            'locationId',
            'destinationSiteId',
            'scheduledAt',
            'requestedTrailerId',
            'expectedTrailerId',
            'decision',
            'assignmentType',
            'assigned',
            'resourceId',
            'tripVersion',
            'stopVersion',
          ])
            if (payload.containsKey(field)) field: payload[field],
        };
        rows.add({
          'id': item['id'],
          'tripId': item['tripId'],
          'updateType': item['updateType'],
          'occurredAt': item['occurredAt'],
          'payload': safe,
        });
      }
      await db.transaction(() async {
        if (!current(owner)) return;
        var sequence =
            (await db
                    .customSelect(
                      'SELECT coalesce(max(sequence),0) n FROM cached_driver_updates WHERE user_id=? AND tenant_id=?',
                      variables: [Variable(user), Variable(tenant)],
                    )
                    .getSingle())
                .read<int>('n');
        for (final item in rows) {
          final old =
              await (db.select(db.cachedDriverUpdates)..where(
                    (r) =>
                        r.userId.equals(user) &
                        r.tenantId.equals(tenant) &
                        r.id.equals(item['id'] as String),
                  ))
                  .getSingleOrNull();
          if (old != null) {
            if (old.projectionJson != jsonEncode(item)) {
              throw const FormatException(
                'La identidad de una instrucción cambió.',
              );
            }
            continue;
          }
          await db
              .into(db.cachedDriverUpdates)
              .insert(
                CachedDriverUpdatesCompanion.insert(
                  userId: user,
                  tenantId: tenant,
                  id: item['id'] as String,
                  tripId: item['tripId'] as String,
                  updateType: item['updateType'] as String,
                  projectionJson: jsonEncode(item),
                  sequence: ++sequence,
                  downloadedAt: DateTime.now().toUtc(),
                ),
              );
        }
        // nextCursor is a continuation, not a watermark. Keep the last server-issued
        // non-null anchor and reread/deduplicate its tail after reaching the end.
        if (next is String) {
          await db
              .into(db.driverUpdateCursors)
              .insertOnConflictUpdate(
                DriverUpdateCursorsCompanion.insert(
                  userId: user,
                  tenantId: tenant,
                  cursor: Value(next),
                ),
              );
        }
      });
      if (next == null) return;
      cursor = next as String;
      seen.add(cursor);
    }
  }
}
