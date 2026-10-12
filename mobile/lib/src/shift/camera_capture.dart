import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:image_picker/image_picker.dart';

import '../persistence/app_database.dart';
import '../media/media_store.dart';
import '../road/online_command_journal.dart';
import 'inspection_store.dart';

abstract interface class InspectionCamera {
  Future<XFile?> takePhoto();
  Future<List<XFile>> recoverPhotos();
}

class NativeInspectionCamera implements InspectionCamera {
  NativeInspectionCamera([ImagePicker? picker])
    : picker = picker ?? ImagePicker();
  final ImagePicker picker;
  @override
  Future<XFile?> takePhoto() =>
      picker.pickImage(source: ImageSource.camera, requestFullMetadata: false);
  @override
  Future<List<XFile>> recoverPhotos() async {
    final result = await picker.retrieveLostData();
    if (result.exception != null) {
      throw const FormatException('No se pudo recuperar la captura.');
    }
    return result.files ?? [];
  }
}

class InspectionCapture {
  InspectionCapture(this.drafts, this.media, this.camera);
  final InspectionStore drafts;
  final MediaStore media;
  final InspectionCamera camera;
  bool busy = false;
  static final _inFlight = <String>{};
  AppDatabase get db => drafts.db;
  static const positions = {
    'front': 'PRETRIP_FRONT',
    'rear': 'PRETRIP_REAR',
    'driverSide': 'PRETRIP_DRIVER_SIDE',
    'passengerSide': 'PRETRIP_PASSENGER_SIDE',
  };
  Future<void> take(InspectionDraft draft, String position) async {
    final s = drafts.owner();
    if (busy ||
        _inFlight.isNotEmpty ||
        s.userId != draft.userId ||
        s.membership.tenantId != draft.tenantId ||
        !positions.containsKey(position) ||
        draft.commandId != null ||
        !s.membership.capabilities.contains('PRETRIP_CREATE')) {
      throw const FormatException('Capture unavailable in this context.');
    }
    final id = operationUuid();
    await db
        .into(db.cameraCaptures)
        .insert(
          CameraCapturesCompanion.insert(
            id: id,
            userId: draft.userId,
            tenantId: draft.tenantId,
            draftId: draft.id,
            position: position,
            createdAt: media.now(),
          ),
        );
    final capture = (await (db.select(
      db.cameraCaptures,
    )..where((r) => r.id.equals(id))).getSingle());
    busy = true;
    _inFlight.add(capture.id);
    try {
      final file = await camera.takePhoto();
      if (file == null) {
        await _state(capture, 'CANCELLED');
        return;
      }
      await _persist(capture, file, original: draft);
    } catch (_) {
      try {
        await _state(capture, 'FAILED');
      } catch (_) {
        /* Durable capture context remains for restart recovery. */
      }
      rethrow;
    } finally {
      _inFlight.remove(capture.id);
      busy = false;
    }
  }

  Future<void> _state(CameraCapture capture, String state) async {
    await (db.update(db.cameraCaptures)..where(
          (r) =>
              r.id.equals(capture.id) &
              r.userId.equals(capture.userId) &
              r.tenantId.equals(capture.tenantId) &
              (r.state.equals('PENDING') | r.state.equals('FAILED')),
        ))
        .write(CameraCapturesCompanion(state: Value(state)));
  }

  static String mime(List<int> header) {
    if (header.length >= 3 &&
        header[0] == 255 &&
        header[1] == 216 &&
        header[2] == 255) {
      return 'image/jpeg';
    }
    if (header.length >= 8 &&
        header.take(8).join(',') == '137,80,78,71,13,10,26,10') {
      return 'image/png';
    }
    throw const FormatException(
      'La cámara debe entregar una imagen JPEG o PNG. No se convirtió el archivo.',
    );
  }

  Future<void> _persist(
    CameraCapture capture,
    XFile? photo, {
    InspectionDraft? original,
  }) async {
    final draft =
        original ??
        await drafts.find(capture.userId, capture.tenantId, capture.draftId);
    if (draft == null || draft.commandId != null) {
      throw const FormatException('Capture draft unavailable.');
    }
    // Copy bytes before SQLite reads can fail, preserving the original namespace
    // even if the app's session was changed or disposed while native UI was open.
    if (photo != null) {
      final type = mime(
            await photo
                .openRead(0, 12)
                .fold<List<int>>([], (all, next) => all..addAll(next)),
          ),
          s = drafts.session.snapshot;
      final current =
          s != null &&
          drafts.session.auth.uid == s.firebaseUid &&
          s.userId == capture.userId &&
          s.membership.tenantId == capture.tenantId;
      if (current) {
        await media.capture(
          drafts.session,
          positions[capture.position]!,
          type,
          photo.openRead(),
          tripId: draft.tripId,
          localId: capture.id,
        );
      } else {
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
          positions[capture.position]!,
          type,
          file,
          tripId: draft.tripId,
        );
      }
    }
    var row = await media.find(capture.userId, capture.tenantId, capture.id);
    if (row == null) {
      final file = await media.files.inspect(
            capture.userId,
            capture.tenantId,
            capture.id,
          ),
          header = await (await media.files.open(
            capture.userId,
            capture.tenantId,
            capture.id,
          )).first;
      await media.register(
        capture.userId,
        capture.tenantId,
        capture.id,
        positions[capture.position]!,
        mime(header),
        file,
        tripId: draft.tripId,
      );
      row = await media.find(capture.userId, capture.tenantId, capture.id);
    }
    if (row == null) throw const FormatException('Capture not stored.');
    await media.verify(row);
    await db.transaction(() async {
      final fresh = await drafts.find(
        capture.userId,
        capture.tenantId,
        draft.id,
      );
      if (fresh == null || fresh.commandId != null) {
        throw const FormatException('Draft changed.');
      }
      final photos = jsonDecode(fresh.photosJson) as Map<String, dynamic>;
      photos[capture.position] = row!.id;
      await (db.update(db.inspectionDrafts)..where(
            (r) =>
                r.id.equals(draft.id) &
                r.userId.equals(draft.userId) &
                r.tenantId.equals(draft.tenantId) &
                r.commandId.isNull(),
          ))
          .write(
            InspectionDraftsCompanion(
              photosJson: Value(jsonEncode(photos)),
              updatedAt: Value(media.now()),
            ),
          );
      await (db.update(db.cameraCaptures)..where(
            (r) =>
                r.id.equals(capture.id) &
                r.userId.equals(capture.userId) &
                r.tenantId.equals(capture.tenantId) &
                (r.state.equals('PENDING') | r.state.equals('FAILED')),
          ))
          .write(
            CameraCapturesCompanion(
              state: const Value('CAPTURED'),
              mediaId: Value(row!.id),
            ),
          );
    });
  }

  Future<void> recover() async {
    if (busy || _inFlight.isNotEmpty) return;
    await _repairFailed();
    final pending =
        await (db.select(db.cameraCaptures)
              ..where((r) => r.state.equals('PENDING'))
              ..limit(1))
            .get();
    if (pending.isEmpty) return;
    final capture = pending.single;
    try {
      try {
        await _persist(capture, null);
        return;
      } catch (_) {
        /* The native result is needed if private copy was interrupted. */
      }
      final files = await camera.recoverPhotos();
      if (files.length != 1) {
        throw const FormatException(
          'No hay una captura inequívoca para recuperar.',
        );
      }
      await _persist(capture, files.single);
    } catch (_) {
      await _state(capture, 'FAILED');
    }
    await _repairFailed();
  }

  Future<void> _repairFailed() async {
    // A complete private copy can survive a SQLite/session failure. Repair only
    // the authenticated namespace; partial files remain visible as a failed draft.
    final owner = drafts.session.snapshot;
    if (owner == null || drafts.session.auth.uid != owner.firebaseUid) return;
    final failed =
        await (db.select(db.cameraCaptures)..where(
              (r) =>
                  r.state.equals('FAILED') &
                  r.userId.equals(owner.userId) &
                  r.tenantId.equals(owner.membership.tenantId),
            ))
            .get();
    for (final row in failed) {
      try {
        await _persist(row, null);
      } catch (_) {
        /* Retake remains explicit; never discard the file or authorize it. */
      }
    }
  }
}
