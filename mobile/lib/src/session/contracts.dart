class Membership {
  Membership(this.tenantId, this.name, this.capabilities);
  final String tenantId;
  final String name;
  final Set<String> capabilities;
  bool get road => capabilities.contains('TRIP_VIEW_ASSIGNED');
  bool get yard =>
      capabilities.contains('YARD_MOVE_EXECUTE') ||
      capabilities.contains('YARD_CHECK_EXECUTE');
  Map<String, dynamic> toJson() => {
    'tenantId': tenantId,
    'name': name,
    'capabilities': capabilities.toList(),
  };
  factory Membership.cached(Map<String, dynamic> j) => Membership(
    j['tenantId'] as String,
    j['name'] as String,
    Set<String>.from(j['capabilities'] as List),
  );
}

class UserContext {
  UserContext(this.userId, this.memberships);
  final String userId;
  final List<Membership> memberships;
  factory UserContext.fromJson(Map<String, dynamic> j) => UserContext(
    j['userId'] as String,
    (j['memberships'] as List)
        .cast<Map<String, dynamic>>()
        .where((m) => m['status'] == 'ACTIVE')
        .map(
          (m) => Membership(
            m['tenantId'] as String,
            m['tenantName'] as String,
            Set<String>.from(m['capabilities'] as List),
          ),
        )
        .where((m) => m.road || m.yard)
        .toList(),
  );
}

class DriverProfile {
  DriverProfile(this.id, this.name);
  final String id;
  final String name;
  factory DriverProfile.fromJson(Map<String, dynamic> j) =>
      DriverProfile(j['id'] as String, j['name'] as String);
  Map<String, dynamic> toJson() => {'id': id, 'name': name};
}

class WorkTrip {
  WorkTrip(this.data);
  final Map<String, dynamic> data;
  String get id => data['id'] as String;
  String get status => data['status'] as String;
  List<Map<String, dynamic>> get stops =>
      (data['stops'] as List).cast<Map<String, dynamic>>();
  Map<String, dynamic>? get plannedTrailer =>
      data['plannedTrailer'] as Map<String, dynamic>?;
}

class SessionSnapshot {
  SessionSnapshot(
    this.firebaseUid,
    this.userId,
    this.membership,
    this.driver,
    this.refreshedAt,
    this.trips,
  );
  final String firebaseUid;
  final String userId;
  final Membership membership;
  final DriverProfile? driver;
  final DateTime refreshedAt;
  final List<WorkTrip> trips;
}
