import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';

import 'fixtures.dart';

void main() {
  late YardFixture f;
  Future<void> settle(WidgetTester tester) async {
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    for (var step = 0; step < 6; step++) {
      await tester.pump(const Duration(milliseconds: 100));
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
    }
  }

  Future<void> start(WidgetTester tester, {bool dual = false}) async {
    await tester.binding.setSurfaceSize(const Size(1000, 2200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    f = YardFixture();
    await tester.runAsync(() => f.open(dual: dual));
    addTearDown(() async {
      await tester.runAsync(f.close);
    });
    await tester.pumpWidget(
      MaterialApp(
        home: SessionView(
          session: f.mobile.session,
          worker: f.worker,
          yardScanner: f.scanner,
        ),
      ),
    );
    await settle(tester);
  }

  Future<void> finish(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(
      const Duration(milliseconds: 1),
    ); // Run Drift's zero-delay cancellation timers.
    await tester.runAsync(f.close);
  }

  Future<void> scanAndChoose(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('yard-scan')));
    await settle(tester);
    expect(find.text('Trailer T114'), findsOneWidget);
    expect(find.text('Recorded location: Yard A'), findsOneWidget);
    await tester.tap(find.byKey(const Key('yard-destination')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dock 17').last);
    await settle(tester);
  }

  testWidgets(
    'Yard-only opens real context with Site selector and native scanner port',
    (tester) async {
      await start(tester);
      expect(find.byKey(const Key('yard-mode')), findsOneWidget);
      expect(find.byKey(const Key('yard-site')), findsOneWidget);
      expect(find.text('Main Site'), findsOneWidget);
      expect(find.byKey(const Key('road-mode')), findsNothing);
      await scanAndChoose(tester);
      expect(find.text('Customer: Customer A'), findsOneWidget);
      expect(find.textContaining('LOADED'), findsOneWidget);
      expect(find.textContaining('Version: 12'), findsOneWidget);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      await finish(tester);
    },
  );
  testWidgets(
    'multi-role switches Road/Yard without changing operational authority',
    (tester) async {
      await start(tester, dual: true);
      expect(find.byKey(const Key('road-mode')), findsOneWidget);
      await tester.tap(find.text('Yard Mode'));
      await settle(tester);
      expect(find.byKey(const Key('yard-mode')), findsOneWidget);
      await scanAndChoose(tester);
      await tester.tap(find.text('Road Mode'));
      await settle(tester);
      expect(find.byKey(const Key('road-mode')), findsOneWidget);
      expect(f.scanner.cancellations, isPositive);
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      await finish(tester);
    },
  );
  testWidgets('Not Yet performs no POST and no durable operation', (
    tester,
  ) async {
    await start(tester);
    await scanAndChoose(tester);
    await tester.tap(find.byKey(const Key('yard-confirm-review')));
    await tester.pumpAndSettle();
    expect(find.text('Confirm Yard Move'), findsOneWidget);
    await tester.tap(find.text('Not Yet'));
    await settle(tester);
    expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    expect(
      await tester.runAsync(() => f.worker.store.list('user-A', 'A')),
      isEmpty,
    );
    await finish(tester);
  });
  testWidgets(
    'conscious confirmation persists first and distinguishes sending from server ACK',
    (tester) async {
      await start(tester);
      await scanAndChoose(tester);
      final held = Completer<void>();
      Map<String, dynamic>? result;
      String? requestBody;
      f.custom = (r) async {
        if (r.method == 'GET') return f.reads(r);
        requestBody = r.body;
        result = acceptedMove(jsonDecode(r.body) as Map<String, dynamic>);
        await held.future;
        f.currentTrailer = result!['trailer'] as Map<String, dynamic>;
        return f.mobile.response(result!);
      };
      await tester.tap(find.byKey(const Key('yard-confirm-review')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('yard-confirm')));
      await settle(tester);
      expect(requestBody, isNotNull);
      final queued = (await tester.runAsync(
        () => f.worker.store.list('user-A', 'A'),
      ))!.single;
      expect(queued.state, 'SENDING');
      expect(queued.payloadJson, requestBody);
      expect(find.text('Server confirmed Yard Move'), findsNothing);
      held.complete();
      await tester.runAsync(f.worker.sync);
      await settle(tester);
      expect(find.text('Server confirmed Yard Move'), findsOneWidget);
      expect(
        (await tester.runAsync(() => f.worker.store.list('user-A', 'A')))!
            .single
            .state,
        'ACKNOWLEDGED',
      );
      await tester.tap(find.byKey(const Key('yard-scan')));
      await settle(tester);
      expect(find.text('Recorded location: Dock 17'), findsOneWidget);
      await finish(tester);
    },
  );
  testWidgets(
    'LOCATION_MISMATCH retains command and shows authorized refreshed location',
    (tester) async {
      await start(tester);
      await scanAndChoose(tester);
      f.custom = (r) async {
        if (r.method == 'GET') return f.reads(r);
        f.currentTrailer = yardTrailer(current: dock2Id, version: 13);
        return f.mobile.response({
          'code': 'LOCATION_MISMATCH',
          'detail': 'Review the recorded location',
          'currentTrailer': f.currentTrailer,
        }, 409);
      };
      await tester.tap(find.byKey(const Key('yard-confirm-review')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('yard-confirm')));
      await settle(tester);
      await settle(tester);
      final conflict = (await tester.runAsync(
        () => f.worker.store.list('user-A', 'A'),
      ))!.single;
      expect(conflict.state, 'CONFLICT');
      expect(jsonDecode(conflict.payloadJson)['fromLocationId'], yardId);
      expect(find.textContaining('LOCATION_MISMATCH'), findsWidgets);
      expect(
        find.textContaining('Last downloaded recorded location: Dock 18'),
        findsOneWidget,
      );
      expect(f.mobile.requests.where((r) => r.method == 'POST'), hasLength(1));
      await finish(tester);
    },
  );
  testWidgets('account switch during review cannot confirm for a new User', (
    tester,
  ) async {
    await start(tester);
    await scanAndChoose(tester);
    await tester.tap(find.byKey(const Key('yard-confirm-review')));
    await tester.pumpAndSettle();
    f.mobile.auth.uid = 'firebase-B';
    final refresh = f.mobile.session.refresh();
    await settle(tester);
    await tester.runAsync(() => refresh);
    await settle(tester);
    expect(find.byKey(const Key('yard-confirm')), findsNothing);
    expect(find.text('Confirm Yard Move'), findsNothing);
    expect(
      await tester.runAsync(() => f.worker.store.list('user-A', 'A')),
      isEmpty,
    );
    expect(
      await tester.runAsync(() => f.worker.store.list('user-B', 'A')),
      isEmpty,
    );
    expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    await finish(tester);
  });
}
