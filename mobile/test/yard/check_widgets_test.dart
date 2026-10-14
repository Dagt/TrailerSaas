import 'dart:io';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/contracts.dart';
import 'package:trailersaas_mobile/src/yard/check_view.dart';
import 'package:trailersaas_mobile/src/yard/yard_contracts.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late CheckFixture f;
  late Directory dir;
  late FakeYardScanner scanner;
  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
    }
  }

  Future<void> start(WidgetTester tester, {bool completed = false}) async {
    await tester.binding.setSurfaceSize(const Size(1000, 2200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.runAsync(() async {
      dir = await Directory.systemTemp.createTemp('check-widget-');
      f = CheckFixture(dir, 1);
      await f.open();
      if (completed) {
        f.summary = checkSummary(1, status: 'COMPLETED', version: 1);
        f.items.single['result'] = 'MISSING';
      }
      await f.snapshot();
    });
    scanner = FakeYardScanner();
    addTearDown(() async {
      await tester.runAsync(() async {
        await f.close();
        await dir.delete(recursive: true);
      });
    });
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: YardChecksPanel(
              session: f.mobile.session,
              worker: f.worker,
              site: YardSite.fromJson(yardSite()),
              scanner: scanner,
            ),
          ),
        ),
      ),
    );
    await settle(tester);
    await tester.tap(find.byKey(const ValueKey('check-$checkId')));
    await settle(tester);
  }

  Future<void> finish(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 1));
  }

  for (final action in ['cancel', 'reconcile']) {
    for (final confirm in [true, false]) {
      testWidgets(
        '$action reason dialog ${confirm ? 'Confirm' : 'Not Yet'} keeps controller alive through exit animation',
        (tester) async {
          await start(tester, completed: action == 'reconcile');
          if (action == 'reconcile') {
            f.custom = (r) async {
              if (r.method != 'POST') return f.handle(r);
              f.items.single['reconciliationId'] = inventoryId(90);
              return f.mobile.response({
                'reconciliationId': inventoryId(90),
                'yardCheckId': checkId,
                'itemId': inventoryId(1),
                'trailer': {
                  ...yardTrailer(version: 13),
                  'locationState': 'LOCATION_UNKNOWN',
                  'currentLocation': null,
                },
                'movementEvent': {
                  'id': inventoryId(91),
                  'eventType': 'LOCATION_MARKED_UNKNOWN',
                  'occurredAt': '2026-10-14T12:00:00Z',
                  'recordedAt': '2026-10-14T12:00:00Z',
                },
                'alertId': inventoryId(92),
              });
            };
          }
          await tester.tap(
            action == 'cancel'
                ? find.byKey(const Key('check-cancel'))
                : find.text('Mark Location Unknown'),
          );
          await settle(tester);
          await tester.enterText(
            find.byType(TextField),
            action == 'cancel' ? 'Stopped safely' : 'Verified missing Trailer',
          );
          await tester.tap(find.text(confirm ? 'Confirm' : 'Not Yet'));
          await tester.pump(const Duration(milliseconds: 10));
          // The route still owns the TextField; no command may escape before it exits.
          expect(tester.takeException(), isNull);
          expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
          await settle(tester);
          expect(tester.takeException(), isNull);
          final posts = f.mobile.requests
              .where((r) => r.method == 'POST')
              .toList();
          expect(posts, hasLength(confirm ? 1 : 0));
          if (confirm) {
            expect(
              jsonDecode(posts.single.body)['reason'],
              action == 'cancel'
                  ? 'Stopped safely'
                  : 'Verified missing Trailer',
            );
            expect(
              (await tester.runAsync(
                () => f.worker.store.list(checkUser, 'A'),
              ))!.single.state,
              'ACKNOWLEDGED',
            );
          }
          await finish(tester);
        },
      );
    }
  }
  for (final boundary in ['user', 'tenant', 'background', 'site', 'dispose']) {
    testWidgets(
      '$boundary change during reason-dialog Confirm exit removes old route and never submits late cancellation',
      (tester) async {
        await start(tester);
        final old = f.mobile.session.snapshot!;
        await tester.tap(find.byKey(const Key('check-cancel')));
        await settle(tester);
        await tester.enterText(find.byType(TextField), 'Stopped safely');
        await tester.tap(find.text('Confirm'));
        await tester.pump(const Duration(milliseconds: 10));
        expect(tester.takeException(), isNull);
        f.mobile.network.status = ConnectivityStatus.unavailable;
        if (boundary == 'user' || boundary == 'tenant') {
          f.mobile.session.snapshot = SessionSnapshot(
            boundary == 'user' ? 'firebase-B' : old.firebaseUid,
            boundary == 'user' ? inventoryId(80) : old.userId,
            Membership(
              boundary == 'tenant' ? 'B' : old.membership.tenantId,
              'Tenant',
              old.membership.capabilities,
            ),
            null,
            old.refreshedAt,
            [],
          );
          if (boundary == 'user') f.mobile.auth.uid = 'firebase-B';
          f.mobile.session.notifyListeners();
        } else if (boundary == 'background') {
          tester.binding.handleAppLifecycleStateChanged(
            AppLifecycleState.inactive,
          );
        } else {
          await tester.pumpWidget(
            MaterialApp(
              home: Scaffold(
                body: boundary == 'dispose'
                    ? const SizedBox.shrink()
                    : YardChecksPanel(
                        session: f.mobile.session,
                        worker: f.worker,
                        site: YardSite.fromJson({
                          ...yardSite(),
                          'id': inventoryId(81),
                          'locations': <Map<String, dynamic>>[],
                        }),
                        scanner: scanner,
                      ),
              ),
            ),
          );
        }
        await settle(tester);
        expect(tester.takeException(), isNull);
        expect(find.byType(AlertDialog), findsNothing);
        expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
        expect(f.summary['status'], 'IN_PROGRESS');
        expect(await tester.runAsync(() => f.cache.list(old)), hasLength(1));
        if (boundary == 'background') {
          tester.binding.handleAppLifecycleStateChanged(
            AppLifecycleState.resumed,
          );
        }
        await finish(tester);
      },
    );
  }
  testWidgets(
    'background removes an open reason route without navigating a disposed widget',
    (tester) async {
      await start(tester);
      await tester.tap(find.byKey(const Key('check-cancel')));
      await settle(tester);
      await tester.enterText(
        find.byType(TextField),
        'Partial evidence retained',
      );
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      // Paused disables Flutter frames; removal finishes on the first resumed frame.
      for (final state in [
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await settle(tester);
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      await finish(tester);
    },
  );
  testWidgets(
    'unmount during Not Yet reason-dialog exit disposes safely and retains history',
    (tester) async {
      await start(tester);
      await tester.tap(find.byKey(const Key('check-cancel')));
      await settle(tester);
      await tester.enterText(find.byType(TextField), 'No cancellation');
      await tester.tap(find.text('Not Yet'));
      await tester.pump(const Duration(milliseconds: 10));
      await tester.pumpWidget(
        const MaterialApp(home: Scaffold(body: SizedBox.shrink())),
      );
      await settle(tester);
      expect(tester.takeException(), isNull);
      expect(find.byType(AlertDialog), findsNothing);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      await finish(tester);
    },
  );

  testWidgets(
    'server conflict is distinct from local pending and never reports a scan ACK',
    (tester) async {
      await start(tester);
      f.custom = (r) async => r.method == 'POST'
          ? f.mobile.response({'code': 'RESOURCE_CONFLICT'}, 409)
          : f.handle(r);
      await tester.tap(find.byKey(const Key('check-observed-location')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yard A').last);
      await settle(tester);
      await tester.tap(find.byKey(const Key('check-scan')));
      await settle(tester);
      expect(find.textContaining('SERVER_CONFLICT'), findsOneWidget);
      expect(
        find.textContaining('conflicts requiring review 1'),
        findsOneWidget,
      );
      expect(find.textContaining('Server ACK'), findsNothing);
      await finish(tester);
    },
  );
  testWidgets(
    'known offline unexpected Trailer displays verified cached identity rather than unresolved QR',
    (tester) async {
      await start(tester);
      await tester.runAsync(
        () => f.mobile.cache.rememberIdentifiedTrailer(
          f.mobile.session.snapshot!,
          qrToken,
          {...yardTrailer(id: secondTrailerId), 'yardProjection': true},
          () => true,
        ),
      );
      f.mobile.network.status = ConnectivityStatus.unavailable;
      await tester.tap(find.byKey(const Key('check-observed-location')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yard A').last);
      await settle(tester);
      await tester.tap(find.byKey(const Key('check-scan')));
      await settle(tester);
      expect(
        find.textContaining(
          'T115 (cached identity) · LOCAL_PENDING · UNEXPECTED',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Unresolved Trailer QR'), findsNothing);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      await finish(tester);
    },
  );

  testWidgets(
    'real cached progress, native scan port and explicit server result remain usable for Yard-only',
    (tester) async {
      await start(tester);
      expect(find.textContaining('Expected 1'), findsOneWidget);
      await tester.tap(find.byKey(const Key('check-observed-location')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yard A').last);
      await settle(tester);
      await tester.tap(find.byKey(const Key('check-scan')));
      await settle(tester);
      expect(find.textContaining('scanned by server 1'), findsOneWidget);
      expect(find.textContaining('Server ACK'), findsOneWidget);
      expect(
        f.mobile.requests.where(
          (r) => r.method == 'POST' && !r.url.path.endsWith('/scans'),
        ),
        isEmpty,
      );
      await finish(tester);
    },
  );
  testWidgets(
    'Not Yet review does not complete or cancel or mutate any Trailer',
    (tester) async {
      await start(tester);
      await tester.tap(find.byKey(const Key('check-complete')));
      await settle(tester);
      expect(find.text('Complete Yard Check'), findsOneWidget);
      await tester.tap(find.text('Not Yet'));
      await settle(tester);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      expect(f.summary['status'], 'IN_PROGRESS');
      await finish(tester);
    },
  );
  testWidgets(
    'double tap Review opens one terminal modal and Not Yet retains inventory',
    (tester) async {
      await start(tester);
      await tester.tap(find.byKey(const Key('check-complete')));
      await tester.tap(find.byKey(const Key('check-complete')));
      await settle(tester);
      expect(find.text('Complete Yard Check'), findsOneWidget);
      await tester.tap(find.text('Not Yet'));
      await settle(tester);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      expect(f.summary['status'], 'IN_PROGRESS');
      await finish(tester);
    },
  );
  testWidgets(
    'unknown offline QR is visibly unresolved, with pending evidence and no fake ACK',
    (tester) async {
      await start(tester);
      f.mobile.network.status = ConnectivityStatus.unavailable;
      await tester.tap(find.byKey(const Key('check-observed-location')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yard A').last);
      await settle(tester);
      await tester.tap(find.byKey(const Key('check-scan')));
      await settle(tester);
      expect(find.textContaining('UNRESOLVED_QR'), findsOneWidget);
      expect(find.textContaining('pending synchronization 1'), findsOneWidget);
      expect(find.textContaining('Server ACK'), findsNothing);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      await finish(tester);
    },
  );
  testWidgets(
    'explicit completion displays MISSING only after actual typed server ACK',
    (tester) async {
      await start(tester);
      await tester.tap(find.byKey(const Key('check-complete')));
      await settle(tester);
      await tester.tap(find.text('Confirm'));
      await settle(tester);
      expect(f.summary['status'], 'COMPLETED');
      expect(find.textContaining('server COMPLETED'), findsOneWidget);
      expect(find.textContaining('T1 · MISSING'), findsOneWidget);
      expect(find.text('Mark Location Unknown'), findsOneWidget);
      await finish(tester);
    },
  );
}
