import 'dart:convert';

import 'package:drift/drift.dart';

import '../session/contracts.dart';
import 'app_database.dart';

/// Only read projections; command/media retention and replay remain #46/#54.
class SessionCache {
  SessionCache(this.db);
  final AppDatabase db;
  Future<void> _writes = Future.value();
  Future<void> _serialize(Future<void> Function() action) {
    final next = _writes.then((_) => action());
    _writes = next.catchError((Object _) {});
    return next;
  }

  Future<void> clear() => _serialize(
    () => db.transaction(() async {
      await db.delete(db.cachedStops).go();
      await db.delete(db.cachedTrips).go();
      await db.delete(db.cachedTrailers).go();
      await db.delete(db.localSessionContexts).go();
    }),
  );
  Future<void> replace(
    SessionSnapshot snapshot,
    bool Function() stillCurrent,
  ) => _serialize(
    () => db.transaction(() async {
      if (!stillCurrent()) return;
      final user = snapshot.userId;
      final tenant = snapshot.membership.tenantId;
      await (db.delete(
        db.cachedStops,
      )..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))).go();
      await (db.delete(
        db.cachedTrips,
      )..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))).go();
      await (db.delete(
        db.cachedTrailers,
      )..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))).go();
      for (final trip in snapshot.trips) {
        final projection = Map<String, dynamic>.from(trip.data)
          ..remove('stops');
        await db
            .into(db.cachedTrips)
            .insertOnConflictUpdate(
              CachedTripsCompanion.insert(
                userId: user,
                tenantId: tenant,
                id: trip.id,
                serverVersion: trip.data['version'] as int,
                status: trip.status,
                projectionJson: jsonEncode(projection),
                refreshedAt: snapshot.refreshedAt,
              ),
            );
        for (final stop in trip.stops) {
          await db
              .into(db.cachedStops)
              .insertOnConflictUpdate(
                CachedStopsCompanion.insert(
                  userId: user,
                  tenantId: tenant,
                  id: stop['id'] as String,
                  tripId: trip.id,
                  sequenceNumber: stop['sequenceNumber'] as int,
                  projectionJson: jsonEncode(stop),
                ),
              );
        }
        final trailer = trip.plannedTrailer;
        if (trailer != null) {
          await db
              .into(db.cachedTrailers)
              .insertOnConflictUpdate(
                CachedTrailersCompanion.insert(
                  userId: user,
                  tenantId: tenant,
                  id: trailer['id'] as String,
                  serverVersion: trailer['version'] as int,
                  projectionJson: jsonEncode(trailer),
                  refreshedAt: snapshot.refreshedAt,
                ),
              );
        }
      }
      await db
          .into(db.localSessionContexts)
          .insertOnConflictUpdate(
            LocalSessionContextsCompanion.insert(
              firebaseUid: snapshot.firebaseUid,
              userId: user,
              tenantId: tenant,
              membershipJson: jsonEncode(snapshot.membership.toJson()),
              driverJson: Value(
                snapshot.driver == null
                    ? null
                    : jsonEncode(snapshot.driver!.toJson()),
              ),
              refreshedAt: snapshot.refreshedAt,
            ),
          );
    }),
  );
  Future<SessionSnapshot?> restore(String firebaseUid) async {
    await _writes;
    final row =
        await (db.select(db.localSessionContexts)
              ..where((r) => r.firebaseUid.equals(firebaseUid))
              ..orderBy([(r) => OrderingTerm.desc(r.refreshedAt)])
              ..limit(1))
            .getSingleOrNull();
    if (row == null) return null;
    final trips =
        await (db.select(db.cachedTrips)..where(
              (r) =>
                  r.userId.equals(row.userId) & r.tenantId.equals(row.tenantId),
            ))
            .get();
    final stops =
        await (db.select(db.cachedStops)
              ..where(
                (r) =>
                    r.userId.equals(row.userId) &
                    r.tenantId.equals(row.tenantId),
              )
              ..orderBy([(r) => OrderingTerm.asc(r.sequenceNumber)]))
            .get();
    return SessionSnapshot(
      firebaseUid,
      row.userId,
      Membership.cached(jsonDecode(row.membershipJson) as Map<String, dynamic>),
      row.driverJson == null
          ? null
          : DriverProfile.fromJson(
              jsonDecode(row.driverJson!) as Map<String, dynamic>,
            ),
      row.refreshedAt,
      trips
          .map(
            (trip) => WorkTrip({
              ...jsonDecode(trip.projectionJson) as Map<String, dynamic>,
              'stops': stops
                  .where((s) => s.tripId == trip.id)
                  .map(
                    (s) => jsonDecode(s.projectionJson) as Map<String, dynamic>,
                  )
                  .toList(),
            }),
          )
          .toList(),
    );
  }
}
