import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/platform/qr_scanner.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/yard/yard_controller.dart';

import '../session/fixtures.dart';
import '../road/online_execution_test.dart' show MemoryJournal;

const yardSiteId = '00000000-0000-4000-8000-000000000001';
const yardId = '00000000-0000-4000-8000-000000000002';
const dockId = '00000000-0000-4000-8000-000000000003';
const dock2Id = '00000000-0000-4000-8000-000000000004';
const trailerId = '00000000-0000-4000-8000-000000000005';
const secondTrailerId = '00000000-0000-4000-8000-000000000006';
const customerId = '00000000-0000-4000-8000-000000000007';
const qrToken = 'AAAAAAAAAAAAAAAAAAAAAA';

Map<String, dynamic> location(String id) => {
  'id': id,
  'siteId': yardSiteId,
  'name': id == yardId
      ? 'Yard A'
      : id == dockId
      ? 'Dock 17'
      : 'Dock 18',
  'type': id == yardId ? 'YARD' : 'DOCK',
  'version': 0,
  'customer': null,
};
Map<String, dynamic> yardSite() => {
  'id': yardSiteId,
  'name': 'Main Site',
  'timezone': 'UTC',
  'version': 0,
  'locations': [location(yardId), location(dockId), location(dock2Id)],
  'nextLocationCursor': null,
};
Map<String, dynamic> yardTrailer({
  String id = trailerId,
  String current = yardId,
  int version = 12,
}) => {
  'trailerId': id,
  'operationalNumber': id == trailerId ? 'T114' : 'T115',
  'designatedCustomer': {'id': customerId, 'name': 'Customer A'},
  'locationState': 'AT_LOCATION',
  'currentLocation': {
    'id': current,
    'name': location(current)['name'],
    'type': location(current)['type'],
    'siteId': yardSiteId,
    'siteName': 'Main Site',
    'active': true,
    'siteActive': true,
  },
  'lastKnownLocationId': current,
  'lastSeenAt': '2026-10-10T10:00:00Z',
  'loadState': 'LOADED',
  'serviceabilityState': 'IN_SERVICE',
  'couplingState': 'UNCOUPLED',
  'active': true,
  'version': version,
};
Map<String, dynamic> acceptedMove(
  Map<String, dynamic> body, [
  String event = '00000000-0000-4000-8000-000000000010',
]) => {
  'trailer': {
    ...yardTrailer(
      id: body['trailerId'] as String,
      current: body['toLocationId'] as String,
      version: (body['expectedTrailerVersion'] as int) + 1,
    ),
    'lastSeenAt': body['observedAt'],
  },
  'movementEvent': {
    'id': event,
    'eventType': 'YARD_MOVED',
    'occurredAt': body['observedAt'],
    'recordedAt': DateTime.now().toUtc().toIso8601String(),
  },
};

class FakeYardScanner implements CancellableQrScanner {
  String? value = qrToken;
  Completer<String?>? pending;
  int cancellations = 0;
  @override
  Future<String?> scan() => pending?.future ?? Future.value(value);
  @override
  void cancel() {
    cancellations++;
  }
}

class YardFixture {
  bool _closed = false;
  YardFixture({AppDatabase? database}) {
    mobile = MobileFixture(database: database, apiClock: () => now);
  }
  late final MobileFixture mobile;
  final scanner = FakeYardScanner();
  late ReplayWorker worker;
  late YardModeController controller;
  DateTime now = DateTime.now().toUtc().subtract(const Duration(seconds: 1));
  Map<String, dynamic> currentTrailer = yardTrailer();
  Future<http.Response> Function(http.Request)? custom;
  Future<http.Response> reads(http.Request r) async {
    if (r.url.path.endsWith('/api/v1/me')) {
      return mobile.response({
        'userId': mobile.auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
        'memberships': mobile.memberships,
      });
    }
    if (r.url.path.endsWith('/driver/me')) {
      return mobile.response({'id': 'driver', 'name': 'Jane', 'active': true});
    }
    if (r.url.path.endsWith('/yard/sites')) {
      return mobile.response({
        'items': [yardSite()],
        'nextCursor': null,
      });
    }
    if (r.url.path.endsWith('/locations')) {
      return mobile.response({
        'items': yardSite()['locations'],
        'nextCursor': null,
      });
    }
    if (r.url.path.contains('/trailer-qr/')) {
      return mobile.response({
        'trailerId': trailerId,
        'version': currentTrailer['version'],
        'operationalNumber': 'T114',
        'active': true,
      });
    }
    if (r.url.path.contains('/yard/trailers/')) {
      return mobile.response(
        r.url.path.endsWith(secondTrailerId)
            ? yardTrailer(id: secondTrailerId)
            : currentTrailer,
      );
    }
    return mobile.response({'items': mobile.trips, 'nextCursor': null});
  }

  Future<void> open({bool dual = false}) async {
    mobile.memberships = [
      membership(
        'A',
        capabilities: ['YARD_MOVE_EXECUTE', if (dual) 'TRIP_VIEW_ASSIGNED'],
      ),
    ];
    mobile.handler = (r) async {
      if (custom != null) return custom!(r);
      if (r.method == 'GET') return reads(r);
      final response = acceptedMove(jsonDecode(r.body) as Map<String, dynamic>);
      currentTrailer = response['trailer'] as Map<String, dynamic>;
      return mobile.response(response);
    };
    await mobile.session.start();
    worker = ReplayWorker(
      mobile.session,
      CommandStore(mobile.db, clock: () => now),
      MemoryJournal(),
      scheduleTimers: false,
      jitter: () => 0.5,
    );
    controller = YardModeController(
      mobile.session,
      worker,
      scanner,
      clock: () => now,
    );
    await controller.load();
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    controller.dispose();
    await worker.shutdown();
    await mobile.close();
  }
}
