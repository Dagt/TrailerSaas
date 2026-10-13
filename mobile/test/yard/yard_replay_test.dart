import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/yard/yard_commands.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory directory;
  late File file;
  late YardFixture f;
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('yard-replay-');
    file = File('${directory.path}/device.sqlite');
    f = YardFixture(database: AppDatabase(NativeDatabase(file)));
    await f.open();
  });
  tearDown(() async {
    await f.close();
    await directory.delete(recursive: true);
  });
  Future<void> queue(String destination, {String asset = trailerId}) =>
      YardReplay(f.worker.store)
          .capture('user-A', 'A', yardTrailer(id: asset), destination, f.now);
  Future<void> offline() async {
    f.mobile.network.status = ConnectivityStatus.unavailable;
    await f.mobile.session.refresh();
  }

  test('two successive offline moves keep planned origin but defer version and request until first ACK', () async {
    await offline();
    await queue(dockId);
    await queue(dock2Id);
    final original = await f.worker.store.list('user-A', 'A');
    expect(original, hasLength(2));
    final first = original.first, second = original.last;
    expect(jsonDecode(second.payloadJson)['fromLocationId'], dockId);
    expect(jsonDecode(second.payloadJson)['expectedTrailerVersion'], {
      'operationId': first.clientOperationId,
    });
    expect(jsonDecode(second.dependenciesJson), [first.clientOperationId]);
    expect(await YardReplay(f.worker.store).materialize(second), isNull);
    final posts = <http.Request>[];
    f.custom = (r) async {
      if (r.method == 'GET') return f.reads(r);
      posts.add(r);
      final body = jsonDecode(r.body) as Map<String, dynamic>;
      if (posts.length == 2) {
        expect(
          (await f.worker.store.find(
            'user-A',
            'A',
            first.clientOperationId,
          ))!.state,
          'ACKNOWLEDGED',
        );
        expect(body['expectedTrailerVersion'], 13);
        expect(body['fromLocationId'], dockId);
      }
      final response = acceptedMove(
        body,
        posts.length == 1
            ? '00000000-0000-4000-8000-000000000010'
            : '00000000-0000-4000-8000-000000000011',
      );
      f.currentTrailer = response['trailer'] as Map<String, dynamic>;
      return f.mobile.response(response);
    };
    f.mobile.network.status = ConnectivityStatus.available;
    await f.mobile.session.refresh();
    await f.worker.sync();
    expect(posts, hasLength(2));
    expect(
      (await f.worker.store.list('user-A', 'A')).map((r) => r.state),
      everyElement('ACKNOWLEDGED'),
    );
    expect(
      (await f.worker.store.find(
        'user-A',
        'A',
        second.clientOperationId,
      ))!.payloadJson,
      second.payloadJson,
    );
    final frozen = await YardReplay(f.worker.store).frozen(second);
    expect(jsonDecode(frozen!.payloadJson)['expectedTrailerVersion'], 13);
    expect(f.currentTrailer['version'], 14);
    expect(f.currentTrailer['currentLocation']['id'], dock2Id);
  });
  test('lost ACK and SQLite reopen retain UUID/request and never perform the first move twice', () async {
    await offline();
    await queue(dockId);
    await queue(dock2Id);
    final originals = await f.worker.store.list('user-A', 'A');
    final ledger = <String, Map<String, dynamic>>{};
    final bodies = <String, String>{};
    final attempts = <http.Request>[];
    var physicalMoves = 0;
    var loseAck = true;
    var server = yardTrailer();
    Future<http.Response> serverHandler(http.Request r) async {
      if (r.method == 'GET') {
        f.currentTrailer = server;
        return f.reads(r);
      }
      attempts.add(r);
      final key = r.headers['Idempotency-Key']!;
      if (ledger.containsKey(key)) {
        expect(r.body, bodies[key]);
        return f.mobile.response(ledger[key]!);
      }
      final body = jsonDecode(r.body) as Map<String, dynamic>;
      expect(body['expectedTrailerVersion'], server['version']);
      expect(body['fromLocationId'], server['currentLocation']['id']);
      final response = acceptedMove(
        body,
        physicalMoves == 0
            ? '00000000-0000-4000-8000-000000000010'
            : '00000000-0000-4000-8000-000000000011',
      );
      ledger[key] = response;
      bodies[key] = r.body;
      server = response['trailer'] as Map<String, dynamic>;
      physicalMoves++;
      if (loseAck) {
        loseAck = false;
        throw const SocketException('ACK lost');
      }
      return f.mobile.response(response);
    }

    f.custom = serverHandler;
    f.mobile.network.status = ConnectivityStatus.available;
    await f.mobile.session.refresh();
    await f.worker.sync();
    expect(physicalMoves, 1);
    expect(
      (await f.worker.store.list('user-A', 'A')).first.state,
      'RETRYABLE_FAILURE',
    );
    final firstRequest = await YardReplay(f.worker.store)
        .frozen(originals.first);
    expect(await YardReplay(f.worker.store).frozen(originals.last), isNull);
    final later = f.now.add(const Duration(minutes: 10));
    await f.close();
    f = YardFixture(database: AppDatabase(NativeDatabase(file)));
    await f.open();
    f.now = later;
    f.custom = serverHandler;
    expect(
      (await f.worker.store.list(
        'user-A',
        'A',
      )).map((r) => r.clientOperationId),
      originals.map((r) => r.clientOperationId),
    );
    expect(
      (await YardReplay(f.worker.store).frozen(originals.first))!.payloadJson,
      firstRequest!.payloadJson,
    );
    await f.worker.sync();
    expect(physicalMoves, 2);
    expect(attempts, hasLength(3));
    expect(
      attempts[0].headers['Idempotency-Key'],
      attempts[1].headers['Idempotency-Key'],
    );
    expect(attempts[0].body, attempts[1].body);
    expect(
      (await f.worker.store.list('user-A', 'A')).map((r) => r.state),
      everyElement('ACKNOWLEDGED'),
    );
  });
  for (final status in [408, 429, 500, 503]) {
    test('$status uses persisted retry and the same frozen key/body', () async {
      await queue(dockId);
      final captured = (await f.worker.store.list('user-A', 'A')).single;
      final posts = <http.Request>[];
      f.custom = (r) async {
        if (r.method == 'GET') return f.reads(r);
        posts.add(r);
        if (posts.length == 1) {
          return http.Response(
            jsonEncode({'code': 'TEMPORARY_FAILURE', 'detail': 'Retry later'}),
            status,
            headers: {
              'content-type': 'application/json',
              if (status == 429) 'retry-after': '120',
            },
          );
        }
        final response = acceptedMove(
          jsonDecode(r.body) as Map<String, dynamic>,
        );
        f.currentTrailer = response['trailer'] as Map<String, dynamic>;
        return f.mobile.response(response);
      };
      await f.worker.sync();
      final retry = (await f.worker.store.find(
        'user-A',
        'A',
        captured.clientOperationId,
      ))!;
      expect(retry.state, 'RETRYABLE_FAILURE');
      expect(retry.nextRetryAt, isNotNull);
      if (status == 429) {
        expect(
          retry.nextRetryAt!.difference(f.now).inSeconds,
          greaterThanOrEqualTo(120),
        );
      }
      await f.worker.sync();
      expect(posts, hasLength(1));
      f.now = f.now.add(const Duration(minutes: 10));
      await f.worker.sync();
      expect(posts, hasLength(2));
      expect(posts[0].body, posts[1].body);
      expect(
        posts[0].headers['Idempotency-Key'],
        posts[1].headers['Idempotency-Key'],
      );
      expect(
        (await f.worker.store.find(
          'user-A',
          'A',
          captured.clientOperationId,
        ))!.state,
        'ACKNOWLEDGED',
      );
    });
  }
  test(
    '401 refreshes Firebase once with the same request and identity',
    () async {
      await queue(dockId);
      final posts = <http.Request>[];
      f.custom = (r) async {
        if (r.method == 'GET') return f.reads(r);
        posts.add(r);
        if (posts.length == 1) {
          return f.mobile.response({
            'code': 'AUTHENTICATION_REQUIRED',
            'detail': 'Expired',
          }, 401);
        }
        final response = acceptedMove(
          jsonDecode(r.body) as Map<String, dynamic>,
        );
        f.currentTrailer = response['trailer'] as Map<String, dynamic>;
        return f.mobile.response(response);
      };
      await f.worker.sync();
      expect(posts, hasLength(2));
      expect(posts.first.body, posts.last.body);
      expect(
        posts.first.headers['Idempotency-Key'],
        posts.last.headers['Idempotency-Key'],
      );
      expect(f.mobile.auth.forceRefreshes, contains(true));
      expect(
        (await f.worker.store.list('user-A', 'A')).single.state,
        'ACKNOWLEDGED',
      );
    },
  );
  for (final code in [
    'LOCATION_MISMATCH',
    'STALE_VERSION',
    'IDEMPOTENCY_KEY_REUSED',
  ]) {
    test(
      '$code blocks dependent moves while independent Trailers continue',
      () async {
        await queue(dockId);
        await queue(dock2Id);
        await queue(dockId, asset: secondTrailerId);
        final originals = await f.worker.store.list('user-A', 'A');
        final posts = <http.Request>[];
        f.custom = (r) async {
          if (r.method == 'GET') return f.reads(r);
          posts.add(r);
          final body = jsonDecode(r.body) as Map<String, dynamic>;
          if (body['trailerId'] == trailerId) {
            return f.mobile.response({
              'code': code,
              'detail': 'Review state',
            }, 409);
          }
          return f.mobile.response(acceptedMove(body));
        };
        await f.worker.sync();
        final rows = await f.worker.store.list('user-A', 'A');
        expect(rows.map((r) => r.state), [
          'CONFLICT',
          'BLOCKED',
          'ACKNOWLEDGED',
        ]);
        expect(rows[0].payloadJson, originals[0].payloadJson);
        expect(rows[1].payloadJson, originals[1].payloadJson);
        expect(await YardReplay(f.worker.store).frozen(rows[1]), isNull);
        expect(posts, hasLength(2));
        await f.worker.sync();
        expect(posts, hasLength(2));
      },
    );
  }
  for (final status in [403, 404]) {
    test('$status is retained for review and never blindly replayed', () async {
      await queue(dockId);
      var posts = 0;
      f.custom = (r) async {
        if (r.method == 'GET') return f.reads(r);
        posts++;
        return f.mobile.response({
          'code': status == 403 ? 'CAPABILITY_DENIED' : 'RESOURCE_NOT_FOUND',
          'detail': 'Unavailable',
        }, status);
      };
      await f.worker.sync();
      expect(
        (await f.worker.store.list('user-A', 'A')).single.state,
        'CONFLICT',
      );
      await f.worker.sync();
      expect(posts, 1);
    });
  }
  test('two workers sharing SQLite serialize requests and a late failure cannot undo acknowledgment', () async {
    await queue(dockId);
    final original = (await f.worker.store.list('user-A', 'A')).single;
    final other = ReplayWorker(
      f.mobile.session,
      f.worker.store,
      MemoryJournal(),
      scheduleTimers: false,
    );
    var active = 0, maximum = 0, posts = 0;
    f.custom = (r) async {
      if (r.method == 'GET') return f.reads(r);
      posts++;
      active++;
      if (active > maximum) maximum = active;
      await Future<void>.delayed(const Duration(milliseconds: 25));
      active--;
      final response = acceptedMove(jsonDecode(r.body) as Map<String, dynamic>);
      f.currentTrailer = response['trailer'] as Map<String, dynamic>;
      return f.mobile.response(response);
    };
    try {
      await Future.wait([f.worker.sync(), other.sync()]);
      expect(maximum, 1);
      expect(posts, 1);
      await f.worker.store.result(
        original,
        CommandState.retryableFailure,
        code: 'RESULT_UNCERTAIN',
      );
      expect(
        (await f.worker.store.find(
          'user-A',
          'A',
          original.clientOperationId,
        ))!.state,
        'ACKNOWLEDGED',
      );
    } finally {
      await other.shutdown();
    }
  });
  for (final scope in ['User', 'Tenant']) {
    test(
      '$scope switch during HTTP fences late ACK and leaves original request recoverable',
      () async {
        await queue(dockId);
        final original = (await f.worker.store.list('user-A', 'A')).single;
        final held = Completer<http.Response>();
        final started = Completer<void>();
        f.custom = (r) async {
          if (r.method == 'GET') return f.reads(r);
          if (!started.isCompleted) started.complete();
          return held.future;
        };
        final syncing = f.worker.sync();
        await started.future;
        if (scope == 'User') {
          f.mobile.auth.uid = 'firebase-B';
        } else {
          f.mobile.memberships = [
            membership('B', capabilities: ['YARD_MOVE_EXECUTE']),
          ];
        }
        await f.mobile.session.refresh();
        final frozen = await YardReplay(f.worker.store).frozen(original);
        held.complete(
          f.mobile.response(
            acceptedMove(
              jsonDecode(frozen!.payloadJson) as Map<String, dynamic>,
            ),
          ),
        );
        await syncing;
        expect(
          (await f.worker.store.find(
            'user-A',
            'A',
            original.clientOperationId,
          ))!.state,
          isNot('ACKNOWLEDGED'),
        );
        expect(
          (await YardReplay(f.worker.store).frozen(original))!.payloadJson,
          frozen.payloadJson,
        );
        expect(
          await f.worker.store.list(
            scope == 'User' ? 'user-B' : 'user-A',
            scope == 'User' ? 'A' : 'B',
          ),
          isEmpty,
        );
      },
    );
  }
  test('malformed success does not release a dependent request or report server confirmation', () async {
    await queue(dockId);
    await queue(dock2Id);
    f.custom = (r) async {
      if (r.method == 'GET') return f.reads(r);
      return f.mobile.response({
        'trailer': yardTrailer(),
        'movementEvent': {'eventType': 'YARD_MOVED'},
      });
    };
    await f.worker.sync();
    final rows = await f.worker.store.list('user-A', 'A');
    expect(rows.first.state, 'RETRYABLE_FAILURE');
    expect(rows.last.state, 'QUEUED');
    expect(await f.mobile.db.select(f.mobile.db.commandResults).get(), isEmpty);
    expect(await YardReplay(f.worker.store).frozen(rows.last), isNull);
  });
  test('separate SQLite connections retain one logical move and terminal ACK when a live send is recovered', () async {
    await queue(dockId);
    final original = (await f.worker.store.list('user-A', 'A')).single;
    await YardReplay(f.worker.store).materialize(original);
    final secondDb = AppDatabase(NativeDatabase(file));
    await secondDb.initialize();
    final other = ReplayWorker(
      f.mobile.session,
      CommandStore(secondDb, clock: () => f.now),
      MemoryJournal(),
      scheduleTimers: false,
    );
    final started = Completer<void>(), release = Completer<void>();
    final attempts = <http.Request>[];
    Map<String, dynamic>? logicalResult;
    f.custom = (r) async {
      if (r.method == 'GET') return f.reads(r);
      attempts.add(r);
      logicalResult ??= acceptedMove(
        jsonDecode(r.body) as Map<String, dynamic>,
      );
      f.currentTrailer = logicalResult!['trailer'] as Map<String, dynamic>;
      if (attempts.length == 1) {
        started.complete();
        await release.future;
        throw const SocketException('Late lost ACK');
      }
      return f.mobile.response(logicalResult!);
    };
    final first = f.worker.sync();
    await started.future;
    try {
      await other.sync();
      expect(
        (await other.store.find(
          'user-A',
          'A',
          original.clientOperationId,
        ))!.state,
        'ACKNOWLEDGED',
      );
      release.complete();
      await first;
      expect(attempts, hasLength(2));
      expect(attempts.first.body, attempts.last.body);
      expect(
        attempts.first.headers['Idempotency-Key'],
        attempts.last.headers['Idempotency-Key'],
      );
      expect(
        (await f.worker.store.find(
          'user-A',
          'A',
          original.clientOperationId,
        ))!.state,
        'ACKNOWLEDGED',
      );
      expect(
        await f.mobile.db.select(f.mobile.db.commandResults).get(),
        hasLength(1),
      );
    } finally {
      if (!release.isCompleted) release.complete();
      await first;
      await other.shutdown();
      await secondDb.close();
    }
  });
}
