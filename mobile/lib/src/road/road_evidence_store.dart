import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:image_picker/image_picker.dart';

import '../media/media_dependencies.dart';
import '../media/media_file_store.dart';
import '../persistence/app_database.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import '../shift/camera_capture.dart';
import 'online_command_journal.dart';
import 'road_evidence_replay.dart';

/// Durable forms and native-camera context; neither local state nor photos grant server authority.
class RoadEvidenceStore {
  RoadEvidenceStore(this.replay, {InspectionCamera? camera})
    : camera = camera ?? NativeInspectionCamera();
  final RoadEvidenceReplay replay;
  final InspectionCamera camera;
  AppDatabase get db => replay.shifts.db;
  SessionController get session => replay.session;
  bool busy = false;
  static bool cameraInFlight = false;
  SessionSnapshot owner() {
    final s = session.snapshot;
    if (s == null ||
        s.driver == null ||
        session.auth.uid != s.firebaseUid ||
        !{SessionState.ready, SessionState.offline}.contains(session.state)) {
      throw const FormatException('Actualiza la sesión autorizada.');
    }
    return s;
  }

  bool current(RoadDraft draft) {
    final s = session.snapshot;
    return s != null &&
        session.auth.uid == s.firebaseUid &&
        s.userId == draft.userId &&
        s.membership.tenantId == draft.tenantId;
  }

  static String capability(String purpose) => switch (purpose) {
    'SIGNED_BOL' || 'NO_RECEIVER_AVAILABLE' => 'POD_UPLOAD',
    'FUEL' => 'FUEL_EVENT_CREATE',
    _ => 'EXCEPTION_REPORT',
  };
  Future<RoadDraft> create(String purpose, String trip, {String? stop}) async {
    final s = owner();
    final work = s.trips.where((t) => t.id == trip).firstOrNull;
    if (!{
          'SIGNED_BOL',
          'NO_RECEIVER_AVAILABLE',
          'FUEL',
          'PROBLEM',
        }.contains(purpose) ||
        !s.membership.capabilities.contains(capability(purpose)) ||
        work == null ||
        {'COMPLETED', 'CANCELLED'}.contains(work.status)) {
      throw const FormatException(
        'Trabajo no disponible para registrar esta acción.',
      );
    }
    if (stop != null && !work.stops.any((v) => v['id'] == stop)) {
      throw const FormatException('Stop no descargado.');
    }
    if ({'SIGNED_BOL', 'NO_RECEIVER_AVAILABLE'}.contains(purpose) &&
        !work.stops.any(
          (v) => v['id'] == stop && v['stopType'] == 'DELIVERY',
        )) {
      throw const FormatException('Se requiere un Stop de entrega.');
    }
    final id = operationUuid();
    final tractor = work.data['currentTractorId'];
    if (purpose == 'FUEL' &&
        (tractor is! String || !uuidPattern.hasMatch(tractor))) {
      throw const FormatException('Se requiere un Tractor asignado.');
    }
    await db
        .into(db.roadDrafts)
        .insert(
          RoadDraftsCompanion.insert(
            id: id,
            userId: s.userId,
            tenantId: s.membership.tenantId,
            tripId: trip,
            tractorId: Value(tractor as String?),
            stopId: Value(stop),
            purpose: purpose,
            updatedAt: replay.shifts.store.now(),
          ),
        );
    return (await find(s.userId, s.membership.tenantId, id))!;
  }

  Future<RoadDraft?> find(String user, String tenant, String id) =>
      (db.select(db.roadDrafts)..where(
            (r) =>
                r.id.equals(id) &
                r.userId.equals(user) &
                r.tenantId.equals(tenant),
          ))
          .getSingleOrNull();
  Stream<List<RoadDraft>> watch(String user, String tenant) =>
      (db.select(db.roadDrafts)
            ..where((r) => r.userId.equals(user) & r.tenantId.equals(tenant))
            ..orderBy([(r) => OrderingTerm.desc(r.updatedAt)]))
          .watch();
  Future<void> save(RoadDraft draft, Map<String, dynamic> inputs) async {
    if (!current(draft) || draft.commandId != null) {
      throw const FormatException('El contexto del borrador cambió.');
    }
    final fields = draft.purpose == 'FUEL'
        ? {'odometer'}
        : draft.purpose == 'PROBLEM'
        ? {'category', 'comment'}
        : {'comment'};
    if (!inputs.keys.every(fields.contains)) {
      throw const FormatException('Campos inválidos.');
    }
    if (inputs['comment'] != null &&
        (inputs['comment'] is! String ||
            (inputs['comment'] as String).length > 4000)) {
      throw const FormatException('Comentario inválido.');
    }
    if (inputs['odometer'] != null &&
        (inputs['odometer'] is! num ||
            !(inputs['odometer'] as num).isFinite ||
            (inputs['odometer'] as num) < 0 ||
            (inputs['odometer'] as num) > 1000000000000)) {
      throw const FormatException('Odómetro inválido.');
    }
    await (db.update(db.roadDrafts)..where(
          (r) =>
              r.id.equals(draft.id) &
              r.userId.equals(draft.userId) &
              r.tenantId.equals(draft.tenantId) &
              r.commandId.isNull(),
        ))
        .write(
          RoadDraftsCompanion(
            inputsJson: Value(jsonEncode(inputs)),
            updatedAt: Value(replay.shifts.store.now()),
          ),
        );
  }

  static String fieldType(RoadDraft draft, String field) =>
      switch (draft.purpose) {
        'SIGNED_BOL' => 'BOL',
        'NO_RECEIVER_AVAILABLE' => field == 'bol' ? 'BOL' : 'DELIVERY_PHOTO',
        'FUEL' => 'FUEL_RECEIPT',
        _ => 'DAMAGE_PHOTO',
      };
  Future<void> take(RoadDraft draft, String field) async {
    final s = owner();
    final allowed = switch (draft.purpose) {
      'SIGNED_BOL' => {'bol'},
      'NO_RECEIVER_AVAILABLE' => {'bol', 'deliveryPhoto'},
      'FUEL' => {'receipt'},
      _ => {'damage0', 'damage1', 'damage2', 'damage3'},
    };
    if (busy ||
        cameraInFlight ||
        !current(draft) ||
        draft.commandId != null ||
        !allowed.contains(field) ||
        !s.membership.capabilities.contains(capability(draft.purpose))) {
      throw const FormatException('Captura no disponible.');
    }
    final id = operationUuid();
    await db
        .into(db.roadCaptures)
        .insert(
          RoadCapturesCompanion.insert(
            id: id,
            userId: draft.userId,
            tenantId: draft.tenantId,
            draftId: draft.id,
            documentType: fieldType(draft, field),
            field: field,
            createdAt: replay.shifts.store.now(),
          ),
        );
    busy = true;
    cameraInFlight = true;
    try {
      final file = await camera.takePhoto();
      if (file == null) {
        await state(id, 'CANCELLED');
        return;
      }
      await persist(id, file);
    } catch (_) {
      try {
        await state(id, 'FAILED');
      } catch (_) {
        /* Original durable context remains recoverable. */
      }
      rethrow;
    } finally {
      busy = false;
      cameraInFlight = false;
    }
  }

  Future<void> state(String id, String value) =>
      (db.update(db.roadCaptures)..where(
            (r) =>
                r.id.equals(id) &
                (r.state.equals('PENDING') | r.state.equals('FAILED')),
          ))
          .write(RoadCapturesCompanion(state: Value(value)));
  Future<void> persist(String id, XFile? photo) async {
    final capture = await (db.select(
      db.roadCaptures,
    )..where((r) => r.id.equals(id))).getSingle();
    final draft = await find(capture.userId, capture.tenantId, capture.draftId);
    if (draft == null || draft.commandId != null) {
      throw const FormatException('Borrador no disponible.');
    }
    final media = replay.media;
    if (photo != null) {
      final mime = InspectionCapture.mime(
        await photo
            .openRead(0, 12)
            .fold<List<int>>([], (all, next) => all..addAll(next)),
      );
      // A late native callback preserves bytes only in the original namespace.
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
        capture.documentType,
        mime,
        file,
        tripId: draft.tripId,
      );
    }
    var row = await media.find(capture.userId, capture.tenantId, capture.id);
    if (row == null) {
      final file = await media.files.inspect(
        capture.userId,
        capture.tenantId,
        capture.id,
      );
      final header = await (await media.files.open(
        capture.userId,
        capture.tenantId,
        capture.id,
      )).first;
      await media.register(
        capture.userId,
        capture.tenantId,
        capture.id,
        capture.documentType,
        InspectionCapture.mime(header),
        file,
        tripId: draft.tripId,
      );
      row = await media.find(capture.userId, capture.tenantId, capture.id);
    }
    if (row == null) throw const FormatException('Archivo no registrado.');
    await media.verify(row);
    await db.transaction(() async {
      final fresh = (await find(
        capture.userId,
        capture.tenantId,
        capture.draftId,
      ))!;
      if (fresh.commandId != null) {
        throw const FormatException('Operación ya guardada.');
      }
      final photos = jsonDecode(fresh.mediaJson) as Map<String, dynamic>;
      photos[capture.field] = capture.id;
      await (db.update(
        db.roadDrafts,
      )..where((r) => r.id.equals(fresh.id) & r.commandId.isNull())).write(
        RoadDraftsCompanion(
          mediaJson: Value(jsonEncode(photos)),
          updatedAt: Value(media.now()),
        ),
      );
      await (db.update(db.roadCaptures)..where((r) => r.id.equals(id))).write(
        RoadCapturesCompanion(
          state: const Value('CAPTURED'),
          mediaId: Value(capture.id),
        ),
      );
    });
  }

  Future<void> recover() async {
    if (busy || cameraInFlight) return;
    for (final failed in await (db.select(
      db.roadCaptures,
    )..where((r) => r.state.equals('FAILED'))).get()) {
      try {
        await persist(failed.id, null);
      } catch (_) {
        /* Retained for explicit recapture/review. */
      }
    }
    final pending =
        await (db.select(db.roadCaptures)
              ..where((r) => r.state.equals('PENDING'))
              ..limit(1))
            .get();
    if (pending.isEmpty) return;
    try {
      await persist(pending.single.id, null);
      return;
    } catch (_) {
      /* Native interruption may precede private copy. */
    }
    try {
      final photos = await camera.recoverPhotos();
      if (photos.length == 1) {
        await persist(pending.single.id, photos.single);
      } else {
        await state(pending.single.id, photos.isEmpty ? 'CANCELLED' : 'FAILED');
      }
    } catch (_) {
      await state(pending.single.id, 'FAILED');
    }
  }

  Future<String> queue(RoadDraft draft) async {
    final s = owner();
    if (!current(draft) ||
        !s.membership.capabilities.contains(capability(draft.purpose))) {
      throw const FormatException('La sesión cambió.');
    }
    return db.transaction(() async {
      final fresh = (await find(draft.userId, draft.tenantId, draft.id))!;
      if (fresh.commandId != null) return fresh.commandId!;
      final work = s.trips.where((t) => t.id == fresh.tripId).firstOrNull;
      if (work == null) throw const FormatException('Trabajo no descargado.');
      final photos = jsonDecode(fresh.mediaJson) as Map<String, dynamic>,
          inputs = jsonDecode(fresh.inputsJson) as Map<String, dynamic>;
      final kind = switch (fresh.purpose) {
        'SIGNED_BOL' => EvidenceCommandKind.signedBol,
        'NO_RECEIVER_AVAILABLE' => EvidenceCommandKind.noReceiver,
        'FUEL' => EvidenceCommandKind.fuel,
        _ => EvidenceCommandKind.damage,
      };
      final body = <String, dynamic>{
        'occurredAt': replay.shifts.store.now().toIso8601String(),
      };
      switch (kind) {
        case EvidenceCommandKind.signedBol:
          body.addAll({
            'evidenceMode': 'SIGNED_BOL',
            'bolDocumentId': photos['bol'],
          });
        case EvidenceCommandKind.noReceiver:
          body.addAll({
            'evidenceMode': 'NO_RECEIVER_AVAILABLE',
            'bolDocumentId': photos['bol'],
            'deliveryPhotoDocumentId': photos['deliveryPhoto'],
            'comment': (inputs['comment'] as String? ?? '').trim(),
          });
        case EvidenceCommandKind.fuel:
          if (fresh.tractorId != work.data['currentTractorId']) {
            throw const FormatException(
              'El Tractor cambió. Conserva el recibo y revisa con operaciones.',
            );
          }
          body.addAll({
            'tripId': fresh.tripId,
            'tractorId': fresh.tractorId,
            'odometer': inputs['odometer'],
            'receiptDocumentId': photos['receipt'],
          });
        case EvidenceCommandKind.damage:
          body.addAll({
            'stopId': fresh.stopId,
            'category': inputs['category'],
            'comment': (inputs['comment'] as String? ?? '').trim(),
            'documentIds': photos.entries.toList()
              ..sort((a, b) => a.key.compareTo(b.key)),
          });
          body['documentIds'] =
              (body['documentIds'] as List<MapEntry<String, dynamic>>)
                  .map((e) => e.value)
                  .toList();
        case EvidenceCommandKind.preTrip:
          throw const FormatException();
      }
      final id = operationUuid();
      await MediaDependencies(replay.shifts.store).enqueue(
        s.userId,
        s.membership.tenantId,
        EvidenceCommandIntent(
          kind,
          OnlineCommand(
            id,
            fresh.tripId,
            EvidenceCommandIntent.path(
              kind,
              fresh.tenantId,
              fresh.tripId,
              fresh.stopId,
            ),
            body,
          ),
          resources: [
            'trip:${fresh.tripId}',
            if (work.data['currentTractorId'] != null)
              'tractor:${work.data['currentTractorId']}',
          ],
        ),
      );
      await (db.update(
        db.roadDrafts,
      )..where((r) => r.id.equals(fresh.id) & r.commandId.isNull())).write(
        RoadDraftsCompanion(
          commandId: Value(id),
          updatedAt: Value(replay.shifts.store.now()),
        ),
      );
      return id;
    });
  }
}
