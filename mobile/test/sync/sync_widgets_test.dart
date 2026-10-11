import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
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
      final button = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Sync now'),
      );
      expect(button.onPressed, isNull);
      expect(f.requests.where((r) => r.method == 'POST'), isEmpty);
      expect(find.text('Pending synchronization'), findsOneWidget);
      await close(tester);
    },
  );
}
