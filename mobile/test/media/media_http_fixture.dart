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
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart' show FakeAuth, FakeNetwork, work;
import 'media_storage_test.dart' show user, tenant, other;

class TestEvidencePort implements EvidenceReplayPort {
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
  final posts = <({String path, String? key, Map<String, dynamic> body})>[];
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
  String failCode = 'TEMPORARY_FAILURE';
  String? retryAfter;
  Completer<void>? putEntered, putRelease, finalizeEntered, finalizeRelease;
  bool switchUser = false;
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

  ReplayWorker makeWorker({bool futurePort = false}) => ReplayWorker(
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
    futureEvidence: futurePort ? TestEvidencePort(api) : null,
  );
  Future<String> capture([String type = 'BOL']) => media.capture(
    session,
    type,
    'image/jpeg',
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
            r.headers.contentType?.mimeType != 'image/jpeg') {
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
                'capabilities': [
                  'TRIP_VIEW_ASSIGNED',
                  'POD_UPLOAD',
                  'PRETRIP_CREATE',
                  'FUEL_EVENT_CREATE',
                  'EXCEPTION_REPORT',
                ],
              },
            ],
          });
        } else if (path.endsWith('/driver/me')) {
          await reply(r, {'id': user, 'name': 'Driver', 'active': true});
        } else if (RegExp(r'/trips/trip-[AB]$').hasMatch(path)) {
          await reply(r, {
            'trip': work(path.split('/').last),
            'currentCoupling': null,
            'departurePrerequisitesSatisfied': false,
          });
        } else {
          await reply(r, {
            'items': noTrips ? [] : [work('trip-A'), work('trip-B')],
            'nextCursor': null,
          });
        }
        return;
      }
      final body =
          jsonDecode(await utf8.decoder.bind(r).join()) as Map<String, dynamic>;
      final key = r.headers.value('idempotency-key');
      posts.add((path: path, key: key, body: body));
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
      if (path.endsWith('/documents/uploads')) {
        final id = operationUuid();
        documents[id] = {...body, 'id': id, 'uploadStatus': 'PENDING'};
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
        await reply(r, doc);
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
