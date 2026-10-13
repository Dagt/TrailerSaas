import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';

import '../media/media_http_fixture.dart';
import '../media/media_storage_test.dart' show user, tenant;
import '../media/media_widgets_test.dart' show real, mount;
import '../shift/camera_capture_test.dart' show ControlledCamera;
import 'fixtures.dart';

void main() {
  late MediaHttpFixture f;
  late ControlledCamera camera;
  Future<void> settle(WidgetTester t) async {
    for (var n = 0; n < 8; n++) {
      await t.pump(const Duration(milliseconds: 100));
      await t.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 60)),
      );
    }
  }

  Future<void> start(WidgetTester t) async {
    await t.binding.setSurfaceSize(const Size(1000, 2500));
    f = MediaHttpFixture()..yardWorkflows = true;
    camera = ControlledCamera();
    await real(t, () async {
      await f.start(fileDatabase: true);
      final file = File('${f.directory.path}/photo.jpg');
      await file.writeAsBytes([255, 216, 255, 4, 5], flush: true);
      camera.file = XFile(file.path);
    });
    await mount(
      t,
      MaterialApp(
        home: SessionView(
          session: f.session,
          worker: f.worker,
          yardScanner: FakeYardScanner(),
          yardCamera: camera,
        ),
      ),
    );
    await settle(t);
    await t.tap(find.byKey(const Key('yard-scan')));
    await settle(t);
    expect(find.text('Trailer T114'), findsOneWidget);
  }

  Future<void> finish(WidgetTester t) async {
    await t.pumpWidget(const SizedBox.shrink());
    await t.pump(const Duration(milliseconds: 1));
    await t.runAsync(f.close);
    await t.binding.setSurfaceSize(null);
  }

  testWidgets(
    'damage photo and Not Yet persist draft without OOS; resumed explicit offline confirm is honest',
    (t) async {
      await start(t);
      try {
        f.network.status = ConnectivityStatus.unavailable;
        f.network.events.add(ConnectivityStatus.unavailable);
        await settle(t);
        await t.tap(find.byKey(const Key('yard-report-damage')));
        await settle(t);
        await t.enterText(
          find.byKey(const Key('yard-oos-comment')),
          'Frame crack visibly confirmed',
        );
        await real(t, () => t.tap(find.byKey(const Key('yard-damage-photo'))));
        await settle(t);
        expect(find.text('Retained damage photographs: 1'), findsOneWidget);
        expect(
          await t.runAsync(() => f.media.list(user, tenant)),
          hasLength(1),
        );
        expect(
          await t.runAsync(() => f.worker.store.list(user, tenant)),
          isEmpty,
        );
        expect(f.businessEffects, 0);
        await t.tap(find.text('Not Yet'));
        await settle(t);
        await t.tap(find.byKey(const Key('yard-report-damage')));
        await settle(t);
        expect(
          t
              .widget<TextField>(find.byKey(const Key('yard-oos-comment')))
              .controller!
              .text,
          'Frame crack visibly confirmed',
        );
        expect(find.text('Retained damage photographs: 1'), findsOneWidget);
        await t.tap(find.byKey(const Key('yard-oos-confirm')));
        await settle(t);
        final commands = (await t.runAsync(
          () => f.worker.store.list(user, tenant),
        ))!;
        expect(commands, hasLength(1));
        expect(commands.single.commandType, 'OOS');
        expect(commands.single.state, 'QUEUED');
        expect(
          find.textContaining(
            'waiting for finalized evidence and server confirmation',
          ),
          findsOneWidget,
        );
        expect(find.text('Server confirmed Out of Service'), findsNothing);
        expect(f.posts, isEmpty);
        expect(f.yardState['serviceabilityState'], 'IN_SERVICE');
        expect(t.takeException(), isNull);
      } finally {
        await finish(t);
      }
    },
  );
  testWidgets(
    'OOS UI rejects missing photo or blank comment without creating command',
    (t) async {
      await start(t);
      try {
        await t.tap(find.byKey(const Key('yard-report-damage')));
        await settle(t);
        await t.tap(find.byKey(const Key('yard-oos-confirm')));
        await settle(t);
        expect(
          await t.runAsync(() => f.worker.store.list(user, tenant)),
          isEmpty,
        );
        expect(f.businessEffects, 0);
        expect(find.byKey(const Key('yard-oos-comment')), findsOneWidget);
        await t.tap(find.text('Not Yet'));
        await settle(t);
      } finally {
        await finish(t);
      }
    },
  );
}
