import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/offline_actions.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/sync/sync_panel.dart';

import '../road/online_execution_test.dart' show MemoryJournal;
import '../session/fixtures.dart';
import 'command_store_test.dart' show intent;

void main() {
  late MobileFixture f;
  late CommandStore store;
  late ReplayWorker worker;
  Future<void> setup(
    WidgetTester tester, {
    bool empty = false,
    bool offline = false,
  }) async {
    await tester.runAsync(() async {
      f = MobileFixture();
      f.memberships = [
        membership(
          'A',
          capabilities: [
            'TRIP_VIEW_ASSIGNED',
            'TRIP_EXECUTE_ASSIGNED',
            'ARRIVAL_CONFIRM',
          ],
        ),
      ];
      final trip = work('trip-A')..['status'] = 'IN_TRANSIT';
      (trip['stops'] as List).single['status'] = 'EN_ROUTE';
      (trip['stops'] as List).single['stopType'] = 'OTHER';
      f.trips = empty ? [] : [trip];
      await f.session.start();
      if (offline) f.session.state = SessionState.offline;
      store = CommandStore(f.db);
      worker = ReplayWorker(
        f.session,
        store,
        MemoryJournal(),
        scheduleTimers: false,
      );
    });
  }

  Future<void> settleReads(WidgetTester tester) async {
    await tester.runAsync(() async {
      await store.watch('user-A', 'A').first;
    });
    await tester.pump();
  }

  Future<void> close(WidgetTester tester) async {
    await tester.runAsync(() => tester.pumpWidget(const SizedBox.shrink()));
    await tester.runAsync(() async {
      await worker.shutdown();
      await f.close();
    });
  }

  testWidgets(
    'authenticated Road shows pending recovery even with zero assigned Trips',
    (tester) async {
      await setup(tester, empty: true);
      await tester.runAsync(() => store.enqueue('user-A', 'A', intent()));
      await tester.runAsync(
        () => tester.pumpWidget(
          MaterialApp(
            home: SessionView(
              session: f.session,
              worker: worker,
              journal: MemoryJournal(),
            ),
          ),
        ),
      );
      await settleReads(tester);
      expect(find.text('No assigned work downloaded.'), findsOneWidget);
      expect(find.text('Synchronization'), findsOneWidget);
      expect(find.text('Confirm arrival'), findsOneWidget);
      expect(find.text('Pending synchronization'), findsOneWidget);
      expect(find.text('Sync now'), findsOneWidget);
      expect(find.textContaining('Confirmed by server'), findsNothing);
      await close(tester);
    },
  );
  testWidgets(
    'sync UI distinguishes ACK, blocked and review without raw server metadata',
    (tester) async {
      await setup(tester);
      await tester.runAsync(() async {
        final a = intent(), b = intent(), c = intent();
        for (final i in [a, b, c]) {
          await store.enqueue('user-A', 'A', i);
        }
        await store.result(
          (await store.find('user-A', 'A', a.command.id))!,
          CommandState.acknowledged,
          status: 200,
        );
        await store.result(
          (await store.find('user-A', 'A', b.command.id))!,
          CommandState.conflict,
          status: 409,
          code: 'INVALID_STATE_TRANSITION',
          category: 'STATE',
        );
        await store.result(
          (await store.find('user-A', 'A', c.command.id))!,
          CommandState.blocked,
          code: 'DEPENDENCY_FAILED',
          category: 'DEPENDENCY',
        );
      });
      await tester.runAsync(
        () => tester.pumpWidget(
          MaterialApp(
            home: Scaffold(body: SyncPanel(worker: worker)),
          ),
        ),
      );
      await settleReads(tester);
      expect(find.text('Confirmed by server'), findsOneWidget);
      expect(find.textContaining('Requires review'), findsOneWidget);
      expect(
        find.textContaining('Blocked by another operation'),
        findsOneWidget,
      );
      expect(find.text('Review'), findsOneWidget);
      expect(find.textContaining('payloadJson'), findsNothing);
      expect(find.textContaining('private-bearer'), findsNothing);
      // A standalone queue must reflow and keep recovery reachable on a small
      // viewport as well as when naturally embedded in the workspace list.
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(320, 480);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(
        find.text('Review'),
        120,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.ensureVisible(find.text('Review'));
      await tester.pumpAndSettle();
      expect(
        tester.getRect(find.text('Review')).bottom,
        lessThanOrEqualTo(480),
      );
      expect(tester.takeException(), isNull);
      await close(tester);
    },
  );
  testWidgets(
    'offline recording confirms local intent only; next guided action keeps server state unchanged',
    (tester) async {
      await setup(tester, offline: true);
      final owner = f.session.snapshot!, trip = owner.trips.single;
      final original = jsonEncode(trip.data);
      await tester.runAsync(
        () => tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: OfflineActions(worker: worker, trip: trip),
            ),
          ),
        ),
      );
      await settleReads(tester);
      expect(find.text('Record arrivals'), findsOneWidget);
      await tester.runAsync(() => tester.tap(find.text('Record arrivals')));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('NOT confirmed by the server yet'),
        findsOneWidget,
      );
      await tester.runAsync(() async {
        await tester.tap(find.text('Save pending action'));
        await store.watch('user-A', 'A').firstWhere((rows) => rows.isNotEmpty);
      });
      await tester.pumpAndSettle();
      expect(find.text('Record service starts'), findsOneWidget);
      expect(find.textContaining('Saved on this device'), findsOneWidget);
      expect(jsonEncode(trip.data), original);
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      await close(tester);
    },
  );
  testWidgets(
    'another User/Tenant does not render pending commands from previous namespace',
    (tester) async {
      await setup(tester);
      await tester.runAsync(
        () => store.enqueue(
          'other-user',
          'other-tenant',
          intent(tenant: 'other-tenant'),
        ),
      );
      await tester.runAsync(
        () => tester.pumpWidget(
          MaterialApp(
            home: Scaffold(body: SyncPanel(worker: worker)),
          ),
        ),
      );
      await settleReads(tester);
      expect(find.text('No recorded operations.'), findsOneWidget);
      expect(find.text('Confirm arrival'), findsNothing);
      await close(tester);
    },
  );
  testWidgets(
    'offline sync button is disabled and cached state cannot trigger POST',
    (tester) async {
      await setup(tester, offline: true);
      await tester.runAsync(() => store.enqueue('user-A', 'A', intent()));
      await tester.runAsync(
        () => tester.pumpWidget(
          MaterialApp(
            home: Scaffold(body: SyncPanel(worker: worker)),
          ),
        ),
      );
      await settleReads(tester);
      final button = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, 'Sync now'),
      );
      expect(button.onPressed, isNull);
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      expect(find.text('Pending synchronization'), findsOneWidget);
      await close(tester);
    },
  );

  testWidgets(
    'foreground resume recovers an abandoned same-scope send without timers',
    (tester) async {
      await setup(tester);
      late Completer<void> entered;
      late Completer<http.Response> response;
      late Future<void> first;
      late String key;
      var posts = 0;
      await tester.runAsync(() async {
        // Create asynchronous gates in the real-I/O zone used by Drift/HTTP.
        entered = Completer<void>();
        response = Completer<http.Response>();
        final command = intent();
        key = command.command.id;
        await store.enqueue('user-A', 'A', command);
        f.handler = (request) async {
          if (request.method == 'POST') {
            posts++;
            if (posts == 1) {
              entered.complete();
              return response.future;
            }
            return f.response({'trip': work('trip-A')});
          }
          if (request.url.path.endsWith('/api/v1/me')) {
            return f.response({
              'userId': 'user-A',
              'memberships': f.memberships,
            });
          }
          if (request.url.path.endsWith('/driver/me')) {
            return f.response({'id': 'driver', 'name': 'Jane', 'active': true});
          }
          if (request.url.path.contains('/driver/me/trips/')) {
            return f.response({
              'trip': work('trip-A'),
              'currentCoupling': null,
            });
          }
          return f.response({'items': f.trips, 'nextCursor': null});
        };
        first = worker.sync();
        await entered.future;
      });
      await tester.runAsync(
        () => tester.pumpWidget(
          MaterialApp(
            home: Scaffold(body: SyncPanel(worker: worker)),
          ),
        ),
      );
      await tester.runAsync(() async {
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
        f.network.status = ConnectivityStatus.unavailable;
        f.network.events.add(ConnectivityStatus.unavailable);
        expect(f.session.state, SessionState.offline);
        response.completeError(
          const SocketException('response lost while paused'),
        );
        await first;
        expect((await store.list('user-A', 'A')).single.state, 'SENDING');
        // Only resume triggers replay: no network event, manual sync or timer.
        f.network.status = ConnectivityStatus.available;
        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
        await store
            .watch('user-A', 'A')
            .firstWhere((rows) => rows.single.state == 'ACKNOWLEDGED');
      });
      await settleReads(tester);
      expect(posts, 2);
      expect(
        f.requests
            .where((r) => r.method == 'POST')
            .map((r) => r.headers['Idempotency-Key'])
            .toSet(),
        {key},
      );
      await close(tester);
    },
  );
}
