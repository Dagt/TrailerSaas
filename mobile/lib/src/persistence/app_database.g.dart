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

class $LocalCommandsTable extends LocalCommands
    with TableInfo<$LocalCommandsTable, LocalCommand> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalCommandsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _clientOperationIdMeta = const VerificationMeta(
    'clientOperationId',
  );
  @override
  late final GeneratedColumn<String> clientOperationId =
      GeneratedColumn<String>(
        'client_operation_id',
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
  static const VerificationMeta _commandTypeMeta = const VerificationMeta(
    'commandType',
  );
  @override
  late final GeneratedColumn<String> commandType = GeneratedColumn<String>(
    'command_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _queueSequenceMeta = const VerificationMeta(
    'queueSequence',
  );
  @override
  late final GeneratedColumn<int> queueSequence = GeneratedColumn<int>(
    'queue_sequence',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _endpointPathMeta = const VerificationMeta(
    'endpointPath',
  );
  @override
  late final GeneratedColumn<String> endpointPath = GeneratedColumn<String>(
    'endpoint_path',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _httpMethodMeta = const VerificationMeta(
    'httpMethod',
  );
  @override
  late final GeneratedColumn<String> httpMethod = GeneratedColumn<String>(
    'http_method',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _payloadJsonMeta = const VerificationMeta(
    'payloadJson',
  );
  @override
  late final GeneratedColumn<String> payloadJson = GeneratedColumn<String>(
    'payload_json',
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
  static const VerificationMeta _aggregateIdMeta = const VerificationMeta(
    'aggregateId',
  );
  @override
  late final GeneratedColumn<String> aggregateId = GeneratedColumn<String>(
    'aggregate_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _resourceKeysJsonMeta = const VerificationMeta(
    'resourceKeysJson',
  );
  @override
  late final GeneratedColumn<String> resourceKeysJson = GeneratedColumn<String>(
    'resource_keys_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _dependenciesJsonMeta = const VerificationMeta(
    'dependenciesJson',
  );
  @override
  late final GeneratedColumn<String> dependenciesJson = GeneratedColumn<String>(
    'dependencies_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _occurredAtMeta = const VerificationMeta(
    'occurredAt',
  );
  @override
  late final GeneratedColumn<DateTime> occurredAt = GeneratedColumn<DateTime>(
    'occurred_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptCountMeta = const VerificationMeta(
    'attemptCount',
  );
  @override
  late final GeneratedColumn<int> attemptCount = GeneratedColumn<int>(
    'attempt_count',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _nextRetryAtMeta = const VerificationMeta(
    'nextRetryAt',
  );
  @override
  late final GeneratedColumn<DateTime> nextRetryAt = GeneratedColumn<DateTime>(
    'next_retry_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastHttpStatusMeta = const VerificationMeta(
    'lastHttpStatus',
  );
  @override
  late final GeneratedColumn<int> lastHttpStatus = GeneratedColumn<int>(
    'last_http_status',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastErrorCodeMeta = const VerificationMeta(
    'lastErrorCode',
  );
  @override
  late final GeneratedColumn<String> lastErrorCode = GeneratedColumn<String>(
    'last_error_code',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _ackedAtMeta = const VerificationMeta(
    'ackedAt',
  );
  @override
  late final GeneratedColumn<DateTime> ackedAt = GeneratedColumn<DateTime>(
    'acked_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    clientOperationId,
    userId,
    tenantId,
    commandType,
    queueSequence,
    endpointPath,
    httpMethod,
    payloadJson,
    tripId,
    aggregateId,
    resourceKeysJson,
    dependenciesJson,
    occurredAt,
    createdAt,
    state,
    attemptCount,
    nextRetryAt,
    lastHttpStatus,
    lastErrorCode,
    ackedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_commands';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalCommand> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('client_operation_id')) {
      context.handle(
        _clientOperationIdMeta,
        clientOperationId.isAcceptableOrUnknown(
          data['client_operation_id']!,
          _clientOperationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_clientOperationIdMeta);
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
    if (data.containsKey('command_type')) {
      context.handle(
        _commandTypeMeta,
        commandType.isAcceptableOrUnknown(
          data['command_type']!,
          _commandTypeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_commandTypeMeta);
    }
    if (data.containsKey('queue_sequence')) {
      context.handle(
        _queueSequenceMeta,
        queueSequence.isAcceptableOrUnknown(
          data['queue_sequence']!,
          _queueSequenceMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_queueSequenceMeta);
    }
    if (data.containsKey('endpoint_path')) {
      context.handle(
        _endpointPathMeta,
        endpointPath.isAcceptableOrUnknown(
          data['endpoint_path']!,
          _endpointPathMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_endpointPathMeta);
    }
    if (data.containsKey('http_method')) {
      context.handle(
        _httpMethodMeta,
        httpMethod.isAcceptableOrUnknown(data['http_method']!, _httpMethodMeta),
      );
    } else if (isInserting) {
      context.missing(_httpMethodMeta);
    }
    if (data.containsKey('payload_json')) {
      context.handle(
        _payloadJsonMeta,
        payloadJson.isAcceptableOrUnknown(
          data['payload_json']!,
          _payloadJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_payloadJsonMeta);
    }
    if (data.containsKey('trip_id')) {
      context.handle(
        _tripIdMeta,
        tripId.isAcceptableOrUnknown(data['trip_id']!, _tripIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tripIdMeta);
    }
    if (data.containsKey('aggregate_id')) {
      context.handle(
        _aggregateIdMeta,
        aggregateId.isAcceptableOrUnknown(
          data['aggregate_id']!,
          _aggregateIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_aggregateIdMeta);
    }
    if (data.containsKey('resource_keys_json')) {
      context.handle(
        _resourceKeysJsonMeta,
        resourceKeysJson.isAcceptableOrUnknown(
          data['resource_keys_json']!,
          _resourceKeysJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_resourceKeysJsonMeta);
    }
    if (data.containsKey('dependencies_json')) {
      context.handle(
        _dependenciesJsonMeta,
        dependenciesJson.isAcceptableOrUnknown(
          data['dependencies_json']!,
          _dependenciesJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_dependenciesJsonMeta);
    }
    if (data.containsKey('occurred_at')) {
      context.handle(
        _occurredAtMeta,
        occurredAt.isAcceptableOrUnknown(data['occurred_at']!, _occurredAtMeta),
      );
    } else if (isInserting) {
      context.missing(_occurredAtMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('attempt_count')) {
      context.handle(
        _attemptCountMeta,
        attemptCount.isAcceptableOrUnknown(
          data['attempt_count']!,
          _attemptCountMeta,
        ),
      );
    }
    if (data.containsKey('next_retry_at')) {
      context.handle(
        _nextRetryAtMeta,
        nextRetryAt.isAcceptableOrUnknown(
          data['next_retry_at']!,
          _nextRetryAtMeta,
        ),
      );
    }
    if (data.containsKey('last_http_status')) {
      context.handle(
        _lastHttpStatusMeta,
        lastHttpStatus.isAcceptableOrUnknown(
          data['last_http_status']!,
          _lastHttpStatusMeta,
        ),
      );
    }
    if (data.containsKey('last_error_code')) {
      context.handle(
        _lastErrorCodeMeta,
        lastErrorCode.isAcceptableOrUnknown(
          data['last_error_code']!,
          _lastErrorCodeMeta,
        ),
      );
    }
    if (data.containsKey('acked_at')) {
      context.handle(
        _ackedAtMeta,
        ackedAt.isAcceptableOrUnknown(data['acked_at']!, _ackedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {clientOperationId};
  @override
  LocalCommand map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalCommand(
      clientOperationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}client_operation_id'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      commandType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}command_type'],
      )!,
      queueSequence: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}queue_sequence'],
      )!,
      endpointPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}endpoint_path'],
      )!,
      httpMethod: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}http_method'],
      )!,
      payloadJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payload_json'],
      )!,
      tripId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trip_id'],
      )!,
      aggregateId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}aggregate_id'],
      )!,
      resourceKeysJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}resource_keys_json'],
      )!,
      dependenciesJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}dependencies_json'],
      )!,
      occurredAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}occurred_at'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      attemptCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempt_count'],
      )!,
      nextRetryAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}next_retry_at'],
      ),
      lastHttpStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}last_http_status'],
      ),
      lastErrorCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_error_code'],
      ),
      ackedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}acked_at'],
      ),
    );
  }

  @override
  $LocalCommandsTable createAlias(String alias) {
    return $LocalCommandsTable(attachedDatabase, alias);
  }
}

class LocalCommand extends DataClass implements Insertable<LocalCommand> {
  final String clientOperationId;
  final String userId;
  final String tenantId;
  final String commandType;
  final int queueSequence;
  final String endpointPath;
  final String httpMethod;
  final String payloadJson;
  final String tripId;
  final String aggregateId;
  final String resourceKeysJson;
  final String dependenciesJson;
  final DateTime occurredAt;
  final DateTime createdAt;
  final String state;
  final int attemptCount;
  final DateTime? nextRetryAt;
  final int? lastHttpStatus;
  final String? lastErrorCode;
  final DateTime? ackedAt;
  const LocalCommand({
    required this.clientOperationId,
    required this.userId,
    required this.tenantId,
    required this.commandType,
    required this.queueSequence,
    required this.endpointPath,
    required this.httpMethod,
    required this.payloadJson,
    required this.tripId,
    required this.aggregateId,
    required this.resourceKeysJson,
    required this.dependenciesJson,
    required this.occurredAt,
    required this.createdAt,
    required this.state,
    required this.attemptCount,
    this.nextRetryAt,
    this.lastHttpStatus,
    this.lastErrorCode,
    this.ackedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_operation_id'] = Variable<String>(clientOperationId);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['command_type'] = Variable<String>(commandType);
    map['queue_sequence'] = Variable<int>(queueSequence);
    map['endpoint_path'] = Variable<String>(endpointPath);
    map['http_method'] = Variable<String>(httpMethod);
    map['payload_json'] = Variable<String>(payloadJson);
    map['trip_id'] = Variable<String>(tripId);
    map['aggregate_id'] = Variable<String>(aggregateId);
    map['resource_keys_json'] = Variable<String>(resourceKeysJson);
    map['dependencies_json'] = Variable<String>(dependenciesJson);
    map['occurred_at'] = Variable<DateTime>(occurredAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['state'] = Variable<String>(state);
    map['attempt_count'] = Variable<int>(attemptCount);
    if (!nullToAbsent || nextRetryAt != null) {
      map['next_retry_at'] = Variable<DateTime>(nextRetryAt);
    }
    if (!nullToAbsent || lastHttpStatus != null) {
      map['last_http_status'] = Variable<int>(lastHttpStatus);
    }
    if (!nullToAbsent || lastErrorCode != null) {
      map['last_error_code'] = Variable<String>(lastErrorCode);
    }
    if (!nullToAbsent || ackedAt != null) {
      map['acked_at'] = Variable<DateTime>(ackedAt);
    }
    return map;
  }

  LocalCommandsCompanion toCompanion(bool nullToAbsent) {
    return LocalCommandsCompanion(
      clientOperationId: Value(clientOperationId),
      userId: Value(userId),
      tenantId: Value(tenantId),
      commandType: Value(commandType),
      queueSequence: Value(queueSequence),
      endpointPath: Value(endpointPath),
      httpMethod: Value(httpMethod),
      payloadJson: Value(payloadJson),
      tripId: Value(tripId),
      aggregateId: Value(aggregateId),
      resourceKeysJson: Value(resourceKeysJson),
      dependenciesJson: Value(dependenciesJson),
      occurredAt: Value(occurredAt),
      createdAt: Value(createdAt),
      state: Value(state),
      attemptCount: Value(attemptCount),
      nextRetryAt: nextRetryAt == null && nullToAbsent
          ? const Value.absent()
          : Value(nextRetryAt),
      lastHttpStatus: lastHttpStatus == null && nullToAbsent
          ? const Value.absent()
          : Value(lastHttpStatus),
      lastErrorCode: lastErrorCode == null && nullToAbsent
          ? const Value.absent()
          : Value(lastErrorCode),
      ackedAt: ackedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(ackedAt),
    );
  }

  factory LocalCommand.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalCommand(
      clientOperationId: serializer.fromJson<String>(json['clientOperationId']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      commandType: serializer.fromJson<String>(json['commandType']),
      queueSequence: serializer.fromJson<int>(json['queueSequence']),
      endpointPath: serializer.fromJson<String>(json['endpointPath']),
      httpMethod: serializer.fromJson<String>(json['httpMethod']),
      payloadJson: serializer.fromJson<String>(json['payloadJson']),
      tripId: serializer.fromJson<String>(json['tripId']),
      aggregateId: serializer.fromJson<String>(json['aggregateId']),
      resourceKeysJson: serializer.fromJson<String>(json['resourceKeysJson']),
      dependenciesJson: serializer.fromJson<String>(json['dependenciesJson']),
      occurredAt: serializer.fromJson<DateTime>(json['occurredAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      state: serializer.fromJson<String>(json['state']),
      attemptCount: serializer.fromJson<int>(json['attemptCount']),
      nextRetryAt: serializer.fromJson<DateTime?>(json['nextRetryAt']),
      lastHttpStatus: serializer.fromJson<int?>(json['lastHttpStatus']),
      lastErrorCode: serializer.fromJson<String?>(json['lastErrorCode']),
      ackedAt: serializer.fromJson<DateTime?>(json['ackedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'clientOperationId': serializer.toJson<String>(clientOperationId),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'commandType': serializer.toJson<String>(commandType),
      'queueSequence': serializer.toJson<int>(queueSequence),
      'endpointPath': serializer.toJson<String>(endpointPath),
      'httpMethod': serializer.toJson<String>(httpMethod),
      'payloadJson': serializer.toJson<String>(payloadJson),
      'tripId': serializer.toJson<String>(tripId),
      'aggregateId': serializer.toJson<String>(aggregateId),
      'resourceKeysJson': serializer.toJson<String>(resourceKeysJson),
      'dependenciesJson': serializer.toJson<String>(dependenciesJson),
      'occurredAt': serializer.toJson<DateTime>(occurredAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'state': serializer.toJson<String>(state),
      'attemptCount': serializer.toJson<int>(attemptCount),
      'nextRetryAt': serializer.toJson<DateTime?>(nextRetryAt),
      'lastHttpStatus': serializer.toJson<int?>(lastHttpStatus),
      'lastErrorCode': serializer.toJson<String?>(lastErrorCode),
      'ackedAt': serializer.toJson<DateTime?>(ackedAt),
    };
  }

  LocalCommand copyWith({
    String? clientOperationId,
    String? userId,
    String? tenantId,
    String? commandType,
    int? queueSequence,
    String? endpointPath,
    String? httpMethod,
    String? payloadJson,
    String? tripId,
    String? aggregateId,
    String? resourceKeysJson,
    String? dependenciesJson,
    DateTime? occurredAt,
    DateTime? createdAt,
    String? state,
    int? attemptCount,
    Value<DateTime?> nextRetryAt = const Value.absent(),
    Value<int?> lastHttpStatus = const Value.absent(),
    Value<String?> lastErrorCode = const Value.absent(),
    Value<DateTime?> ackedAt = const Value.absent(),
  }) => LocalCommand(
    clientOperationId: clientOperationId ?? this.clientOperationId,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    commandType: commandType ?? this.commandType,
    queueSequence: queueSequence ?? this.queueSequence,
    endpointPath: endpointPath ?? this.endpointPath,
    httpMethod: httpMethod ?? this.httpMethod,
    payloadJson: payloadJson ?? this.payloadJson,
    tripId: tripId ?? this.tripId,
    aggregateId: aggregateId ?? this.aggregateId,
    resourceKeysJson: resourceKeysJson ?? this.resourceKeysJson,
    dependenciesJson: dependenciesJson ?? this.dependenciesJson,
    occurredAt: occurredAt ?? this.occurredAt,
    createdAt: createdAt ?? this.createdAt,
    state: state ?? this.state,
    attemptCount: attemptCount ?? this.attemptCount,
    nextRetryAt: nextRetryAt.present ? nextRetryAt.value : this.nextRetryAt,
    lastHttpStatus: lastHttpStatus.present
        ? lastHttpStatus.value
        : this.lastHttpStatus,
    lastErrorCode: lastErrorCode.present
        ? lastErrorCode.value
        : this.lastErrorCode,
    ackedAt: ackedAt.present ? ackedAt.value : this.ackedAt,
  );
  LocalCommand copyWithCompanion(LocalCommandsCompanion data) {
    return LocalCommand(
      clientOperationId: data.clientOperationId.present
          ? data.clientOperationId.value
          : this.clientOperationId,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      commandType: data.commandType.present
          ? data.commandType.value
          : this.commandType,
      queueSequence: data.queueSequence.present
          ? data.queueSequence.value
          : this.queueSequence,
      endpointPath: data.endpointPath.present
          ? data.endpointPath.value
          : this.endpointPath,
      httpMethod: data.httpMethod.present
          ? data.httpMethod.value
          : this.httpMethod,
      payloadJson: data.payloadJson.present
          ? data.payloadJson.value
          : this.payloadJson,
      tripId: data.tripId.present ? data.tripId.value : this.tripId,
      aggregateId: data.aggregateId.present
          ? data.aggregateId.value
          : this.aggregateId,
      resourceKeysJson: data.resourceKeysJson.present
          ? data.resourceKeysJson.value
          : this.resourceKeysJson,
      dependenciesJson: data.dependenciesJson.present
          ? data.dependenciesJson.value
          : this.dependenciesJson,
      occurredAt: data.occurredAt.present
          ? data.occurredAt.value
          : this.occurredAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      state: data.state.present ? data.state.value : this.state,
      attemptCount: data.attemptCount.present
          ? data.attemptCount.value
          : this.attemptCount,
      nextRetryAt: data.nextRetryAt.present
          ? data.nextRetryAt.value
          : this.nextRetryAt,
      lastHttpStatus: data.lastHttpStatus.present
          ? data.lastHttpStatus.value
          : this.lastHttpStatus,
      lastErrorCode: data.lastErrorCode.present
          ? data.lastErrorCode.value
          : this.lastErrorCode,
      ackedAt: data.ackedAt.present ? data.ackedAt.value : this.ackedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalCommand(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('commandType: $commandType, ')
          ..write('queueSequence: $queueSequence, ')
          ..write('endpointPath: $endpointPath, ')
          ..write('httpMethod: $httpMethod, ')
          ..write('payloadJson: $payloadJson, ')
          ..write('tripId: $tripId, ')
          ..write('aggregateId: $aggregateId, ')
          ..write('resourceKeysJson: $resourceKeysJson, ')
          ..write('dependenciesJson: $dependenciesJson, ')
          ..write('occurredAt: $occurredAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('state: $state, ')
          ..write('attemptCount: $attemptCount, ')
          ..write('nextRetryAt: $nextRetryAt, ')
          ..write('lastHttpStatus: $lastHttpStatus, ')
          ..write('lastErrorCode: $lastErrorCode, ')
          ..write('ackedAt: $ackedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    clientOperationId,
    userId,
    tenantId,
    commandType,
    queueSequence,
    endpointPath,
    httpMethod,
    payloadJson,
    tripId,
    aggregateId,
    resourceKeysJson,
    dependenciesJson,
    occurredAt,
    createdAt,
    state,
    attemptCount,
    nextRetryAt,
    lastHttpStatus,
    lastErrorCode,
    ackedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalCommand &&
          other.clientOperationId == this.clientOperationId &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.commandType == this.commandType &&
          other.queueSequence == this.queueSequence &&
          other.endpointPath == this.endpointPath &&
          other.httpMethod == this.httpMethod &&
          other.payloadJson == this.payloadJson &&
          other.tripId == this.tripId &&
          other.aggregateId == this.aggregateId &&
          other.resourceKeysJson == this.resourceKeysJson &&
          other.dependenciesJson == this.dependenciesJson &&
          other.occurredAt == this.occurredAt &&
          other.createdAt == this.createdAt &&
          other.state == this.state &&
          other.attemptCount == this.attemptCount &&
          other.nextRetryAt == this.nextRetryAt &&
          other.lastHttpStatus == this.lastHttpStatus &&
          other.lastErrorCode == this.lastErrorCode &&
          other.ackedAt == this.ackedAt);
}

class LocalCommandsCompanion extends UpdateCompanion<LocalCommand> {
  final Value<String> clientOperationId;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> commandType;
  final Value<int> queueSequence;
  final Value<String> endpointPath;
  final Value<String> httpMethod;
  final Value<String> payloadJson;
  final Value<String> tripId;
  final Value<String> aggregateId;
  final Value<String> resourceKeysJson;
  final Value<String> dependenciesJson;
  final Value<DateTime> occurredAt;
  final Value<DateTime> createdAt;
  final Value<String> state;
  final Value<int> attemptCount;
  final Value<DateTime?> nextRetryAt;
  final Value<int?> lastHttpStatus;
  final Value<String?> lastErrorCode;
  final Value<DateTime?> ackedAt;
  final Value<int> rowid;
  const LocalCommandsCompanion({
    this.clientOperationId = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.commandType = const Value.absent(),
    this.queueSequence = const Value.absent(),
    this.endpointPath = const Value.absent(),
    this.httpMethod = const Value.absent(),
    this.payloadJson = const Value.absent(),
    this.tripId = const Value.absent(),
    this.aggregateId = const Value.absent(),
    this.resourceKeysJson = const Value.absent(),
    this.dependenciesJson = const Value.absent(),
    this.occurredAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.state = const Value.absent(),
    this.attemptCount = const Value.absent(),
    this.nextRetryAt = const Value.absent(),
    this.lastHttpStatus = const Value.absent(),
    this.lastErrorCode = const Value.absent(),
    this.ackedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalCommandsCompanion.insert({
    required String clientOperationId,
    required String userId,
    required String tenantId,
    required String commandType,
    required int queueSequence,
    required String endpointPath,
    required String httpMethod,
    required String payloadJson,
    required String tripId,
    required String aggregateId,
    required String resourceKeysJson,
    required String dependenciesJson,
    required DateTime occurredAt,
    required DateTime createdAt,
    required String state,
    this.attemptCount = const Value.absent(),
    this.nextRetryAt = const Value.absent(),
    this.lastHttpStatus = const Value.absent(),
    this.lastErrorCode = const Value.absent(),
    this.ackedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : clientOperationId = Value(clientOperationId),
       userId = Value(userId),
       tenantId = Value(tenantId),
       commandType = Value(commandType),
       queueSequence = Value(queueSequence),
       endpointPath = Value(endpointPath),
       httpMethod = Value(httpMethod),
       payloadJson = Value(payloadJson),
       tripId = Value(tripId),
       aggregateId = Value(aggregateId),
       resourceKeysJson = Value(resourceKeysJson),
       dependenciesJson = Value(dependenciesJson),
       occurredAt = Value(occurredAt),
       createdAt = Value(createdAt),
       state = Value(state);
  static Insertable<LocalCommand> custom({
    Expression<String>? clientOperationId,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? commandType,
    Expression<int>? queueSequence,
    Expression<String>? endpointPath,
    Expression<String>? httpMethod,
    Expression<String>? payloadJson,
    Expression<String>? tripId,
    Expression<String>? aggregateId,
    Expression<String>? resourceKeysJson,
    Expression<String>? dependenciesJson,
    Expression<DateTime>? occurredAt,
    Expression<DateTime>? createdAt,
    Expression<String>? state,
    Expression<int>? attemptCount,
    Expression<DateTime>? nextRetryAt,
    Expression<int>? lastHttpStatus,
    Expression<String>? lastErrorCode,
    Expression<DateTime>? ackedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientOperationId != null) 'client_operation_id': clientOperationId,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (commandType != null) 'command_type': commandType,
      if (queueSequence != null) 'queue_sequence': queueSequence,
      if (endpointPath != null) 'endpoint_path': endpointPath,
      if (httpMethod != null) 'http_method': httpMethod,
      if (payloadJson != null) 'payload_json': payloadJson,
      if (tripId != null) 'trip_id': tripId,
      if (aggregateId != null) 'aggregate_id': aggregateId,
      if (resourceKeysJson != null) 'resource_keys_json': resourceKeysJson,
      if (dependenciesJson != null) 'dependencies_json': dependenciesJson,
      if (occurredAt != null) 'occurred_at': occurredAt,
      if (createdAt != null) 'created_at': createdAt,
      if (state != null) 'state': state,
      if (attemptCount != null) 'attempt_count': attemptCount,
      if (nextRetryAt != null) 'next_retry_at': nextRetryAt,
      if (lastHttpStatus != null) 'last_http_status': lastHttpStatus,
      if (lastErrorCode != null) 'last_error_code': lastErrorCode,
      if (ackedAt != null) 'acked_at': ackedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalCommandsCompanion copyWith({
    Value<String>? clientOperationId,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? commandType,
    Value<int>? queueSequence,
    Value<String>? endpointPath,
    Value<String>? httpMethod,
    Value<String>? payloadJson,
    Value<String>? tripId,
    Value<String>? aggregateId,
    Value<String>? resourceKeysJson,
    Value<String>? dependenciesJson,
    Value<DateTime>? occurredAt,
    Value<DateTime>? createdAt,
    Value<String>? state,
    Value<int>? attemptCount,
    Value<DateTime?>? nextRetryAt,
    Value<int?>? lastHttpStatus,
    Value<String?>? lastErrorCode,
    Value<DateTime?>? ackedAt,
    Value<int>? rowid,
  }) {
    return LocalCommandsCompanion(
      clientOperationId: clientOperationId ?? this.clientOperationId,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      commandType: commandType ?? this.commandType,
      queueSequence: queueSequence ?? this.queueSequence,
      endpointPath: endpointPath ?? this.endpointPath,
      httpMethod: httpMethod ?? this.httpMethod,
      payloadJson: payloadJson ?? this.payloadJson,
      tripId: tripId ?? this.tripId,
      aggregateId: aggregateId ?? this.aggregateId,
      resourceKeysJson: resourceKeysJson ?? this.resourceKeysJson,
      dependenciesJson: dependenciesJson ?? this.dependenciesJson,
      occurredAt: occurredAt ?? this.occurredAt,
      createdAt: createdAt ?? this.createdAt,
      state: state ?? this.state,
      attemptCount: attemptCount ?? this.attemptCount,
      nextRetryAt: nextRetryAt ?? this.nextRetryAt,
      lastHttpStatus: lastHttpStatus ?? this.lastHttpStatus,
      lastErrorCode: lastErrorCode ?? this.lastErrorCode,
      ackedAt: ackedAt ?? this.ackedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (clientOperationId.present) {
      map['client_operation_id'] = Variable<String>(clientOperationId.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (commandType.present) {
      map['command_type'] = Variable<String>(commandType.value);
    }
    if (queueSequence.present) {
      map['queue_sequence'] = Variable<int>(queueSequence.value);
    }
    if (endpointPath.present) {
      map['endpoint_path'] = Variable<String>(endpointPath.value);
    }
    if (httpMethod.present) {
      map['http_method'] = Variable<String>(httpMethod.value);
    }
    if (payloadJson.present) {
      map['payload_json'] = Variable<String>(payloadJson.value);
    }
    if (tripId.present) {
      map['trip_id'] = Variable<String>(tripId.value);
    }
    if (aggregateId.present) {
      map['aggregate_id'] = Variable<String>(aggregateId.value);
    }
    if (resourceKeysJson.present) {
      map['resource_keys_json'] = Variable<String>(resourceKeysJson.value);
    }
    if (dependenciesJson.present) {
      map['dependencies_json'] = Variable<String>(dependenciesJson.value);
    }
    if (occurredAt.present) {
      map['occurred_at'] = Variable<DateTime>(occurredAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (attemptCount.present) {
      map['attempt_count'] = Variable<int>(attemptCount.value);
    }
    if (nextRetryAt.present) {
      map['next_retry_at'] = Variable<DateTime>(nextRetryAt.value);
    }
    if (lastHttpStatus.present) {
      map['last_http_status'] = Variable<int>(lastHttpStatus.value);
    }
    if (lastErrorCode.present) {
      map['last_error_code'] = Variable<String>(lastErrorCode.value);
    }
    if (ackedAt.present) {
      map['acked_at'] = Variable<DateTime>(ackedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalCommandsCompanion(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('commandType: $commandType, ')
          ..write('queueSequence: $queueSequence, ')
          ..write('endpointPath: $endpointPath, ')
          ..write('httpMethod: $httpMethod, ')
          ..write('payloadJson: $payloadJson, ')
          ..write('tripId: $tripId, ')
          ..write('aggregateId: $aggregateId, ')
          ..write('resourceKeysJson: $resourceKeysJson, ')
          ..write('dependenciesJson: $dependenciesJson, ')
          ..write('occurredAt: $occurredAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('state: $state, ')
          ..write('attemptCount: $attemptCount, ')
          ..write('nextRetryAt: $nextRetryAt, ')
          ..write('lastHttpStatus: $lastHttpStatus, ')
          ..write('lastErrorCode: $lastErrorCode, ')
          ..write('ackedAt: $ackedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $LocalSyncIssuesTable extends LocalSyncIssues
    with TableInfo<$LocalSyncIssuesTable, LocalSyncIssue> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalSyncIssuesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _clientOperationIdMeta = const VerificationMeta(
    'clientOperationId',
  );
  @override
  late final GeneratedColumn<String> clientOperationId =
      GeneratedColumn<String>(
        'client_operation_id',
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
  static const VerificationMeta _categoryMeta = const VerificationMeta(
    'category',
  );
  @override
  late final GeneratedColumn<String> category = GeneratedColumn<String>(
    'category',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _errorCodeMeta = const VerificationMeta(
    'errorCode',
  );
  @override
  late final GeneratedColumn<String> errorCode = GeneratedColumn<String>(
    'error_code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _relatedResourceMeta = const VerificationMeta(
    'relatedResource',
  );
  @override
  late final GeneratedColumn<String> relatedResource = GeneratedColumn<String>(
    'related_resource',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _messageMeta = const VerificationMeta(
    'message',
  );
  @override
  late final GeneratedColumn<String> message = GeneratedColumn<String>(
    'message',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _resolvedAtMeta = const VerificationMeta(
    'resolvedAt',
  );
  @override
  late final GeneratedColumn<DateTime> resolvedAt = GeneratedColumn<DateTime>(
    'resolved_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    clientOperationId,
    userId,
    tenantId,
    category,
    errorCode,
    relatedResource,
    message,
    createdAt,
    resolvedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_sync_issues';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalSyncIssue> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('client_operation_id')) {
      context.handle(
        _clientOperationIdMeta,
        clientOperationId.isAcceptableOrUnknown(
          data['client_operation_id']!,
          _clientOperationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_clientOperationIdMeta);
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
    if (data.containsKey('category')) {
      context.handle(
        _categoryMeta,
        category.isAcceptableOrUnknown(data['category']!, _categoryMeta),
      );
    } else if (isInserting) {
      context.missing(_categoryMeta);
    }
    if (data.containsKey('error_code')) {
      context.handle(
        _errorCodeMeta,
        errorCode.isAcceptableOrUnknown(data['error_code']!, _errorCodeMeta),
      );
    } else if (isInserting) {
      context.missing(_errorCodeMeta);
    }
    if (data.containsKey('related_resource')) {
      context.handle(
        _relatedResourceMeta,
        relatedResource.isAcceptableOrUnknown(
          data['related_resource']!,
          _relatedResourceMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_relatedResourceMeta);
    }
    if (data.containsKey('message')) {
      context.handle(
        _messageMeta,
        message.isAcceptableOrUnknown(data['message']!, _messageMeta),
      );
    } else if (isInserting) {
      context.missing(_messageMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('resolved_at')) {
      context.handle(
        _resolvedAtMeta,
        resolvedAt.isAcceptableOrUnknown(data['resolved_at']!, _resolvedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalSyncIssue map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalSyncIssue(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      clientOperationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}client_operation_id'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      category: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}category'],
      )!,
      errorCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}error_code'],
      )!,
      relatedResource: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}related_resource'],
      )!,
      message: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}message'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      resolvedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}resolved_at'],
      ),
    );
  }

  @override
  $LocalSyncIssuesTable createAlias(String alias) {
    return $LocalSyncIssuesTable(attachedDatabase, alias);
  }
}

class LocalSyncIssue extends DataClass implements Insertable<LocalSyncIssue> {
  final String id;
  final String clientOperationId;
  final String userId;
  final String tenantId;
  final String category;
  final String errorCode;
  final String relatedResource;
  final String message;
  final DateTime createdAt;
  final DateTime? resolvedAt;
  const LocalSyncIssue({
    required this.id,
    required this.clientOperationId,
    required this.userId,
    required this.tenantId,
    required this.category,
    required this.errorCode,
    required this.relatedResource,
    required this.message,
    required this.createdAt,
    this.resolvedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['client_operation_id'] = Variable<String>(clientOperationId);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['category'] = Variable<String>(category);
    map['error_code'] = Variable<String>(errorCode);
    map['related_resource'] = Variable<String>(relatedResource);
    map['message'] = Variable<String>(message);
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || resolvedAt != null) {
      map['resolved_at'] = Variable<DateTime>(resolvedAt);
    }
    return map;
  }

  LocalSyncIssuesCompanion toCompanion(bool nullToAbsent) {
    return LocalSyncIssuesCompanion(
      id: Value(id),
      clientOperationId: Value(clientOperationId),
      userId: Value(userId),
      tenantId: Value(tenantId),
      category: Value(category),
      errorCode: Value(errorCode),
      relatedResource: Value(relatedResource),
      message: Value(message),
      createdAt: Value(createdAt),
      resolvedAt: resolvedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(resolvedAt),
    );
  }

  factory LocalSyncIssue.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalSyncIssue(
      id: serializer.fromJson<String>(json['id']),
      clientOperationId: serializer.fromJson<String>(json['clientOperationId']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      category: serializer.fromJson<String>(json['category']),
      errorCode: serializer.fromJson<String>(json['errorCode']),
      relatedResource: serializer.fromJson<String>(json['relatedResource']),
      message: serializer.fromJson<String>(json['message']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      resolvedAt: serializer.fromJson<DateTime?>(json['resolvedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'clientOperationId': serializer.toJson<String>(clientOperationId),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'category': serializer.toJson<String>(category),
      'errorCode': serializer.toJson<String>(errorCode),
      'relatedResource': serializer.toJson<String>(relatedResource),
      'message': serializer.toJson<String>(message),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'resolvedAt': serializer.toJson<DateTime?>(resolvedAt),
    };
  }

  LocalSyncIssue copyWith({
    String? id,
    String? clientOperationId,
    String? userId,
    String? tenantId,
    String? category,
    String? errorCode,
    String? relatedResource,
    String? message,
    DateTime? createdAt,
    Value<DateTime?> resolvedAt = const Value.absent(),
  }) => LocalSyncIssue(
    id: id ?? this.id,
    clientOperationId: clientOperationId ?? this.clientOperationId,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    category: category ?? this.category,
    errorCode: errorCode ?? this.errorCode,
    relatedResource: relatedResource ?? this.relatedResource,
    message: message ?? this.message,
    createdAt: createdAt ?? this.createdAt,
    resolvedAt: resolvedAt.present ? resolvedAt.value : this.resolvedAt,
  );
  LocalSyncIssue copyWithCompanion(LocalSyncIssuesCompanion data) {
    return LocalSyncIssue(
      id: data.id.present ? data.id.value : this.id,
      clientOperationId: data.clientOperationId.present
          ? data.clientOperationId.value
          : this.clientOperationId,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      category: data.category.present ? data.category.value : this.category,
      errorCode: data.errorCode.present ? data.errorCode.value : this.errorCode,
      relatedResource: data.relatedResource.present
          ? data.relatedResource.value
          : this.relatedResource,
      message: data.message.present ? data.message.value : this.message,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      resolvedAt: data.resolvedAt.present
          ? data.resolvedAt.value
          : this.resolvedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalSyncIssue(')
          ..write('id: $id, ')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('category: $category, ')
          ..write('errorCode: $errorCode, ')
          ..write('relatedResource: $relatedResource, ')
          ..write('message: $message, ')
          ..write('createdAt: $createdAt, ')
          ..write('resolvedAt: $resolvedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    clientOperationId,
    userId,
    tenantId,
    category,
    errorCode,
    relatedResource,
    message,
    createdAt,
    resolvedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalSyncIssue &&
          other.id == this.id &&
          other.clientOperationId == this.clientOperationId &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.category == this.category &&
          other.errorCode == this.errorCode &&
          other.relatedResource == this.relatedResource &&
          other.message == this.message &&
          other.createdAt == this.createdAt &&
          other.resolvedAt == this.resolvedAt);
}

class LocalSyncIssuesCompanion extends UpdateCompanion<LocalSyncIssue> {
  final Value<String> id;
  final Value<String> clientOperationId;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> category;
  final Value<String> errorCode;
  final Value<String> relatedResource;
  final Value<String> message;
  final Value<DateTime> createdAt;
  final Value<DateTime?> resolvedAt;
  final Value<int> rowid;
  const LocalSyncIssuesCompanion({
    this.id = const Value.absent(),
    this.clientOperationId = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.category = const Value.absent(),
    this.errorCode = const Value.absent(),
    this.relatedResource = const Value.absent(),
    this.message = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.resolvedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalSyncIssuesCompanion.insert({
    required String id,
    required String clientOperationId,
    required String userId,
    required String tenantId,
    required String category,
    required String errorCode,
    required String relatedResource,
    required String message,
    required DateTime createdAt,
    this.resolvedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       clientOperationId = Value(clientOperationId),
       userId = Value(userId),
       tenantId = Value(tenantId),
       category = Value(category),
       errorCode = Value(errorCode),
       relatedResource = Value(relatedResource),
       message = Value(message),
       createdAt = Value(createdAt);
  static Insertable<LocalSyncIssue> custom({
    Expression<String>? id,
    Expression<String>? clientOperationId,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? category,
    Expression<String>? errorCode,
    Expression<String>? relatedResource,
    Expression<String>? message,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? resolvedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (clientOperationId != null) 'client_operation_id': clientOperationId,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (category != null) 'category': category,
      if (errorCode != null) 'error_code': errorCode,
      if (relatedResource != null) 'related_resource': relatedResource,
      if (message != null) 'message': message,
      if (createdAt != null) 'created_at': createdAt,
      if (resolvedAt != null) 'resolved_at': resolvedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalSyncIssuesCompanion copyWith({
    Value<String>? id,
    Value<String>? clientOperationId,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? category,
    Value<String>? errorCode,
    Value<String>? relatedResource,
    Value<String>? message,
    Value<DateTime>? createdAt,
    Value<DateTime?>? resolvedAt,
    Value<int>? rowid,
  }) {
    return LocalSyncIssuesCompanion(
      id: id ?? this.id,
      clientOperationId: clientOperationId ?? this.clientOperationId,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      category: category ?? this.category,
      errorCode: errorCode ?? this.errorCode,
      relatedResource: relatedResource ?? this.relatedResource,
      message: message ?? this.message,
      createdAt: createdAt ?? this.createdAt,
      resolvedAt: resolvedAt ?? this.resolvedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (clientOperationId.present) {
      map['client_operation_id'] = Variable<String>(clientOperationId.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (category.present) {
      map['category'] = Variable<String>(category.value);
    }
    if (errorCode.present) {
      map['error_code'] = Variable<String>(errorCode.value);
    }
    if (relatedResource.present) {
      map['related_resource'] = Variable<String>(relatedResource.value);
    }
    if (message.present) {
      map['message'] = Variable<String>(message.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (resolvedAt.present) {
      map['resolved_at'] = Variable<DateTime>(resolvedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalSyncIssuesCompanion(')
          ..write('id: $id, ')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('category: $category, ')
          ..write('errorCode: $errorCode, ')
          ..write('relatedResource: $relatedResource, ')
          ..write('message: $message, ')
          ..write('createdAt: $createdAt, ')
          ..write('resolvedAt: $resolvedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $LocalMediaTable extends LocalMedia
    with TableInfo<$LocalMediaTable, LocalMediaData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalMediaTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
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
  static const VerificationMeta _documentTypeMeta = const VerificationMeta(
    'documentType',
  );
  @override
  late final GeneratedColumn<String> documentType = GeneratedColumn<String>(
    'document_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _contentTypeMeta = const VerificationMeta(
    'contentType',
  );
  @override
  late final GeneratedColumn<String> contentType = GeneratedColumn<String>(
    'content_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _fileKeyMeta = const VerificationMeta(
    'fileKey',
  );
  @override
  late final GeneratedColumn<String> fileKey = GeneratedColumn<String>(
    'file_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sizeBytesMeta = const VerificationMeta(
    'sizeBytes',
  );
  @override
  late final GeneratedColumn<int> sizeBytes = GeneratedColumn<int>(
    'size_bytes',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _checksumSha256Meta = const VerificationMeta(
    'checksumSha256',
  );
  @override
  late final GeneratedColumn<String> checksumSha256 = GeneratedColumn<String>(
    'checksum_sha256',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _serverDocumentIdMeta = const VerificationMeta(
    'serverDocumentId',
  );
  @override
  late final GeneratedColumn<String> serverDocumentId = GeneratedColumn<String>(
    'server_document_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _finalizeKeyMeta = const VerificationMeta(
    'finalizeKey',
  );
  @override
  late final GeneratedColumn<String> finalizeKey = GeneratedColumn<String>(
    'finalize_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptHistoryJsonMeta =
      const VerificationMeta('attemptHistoryJson');
  @override
  late final GeneratedColumn<String> attemptHistoryJson =
      GeneratedColumn<String>(
        'attempt_history_json',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: false,
        defaultValue: const Constant('[]'),
      );
  static const VerificationMeta _attemptCountMeta = const VerificationMeta(
    'attemptCount',
  );
  @override
  late final GeneratedColumn<int> attemptCount = GeneratedColumn<int>(
    'attempt_count',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _nextRetryAtMeta = const VerificationMeta(
    'nextRetryAt',
  );
  @override
  late final GeneratedColumn<DateTime> nextRetryAt = GeneratedColumn<DateTime>(
    'next_retry_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastHttpStatusMeta = const VerificationMeta(
    'lastHttpStatus',
  );
  @override
  late final GeneratedColumn<int> lastHttpStatus = GeneratedColumn<int>(
    'last_http_status',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastErrorCodeMeta = const VerificationMeta(
    'lastErrorCode',
  );
  @override
  late final GeneratedColumn<String> lastErrorCode = GeneratedColumn<String>(
    'last_error_code',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _reviewRequiredMeta = const VerificationMeta(
    'reviewRequired',
  );
  @override
  late final GeneratedColumn<bool> reviewRequired = GeneratedColumn<bool>(
    'review_required',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("review_required" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _tripIdMeta = const VerificationMeta('tripId');
  @override
  late final GeneratedColumn<String> tripId = GeneratedColumn<String>(
    'trip_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _capturedAtMeta = const VerificationMeta(
    'capturedAt',
  );
  @override
  late final GeneratedColumn<DateTime> capturedAt = GeneratedColumn<DateTime>(
    'captured_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _finalizedAtMeta = const VerificationMeta(
    'finalizedAt',
  );
  @override
  late final GeneratedColumn<DateTime> finalizedAt = GeneratedColumn<DateTime>(
    'finalized_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _fileStateMeta = const VerificationMeta(
    'fileState',
  );
  @override
  late final GeneratedColumn<String> fileState = GeneratedColumn<String>(
    'file_state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('PRESENT'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    userId,
    tenantId,
    documentType,
    contentType,
    fileKey,
    sizeBytes,
    checksumSha256,
    state,
    serverDocumentId,
    finalizeKey,
    attemptHistoryJson,
    attemptCount,
    nextRetryAt,
    lastHttpStatus,
    lastErrorCode,
    reviewRequired,
    tripId,
    capturedAt,
    updatedAt,
    finalizedAt,
    fileState,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_media';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalMediaData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
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
    if (data.containsKey('document_type')) {
      context.handle(
        _documentTypeMeta,
        documentType.isAcceptableOrUnknown(
          data['document_type']!,
          _documentTypeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_documentTypeMeta);
    }
    if (data.containsKey('content_type')) {
      context.handle(
        _contentTypeMeta,
        contentType.isAcceptableOrUnknown(
          data['content_type']!,
          _contentTypeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_contentTypeMeta);
    }
    if (data.containsKey('file_key')) {
      context.handle(
        _fileKeyMeta,
        fileKey.isAcceptableOrUnknown(data['file_key']!, _fileKeyMeta),
      );
    } else if (isInserting) {
      context.missing(_fileKeyMeta);
    }
    if (data.containsKey('size_bytes')) {
      context.handle(
        _sizeBytesMeta,
        sizeBytes.isAcceptableOrUnknown(data['size_bytes']!, _sizeBytesMeta),
      );
    } else if (isInserting) {
      context.missing(_sizeBytesMeta);
    }
    if (data.containsKey('checksum_sha256')) {
      context.handle(
        _checksumSha256Meta,
        checksumSha256.isAcceptableOrUnknown(
          data['checksum_sha256']!,
          _checksumSha256Meta,
        ),
      );
    } else if (isInserting) {
      context.missing(_checksumSha256Meta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('server_document_id')) {
      context.handle(
        _serverDocumentIdMeta,
        serverDocumentId.isAcceptableOrUnknown(
          data['server_document_id']!,
          _serverDocumentIdMeta,
        ),
      );
    }
    if (data.containsKey('finalize_key')) {
      context.handle(
        _finalizeKeyMeta,
        finalizeKey.isAcceptableOrUnknown(
          data['finalize_key']!,
          _finalizeKeyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_finalizeKeyMeta);
    }
    if (data.containsKey('attempt_history_json')) {
      context.handle(
        _attemptHistoryJsonMeta,
        attemptHistoryJson.isAcceptableOrUnknown(
          data['attempt_history_json']!,
          _attemptHistoryJsonMeta,
        ),
      );
    }
    if (data.containsKey('attempt_count')) {
      context.handle(
        _attemptCountMeta,
        attemptCount.isAcceptableOrUnknown(
          data['attempt_count']!,
          _attemptCountMeta,
        ),
      );
    }
    if (data.containsKey('next_retry_at')) {
      context.handle(
        _nextRetryAtMeta,
        nextRetryAt.isAcceptableOrUnknown(
          data['next_retry_at']!,
          _nextRetryAtMeta,
        ),
      );
    }
    if (data.containsKey('last_http_status')) {
      context.handle(
        _lastHttpStatusMeta,
        lastHttpStatus.isAcceptableOrUnknown(
          data['last_http_status']!,
          _lastHttpStatusMeta,
        ),
      );
    }
    if (data.containsKey('last_error_code')) {
      context.handle(
        _lastErrorCodeMeta,
        lastErrorCode.isAcceptableOrUnknown(
          data['last_error_code']!,
          _lastErrorCodeMeta,
        ),
      );
    }
    if (data.containsKey('review_required')) {
      context.handle(
        _reviewRequiredMeta,
        reviewRequired.isAcceptableOrUnknown(
          data['review_required']!,
          _reviewRequiredMeta,
        ),
      );
    }
    if (data.containsKey('trip_id')) {
      context.handle(
        _tripIdMeta,
        tripId.isAcceptableOrUnknown(data['trip_id']!, _tripIdMeta),
      );
    }
    if (data.containsKey('captured_at')) {
      context.handle(
        _capturedAtMeta,
        capturedAt.isAcceptableOrUnknown(data['captured_at']!, _capturedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_capturedAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    if (data.containsKey('finalized_at')) {
      context.handle(
        _finalizedAtMeta,
        finalizedAt.isAcceptableOrUnknown(
          data['finalized_at']!,
          _finalizedAtMeta,
        ),
      );
    }
    if (data.containsKey('file_state')) {
      context.handle(
        _fileStateMeta,
        fileState.isAcceptableOrUnknown(data['file_state']!, _fileStateMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalMediaData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalMediaData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      documentType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}document_type'],
      )!,
      contentType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_type'],
      )!,
      fileKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}file_key'],
      )!,
      sizeBytes: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}size_bytes'],
      )!,
      checksumSha256: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}checksum_sha256'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      serverDocumentId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_document_id'],
      ),
      finalizeKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}finalize_key'],
      )!,
      attemptHistoryJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}attempt_history_json'],
      )!,
      attemptCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempt_count'],
      )!,
      nextRetryAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}next_retry_at'],
      ),
      lastHttpStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}last_http_status'],
      ),
      lastErrorCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_error_code'],
      ),
      reviewRequired: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}review_required'],
      )!,
      tripId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trip_id'],
      ),
      capturedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}captured_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      finalizedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}finalized_at'],
      ),
      fileState: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}file_state'],
      )!,
    );
  }

  @override
  $LocalMediaTable createAlias(String alias) {
    return $LocalMediaTable(attachedDatabase, alias);
  }
}

class LocalMediaData extends DataClass implements Insertable<LocalMediaData> {
  final String id;
  final String userId;
  final String tenantId;
  final String documentType;
  final String contentType;
  final String fileKey;
  final int sizeBytes;
  final String checksumSha256;
  final String state;
  final String? serverDocumentId;
  final String finalizeKey;
  final String attemptHistoryJson;
  final int attemptCount;
  final DateTime? nextRetryAt;
  final int? lastHttpStatus;
  final String? lastErrorCode;
  final bool reviewRequired;
  final String? tripId;
  final DateTime capturedAt;
  final DateTime updatedAt;
  final DateTime? finalizedAt;
  final String fileState;
  const LocalMediaData({
    required this.id,
    required this.userId,
    required this.tenantId,
    required this.documentType,
    required this.contentType,
    required this.fileKey,
    required this.sizeBytes,
    required this.checksumSha256,
    required this.state,
    this.serverDocumentId,
    required this.finalizeKey,
    required this.attemptHistoryJson,
    required this.attemptCount,
    this.nextRetryAt,
    this.lastHttpStatus,
    this.lastErrorCode,
    required this.reviewRequired,
    this.tripId,
    required this.capturedAt,
    required this.updatedAt,
    this.finalizedAt,
    required this.fileState,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['document_type'] = Variable<String>(documentType);
    map['content_type'] = Variable<String>(contentType);
    map['file_key'] = Variable<String>(fileKey);
    map['size_bytes'] = Variable<int>(sizeBytes);
    map['checksum_sha256'] = Variable<String>(checksumSha256);
    map['state'] = Variable<String>(state);
    if (!nullToAbsent || serverDocumentId != null) {
      map['server_document_id'] = Variable<String>(serverDocumentId);
    }
    map['finalize_key'] = Variable<String>(finalizeKey);
    map['attempt_history_json'] = Variable<String>(attemptHistoryJson);
    map['attempt_count'] = Variable<int>(attemptCount);
    if (!nullToAbsent || nextRetryAt != null) {
      map['next_retry_at'] = Variable<DateTime>(nextRetryAt);
    }
    if (!nullToAbsent || lastHttpStatus != null) {
      map['last_http_status'] = Variable<int>(lastHttpStatus);
    }
    if (!nullToAbsent || lastErrorCode != null) {
      map['last_error_code'] = Variable<String>(lastErrorCode);
    }
    map['review_required'] = Variable<bool>(reviewRequired);
    if (!nullToAbsent || tripId != null) {
      map['trip_id'] = Variable<String>(tripId);
    }
    map['captured_at'] = Variable<DateTime>(capturedAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    if (!nullToAbsent || finalizedAt != null) {
      map['finalized_at'] = Variable<DateTime>(finalizedAt);
    }
    map['file_state'] = Variable<String>(fileState);
    return map;
  }

  LocalMediaCompanion toCompanion(bool nullToAbsent) {
    return LocalMediaCompanion(
      id: Value(id),
      userId: Value(userId),
      tenantId: Value(tenantId),
      documentType: Value(documentType),
      contentType: Value(contentType),
      fileKey: Value(fileKey),
      sizeBytes: Value(sizeBytes),
      checksumSha256: Value(checksumSha256),
      state: Value(state),
      serverDocumentId: serverDocumentId == null && nullToAbsent
          ? const Value.absent()
          : Value(serverDocumentId),
      finalizeKey: Value(finalizeKey),
      attemptHistoryJson: Value(attemptHistoryJson),
      attemptCount: Value(attemptCount),
      nextRetryAt: nextRetryAt == null && nullToAbsent
          ? const Value.absent()
          : Value(nextRetryAt),
      lastHttpStatus: lastHttpStatus == null && nullToAbsent
          ? const Value.absent()
          : Value(lastHttpStatus),
      lastErrorCode: lastErrorCode == null && nullToAbsent
          ? const Value.absent()
          : Value(lastErrorCode),
      reviewRequired: Value(reviewRequired),
      tripId: tripId == null && nullToAbsent
          ? const Value.absent()
          : Value(tripId),
      capturedAt: Value(capturedAt),
      updatedAt: Value(updatedAt),
      finalizedAt: finalizedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(finalizedAt),
      fileState: Value(fileState),
    );
  }

  factory LocalMediaData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalMediaData(
      id: serializer.fromJson<String>(json['id']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      documentType: serializer.fromJson<String>(json['documentType']),
      contentType: serializer.fromJson<String>(json['contentType']),
      fileKey: serializer.fromJson<String>(json['fileKey']),
      sizeBytes: serializer.fromJson<int>(json['sizeBytes']),
      checksumSha256: serializer.fromJson<String>(json['checksumSha256']),
      state: serializer.fromJson<String>(json['state']),
      serverDocumentId: serializer.fromJson<String?>(json['serverDocumentId']),
      finalizeKey: serializer.fromJson<String>(json['finalizeKey']),
      attemptHistoryJson: serializer.fromJson<String>(
        json['attemptHistoryJson'],
      ),
      attemptCount: serializer.fromJson<int>(json['attemptCount']),
      nextRetryAt: serializer.fromJson<DateTime?>(json['nextRetryAt']),
      lastHttpStatus: serializer.fromJson<int?>(json['lastHttpStatus']),
      lastErrorCode: serializer.fromJson<String?>(json['lastErrorCode']),
      reviewRequired: serializer.fromJson<bool>(json['reviewRequired']),
      tripId: serializer.fromJson<String?>(json['tripId']),
      capturedAt: serializer.fromJson<DateTime>(json['capturedAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      finalizedAt: serializer.fromJson<DateTime?>(json['finalizedAt']),
      fileState: serializer.fromJson<String>(json['fileState']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'documentType': serializer.toJson<String>(documentType),
      'contentType': serializer.toJson<String>(contentType),
      'fileKey': serializer.toJson<String>(fileKey),
      'sizeBytes': serializer.toJson<int>(sizeBytes),
      'checksumSha256': serializer.toJson<String>(checksumSha256),
      'state': serializer.toJson<String>(state),
      'serverDocumentId': serializer.toJson<String?>(serverDocumentId),
      'finalizeKey': serializer.toJson<String>(finalizeKey),
      'attemptHistoryJson': serializer.toJson<String>(attemptHistoryJson),
      'attemptCount': serializer.toJson<int>(attemptCount),
      'nextRetryAt': serializer.toJson<DateTime?>(nextRetryAt),
      'lastHttpStatus': serializer.toJson<int?>(lastHttpStatus),
      'lastErrorCode': serializer.toJson<String?>(lastErrorCode),
      'reviewRequired': serializer.toJson<bool>(reviewRequired),
      'tripId': serializer.toJson<String?>(tripId),
      'capturedAt': serializer.toJson<DateTime>(capturedAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'finalizedAt': serializer.toJson<DateTime?>(finalizedAt),
      'fileState': serializer.toJson<String>(fileState),
    };
  }

  LocalMediaData copyWith({
    String? id,
    String? userId,
    String? tenantId,
    String? documentType,
    String? contentType,
    String? fileKey,
    int? sizeBytes,
    String? checksumSha256,
    String? state,
    Value<String?> serverDocumentId = const Value.absent(),
    String? finalizeKey,
    String? attemptHistoryJson,
    int? attemptCount,
    Value<DateTime?> nextRetryAt = const Value.absent(),
    Value<int?> lastHttpStatus = const Value.absent(),
    Value<String?> lastErrorCode = const Value.absent(),
    bool? reviewRequired,
    Value<String?> tripId = const Value.absent(),
    DateTime? capturedAt,
    DateTime? updatedAt,
    Value<DateTime?> finalizedAt = const Value.absent(),
    String? fileState,
  }) => LocalMediaData(
    id: id ?? this.id,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    documentType: documentType ?? this.documentType,
    contentType: contentType ?? this.contentType,
    fileKey: fileKey ?? this.fileKey,
    sizeBytes: sizeBytes ?? this.sizeBytes,
    checksumSha256: checksumSha256 ?? this.checksumSha256,
    state: state ?? this.state,
    serverDocumentId: serverDocumentId.present
        ? serverDocumentId.value
        : this.serverDocumentId,
    finalizeKey: finalizeKey ?? this.finalizeKey,
    attemptHistoryJson: attemptHistoryJson ?? this.attemptHistoryJson,
    attemptCount: attemptCount ?? this.attemptCount,
    nextRetryAt: nextRetryAt.present ? nextRetryAt.value : this.nextRetryAt,
    lastHttpStatus: lastHttpStatus.present
        ? lastHttpStatus.value
        : this.lastHttpStatus,
    lastErrorCode: lastErrorCode.present
        ? lastErrorCode.value
        : this.lastErrorCode,
    reviewRequired: reviewRequired ?? this.reviewRequired,
    tripId: tripId.present ? tripId.value : this.tripId,
    capturedAt: capturedAt ?? this.capturedAt,
    updatedAt: updatedAt ?? this.updatedAt,
    finalizedAt: finalizedAt.present ? finalizedAt.value : this.finalizedAt,
    fileState: fileState ?? this.fileState,
  );
  LocalMediaData copyWithCompanion(LocalMediaCompanion data) {
    return LocalMediaData(
      id: data.id.present ? data.id.value : this.id,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      documentType: data.documentType.present
          ? data.documentType.value
          : this.documentType,
      contentType: data.contentType.present
          ? data.contentType.value
          : this.contentType,
      fileKey: data.fileKey.present ? data.fileKey.value : this.fileKey,
      sizeBytes: data.sizeBytes.present ? data.sizeBytes.value : this.sizeBytes,
      checksumSha256: data.checksumSha256.present
          ? data.checksumSha256.value
          : this.checksumSha256,
      state: data.state.present ? data.state.value : this.state,
      serverDocumentId: data.serverDocumentId.present
          ? data.serverDocumentId.value
          : this.serverDocumentId,
      finalizeKey: data.finalizeKey.present
          ? data.finalizeKey.value
          : this.finalizeKey,
      attemptHistoryJson: data.attemptHistoryJson.present
          ? data.attemptHistoryJson.value
          : this.attemptHistoryJson,
      attemptCount: data.attemptCount.present
          ? data.attemptCount.value
          : this.attemptCount,
      nextRetryAt: data.nextRetryAt.present
          ? data.nextRetryAt.value
          : this.nextRetryAt,
      lastHttpStatus: data.lastHttpStatus.present
          ? data.lastHttpStatus.value
          : this.lastHttpStatus,
      lastErrorCode: data.lastErrorCode.present
          ? data.lastErrorCode.value
          : this.lastErrorCode,
      reviewRequired: data.reviewRequired.present
          ? data.reviewRequired.value
          : this.reviewRequired,
      tripId: data.tripId.present ? data.tripId.value : this.tripId,
      capturedAt: data.capturedAt.present
          ? data.capturedAt.value
          : this.capturedAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      finalizedAt: data.finalizedAt.present
          ? data.finalizedAt.value
          : this.finalizedAt,
      fileState: data.fileState.present ? data.fileState.value : this.fileState,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalMediaData(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('documentType: $documentType, ')
          ..write('contentType: $contentType, ')
          ..write('fileKey: $fileKey, ')
          ..write('sizeBytes: $sizeBytes, ')
          ..write('checksumSha256: $checksumSha256, ')
          ..write('state: $state, ')
          ..write('serverDocumentId: $serverDocumentId, ')
          ..write('finalizeKey: $finalizeKey, ')
          ..write('attemptHistoryJson: $attemptHistoryJson, ')
          ..write('attemptCount: $attemptCount, ')
          ..write('nextRetryAt: $nextRetryAt, ')
          ..write('lastHttpStatus: $lastHttpStatus, ')
          ..write('lastErrorCode: $lastErrorCode, ')
          ..write('reviewRequired: $reviewRequired, ')
          ..write('tripId: $tripId, ')
          ..write('capturedAt: $capturedAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('finalizedAt: $finalizedAt, ')
          ..write('fileState: $fileState')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hashAll([
    id,
    userId,
    tenantId,
    documentType,
    contentType,
    fileKey,
    sizeBytes,
    checksumSha256,
    state,
    serverDocumentId,
    finalizeKey,
    attemptHistoryJson,
    attemptCount,
    nextRetryAt,
    lastHttpStatus,
    lastErrorCode,
    reviewRequired,
    tripId,
    capturedAt,
    updatedAt,
    finalizedAt,
    fileState,
  ]);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalMediaData &&
          other.id == this.id &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.documentType == this.documentType &&
          other.contentType == this.contentType &&
          other.fileKey == this.fileKey &&
          other.sizeBytes == this.sizeBytes &&
          other.checksumSha256 == this.checksumSha256 &&
          other.state == this.state &&
          other.serverDocumentId == this.serverDocumentId &&
          other.finalizeKey == this.finalizeKey &&
          other.attemptHistoryJson == this.attemptHistoryJson &&
          other.attemptCount == this.attemptCount &&
          other.nextRetryAt == this.nextRetryAt &&
          other.lastHttpStatus == this.lastHttpStatus &&
          other.lastErrorCode == this.lastErrorCode &&
          other.reviewRequired == this.reviewRequired &&
          other.tripId == this.tripId &&
          other.capturedAt == this.capturedAt &&
          other.updatedAt == this.updatedAt &&
          other.finalizedAt == this.finalizedAt &&
          other.fileState == this.fileState);
}

class LocalMediaCompanion extends UpdateCompanion<LocalMediaData> {
  final Value<String> id;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> documentType;
  final Value<String> contentType;
  final Value<String> fileKey;
  final Value<int> sizeBytes;
  final Value<String> checksumSha256;
  final Value<String> state;
  final Value<String?> serverDocumentId;
  final Value<String> finalizeKey;
  final Value<String> attemptHistoryJson;
  final Value<int> attemptCount;
  final Value<DateTime?> nextRetryAt;
  final Value<int?> lastHttpStatus;
  final Value<String?> lastErrorCode;
  final Value<bool> reviewRequired;
  final Value<String?> tripId;
  final Value<DateTime> capturedAt;
  final Value<DateTime> updatedAt;
  final Value<DateTime?> finalizedAt;
  final Value<String> fileState;
  final Value<int> rowid;
  const LocalMediaCompanion({
    this.id = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.documentType = const Value.absent(),
    this.contentType = const Value.absent(),
    this.fileKey = const Value.absent(),
    this.sizeBytes = const Value.absent(),
    this.checksumSha256 = const Value.absent(),
    this.state = const Value.absent(),
    this.serverDocumentId = const Value.absent(),
    this.finalizeKey = const Value.absent(),
    this.attemptHistoryJson = const Value.absent(),
    this.attemptCount = const Value.absent(),
    this.nextRetryAt = const Value.absent(),
    this.lastHttpStatus = const Value.absent(),
    this.lastErrorCode = const Value.absent(),
    this.reviewRequired = const Value.absent(),
    this.tripId = const Value.absent(),
    this.capturedAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.finalizedAt = const Value.absent(),
    this.fileState = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalMediaCompanion.insert({
    required String id,
    required String userId,
    required String tenantId,
    required String documentType,
    required String contentType,
    required String fileKey,
    required int sizeBytes,
    required String checksumSha256,
    required String state,
    this.serverDocumentId = const Value.absent(),
    required String finalizeKey,
    this.attemptHistoryJson = const Value.absent(),
    this.attemptCount = const Value.absent(),
    this.nextRetryAt = const Value.absent(),
    this.lastHttpStatus = const Value.absent(),
    this.lastErrorCode = const Value.absent(),
    this.reviewRequired = const Value.absent(),
    this.tripId = const Value.absent(),
    required DateTime capturedAt,
    required DateTime updatedAt,
    this.finalizedAt = const Value.absent(),
    this.fileState = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       userId = Value(userId),
       tenantId = Value(tenantId),
       documentType = Value(documentType),
       contentType = Value(contentType),
       fileKey = Value(fileKey),
       sizeBytes = Value(sizeBytes),
       checksumSha256 = Value(checksumSha256),
       state = Value(state),
       finalizeKey = Value(finalizeKey),
       capturedAt = Value(capturedAt),
       updatedAt = Value(updatedAt);
  static Insertable<LocalMediaData> custom({
    Expression<String>? id,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? documentType,
    Expression<String>? contentType,
    Expression<String>? fileKey,
    Expression<int>? sizeBytes,
    Expression<String>? checksumSha256,
    Expression<String>? state,
    Expression<String>? serverDocumentId,
    Expression<String>? finalizeKey,
    Expression<String>? attemptHistoryJson,
    Expression<int>? attemptCount,
    Expression<DateTime>? nextRetryAt,
    Expression<int>? lastHttpStatus,
    Expression<String>? lastErrorCode,
    Expression<bool>? reviewRequired,
    Expression<String>? tripId,
    Expression<DateTime>? capturedAt,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? finalizedAt,
    Expression<String>? fileState,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (documentType != null) 'document_type': documentType,
      if (contentType != null) 'content_type': contentType,
      if (fileKey != null) 'file_key': fileKey,
      if (sizeBytes != null) 'size_bytes': sizeBytes,
      if (checksumSha256 != null) 'checksum_sha256': checksumSha256,
      if (state != null) 'state': state,
      if (serverDocumentId != null) 'server_document_id': serverDocumentId,
      if (finalizeKey != null) 'finalize_key': finalizeKey,
      if (attemptHistoryJson != null)
        'attempt_history_json': attemptHistoryJson,
      if (attemptCount != null) 'attempt_count': attemptCount,
      if (nextRetryAt != null) 'next_retry_at': nextRetryAt,
      if (lastHttpStatus != null) 'last_http_status': lastHttpStatus,
      if (lastErrorCode != null) 'last_error_code': lastErrorCode,
      if (reviewRequired != null) 'review_required': reviewRequired,
      if (tripId != null) 'trip_id': tripId,
      if (capturedAt != null) 'captured_at': capturedAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (finalizedAt != null) 'finalized_at': finalizedAt,
      if (fileState != null) 'file_state': fileState,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalMediaCompanion copyWith({
    Value<String>? id,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? documentType,
    Value<String>? contentType,
    Value<String>? fileKey,
    Value<int>? sizeBytes,
    Value<String>? checksumSha256,
    Value<String>? state,
    Value<String?>? serverDocumentId,
    Value<String>? finalizeKey,
    Value<String>? attemptHistoryJson,
    Value<int>? attemptCount,
    Value<DateTime?>? nextRetryAt,
    Value<int?>? lastHttpStatus,
    Value<String?>? lastErrorCode,
    Value<bool>? reviewRequired,
    Value<String?>? tripId,
    Value<DateTime>? capturedAt,
    Value<DateTime>? updatedAt,
    Value<DateTime?>? finalizedAt,
    Value<String>? fileState,
    Value<int>? rowid,
  }) {
    return LocalMediaCompanion(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      documentType: documentType ?? this.documentType,
      contentType: contentType ?? this.contentType,
      fileKey: fileKey ?? this.fileKey,
      sizeBytes: sizeBytes ?? this.sizeBytes,
      checksumSha256: checksumSha256 ?? this.checksumSha256,
      state: state ?? this.state,
      serverDocumentId: serverDocumentId ?? this.serverDocumentId,
      finalizeKey: finalizeKey ?? this.finalizeKey,
      attemptHistoryJson: attemptHistoryJson ?? this.attemptHistoryJson,
      attemptCount: attemptCount ?? this.attemptCount,
      nextRetryAt: nextRetryAt ?? this.nextRetryAt,
      lastHttpStatus: lastHttpStatus ?? this.lastHttpStatus,
      lastErrorCode: lastErrorCode ?? this.lastErrorCode,
      reviewRequired: reviewRequired ?? this.reviewRequired,
      tripId: tripId ?? this.tripId,
      capturedAt: capturedAt ?? this.capturedAt,
      updatedAt: updatedAt ?? this.updatedAt,
      finalizedAt: finalizedAt ?? this.finalizedAt,
      fileState: fileState ?? this.fileState,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (documentType.present) {
      map['document_type'] = Variable<String>(documentType.value);
    }
    if (contentType.present) {
      map['content_type'] = Variable<String>(contentType.value);
    }
    if (fileKey.present) {
      map['file_key'] = Variable<String>(fileKey.value);
    }
    if (sizeBytes.present) {
      map['size_bytes'] = Variable<int>(sizeBytes.value);
    }
    if (checksumSha256.present) {
      map['checksum_sha256'] = Variable<String>(checksumSha256.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (serverDocumentId.present) {
      map['server_document_id'] = Variable<String>(serverDocumentId.value);
    }
    if (finalizeKey.present) {
      map['finalize_key'] = Variable<String>(finalizeKey.value);
    }
    if (attemptHistoryJson.present) {
      map['attempt_history_json'] = Variable<String>(attemptHistoryJson.value);
    }
    if (attemptCount.present) {
      map['attempt_count'] = Variable<int>(attemptCount.value);
    }
    if (nextRetryAt.present) {
      map['next_retry_at'] = Variable<DateTime>(nextRetryAt.value);
    }
    if (lastHttpStatus.present) {
      map['last_http_status'] = Variable<int>(lastHttpStatus.value);
    }
    if (lastErrorCode.present) {
      map['last_error_code'] = Variable<String>(lastErrorCode.value);
    }
    if (reviewRequired.present) {
      map['review_required'] = Variable<bool>(reviewRequired.value);
    }
    if (tripId.present) {
      map['trip_id'] = Variable<String>(tripId.value);
    }
    if (capturedAt.present) {
      map['captured_at'] = Variable<DateTime>(capturedAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (finalizedAt.present) {
      map['finalized_at'] = Variable<DateTime>(finalizedAt.value);
    }
    if (fileState.present) {
      map['file_state'] = Variable<String>(fileState.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalMediaCompanion(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('documentType: $documentType, ')
          ..write('contentType: $contentType, ')
          ..write('fileKey: $fileKey, ')
          ..write('sizeBytes: $sizeBytes, ')
          ..write('checksumSha256: $checksumSha256, ')
          ..write('state: $state, ')
          ..write('serverDocumentId: $serverDocumentId, ')
          ..write('finalizeKey: $finalizeKey, ')
          ..write('attemptHistoryJson: $attemptHistoryJson, ')
          ..write('attemptCount: $attemptCount, ')
          ..write('nextRetryAt: $nextRetryAt, ')
          ..write('lastHttpStatus: $lastHttpStatus, ')
          ..write('lastErrorCode: $lastErrorCode, ')
          ..write('reviewRequired: $reviewRequired, ')
          ..write('tripId: $tripId, ')
          ..write('capturedAt: $capturedAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('finalizedAt: $finalizedAt, ')
          ..write('fileState: $fileState, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CommandMediaDependenciesTable extends CommandMediaDependencies
    with TableInfo<$CommandMediaDependenciesTable, CommandMediaDependency> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CommandMediaDependenciesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _clientOperationIdMeta = const VerificationMeta(
    'clientOperationId',
  );
  @override
  late final GeneratedColumn<String> clientOperationId =
      GeneratedColumn<String>(
        'client_operation_id',
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
  static const VerificationMeta _mediaIdMeta = const VerificationMeta(
    'mediaId',
  );
  @override
  late final GeneratedColumn<String> mediaId = GeneratedColumn<String>(
    'media_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _fieldPathMeta = const VerificationMeta(
    'fieldPath',
  );
  @override
  late final GeneratedColumn<String> fieldPath = GeneratedColumn<String>(
    'field_path',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    clientOperationId,
    userId,
    tenantId,
    mediaId,
    fieldPath,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'command_media_dependencies';
  @override
  VerificationContext validateIntegrity(
    Insertable<CommandMediaDependency> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('client_operation_id')) {
      context.handle(
        _clientOperationIdMeta,
        clientOperationId.isAcceptableOrUnknown(
          data['client_operation_id']!,
          _clientOperationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_clientOperationIdMeta);
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
    if (data.containsKey('media_id')) {
      context.handle(
        _mediaIdMeta,
        mediaId.isAcceptableOrUnknown(data['media_id']!, _mediaIdMeta),
      );
    } else if (isInserting) {
      context.missing(_mediaIdMeta);
    }
    if (data.containsKey('field_path')) {
      context.handle(
        _fieldPathMeta,
        fieldPath.isAcceptableOrUnknown(data['field_path']!, _fieldPathMeta),
      );
    } else if (isInserting) {
      context.missing(_fieldPathMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {clientOperationId, fieldPath};
  @override
  CommandMediaDependency map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CommandMediaDependency(
      clientOperationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}client_operation_id'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      mediaId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}media_id'],
      )!,
      fieldPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}field_path'],
      )!,
    );
  }

  @override
  $CommandMediaDependenciesTable createAlias(String alias) {
    return $CommandMediaDependenciesTable(attachedDatabase, alias);
  }
}

class CommandMediaDependency extends DataClass
    implements Insertable<CommandMediaDependency> {
  final String clientOperationId;
  final String userId;
  final String tenantId;
  final String mediaId;
  final String fieldPath;
  const CommandMediaDependency({
    required this.clientOperationId,
    required this.userId,
    required this.tenantId,
    required this.mediaId,
    required this.fieldPath,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_operation_id'] = Variable<String>(clientOperationId);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['media_id'] = Variable<String>(mediaId);
    map['field_path'] = Variable<String>(fieldPath);
    return map;
  }

  CommandMediaDependenciesCompanion toCompanion(bool nullToAbsent) {
    return CommandMediaDependenciesCompanion(
      clientOperationId: Value(clientOperationId),
      userId: Value(userId),
      tenantId: Value(tenantId),
      mediaId: Value(mediaId),
      fieldPath: Value(fieldPath),
    );
  }

  factory CommandMediaDependency.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CommandMediaDependency(
      clientOperationId: serializer.fromJson<String>(json['clientOperationId']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      mediaId: serializer.fromJson<String>(json['mediaId']),
      fieldPath: serializer.fromJson<String>(json['fieldPath']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'clientOperationId': serializer.toJson<String>(clientOperationId),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'mediaId': serializer.toJson<String>(mediaId),
      'fieldPath': serializer.toJson<String>(fieldPath),
    };
  }

  CommandMediaDependency copyWith({
    String? clientOperationId,
    String? userId,
    String? tenantId,
    String? mediaId,
    String? fieldPath,
  }) => CommandMediaDependency(
    clientOperationId: clientOperationId ?? this.clientOperationId,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    mediaId: mediaId ?? this.mediaId,
    fieldPath: fieldPath ?? this.fieldPath,
  );
  CommandMediaDependency copyWithCompanion(
    CommandMediaDependenciesCompanion data,
  ) {
    return CommandMediaDependency(
      clientOperationId: data.clientOperationId.present
          ? data.clientOperationId.value
          : this.clientOperationId,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      mediaId: data.mediaId.present ? data.mediaId.value : this.mediaId,
      fieldPath: data.fieldPath.present ? data.fieldPath.value : this.fieldPath,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CommandMediaDependency(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('mediaId: $mediaId, ')
          ..write('fieldPath: $fieldPath')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(clientOperationId, userId, tenantId, mediaId, fieldPath);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CommandMediaDependency &&
          other.clientOperationId == this.clientOperationId &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.mediaId == this.mediaId &&
          other.fieldPath == this.fieldPath);
}

class CommandMediaDependenciesCompanion
    extends UpdateCompanion<CommandMediaDependency> {
  final Value<String> clientOperationId;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> mediaId;
  final Value<String> fieldPath;
  final Value<int> rowid;
  const CommandMediaDependenciesCompanion({
    this.clientOperationId = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.mediaId = const Value.absent(),
    this.fieldPath = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CommandMediaDependenciesCompanion.insert({
    required String clientOperationId,
    required String userId,
    required String tenantId,
    required String mediaId,
    required String fieldPath,
    this.rowid = const Value.absent(),
  }) : clientOperationId = Value(clientOperationId),
       userId = Value(userId),
       tenantId = Value(tenantId),
       mediaId = Value(mediaId),
       fieldPath = Value(fieldPath);
  static Insertable<CommandMediaDependency> custom({
    Expression<String>? clientOperationId,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? mediaId,
    Expression<String>? fieldPath,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientOperationId != null) 'client_operation_id': clientOperationId,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (mediaId != null) 'media_id': mediaId,
      if (fieldPath != null) 'field_path': fieldPath,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CommandMediaDependenciesCompanion copyWith({
    Value<String>? clientOperationId,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? mediaId,
    Value<String>? fieldPath,
    Value<int>? rowid,
  }) {
    return CommandMediaDependenciesCompanion(
      clientOperationId: clientOperationId ?? this.clientOperationId,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      mediaId: mediaId ?? this.mediaId,
      fieldPath: fieldPath ?? this.fieldPath,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (clientOperationId.present) {
      map['client_operation_id'] = Variable<String>(clientOperationId.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (mediaId.present) {
      map['media_id'] = Variable<String>(mediaId.value);
    }
    if (fieldPath.present) {
      map['field_path'] = Variable<String>(fieldPath.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CommandMediaDependenciesCompanion(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('mediaId: $mediaId, ')
          ..write('fieldPath: $fieldPath, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $MaterializedRequestsTable extends MaterializedRequests
    with TableInfo<$MaterializedRequestsTable, MaterializedRequest> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $MaterializedRequestsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _clientOperationIdMeta = const VerificationMeta(
    'clientOperationId',
  );
  @override
  late final GeneratedColumn<String> clientOperationId =
      GeneratedColumn<String>(
        'client_operation_id',
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
  static const VerificationMeta _endpointPathMeta = const VerificationMeta(
    'endpointPath',
  );
  @override
  late final GeneratedColumn<String> endpointPath = GeneratedColumn<String>(
    'endpoint_path',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _httpMethodMeta = const VerificationMeta(
    'httpMethod',
  );
  @override
  late final GeneratedColumn<String> httpMethod = GeneratedColumn<String>(
    'http_method',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _payloadJsonMeta = const VerificationMeta(
    'payloadJson',
  );
  @override
  late final GeneratedColumn<String> payloadJson = GeneratedColumn<String>(
    'payload_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    clientOperationId,
    userId,
    tenantId,
    endpointPath,
    httpMethod,
    payloadJson,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'materialized_requests';
  @override
  VerificationContext validateIntegrity(
    Insertable<MaterializedRequest> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('client_operation_id')) {
      context.handle(
        _clientOperationIdMeta,
        clientOperationId.isAcceptableOrUnknown(
          data['client_operation_id']!,
          _clientOperationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_clientOperationIdMeta);
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
    if (data.containsKey('endpoint_path')) {
      context.handle(
        _endpointPathMeta,
        endpointPath.isAcceptableOrUnknown(
          data['endpoint_path']!,
          _endpointPathMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_endpointPathMeta);
    }
    if (data.containsKey('http_method')) {
      context.handle(
        _httpMethodMeta,
        httpMethod.isAcceptableOrUnknown(data['http_method']!, _httpMethodMeta),
      );
    } else if (isInserting) {
      context.missing(_httpMethodMeta);
    }
    if (data.containsKey('payload_json')) {
      context.handle(
        _payloadJsonMeta,
        payloadJson.isAcceptableOrUnknown(
          data['payload_json']!,
          _payloadJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_payloadJsonMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {clientOperationId};
  @override
  MaterializedRequest map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return MaterializedRequest(
      clientOperationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}client_operation_id'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      endpointPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}endpoint_path'],
      )!,
      httpMethod: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}http_method'],
      )!,
      payloadJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payload_json'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $MaterializedRequestsTable createAlias(String alias) {
    return $MaterializedRequestsTable(attachedDatabase, alias);
  }
}

class MaterializedRequest extends DataClass
    implements Insertable<MaterializedRequest> {
  final String clientOperationId;
  final String userId;
  final String tenantId;
  final String endpointPath;
  final String httpMethod;
  final String payloadJson;
  final DateTime createdAt;
  const MaterializedRequest({
    required this.clientOperationId,
    required this.userId,
    required this.tenantId,
    required this.endpointPath,
    required this.httpMethod,
    required this.payloadJson,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_operation_id'] = Variable<String>(clientOperationId);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['endpoint_path'] = Variable<String>(endpointPath);
    map['http_method'] = Variable<String>(httpMethod);
    map['payload_json'] = Variable<String>(payloadJson);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  MaterializedRequestsCompanion toCompanion(bool nullToAbsent) {
    return MaterializedRequestsCompanion(
      clientOperationId: Value(clientOperationId),
      userId: Value(userId),
      tenantId: Value(tenantId),
      endpointPath: Value(endpointPath),
      httpMethod: Value(httpMethod),
      payloadJson: Value(payloadJson),
      createdAt: Value(createdAt),
    );
  }

  factory MaterializedRequest.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return MaterializedRequest(
      clientOperationId: serializer.fromJson<String>(json['clientOperationId']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      endpointPath: serializer.fromJson<String>(json['endpointPath']),
      httpMethod: serializer.fromJson<String>(json['httpMethod']),
      payloadJson: serializer.fromJson<String>(json['payloadJson']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'clientOperationId': serializer.toJson<String>(clientOperationId),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'endpointPath': serializer.toJson<String>(endpointPath),
      'httpMethod': serializer.toJson<String>(httpMethod),
      'payloadJson': serializer.toJson<String>(payloadJson),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  MaterializedRequest copyWith({
    String? clientOperationId,
    String? userId,
    String? tenantId,
    String? endpointPath,
    String? httpMethod,
    String? payloadJson,
    DateTime? createdAt,
  }) => MaterializedRequest(
    clientOperationId: clientOperationId ?? this.clientOperationId,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    endpointPath: endpointPath ?? this.endpointPath,
    httpMethod: httpMethod ?? this.httpMethod,
    payloadJson: payloadJson ?? this.payloadJson,
    createdAt: createdAt ?? this.createdAt,
  );
  MaterializedRequest copyWithCompanion(MaterializedRequestsCompanion data) {
    return MaterializedRequest(
      clientOperationId: data.clientOperationId.present
          ? data.clientOperationId.value
          : this.clientOperationId,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      endpointPath: data.endpointPath.present
          ? data.endpointPath.value
          : this.endpointPath,
      httpMethod: data.httpMethod.present
          ? data.httpMethod.value
          : this.httpMethod,
      payloadJson: data.payloadJson.present
          ? data.payloadJson.value
          : this.payloadJson,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('MaterializedRequest(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('endpointPath: $endpointPath, ')
          ..write('httpMethod: $httpMethod, ')
          ..write('payloadJson: $payloadJson, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    clientOperationId,
    userId,
    tenantId,
    endpointPath,
    httpMethod,
    payloadJson,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is MaterializedRequest &&
          other.clientOperationId == this.clientOperationId &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.endpointPath == this.endpointPath &&
          other.httpMethod == this.httpMethod &&
          other.payloadJson == this.payloadJson &&
          other.createdAt == this.createdAt);
}

class MaterializedRequestsCompanion
    extends UpdateCompanion<MaterializedRequest> {
  final Value<String> clientOperationId;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> endpointPath;
  final Value<String> httpMethod;
  final Value<String> payloadJson;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const MaterializedRequestsCompanion({
    this.clientOperationId = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.endpointPath = const Value.absent(),
    this.httpMethod = const Value.absent(),
    this.payloadJson = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  MaterializedRequestsCompanion.insert({
    required String clientOperationId,
    required String userId,
    required String tenantId,
    required String endpointPath,
    required String httpMethod,
    required String payloadJson,
    required DateTime createdAt,
    this.rowid = const Value.absent(),
  }) : clientOperationId = Value(clientOperationId),
       userId = Value(userId),
       tenantId = Value(tenantId),
       endpointPath = Value(endpointPath),
       httpMethod = Value(httpMethod),
       payloadJson = Value(payloadJson),
       createdAt = Value(createdAt);
  static Insertable<MaterializedRequest> custom({
    Expression<String>? clientOperationId,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? endpointPath,
    Expression<String>? httpMethod,
    Expression<String>? payloadJson,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientOperationId != null) 'client_operation_id': clientOperationId,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (endpointPath != null) 'endpoint_path': endpointPath,
      if (httpMethod != null) 'http_method': httpMethod,
      if (payloadJson != null) 'payload_json': payloadJson,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  MaterializedRequestsCompanion copyWith({
    Value<String>? clientOperationId,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? endpointPath,
    Value<String>? httpMethod,
    Value<String>? payloadJson,
    Value<DateTime>? createdAt,
    Value<int>? rowid,
  }) {
    return MaterializedRequestsCompanion(
      clientOperationId: clientOperationId ?? this.clientOperationId,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      endpointPath: endpointPath ?? this.endpointPath,
      httpMethod: httpMethod ?? this.httpMethod,
      payloadJson: payloadJson ?? this.payloadJson,
      createdAt: createdAt ?? this.createdAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (clientOperationId.present) {
      map['client_operation_id'] = Variable<String>(clientOperationId.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<String>(userId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (endpointPath.present) {
      map['endpoint_path'] = Variable<String>(endpointPath.value);
    }
    if (httpMethod.present) {
      map['http_method'] = Variable<String>(httpMethod.value);
    }
    if (payloadJson.present) {
      map['payload_json'] = Variable<String>(payloadJson.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('MaterializedRequestsCompanion(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('endpointPath: $endpointPath, ')
          ..write('httpMethod: $httpMethod, ')
          ..write('payloadJson: $payloadJson, ')
          ..write('createdAt: $createdAt, ')
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
  late final $LocalCommandsTable localCommands = $LocalCommandsTable(this);
  late final $LocalSyncIssuesTable localSyncIssues = $LocalSyncIssuesTable(
    this,
  );
  late final $LocalMediaTable localMedia = $LocalMediaTable(this);
  late final $CommandMediaDependenciesTable commandMediaDependencies =
      $CommandMediaDependenciesTable(this);
  late final $MaterializedRequestsTable materializedRequests =
      $MaterializedRequestsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    localSessionContexts,
    cachedTrips,
    cachedStops,
    cachedTrailers,
    localCommands,
    localSyncIssues,
    localMedia,
    commandMediaDependencies,
    materializedRequests,
  ];
}
