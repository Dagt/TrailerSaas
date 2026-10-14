import 'dart:convert';
import 'dart:io';

import 'package:drift/native.dart';
import 'package:http/http.dart' as http;
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/sync/replay_worker.dart';
import 'package:trailersaas_mobile/src/yard/check_commands.dart';
import 'package:trailersaas_mobile/src/yard/check_store.dart';

import '../session/fixtures.dart';
import '../road/online_execution_test.dart' show MemoryJournal;
import 'fixtures.dart';

const checkId = '10000000-0000-4000-8000-000000000001';
const checkUser = '10000000-0000-4000-8000-000000000002';
String inventoryId(int n) =>
    '20000000-0000-4000-8000-${n.toString().padLeft(12, '0')}';
Map<String, dynamic> checkSummary(
  int count, {
  int version = 0,
  String status = 'IN_PROGRESS',
  int matched = 0,
  int mismatch = 0,
  int unexpected = 0,
}) => {
  'id': checkId,
  'siteId': yardSiteId,
  'locationId': null,
  'status': status,
  'version': version,
  'startedByUserId': checkUser,
  'startedAt': '2026-10-10T09:00:00Z',
  'completedByUserId': status == 'COMPLETED' ? checkUser : null,
  'completedAt': status == 'COMPLETED' ? '2026-10-13T12:00:00Z' : null,
  'cancelledByUserId': status == 'CANCELLED' ? checkUser : null,
  'cancelledAt': status == 'CANCELLED' ? '2026-10-13T12:00:00Z' : null,
  'cancellationReason': status == 'CANCELLED' ? 'Stopped safely' : null,
  'expectedCount': count,
  'scannedCount': matched + mismatch + unexpected,
  'matchedCount': matched,
  'missingCount': status == 'COMPLETED' ? count - matched - mismatch : 0,
  'unexpectedCount': unexpected,
  'mismatchCount': mismatch,
  'remainingCount': status == 'COMPLETED' ? 0 : count - matched - mismatch,
  'totalItemCount': count + unexpected,
};
Map<String, dynamic> checkItem(int n) => {
  'id': inventoryId(n),
  'trailerId': n == 1 ? trailerId : inventoryId(1000 + n),
  'expected': true,
  'expectedLocationId': yardId,
  'recordedSnapshot': {
    'operationalNumber': 'T$n',
    'trailerVersion': 12,
    'locationState': 'AT_LOCATION',
    'currentLocationId': yardId,
    'loadState': 'LOADED',
    'serviceabilityState': 'IN_SERVICE',
    'couplingState': 'UNCOUPLED',
    'lastKnownLocationId': yardId,
    'lastSeenAt': '2026-10-10T08:00:00Z',
    'locationName': 'Yard A',
    'locationType': 'YARD',
    'siteName': 'Main Site',
  },
  'found': false,
  'result': null,
  'observedLocationId': null,
  'scannedAt': null,
  'scannedByUserId': null,
  'recordedAt': null,
  'clientOperationId': null,
  'reconciliationId': null,
};

class CheckFixture {
  CheckFixture(this.directory, int count) {
    mobile = MobileFixture(
      database: AppDatabase(
        NativeDatabase(File('${directory.path}/checks.sqlite')),
      ),
    );
    summary = checkSummary(count);
    items = [for (var i = 1; i <= count; i++) checkItem(i)];
  }
  final Directory directory;
  late final MobileFixture mobile;
  late ReplayWorker worker;
  late Map<String, dynamic> summary;
  late List<Map<String, dynamic>> items;
  final receipts = <String, Map<String, dynamic>>{};
  Future<http.Response> Function(http.Request)? custom;
  int starts = 0;
  CheckStore get cache => CheckStore(worker.store);
  CheckReplay get replay => CheckReplay(worker.store, mobile.session);
  Future<void> open() async {
    mobile.handler = (r) => custom?.call(r) ?? handle(r);
    await mobile.session.start();
    worker = ReplayWorker(
      mobile.session,
      CommandStore(mobile.db),
      MemoryJournal(),
      scheduleTimers: false,
      jitter: () => 0.5,
    );
  }

  Map<String, dynamic> page([String? cursor]) {
    final offset = cursor == null
        ? 0
        : items.indexWhere((i) => i['id'] == cursor) + 1;
    final rows = items.skip(offset).take(200).toList();
    final next = offset + rows.length < items.length ? rows.last['id'] : null;
    return {
      'check': summary,
      'items': rows,
      'nextCursor': next,
      'snapshotComplete': cursor == null && next == null,
    };
  }

  Future<http.Response> handle(http.Request r) async {
    if (r.url.path.endsWith('/api/v1/me')) {
      return mobile.response({
        'userId': checkUser,
        'memberships': [
          membership(
            'A',
            capabilities: [
              'YARD_CHECK_EXECUTE',
              'YARD_MOVE_EXECUTE',
              'TRAILER_LOCATION_CORRECT',
            ],
          ),
        ],
      });
    }
    if (r.url.path.contains('/trailer-qr/')) {
      return mobile.response({
        'trailerId': trailerId,
        'active': true,
        'operationalNumber': 'T1',
        'version': 12,
      });
    }
    if (r.url.path.contains('/yard/trailers/')) {
      return mobile.response(yardTrailer());
    }
    if (r.url.path.endsWith('/yard/sites')) {
      return mobile.response({
        'items': [yardSite()],
        'nextCursor': null,
      });
    }
    if (r.method == 'GET' && r.url.path.endsWith('/yard-checks')) {
      return mobile.response({
        'items': [summary],
        'nextCursor': null,
      });
    }
    if (r.method == 'GET' && r.url.path.endsWith('/$checkId')) {
      final expected = r.url.queryParameters['expectedVersion'];
      if (expected != null && expected != '${summary['version']}') {
        return mobile.response({'code': 'STALE_VERSION'}, 409);
      }
      return mobile.response(page(r.url.queryParameters['cursor']));
    }
    final key = r.headers['Idempotency-Key'] ?? r.headers['idempotency-key'];
    if (key != null && receipts.containsKey(key)) {
      return mobile.response(receipts[key]!);
    }
    final body = jsonDecode(r.body) as Map<String, dynamic>;
    Map<String, dynamic> result;
    if (r.url.path.endsWith('/yard-checks')) {
      starts++;
      result = page();
    } else if (r.url.path.endsWith('/scans')) {
      final i = items.firstWhere((i) => i['trailerId'] == body['trailerId']);
      final duplicate = i['found'] == true;
      if (!duplicate) {
        i.addAll({
          'found': true,
          'result': body['observedLocationId'] == i['expectedLocationId']
              ? 'MATCHED'
              : 'LOCATION_MISMATCH',
          'observedLocationId': body['observedLocationId'],
          'scannedAt': body['scannedAt'],
          'scannedByUserId': checkUser,
          'recordedAt': DateTime.now().toUtc().toIso8601String(),
          'clientOperationId': key,
        });
        summary = checkSummary(
          summary['expectedCount'] as int,
          version: (summary['version'] as int) + 1,
          matched: items.where((i) => i['result'] == 'MATCHED').length,
          mismatch: items
              .where((i) => i['result'] == 'LOCATION_MISMATCH')
              .length,
        );
      }
      result = {
        'check': summary,
        'item': i,
        'outcome': duplicate ? 'ALREADY_SCANNED' : i['result'],
      };
    } else if (r.url.path.endsWith('/completion') ||
        r.url.path.endsWith('/cancellation')) {
      if (body['expectedVersion'] != summary['version']) {
        return mobile.response({'code': 'STALE_VERSION'}, 409);
      }
      final cancel = r.url.path.endsWith('/cancellation');
      summary = checkSummary(
        summary['expectedCount'] as int,
        version: (summary['version'] as int) + 1,
        status: cancel ? 'CANCELLED' : 'COMPLETED',
        matched: summary['matchedCount'] as int,
        mismatch: summary['mismatchCount'] as int,
      );
      if (cancel) {
        summary['cancellationReason'] = body['reason'];
      } else {
        for (final i in items) {
          if (i['found'] == false) i['result'] = 'MISSING';
        }
      }
      result = summary;
    } else {
      throw StateError('Unexpected request ${r.url}');
    }
    final frozen = jsonDecode(jsonEncode(result)) as Map<String, dynamic>;
    if (key != null) receipts[key] = frozen;
    return mobile.response(
      frozen,
      r.url.path.endsWith('/yard-checks') ? 201 : 200,
    );
  }

  Future<void> snapshot() => cache.download(
    mobile.session,
    mobile.session.snapshot!,
    checkId,
    () => true,
  );
  Future<void> close() async {
    await worker.shutdown();
    await mobile.close();
  }
}
