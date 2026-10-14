import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../session/api_client.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../sync/command_store.dart';
import 'check_contracts.dart';
import 'yard_contracts.dart';

/// Cache and bounded authorized reads only. All mutations use the existing worker.
class CheckStore {
  CheckStore(this.commands);
  final CommandStore commands;
  AppDatabase get db => commands.db;
  String root(SessionSnapshot o) =>
      'tenants/${o.membership.tenantId}/yard-checks';
  Future<List<CachedYardCheck>> list(SessionSnapshot o) =>
      (db.select(db.cachedYardChecks)..where(
            (r) =>
                r.userId.equals(o.userId) &
                r.tenantId.equals(o.membership.tenantId),
          ))
          .get();
  Future<CachedYardCheck?> find(SessionSnapshot o, String id) =>
      (db.select(db.cachedYardChecks)..where(
            (r) =>
                r.userId.equals(o.userId) &
                r.tenantId.equals(o.membership.tenantId) &
                r.id.equals(id),
          ))
          .getSingleOrNull();
  Future<List<CachedYardCheckItem>> items(SessionSnapshot o, String id) =>
      (db.select(db.cachedYardCheckItems)
            ..where(
              (r) =>
                  r.userId.equals(o.userId) &
                  r.tenantId.equals(o.membership.tenantId) &
                  r.checkId.equals(id),
            )
            ..orderBy([(r) => OrderingTerm.asc(r.id)]))
          .get();
  Future<List<YardCheckObservation>> observations(
    SessionSnapshot o,
    String id,
  ) =>
      (db.select(db.yardCheckObservations)
            ..where(
              (r) =>
                  r.userId.equals(o.userId) &
                  r.tenantId.equals(o.membership.tenantId) &
                  r.checkId.equals(id),
            )
            ..orderBy([(r) => OrderingTerm.asc(r.createdAt)]))
          .get();

  Future<void> summary(
    SessionSnapshot o,
    CheckSummary s, {
    bool associate = false,
  }) async {
    final previous = await find(o, s.id);
    if (previous != null) {
      if (CheckSummary(checkMap(jsonDecode(previous.summaryJson))).origin !=
          s.origin) {
        throw const FormatException('Server changed immutable Check origin');
      }
      await (db.update(db.cachedYardChecks)..where(
            (r) =>
                r.userId.equals(o.userId) &
                r.tenantId.equals(o.membership.tenantId) &
                r.id.equals(s.id),
          ))
          .write(
            CachedYardChecksCompanion(
              status: Value(s.status),
              serverVersion: Value(s.version),
              summaryJson: Value(jsonEncode(s.json)),
              associated: Value(associate || previous.associated),
              accessBlocked: const Value(false),
              refreshedAt: Value(commands.now()),
            ),
          );
    } else {
      await db
          .into(db.cachedYardChecks)
          .insert(
            CachedYardChecksCompanion.insert(
              userId: o.userId,
              tenantId: o.membership.tenantId,
              id: s.id,
              siteId: s.siteId,
              locationId: Value(s.locationId),
              startedAt: s.json['startedAt'] as String,
              expectedCount: s.count('expectedCount'),
              status: s.status,
              serverVersion: s.version,
              summaryJson: jsonEncode(s.json),
              associated: Value(
                associate || s.json['startedByUserId'] == o.userId,
              ),
              refreshedAt: commands.now(),
            ),
          );
    }
  }

  Future<CheckSnapshot> download(
    SessionController session,
    SessionSnapshot o,
    String id,
    bool Function() current,
  ) async {
    yardUuid(id);
    // Bounded restart only for a proven version race. Invalid/malformed pages fail closed.
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        CheckSummary? first;
        String? cursor;
        final cursors = <String>{}, ids = <String>{}, trailers = <String>{};
        final all = <CheckItem>[];
        do {
          if (!current()) throw StateError('Check owner changed');
          final raw = await session.api.get('${root(o)}/$id', {
            'limit': '200',
            'cursor': ?cursor,
            if (cursor != null) 'expectedVersion': '${first!.version}',
          });
          if (!current()) throw StateError('Check owner changed');
          final page = CheckDetail(raw);
          first ??= page.check;
          if (page.check.id != id ||
              page.check.origin != first.origin ||
              page.check.status != first.status ||
              page.check.version != first.version ||
              jsonEncode(page.check.json) != jsonEncode(first.json)) {
            throw const ApiProblem(
              409,
              'STALE_VERSION',
              'The Check changed during download.',
            );
          }
          if (cursor != null && raw['snapshotComplete'] == true) {
            throw const FormatException(
              'Continuation falsely claims full snapshot',
            );
          }
          for (final i in page.items) {
            if (!ids.add(i.id) ||
                !trailers.add(i.trailerId) ||
                (cursor != null && i.id.compareTo(cursor) <= 0)) {
              throw const FormatException(
                'Repeated or unordered inventory identity',
              );
            }
            if (!i.found &&
                i.json['result'] == 'MISSING' &&
                first.status != 'COMPLETED') {
              throw const FormatException('Premature missing finding');
            }
            all.add(i);
          }
          if (page.nextCursor != null &&
              (page.items.isEmpty ||
                  page.nextCursor != page.items.last.id ||
                  !cursors.add(page.nextCursor!))) {
            throw const FormatException('Invalid inventory cursor');
          }
          cursor = page.nextCursor;
          if (all.length > first.count('totalItemCount')) {
            throw const FormatException('Inventory exceeds authorized count');
          }
        } while (cursor != null);
        if (all.length != first.count('totalItemCount') ||
            all.where((i) => i.expected).length !=
                first.count('expectedCount') ||
            all.where((i) => i.found).length != first.count('scannedCount')) {
          throw const FormatException('Inventory download incomplete');
        }
        for (final result in [
          'MATCHED',
          'UNEXPECTED',
          'LOCATION_MISMATCH',
          'MISSING',
        ]) {
          final key = switch (result) {
            'MATCHED' => 'matchedCount',
            'UNEXPECTED' => 'unexpectedCount',
            'LOCATION_MISMATCH' => 'mismatchCount',
            _ => 'missingCount',
          };
          if (all.where((i) => i.json['result'] == result).length !=
              first.count(key)) {
            throw const FormatException(
              'Snapshot classifications disagree with summary',
            );
          }
        }
        final result = CheckSnapshot(first, all);
        await persist(o, result, current);
        return result;
      } on ApiProblem catch (e) {
        if (e.status == 409 && e.code == 'STALE_VERSION' && attempt < 2) {
          continue;
        }
        if ({403, 404}.contains(e.status) && current()) await deny(o, id);
        rethrow;
      }
    }
    throw const FormatException('Check download unavailable');
  }

  Future<void> persist(
    SessionSnapshot o,
    CheckSnapshot snapshot,
    bool Function() current,
  ) => db.transaction(() async {
    if (!current()) throw StateError('Check owner changed');
    final previous = await items(o, snapshot.check.id);
    final byId = {for (final i in snapshot.items) i.id: i};
    for (final row in previous) {
      final now = byId[row.id];
      if (now == null ||
          CheckItem(checkMap(jsonDecode(row.originalJson))).origin !=
              now.origin) {
        throw const FormatException('Original inventory changed');
      }
    }
    await summary(o, snapshot.check);
    for (final i in snapshot.items) {
      if (previous.any((r) => r.id == i.id)) {
        await (db.update(db.cachedYardCheckItems)..where(
              (r) =>
                  r.userId.equals(o.userId) &
                  r.tenantId.equals(o.membership.tenantId) &
                  r.checkId.equals(snapshot.check.id) &
                  r.id.equals(i.id),
            ))
            .write(
              CachedYardCheckItemsCompanion(
                serverJson: Value(jsonEncode(i.json)),
              ),
            );
      } else {
        final oldCheck = await find(o, snapshot.check.id);
        if (oldCheck!.snapshotDownloaded && i.expected) {
          throw const FormatException('Expected membership changed');
        }
        await db
            .into(db.cachedYardCheckItems)
            .insert(
              CachedYardCheckItemsCompanion.insert(
                userId: o.userId,
                tenantId: o.membership.tenantId,
                checkId: snapshot.check.id,
                id: i.id,
                trailerId: i.trailerId,
                expected: i.expected,
                expectedLocationId: Value(i.expectedLocationId),
                originalJson: jsonEncode(i.json),
                serverJson: jsonEncode(i.json),
              ),
            );
      }
    }
    if (!current()) throw StateError('Check owner changed');
    await (db.update(db.cachedYardChecks)..where(
          (r) =>
              r.userId.equals(o.userId) &
              r.tenantId.equals(o.membership.tenantId) &
              r.id.equals(snapshot.check.id),
        ))
        .write(
          CachedYardChecksCompanion(
            snapshotDownloaded: const Value(true),
            downloadedAt: Value(commands.now()),
            accessBlocked: const Value(false),
          ),
        );
  });

  Future<void> deny(SessionSnapshot o, String id) =>
      (db.update(db.cachedYardChecks)..where(
            (r) =>
                r.userId.equals(o.userId) &
                r.tenantId.equals(o.membership.tenantId) &
                r.id.equals(id),
          ))
          .write(const CachedYardChecksCompanion(accessBlocked: Value(true)));

  Future<void> directory(
    SessionController session,
    SessionSnapshot o,
    bool Function() current,
  ) async {
    final result = <CheckSummary>[], seen = <String>{}, cursors = <String>{};
    String? cursor;
    do {
      final raw = await session.api.get(root(o), {
        'limit': '50',
        'cursor': ?cursor,
      });
      if (!current()) throw StateError('Check owner changed');
      final rows = raw['items'];
      if (rows is! List || rows.length > 50) {
        throw const FormatException('Invalid Check directory');
      }
      for (final r in rows) {
        final s = CheckSummary(checkMap(r));
        if (!seen.add(s.id)) throw const FormatException('Repeated Check');
        result.add(s);
      }
      cursor = raw['nextCursor'] as String?;
      if (cursor != null) {
        yardUuid(cursor);
        if (rows.isEmpty || !cursors.add(cursor)) {
          throw const FormatException('Invalid Check directory cursor');
        }
      }
    } while (cursor != null);
    await db.transaction(() async {
      if (!current()) throw StateError('Check owner changed');
      for (final s in result) {
        await summary(o, s);
      }
      for (final old in await list(o)) {
        if (!seen.contains(old.id)) await deny(o, old.id);
      }
      await db
          .into(db.yardCheckDirectories)
          .insertOnConflictUpdate(
            YardCheckDirectoriesCompanion.insert(
              userId: o.userId,
              tenantId: o.membership.tenantId,
              complete: const Value(true),
              refreshedAt: commands.now(),
            ),
          );
      if (!current()) throw StateError('Check owner changed');
    });
  }

  Future<bool> hasUnfinished(String user, String tenant) async {
    final checks =
        await (db.select(db.cachedYardChecks)..where(
              (r) =>
                  r.userId.equals(user) &
                  r.tenantId.equals(tenant) &
                  r.associated.equals(true),
            ))
            .get();
    final start = (await commands.list(user, tenant)).any(
      (c) => c.commandType == 'YARD_CHECK_START' && c.state != 'ACKNOWLEDGED',
    );
    return start ||
        checks.any((c) => c.status == 'IN_PROGRESS' || c.accessBlocked);
  }
}
