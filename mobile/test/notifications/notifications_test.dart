import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/notifications/messaging_port.dart';
import 'package:trailersaas_mobile/src/notifications/device_registration_store.dart';
import 'package:trailersaas_mobile/src/notifications/notifications_controller.dart';
import 'package:trailersaas_mobile/src/notifications/notifications_panel.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';

import '../session/fixtures.dart';

const tenant = '00000000-0000-4000-8000-000000000001';
const otherTenant = '00000000-0000-4000-8000-000000000002';
const alertId = '00000000-0000-4000-8000-000000000003';

class MemoryDevices implements DeviceRegistrationStore {
  final ids = <String, String>{};
  @override
  Future<String> ensure(String userId) async =>
      ids.putIfAbsent(userId, operationUuid);
}

class FakeMessaging implements MessagingPort {
  @override
  String? platform = 'ANDROID';
  PushPermission granted = PushPermission.authorized;
  String? currentToken = 'private-fcm-token';
  Object? tokenError;
  Future<PushPermission> Function(bool)? onPermission;
  final tokens = StreamController<String>.broadcast(sync: true);
  final messages = StreamController<PushHint>.broadcast(sync: true);
  int requests = 0, reads = 0, deleted = 0;
  @override
  Future<PushPermission> permission({bool request = false}) async {
    if (request) requests++;
    return onPermission == null ? granted : onPermission!(request);
  }

  @override
  Future<void> enabled(bool value) async {}
  @override
  Future<String?> token() async {
    reads++;
    if (tokenError != null) throw tokenError!;
    return currentToken;
  }

  @override
  Future<void> deleteToken() async {
    deleted++;
  }

  @override
  Stream<String> get tokenChanges => tokens.stream;
  @override
  Stream<PushHint> get hints => messages.stream;
  @override
  Future<PushHint?> initialHint() async => null;
  Future<void> close() async {
    await tokens.close();
    await messages.close();
  }
}

class Fixture {
  final f = MobileFixture(), port = FakeMessaging(), store = MemoryDevices();
  late NotificationsController controller;
  int? deviceFailure;
  Future<http.Response> Function(http.Request)? intercept;
  Future<void> prepare() async {
    f.memberships = [
      membership(tenant, capabilities: ['ALERT_VIEW']),
    ];
    f.handler = (r) async {
      if (intercept != null) return intercept!(r);
      return response(r);
    };
    await f.session.start();
    controller = NotificationsController(f.session, port, store);
  }

  http.Response response(http.Request r) {
    if (r.url.path.endsWith('/me')) {
      return f.response({
        'userId': f.auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
        'memberships': f.memberships,
      });
    }
    if (r.url.path.endsWith('/devices')) {
      if (deviceFailure != null) {
        return f.response({'code': 'DENIED', 'detail': 'safe'}, deviceFailure!);
      }
      final b = jsonDecode(r.body) as Map<String, dynamic>;
      return f.response({
        'id': b['deviceId'],
        'userId': f.auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
        'platform': b['platform'],
        'active': true,
        'pushRegistered': b['fcmToken'] != null,
      });
    }
    if (r.method == 'DELETE') return http.Response('', 204);
    return f.response({
      'id': alertId,
      'tenantId': tenant,
      'status': 'OPEN',
      'relatedResource': {'operationalNumber': 'TR-123'},
    });
  }

  List<http.Request> get posts => f.requests
      .where((r) => r.method == 'POST' && r.url.path.endsWith('/devices'))
      .toList();
  Future<void> start() async {
    await prepare();
    await controller.start();
  }

  Future<void> switchUser() async {
    f.auth.uid = 'firebase-B';
    f.auth.events.add('firebase-B');
    await pumpEventQueue(times: 30);
  }

  Future<void> close() async {
    await controller.shutdown();
    await port.close();
    await f.close();
  }
}

void main() {
  test('revoked Alert capability clears detail and fences in-flight same-Tenant read', () async {
    final t = Fixture();
    await t.start();
    addTearDown(t.close);
    t.port.messages.add(const PushHint(tenant, alertId));
    await t.controller.reviewHint();
    expect(t.controller.detail, isNotNull);
    final result = Completer<http.Response>();
    t.intercept = (r) async =>
        r.url.path.contains('/alerts/') ? result.future : t.response(r);
    final review = t.controller.reviewHint();
    await pumpEventQueue();
    t.f.memberships = [
      membership(tenant, capabilities: ['YARD_MOVE_EXECUTE']),
    ];
    await t.f.session.refresh();
    result.complete(
      t.f.response({
        'id': alertId,
        'tenantId': tenant,
        'status': 'OPEN',
        'relatedResource': {'operationalNumber': 'Revoked resource'},
      }),
    );
    await review;
    expect(t.controller.detail, isNull);
    expect(t.controller.hint, isNull);
  });
  test(
    'malformed registration receipt does not claim server confirmation',
    () async {
      final t = Fixture();
      await t.prepare();
      t.intercept = (r) async =>
          r.method == 'POST' ? t.f.response({'active': true}) : t.response(r);
      await t.controller.start();
      addTearDown(t.close);
      expect(t.controller.status, isNot(contains('confirmed by server')));
    },
  );
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'existing OS permission registers one stable owner ID without asking again',
    () async {
      final t = Fixture();
      await t.start();
      addTearDown(t.close);
      expect(t.port.requests, 0);
      expect(t.posts, hasLength(1));
      expect(t.controller.status, contains('confirmed by server'));
      expect(t.f.session.snapshot!.driver, isNull);
      expect(t.f.db.schemaVersion, 9);
    },
  );
  test(
    'denied permission records nullable token and keeps work available',
    () async {
      final t = Fixture();
      t.port.granted = PushPermission.denied;
      await t.start();
      addTearDown(t.close);
      expect(t.port.reads, 0);
      expect(jsonDecode(t.posts.single.body)['fcmToken'], isNull);
      expect(t.controller.status, contains('without push'));
      expect(t.f.session.state, SessionState.ready);
    },
  );
  test('permission is requested only from explicit enable action', () async {
    final t = Fixture();
    t.port.granted = PushPermission.unknown;
    await t.start();
    addTearDown(t.close);
    t.port.onPermission = (request) async =>
        request ? PushPermission.authorized : PushPermission.unknown;
    await t.controller.refresh(requestPermission: true);
    expect(t.port.requests, 1);
    expect(t.controller.permission, PushPermission.authorized);
  });
  test('unsupported platform never registers or asks permission', () async {
    final t = Fixture();
    t.port.platform = null;
    await t.start();
    addTearDown(t.close);
    expect(t.posts, isEmpty);
    expect(t.port.requests, 0);
  });
  test('native failures are safe and do not block Road/Yard session', () async {
    final t = Fixture();
    t.port.tokenError = StateError('private-fcm-token');
    await t.start();
    addTearDown(t.close);
    expect(t.controller.status, contains('unavailable'));
    expect(t.controller.status, isNot(contains('private-fcm-token')));
    expect(t.f.session.state, SessionState.ready);
    expect(t.posts, isEmpty);
  });
  test(
    'token refresh reads native state rather than trusting late stream token',
    () async {
      final t = Fixture();
      await t.start();
      addTearDown(t.close);
      final id = jsonDecode(t.posts.single.body)['deviceId'];
      t.port.currentToken = 'new-current-token';
      t.port.tokens.add('stale-stream-value');
      await pumpEventQueue();
      await t.controller.refresh();
      expect(jsonDecode(t.posts.last.body)['fcmToken'], 'new-current-token');
      expect(jsonDecode(t.posts.last.body)['deviceId'], id);
    },
  );
  for (final status in [401, 403, 429, 503]) {
    test(
      'registration $status is not accepted and manual recovery keeps identity',
      () async {
        final t = Fixture();
        t.deviceFailure = status;
        await t.start();
        addTearDown(t.close);
        final id = jsonDecode(t.posts.single.body)['deviceId'];
        expect(t.controller.status, isNot(contains('confirmed')));
        t.deviceFailure = null;
        await t.controller.refresh();
        expect(jsonDecode(t.posts.last.body)['deviceId'], id);
        expect(t.controller.status, contains('confirmed by server'));
      },
    );
  }
  test(
    'lost registration ACK retries same UUID, not another registration',
    () async {
      final t = Fixture();
      await t.prepare();
      var lost = true;
      t.intercept = (r) async {
        if (r.method == 'POST' && lost) {
          lost = false;
          throw http.ClientException('lost ACK');
        }
        return t.response(r);
      };
      await t.controller.start();
      addTearDown(t.close);
      final id = jsonDecode(t.posts.single.body)['deviceId'];
      await t.controller.refresh();
      expect(jsonDecode(t.posts.last.body)['deviceId'], id);
      expect(t.store.ids, hasLength(1));
    },
  );
  test('account change during permission dialog cannot persist old callback as new owner', () async {
    final t = Fixture();
    await t.prepare();
    final permission = Completer<PushPermission>();
    var first = true;
    t.port.onPermission = (_) {
      if (first) {
        first = false;
        return permission.future;
      }
      return Future.value(PushPermission.authorized);
    };
    final start = t.controller.start();
    await pumpEventQueue();
    await t.switchUser();
    permission.complete(PushPermission.authorized);
    await start;
    addTearDown(t.close);
    expect(t.store.ids.keys, contains('user-B'));
    expect(t.store.ids.keys, isNot(contains('user-A')));
    expect(
      t.posts.every(
        (r) => jsonDecode(r.body)['deviceId'] == t.store.ids['user-B'],
      ),
      isTrue,
    );
  });
  test(
    'late old HTTP receipt is ignored and new account gets a different ID',
    () async {
      final t = Fixture();
      await t.prepare();
      final ack = Completer<http.Response>();
      var first = true;
      t.intercept = (r) async {
        if (r.method == 'POST' && first) {
          first = false;
          return ack.future;
        }
        return t.response(r);
      };
      final start = t.controller.start();
      await pumpEventQueue();
      final a = jsonDecode(t.posts.first.body)['deviceId'];
      await t.switchUser();
      ack.complete(
        t.f.response({
          'id': a,
          'userId': 'user-A',
          'platform': 'ANDROID',
          'active': true,
          'pushRegistered': true,
        }),
      );
      await start;
      addTearDown(t.close);
      expect(t.store.ids['user-B'], isNot(a));
      expect(jsonDecode(t.posts.last.body)['deviceId'], t.store.ids['user-B']);
      expect(t.controller.status, contains('confirmed by server'));
    },
  );
  test('logout drains in-flight registration and deactivates before Firebase signout', () async {
    final t = Fixture();
    await t.prepare();
    final ack = Completer<http.Response>();
    var first = true;
    t.intercept = (r) async {
      if (r.method == 'POST' && first) {
        first = false;
        return ack.future;
      }
      if (r.method == 'DELETE') expect(t.f.auth.uid, 'firebase-A');
      return t.response(r);
    };
    final start = t.controller.start();
    await pumpEventQueue();
    final id = jsonDecode(t.posts.first.body)['deviceId'];
    final logout = t.f.session.signOut();
    await pumpEventQueue();
    ack.complete(
      t.f.response({
        'id': id,
        'userId': 'user-A',
        'platform': 'ANDROID',
        'active': true,
        'pushRegistered': true,
      }),
    );
    await start;
    await logout;
    await pumpEventQueue();
    addTearDown(t.close);
    expect(
      t.f.requests.where((r) => r.method == 'DELETE').single.url.path,
      endsWith('/devices/$id'),
    );
    expect(t.f.auth.uid, isNull);
    expect(t.port.deleted, greaterThan(0));
  });
  test('external account change during logout cannot sign out or delete token for new account', () async {
    final t = Fixture();
    await t.start();
    addTearDown(t.close);
    final pause = Completer<void>();
    t.f.session.beforeSignOut = () async {
      await pause.future;
    };
    final logout = t.f.session.signOut();
    await t.switchUser();
    pause.complete();
    await logout;
    expect(t.f.auth.uid, 'firebase-B');
    expect(t.f.session.user!.userId, 'user-B');
  });
  test('background fences late permission result; foreground safely registers afterward', () async {
    final t = Fixture();
    await t.prepare();
    final permission = Completer<PushPermission>();
    var first = true;
    t.port.onPermission = (_) {
      if (first) {
        first = false;
        return permission.future;
      }
      return Future.value(PushPermission.authorized);
    };
    final start = t.controller.start();
    await pumpEventQueue();
    t.controller.suspend();
    permission.complete(PushPermission.authorized);
    await start;
    expect(t.posts, isEmpty);
    await t.controller.resume();
    addTearDown(t.close);
    expect(t.posts, hasLength(1));
  });
  test(
    'reconnect refreshes current registration without another worker',
    () async {
      final t = Fixture();
      t.deviceFailure = 503;
      await t.start();
      addTearDown(t.close);
      t.deviceFailure = null;
      t.f.network.events.add(ConnectivityStatus.available);
      await pumpEventQueue();
      await t.controller.refresh();
      expect(t.controller.status, contains('confirmed by server'));
    },
  );
  test('notification is only a hint; reviewing performs authenticated GET with no operational POST', () async {
    final t = Fixture();
    await t.start();
    addTearDown(t.close);
    t.f.requests.clear();
    t.port.messages.add(const PushHint(tenant, alertId));
    expect(t.f.requests, isEmpty);
    await t.controller.reviewHint();
    expect(t.f.requests.single.method, 'GET');
    expect(t.controller.detail!['id'], alertId);
  });
  test(
    'foreign Tenant or denied capability hint cannot trigger read or mutation',
    () async {
      final t = Fixture();
      await t.start();
      addTearDown(t.close);
      t.f.requests.clear();
      t.port.messages.add(const PushHint(otherTenant, alertId));
      await t.controller.reviewHint();
      expect(t.f.requests, isEmpty);
      t.f.memberships = [
        membership(tenant, capabilities: ['YARD_MOVE_EXECUTE']),
      ];
      await t.f.session.refresh();
      t.f.requests.clear();
      t.port.messages.add(const PushHint(tenant, alertId));
      await t.controller.reviewHint();
      expect(t.controller.hint, isNull);
      expect(t.f.requests, isEmpty);
    },
  );
  test('late Alert detail after Tenant switch is not displayed', () async {
    final t = Fixture();
    await t.start();
    addTearDown(t.close);
    final result = Completer<http.Response>();
    t.port.messages.add(const PushHint(tenant, alertId));
    t.intercept = (r) async =>
        r.url.path.contains('/alerts/') ? result.future : t.response(r);
    final review = t.controller.reviewHint();
    await pumpEventQueue();
    t.f.memberships = [
      membership(otherTenant, capabilities: ['ALERT_VIEW']),
    ];
    await t.f.session.refresh();
    result.complete(
      t.f.response({
        'id': alertId,
        'tenantId': tenant,
        'status': 'OPEN',
        'relatedResource': {'operationalNumber': 'Private old Trailer'},
      }),
    );
    await review;
    expect(t.controller.detail, isNull);
  });
  test(
    'shutdown cancels listeners and late messages cannot navigate',
    () async {
      final t = Fixture();
      await t.start();
      await t.controller.shutdown();
      t.port.messages.add(const PushHint(tenant, alertId));
      t.port.tokens.add('late');
      await pumpEventQueue();
      expect(t.port.tokens.hasListener, isFalse);
      expect(t.port.messages.hasListener, isFalse);
      await t.port.close();
      await t.f.close();
    },
  );
  test(
    'private file survives reopen, isolates owners and stores no FCM token',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'device-registration-test-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final a = await FileDeviceRegistrationStore(() async => directory)
          .ensure('user-A');
      final b = await FileDeviceRegistrationStore(() async => directory)
          .ensure('user-B');
      expect(
        await FileDeviceRegistrationStore(() async => directory)
            .ensure('user-A'),
        a,
      );
      expect(a, isNot(b));
      for (final file in await directory.list().toList()) {
        expect(
          await (file as File).readAsString(),
          isNot(contains('fcmToken')),
        );
      }
    },
  );
  test('malformed or excessive push data never becomes a route', () {
    expect(
      PushHint.parse({'kind': 'ALERT', 'tenantId': tenant, 'alertId': alertId}),
      isNotNull,
    );
    for (final data in [
      {'kind': 'ALERT', 'tenantId': 'bad', 'alertId': alertId},
      {
        'kind': 'ALERT',
        'tenantId': tenant,
        'alertId': alertId,
        'url': 'https://untrusted.invalid',
      },
      {'kind': 'TRIP', 'tenantId': tenant, 'alertId': alertId},
    ]) {
      expect(PushHint.parse(data), isNull);
    }
  });
  testWidgets(
    'explicit accessible permission action and unmount during callback are safe',
    (tester) async {
      final t = (await tester.runAsync(() async {
        final t = Fixture();
        t.port.granted = PushPermission.denied;
        await t.start();
        return t;
      }))!;
      addTearDown(() => tester.runAsync(t.close));
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: NotificationsPanel(controller: t.controller)),
        ),
      );
      expect(find.text('Enable notifications'), findsOneWidget);
      expect(find.textContaining('without push'), findsOneWidget);
      await tester.runAsync(() async {
        final permission = Completer<PushPermission>();
        t.port.onPermission = (_) => permission.future;
        await tester.tap(find.text('Enable notifications'));
        await pumpEventQueue();
        expect(t.port.requests, 1);
        await tester.pumpWidget(const SizedBox());
        permission.complete(PushPermission.authorized);
        await t.controller.refresh();
      });
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    },
  );
}
