// Explicit debug-only Android review entry. Never imported by lib/main.dart.
// Run only on a dedicated emulator with -t test/native_review/main.dart and
// --dart-define=UX06_NATIVE_REVIEW=true. No Firebase or real HTTP transport.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:trailersaas_mobile/src/platform/native_qr_scanner.dart';
import 'package:trailersaas_mobile/src/road/online_command_journal.dart';
import 'package:trailersaas_mobile/src/road/road_trip_screen.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';
import 'package:trailersaas_mobile/src/ui/fleet_theme.dart';
import 'package:trailersaas_mobile/src/yard/yard_view.dart';

import '../session/fixtures.dart';
import '../yard/fixtures.dart';

class ReviewJournal implements OnlineCommandJournal {
  @override
  Future<OnlineCommand?> read(String user, String tenant) async => null;
  @override
  Future<void> save(String user, String tenant, OnlineCommand command) async =>
      throw StateError('ISOLATED_REVIEW_WRITES_DISABLED');
  @override
  Future<void> clear(String user, String tenant, {String? operationId}) async {}
}

Future<void> main() async {
  if (!kDebugMode || !const bool.fromEnvironment('UX06_NATIVE_REVIEW')) {
    throw StateError('Explicit debug native-review build required');
  }
  WidgetsFlutterBinding.ensureInitialized();
  final yard = YardFixture();
  final trip = work('trip-A');
  yard.mobile.trips = [trip];
  yard.custom = (request) async {
    if (request.method != 'GET') {
      debugPrint('ISOLATED_REVIEW_WRITE_REJECTED:${request.method}');
      return yard.mobile.response({
        'code': 'ISOLATED_REVIEW_WRITES_DISABLED',
      }, 403);
    }
    if (request.url.path.endsWith('/trips/trip-A')) {
      return yard.mobile.response({
        'trip': trip,
        'currentCoupling': null,
        'tractorOperationalNumber': 'T-12',
        'departurePrerequisitesSatisfied': false,
        'arrivalAssistance': null,
      });
    }
    return yard.reads(request);
  };
  await yard.open(
    dual: true,
    capabilities: ['TRIP_EXECUTE_ASSIGNED', 'ARRIVAL_CONFIRM', 'TRAILER_HOOK'],
  );
  final signIn = MobileFixture(uid: null);
  signIn.handler = (request) async =>
      signIn.response({'code': 'ISOLATED_REVIEW_WRITES_DISABLED'}, 403);
  await signIn.session.start();
  runApp(
    MaterialApp(
      title: 'ISOLATED ANDROID REVIEW',
      theme: fleetTheme(),
      debugShowCheckedModeBanner: false,
      builder: (context, child) => Column(
        children: [
          Material(
            color: const Color(0xfffffbeb),
            child: SafeArea(
              bottom: false,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: const Text(
                  'ISOLATED ANDROID REVIEW · Synthetic data · No Firebase/Backend · HTTP writes rejected',
                  style: TextStyle(fontSize: 12, color: Color(0xff92400e)),
                ),
              ),
            ),
          ),
          Expanded(child: child!),
        ],
      ),
      home: Builder(
        builder: (context) {
          void open(Widget page) =>
              Navigator.of(context)
                  .push<void>(MaterialPageRoute(builder: (_) => page));
          return Scaffold(
            appBar: AppBar(title: const Text('UX-06 native review')),
            body: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const Text(
                  'Actual production widgets on Android. Isolated fixtures are not real authorization or accepted operations. Use Back to return.',
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => open(
                    SessionView(
                      session: yard.mobile.session,
                      journal: ReviewJournal(),
                      worker: yard.worker,
                    ),
                  ),
                  child: const Text('Road and Yard workspace'),
                ),
                OutlinedButton(
                  onPressed: () => open(SessionView(session: signIn.session)),
                  child: const Text('Sign-in keyboard review'),
                ),
                OutlinedButton(
                  onPressed: () => NativeQrScanner(context).scan(),
                  child: const Text('Native QR permission review'),
                ),
                OutlinedButton(
                  onPressed: () => open(
                    Scaffold(
                      appBar: AppBar(title: const Text('Synthetic Yard scan')),
                      body: SingleChildScrollView(
                        padding: const EdgeInsets.all(16),
                        child: YardHomePanel(
                          session: yard.mobile.session,
                          worker: yard.worker,
                          scanner: FakeYardScanner(),
                        ),
                      ),
                    ),
                  ),
                  child: const Text('Yard movement review fixture'),
                ),
                OutlinedButton(
                  onPressed: () => open(
                    RoadTripScreen(
                      session: yard.mobile.session,
                      cached: yard.mobile.session.snapshot!.trips.single,
                      journal: ReviewJournal(),
                    ),
                  ),
                  child: const Text('Assigned Trip native QR'),
                ),
              ],
            ),
          );
        },
      ),
    ),
  );
}
