import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';

import 'fixtures.dart';

void main() {
  late YardFixture f;
  void yardTestWidgets(String name, Future<void> Function(WidgetTester) body) {
    testWidgets(name, (t) async {
      try {
        await body(t);
      } finally {
        await t.pumpWidget(const SizedBox.shrink());
        await t.pump(const Duration(milliseconds: 1));
        await t.runAsync(f.close);
      }
    });
  }

  Future<void> settle(WidgetTester t) async {
    for (var n = 0; n < 6; n++) {
      await t.pump(const Duration(milliseconds: 100));
      await t.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
    }
  }

  Future<void> start(WidgetTester t, {bool authorized = true}) async {
    await t.binding.setSurfaceSize(const Size(1000, 2500));
    f = YardFixture();
    await t.runAsync(
      () => f.open(
        capabilities: authorized
            ? ['TRAILER_LOCATION_CORRECT', 'TRAILER_STATE_CORRECT']
            : [],
      ),
    );
    addTearDown(() async {
      await t.pumpWidget(const SizedBox.shrink());
      await t.pump();
      await t.runAsync(f.close);
      await t.binding.setSurfaceSize(null);
    });
    await t.pumpWidget(
      MaterialApp(
        home: SessionView(
          session: f.mobile.session,
          worker: f.worker,
          yardScanner: f.scanner,
        ),
      ),
    );
    await settle(t);
    await t.tap(find.byKey(const Key('yard-scan')));
    await settle(t);
    expect(find.text('Trailer T114'), findsOneWidget);
  }

  yardTestWidgets(
    'Yard authority alone allows reporting but never corrective or OOS controls',
    (t) async {
      await start(t, authorized: false);
      for (final name in [
        'yard-correct-location',
        'yard-correct-load',
        'yard-report-damage',
      ]) {
        expect(
          t.widget<OutlinedButton>(find.byKey(Key(name))).onPressed,
          isNull,
        );
      }
      expect(
        t
            .widget<OutlinedButton>(
              find.byKey(const Key('yard-report-discrepancy')),
            )
            .onPressed,
        isNotNull,
      );
      expect(
        find.textContaining('Report the discrepancy instead'),
        findsOneWidget,
      );
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    },
  );
  for (final name in [
    'yard-correct-location',
    'yard-correct-load',
    'yard-report-discrepancy',
  ]) {
    yardTestWidgets(
      '$name requires conscious confirmation and Not Yet never creates a command',
      (t) async {
        await start(t);
        await t.tap(find.byKey(Key(name)));
        await settle(t);
        await t.tap(find.text('Not Yet'));
        await settle(t);
        expect(
          await t.runAsync(() => f.worker.store.list('user-A', 'A')),
          isEmpty,
        );
        expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
      },
    );
  }
  for (final entry in {
    'yard-correct-location': 'LOCATION_CORRECTION',
    'yard-correct-load': 'LOAD_STATE_CHANGE',
    'yard-report-discrepancy': 'DISCREPANCY_REPORT',
  }.entries) {
    yardTestWidgets(
      '${entry.value} offline confirmation is durable honest and renders typed history',
      (t) async {
        await start(t);
        f.mobile.network.status = ConnectivityStatus.unavailable;
        f.mobile.network.events.add(ConnectivityStatus.unavailable);
        await settle(t);
        await t.tap(find.byKey(Key(entry.key)));
        await settle(t);
        await t.tap(
          find.byKey(
            Key(
              entry.value == 'LOAD_STATE_CHANGE'
                  ? 'yard-load-state'
                  : 'yard-observed-location',
            ),
          ),
        );
        await t.pumpAndSettle();
        await t.tap(
          find
              .text(entry.value == 'LOAD_STATE_CHANGE' ? 'EMPTY' : 'Dock 17')
              .last,
        );
        await t.pumpAndSettle();
        await t.enterText(
          find.byKey(const Key('yard-action-reason')),
          'Physically verified',
        );
        await t.tap(find.byKey(const Key('yard-action-confirm')));
        await settle(t);
        final commands = (await t.runAsync(
          () => f.worker.store.list('user-A', 'A'),
        ))!;
        expect(commands, hasLength(1));
        expect(commands.single.commandType, entry.value);
        expect(commands.single.state, 'QUEUED');
        expect(
          jsonDecode(commands.single.payloadJson)['reason'],
          'Physically verified',
        );
        expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
        expect(find.textContaining('Server confirmed'), findsNothing);
        expect(t.takeException(), isNull);
        expect(f.currentTrailer['version'], 12);
      },
    );
  }
  yardTestWidgets(
    'empty reason refuses confirmation without recording an operation',
    (t) async {
      await start(t);
      await t.tap(find.byKey(const Key('yard-correct-load')));
      await settle(t);
      await t.tap(find.byKey(const Key('yard-action-confirm')));
      await settle(t);
      expect(
        find.text('Choose the observed value and enter a reason.'),
        findsOneWidget,
      );
      expect(
        await t.runAsync(() => f.worker.store.list('user-A', 'A')),
        isEmpty,
      );
    },
  );
  yardTestWidgets(
    'session switch invalidates open review and prevents a late corrective command',
    (t) async {
      await start(t);
      await t.tap(find.byKey(const Key('yard-correct-load')));
      await settle(t);
      f.mobile.auth.uid = 'firebase-B';
      final refresh = f.mobile.session.refresh();
      await settle(t);
      await t.runAsync(() => refresh);
      await settle(t);
      expect(find.byKey(const Key('yard-action-confirm')), findsNothing);
      expect(
        await t.runAsync(() => f.worker.store.list('user-A', 'A')),
        isEmpty,
      );
      expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
    },
  );
}
