// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $LocalSessionContextsTable extends LocalSessionContexts
    with TableInfo<$LocalSessionContextsTable, LocalSessionContext> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalSessionContextsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _firebaseUidMeta = const VerificationMeta(
    'firebaseUid',
  );
  @override
  late final GeneratedColumn<String> firebaseUid = GeneratedColumn<String>(
    'firebase_uid',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _userIdMeta = const VerificationMeta('userId');
  @override
  late final GeneratedColumn<String> userId = GeneratedColumn<String>(
    'user_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _membershipJsonMeta = const VerificationMeta(
    'membershipJson',
  );
  @override
  late final GeneratedColumn<String> membershipJson = GeneratedColumn<String>(
    'membership_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _driverJsonMeta = const VerificationMeta(
    'driverJson',
  );
  @override
  late final GeneratedColumn<String> driverJson = GeneratedColumn<String>(
    'driver_json',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _refreshedAtMeta = const VerificationMeta(
    'refreshedAt',
  );
  @override
  late final GeneratedColumn<DateTime> refreshedAt = GeneratedColumn<DateTime>(
    'refreshed_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    firebaseUid,
    userId,
    tenantId,
    membershipJson,
    driverJson,
    refreshedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_session_context';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalSessionContext> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('firebase_uid')) {
      context.handle(
        _firebaseUidMeta,
        firebaseUid.isAcceptableOrUnknown(
          data['firebase_uid']!,
          _firebaseUidMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_firebaseUidMeta);
    }
    if (data.containsKey('user_id')) {
      context.handle(
        _userIdMeta,
        userId.isAcceptableOrUnknown(data['user_id']!, _userIdMeta),
      );
    } else if (isInserting) {
      context.missing(_userIdMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('membership_json')) {
      context.handle(
        _membershipJsonMeta,
        membershipJson.isAcceptableOrUnknown(
          data['membership_json']!,
          _membershipJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_membershipJsonMeta);
    }
    if (data.containsKey('driver_json')) {
      context.handle(
        _driverJsonMeta,
        driverJson.isAcceptableOrUnknown(data['driver_json']!, _driverJsonMeta),
      );
    }
    if (data.containsKey('refreshed_at')) {
      context.handle(
        _refreshedAtMeta,
        refreshedAt.isAcceptableOrUnknown(
          data['refreshed_at']!,
          _refreshedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_refreshedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {userId, tenantId};
  @override
  LocalSessionContext map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalSessionContext(
      firebaseUid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}firebase_uid'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      membershipJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}membership_json'],
      )!,
      driverJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}driver_json'],
      ),
      refreshedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}refreshed_at'],
      )!,
    );
  }

  @override
  $LocalSessionContextsTable createAlias(String alias) {
    return $LocalSessionContextsTable(attachedDatabase, alias);
  }
}

class LocalSessionContext extends DataClass
    implements Insertable<LocalSessionContext> {
  final String firebaseUid;
  final String userId;
  final String tenantId;
  final String membershipJson;
  final String? driverJson;
  final DateTime refreshedAt;
  const LocalSessionContext({
    required this.firebaseUid,
    required this.userId,
    required this.tenantId,
    required this.membershipJson,
    this.driverJson,
    required this.refreshedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['firebase_uid'] = Variable<String>(firebaseUid);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['membership_json'] = Variable<String>(membershipJson);
    if (!nullToAbsent || driverJson != null) {
      map['driver_json'] = Variable<String>(driverJson);
    }
    map['refreshed_at'] = Variable<DateTime>(refreshedAt);
    return map;
  }

  LocalSessionContextsCompanion toCompanion(bool nullToAbsent) {
    return LocalSessionContextsCompanion(
      firebaseUid: Value(firebaseUid),
      userId: Value(userId),
      tenantId: Value(tenantId),
      membershipJson: Value(membershipJson),
      driverJson: driverJson == null && nullToAbsent
          ? const Value.absent()
          : Value(driverJson),
      refreshedAt: Value(refreshedAt),
    );
  }

  factory LocalSessionContext.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalSessionContext(
      firebaseUid: serializer.fromJson<String>(json['firebaseUid']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      membershipJson: serializer.fromJson<String>(json['membershipJson']),
      driverJson: serializer.fromJson<String?>(json['driverJson']),
      refreshedAt: serializer.fromJson<DateTime>(json['refreshedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'firebaseUid': serializer.toJson<String>(firebaseUid),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'membershipJson': serializer.toJson<String>(membershipJson),
      'driverJson': serializer.toJson<String?>(driverJson),
      'refreshedAt': serializer.toJson<DateTime>(refreshedAt),
    };
  }

  LocalSessionContext copyWith({
    String? firebaseUid,
    String? userId,
    String? tenantId,
    String? membershipJson,
    Value<String?> driverJson = const Value.absent(),
    DateTime? refreshedAt,
  }) => LocalSessionContext(
    firebaseUid: firebaseUid ?? this.firebaseUid,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    membershipJson: membershipJson ?? this.membershipJson,
    driverJson: driverJson.present ? driverJson.value : this.driverJson,
    refreshedAt: refreshedAt ?? this.refreshedAt,
  );
  LocalSessionContext copyWithCompanion(LocalSessionContextsCompanion data) {
    return LocalSessionContext(
      firebaseUid: data.firebaseUid.present
          ? data.firebaseUid.value
          : this.firebaseUid,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      membershipJson: data.membershipJson.present
          ? data.membershipJson.value
          : this.membershipJson,
      driverJson: data.driverJson.present
          ? data.driverJson.value
          : this.driverJson,
      refreshedAt: data.refreshedAt.present
          ? data.refreshedAt.value
          : this.refreshedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalSessionContext(')
          ..write('firebaseUid: $firebaseUid, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('membershipJson: $membershipJson, ')
          ..write('driverJson: $driverJson, ')
          ..write('refreshedAt: $refreshedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    firebaseUid,
    userId,
    tenantId,
    membershipJson,
    driverJson,
    refreshedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalSessionContext &&
          other.firebaseUid == this.firebaseUid &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.membershipJson == this.membershipJson &&
          other.driverJson == this.driverJson &&
          other.refreshedAt == this.refreshedAt);
}

class LocalSessionContextsCompanion
    extends UpdateCompanion<LocalSessionContext> {
  final Value<String> firebaseUid;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> membershipJson;
  final Value<String?> driverJson;
  final Value<DateTime> refreshedAt;
  final Value<int> rowid;
  const LocalSessionContextsCompanion({
    this.firebaseUid = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.membershipJson = const Value.absent(),
    this.driverJson = const Value.absent(),
    this.refreshedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalSessionContextsCompanion.insert({
    required String firebaseUid,
    required String userId,
    required String tenantId,
    required String membershipJson,
    this.driverJson = const Value.absent(),
    required DateTime refreshedAt,
    this.rowid = const Value.absent(),
  }) : firebaseUid = Value(firebaseUid),
       userId = Value(userId),
       tenantId = Value(tenantId),
       membershipJson = Value(membershipJson),
       refreshedAt = Value(refreshedAt);
  static Insertable<LocalSessionContext> custom({
    Expression<String>? firebaseUid,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? membershipJson,
    Expression<String>? driverJson,
    Expression<DateTime>? refreshedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (firebaseUid != null) 'firebase_uid': firebaseUid,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (membershipJson != null) 'membership_json': membershipJson,
      if (driverJson != null) 'driver_json': driverJson,
      if (refreshedAt != null) 'refreshed_at': refreshedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalSessionContextsCompanion copyWith({
    Value<String>? firebaseUid,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? membershipJson,
    Value<String?>? driverJson,
    Value<DateTime>? refreshedAt,
    Value<int>? rowid,
  }) {
    return LocalSessionContextsCompanion(
      firebaseUid: firebaseUid ?? this.firebaseUid,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      membershipJson: membershipJson ?? this.membershipJson,
      driverJson: driverJson ?? this.driverJson,
      refreshedAt: refreshedAt ?? this.refreshedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (firebaseUid.present) {
      map['firebase_uid'] = Variable<String>(firebaseUid.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (membershipJson.present) {
      map['membership_json'] = Variable<String>(membershipJson.value);
    }
    if (driverJson.present) {
      map['driver_json'] = Variable<String>(driverJson.value);
    }
    if (refreshedAt.present) {
      map['refreshed_at'] = Variable<DateTime>(refreshedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalSessionContextsCompanion(')
          ..write('firebaseUid: $firebaseUid, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('membershipJson: $membershipJson, ')
          ..write('driverJson: $driverJson, ')
          ..write('refreshedAt: $refreshedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CachedTripsTable extends CachedTrips
    with TableInfo<$CachedTripsTable, CachedTrip> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CachedTripsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _userIdMeta = const VerificationMeta('userId');
  @override
  late final GeneratedColumn<String> userId = GeneratedColumn<String>(
    'user_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _serverVersionMeta = const VerificationMeta(
    'serverVersion',
  );
  @override
  late final GeneratedColumn<int> serverVersion = GeneratedColumn<int>(
    'server_version',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _projectionJsonMeta = const VerificationMeta(
    'projectionJson',
  );
  @override
  late final GeneratedColumn<String> projectionJson = GeneratedColumn<String>(
    'projection_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _refreshedAtMeta = const VerificationMeta(
    'refreshedAt',
  );
  @override
  late final GeneratedColumn<DateTime> refreshedAt = GeneratedColumn<DateTime>(
    'refreshed_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    userId,
    tenantId,
    id,
    serverVersion,
    status,
    projectionJson,
    refreshedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cached_trips';
  @override
  VerificationContext validateIntegrity(
    Insertable<CachedTrip> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('user_id')) {
      context.handle(
        _userIdMeta,
        userId.isAcceptableOrUnknown(data['user_id']!, _userIdMeta),
      );
    } else if (isInserting) {
      context.missing(_userIdMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('server_version')) {
      context.handle(
        _serverVersionMeta,
        serverVersion.isAcceptableOrUnknown(
          data['server_version']!,
          _serverVersionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_serverVersionMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    if (data.containsKey('projection_json')) {
      context.handle(
        _projectionJsonMeta,
        projectionJson.isAcceptableOrUnknown(
          data['projection_json']!,
          _projectionJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_projectionJsonMeta);
    }
    if (data.containsKey('refreshed_at')) {
      context.handle(
        _refreshedAtMeta,
        refreshedAt.isAcceptableOrUnknown(
          data['refreshed_at']!,
          _refreshedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_refreshedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {userId, tenantId, id};
  @override
  CachedTrip map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CachedTrip(
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      serverVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}server_version'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      projectionJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}projection_json'],
      )!,
      refreshedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}refreshed_at'],
      )!,
    );
  }

  @override
  $CachedTripsTable createAlias(String alias) {
    return $CachedTripsTable(attachedDatabase, alias);
  }
}

class CachedTrip extends DataClass implements Insertable<CachedTrip> {
  final String userId;
  final String tenantId;
  final String id;
  final int serverVersion;
  final String status;
  final String projectionJson;
  final DateTime refreshedAt;
  const CachedTrip({
    required this.userId,
    required this.tenantId,
    required this.id,
    required this.serverVersion,
    required this.status,
    required this.projectionJson,
    required this.refreshedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['id'] = Variable<String>(id);
    map['server_version'] = Variable<int>(serverVersion);
    map['status'] = Variable<String>(status);
    map['projection_json'] = Variable<String>(projectionJson);
    map['refreshed_at'] = Variable<DateTime>(refreshedAt);
    return map;
  }

  CachedTripsCompanion toCompanion(bool nullToAbsent) {
    return CachedTripsCompanion(
      userId: Value(userId),
      tenantId: Value(tenantId),
      id: Value(id),
      serverVersion: Value(serverVersion),
      status: Value(status),
      projectionJson: Value(projectionJson),
      refreshedAt: Value(refreshedAt),
    );
  }

  factory CachedTrip.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CachedTrip(
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      id: serializer.fromJson<String>(json['id']),
      serverVersion: serializer.fromJson<int>(json['serverVersion']),
      status: serializer.fromJson<String>(json['status']),
      projectionJson: serializer.fromJson<String>(json['projectionJson']),
      refreshedAt: serializer.fromJson<DateTime>(json['refreshedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'id': serializer.toJson<String>(id),
      'serverVersion': serializer.toJson<int>(serverVersion),
      'status': serializer.toJson<String>(status),
      'projectionJson': serializer.toJson<String>(projectionJson),
      'refreshedAt': serializer.toJson<DateTime>(refreshedAt),
    };
  }

  CachedTrip copyWith({
    String? userId,
    String? tenantId,
    String? id,
    int? serverVersion,
    String? status,
    String? projectionJson,
    DateTime? refreshedAt,
  }) => CachedTrip(
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    id: id ?? this.id,
    serverVersion: serverVersion ?? this.serverVersion,
    status: status ?? this.status,
    projectionJson: projectionJson ?? this.projectionJson,
    refreshedAt: refreshedAt ?? this.refreshedAt,
  );
  CachedTrip copyWithCompanion(CachedTripsCompanion data) {
    return CachedTrip(
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      id: data.id.present ? data.id.value : this.id,
      serverVersion: data.serverVersion.present
          ? data.serverVersion.value
          : this.serverVersion,
      status: data.status.present ? data.status.value : this.status,
      projectionJson: data.projectionJson.present
          ? data.projectionJson.value
          : this.projectionJson,
      refreshedAt: data.refreshedAt.present
          ? data.refreshedAt.value
          : this.refreshedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CachedTrip(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('id: $id, ')
          ..write('serverVersion: $serverVersion, ')
          ..write('status: $status, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('refreshedAt: $refreshedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    userId,
    tenantId,
    id,
    serverVersion,
    status,
    projectionJson,
    refreshedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CachedTrip &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.id == this.id &&
          other.serverVersion == this.serverVersion &&
          other.status == this.status &&
          other.projectionJson == this.projectionJson &&
          other.refreshedAt == this.refreshedAt);
}

class CachedTripsCompanion extends UpdateCompanion<CachedTrip> {
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> id;
  final Value<int> serverVersion;
  final Value<String> status;
  final Value<String> projectionJson;
  final Value<DateTime> refreshedAt;
  final Value<int> rowid;
  const CachedTripsCompanion({
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.id = const Value.absent(),
    this.serverVersion = const Value.absent(),
    this.status = const Value.absent(),
    this.projectionJson = const Value.absent(),
    this.refreshedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CachedTripsCompanion.insert({
    required String userId,
    required String tenantId,
    required String id,
    required int serverVersion,
    required String status,
    required String projectionJson,
    required DateTime refreshedAt,
    this.rowid = const Value.absent(),
  }) : userId = Value(userId),
       tenantId = Value(tenantId),
       id = Value(id),
       serverVersion = Value(serverVersion),
       status = Value(status),
       projectionJson = Value(projectionJson),
       refreshedAt = Value(refreshedAt);
  static Insertable<CachedTrip> custom({
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? id,
    Expression<int>? serverVersion,
    Expression<String>? status,
    Expression<String>? projectionJson,
    Expression<DateTime>? refreshedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (id != null) 'id': id,
      if (serverVersion != null) 'server_version': serverVersion,
      if (status != null) 'status': status,
      if (projectionJson != null) 'projection_json': projectionJson,
      if (refreshedAt != null) 'refreshed_at': refreshedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CachedTripsCompanion copyWith({
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? id,
    Value<int>? serverVersion,
    Value<String>? status,
    Value<String>? projectionJson,
    Value<DateTime>? refreshedAt,
    Value<int>? rowid,
  }) {
    return CachedTripsCompanion(
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      id: id ?? this.id,
      serverVersion: serverVersion ?? this.serverVersion,
      status: status ?? this.status,
      projectionJson: projectionJson ?? this.projectionJson,
      refreshedAt: refreshedAt ?? this.refreshedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (serverVersion.present) {
      map['server_version'] = Variable<int>(serverVersion.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (projectionJson.present) {
      map['projection_json'] = Variable<String>(projectionJson.value);
    }
    if (refreshedAt.present) {
      map['refreshed_at'] = Variable<DateTime>(refreshedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CachedTripsCompanion(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('id: $id, ')
          ..write('serverVersion: $serverVersion, ')
          ..write('status: $status, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('refreshedAt: $refreshedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CachedStopsTable extends CachedStops
    with TableInfo<$CachedStopsTable, CachedStop> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CachedStopsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _userIdMeta = const VerificationMeta('userId');
  @override
  late final GeneratedColumn<String> userId = GeneratedColumn<String>(
    'user_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tripIdMeta = const VerificationMeta('tripId');
  @override
  late final GeneratedColumn<String> tripId = GeneratedColumn<String>(
    'trip_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sequenceNumberMeta = const VerificationMeta(
    'sequenceNumber',
  );
  @override
  late final GeneratedColumn<int> sequenceNumber = GeneratedColumn<int>(
    'sequence_number',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _projectionJsonMeta = const VerificationMeta(
    'projectionJson',
  );
  @override
  late final GeneratedColumn<String> projectionJson = GeneratedColumn<String>(
    'projection_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    userId,
    tenantId,
    id,
    tripId,
    sequenceNumber,
    projectionJson,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cached_stops';
  @override
  VerificationContext validateIntegrity(
    Insertable<CachedStop> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('user_id')) {
      context.handle(
        _userIdMeta,
        userId.isAcceptableOrUnknown(data['user_id']!, _userIdMeta),
      );
    } else if (isInserting) {
      context.missing(_userIdMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('trip_id')) {
      context.handle(
        _tripIdMeta,
        tripId.isAcceptableOrUnknown(data['trip_id']!, _tripIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tripIdMeta);
    }
    if (data.containsKey('sequence_number')) {
      context.handle(
        _sequenceNumberMeta,
        sequenceNumber.isAcceptableOrUnknown(
          data['sequence_number']!,
          _sequenceNumberMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sequenceNumberMeta);
    }
    if (data.containsKey('projection_json')) {
      context.handle(
        _projectionJsonMeta,
        projectionJson.isAcceptableOrUnknown(
          data['projection_json']!,
          _projectionJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_projectionJsonMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {userId, tenantId, id};
  @override
  CachedStop map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CachedStop(
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      tripId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trip_id'],
      )!,
      sequenceNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sequence_number'],
      )!,
      projectionJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}projection_json'],
      )!,
    );
  }

  @override
  $CachedStopsTable createAlias(String alias) {
    return $CachedStopsTable(attachedDatabase, alias);
  }
}

class CachedStop extends DataClass implements Insertable<CachedStop> {
  final String userId;
  final String tenantId;
  final String id;
  final String tripId;
  final int sequenceNumber;
  final String projectionJson;
  const CachedStop({
    required this.userId,
    required this.tenantId,
    required this.id,
    required this.tripId,
    required this.sequenceNumber,
    required this.projectionJson,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['id'] = Variable<String>(id);
    map['trip_id'] = Variable<String>(tripId);
    map['sequence_number'] = Variable<int>(sequenceNumber);
    map['projection_json'] = Variable<String>(projectionJson);
    return map;
  }

  CachedStopsCompanion toCompanion(bool nullToAbsent) {
    return CachedStopsCompanion(
      userId: Value(userId),
      tenantId: Value(tenantId),
      id: Value(id),
      tripId: Value(tripId),
      sequenceNumber: Value(sequenceNumber),
      projectionJson: Value(projectionJson),
    );
  }

  factory CachedStop.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CachedStop(
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      id: serializer.fromJson<String>(json['id']),
      tripId: serializer.fromJson<String>(json['tripId']),
      sequenceNumber: serializer.fromJson<int>(json['sequenceNumber']),
      projectionJson: serializer.fromJson<String>(json['projectionJson']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'id': serializer.toJson<String>(id),
      'tripId': serializer.toJson<String>(tripId),
      'sequenceNumber': serializer.toJson<int>(sequenceNumber),
      'projectionJson': serializer.toJson<String>(projectionJson),
    };
  }

  CachedStop copyWith({
    String? userId,
    String? tenantId,
    String? id,
    String? tripId,
    int? sequenceNumber,
    String? projectionJson,
  }) => CachedStop(
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    id: id ?? this.id,
    tripId: tripId ?? this.tripId,
    sequenceNumber: sequenceNumber ?? this.sequenceNumber,
    projectionJson: projectionJson ?? this.projectionJson,
  );
  CachedStop copyWithCompanion(CachedStopsCompanion data) {
    return CachedStop(
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      id: data.id.present ? data.id.value : this.id,
      tripId: data.tripId.present ? data.tripId.value : this.tripId,
      sequenceNumber: data.sequenceNumber.present
          ? data.sequenceNumber.value
          : this.sequenceNumber,
      projectionJson: data.projectionJson.present
          ? data.projectionJson.value
          : this.projectionJson,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CachedStop(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('id: $id, ')
          ..write('tripId: $tripId, ')
          ..write('sequenceNumber: $sequenceNumber, ')
          ..write('projectionJson: $projectionJson')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(userId, tenantId, id, tripId, sequenceNumber, projectionJson);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CachedStop &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.id == this.id &&
          other.tripId == this.tripId &&
          other.sequenceNumber == this.sequenceNumber &&
          other.projectionJson == this.projectionJson);
}

class CachedStopsCompanion extends UpdateCompanion<CachedStop> {
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> id;
  final Value<String> tripId;
  final Value<int> sequenceNumber;
  final Value<String> projectionJson;
  final Value<int> rowid;
  const CachedStopsCompanion({
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.id = const Value.absent(),
    this.tripId = const Value.absent(),
    this.sequenceNumber = const Value.absent(),
    this.projectionJson = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CachedStopsCompanion.insert({
    required String userId,
    required String tenantId,
    required String id,
    required String tripId,
    required int sequenceNumber,
    required String projectionJson,
    this.rowid = const Value.absent(),
  }) : userId = Value(userId),
       tenantId = Value(tenantId),
       id = Value(id),
       tripId = Value(tripId),
       sequenceNumber = Value(sequenceNumber),
       projectionJson = Value(projectionJson);
  static Insertable<CachedStop> custom({
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? id,
    Expression<String>? tripId,
    Expression<int>? sequenceNumber,
    Expression<String>? projectionJson,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (id != null) 'id': id,
      if (tripId != null) 'trip_id': tripId,
      if (sequenceNumber != null) 'sequence_number': sequenceNumber,
      if (projectionJson != null) 'projection_json': projectionJson,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CachedStopsCompanion copyWith({
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? id,
    Value<String>? tripId,
    Value<int>? sequenceNumber,
    Value<String>? projectionJson,
    Value<int>? rowid,
  }) {
    return CachedStopsCompanion(
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      id: id ?? this.id,
      tripId: tripId ?? this.tripId,
      sequenceNumber: sequenceNumber ?? this.sequenceNumber,
      projectionJson: projectionJson ?? this.projectionJson,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (tripId.present) {
      map['trip_id'] = Variable<String>(tripId.value);
    }
    if (sequenceNumber.present) {
      map['sequence_number'] = Variable<int>(sequenceNumber.value);
    }
    if (projectionJson.present) {
      map['projection_json'] = Variable<String>(projectionJson.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CachedStopsCompanion(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('id: $id, ')
          ..write('tripId: $tripId, ')
          ..write('sequenceNumber: $sequenceNumber, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CachedTrailersTable extends CachedTrailers
    with TableInfo<$CachedTrailersTable, CachedTrailer> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CachedTrailersTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _userIdMeta = const VerificationMeta('userId');
  @override
  late final GeneratedColumn<String> userId = GeneratedColumn<String>(
    'user_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _serverVersionMeta = const VerificationMeta(
    'serverVersion',
  );
  @override
  late final GeneratedColumn<int> serverVersion = GeneratedColumn<int>(
    'server_version',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _projectionJsonMeta = const VerificationMeta(
    'projectionJson',
  );
  @override
  late final GeneratedColumn<String> projectionJson = GeneratedColumn<String>(
    'projection_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _refreshedAtMeta = const VerificationMeta(
    'refreshedAt',
  );
  @override
  late final GeneratedColumn<DateTime> refreshedAt = GeneratedColumn<DateTime>(
    'refreshed_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    userId,
    tenantId,
    id,
    serverVersion,
    projectionJson,
    refreshedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cached_trailers';
  @override
  VerificationContext validateIntegrity(
    Insertable<CachedTrailer> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('user_id')) {
      context.handle(
        _userIdMeta,
        userId.isAcceptableOrUnknown(data['user_id']!, _userIdMeta),
      );
    } else if (isInserting) {
      context.missing(_userIdMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('server_version')) {
      context.handle(
        _serverVersionMeta,
        serverVersion.isAcceptableOrUnknown(
          data['server_version']!,
          _serverVersionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_serverVersionMeta);
    }
    if (data.containsKey('projection_json')) {
      context.handle(
        _projectionJsonMeta,
        projectionJson.isAcceptableOrUnknown(
          data['projection_json']!,
          _projectionJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_projectionJsonMeta);
    }
    if (data.containsKey('refreshed_at')) {
      context.handle(
        _refreshedAtMeta,
        refreshedAt.isAcceptableOrUnknown(
          data['refreshed_at']!,
          _refreshedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_refreshedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {userId, tenantId, id};
  @override
  CachedTrailer map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CachedTrailer(
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      serverVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}server_version'],
      )!,
      projectionJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}projection_json'],
      )!,
      refreshedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}refreshed_at'],
      )!,
    );
  }

  @override
  $CachedTrailersTable createAlias(String alias) {
    return $CachedTrailersTable(attachedDatabase, alias);
  }
}

class CachedTrailer extends DataClass implements Insertable<CachedTrailer> {
  final String userId;
  final String tenantId;
  final String id;
  final int serverVersion;
  final String projectionJson;
  final DateTime refreshedAt;
  const CachedTrailer({
    required this.userId,
    required this.tenantId,
    required this.id,
    required this.serverVersion,
    required this.projectionJson,
    required this.refreshedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['id'] = Variable<String>(id);
    map['server_version'] = Variable<int>(serverVersion);
    map['projection_json'] = Variable<String>(projectionJson);
    map['refreshed_at'] = Variable<DateTime>(refreshedAt);
    return map;
  }

  CachedTrailersCompanion toCompanion(bool nullToAbsent) {
    return CachedTrailersCompanion(
      userId: Value(userId),
      tenantId: Value(tenantId),
      id: Value(id),
      serverVersion: Value(serverVersion),
      projectionJson: Value(projectionJson),
      refreshedAt: Value(refreshedAt),
    );
  }

  factory CachedTrailer.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CachedTrailer(
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      id: serializer.fromJson<String>(json['id']),
      serverVersion: serializer.fromJson<int>(json['serverVersion']),
      projectionJson: serializer.fromJson<String>(json['projectionJson']),
      refreshedAt: serializer.fromJson<DateTime>(json['refreshedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'id': serializer.toJson<String>(id),
      'serverVersion': serializer.toJson<int>(serverVersion),
      'projectionJson': serializer.toJson<String>(projectionJson),
      'refreshedAt': serializer.toJson<DateTime>(refreshedAt),
    };
  }

  CachedTrailer copyWith({
    String? userId,
    String? tenantId,
    String? id,
    int? serverVersion,
    String? projectionJson,
    DateTime? refreshedAt,
  }) => CachedTrailer(
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    id: id ?? this.id,
    serverVersion: serverVersion ?? this.serverVersion,
    projectionJson: projectionJson ?? this.projectionJson,
    refreshedAt: refreshedAt ?? this.refreshedAt,
  );
  CachedTrailer copyWithCompanion(CachedTrailersCompanion data) {
    return CachedTrailer(
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      id: data.id.present ? data.id.value : this.id,
      serverVersion: data.serverVersion.present
          ? data.serverVersion.value
          : this.serverVersion,
      projectionJson: data.projectionJson.present
          ? data.projectionJson.value
          : this.projectionJson,
      refreshedAt: data.refreshedAt.present
          ? data.refreshedAt.value
          : this.refreshedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CachedTrailer(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('id: $id, ')
          ..write('serverVersion: $serverVersion, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('refreshedAt: $refreshedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    userId,
    tenantId,
    id,
    serverVersion,
    projectionJson,
    refreshedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CachedTrailer &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.id == this.id &&
          other.serverVersion == this.serverVersion &&
          other.projectionJson == this.projectionJson &&
          other.refreshedAt == this.refreshedAt);
}

class CachedTrailersCompanion extends UpdateCompanion<CachedTrailer> {
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> id;
  final Value<int> serverVersion;
  final Value<String> projectionJson;
  final Value<DateTime> refreshedAt;
  final Value<int> rowid;
  const CachedTrailersCompanion({
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.id = const Value.absent(),
    this.serverVersion = const Value.absent(),
    this.projectionJson = const Value.absent(),
    this.refreshedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CachedTrailersCompanion.insert({
    required String userId,
    required String tenantId,
    required String id,
    required int serverVersion,
    required String projectionJson,
    required DateTime refreshedAt,
    this.rowid = const Value.absent(),
  }) : userId = Value(userId),
       tenantId = Value(tenantId),
       id = Value(id),
       serverVersion = Value(serverVersion),
       projectionJson = Value(projectionJson),
       refreshedAt = Value(refreshedAt);
  static Insertable<CachedTrailer> custom({
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? id,
    Expression<int>? serverVersion,
    Expression<String>? projectionJson,
    Expression<DateTime>? refreshedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (id != null) 'id': id,
      if (serverVersion != null) 'server_version': serverVersion,
      if (projectionJson != null) 'projection_json': projectionJson,
      if (refreshedAt != null) 'refreshed_at': refreshedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CachedTrailersCompanion copyWith({
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? id,
    Value<int>? serverVersion,
    Value<String>? projectionJson,
    Value<DateTime>? refreshedAt,
    Value<int>? rowid,
  }) {
    return CachedTrailersCompanion(
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      id: id ?? this.id,
      serverVersion: serverVersion ?? this.serverVersion,
      projectionJson: projectionJson ?? this.projectionJson,
      refreshedAt: refreshedAt ?? this.refreshedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (serverVersion.present) {
      map['server_version'] = Variable<int>(serverVersion.value);
    }
    if (projectionJson.present) {
      map['projection_json'] = Variable<String>(projectionJson.value);
    }
    if (refreshedAt.present) {
      map['refreshed_at'] = Variable<DateTime>(refreshedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CachedTrailersCompanion(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('id: $id, ')
          ..write('serverVersion: $serverVersion, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('refreshedAt: $refreshedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  late final $LocalSessionContextsTable localSessionContexts =
      $LocalSessionContextsTable(this);
  late final $CachedTripsTable cachedTrips = $CachedTripsTable(this);
  late final $CachedStopsTable cachedStops = $CachedStopsTable(this);
  late final $CachedTrailersTable cachedTrailers = $CachedTrailersTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    localSessionContexts,
    cachedTrips,
    cachedStops,
    cachedTrailers,
  ];
}
