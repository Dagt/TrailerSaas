import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../road/online_command_journal.dart';
import '../session/session_controller.dart';
import '../sync/command_store.dart';
import 'media_file_store.dart';

enum MediaState {
  localOnly('LOCAL_ONLY'),
  initPending('INIT_PENDING'),
  uploadPending('UPLOAD_PENDING'),
  uploading('UPLOADING'),
  uploaded('UPLOADED'),
  finalizePending('FINALIZE_PENDING'),
  finalized('FINALIZED'),
  failed('FAILED');

  const MediaState(this.value);
  final String value;
}

class MediaStore {
  MediaStore(
    this.db,
    this.files, {
    DateTime Function()? clock,
    this.maxBytes = 25 * 1024 * 1024,
  }) : now = clock ?? (() => DateTime.now().toUtc());
  final AppDatabase db;
  final MediaFileStore files;
  final DateTime Function() now;
  final int maxBytes;
  static const types = {
    'BOL',
    'POD',
    'DELIVERY_PHOTO',
    'PRETRIP_FRONT',
    'PRETRIP_REAR',
    'PRETRIP_DRIVER_SIDE',
    'PRETRIP_PASSENGER_SIDE',
    'DAMAGE_PHOTO',
    'FUEL_RECEIPT',
  };
  static String capability(String type) => switch (type) {
    'BOL' || 'POD' || 'DELIVERY_PHOTO' => 'POD_UPLOAD',
    'FUEL_RECEIPT' => 'FUEL_EVENT_CREATE',
    'DAMAGE_PHOTO' => 'EXCEPTION_REPORT',
    _ => 'PRETRIP_CREATE',
  };
  void validateType(String type, String mime) {
    if (!types.contains(type) ||
        !({'image/jpeg', 'image/png'}.contains(mime) ||
            mime == 'application/pdf' && {'BOL', 'POD'}.contains(type))) {
      throw const MediaFileProblem('INVALID_MEDIA_TYPE');
    }
  }

  Future<String> capture(
    SessionController session,
    String type,
    String mime,
    Stream<List<int>> bytes, {
    String? tripId,
    String? localId,
  }) async {
    final owner = session.snapshot;
    if (owner == null ||
        session.auth.uid != owner.firebaseUid ||
        owner.driver == null ||
        !{SessionState.ready, SessionState.offline}.contains(session.state) ||
        !owner.membership.capabilities.contains(capability(type)) ||
        tripId != null && !owner.trips.any((t) => t.id == tripId)) {
      throw const MediaFileProblem('AUTHORIZATION_REQUIRED');
    }
    validateType(type, mime);
    final id = localId ?? operationUuid();
    if (!uuidPattern.hasMatch(id)) {
      throw const MediaFileProblem('INVALID_MEDIA_ID');
    }
    final file = await files.capture(
      owner.userId,
      owner.membership.tenantId,
      id,
      bytes,
      maxBytes,
    );
    // An identity switch cannot make these bytes belong to the new identity.
    // Retain an orphan in the original namespace if SQLite/context becomes unavailable.
    if (session.auth.uid != owner.firebaseUid ||
        session.snapshot?.userId != owner.userId ||
        session.snapshot?.membership.tenantId != owner.membership.tenantId) {
      throw const MediaFileProblem('SESSION_CHANGED');
    }
    await register(
      owner.userId,
      owner.membership.tenantId,
      id,
      type,
      mime,
      file,
      tripId: tripId,
    );
    return id;
  }

  Future<void> register(
    String user,
    String tenant,
    String id,
    String type,
    String mime,
    StoredMediaFile file, {
    String? tripId,
  }) async {
    validateType(type, mime);
    final actual = await files.inspect(user, tenant, file.key);
    if (actual.size != file.size ||
        actual.sha256 != file.sha256 ||
        actual.size > maxBytes ||
        id != file.key) {
      throw const MediaFileProblem('FILE_CHANGED');
    }
    final existing = await find(user, tenant, id);
    if (existing != null) {
      if (existing.checksumSha256 != file.sha256 ||
          existing.documentType != type ||
          existing.contentType != mime) {
        throw const MediaFileProblem('DUPLICATE_CAPTURE');
      }
      return;
    }
    await db
        .into(db.localMedia)
        .insert(
          LocalMediaCompanion.insert(
            id: id,
            userId: user,
            tenantId: tenant,
            documentType: type,
            contentType: mime,
            fileKey: file.key,
            sizeBytes: file.size,
            checksumSha256: file.sha256,
            state: MediaState.localOnly.value,
            finalizeKey: operationUuid(),
            capturedAt: now(),
            updatedAt: now(),
            tripId: Value(tripId),
          ),
        );
  }

  Future<List<LocalMediaData>> list(String user, String tenant) =>
      (db.select(db.localMedia)
            ..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))
            ..orderBy([(r) => OrderingTerm.asc(r.capturedAt)]))
          .get();
  Stream<List<LocalMediaData>> watch(String user, String tenant) =>
      (db.select(db.localMedia)
            ..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))
            ..orderBy([(r) => OrderingTerm.asc(r.capturedAt)]))
          .watch();
  Future<LocalMediaData?> find(String user, String tenant, String id) =>
      (db.select(db.localMedia)..where(
            (r) =>
                r.id.equals(id) &
                r.userId.equals(user) &
                r.tenantId.equals(tenant),
          ))
          .getSingleOrNull();
  Future<void> verify(LocalMediaData row) async {
    if (row.fileState != 'PRESENT') {
      throw const MediaFileProblem('FILE_UNAVAILABLE');
    }
    final file = await files.inspect(row.userId, row.tenantId, row.fileKey);
    if (file.size != row.sizeBytes || file.sha256 != row.checksumSha256) {
      throw const MediaFileProblem('FILE_CHANGED');
    }
  }

  Future<bool> change(LocalMediaData row, LocalMediaCompanion update) async =>
      await (db.update(db.localMedia)..where(
            (r) =>
                r.id.equals(row.id) &
                r.userId.equals(row.userId) &
                r.tenantId.equals(row.tenantId) &
                r.state.equals(row.state) &
                r.attemptCount.equals(row.attemptCount),
          ))
          .write(update) ==
      1;
  Future<bool> claim(LocalMediaData row) {
    if (row.state == 'FINALIZED' ||
        row.reviewRequired ||
        row.fileState != 'PRESENT' ||
        (row.nextRetryAt != null && now().isBefore(row.nextRetryAt!))) {
      return Future.value(false);
    }
    return change(
      row,
      LocalMediaCompanion(
        state: const Value('UPLOADING'),
        attemptCount: Value(row.attemptCount + 1),
        updatedAt: Value(now()),
        nextRetryAt: const Value(null),
      ),
    );
  }

  /// Call only while owning ReplayWorker's entire HTTP/database lane.
  Future<void> recover(String user, String tenant) async {
    for (final row in await list(user, tenant)) {
      if (row.state == 'UPLOADING') {
        await change(
          row,
          LocalMediaCompanion(
            state: Value(
              row.serverDocumentId == null
                  ? 'INIT_PENDING'
                  : 'FINALIZE_PENDING',
            ),
            updatedAt: Value(now()),
            lastErrorCode: const Value('INTERRUPTED'),
          ),
        );
      }
    }
  }

  Future<void> failed(
    LocalMediaData row,
    String code, {
    int? status,
    DateTime? retryAt,
    bool review = false,
  }) => change(
    row,
    LocalMediaCompanion(
      state: Value(
        review
            ? 'FAILED'
            : row.serverDocumentId == null
            ? 'INIT_PENDING'
            : 'FINALIZE_PENDING',
      ),
      updatedAt: Value(now()),
      lastErrorCode: Value(safeCode(code)),
      lastHttpStatus: Value(status),
      nextRetryAt: Value(retryAt),
      reviewRequired: Value(review),
    ),
  );

  static String? safeCode(String? code) =>
      {
        'FILE_CHANGED',
        'FILE_MISSING',
        'FILE_UNAVAILABLE',
        'INVALID_FILE_SIZE',
        'UPLOAD_URL_EXPIRED',
        'UNSAFE_UPLOAD_URL',
        'UPLOAD_NOT_COMPLETE',
        'UPLOAD_CONTENT_MISMATCH',
        'UPLOAD_TRANSPORT_FAILED',
        'REQUEST_FROZEN',
        'PREVIOUS_UPLOAD_SUPERSEDED',
      }.contains(code)
      ? code
      : CommandStore.safeCode(code);

  Future<void> retryReviewed(String user, String tenant, String id) async {
    final row = await find(user, tenant, id);
    if (row == null ||
        row.state != 'FAILED' ||
        row.lastErrorCode == 'IDEMPOTENCY_KEY_REUSED') {
      return;
    }
    await verify(row);
    await change(
      row,
      LocalMediaCompanion(
        state: Value(
          row.serverDocumentId == null ? 'INIT_PENDING' : 'FINALIZE_PENDING',
        ),
        reviewRequired: const Value(false),
        nextRetryAt: Value(now()),
        updatedAt: Value(now()),
      ),
    );
  }

  Future<void> newAttempt(LocalMediaData row) async {
    final used = await db
        .customSelect(
          'SELECT 1 FROM command_media_dependencies d JOIN materialized_requests r ON r.client_operation_id=d.client_operation_id WHERE d.media_id=? LIMIT 1',
          variables: [Variable(row.id)],
        )
        .get();
    if (used.isNotEmpty) throw const MediaFileProblem('REQUEST_FROZEN');
    if (row.serverDocumentId == null ||
        row.lastErrorCode != 'UPLOAD_NOT_COMPLETE') {
      throw const MediaFileProblem('RESULT_UNCERTAIN');
    }
    final previous = List<Object?>.from(
      jsonDecode(row.attemptHistoryJson) as List,
    );
    if (row.serverDocumentId != null) {
      previous.add({
        'documentId': row.serverDocumentId,
        'finalizeKey': row.finalizeKey,
      });
    }
    await change(
      row,
      LocalMediaCompanion(
        state: const Value('INIT_PENDING'),
        serverDocumentId: const Value(null),
        finalizeKey: Value(operationUuid()),
        attemptHistoryJson: Value(jsonEncode(previous)),
        updatedAt: Value(now()),
        lastErrorCode: const Value('PREVIOUS_UPLOAD_SUPERSEDED'),
      ),
    );
  }

  /// Future owning workflow releases this pin only after offline access is no
  /// longer needed. Cleanup still independently requires every dependent ACK.
  Future<void> releaseOfflineUse(String user, String tenant, String id) async {
    await (db.update(db.localMedia)..where(
          (r) =>
              r.id.equals(id) &
              r.userId.equals(user) &
              r.tenantId.equals(tenant) &
              r.state.equals('FINALIZED'),
        ))
        .write(const LocalMediaCompanion(retainOffline: Value(false)));
  }

  /// Filesystem removal uses a durable tombstone. A crash never makes a missing
  /// file look available; retries complete REMOVING without initiating upload.
  Future<int> cleanup(
    String user,
    String tenant, {
    Duration grace = const Duration(days: 7),
  }) async {
    if (grace < const Duration(days: 1)) {
      throw ArgumentError('Grace must be at least one day');
    }
    var removed = 0;
    for (final row in await list(user, tenant)) {
      final eligible = await db.transaction(() async {
        final current = await find(user, tenant, row.id);
        if (current == null ||
            current.state != 'FINALIZED' ||
            current.retainOffline ||
            current.finalizedAt == null ||
            now().difference(current.finalizedAt!) < grace ||
            current.fileState == 'REMOVED') {
          return false;
        }
        final pending = await db
            .customSelect(
              "SELECT 1 FROM command_media_dependencies d JOIN local_commands c ON c.client_operation_id=d.client_operation_id WHERE d.media_id=? AND c.state<>'ACKNOWLEDGED' LIMIT 1",
              variables: [Variable(row.id)],
            )
            .get();
        if (pending.isNotEmpty) return false;
        await (db.update(db.localMedia)..where(
              (r) =>
                  r.id.equals(row.id) &
                  r.userId.equals(user) &
                  r.tenantId.equals(tenant),
            ))
            .write(const LocalMediaCompanion(fileState: Value('REMOVING')));
        return true;
      });
      if (!eligible) continue;
      await files.remove(user, tenant, row.fileKey);
      await (db.update(db.localMedia)..where(
            (r) =>
                r.id.equals(row.id) &
                r.userId.equals(user) &
                r.tenantId.equals(tenant) &
                r.fileState.equals('REMOVING'),
          ))
          .write(const LocalMediaCompanion(fileState: Value('REMOVED')));
      removed++;
    }
    return removed;
  }
}
