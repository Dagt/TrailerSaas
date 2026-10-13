import 'dart:convert';

import '../media/media_file_store.dart';

const yardLocationTypes = {
  'BASE',
  'YARD',
  'DOCK',
  'CUSTOMER_AREA',
  'SHOP',
  'OTHER',
};
void yardUuid(Object? value) {
  if (value is! String || !uuidPattern.hasMatch(value)) {
    throw const FormatException('Invalid Yard identity');
  }
}

void yardCustomer(Object? value) {
  if (value == null) return;
  if (value is! Map ||
      value.keys.toSet().difference({'id', 'name'}).isNotEmpty ||
      value['name'] is! String) {
    throw const FormatException('Invalid Customer summary');
  }
  yardUuid(value['id']);
}

class YardLocation {
  YardLocation(
    this.id,
    this.siteId,
    this.name,
    this.type,
    this.version,
    this.customer,
  );
  final String id, siteId, name, type;
  final int version;
  final Map<String, dynamic>? customer;
  factory YardLocation.fromJson(Map<String, dynamic> raw) {
    yardUuid(raw['id']);
    yardUuid(raw['siteId']);
    yardCustomer(raw['customer']);
    if (raw.keys.toSet().difference({
          'id',
          'siteId',
          'name',
          'type',
          'version',
          'customer',
        }).isNotEmpty ||
        raw['name'] is! String ||
        !yardLocationTypes.contains(raw['type']) ||
        raw['version'] is! int ||
        (raw['version'] as int) < 0) {
      throw const FormatException('Invalid Yard location');
    }
    return YardLocation(
      raw['id'] as String,
      raw['siteId'] as String,
      raw['name'] as String,
      raw['type'] as String,
      raw['version'] as int,
      raw['customer'] == null
          ? null
          : Map.unmodifiable(raw['customer'] as Map<String, dynamic>),
    );
  }
  Map<String, dynamic> toJson() => {
    'id': id,
    'siteId': siteId,
    'name': name,
    'type': type,
    'version': version,
    'customer': customer,
  };
}

class YardSite {
  YardSite(
    this.id,
    this.name,
    this.timezone,
    this.version,
    this.locations,
    this.nextLocationCursor,
  );
  final String id, name, timezone;
  final int version;
  final List<YardLocation> locations;
  final String? nextLocationCursor;
  factory YardSite.fromJson(Map<String, dynamic> raw) {
    yardUuid(raw['id']);
    if (raw['nextLocationCursor'] != null) yardUuid(raw['nextLocationCursor']);
    if (raw.keys.toSet().difference({
          'id',
          'name',
          'timezone',
          'version',
          'locations',
          'nextLocationCursor',
        }).isNotEmpty ||
        raw['name'] is! String ||
        raw['timezone'] is! String ||
        raw['version'] is! int ||
        (raw['version'] as int) < 0 ||
        raw['locations'] is! List) {
      throw const FormatException('Invalid Yard Site');
    }
    final locations = (raw['locations'] as List)
        .map((l) => YardLocation.fromJson(l as Map<String, dynamic>))
        .toList();
    if (locations.any((l) => l.siteId != raw['id']) ||
        locations.map((l) => l.id).toSet().length != locations.length) {
      throw const FormatException('Invalid Site/location relationship');
    }
    return YardSite(
      raw['id'] as String,
      raw['name'] as String,
      raw['timezone'] as String,
      raw['version'] as int,
      List.unmodifiable(locations),
      raw['nextLocationCursor'] as String?,
    );
  }
  YardSite withLocations(List<YardLocation> all) =>
      YardSite(id, name, timezone, version, List.unmodifiable(all), null);
  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'timezone': timezone,
    'version': version,
    'locations': locations.map((l) => l.toJson()).toList(),
    'nextLocationCursor': nextLocationCursor,
  };
}

class YardTrailer {
  YardTrailer._(this.data);
  final Map<String, dynamic> data;
  String get id => data['trailerId'] as String;
  String get number => data['operationalNumber'] as String;
  Map<String, dynamic>? get location =>
      data['currentLocation'] as Map<String, dynamic>?;
  bool get movable =>
      data['active'] == true &&
      data['locationState'] == 'AT_LOCATION' &&
      data['serviceabilityState'] == 'IN_SERVICE' &&
      data['couplingState'] == 'UNCOUPLED' &&
      location?['active'] == true &&
      location?['siteActive'] == true;
  factory YardTrailer.fromJson(Map<String, dynamic> raw) {
    const fields = {
      'trailerId',
      'operationalNumber',
      'designatedCustomer',
      'locationState',
      'currentLocation',
      'lastKnownLocationId',
      'lastSeenAt',
      'loadState',
      'serviceabilityState',
      'couplingState',
      'active',
      'version',
    };
    if (raw.length != fields.length ||
        !raw.keys.every(fields.contains) ||
        raw['operationalNumber'] is! String ||
        raw['active'] is! bool ||
        raw['version'] is! int ||
        (raw['version'] as int) < 0 ||
        !{
          'AT_LOCATION',
          'IN_TRANSIT',
          'LOCATION_UNKNOWN',
        }.contains(raw['locationState']) ||
        !{'EMPTY', 'LOADED', 'UNKNOWN'}.contains(raw['loadState']) ||
        !{
          'IN_SERVICE',
          'OUT_OF_SERVICE',
        }.contains(raw['serviceabilityState']) ||
        !{'COUPLED', 'UNCOUPLED'}.contains(raw['couplingState'])) {
      throw const FormatException('Invalid Yard Trailer projection');
    }
    yardUuid(raw['trailerId']);
    yardCustomer(raw['designatedCustomer']);
    if (raw['lastKnownLocationId'] != null) {
      yardUuid(raw['lastKnownLocationId']);
    }
    if (raw['lastSeenAt'] != null) DateTime.parse(raw['lastSeenAt'] as String);
    final location = raw['currentLocation'];
    if (location != null) {
      if (location is! Map ||
          location.length != 7 ||
          !location.keys.every(
            {
              'id',
              'name',
              'type',
              'siteId',
              'siteName',
              'active',
              'siteActive',
            }.contains,
          ) ||
          location['name'] is! String ||
          location['siteName'] is! String ||
          !yardLocationTypes.contains(location['type']) ||
          location['active'] is! bool ||
          location['siteActive'] is! bool) {
        throw const FormatException('Invalid recorded location');
      }
      yardUuid(location['id']);
      yardUuid(location['siteId']);
    }
    if ((raw['locationState'] == 'AT_LOCATION') != (location != null)) {
      throw const FormatException('Invalid physical location state');
    }
    final data = jsonDecode(jsonEncode(raw)) as Map<String, dynamic>;
    if (data['currentLocation'] != null) {
      data['currentLocation'] = Map<String, dynamic>.unmodifiable(
        data['currentLocation'] as Map<String, dynamic>,
      );
    }
    if (data['designatedCustomer'] != null) {
      data['designatedCustomer'] = Map<String, dynamic>.unmodifiable(
        data['designatedCustomer'] as Map<String, dynamic>,
      );
    }
    return YardTrailer._(Map.unmodifiable(data));
  }
}
