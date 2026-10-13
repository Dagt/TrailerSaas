import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

import '../session/api_client.dart';

import '../persistence/app_database.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../media/media_file_store.dart';

/// Targeted server instructions survive removal of a Trip from current assigned work.
class DriverUpdatesStore extends ChangeNotifier {
  DriverUpdatesStore(this.db, this.session);
  final AppDatabase db;
  final SessionController session;
  bool _closed = false;
  final _errors = <String, String>{};
  String? get message {
    final s = session.snapshot;
    return s == null
        ? null
        : _errors['${s.userId}|${s.membership.tenantId}|${s.driver?.id}'];
  }

  static final _lanes = Expando<Future<void>>();
  bool current(SessionSnapshot s) =>
      !_closed &&
      session.state == SessionState.ready &&
      session.auth.uid == s.firebaseUid &&
      session.snapshot?.userId == s.userId &&
      session.snapshot?.membership.tenantId == s.membership.tenantId &&
      session.snapshot?.driver?.id == s.driver?.id &&
      session.snapshot?.driver != null &&
      session.snapshot!.membership.road;
  Stream<List<CachedDriverUpdate>> watch(String user, String tenant) {
    final driver = session.snapshot?.driver?.id;
    if (driver == null) return Stream.value([]);
    return db
        .customSelect(
          '''SELECT u.* FROM cached_driver_updates u WHERE u.user_id=? AND u.tenant_id=? AND u.driver_id=?
      AND NOT EXISTS(SELECT 1 FROM driver_update_cursors c WHERE c.user_id=u.user_id AND c.tenant_id=u.tenant_id AND c.driver_id=u.driver_id AND c.access_blocked)
      ORDER BY u.sequence DESC LIMIT 200''',
          variables: [Variable(user), Variable(tenant), Variable(driver)],
          readsFrom: {db.cachedDriverUpdates, db.driverUpdateCursors},
        )
        .watch()
        .map(
          (rows) =>
              rows.map((r) => db.cachedDriverUpdates.map(r.data)).toList(),
        );
  }

  Future<void> refresh() {
    final owner = session.snapshot;
    if (owner == null || !current(owner)) return Future.value();
    final future = (_lanes[db] ?? Future.value()).catchError((Object _) {}).then((
      _,
    ) async {
      try {
        await _refresh(owner);
        if (current(owner)) {
          _errors.remove(
            '${owner.userId}|${owner.membership.tenantId}|${owner.driver?.id}',
          );
          notifyListeners();
        }
      } catch (error, stack) {
        if (current(owner)) {
          if (error is ApiProblem && {401, 403}.contains(error.status)) {
            await db
                .into(db.driverUpdateCursors)
                .insertOnConflictUpdate(
                  DriverUpdateCursorsCompanion.insert(
                    userId: owner.userId,
                    tenantId: owner.membership.tenantId,
                    driverId: owner.driver!.id,
                    accessBlocked: const Value(true),
                  ),
                );
            await session.rejectOperationalContext(error);
          }
          if (current(owner)) {
            _errors['${owner.userId}|${owner.membership.tenantId}|${owner.driver?.id}'] = 'No se pudieron actualizar las instrucciones. Conservamos las descargadas; reintenta.';
            notifyListeners();
          }
        }
        Error.throwWithStackTrace(error, stack);
      }
    });
    _lanes[db] = future.catchError((Object _) {});
    return future;
  }

  Future<void> shutdown() async {
    if (_closed) return;
    _closed = true;
    await _lanes[db]?.catchError((Object _) {});
    super.dispose();
  }

  Future<void> _refresh(SessionSnapshot owner) async {
    if (!current(owner)) return;
    final user = owner.userId,
        tenant = owner.membership.tenantId,
        driver = owner.driver!.id;
    var cursor =
        (await (db.select(db.driverUpdateCursors)..where(
                  (r) =>
                      r.userId.equals(user) &
                      r.tenantId.equals(tenant) &
                      r.driverId.equals(driver),
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
                      'SELECT coalesce(max(sequence),0) n FROM cached_driver_updates WHERE user_id=? AND tenant_id=? AND driver_id=?',
                      variables: [
                        Variable(user),
                        Variable(tenant),
                        Variable(driver),
                      ],
                    )
                    .getSingle())
                .read<int>('n');
        for (final item in rows) {
          final old =
              await (db.select(db.cachedDriverUpdates)..where(
                    (r) =>
                        r.userId.equals(user) &
                        r.tenantId.equals(tenant) &
                        r.driverId.equals(driver) &
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
                  driverId: driver,
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
        {
          await db
              .into(db.driverUpdateCursors)
              .insertOnConflictUpdate(
                DriverUpdateCursorsCompanion.insert(
                  userId: user,
                  tenantId: tenant,
                  driverId: driver,
                  cursor: Value(next is String ? next : cursor),
                  accessBlocked: const Value(false),
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
