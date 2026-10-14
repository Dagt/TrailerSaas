import 'dart:convert';

import 'yard_contracts.dart';

Object? canonicalCheck(Object? value) {
  if (value is Map<String, dynamic>) {
    final keys = value.keys.toList()..sort();
    return {for (final key in keys) key: canonicalCheck(value[key])};
  }
  if (value is List) return value.map(canonicalCheck).toList();
  return value;
}

Map<String, dynamic> checkMap(Object? value) {
  if (value is! Map<String, dynamic>) {
    throw const FormatException('Invalid Yard Check response');
  }
  return value;
}

void checkTime(Object? value) {
  if (value is! String ||
      !value.endsWith('Z') ||
      DateTime.tryParse(value) == null) {
    throw const FormatException('Invalid Check timestamp');
  }
}

class CheckSummary {
  CheckSummary(Map<String, dynamic> value) : json = Map.unmodifiable(value) {
    const fields = {
      'id',
      'siteId',
      'locationId',
      'status',
      'version',
      'startedByUserId',
      'startedAt',
      'completedByUserId',
      'completedAt',
      'cancelledByUserId',
      'cancelledAt',
      'cancellationReason',
      'expectedCount',
      'scannedCount',
      'matchedCount',
      'missingCount',
      'unexpectedCount',
      'mismatchCount',
      'remainingCount',
      'totalItemCount',
    };
    if (value.length != fields.length || !value.keys.every(fields.contains)) {
      throw const FormatException('Invalid Check summary fields');
    }
    for (final key in ['id', 'siteId', 'startedByUserId']) {
      yardUuid(value[key]);
    }
    if (value['locationId'] != null) yardUuid(value['locationId']);
    checkTime(value['startedAt']);
    if (!{'IN_PROGRESS', 'COMPLETED', 'CANCELLED'}.contains(value['status'])) {
      throw const FormatException('Invalid Check status');
    }
    for (final key in [
      'version',
      'expectedCount',
      'scannedCount',
      'matchedCount',
      'missingCount',
      'unexpectedCount',
      'mismatchCount',
      'remainingCount',
      'totalItemCount',
    ]) {
      if (value[key] is! int || (value[key] as int) < 0) {
        throw const FormatException('Invalid Check counts');
      }
    }
    if (count('totalItemCount') !=
            count('expectedCount') + count('unexpectedCount') ||
        count('scannedCount') !=
            count('matchedCount') +
                count('unexpectedCount') +
                count('mismatchCount') ||
        count('expectedCount') !=
            count('matchedCount') +
                count('mismatchCount') +
                count('remainingCount') +
                count('missingCount')) {
      throw const FormatException('Inconsistent Check counts');
    }
    for (final action in ['completed', 'cancelled']) {
      final terminal =
          status == (action == 'completed' ? 'COMPLETED' : 'CANCELLED');
      if (terminal) {
        yardUuid(value['${action}ByUserId']);
        checkTime(value['${action}At']);
      } else if (value['${action}ByUserId'] != null ||
          value['${action}At'] != null) {
        throw const FormatException('Invalid Check terminal receipt');
      }
    }
    if (status == 'CANCELLED' &&
        (value['cancellationReason'] is! String ||
            (value['cancellationReason'] as String).trim().isEmpty)) {
      throw const FormatException('Cancellation reason missing');
    }
  }
  final Map<String, dynamic> json;
  String get id => json['id'] as String;
  String get siteId => json['siteId'] as String;
  String? get locationId => json['locationId'] as String?;
  String get status => json['status'] as String;
  int get version => count('version');
  int count(String key) => json[key] as int;
  String get origin => jsonEncode(
    canonicalCheck([
      id,
      siteId,
      locationId,
      json['startedAt'],
      json['startedByUserId'],
      count('expectedCount'),
    ]),
  );
}

class CheckItem {
  CheckItem(Map<String, dynamic> value) : json = Map.unmodifiable(value) {
    const fields = {
      'id',
      'trailerId',
      'expected',
      'expectedLocationId',
      'recordedSnapshot',
      'found',
      'result',
      'observedLocationId',
      'scannedAt',
      'scannedByUserId',
      'recordedAt',
      'clientOperationId',
      'reconciliationId',
    };
    if (value.length != fields.length || !value.keys.every(fields.contains)) {
      throw const FormatException('Invalid inventory fields');
    }
    yardUuid(value['id']);
    yardUuid(value['trailerId']);
    if (value['expected'] is! bool || value['found'] is! bool) {
      throw const FormatException('Invalid Check membership');
    }
    if (expected) {
      yardUuid(value['expectedLocationId']);
    } else if (value['expectedLocationId'] != null) {
      throw const FormatException('Unexpected item has expected location');
    }
    final snapshot = checkMap(value['recordedSnapshot']);
    final facts = {
      'operationalNumber',
      'trailerVersion',
      'locationState',
      'currentLocationId',
      'loadState',
      'serviceabilityState',
      'couplingState',
      'lastKnownLocationId',
      'lastSeenAt',
      if (expected) ...{'locationName', 'locationType', 'siteName'},
    };
    if (snapshot.length != facts.length ||
        !snapshot.keys.every(facts.contains)) {
      throw const FormatException('Invalid snapshot facts');
    }
    if (snapshot['operationalNumber'] is! String ||
        (snapshot['operationalNumber'] as String).trim().isEmpty ||
        snapshot['trailerVersion'] is! int ||
        (snapshot['trailerVersion'] as int) < 0 ||
        !{
          'AT_LOCATION',
          'IN_TRANSIT',
          'LOCATION_UNKNOWN',
        }.contains(snapshot['locationState']) ||
        !{'EMPTY', 'LOADED', 'UNKNOWN'}.contains(snapshot['loadState']) ||
        !{
          'IN_SERVICE',
          'OUT_OF_SERVICE',
        }.contains(snapshot['serviceabilityState']) ||
        !{'COUPLED', 'UNCOUPLED'}.contains(snapshot['couplingState'])) {
      throw const FormatException('Invalid inventory snapshot');
    }
    for (final key in ['currentLocationId', 'lastKnownLocationId']) {
      if (snapshot[key] != null) yardUuid(snapshot[key]);
    }
    if (snapshot['lastSeenAt'] != null) checkTime(snapshot['lastSeenAt']);
    if (expected &&
        (snapshot['locationName'] is! String ||
            snapshot['siteName'] is! String ||
            !{'YARD', 'DOCK'}.contains(snapshot['locationType']) ||
            snapshot['currentLocationId'] != value['expectedLocationId'] ||
            snapshot['locationState'] != 'AT_LOCATION')) {
      throw const FormatException('Invalid expected physical facts');
    }
    if (found) {
      if (!{
            'MATCHED',
            'UNEXPECTED',
            'LOCATION_MISMATCH',
          }.contains(value['result']) ||
          (expected == (value['result'] == 'UNEXPECTED'))) {
        throw const FormatException('Invalid observation result');
      }
      for (final key in [
        'observedLocationId',
        'scannedByUserId',
        'clientOperationId',
      ]) {
        yardUuid(value[key]);
      }
      checkTime(value['scannedAt']);
      checkTime(value['recordedAt']);
      if (expected &&
          ((value['expectedLocationId'] == value['observedLocationId']) !=
              (value['result'] == 'MATCHED'))) {
        throw const FormatException('Invalid match classification');
      }
    } else if (value['result'] != null && value['result'] != 'MISSING') {
      throw const FormatException('Invalid unobserved item');
    } else if (!expected ||
        [
          'observedLocationId',
          'scannedAt',
          'scannedByUserId',
          'recordedAt',
          'clientOperationId',
        ].any((k) => value[k] != null)) {
      throw const FormatException(
        'Unobserved item contains fabricated evidence',
      );
    }
    if (value['reconciliationId'] != null) yardUuid(value['reconciliationId']);
  }
  final Map<String, dynamic> json;
  String get id => json['id'] as String;
  String get trailerId => json['trailerId'] as String;
  bool get expected => json['expected'] as bool;
  bool get found => json['found'] as bool;
  String? get expectedLocationId => json['expectedLocationId'] as String?;
  String get origin => jsonEncode(
    canonicalCheck([
      id,
      trailerId,
      expected,
      expectedLocationId,
      json['recordedSnapshot'],
    ]),
  );
}

class CheckDetail {
  CheckDetail(Map<String, dynamic> value)
    : check = CheckSummary(checkMap(value['check'])),
      items = (value['items'] as List)
          .map((r) => CheckItem(checkMap(r)))
          .toList(),
      nextCursor = value['nextCursor'] as String? {
    if (value['snapshotComplete'] is! bool ||
        (value['snapshotComplete'] == true && nextCursor != null) ||
        items.length > 200) {
      throw const FormatException('Invalid snapshot continuation');
    }
    if (nextCursor != null) yardUuid(nextCursor);
  }
  final CheckSummary check;
  final List<CheckItem> items;
  final String? nextCursor;
}

class CheckSnapshot {
  CheckSnapshot(this.check, this.items);
  final CheckSummary check;
  final List<CheckItem> items;
  String get review => jsonEncode(
    canonicalCheck([
      check.json,
      [for (final i in items) i.json],
    ]),
  );
}
