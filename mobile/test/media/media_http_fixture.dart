import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:drift/native.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/persistence/session_cache.dart';
import 'package:trailersaas_mobile/src/session/api_client.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/media/media_file_store.dart';
import 'package:trailersaas_mobile/src/media/media_store.dart';
import 'package:trailersaas_mobile/src/media/documents_client.dart';
import 'package:trailersaas_mobile/src/media/media_processor.dart';
import 'package:trailersaas_mobile/src/media/media_dependencies.dart';
import 'package:trailersaas_mobile/src/shift/shift_commands.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_replay.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart' show FakeAuth, FakeNetwork, work;
import 'media_storage_test.dart' show user, tenant, other;
import '../yard/fixtures.dart' show yardTrailer, yardSite, trailerId;

class TestEvidencePort implements EvidenceReplayPort {
  @override
  bool supports(LocalCommand intent) =>
      EvidenceCommandIntent.kindFor(intent) != null;
  TestEvidencePort(this.api);
  final ApiClient api;
  @override
  Future<Map<String, dynamic>> send(
    LocalCommand c,
    Map<String, dynamic> body,
  ) => api.post(c.endpointPath, body, c.clientOperationId);
}

class MediaHttpFixture {
  late Directory directory;
  late HttpServer server;
  late AppDatabase db;
  late PrivateMediaFileStore files;
  late MediaStore media;
  late ApiClient api;
  late SessionController session;
  late ReplayWorker worker;
  final auth = FakeAuth();
  final network = FakeNetwork();
  DateTime now = DateTime.now().toUtc();
  final documents = <String, Map<String, dynamic>>{};
  final objects = <String, List<int>>{};
  final completedKeys = <String, String>{};
  final posts =
      <({String path, String? key, Map<String, dynamic> body, String raw})>[];
  int puts = 0, finalizeEffects = 0, businessEffects = 0;
  final businessKeys = <String, String>{};
  bool loseFinalizeAck = false,
      loseInitAck = false,
      loseBusinessAck = false,
      losePutAck = false,
      noTrips = false;
  int? putStatus;
  Uri? putRedirect;
  int? failNext;
  final failureStatuses = <int>[];
  Map<String, dynamic>? finalizeResponseOverride;
  String failCode = 'TEMPORARY_FAILURE';
  String? retryAfter;
  Completer<void>? putEntered, putRelease, finalizeEntered, finalizeRelease;
  bool switchUser = false;
  bool shiftWorkflows = false, reassigned = false, shiftRevoked = false;
  bool roadWorkflows = false, roadCompleted = false;
  bool yardWorkflows = false, yardOosDenied = false;
  Map<String, dynamic> yardState = yardTrailer();
  final yardLedger =
      <String, ({String logical, Map<String, dynamic> result})>{};
  final yardConsumedDocuments = <String>{};
  static const roadTrip = '55555555-5555-4555-8555-555555555555';
  static const roadStop = '66666666-6666-4666-8666-666666666666';
  final roadLinks = <String, Set<String>>{};
  final roadLedger =
      <String, ({String logical, Map<String, dynamic> result})>{};
  Map<String, dynamic>? roadResponseOverride;
  final driverUpdates = <Map<String, dynamic>>[];
  final roadFailureStatuses = <int>[];
  bool repeatUpdatesCursor = false;
  int? updatesStatus;
  String? driverIdentityOverride;
  Completer<void>? updatesEntered, updatesRelease;
  Map<String, dynamic> roadWork() => {
    ...work(roadTrip),
    'status': roadCompleted ? 'COMPLETED' : 'AT_STOP',
    'currentTractorId': '44444444-4444-4444-8444-444444444444',
    'stops': [
      {
        ...(work(roadTrip)['stops'] as List).first as Map<String, dynamic>,
        'id': roadStop,
        'stopType': 'DELIVERY',
        'status': roadCompleted ? 'COMPLETED' : 'IN_SERVICE',
      },
    ],
  };
  Map<String, dynamic>? serverShift;
  final serverInspections = <Map<String, dynamic>>[];
  final shiftLedger =
      <String, ({String logical, Map<String, dynamic> result})>{};
  Future<void> start({
    bool futurePort = false,
    bool fileDatabase = false,
  }) async {
    directory = await Directory.systemTemp.createTemp('media-http-');
    files = PrivateMediaFileStore(() => Future.value(directory));
    db = AppDatabase(
      fileDatabase
          ? NativeDatabase(File('${directory.path}/device.sqlite'))
          : NativeDatabase.memory(),
    );
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((r) => unawaited(_handle(r)));
    api = ApiClient(
      Uri.parse('http://127.0.0.1:${server.port}/api/v1'),
      auth,
      http.Client(),
      clock: () => now,
    );
    session = SessionController(auth, api, SessionCache(db), network);
    media = MediaStore(db, files, clock: () => now);
    await session.start();
    worker = makeWorker(futurePort: futurePort);
  }

  ReplayWorker makeWorker({bool futurePort = false}) {
    final shiftReplay = shiftWorkflows || roadWorkflows
        ? ShiftReplay(CommandStore(db, clock: () => now), session)
        : null;
    return ReplayWorker(
      session,
      CommandStore(db, clock: () => now),
      MemoryJournal(),
      scheduleTimers: false,
      media: MediaProcessor(
        media,
        DocumentsClient(api, clock: () => now),
        R2SignedPutTransport(allowTestLoopback: true, clock: () => now),
        jitter: () => 0.5,
      ),
      futureEvidence: roadWorkflows
          ? RoadEvidenceReplay(shiftReplay!, media)
          : futurePort
          ? TestEvidencePort(api)
          : shiftReplay,
      shifts: shiftReplay,
    );
  }

  Future<void> restart() async {
    await worker.shutdown();
    await session.shutdown();
    await db.close();
    db = AppDatabase(NativeDatabase(File('${directory.path}/device.sqlite')));
    media = MediaStore(db, files, clock: () => now);
    session = SessionController(auth, api, SessionCache(db), network);
    await session.start();
    worker = makeWorker();
  }

  Future<String> capture([String type = 'BOL', String mime = 'image/jpeg']) =>
      media.capture(
        session,
        type,
        mime,
        Stream.fromIterable([
          [1, 2],
          [3, 4],
        ]),
        tripId: 'trip-A',
      );
  Future<void> reply(HttpRequest r, Object body, [int status = 200]) async {
    r.response.statusCode = status;
    r.response.headers.contentType = ContentType.json;
    r.response.write(jsonEncode(body));
    await r.response.close();
  }

  Future<void> _handle(HttpRequest r) async {
    try {
      final path = r.uri.path;
      if (r.method == 'PUT') {
        puts++;
        if (putEntered != null && !putEntered!.isCompleted) {
          putEntered!.complete();
        }
        if (putRelease != null) await putRelease!.future;
        final bytes = await r.fold<List<int>>(
          [],
          (all, next) => all..addAll(next),
        );
        if (r.headers.value('authorization') != null ||
            r.headers.contentLength != bytes.length ||
            r.headers.contentType?.mimeType !=
                documents[path.split('/').last]?['contentType']) {
          await reply(r, {}, 400);
          return;
        }
        if (putStatus != null) {
          final status = putStatus!;
          putStatus = null;
          if (putRedirect != null) {
            r.response.headers.set('Location', putRedirect!.toString());
          }
          await reply(r, {}, status);
          return;
        }
        objects[path.split('/').last] = bytes;
        if (losePutAck) {
          losePutAck = false;
          final socket = await r.response.detachSocket(writeHeaders: false);
          socket.destroy();
          return;
        }
        await reply(r, {});
        return;
      }
      if (r.headers.value('authorization') != 'Bearer private-bearer') {
        await reply(r, {'code': 'AUTHENTICATION_REQUIRED'}, 401);
        return;
      }
      if (r.method == 'GET') {
        if (path.endsWith('/api/v1/me')) {
          await reply(r, {
            'userId': switchUser ? other : user,
            'memberships': [
              {
                'tenantId': tenant,
                'tenantName': 'Operations',
                'status': 'ACTIVE',
                'roles': [],
                'capabilities': yardWorkflows
                    ? [
                        'YARD_MOVE_EXECUTE',
                        'TRAILER_LOCATION_CORRECT',
                        'TRAILER_STATE_CORRECT',
                        if (!yardOosDenied) 'TRAILER_OOS_MARK',
                      ]
                    : [
                        'TRIP_VIEW_ASSIGNED',
                        'POD_UPLOAD',
                        'PRETRIP_CREATE',
                        'FUEL_EVENT_CREATE',
                        'EXCEPTION_REPORT',
                        if (roadWorkflows) 'TRIP_EXECUTE_ASSIGNED',
                        if (shiftWorkflows) ...[
                          'SHIFT_START',
                          'SHIFT_END',
                          'POSTTRIP_CREATE',
                        ],
                      ],
              },
            ],
          });
        } else if (yardWorkflows && path.endsWith('/yard/sites')) {
          await reply(r, {
            'items': [yardSite()],
            'nextCursor': null,
          });
        } else if (yardWorkflows && path.contains('/trailer-qr/')) {
          await reply(r, {
            'trailerId': trailerId,
            'version': yardState['version'],
            'operationalNumber': 'T114',
            'active': true,
          });
        } else if (yardWorkflows && path.contains('/yard/trailers/')) {
          await reply(r, yardState);
        } else if (path.endsWith('/driver/me')) {
          await reply(r, {
            'id': driverIdentityOverride ?? user,
            'name': 'Driver',
            'active': true,
            if (shiftWorkflows)
              'shiftContext': {
                'currentShift': switchUser ? null : serverShift,
                'inspections': switchUser ? [] : serverInspections,
                'authorizedTractors': reassigned || noTrips
                    ? []
                    : [
                        {
                          'tractorId': '44444444-4444-4444-8444-444444444444',
                          'operationalNumber': 'T-12',
                          'tripId': null,
                        },
                      ],
                'usedTractors': serverInspections.isEmpty
                    ? []
                    : [
                        {
                          'tractorId': '44444444-4444-4444-8444-444444444444',
                          'operationalNumber': 'T-12',
                          'tripId': null,
                        },
                      ],
                'offDutyBlockers':
                    serverInspections.any(
                          (i) => i['inspectionType'] == 'PRE_TRIP',
                        ) &&
                        !serverInspections.any(
                          (i) => i['inspectionType'] == 'POST_TRIP',
                        )
                    ? ['POST_TRIP_REQUIRED']
                    : [],
              },
          });
        } else if (roadWorkflows && path.endsWith('/driver/me/updates')) {
          if (updatesStatus != null) {
            await reply(r, {'code': 'CAPABILITY_DENIED'}, updatesStatus!);
            return;
          }
          final limit = int.parse(r.uri.queryParameters['limit'] ?? '200');
          final offset =
              int.tryParse(r.uri.queryParameters['cursor'] ?? '0') ?? 0;
          final page = driverUpdates.skip(offset).take(limit).toList();
          final visible = !switchUser;
          if (updatesEntered != null && !updatesEntered!.isCompleted) {
            updatesEntered!.complete();
          }
          if (updatesRelease != null) await updatesRelease!.future;
          await reply(r, {
            'items': visible ? page : [],
            'nextCursor': repeatUpdatesCursor
                ? r.uri.queryParameters['cursor'] ?? '0'
                : offset + page.length < driverUpdates.length
                ? '${offset + page.length}'
                : null,
          });
        } else if (roadWorkflows && path.endsWith('/trips/$roadTrip')) {
          await reply(r, {
            'trip': roadWork(),
            'currentCoupling': null,
            'departurePrerequisitesSatisfied': true,
          });
        } else if (RegExp(r'/trips/trip-[AB]$').hasMatch(path)) {
          await reply(r, {
            'trip': work(path.split('/').last),
            'currentCoupling': null,
            'departurePrerequisitesSatisfied': false,
          });
        } else {
          await reply(r, {
            'items': noTrips || roadWorkflows && roadCompleted
                ? []
                : roadWorkflows
                ? [roadWork()]
                : [work('trip-A'), work('trip-B')],
            'nextCursor': null,
          });
        }
        return;
      }
      final raw = await utf8.decoder.bind(r).join();
      final body = jsonDecode(raw) as Map<String, dynamic>;
      final key = r.headers.value('idempotency-key');
      posts.add((path: path, key: key, body: body, raw: raw));
      if (failureStatuses.isNotEmpty) failNext = failureStatuses.removeAt(0);
      if (failNext != null) {
        final status = failNext!;
        failNext = null;
        if (retryAfter != null) {
          r.response.headers.set('Retry-After', retryAfter!);
        }
        await reply(r, {
          'code': failCode,
          'detail': 'sensitive detail must not persist',
        }, status);
        return;
      }
      if (path.endsWith('/documents/uploads') ||
          yardWorkflows && path.endsWith('/damage-uploads')) {
        if (yardWorkflows &&
            (yardOosDenied ||
                body['documentType'] != 'DAMAGE_PHOTO' ||
                !path.contains(trailerId))) {
          await reply(r, {'code': 'CAPABILITY_DENIED'}, 403);
          return;
        }
        final id = operationUuid();
        documents[id] = {
          ...body,
          'id': id,
          'uploadStatus': 'PENDING',
          if (yardWorkflows) 'trailerId': trailerId,
        };
        if (loseInitAck) {
          loseInitAck = false;
          final socket = await r.response.detachSocket(writeHeaders: false);
          socket.destroy();
          return;
        }
        await reply(r, {
          'documentId': id,
          'uploadStatus': 'PENDING',
          'uploadUrl':
              'http://127.0.0.1:${server.port}/objects/$id?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=private-signature',
          'expiresAt': now.add(const Duration(minutes: 5)).toIso8601String(),
        });
        return;
      }
      if (path.endsWith('/upload-completion')) {
        if (yardWorkflows && yardOosDenied) {
          await reply(r, {'code': 'CAPABILITY_DENIED'}, 403);
          return;
        }
        if (finalizeEntered != null && !finalizeEntered!.isCompleted) {
          finalizeEntered!.complete();
        }
        if (finalizeRelease != null) await finalizeRelease!.future;
        final id = path.split('/')[6], doc = documents[id], bytes = objects[id];
        if (doc == null) {
          await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
          return;
        }
        final logical = '$id|${jsonEncode(body)}';
        if (key == null ||
            completedKeys.containsKey(key) && completedKeys[key] != logical) {
          await reply(r, {'code': 'IDEMPOTENCY_KEY_REUSED'}, 409);
          return;
        }
        if (bytes == null) {
          await reply(r, {'code': 'UPLOAD_NOT_COMPLETE'}, 409);
          return;
        }
        if (bytes.length != doc['sizeBytes'] ||
            sha256.convert(bytes).toString() != body['checksumSha256']) {
          await reply(r, {'code': 'UPLOAD_CONTENT_MISMATCH'}, 409);
          return;
        }
        if (!completedKeys.containsKey(key)) {
          completedKeys[key] = logical;
          finalizeEffects++;
          doc['uploadStatus'] = 'UPLOADED';
          doc['uploadedAt'] = now.toIso8601String();
        }
        if (loseFinalizeAck) {
          loseFinalizeAck = false;
          final socket = await r.response.detachSocket(writeHeaders: false);
          socket.destroy();
          return;
        }
        final override = finalizeResponseOverride;
        finalizeResponseOverride = null;
        await reply(r, override == null ? doc : {...doc, ...override});
        return;
      }
      if (yardWorkflows &&
          (path.endsWith('/yard-moves') || path.contains('/yard/'))) {
        if (key == null || switchUser || body['trailerId'] != trailerId) {
          await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
          return;
        }
        final logical = '$path|${jsonEncode(body)}';
        final saved = yardLedger[key];
        if (saved != null && saved.logical != logical) {
          await reply(r, {'code': 'IDEMPOTENCY_KEY_REUSED'}, 409);
          return;
        }
        if (saved == null) {
          Map<String, dynamic> result;
          if (path.endsWith('/discrepancy-reports')) {
            result = {
              'trailer': {...yardState},
              'report': {
                'id': operationUuid(),
                'trailerId': trailerId,
                'siteId': body['siteId'],
                'recordedLocationId': body['recordedLocationId'],
                'reportedLocationId': body['reportedLocationId'],
                'observedTrailerVersion': body['observedTrailerVersion'],
                'actorUserId': user,
                'reason': body['reason'],
                'observedAt': body['observedAt'],
                'recordedAt': now.toUtc().toIso8601String(),
                'clientOperationId': key,
              },
            };
          } else {
            if (body['expectedTrailerVersion'] != yardState['version']) {
              await reply(r, {'code': 'STALE_VERSION'}, 409);
              return;
            }
            String event;
            if (path.endsWith('/yard-moves') ||
                path.endsWith('/location-corrections')) {
              final destination =
                  body[path.endsWith('/yard-moves')
                          ? 'toLocationId'
                          : 'newLocationId']
                      as String;
              if (path.endsWith('/yard-moves') &&
                  (body['fromLocationId'] !=
                          (yardState['currentLocation'] as Map)['id'] ||
                      yardState['serviceabilityState'] != 'IN_SERVICE')) {
                await reply(r, {'code': 'LOCATION_MISMATCH'}, 409);
                return;
              }
              yardState = {
                ...yardState,
                ...{
                  'currentLocation': yardTrailer(
                    current: destination,
                  )['currentLocation'],
                  'lastKnownLocationId': destination,
                  'lastSeenAt': body['observedAt'],
                },
              };
              event = path.endsWith('/yard-moves')
                  ? 'YARD_MOVED'
                  : 'LOCATION_CORRECTED';
            } else if (path.endsWith('/load-state-changes')) {
              if (yardState['loadState'] == body['loadState']) {
                await reply(r, {'code': 'RESOURCE_CONFLICT'}, 409);
                return;
              }
              yardState = {...yardState, 'loadState': body['loadState']};
              event = 'LOAD_STATE_CHANGED';
            } else {
              final photos = List<String>.from(body['documentIds'] as List);
              if (yardOosDenied) {
                await reply(r, {'code': 'CAPABILITY_DENIED'}, 403);
                return;
              }
              if (photos.isEmpty ||
                  photos.any(
                    (id) =>
                        documents[id]?['uploadStatus'] != 'UPLOADED' ||
                        documents[id]?['documentType'] != 'DAMAGE_PHOTO' ||
                        documents[id]?['trailerId'] != trailerId ||
                        yardConsumedDocuments.contains(id),
                  )) {
                await reply(r, {'code': 'EVIDENCE_REQUIRED'}, 409);
                return;
              }
              if (yardState['serviceabilityState'] != 'IN_SERVICE') {
                await reply(r, {'code': 'INVALID_STATE_TRANSITION'}, 409);
                return;
              }
              yardConsumedDocuments.addAll(photos);
              yardState = {
                ...yardState,
                'serviceabilityState': 'OUT_OF_SERVICE',
              };
              event = 'MARKED_OOS';
            }
            yardState = {
              ...yardState,
              'version': (yardState['version'] as int) + 1,
            };
            result = {
              'trailer': {...yardState},
              'movementEvent': {
                'id': operationUuid(),
                'eventType': event,
                'occurredAt': body['observedAt'],
                'recordedAt': now.toUtc().toIso8601String(),
              },
              if (event == 'MARKED_OOS') 'documentIds': body['documentIds'],
            };
          }
          yardLedger[key] = (logical: logical, result: result);
          businessEffects++;
        }
        if (loseBusinessAck) {
          loseBusinessAck = false;
          final socket = await r.response.detachSocket(writeHeaders: false);
          socket.destroy();
          return;
        }
        await reply(r, yardLedger[key]!.result);
        return;
      }
      if (roadWorkflows && path.endsWith('/links')) {
        final id = path.split('/')[6];
        if (reassigned || switchUser) {
          await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
          return;
        }
        if (documents[id]?['uploadStatus'] != 'UPLOADED' ||
            !{roadTrip, roadStop}.contains(body['entityId'])) {
          await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
          return;
        }
        roadLinks.putIfAbsent(id, () => {}).add(body['entityId'] as String);
        await reply(r, {
          'id': operationUuid(),
          'documentId': id,
          'entityType': body['entityType'],
          'entityId': body['entityId'],
        });
        return;
      }
      if (roadWorkflows &&
          (path.endsWith('/delivery-completions') ||
              path.endsWith('/fuel-events') ||
              path.endsWith('/exceptions'))) {
        if (roadFailureStatuses.isNotEmpty) {
          final status = roadFailureStatuses.removeAt(0);
          if (retryAfter != null) {
            r.response.headers.set('Retry-After', retryAfter!);
          }
          await reply(r, {'code': failCode}, status);
          return;
        }
        if (reassigned || switchUser) {
          await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
          return;
        }
        if (shiftRevoked) {
          await reply(r, {'code': 'CAPABILITY_DENIED'}, 403);
          return;
        }
        final logical = '$path|${jsonEncode(body)}';
        if (key == null ||
            roadLedger.containsKey(key) &&
                roadLedger[key]!.logical != logical) {
          await reply(r, {'code': 'IDEMPOTENCY_KEY_REUSED'}, 409);
          return;
        }
        Map<String, dynamic> result;
        if (roadLedger.containsKey(key)) {
          result = roadLedger[key]!.result;
        } else {
          bool doc(Object? id, String type, String target) =>
              documents[id]?['documentType'] == type &&
              documents[id]?['uploadStatus'] == 'UPLOADED' &&
              (roadLinks[id]?.contains(target) ?? false);
          if (path.endsWith('/delivery-completions')) {
            if (!path.contains(roadStop) ||
                roadCompleted ||
                !doc(body['bolDocumentId'], 'BOL', roadStop) ||
                body['evidenceMode'] == 'NO_RECEIVER_AVAILABLE' &&
                    (!doc(
                          body['deliveryPhotoDocumentId'],
                          'DELIVERY_PHOTO',
                          roadStop,
                        ) ||
                        (body['comment'] as String? ?? '').trim().isEmpty)) {
              await reply(r, {'code': 'EVIDENCE_REQUIRED'}, 409);
              return;
            }
            result = {
              'stopId': roadStop,
              'tripId': roadTrip,
              'stopStatus': 'COMPLETED',
              'tripStatus': 'COMPLETED',
              'stopVersion': 1,
              'tripVersion': 1,
              'completedAt': body['occurredAt'],
            };
            roadCompleted = true;
          } else if (path.endsWith('/fuel-events')) {
            if (body['tripId'] != roadTrip ||
                !doc(body['receiptDocumentId'], 'FUEL_RECEIPT', roadTrip)) {
              await reply(r, {'code': 'EVIDENCE_REQUIRED'}, 409);
              return;
            }
            result = {
              'id': operationUuid(),
              ...body,
              'createdAt': now.toIso8601String(),
            };
          } else {
            final ids = body['documentIds'];
            if (ids is! List ||
                body['category'] == 'EQUIPMENT_DAMAGE' && ids.isEmpty ||
                ids.any(
                  (id) => !doc(
                    id,
                    'DAMAGE_PHOTO',
                    body['stopId'] as String? ?? roadTrip,
                  ),
                )) {
              await reply(r, {'code': 'EVIDENCE_REQUIRED'}, 409);
              return;
            }
            result = {
              'id': operationUuid(),
              'tripId': roadTrip,
              ...body,
              'documentIds': List<String>.from(ids)..sort(),
              'recordedAt': now.toIso8601String(),
            };
          }
          roadLedger[key] = (logical: logical, result: result);
          businessEffects++;
        }
        if (loseBusinessAck) {
          loseBusinessAck = false;
          final socket = await r.response.detachSocket(writeHeaders: false);
          socket.destroy();
          return;
        }
        final override = roadResponseOverride;
        roadResponseOverride = null;
        await reply(r, override ?? result);
        return;
      }
      if (shiftWorkflows &&
          (path.contains('/shifts/') || path.contains('/inspections/'))) {
        if (shiftRevoked) {
          await reply(r, {'code': 'CAPABILITY_DENIED'}, 403);
          return;
        }
        final logical = '$path|${jsonEncode(body)}';
        if (key == null ||
            shiftLedger.containsKey(key) &&
                shiftLedger[key]!.logical != logical) {
          await reply(r, {'code': 'IDEMPOTENCY_KEY_REUSED'}, 409);
          return;
        }
        Map<String, dynamic> result;
        if (shiftLedger.containsKey(key)) {
          result = shiftLedger[key]!.result;
        } else if (path.endsWith('/on-duty')) {
          if (serverShift != null) {
            await reply(r, {'code': 'RESOURCE_CONFLICT'}, 409);
            return;
          }
          result = {
            'id': operationUuid(),
            'state': 'ON_DUTY',
            'onDutyAt': body['occurredAt'],
            'offDutyAt': null,
            'createdAt': now.toIso8601String(),
            'updatedAt': now.toIso8601String(),
          };
          serverShift = result;
        } else if (path.endsWith('/off-duty')) {
          if (serverShift == null ||
              path.split('/').reversed.elementAt(1) != serverShift!['id']) {
            await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
            return;
          }
          if (serverInspections.any((i) => i['inspectionType'] == 'PRE_TRIP') &&
              !serverInspections.any(
                (i) => i['inspectionType'] == 'POST_TRIP',
              )) {
            await reply(r, {'code': 'RESOURCE_CONFLICT'}, 409);
            return;
          }
          result = {
            ...serverShift!,
            'state': 'OFF_DUTY',
            'offDutyAt': body['occurredAt'],
            'updatedAt': now.toIso8601String(),
          };
          serverShift = null;
        } else {
          if (reassigned) {
            await reply(r, {'code': 'RESOURCE_NOT_FOUND'}, 404);
            return;
          }
          if (serverShift == null || body['shiftId'] != serverShift!['id']) {
            await reply(r, {'code': 'INVALID_STATE_TRANSITION'}, 409);
            return;
          }
          final pre = path.endsWith('/pre-trip');
          if (pre) {
            final expected = {
                  'front': 'PRETRIP_FRONT',
                  'rear': 'PRETRIP_REAR',
                  'driverSide': 'PRETRIP_DRIVER_SIDE',
                  'passengerSide': 'PRETRIP_PASSENGER_SIDE',
                },
                ids = body['documentIds'];
            if (ids is! Map ||
                ids.length != 4 ||
                ids.values.toSet().length != 4 ||
                expected.entries.any(
                  (e) =>
                      documents[ids[e.key]]?['uploadStatus'] != 'UPLOADED' ||
                      documents[ids[e.key]]?['documentType'] != e.value,
                )) {
              await reply(r, {'code': 'EVIDENCE_REQUIRED'}, 409);
              return;
            }
          }
          result = {
            'id': operationUuid(),
            'shiftId': body['shiftId'],
            'tripId': body['tripId'],
            'tractorId': body['tractorId'],
            'inspectionType': pre ? 'PRE_TRIP' : 'POST_TRIP',
            'odometer': body['odometer'],
            'occurredAt': body['occurredAt'],
            'createdAt': now.toIso8601String(),
          };
          serverInspections.add(result);
        }
        if (!shiftLedger.containsKey(key)) {
          shiftLedger[key] = (logical: logical, result: result);
          businessEffects++;
        }
        if (loseBusinessAck) {
          loseBusinessAck = false;
          final socket = await r.response.detachSocket(writeHeaders: false);
          socket.destroy();
          return;
        }
        await reply(r, result);
        return;
      }
      final logical = jsonEncode(body);
      if (key == null ||
          businessKeys.containsKey(key) && businessKeys[key] != logical) {
        await reply(r, {'code': 'IDEMPOTENCY_KEY_REUSED'}, 409);
        return;
      }
      if (!businessKeys.containsKey(key)) {
        businessKeys[key] = logical;
        businessEffects++;
      }
      if (loseBusinessAck) {
        loseBusinessAck = false;
        final socket = await r.response.detachSocket(writeHeaders: false);
        socket.destroy();
        return;
      }
      await reply(r, {
        'trip': work(path.contains('trip-B') ? 'trip-B' : 'trip-A'),
      });
    } catch (_) {
      try {
        await reply(r, {}, 500);
      } catch (_) {}
    }
  }

  Future<void> close() async {
    if (updatesRelease != null && !updatesRelease!.isCompleted) {
      updatesRelease!.complete();
    }
    if (putRelease != null && !putRelease!.isCompleted) putRelease!.complete();
    if (finalizeRelease != null && !finalizeRelease!.isCompleted) {
      finalizeRelease!.complete();
    }
    await worker.shutdown();
    await session.shutdown();
    api.client.close();
    await server.close(force: true);
    await db.close();
    await auth.events.close();
    await network.events.close();
    await directory.delete(recursive: true);
  }
}
