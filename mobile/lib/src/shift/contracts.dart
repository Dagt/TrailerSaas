class DriverShift {
  DriverShift(this.id, this.state, this.onDutyAt, this.offDutyAt);
  final String id, state;
  final DateTime onDutyAt;
  final DateTime? offDutyAt;
  factory DriverShift.fromJson(Map<String, dynamic> j) => DriverShift(
    j['id'] as String,
    j['state'] as String,
    DateTime.parse(j['onDutyAt'] as String),
    j['offDutyAt'] == null ? null : DateTime.parse(j['offDutyAt'] as String),
  );
  Map<String, dynamic> toJson() => {
    'id': id,
    'state': state,
    'onDutyAt': onDutyAt.toUtc().toIso8601String(),
    'offDutyAt': offDutyAt?.toUtc().toIso8601String(),
  };
}

class DriverInspection {
  DriverInspection(this.data);
  final Map<String, dynamic> data;
  String get id => data['id'] as String;
  String get type => data['inspectionType'] as String;
  String get tractorId => data['tractorId'] as String;
  String? get tripId => data['tripId'] as String?;
}

class ShiftTractor {
  ShiftTractor(this.id, this.number, this.tripId);
  final String id, number;
  final String? tripId;
  factory ShiftTractor.fromJson(Map<String, dynamic> j) => ShiftTractor(
    j['tractorId'] as String,
    j['operationalNumber'] as String,
    j['tripId'] as String?,
  );
  Map<String, dynamic> toJson() => {
    'tractorId': id,
    'operationalNumber': number,
    'tripId': tripId,
  };
}

class DriverShiftHome {
  DriverShiftHome(
    this.current,
    this.inspections,
    this.tractors,
    this.usedTractors,
    this.offDutyBlockers,
  );
  final DriverShift? current;
  final List<DriverInspection> inspections;
  final List<ShiftTractor> tractors, usedTractors;
  final List<String> offDutyBlockers;
  factory DriverShiftHome.fromJson(Map<String, dynamic> j) => DriverShiftHome(
    j['currentShift'] == null
        ? null
        : DriverShift.fromJson(j['currentShift'] as Map<String, dynamic>),
    (j['inspections'] as List)
        .map((i) => DriverInspection(i as Map<String, dynamic>))
        .toList(),
    (j['authorizedTractors'] as List)
        .map((t) => ShiftTractor.fromJson(t as Map<String, dynamic>))
        .toList(),
    (j['usedTractors'] as List? ?? [])
        .map((t) => ShiftTractor.fromJson(t as Map<String, dynamic>))
        .toList(),
    List<String>.from(j['offDutyBlockers'] as List),
  );
  Map<String, dynamic> toJson() => {
    'currentShift': current?.toJson(),
    'inspections': inspections.map((i) => i.data).toList(),
    'authorizedTractors': tractors.map((t) => t.toJson()).toList(),
    'usedTractors': usedTractors.map((t) => t.toJson()).toList(),
    'offDutyBlockers': offDutyBlockers,
  };
}
