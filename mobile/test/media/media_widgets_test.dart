import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/media/media_sync_panel.dart';
import 'package:trailersaas_mobile/src/sync/sync_panel.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import 'media_http_fixture.dart';
import 'media_storage_test.dart' show user, tenant;

// Flutter's default widget HTTP override returns 400. Use native localhost HTTP
// only inside controlled fixture work, without changing production authorization.
class _FixtureHttp extends HttpOverrides {}

Future<T?> real<T>(WidgetTester tester, Future<T> Function() work) => tester
    .runAsync(() => HttpOverrides.runWithHttpOverrides(work, _FixtureHttp()));
Future<void> mount(WidgetTester tester, Widget widget) async {
  await real(tester, () => tester.pumpWidget(widget));
}

void main() {
  test('all accepted media states have distinct honest UI labels', () {
    expect(MediaSyncPanel.label('LOCAL_ONLY'), 'Saved on this device');
    for (final state in ['INIT_PENDING', 'UPLOAD_PENDING']) {
      expect(MediaSyncPanel.label(state), 'Pending upload');
    }
    expect(MediaSyncPanel.label('UPLOADING'), 'Uploading — not confirmed');
    for (final state in ['UPLOADED', 'FINALIZE_PENDING']) {
      expect(MediaSyncPanel.label(state), 'Waiting for server finalization');
    }
    expect(MediaSyncPanel.label('FINALIZED'), 'Finalized by server');
    expect(MediaSyncPanel.label('FAILED'), 'Requires attention');
  });
  for (final offline in [false, true]) {
    testWidgets('media visibility and real sync action offline=$offline', (
      tester,
    ) async {
      late MediaHttpFixture f;
      await real(tester, () async {
        f = MediaHttpFixture();
        await f.start();
        await f.capture();
        if (offline) {
          f.network.status = ConnectivityStatus.unavailable;
          f.network.events.add(f.network.status);
        }
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(child: SyncPanel(worker: f.worker)),
          ),
        ),
      );
      await real(tester, () async {
        await f.media.watch(user, tenant).first;
      });
      await tester.pump();
      expect(find.text('Media synchronization'), findsOneWidget);
      expect(find.text('Bill of lading'), findsOneWidget);
      expect(find.textContaining('Saved on this device'), findsWidgets);
      expect(find.textContaining('Finalized by server'), findsNothing);
      expect(find.textContaining('private-bearer'), findsNothing);
      expect(find.textContaining('private-signature'), findsNothing);
      final button = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, 'Sync now'),
      );
      expect(button.onPressed, offline ? isNull : isNotNull);
      if (!offline) {
        await real(tester, () async {
          await tester.tap(find.text('Sync now'));
          await f.worker.sync();
          await f.media.watch(user, tenant).first;
        });
        await tester.pump();
        expect(find.textContaining('Finalized by server'), findsWidgets);
        expect(f.puts, 1);
        expect(f.finalizeEffects, 1);
      }
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    });
  }
  testWidgets(
    'no Trips required; finalization review and logout remain namespace-safe',
    (tester) async {
      late MediaHttpFixture f;
      await real(tester, () async {
        f = MediaHttpFixture();
        await f.start();
        final id = await f.capture();
        await f.worker.sync();
        expect((await f.media.find(user, tenant, id))!.state, 'FINALIZED');
        f.noTrips = true;
        await f.session.refresh();
        expect(f.session.snapshot!.trips, isEmpty);
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(child: SyncPanel(worker: f.worker)),
          ),
        ),
      );
      await real(tester, () async {
        await f.media.watch(user, tenant).first;
      });
      await tester.pump();
      expect(find.textContaining('Finalized by server'), findsWidgets);
      expect(find.textContaining('Server finalized:'), findsOneWidget);
      await real(tester, () async {
        await f.session.signOut();
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(body: SyncPanel(worker: f.worker)),
        ),
      );
      await tester.pump();
      expect(find.text('Bill of lading'), findsNothing);
      expect(find.text('Media synchronization'), findsNothing);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
}
