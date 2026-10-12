import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_view.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_store.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_replay.dart';
import 'package:trailersaas_mobile/src/road/driver_updates_view.dart';
import 'package:trailersaas_mobile/src/road/driver_updates_store.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import '../media/media_http_fixture.dart';
import '../media/media_widgets_test.dart' show real, mount;
import '../media/media_storage_test.dart' show user, tenant;
import '../shift/camera_capture_test.dart' show ControlledCamera;

void main() {
  testWidgets(
    'No Receiver form captures two private images and honestly saves offline intent',
    (tester) async {
      late MediaHttpFixture f;
      late RoadEvidenceStore store;
      late dynamic draft;
      late ControlledCamera camera;
      await real(tester, () async {
        f = MediaHttpFixture()..roadWorkflows = true;
        await f.start();
        camera = ControlledCamera();
        final file = File('${f.directory.path}/native.jpg');
        await file.writeAsBytes([255, 216, 255, 4], flush: true);
        camera.file = XFile(file.path);
        store = RoadEvidenceStore(
          f.worker.futureEvidence! as RoadEvidenceReplay,
          camera: camera,
        );
        draft = await store.create(
          'NO_RECEIVER_AVAILABLE',
          MediaHttpFixture.roadTrip,
          stop: MediaHttpFixture.roadStop,
        );
        f.network.status = ConnectivityStatus.unavailable;
        await f.session.refresh();
      });
      await mount(
        tester,
        MaterialApp(
          home: RoadEvidenceForm(store: store, draft: draft, worker: f.worker),
        ),
      );
      await tester.pump();
      expect(find.text('Fotografiar BOL'), findsOneWidget);
      expect(find.text('Fotografiar entrega'), findsOneWidget);
      expect(find.text('Comentario requerido'), findsOneWidget);
      for (final label in ['Fotografiar BOL', 'Fotografiar entrega']) {
        await tester.ensureVisible(find.text(label));
        await real(tester, () => tester.tap(find.text(label)));
        await real(tester, () async {
          await f.db
              .select(f.db.roadCaptures)
              .watch()
              .firstWhere(
                (rows) =>
                    rows.where((r) => r.state == 'CAPTURED').length ==
                    (label == 'Fotografiar BOL' ? 1 : 2),
              );
        });
        await tester.pump();
      }
      await tester.enterText(find.byType(TextField), 'No había receptor');
      await tester.ensureVisible(find.text('Guardar borrador'));
      await real(tester, () => tester.tap(find.text('Guardar borrador')));
      await real(tester, () async {
        await f.db
            .select(f.db.roadDrafts)
            .watch()
            .firstWhere(
              (rows) => rows.any(
                (r) =>
                    jsonDecode(r.inputsJson)['comment'] == 'No había receptor',
              ),
            );
      });
      await tester.pump();
      expect(
        find.textContaining('Pendiente de confirmación del servidor'),
        findsOneWidget,
      );
      expect(f.businessEffects, 0);
      await real(tester, () async {
        expect((await f.media.list(user, tenant)), hasLength(2));
        await f.session.signOut();
      });
      await tester.pump();
      expect(
        find.text('La sesión cambió. Vuelve al trabajo actual.'),
        findsOneWidget,
      );
      expect(find.text('Fotografiar BOL'), findsNothing);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
  testWidgets(
    'Road controls and retained drafts remain available without assigned Trips',
    (tester) async {
      late MediaHttpFixture f;
      await real(tester, () async {
        f = MediaHttpFixture()..roadWorkflows = true;
        await f.start();
        final store = RoadEvidenceStore(
          f.worker.futureEvidence! as RoadEvidenceReplay,
        );
        await store.create('PROBLEM', MediaHttpFixture.roadTrip);
        f.noTrips = true;
        await f.session.refresh();
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: RoadEvidencePanel(worker: f.worker),
            ),
          ),
        ),
      );
      await real(tester, () async {
        await f.db.select(f.db.roadDrafts).watch().first;
      });
      await tester.pump();
      expect(find.text('Problema operativo'), findsOneWidget);
      expect(find.text('Continuar'), findsOneWidget);
      expect(find.text('Entrega con BOL firmado'), findsNothing);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
  testWidgets(
    'Fuel and problem actions are real forms; POD only in appropriate context',
    (tester) async {
      late MediaHttpFixture f;
      await real(tester, () async {
        f = MediaHttpFixture()..roadWorkflows = true;
        await f.start();
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: RoadEvidencePanel(
                worker: f.worker,
                tripId: MediaHttpFixture.roadTrip,
                stopId: MediaHttpFixture.roadStop,
                deliveryAvailable: true,
              ),
            ),
          ),
        ),
      );
      await real(tester, () async {
        await f.db.select(f.db.roadDrafts).watch().first;
      });
      await tester.pump();
      expect(find.text('Entrega con BOL firmado'), findsOneWidget);
      expect(find.text('Entrega sin receptor disponible'), findsOneWidget);
      expect(find.text('Registrar combustible'), findsOneWidget);
      expect(find.text('Reportar problema'), findsOneWidget);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
  testWidgets(
    'targeted cancellation and reroute render without raw JSON or push',
    (tester) async {
      late MediaHttpFixture f;
      late DriverUpdatesStore store;
      await real(tester, () async {
        f = MediaHttpFixture()..roadWorkflows = true;
        await f.start();
        f.driverUpdates.add({
          'id': operationUuid(),
          'tripId': MediaHttpFixture.roadTrip,
          'updateType': 'TRIP_CANCELLATION_STARTED',
          'occurredAt': f.now.toIso8601String(),
          'payload': {
            'instructions': 'Regresa a base',
            'reason': 'Cancelación confirmada',
            'status': 'CANCELLING',
          },
        });
        store = DriverUpdatesStore(f.db, f.session);
        await store.refresh();
        f.noTrips = true;
        await f.session.refresh();
      });
      await mount(
        tester,
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: DriverUpdatesPanel(store: store),
            ),
          ),
        ),
      );
      await real(tester, () async {
        await store.watch(user, tenant).first;
        await store.refresh();
      });
      await tester.pump();
      expect(find.textContaining('Cancelación activa'), findsOneWidget);
      await tester.tap(find.textContaining('Cancelación activa'));
      await tester.pumpAndSettle();
      expect(find.text('Regresa a base'), findsOneWidget);
      expect(find.textContaining('{"'), findsNothing);
      expect(find.text('Actualizar instrucciones'), findsOneWidget);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
}
