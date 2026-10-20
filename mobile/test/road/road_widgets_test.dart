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
    'actual targeted substitution and removed assignment payloads explain the authorized next step',
    (tester) async {
      late MediaHttpFixture f;
      late DriverUpdatesStore store;
      await real(tester, () async {
        f = MediaHttpFixture()..roadWorkflows = true;
        await f.start();
        for (final payload in [
          {
            'decision': 'AUTHORIZE',
            'currentAssignment': true,
            'changedResource': 'TRAILER',
          },
          {
            'decision': 'REJECT',
            'currentAssignment': true,
            'changedResource': 'TRAILER',
          },
          {'currentAssignment': false, 'changedResource': 'DRIVER'},
        ]) {
          f.driverUpdates.add({
            'id': operationUuid(),
            'tripId': MediaHttpFixture.roadTrip,
            'updateType': payload['changedResource'] == 'TRAILER'
                ? 'TRAILER_EXPECTATION_CHANGED'
                : 'TRIP_ASSIGNMENT_CHANGED',
            'occurredAt': f.now.toIso8601String(),
            'payload': {
              'tripId': MediaHttpFixture.roadTrip,
              'tripVersion': 1,
              ...payload,
            },
          });
        }
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
      expect(find.text('Trailer substitution authorized'), findsOneWidget);
      expect(find.text('Substitution rejected'), findsOneWidget);
      await tester.tap(find.text('Trailer substitution authorized'));
      await tester.pumpAndSettle();
      expect(find.textContaining('is not physical coupling'), findsOneWidget);
      await tester.tap(find.text('Assignment changed / Trip updated'));
      await tester.pumpAndSettle();
      expect(find.text('Assignment removed'), findsOneWidget);
      expect(
        find.textContaining('does not authorize continuing the Trip'),
        findsOneWidget,
      );
      await mount(tester, const SizedBox.shrink());
      await real(tester, () async {
        await store.shutdown();
        await f.close();
      });
    },
  );
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
      expect(find.text('Capture BOL'), findsOneWidget);
      expect(find.text('Capture delivery photo'), findsOneWidget);
      expect(find.text('Required comment'), findsOneWidget);
      for (final label in ['Capture BOL', 'Capture delivery photo']) {
        await tester.ensureVisible(find.text(label));
        await real(tester, () => tester.tap(find.text(label)));
        await real(tester, () async {
          await f.db
              .select(f.db.roadCaptures)
              .watch()
              .firstWhere(
                (rows) =>
                    rows.where((r) => r.state == 'CAPTURED').length ==
                    (label == 'Capture BOL' ? 1 : 2),
              );
        });
        await tester.pump();
      }
      await tester.enterText(find.byType(TextField), 'No había receptor');
      await tester.ensureVisible(find.text('Save draft'));
      await real(tester, () => tester.tap(find.text('Save draft')));
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
        find.textContaining('Awaiting server confirmation'),
        findsOneWidget,
      );
      expect(f.businessEffects, 0);
      await real(tester, () async {
        expect((await f.media.list(user, tenant)), hasLength(2));
        await f.session.signOut();
      });
      await tester.pump();
      expect(
        find.text('The session changed. Return to current work.'),
        findsOneWidget,
      );
      expect(find.text('Capture BOL'), findsNothing);
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
      expect(find.text('Operational problem'), findsOneWidget);
      expect(find.text('Resume'), findsOneWidget);
      expect(find.text('Delivery with signed BOL'), findsNothing);
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
      expect(find.text('Delivery with signed BOL'), findsOneWidget);
      expect(find.text('Delivery with no receiver available'), findsOneWidget);
      expect(find.text('Record fuel'), findsOneWidget);
      expect(find.text('Report a problem'), findsOneWidget);
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
      expect(find.textContaining('Active cancellation'), findsOneWidget);
      await tester.tap(find.textContaining('Active cancellation'));
      await tester.pumpAndSettle();
      expect(find.text('Regresa a base'), findsOneWidget);
      expect(find.textContaining('{"'), findsNothing);
      expect(find.text('Refresh instructions'), findsOneWidget);
      await mount(tester, const SizedBox.shrink());
      await real(tester, f.close);
    },
  );
}
