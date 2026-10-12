import 'dart:async';
import 'dart:io';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/media/documents_client.dart';
import 'package:trailersaas_mobile/src/media/media_file_store.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';

import 'media_http_fixture.dart';
import 'media_storage_test.dart' show user, tenant, other;

void main() {
  late MediaHttpFixture f;
  setUp(() async {
    f = MediaHttpFixture();
    await f.start();
  });
  tearDown(() => f.close());
  test(
    'real HTTP initialize PUT and finalize confirms bytes not local intent',
    () async {
      final id = await f.capture();
      final before = (await f.media.find(user, tenant, id))!;
      expect(before.state, 'LOCAL_ONLY');
      expect(before.serverDocumentId, isNull);
      await f.worker.sync();
      final row = (await f.media.find(user, tenant, id))!;
      expect(row.state, 'FINALIZED');
      expect(row.finalizedAt, isNotNull);
      expect(f.puts, 1);
      expect(f.finalizeEffects, 1);
      final init = f.posts.first, completion = f.posts.last;
      expect(init.key, isNull);
      expect(init.body, {
        'documentType': 'BOL',
        'contentType': 'image/jpeg',
        'sizeBytes': 4,
        'checksumSha256': before.checksumSha256,
      });
      expect(completion.key, before.finalizeKey);
      expect(completion.body, {'checksumSha256': before.checksumSha256});
      expect(completion.path, contains(row.serverDocumentId!));
      expect(f.objects.values.single, [1, 2, 3, 4]);
      expect(row.toJson().toString(), isNot(contains('private-bearer')));
      expect(row.toJson().toString(), isNot(contains('private-signature')));
    },
  );
  test('lost final ACK retries same document key and body without a second PUT or effect', () async {
    final id = await f.capture();
    f.loseFinalizeAck = true;
    await f.worker.sync();
    final pending = (await f.media.find(user, tenant, id))!;
    expect(pending.state, 'FINALIZE_PENDING');
    expect(f.finalizeEffects, 1);
    expect(f.puts, 1);
    f.now = f.now.add(const Duration(minutes: 2));
    await f.worker.sync();
    final done = (await f.media.find(user, tenant, id))!;
    expect(done.state, 'FINALIZED');
    expect(done.serverDocumentId, pending.serverDocumentId);
    expect(done.finalizeKey, pending.finalizeKey);
    expect(f.finalizeEffects, 1);
    expect(f.puts, 1);
    final attempts = f.posts
        .where((p) => p.path.endsWith('upload-completion'))
        .toList();
    expect(attempts, hasLength(2));
    expect(attempts[0].key, attempts[1].key);
    expect(attempts[0].body, attempts[1].body);
  });
  test('lost non-idempotent init ACK may orphan PENDING but preserves bytes and never business evidence', () async {
    final id = await f.capture();
    f.loseInitAck = true;
    await f.worker.sync();
    expect(f.documents, hasLength(1));
    expect(f.puts, 0);
    f.now = f.now.add(const Duration(minutes: 2));
    await f.worker.sync();
    expect(f.documents, hasLength(2));
    expect(f.finalizeEffects, 1);
    expect(f.businessEffects, 0);
    expect((await f.media.find(user, tenant, id))!.state, 'FINALIZED');
    expect(await f.files.inventory(user, tenant), ['$id.bin']);
  });
  test('restart after PUT resolves known document before upload and preserves identity', () async {
    final id = await f.capture();
    f.loseFinalizeAck = true;
    await f.worker.sync();
    final pending = (await f.media.find(user, tenant, id))!;
    await f.worker.shutdown();
    f.worker = f.makeWorker();
    f.now = f.now.add(const Duration(minutes: 2));
    await f.worker.sync();
    expect((await f.media.find(user, tenant, id))!.state, 'FINALIZED');
    expect(
      (await f.media.find(user, tenant, id))!.serverDocumentId,
      pending.serverDocumentId,
    );
    expect(f.puts, 1);
    expect(f.finalizeEffects, 1);
  });
  test('restart known incomplete upload supersedes old identity only after definitive missing-object response', () async {
    final id = await f.capture();
    final original = (await f.media.find(user, tenant, id))!;
    final client = DocumentsClient(f.api, clock: () => f.now);
    final grant = await client.initialize(original);
    await f.media.change(
      original,
      LocalMediaCompanion(
        state: const Value('UPLOAD_PENDING'),
        serverDocumentId: Value(grant.documentId),
      ),
    );
    await f.worker.sync();
    final row = (await f.media.find(user, tenant, id))!;
    expect(row.state, 'FINALIZED');
    expect(row.serverDocumentId, isNot(grant.documentId));
    expect(row.finalizeKey, isNot(original.finalizeKey));
    expect(row.attemptHistoryJson, contains(grant.documentId));
    expect(row.attemptHistoryJson, contains(original.finalizeKey));
    expect(f.finalizeEffects, 1);
  });
  for (final status in [408, 429, 500, 503]) {
    test(
      '$status persists eligibility and retains immutable bytes/key',
      () async {
        final id = await f.capture();
        final before = (await f.media.find(user, tenant, id))!;
        f.failNext = status;
        if (status == 429) f.retryAfter = '120';
        await f.worker.sync();
        var row = (await f.media.find(user, tenant, id))!;
        expect(row.state, 'INIT_PENDING');
        expect(row.finalizeKey, before.finalizeKey);
        expect(row.nextRetryAt, isNotNull);
        expect(row.attemptCount, 1);
        if (status == 429) {
          expect(
            row.nextRetryAt!.isAfter(f.now.add(const Duration(seconds: 119))),
            isTrue,
          );
        }
        final calls = f.posts.length;
        await f.worker.sync();
        expect(f.posts.length, calls);
        f.now = f.now.add(const Duration(minutes: 3));
        await f.worker.sync();
        row = (await f.media.find(user, tenant, id))!;
        expect(row.state, 'FINALIZED');
        expect(row.finalizeKey, before.finalizeKey);
      },
    );
  }
  test('401 renews Firebase exactly once for the same request', () async {
    await f.capture();
    f.failNext = 401;
    f.failCode = 'AUTHENTICATION_REQUIRED';
    final before = f.auth.forceRefreshes.length;
    await f.worker.sync();
    expect((await f.media.list(user, tenant)).single.state, 'FINALIZED');
    expect(f.auth.forceRefreshes.skip(before), contains(true));
    expect(f.puts, 1);
  });
  for (final status in [403, 404]) {
    test(
      '$status pauses media for review without replacing identity or losing file',
      () async {
        final id = await f.capture();
        final before = (await f.media.find(user, tenant, id))!;
        f.failNext = status;
        f.failCode = status == 403 ? 'CAPABILITY_DENIED' : 'RESOURCE_NOT_FOUND';
        await f.worker.sync();
        final row = (await f.media.find(user, tenant, id))!;
        expect(row.state, 'FAILED');
        expect(row.reviewRequired, isTrue);
        expect(row.finalizeKey, before.finalizeKey);
        expect(await f.files.inventory(user, tenant), ['$id.bin']);
        final calls = f.posts.length;
        await f.worker.sync();
        expect(f.posts.length, calls);
      },
    );
  }
  test(
    'identity reuse or content mismatch is visible and never finalizes',
    () async {
      final id = await f.capture();
      f.failNext = 409;
      f.failCode = 'IDEMPOTENCY_KEY_REUSED';
      await f.worker.sync();
      final row = (await f.media.find(user, tenant, id))!;
      expect(row.state, 'FAILED');
      await f.media.retryReviewed(user, tenant, id);
      expect((await f.media.find(user, tenant, id))!.state, 'FAILED');
      expect(f.puts, 0);
    },
  );
  test('offline keeps local media and no network confirmation', () async {
    final id = await f.capture();
    f.network.status = ConnectivityStatus.unavailable;
    f.network.events.add(f.network.status);
    await f.worker.sync();
    expect((await f.media.find(user, tenant, id))!.state, 'LOCAL_ONLY');
    expect(f.posts, isEmpty);
    expect(f.session.state, SessionState.offline);
  });
  test(
    'reconnect while PUT is live never resets claim or overlaps the request',
    () async {
      final id = await f.capture();
      f.putEntered = Completer();
      f.putRelease = Completer();
      final first = f.worker.sync();
      await f.putEntered!.future;
      expect((await f.media.find(user, tenant, id))!.state, 'UPLOADING');
      f.network.status = ConnectivityStatus.unavailable;
      f.network.events.add(f.network.status);
      f.network.status = ConnectivityStatus.available;
      f.network.events.add(f.network.status);
      await f.session.refresh();
      final second = f.worker.sync();
      expect(f.puts, 1);
      expect((await f.media.find(user, tenant, id))!.state, 'UPLOADING');
      f.putRelease!.complete();
      f.putRelease = null;
      await first;
      await second;
      await f.worker.sync();
      expect(f.puts, 1);
      expect((await f.media.find(user, tenant, id))!.state, 'FINALIZED');
    },
  );
  test(
    'late account response cannot finalize old namespace or expose its bytes',
    () async {
      final id = await f.capture();
      f.putEntered = Completer();
      f.putRelease = Completer();
      final sending = f.worker.sync();
      await f.putEntered!.future;
      f.switchUser = true;
      f.auth.uid = 'firebase-B';
      f.auth.events.add('firebase-B');
      await f.session.refresh();
      f.putRelease!.complete();
      f.putRelease = null;
      await sending;
      expect((await f.media.find(user, tenant, id))!.state, 'UPLOADING');
      expect(await f.media.list(other, tenant), isEmpty);
      expect(await f.files.inventory(other, tenant), isEmpty);
      expect(f.finalizeEffects, 0);
    },
  );
  test(
    'unsafe host credentials HTTP and expiration are rejected without a PUT',
    () async {
      final transport = R2SignedPutTransport(clock: () => f.now);
      for (final uri in [
        'http://example.test/a',
        'https://user:secret@a.r2.cloudflarestorage.com/a',
        'https://evil.test/a',
      ]) {
        expect(
          () => transport.validate(
            PendingDocumentUpload(
              user,
              Uri.parse(
                '$uri?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=x',
              ),
              f.now.add(const Duration(minutes: 1)),
            ),
          ),
          throwsA(isA<MediaFileProblem>()),
        );
      }
      expect(
        () => transport.validate(
          PendingDocumentUpload(
            user,
            Uri.parse(
              'https://a.r2.cloudflarestorage.com/a?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=x',
            ),
            f.now,
          ),
        ),
        throwsA(isA<MediaFileProblem>()),
      );
      expect(f.puts, 0);
    },
  );
  test('duplicate worker holds the same private filesystem fence across in-flight PUT', () async {
    await f.capture();
    final duplicate = f.makeWorker();
    f.putEntered = Completer();
    f.putRelease = Completer();
    final first = f.worker.sync();
    await f.putEntered!.future;
    final next = duplicate.sync();
    expect(f.puts, 1);
    f.putRelease!.complete();
    f.putRelease = null;
    await first;
    await next;
    await duplicate.shutdown();
    expect(f.puts, 1);
    expect(f.finalizeEffects, 1);
  });
  test('lost PUT ACK while offline recovers same scope automatically without another PUT', () async {
    final id = await f.capture();
    final original = (await f.media.find(user, tenant, id))!;
    f.putEntered = Completer();
    f.putRelease = Completer();
    f.losePutAck = true;
    final first = f.worker.sync();
    await f.putEntered!.future;
    f.network.status = ConnectivityStatus.unavailable;
    f.network.events.add(f.network.status);
    f.putRelease!.complete();
    f.putRelease = null;
    await first;
    expect((await f.media.find(user, tenant, id))!.state, 'UPLOADING');
    expect(f.puts, 1);
    f.network.status = ConnectivityStatus.available;
    f.network.events.add(f.network.status);
    await f.session.refresh();
    await f.worker.sync();
    final done = (await f.media.find(user, tenant, id))!;
    expect(done.state, 'FINALIZED');
    expect(done.finalizeKey, original.finalizeKey);
    expect(f.puts, 1);
    expect(f.finalizeEffects, 1);
  });
  test(
    'R2 redirect never forwards bytes or credentials to another host',
    () async {
      final peer = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      var redirected = 0;
      peer.listen((r) async {
        redirected++;
        await r.response.close();
      });
      await f.capture();
      f.putStatus = 307;
      f.putRedirect = Uri.parse('http://127.0.0.1:${peer.port}/steal');
      await f.worker.sync();
      expect(redirected, 0);
      expect((await f.media.list(user, tenant)).single.state, 'FAILED');
      expect(f.finalizeEffects, 0);
      expect(f.session.state, SessionState.ready);
      await peer.close(force: true);
    },
  );
  test(
    'R2 signature denial requires review without invalidating Firebase',
    () async {
      await f.capture();
      f.putStatus = 403;
      final refreshes = f.auth.forceRefreshes.where((v) => v).length;
      await f.worker.sync();
      expect((await f.media.list(user, tenant)).single.state, 'FAILED');
      expect(f.session.state, SessionState.ready);
      expect(f.auth.forceRefreshes.where((v) => v).length, refreshes);
    },
  );
  test(
    'malformed authorization response is review not an automatic retry',
    () async {
      await f.capture();
      f.failNext = 403;
      f.failCode = 'UNEXPECTED_RESPONSE';
      await f.worker.sync();
      final row = (await f.media.list(user, tenant)).single;
      expect(row.reviewRequired, isTrue);
      expect(row.nextRetryAt, isNull);
    },
  );
}
