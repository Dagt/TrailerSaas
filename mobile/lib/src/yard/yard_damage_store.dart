import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:image_picker/image_picker.dart';

import '../media/media_store.dart';
import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../road/online_command_journal.dart';
import '../shift/camera_capture.dart';
import '../sync/replay_worker.dart';
import 'yard_commands.dart';
import 'yard_contracts.dart';

/// Draft/capture metadata, not another queue. Media and commands use the existing worker.
class YardDamageStore {
  YardDamageStore(this.session, this.worker, this.camera);
  final SessionController session;
  final ReplayWorker worker;
  final InspectionCamera camera;
  AppDatabase get db => worker.store.db;
  MediaStore get media => worker.media!.store;
  static final _inFlight = <String>{};
  bool busy = false;
  bool authorized(String user, String tenant) {
    final s = session.snapshot;
    return s != null &&
        session.auth.uid == s.firebaseUid &&
        s.userId == user &&
        s.membership.tenantId == tenant &&
        s.membership.capabilities.contains('YARD_MOVE_EXECUTE') &&
        s.membership.capabilities.contains('TRAILER_OOS_MARK');
  }

  Future<YardDraft?> find(String user, String tenant, String id) =>
      (db.select(db.yardDrafts)..where(
            (r) =>
                r.id.equals(id) &
                r.userId.equals(user) &
                r.tenantId.equals(tenant),
          ))
          .getSingleOrNull();
  Future<YardDraft> begin(
    YardTrailer trailer,
    String site,
    bool Function() current, {
    bool fresh = false,
    bool reviewedConflict = false,
  }) async {
    final owner = session.snapshot;
    if (owner == null ||
        worker.media == null ||
        !authorized(owner.userId, owner.membership.tenantId) ||
        !current()) {
      throw StateError('Authorized camera context is required');
    }
    final retained =
        await (db.select(db.yardDrafts)
              ..where(
                (r) =>
                    r.userId.equals(owner.userId) &
                    r.tenantId.equals(owner.membership.tenantId) &
                    r.trailerId.equals(trailer.id) &
                    r.siteId.equals(site) &
                    r.commandId.isNull(),
              )
              ..orderBy([(r) => OrderingTerm.desc(r.createdAt)])
              ..limit(1))
            .getSingleOrNull();
    if (retained != null && !fresh) {
      return retained;
    }
    final earlier = await YardReplay(worker.store)
        .pending(owner.userId, owner.membership.tenantId, trailer.id);
    if (earlier.any((c) => !YardCommandIntent.recognizes(c))) {
      throw StateError('Resolve pending Road work first');
    }
    if (!reviewedConflict &&
        earlier.any(
          (c) => {'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(c.state),
        )) {
      throw StateError('Review the existing physical conflict first');
    }
    if (reviewedConflict &&
        earlier.any(
          (c) =>
              !{'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(c.state),
        )) {
      throw StateError('Uncertain operations cannot be replaced');
    }
    if (!current()) {
      throw StateError('Yard context changed');
    }
    final predecessor = reviewedConflict
        ? null
        : earlier.where(YardCommandIntent.changesVersion).lastOrNull;
    final id = operationUuid(), at = worker.store.now();
    final request = {
      'trailerId': trailer.id,
      'siteId': site,
      'expectedTrailerVersion': predecessor == null
          ? trailer.data['version']
          : {'operationId': predecessor.clientOperationId},
      'observedAt': at.toUtc().toIso8601String(),
      'reviewedConflict': reviewedConflict,
    };
    await db
        .into(db.yardDrafts)
        .insert(
          YardDraftsCompanion.insert(
            id: id,
            userId: owner.userId,
            tenantId: owner.membership.tenantId,
            trailerId: trailer.id,
            siteId: site,
            requestJson: jsonEncode(request),
            createdAt: at,
            updatedAt: at,
          ),
        );
    return (await find(owner.userId, owner.membership.tenantId, id))!;
  }

  Future<void> comment(YardDraft draft, String text) async {
    if (!authorized(draft.userId, draft.tenantId) || text.length > 4000) {
      throw StateError('Draft is not editable');
    }
    await (db.update(db.yardDrafts)..where(
          (r) =>
              r.id.equals(draft.id) &
              r.userId.equals(draft.userId) &
              r.tenantId.equals(draft.tenantId) &
              r.commandId.isNull(),
        ))
        .write(
          YardDraftsCompanion(
            comment: Value(text),
            updatedAt: Value(worker.store.now()),
          ),
        );
  }

  Future<void> retainPhoto(YardDraft draft, String mediaId) async {
    if (!authorized(draft.userId, draft.tenantId)) {
      throw StateError('Authorization required');
    }
    final row = await media.find(draft.userId, draft.tenantId, mediaId);
    if (row == null ||
        row.trailerId != draft.trailerId ||
        row.documentType != 'DAMAGE_PHOTO' ||
        row.fileState != 'PRESENT') {
      throw StateError('Retained photograph unavailable');
    }
    await media.verify(row);
    await db.transaction(() async {
      final fresh = await find(draft.userId, draft.tenantId, draft.id);
      if (fresh == null || fresh.commandId != null) {
        throw StateError('Draft is queued');
      }
      final photos = List<String>.from(jsonDecode(fresh.photosJson) as List);
      if (photos.length >= 50) {
        throw StateError('Too many photographs');
      }
      if (!photos.contains(mediaId)) {
        photos.add(mediaId);
      }
      await (db.update(db.yardDrafts)..where(
            (r) =>
                r.id.equals(draft.id) &
                r.userId.equals(draft.userId) &
                r.tenantId.equals(draft.tenantId) &
                r.commandId.isNull(),
          ))
          .write(
            YardDraftsCompanion(
              photosJson: Value(jsonEncode(photos)),
              updatedAt: Value(worker.store.now()),
            ),
          );
    });
  }

  Future<void> take(YardDraft draft, bool Function() current) async {
    if (busy ||
        _inFlight.isNotEmpty ||
        !current() ||
        !authorized(draft.userId, draft.tenantId) ||
        draft.commandId != null) {
      throw StateError('Camera context is unavailable');
    }
    if ((jsonDecode(draft.photosJson) as List).length >= 50) {
      throw StateError('At most 50 photographs');
    }
    final id = operationUuid();
    await db
        .into(db.yardCaptures)
        .insert(
          YardCapturesCompanion.insert(
            id: id,
            userId: draft.userId,
            tenantId: draft.tenantId,
            draftId: draft.id,
            createdAt: worker.store.now(),
          ),
        );
    final capture = await (db.select(
      db.yardCaptures,
    )..where((r) => r.id.equals(id))).getSingle();
    _inFlight.add(id);
    busy = true;
    try {
      final file = await camera.takePhoto();
      if (file == null) {
        await _state(capture, 'CANCELLED');
        return;
      }
      await _persist(capture, file, draft);
    } catch (_) {
      await _state(capture, 'FAILED');
      rethrow;
    } finally {
      _inFlight.remove(id);
      busy = false;
    }
  }

  Future<void> _state(YardCapture capture, String state) async =>
      (db.update(db.yardCaptures)..where(
            (r) =>
                r.id.equals(capture.id) &
                r.userId.equals(capture.userId) &
                r.tenantId.equals(capture.tenantId) &
                (r.state.equals('PENDING') | r.state.equals('FAILED')),
          ))
          .write(YardCapturesCompanion(state: Value(state)));
  Future<void> _persist(
    YardCapture capture,
    XFile? photo, [
    YardDraft? original,
  ]) async {
    final draft =
        original ??
        await find(capture.userId, capture.tenantId, capture.draftId);
    if (draft == null || draft.commandId != null) {
      throw StateError('Capture draft is unavailable');
    }
    // A late native result stays in its original private namespace, even after logout.
    // It neither grants upload permission nor changes the active account's UI.
    if (photo != null) {
      final mime = InspectionCapture.mime(
        await photo
            .openRead(0, 12)
            .fold<List<int>>([], (all, part) => all..addAll(part)),
      );
      final file = await media.files.capture(
        capture.userId,
        capture.tenantId,
        capture.id,
        photo.openRead(),
        media.maxBytes,
      );
      await media.register(
        capture.userId,
        capture.tenantId,
        capture.id,
        'DAMAGE_PHOTO',
        mime,
        file,
        trailerId: draft.trailerId,
      );
    }
    var row = await media.find(capture.userId, capture.tenantId, capture.id);
    if (row == null) {
      final file = await media.files.inspect(
        capture.userId,
        capture.tenantId,
        capture.id,
      );
      final head = await (await media.files.open(
        capture.userId,
        capture.tenantId,
        capture.id,
      )).first;
      await media.register(
        capture.userId,
        capture.tenantId,
        capture.id,
        'DAMAGE_PHOTO',
        InspectionCapture.mime(head),
        file,
        trailerId: draft.trailerId,
      );
      row = await media.find(capture.userId, capture.tenantId, capture.id);
    }
    if (row == null || row.trailerId != draft.trailerId) {
      throw StateError('Capture did not retain its Trailer');
    }
    await media.verify(row);
    await db.transaction(() async {
      final fresh = await find(capture.userId, capture.tenantId, draft.id);
      if (fresh == null || fresh.commandId != null) {
        throw StateError('Draft already queued');
      }
      final photos = List<String>.from(jsonDecode(fresh.photosJson) as List);
      if (!photos.contains(capture.id)) {
        photos.add(capture.id);
      }
      await (db.update(db.yardDrafts)..where(
            (r) =>
                r.id.equals(draft.id) &
                r.userId.equals(draft.userId) &
                r.tenantId.equals(draft.tenantId) &
                r.commandId.isNull(),
          ))
          .write(
            YardDraftsCompanion(
              photosJson: Value(jsonEncode(photos)),
              updatedAt: Value(worker.store.now()),
            ),
          );
      await (db.update(db.yardCaptures)..where(
            (r) =>
                r.id.equals(capture.id) &
                r.userId.equals(capture.userId) &
                r.tenantId.equals(capture.tenantId),
          ))
          .write(
            YardCapturesCompanion(
              state: const Value('CAPTURED'),
              mediaId: Value(capture.id),
            ),
          );
    });
  }

  Future<void> recover() async {
    if (busy || _inFlight.isNotEmpty || worker.media == null) {
      return;
    }
    final owner = session.snapshot;
    if (owner == null || !authorized(owner.userId, owner.membership.tenantId)) {
      return;
    }
    final captures =
        await (db.select(db.yardCaptures)..where(
              (r) =>
                  r.userId.equals(owner.userId) &
                  r.tenantId.equals(owner.membership.tenantId) &
                  (r.state.equals('PENDING') | r.state.equals('FAILED')),
            ))
            .get();
    for (final capture in captures) {
      try {
        await _persist(capture, null);
        continue;
      } catch (_) {
        /* Retain incomplete copies and the draft. */
      }
      if (capture.state != 'PENDING') {
        continue;
      }
      try {
        final files = await camera.recoverPhotos();
        if (files.length != 1) {
          throw StateError('No unambiguous native capture');
        }
        await _persist(capture, files.single);
      } catch (_) {
        await _state(capture, 'FAILED');
      }
    }
  }

  Future<String?> queue(
    YardDraft draft,
    YardTrailer trailer,
    bool Function() current,
  ) => db.transaction(() async {
    final fresh = await find(draft.userId, draft.tenantId, draft.id);
    if (fresh == null ||
        !current() ||
        !authorized(draft.userId, draft.tenantId) ||
        trailer.id != draft.trailerId) {
      return null;
    }
    if (fresh.commandId != null) {
      return fresh.commandId;
    }
    final request = jsonDecode(fresh.requestJson) as Map;
    final photos = List<String>.from(jsonDecode(fresh.photosJson) as List);
    if (photos.isEmpty || fresh.comment.trim().isEmpty) {
      throw StateError('A comment and photographs are required');
    }
    final version = request['expectedTrailerVersion'];
    if (version is int && version != trailer.data['version']) {
      throw StateError(
        'Trailer changed. Start a new reviewed action; this draft is retained.',
      );
    }
    final operation = await YardReplay(worker.store).captureAction(
      draft.userId,
      draft.tenantId,
      trailer.data,
      'OOS',
      {'siteId': draft.siteId, 'comment': fresh.comment, 'documentIds': photos},
      DateTime.parse(request['observedAt'] as String),
      id: fresh.id,
      originalVersion: version,
      reviewedConflict: request['reviewedConflict'] == true,
      stillCurrent: current,
    );
    if (operation == null) {
      return null;
    }
    await (db.update(db.yardDrafts)..where(
          (r) =>
              r.id.equals(fresh.id) &
              r.userId.equals(fresh.userId) &
              r.tenantId.equals(fresh.tenantId) &
              r.commandId.isNull(),
        ))
        .write(
          YardDraftsCompanion(
            commandId: Value(operation),
            updatedAt: Value(worker.store.now()),
          ),
        );
    return operation;
  });
}
