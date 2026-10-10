import 'dart:async';
import 'dart:convert';

import 'package:drift/native.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:trailersaas_mobile/src/configuration/app_configuration.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/persistence/session_cache.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';
import 'package:trailersaas_mobile/src/session/auth_gateway.dart';
import 'package:trailersaas_mobile/src/session/api_client.dart';
import 'package:trailersaas_mobile/src/session/session_controller.dart';

class FakeAuth implements AuthGateway {
  FakeAuth([this.uid = 'firebase-A']);
  @override
  String? uid;
  final events = StreamController<String?>.broadcast(sync: true);
  final forceRefreshes = <bool>[];
  @override
  Stream<String?> get changes => events.stream;
  @override
  Future<String?> token({bool forceRefresh = false}) async {
    forceRefreshes.add(forceRefresh);
    return uid == null ? null : 'private-bearer';
  }

  @override
  Future<void> signIn(String email, String password) async {
    uid = 'firebase-A';
  }

  @override
  Future<void> signOut() async {
    uid = null;
    events.add(null);
  }
}

class FakeNetwork implements Connectivity {
  ConnectivityStatus status = ConnectivityStatus.available;
  final events = StreamController<ConnectivityStatus>.broadcast(sync: true);
  @override
  Future<ConnectivityStatus> currentStatus() async => status;
  @override
  Stream<ConnectivityStatus> get changes => events.stream;
}

Map<String, dynamic> membership(
  String id, {
  List<String> capabilities = const ['TRIP_VIEW_ASSIGNED', 'YARD_MOVE_EXECUTE'],
}) => {
  'tenantId': id,
  'tenantName': 'Tenant $id',
  'status': 'ACTIVE',
  'roles': [],
  'capabilities': capabilities,
};
Map<String, dynamic> work(String id) => {
  'id': id,
  'status': 'ASSIGNED',
  'currentDriverId': 'driver',
  'currentTractorId': 'tractor',
  'plannedInitialTrailerId': 'trailer',
  'plannedTrailer': {
    'id': 'trailer',
    'operationalNumber': 'TR-123',
    'locationState': 'LOCATION_UNKNOWN',
    'loadState': 'UNKNOWN',
    'serviceabilityState': 'IN_SERVICE',
    'active': true,
    'version': 3,
  },
  'version': 2,
  'updatedAt': '2026-10-10T10:00:00Z',
  'stops': [
    {
      'id': 'stop-$id',
      'sequenceNumber': 1,
      'stopType': 'DELIVERY',
      'siteName': 'Dock 17',
      'locationName': null,
      'siteSnapshot': null,
      'scheduledAt': null,
      'status': 'PENDING',
      'instructions': 'Call Dispatch',
      'version': 0,
      'orderIds': ['order'],
    },
  ],
};

class MobileFixture {
  MobileFixture({String? uid = 'firebase-A', AppDatabase? database}) {
    db = database ?? AppDatabase(NativeDatabase.memory());
    auth = FakeAuth(uid);
    cache = SessionCache(db);
    api = ApiClient(
      AppConfiguration.fromValues().apiBaseUrl,
      auth,
      MockClient((request) async {
        requests.add(request);
        if (handler != null) return handler!(request);
        if (request.url.path.endsWith('/api/v1/me'))
          return response({
            'userId': auth.uid == 'firebase-A' ? 'user-A' : 'user-B',
            'memberships': memberships,
          });
        if (request.url.path.endsWith('/driver/me'))
          return response({
            'id': 'driver',
            'name': 'Jane Driver',
            'active': true,
            'version': 0,
          });
        return response({'items': trips, 'nextCursor': null});
      }),
    );
    session = SessionController(auth, api, cache, network);
  }
  late final AppDatabase db;
  late final FakeAuth auth;
  late final SessionCache cache;
  late final ApiClient api;
  late final SessionController session;
  final network = FakeNetwork();
  List<Map<String, dynamic>> memberships = [membership('A')];
  List<Map<String, dynamic>> trips = [work('trip-A')];
  final requests = <http.Request>[];
  Future<http.Response> Function(http.Request)? handler;
  http.Response response(Object body, [int status = 200]) => http.Response(
    jsonEncode(body),
    status,
    headers: {'content-type': 'application/json'},
  );
  Future<void> close() async {
    session.dispose();
    await db.close();
    await auth.events.close();
    await network.events.close();
  }
}
