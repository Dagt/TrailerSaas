import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';
import 'package:trailersaas_mobile/src/road/road_trip_screen.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_view.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_store.dart';
import 'package:trailersaas_mobile/src/road/road_evidence_replay.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/sync/sync_panel.dart';
import 'package:trailersaas_mobile/src/shift/shift_view.dart';
import 'package:trailersaas_mobile/src/shift/inspection_store.dart';
import 'package:trailersaas_mobile/src/shift/shift_commands.dart';
import 'package:trailersaas_mobile/src/media/media_sync_panel.dart';
import 'package:trailersaas_mobile/src/ui/fleet_theme.dart';
import 'package:trailersaas_mobile/src/ui/fleet_widgets.dart';
import 'package:trailersaas_mobile/src/yard/yard_view.dart';
import 'package:trailersaas_mobile/src/yard/check_view.dart';
import 'package:trailersaas_mobile/src/yard/yard_contracts.dart';

import '../session/fixtures.dart';
import '../road/online_execution_test.dart' show MemoryJournal;
import '../sync/command_store_test.dart' show intent;
import '../yard/fixtures.dart';
import '../yard/check_fixtures.dart';
import '../media/media_http_fixture.dart';
import '../media/media_widgets_test.dart' show real;

// This is a Flutter widget-test capture tool, not an emulator, a production
// entry, or real authentication. Synthetic reads and local state are explicit.
const captureKey = ValueKey('fleet-widget-capture');
final baseline = Platform.environment['FLEET_BASELINE'] == 'true';
Future<void> settle(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 35)),
    );
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> render(WidgetTester tester, Widget screen, double scale) async {
  final baseTheme = baseline ? ThemeData(useMaterial3: true) : fleetTheme();
  ButtonStyle? captureButton(ButtonStyle? style) => style?.copyWith(
    textStyle: WidgetStateProperty.resolveWith(
      (states) => style.textStyle
          ?.resolve(states)
          ?.copyWith(fontFamily: 'FleetCaptureSans'),
    ),
  );
  final theme = Platform.environment['FLEET_FONT_DIR'] == null
      ? baseTheme
      : baseTheme.copyWith(
          textTheme: baseTheme.textTheme.apply(fontFamily: 'FleetCaptureSans'),
          primaryTextTheme: baseTheme.primaryTextTheme.apply(
            fontFamily: 'FleetCaptureSans',
          ),
          appBarTheme: baseTheme.appBarTheme.copyWith(
            titleTextStyle:
                (baseTheme.appBarTheme.titleTextStyle ??
                        baseTheme.textTheme.titleLarge!)
                    .copyWith(fontFamily: 'FleetCaptureSans'),
          ),
          filledButtonTheme: FilledButtonThemeData(
            style: captureButton(baseTheme.filledButtonTheme.style),
          ),
          outlinedButtonTheme: OutlinedButtonThemeData(
            style: captureButton(baseTheme.outlinedButtonTheme.style),
          ),
          textButtonTheme: TextButtonThemeData(
            style: captureButton(baseTheme.textButtonTheme.style),
          ),
        );
  await tester.pumpWidget(
    RepaintBoundary(
      key: captureKey,
      child: MaterialApp(
        theme: theme,
        debugShowCheckedModeBanner: false,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(scale)),
          child: Column(
            children: [
              SizedBox(
                width: double.infinity,
                child: Material(
                  color: const Color(0xfffffbeb),
                  child: SafeArea(
                    bottom: false,
                    child: Padding(
                      padding: const EdgeInsets.all(8),
                      child: Text(
                        'ISOLATED WIDGET TEST · Synthetic data · No live Backend or Firebase',
                        style: const TextStyle(
                          fontSize: 12,
                          fontFamily: 'FleetCaptureSans',
                          color: Color(0xff92400e),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              Expanded(child: child!),
            ],
          ),
        ),
        home: screen,
      ),
    ),
  );
  await settle(tester);
}

Future<void> capture(WidgetTester tester, String name, double scale) async {
  final errors = <String>[];
  Object? error;
  while ((error = tester.takeException()) != null) {
    errors.add(error.toString());
  }
  if (!baseline) {
    expect(
      errors,
      isEmpty,
      reason: 'No overflow or rendering exception: $name',
    );
  }
  final output = Platform.environment['FLEET_CAPTURE_DIR'];
  if (output == null) return;
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(captureKey),
  );
  final bytes = (await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 1);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    return data!.buffer.asUint8List();
  }))!;
  final size = tester.view.physicalSize;
  final filename =
      '$name-${size.width.toInt()}x${size.height.toInt()}-scale$scale.png';
  await tester.runAsync(() async {
    await Directory(output).create(recursive: true);
    await File('$output/$filename').writeAsBytes(bytes);
    await File('$output/manifest.jsonl').writeAsString(
      '${jsonEncode({
        'file': filename,
        'sha256': sha256.convert(bytes).toString(),
        'sourceRevision': Platform.environment['FLEET_SOURCE_SHA'] ?? 'working-tree',
        'viewport': {'width': size.width, 'height': size.height, 'pixelRatio': 1},
        'textScale': scale,
        'scenario': name,
        'environment': 'Flutter widget-test engine on Linux; NOT emulator/device',
        'data': 'Isolated synthetic test fixtures / memory or temporary SQLite; no live authentication or Backend',
        'baseline': baseline,
        'renderErrors': errors,
      })}\n',
      mode: FileMode.append,
    );
  });
}

void main() {
  setUpAll(() async {
    // SDK fonts are used only by the capture tool; no product dependency/asset.
    final fontRoot = Platform.environment['FLEET_FONT_DIR'];
    if (fontRoot != null) {
      for (final family in ['Roboto', 'MaterialIcons']) {
        final loader = FontLoader(
          family == 'Roboto' ? 'FleetCaptureSans' : family,
        );
        for (final file
            in family == 'Roboto'
                ? ['Roboto-Regular.ttf', 'Roboto-Medium.ttf', 'Roboto-Bold.ttf']
                : ['MaterialIcons-Regular.otf']) {
          loader.addFont(
            Future.value(
              ByteData.sublistView(await File('$fontRoot/$file').readAsBytes()),
            ),
          );
        }
        await loader.load();
      }
    }
  });
  for (final layout in [
    (360.0, 800.0, 1.0),
    (430.0, 932.0, 1.0),
    (360.0, 800.0, 1.8),
    (844.0, 390.0, 1.0),
  ]) {
    final (width, height, scale) = layout;
    for (final scenario in [
      'sign-in',
      if (!baseline) 'configuration-error',
      'road-home',
      'road-trip',
      'qr-mismatch',
      'road-offline',
      'sync-states',
      'yard-home',
      'yard-move-review',
      'yard-qr-error',
      'yard-check',
      'pre-trip',
      'post-trip',
      'evidence',
      'media-pending',
      'yard-damage',
    ]) {
      testWidgets(
        '$scenario renders at $width x $height scale $scale without overflow or live writes',
        (tester) async {
          tester.view.devicePixelRatio = 1;
          tester.view.physicalSize = Size(width, height);
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          MobileFixture? mobile;
          YardFixture? yard;
          CheckFixture? checks;
          MediaHttpFixture? media;
          ReplayWorker? worker;
          Directory? directory;
          try {
            if (scenario.startsWith('yard-') &&
                scenario != 'yard-check' &&
                scenario != 'yard-damage') {
              yard = YardFixture();
              await tester.runAsync(() => yard!.open());
              if (scenario == 'yard-qr-error') {
                yard.scanner.value = 'invalid-test-qr';
              }
              // Fixture reads only; any accidental command is rejected, not ACKed.
              yard.custom = (request) async {
                if (request.method != 'GET') {
                  return yard!.mobile.response({
                    'code': 'DEMO_WRITES_DISABLED',
                  }, 403);
                }
                if (scenario == 'yard-home' &&
                    request.url.path.endsWith('/yard/sites')) {
                  return yard!.mobile.response({
                    'items': [
                      {
                        ...yardSite(),
                        'name': 'DEMO · North freight distribution center — receiving Yard and customer loading docks',
                      },
                    ],
                    'nextCursor': null,
                  });
                }
                return yard!.reads(request);
              };
              await render(
                tester,
                Scaffold(
                  appBar: AppBar(title: const Text('Yard workspace')),
                  body: SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: YardHomePanel(
                      session: yard.mobile.session,
                      worker: yard.worker,
                      scanner: yard.scanner,
                    ),
                  ),
                ),
                scale,
              );
              if (scenario == 'yard-move-review' ||
                  scenario == 'yard-qr-error') {
                await tester.ensureVisible(find.byKey(const Key('yard-scan')));
                await tester.tap(find.byKey(const Key('yard-scan')));
                await settle(tester);
                if (scenario == 'yard-move-review') {
                  await tester.ensureVisible(
                    find.byKey(const Key('yard-destination')),
                  );
                  await tester.tap(find.byKey(const Key('yard-destination')));
                  await tester.pumpAndSettle();
                  await tester.tap(find.text('Dock 17').last);
                  await tester.pumpAndSettle();
                  await tester.ensureVisible(
                    find.byKey(const Key('yard-confirm-review')),
                  );
                  await tester.tap(
                    find.byKey(const Key('yard-confirm-review')),
                  );
                  await tester.pumpAndSettle();
                  expect(find.text('Confirm Yard Move'), findsOneWidget);
                }
              }
            } else if (scenario == 'yard-check') {
              await tester.runAsync(() async {
                directory = await Directory.systemTemp.createTemp(
                  'fleet-check-',
                );
                checks = CheckFixture(directory!, 3);
                await checks!.open();
                await checks!.snapshot();
              });
              checks!.custom = (request) => request.method == 'GET'
                  ? checks!.handle(request)
                  : Future.value(
                      checks!.mobile.response({
                        'code': 'DEMO_WRITES_DISABLED',
                      }, 403),
                    );
              await render(
                tester,
                Scaffold(
                  appBar: AppBar(title: const Text('Yard inventory')),
                  body: SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: YardChecksPanel(
                      session: checks!.mobile.session,
                      worker: checks!.worker,
                      site: YardSite.fromJson(yardSite()),
                      scanner: FakeYardScanner(),
                    ),
                  ),
                ),
                scale,
              );
              await tester.ensureVisible(
                find.byKey(const ValueKey('check-$checkId')),
              );
              await tester.tap(find.byKey(const ValueKey('check-$checkId')));
              await settle(tester);
            } else if ([
              'pre-trip',
              'post-trip',
              'evidence',
              'media-pending',
              'yard-damage',
            ].contains(scenario)) {
              late Widget screen;
              await real(tester, () async {
                media = MediaHttpFixture()
                  ..shiftWorkflows = true
                  ..roadWorkflows = true;
                if (scenario == 'yard-damage') {
                  media!
                    ..shiftWorkflows = false
                    ..roadWorkflows = false
                    ..yardWorkflows = true;
                }
                media!.serverShift = {
                  'id': '77777777-7777-4777-8777-777777777777',
                  'state': 'ON_DUTY',
                  'onDutyAt': '2026-10-19T12:00:00Z',
                  'offDutyAt': null,
                };
                media!.serverInspections.add({
                  'id': '88888888-8888-4888-8888-888888888888',
                  'inspectionType': 'PRE_TRIP',
                  'tractorId': '44444444-4444-4444-8444-444444444444',
                  'tripId': null,
                });
                await media!.start();
                if (scenario == 'yard-damage') {
                  screen = Scaffold(
                    appBar: AppBar(title: const Text('Yard workspace')),
                    body: SingleChildScrollView(
                      padding: const EdgeInsets.all(16),
                      child: YardHomePanel(
                        session: media!.session,
                        worker: media!.worker,
                        scanner: FakeYardScanner(),
                      ),
                    ),
                  );
                } else if (scenario == 'evidence') {
                  final store = RoadEvidenceStore(
                    media!.worker.futureEvidence! as RoadEvidenceReplay,
                  );
                  final draft = await store.create(
                    'NO_RECEIVER_AVAILABLE',
                    MediaHttpFixture.roadTrip,
                    stop: MediaHttpFixture.roadStop,
                  );
                  screen = RoadEvidenceForm(
                    store: store,
                    draft: draft,
                    worker: media!.worker,
                  );
                } else if (scenario == 'media-pending') {
                  await media!.media.capture(
                    media!.session,
                    'BOL',
                    'image/jpeg',
                    Stream.value([1, 2, 3, 4]),
                    tripId: MediaHttpFixture.roadTrip,
                  );
                  screen = Scaffold(
                    appBar: AppBar(title: const Text('Saved evidence')),
                    body: SingleChildScrollView(
                      padding: const EdgeInsets.all(16),
                      child: MediaSyncPanel(worker: media!.worker),
                    ),
                  );
                } else {
                  final store = InspectionStore(media!.worker.shifts!);
                  final draft = await store.create(
                    scenario == 'pre-trip' ? 'PRE_TRIP' : 'POST_TRIP',
                    ShiftReference.server(media!.serverShift!['id'] as String),
                    '44444444-4444-4444-8444-444444444444',
                  );
                  screen = InspectionScreen(
                    worker: media!.worker,
                    draft: draft,
                  );
                }
              });
              await render(tester, screen, scale);
              if (scenario == 'yard-damage') {
                await tester.ensureVisible(find.byKey(const Key('yard-scan')));
                await tester.tap(find.byKey(const Key('yard-scan')));
                await settle(tester);
                await tester.ensureVisible(
                  find.byKey(const Key('yard-report-damage')),
                );
                await tester.tap(find.byKey(const Key('yard-report-damage')));
                await settle(tester);
                expect(find.text('Confirm Mark OOS'), findsOneWidget);
              }
            } else {
              await tester.runAsync(() async {
                mobile = MobileFixture(
                  uid: scenario == 'sign-in' ? null : 'firebase-A',
                );
                mobile!.memberships = [
                  membership(
                    'A',
                    capabilities: [
                      'TRIP_VIEW_ASSIGNED',
                      'TRIP_EXECUTE_ASSIGNED',
                      'ARRIVAL_CONFIRM',
                      'TRAILER_HOOK',
                    ],
                  ),
                ];
                final trip = work('trip-A')..['status'] = 'IN_TRANSIT';
                if (scenario == 'qr-mismatch') trip['status'] = 'ASSIGNED';
                (trip['stops'] as List).first['status'] = 'EN_ROUTE';
                mobile!.trips = [trip];
                await mobile!.session.start();
                mobile!.handler = (request) async =>
                    request.url.path.contains('/trailer-qr/')
                    ? mobile!.response({
                        'trailerId': 'different-trailer',
                        'operationalNumber': 'T-WRONG',
                        'version': 2,
                      })
                    : request.method == 'GET'
                    ? mobile!.response({
                        'trip': trip,
                        'currentCoupling': null,
                        'tractorOperationalNumber': 'T-12',
                        'departurePrerequisitesSatisfied': false,
                        'arrivalAssistance': null,
                      })
                    : mobile!.response({'code': 'DEMO_WRITES_DISABLED'}, 403);
                if (scenario == 'sync-states' || scenario == 'road-offline') {
                  worker = ReplayWorker(
                    mobile!.session,
                    CommandStore(mobile!.db),
                    MemoryJournal(),
                    scheduleTimers: false,
                  );
                  mobile!.session.state = SessionState.offline;
                }
                if (scenario == 'sync-states') {
                  for (final state in CommandState.values) {
                    final operation = intent();
                    await worker!.store.enqueue('user-A', 'A', operation);
                    final row = (await worker!.store.list('user-A', 'A'))
                        .firstWhere(
                          (r) => r.clientOperationId == operation.command.id,
                        );
                    await worker!.store.result(
                      row,
                      state,
                      code: state == CommandState.conflict
                          ? 'STALE_VERSION'
                          : null,
                    );
                  }
                }
              });
              final screen = scenario == 'configuration-error'
                  ? const FleetAccessState(
                      title: 'Workspace unavailable',
                      message: 'Authentication configuration could not be initialized.',
                    )
                  : scenario == 'road-trip' || scenario == 'qr-mismatch'
                  ? RoadTripScreen(
                      session: mobile!.session,
                      cached: mobile!.session.snapshot!.trips.single,
                      journal: MemoryJournal(),
                      scanner: FakeYardScanner(),
                    )
                  : scenario == 'sync-states'
                  ? Scaffold(
                      appBar: AppBar(title: const Text('Offline operations')),
                      body: SingleChildScrollView(
                        padding: const EdgeInsets.all(16),
                        child: SyncPanel(worker: worker!),
                      ),
                    )
                  : SessionView(
                      session: mobile!.session,
                      journal: MemoryJournal(),
                      worker: worker,
                    );
              await render(tester, screen, scale);
              if (scenario == 'qr-mismatch') {
                final hook = find.widgetWithText(
                  FilledButton,
                  'Scan Trailer and confirm Hook',
                );
                await tester.scrollUntilVisible(
                  hook,
                  160,
                  scrollable: find.byType(Scrollable).first,
                );
                await Scrollable.ensureVisible(
                  tester.element(hook),
                  alignment: .5,
                );
                await settle(tester);
                expect(hook.hitTestable(), findsOneWidget);
                await tester.tap(hook);
                await settle(tester);
                expect(find.text('Trailer not authorized'), findsOneWidget);
                if (!baseline) {
                  expect(find.text('Close'), findsOneWidget);
                  expect(find.text('Confirm'), findsNothing);
                }
              }
            }
            await capture(tester, scenario, scale);
            // Also inspect real lower-page content on the smallest normal phone.
            if (width == 360 &&
                scale == 1 &&
                scenario != 'yard-move-review' &&
                scenario != 'qr-mismatch' &&
                scenario != 'yard-damage' &&
                scenario != 'sign-in') {
              final scrollables = find.byType(Scrollable);
              if (scrollables.evaluate().isNotEmpty) {
                final state = tester.state<ScrollableState>(scrollables.first);
                state.position.jumpTo(state.position.maxScrollExtent);
                await settle(tester);
                await capture(tester, '$scenario-lower', scale);
              }
            }
            if (mobile != null) {
              expect(mobile!.requests.where((r) => r.method != 'GET'), isEmpty);
            }
            if (yard != null) {
              expect(
                yard.mobile.requests.where((r) => r.method != 'GET'),
                isEmpty,
              );
            }
            if (checks != null) {
              expect(
                checks!.mobile.requests.where((r) => r.method != 'GET'),
                isEmpty,
              );
            }
            if (media != null) {
              expect(media!.posts, isEmpty);
              expect(media!.puts, 0);
              expect(media!.businessEffects, 0);
            }
          } finally {
            await tester.pumpWidget(const SizedBox.shrink());
            await tester.pump(const Duration(milliseconds: 300));
            await tester.runAsync(() async {
              await worker?.shutdown();
              await mobile?.close();
              await yard?.close();
              await checks?.close();
              await media?.close();
              await directory?.delete(recursive: true);
            });
          }
        },
      );
    }
  }
}
