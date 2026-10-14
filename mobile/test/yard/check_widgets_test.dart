import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
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

  Future<void> start(WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(1000, 2200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.runAsync(() async {
      dir = await Directory.systemTemp.createTemp('check-widget-');
      f = CheckFixture(dir, 1);
      await f.open();
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
