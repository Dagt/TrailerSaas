import 'dart:io';

import 'package:image_picker/image_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/shift/shift_view.dart';
import 'package:trailersaas_mobile/src/shift/inspection_store.dart';
import 'package:trailersaas_mobile/src/shift/shift_commands.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../media/media_http_fixture.dart';
import '../media/media_widgets_test.dart' show real, mount;
import '../media/media_storage_test.dart' show user, tenant;
import 'camera_capture_test.dart' show ControlledCamera;
import 'shift_protocol_test.dart' show tractor;

void main() {
  for (final offline in [false, true]) {
    testWidgets(
      'Shift home records honest On Duty online=$offline without needing a Trip',
      (tester) async {
        late MediaHttpFixture f;
        await real(tester, () async {
          f = MediaHttpFixture()..shiftWorkflows = true;
          await f.start();
          if (offline) {
            f.network.status = ConnectivityStatus.unavailable;
            await f.session.refresh();
          }
        });
        await mount(
          tester,
          MaterialApp(
            home: Scaffold(
              body: SingleChildScrollView(
                child: ShiftHomePanel(worker: f.worker),
              ),
            ),
          ),
        );
        await real(tester, () async {
          await f.worker.store.watch(user, tenant).first;
        });
        await tester.pump();
        expect(find.text('Estado del servidor: Off Duty'), findsOneWidget);
        await real(tester, () async {
          await tester.tap(find.text('Go On Duty'));
          await f.worker.store
              .watch(user, tenant)
              .firstWhere(
                (rows) => rows.any((r) => r.commandType == 'on-duty'),
              );
        });
        await tester.pump();
        // Explicitly wait for serialized synchronization outside fake widget time.
        await real(tester, f.worker.sync);
        await tester.pump();
        expect(f.serverShift, offline ? isNull : isNotNull);
        if (offline) {
          expect(
            find.textContaining('On Duty registrado localmente'),
            findsOneWidget,
          );
          expect(find.text('Estado del servidor: Off Duty'), findsOneWidget);
        }
        await mount(tester, const SizedBox.shrink());
        await real(tester, f.close);
      },
    );
  }
  testWidgets(
    'no eligible Tractor disables Pre-Trip with actionable assignment message',
    (tester) async {
      late MediaHttpFixture f;
      await real(tester, () async {
        f = MediaHttpFixture()..shiftWorkflows = true;
        await f.start();
        await InspectionStore(f.worker.shifts!).onDuty();
        await f.worker.sync();
        f.noTrips = true;
        await f.session.refresh();
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ShiftHomePanel(worker: f.worker),
            ),
          ),
        ),
      );
      await real(tester, () async {
        await f.worker.store.watch(user, tenant).first;
      });
      await tester.pump();
      expect(
        find.text('No tienes un Tractor asignado. Contacta a operaciones.'),
        findsOneWidget,
      );
      expect(
        tester
            .widget<OutlinedButton>(
              find.widgetWithText(
                OutlinedButton,
                'Pre-Trip · cuatro fotografías',
              ),
            )
            .onPressed,
        isNull,
      );
      expect(f.session.snapshot!.trips, isEmpty);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
  testWidgets(
    'four capture controls use camera adapter, keep files local, and hide draft on logout',
    (tester) async {
      late MediaHttpFixture f;
      late dynamic draft;
      final camera = ControlledCamera();
      await real(tester, () async {
        f = MediaHttpFixture()..shiftWorkflows = true;
        await f.start();
        final store = InspectionStore(f.worker.shifts!);
        await store.onDuty();
        await f.worker.sync();
        draft = await store.create(
          'PRE_TRIP',
          ShiftReference.server(f.serverShift!['id'] as String),
          tractor,
        );
        final file = File('${f.directory.path}/native.jpg');
        await file.writeAsBytes([255, 216, 255, 4]);
        camera.file = XFile(file.path);
      });
      await mount(
        tester,
        MaterialApp(
          home: InspectionScreen(
            worker: f.worker,
            draft: draft,
            camera: camera,
          ),
        ),
      );
      expect(find.text('Frontal'), findsOneWidget);
      expect(find.text('Trasera'), findsOneWidget);
      expect(find.text('Lado Driver'), findsOneWidget);
      expect(find.text('Lado Passenger'), findsOneWidget);
      await real(tester, () async {
        tester
            .widget<OutlinedButton>(
              find.widgetWithText(OutlinedButton, 'Capturar').first,
            )
            .onPressed!();
      });
      await tester.pump();
      await real(tester, () async {
        await f.db.customSelect('SELECT 1').get();
      });
      await tester.pump();
      // Read the persisted result through the actual SQLite completion boundary.
      await real(tester, () async {
        await f.db
            .select(f.db.cameraCaptures)
            .watch()
            .firstWhere((rows) => rows.any((r) => r.state == 'CAPTURED'));
      });
      await tester.pump();
      expect(f.serverInspections, isEmpty);
      expect(f.puts, 0);
      await real(tester, f.session.signOut);
      await tester.pump();
      expect(find.text('Frontal'), findsNothing);
      expect(find.textContaining('La sesión o Tenant cambió'), findsWidgets);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
}
