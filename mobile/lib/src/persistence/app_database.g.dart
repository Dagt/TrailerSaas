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
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _aggregateIdMeta = const VerificationMeta(
    'aggregateId',
  );
  @override
  late final GeneratedColumn<String> aggregateId = GeneratedColumn<String>(
    'aggregate_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _aggregateTypeMeta = const VerificationMeta(
    'aggregateType',
  );
  @override
  late final GeneratedColumn<String> aggregateType = GeneratedColumn<String>(
    'aggregate_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('TRIP'),
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
    aggregateType,
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
    }
    if (data.containsKey('aggregate_id')) {
      context.handle(
        _aggregateIdMeta,
        aggregateId.isAcceptableOrUnknown(
          data['aggregate_id']!,
          _aggregateIdMeta,
        ),
      );
    }
    if (data.containsKey('aggregate_type')) {
      context.handle(
        _aggregateTypeMeta,
        aggregateType.isAcceptableOrUnknown(
          data['aggregate_type']!,
          _aggregateTypeMeta,
        ),
      );
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
      ),
      aggregateId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}aggregate_id'],
      ),
      aggregateType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}aggregate_type'],
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
  final String? tripId;
  final String? aggregateId;
  final String aggregateType;
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
    this.tripId,
    this.aggregateId,
    required this.aggregateType,
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
    if (!nullToAbsent || tripId != null) {
      map['trip_id'] = Variable<String>(tripId);
    }
    if (!nullToAbsent || aggregateId != null) {
      map['aggregate_id'] = Variable<String>(aggregateId);
    }
    map['aggregate_type'] = Variable<String>(aggregateType);
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
      tripId: tripId == null && nullToAbsent
          ? const Value.absent()
          : Value(tripId),
      aggregateId: aggregateId == null && nullToAbsent
          ? const Value.absent()
          : Value(aggregateId),
      aggregateType: Value(aggregateType),
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
      tripId: serializer.fromJson<String?>(json['tripId']),
      aggregateId: serializer.fromJson<String?>(json['aggregateId']),
      aggregateType: serializer.fromJson<String>(json['aggregateType']),
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
      'tripId': serializer.toJson<String?>(tripId),
      'aggregateId': serializer.toJson<String?>(aggregateId),
      'aggregateType': serializer.toJson<String>(aggregateType),
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
    Value<String?> tripId = const Value.absent(),
    Value<String?> aggregateId = const Value.absent(),
    String? aggregateType,
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
    tripId: tripId.present ? tripId.value : this.tripId,
    aggregateId: aggregateId.present ? aggregateId.value : this.aggregateId,
    aggregateType: aggregateType ?? this.aggregateType,
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
      aggregateType: data.aggregateType.present
          ? data.aggregateType.value
          : this.aggregateType,
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
          ..write('aggregateType: $aggregateType, ')
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
  int get hashCode => Object.hashAll([
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
    aggregateType,
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
  ]);
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
          other.aggregateType == this.aggregateType &&
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
  final Value<String?> tripId;
  final Value<String?> aggregateId;
  final Value<String> aggregateType;
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
    this.aggregateType = const Value.absent(),
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
    this.tripId = const Value.absent(),
    this.aggregateId = const Value.absent(),
    this.aggregateType = const Value.absent(),
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
    Expression<String>? aggregateType,
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
      if (aggregateType != null) 'aggregate_type': aggregateType,
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
    Value<String?>? tripId,
    Value<String?>? aggregateId,
    Value<String>? aggregateType,
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
      aggregateType: aggregateType ?? this.aggregateType,
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
    if (aggregateType.present) {
      map['aggregate_type'] = Variable<String>(aggregateType.value);
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
          ..write('aggregateType: $aggregateType, ')
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
  static const VerificationMeta _retainOfflineMeta = const VerificationMeta(
    'retainOffline',
  );
  @override
  late final GeneratedColumn<bool> retainOffline = GeneratedColumn<bool>(
    'retain_offline',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("retain_offline" IN (0, 1))',
    ),
    defaultValue: const Constant(true),
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
    retainOffline,
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
    if (data.containsKey('retain_offline')) {
      context.handle(
        _retainOfflineMeta,
        retainOffline.isAcceptableOrUnknown(
          data['retain_offline']!,
          _retainOfflineMeta,
        ),
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
      retainOffline: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}retain_offline'],
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
  final bool retainOffline;
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
    required this.retainOffline,
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
    map['retain_offline'] = Variable<bool>(retainOffline);
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
      retainOffline: Value(retainOffline),
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
      retainOffline: serializer.fromJson<bool>(json['retainOffline']),
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
      'retainOffline': serializer.toJson<bool>(retainOffline),
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
    bool? retainOffline,
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
    retainOffline: retainOffline ?? this.retainOffline,
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
      retainOffline: data.retainOffline.present
          ? data.retainOffline.value
          : this.retainOffline,
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
          ..write('fileState: $fileState, ')
          ..write('retainOffline: $retainOffline')
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
    retainOffline,
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
          other.fileState == this.fileState &&
          other.retainOffline == this.retainOffline);
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
  final Value<bool> retainOffline;
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
    this.retainOffline = const Value.absent(),
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
    this.retainOffline = const Value.absent(),
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
    Expression<bool>? retainOffline,
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
      if (retainOffline != null) 'retain_offline': retainOffline,
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
    Value<bool>? retainOffline,
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
      retainOffline: retainOffline ?? this.retainOffline,
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
    if (retainOffline.present) {
      map['retain_offline'] = Variable<bool>(retainOffline.value);
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
          ..write('retainOffline: $retainOffline, ')
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

class $CommandResultsTable extends CommandResults
    with TableInfo<$CommandResultsTable, CommandResult> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CommandResultsTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _resultKindMeta = const VerificationMeta(
    'resultKind',
  );
  @override
  late final GeneratedColumn<String> resultKind = GeneratedColumn<String>(
    'result_kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _serverIdMeta = const VerificationMeta(
    'serverId',
  );
  @override
  late final GeneratedColumn<String> serverId = GeneratedColumn<String>(
    'server_id',
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
  static const VerificationMeta _confirmedAtMeta = const VerificationMeta(
    'confirmedAt',
  );
  @override
  late final GeneratedColumn<DateTime> confirmedAt = GeneratedColumn<DateTime>(
    'confirmed_at',
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
    resultKind,
    serverId,
    projectionJson,
    confirmedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'command_results';
  @override
  VerificationContext validateIntegrity(
    Insertable<CommandResult> instance, {
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
    if (data.containsKey('result_kind')) {
      context.handle(
        _resultKindMeta,
        resultKind.isAcceptableOrUnknown(data['result_kind']!, _resultKindMeta),
      );
    } else if (isInserting) {
      context.missing(_resultKindMeta);
    }
    if (data.containsKey('server_id')) {
      context.handle(
        _serverIdMeta,
        serverId.isAcceptableOrUnknown(data['server_id']!, _serverIdMeta),
      );
    } else if (isInserting) {
      context.missing(_serverIdMeta);
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
    if (data.containsKey('confirmed_at')) {
      context.handle(
        _confirmedAtMeta,
        confirmedAt.isAcceptableOrUnknown(
          data['confirmed_at']!,
          _confirmedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_confirmedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {clientOperationId};
  @override
  CommandResult map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CommandResult(
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
      resultKind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}result_kind'],
      )!,
      serverId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_id'],
      )!,
      projectionJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}projection_json'],
      )!,
      confirmedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}confirmed_at'],
      )!,
    );
  }

  @override
  $CommandResultsTable createAlias(String alias) {
    return $CommandResultsTable(attachedDatabase, alias);
  }
}

class CommandResult extends DataClass implements Insertable<CommandResult> {
  final String clientOperationId;
  final String userId;
  final String tenantId;
  final String resultKind;
  final String serverId;
  final String projectionJson;
  final DateTime confirmedAt;
  const CommandResult({
    required this.clientOperationId,
    required this.userId,
    required this.tenantId,
    required this.resultKind,
    required this.serverId,
    required this.projectionJson,
    required this.confirmedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_operation_id'] = Variable<String>(clientOperationId);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['result_kind'] = Variable<String>(resultKind);
    map['server_id'] = Variable<String>(serverId);
    map['projection_json'] = Variable<String>(projectionJson);
    map['confirmed_at'] = Variable<DateTime>(confirmedAt);
    return map;
  }

  CommandResultsCompanion toCompanion(bool nullToAbsent) {
    return CommandResultsCompanion(
      clientOperationId: Value(clientOperationId),
      userId: Value(userId),
      tenantId: Value(tenantId),
      resultKind: Value(resultKind),
      serverId: Value(serverId),
      projectionJson: Value(projectionJson),
      confirmedAt: Value(confirmedAt),
    );
  }

  factory CommandResult.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CommandResult(
      clientOperationId: serializer.fromJson<String>(json['clientOperationId']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      resultKind: serializer.fromJson<String>(json['resultKind']),
      serverId: serializer.fromJson<String>(json['serverId']),
      projectionJson: serializer.fromJson<String>(json['projectionJson']),
      confirmedAt: serializer.fromJson<DateTime>(json['confirmedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'clientOperationId': serializer.toJson<String>(clientOperationId),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'resultKind': serializer.toJson<String>(resultKind),
      'serverId': serializer.toJson<String>(serverId),
      'projectionJson': serializer.toJson<String>(projectionJson),
      'confirmedAt': serializer.toJson<DateTime>(confirmedAt),
    };
  }

  CommandResult copyWith({
    String? clientOperationId,
    String? userId,
    String? tenantId,
    String? resultKind,
    String? serverId,
    String? projectionJson,
    DateTime? confirmedAt,
  }) => CommandResult(
    clientOperationId: clientOperationId ?? this.clientOperationId,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    resultKind: resultKind ?? this.resultKind,
    serverId: serverId ?? this.serverId,
    projectionJson: projectionJson ?? this.projectionJson,
    confirmedAt: confirmedAt ?? this.confirmedAt,
  );
  CommandResult copyWithCompanion(CommandResultsCompanion data) {
    return CommandResult(
      clientOperationId: data.clientOperationId.present
          ? data.clientOperationId.value
          : this.clientOperationId,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      resultKind: data.resultKind.present
          ? data.resultKind.value
          : this.resultKind,
      serverId: data.serverId.present ? data.serverId.value : this.serverId,
      projectionJson: data.projectionJson.present
          ? data.projectionJson.value
          : this.projectionJson,
      confirmedAt: data.confirmedAt.present
          ? data.confirmedAt.value
          : this.confirmedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CommandResult(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('resultKind: $resultKind, ')
          ..write('serverId: $serverId, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('confirmedAt: $confirmedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    clientOperationId,
    userId,
    tenantId,
    resultKind,
    serverId,
    projectionJson,
    confirmedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CommandResult &&
          other.clientOperationId == this.clientOperationId &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.resultKind == this.resultKind &&
          other.serverId == this.serverId &&
          other.projectionJson == this.projectionJson &&
          other.confirmedAt == this.confirmedAt);
}

class CommandResultsCompanion extends UpdateCompanion<CommandResult> {
  final Value<String> clientOperationId;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> resultKind;
  final Value<String> serverId;
  final Value<String> projectionJson;
  final Value<DateTime> confirmedAt;
  final Value<int> rowid;
  const CommandResultsCompanion({
    this.clientOperationId = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.resultKind = const Value.absent(),
    this.serverId = const Value.absent(),
    this.projectionJson = const Value.absent(),
    this.confirmedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CommandResultsCompanion.insert({
    required String clientOperationId,
    required String userId,
    required String tenantId,
    required String resultKind,
    required String serverId,
    required String projectionJson,
    required DateTime confirmedAt,
    this.rowid = const Value.absent(),
  }) : clientOperationId = Value(clientOperationId),
       userId = Value(userId),
       tenantId = Value(tenantId),
       resultKind = Value(resultKind),
       serverId = Value(serverId),
       projectionJson = Value(projectionJson),
       confirmedAt = Value(confirmedAt);
  static Insertable<CommandResult> custom({
    Expression<String>? clientOperationId,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? resultKind,
    Expression<String>? serverId,
    Expression<String>? projectionJson,
    Expression<DateTime>? confirmedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientOperationId != null) 'client_operation_id': clientOperationId,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (resultKind != null) 'result_kind': resultKind,
      if (serverId != null) 'server_id': serverId,
      if (projectionJson != null) 'projection_json': projectionJson,
      if (confirmedAt != null) 'confirmed_at': confirmedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CommandResultsCompanion copyWith({
    Value<String>? clientOperationId,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? resultKind,
    Value<String>? serverId,
    Value<String>? projectionJson,
    Value<DateTime>? confirmedAt,
    Value<int>? rowid,
  }) {
    return CommandResultsCompanion(
      clientOperationId: clientOperationId ?? this.clientOperationId,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      resultKind: resultKind ?? this.resultKind,
      serverId: serverId ?? this.serverId,
      projectionJson: projectionJson ?? this.projectionJson,
      confirmedAt: confirmedAt ?? this.confirmedAt,
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
    if (resultKind.present) {
      map['result_kind'] = Variable<String>(resultKind.value);
    }
    if (serverId.present) {
      map['server_id'] = Variable<String>(serverId.value);
    }
    if (projectionJson.present) {
      map['projection_json'] = Variable<String>(projectionJson.value);
    }
    if (confirmedAt.present) {
      map['confirmed_at'] = Variable<DateTime>(confirmedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CommandResultsCompanion(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('resultKind: $resultKind, ')
          ..write('serverId: $serverId, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('confirmedAt: $confirmedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CommandResultDependenciesTable extends CommandResultDependencies
    with TableInfo<$CommandResultDependenciesTable, CommandResultDependency> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CommandResultDependenciesTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _sourceOperationIdMeta = const VerificationMeta(
    'sourceOperationId',
  );
  @override
  late final GeneratedColumn<String> sourceOperationId =
      GeneratedColumn<String>(
        'source_operation_id',
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
    sourceOperationId,
    fieldPath,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'command_result_dependencies';
  @override
  VerificationContext validateIntegrity(
    Insertable<CommandResultDependency> instance, {
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
    if (data.containsKey('source_operation_id')) {
      context.handle(
        _sourceOperationIdMeta,
        sourceOperationId.isAcceptableOrUnknown(
          data['source_operation_id']!,
          _sourceOperationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sourceOperationIdMeta);
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
  CommandResultDependency map(
    Map<String, dynamic> data, {
    String? tablePrefix,
  }) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CommandResultDependency(
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
      sourceOperationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_operation_id'],
      )!,
      fieldPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}field_path'],
      )!,
    );
  }

  @override
  $CommandResultDependenciesTable createAlias(String alias) {
    return $CommandResultDependenciesTable(attachedDatabase, alias);
  }
}

class CommandResultDependency extends DataClass
    implements Insertable<CommandResultDependency> {
  final String clientOperationId;
  final String userId;
  final String tenantId;
  final String sourceOperationId;
  final String fieldPath;
  const CommandResultDependency({
    required this.clientOperationId,
    required this.userId,
    required this.tenantId,
    required this.sourceOperationId,
    required this.fieldPath,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_operation_id'] = Variable<String>(clientOperationId);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['source_operation_id'] = Variable<String>(sourceOperationId);
    map['field_path'] = Variable<String>(fieldPath);
    return map;
  }

  CommandResultDependenciesCompanion toCompanion(bool nullToAbsent) {
    return CommandResultDependenciesCompanion(
      clientOperationId: Value(clientOperationId),
      userId: Value(userId),
      tenantId: Value(tenantId),
      sourceOperationId: Value(sourceOperationId),
      fieldPath: Value(fieldPath),
    );
  }

  factory CommandResultDependency.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CommandResultDependency(
      clientOperationId: serializer.fromJson<String>(json['clientOperationId']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      sourceOperationId: serializer.fromJson<String>(json['sourceOperationId']),
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
      'sourceOperationId': serializer.toJson<String>(sourceOperationId),
      'fieldPath': serializer.toJson<String>(fieldPath),
    };
  }

  CommandResultDependency copyWith({
    String? clientOperationId,
    String? userId,
    String? tenantId,
    String? sourceOperationId,
    String? fieldPath,
  }) => CommandResultDependency(
    clientOperationId: clientOperationId ?? this.clientOperationId,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    sourceOperationId: sourceOperationId ?? this.sourceOperationId,
    fieldPath: fieldPath ?? this.fieldPath,
  );
  CommandResultDependency copyWithCompanion(
    CommandResultDependenciesCompanion data,
  ) {
    return CommandResultDependency(
      clientOperationId: data.clientOperationId.present
          ? data.clientOperationId.value
          : this.clientOperationId,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      sourceOperationId: data.sourceOperationId.present
          ? data.sourceOperationId.value
          : this.sourceOperationId,
      fieldPath: data.fieldPath.present ? data.fieldPath.value : this.fieldPath,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CommandResultDependency(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('sourceOperationId: $sourceOperationId, ')
          ..write('fieldPath: $fieldPath')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    clientOperationId,
    userId,
    tenantId,
    sourceOperationId,
    fieldPath,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CommandResultDependency &&
          other.clientOperationId == this.clientOperationId &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.sourceOperationId == this.sourceOperationId &&
          other.fieldPath == this.fieldPath);
}

class CommandResultDependenciesCompanion
    extends UpdateCompanion<CommandResultDependency> {
  final Value<String> clientOperationId;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> sourceOperationId;
  final Value<String> fieldPath;
  final Value<int> rowid;
  const CommandResultDependenciesCompanion({
    this.clientOperationId = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.sourceOperationId = const Value.absent(),
    this.fieldPath = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CommandResultDependenciesCompanion.insert({
    required String clientOperationId,
    required String userId,
    required String tenantId,
    required String sourceOperationId,
    required String fieldPath,
    this.rowid = const Value.absent(),
  }) : clientOperationId = Value(clientOperationId),
       userId = Value(userId),
       tenantId = Value(tenantId),
       sourceOperationId = Value(sourceOperationId),
       fieldPath = Value(fieldPath);
  static Insertable<CommandResultDependency> custom({
    Expression<String>? clientOperationId,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? sourceOperationId,
    Expression<String>? fieldPath,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientOperationId != null) 'client_operation_id': clientOperationId,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (sourceOperationId != null) 'source_operation_id': sourceOperationId,
      if (fieldPath != null) 'field_path': fieldPath,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CommandResultDependenciesCompanion copyWith({
    Value<String>? clientOperationId,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? sourceOperationId,
    Value<String>? fieldPath,
    Value<int>? rowid,
  }) {
    return CommandResultDependenciesCompanion(
      clientOperationId: clientOperationId ?? this.clientOperationId,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      sourceOperationId: sourceOperationId ?? this.sourceOperationId,
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
    if (sourceOperationId.present) {
      map['source_operation_id'] = Variable<String>(sourceOperationId.value);
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
    return (StringBuffer('CommandResultDependenciesCompanion(')
          ..write('clientOperationId: $clientOperationId, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('sourceOperationId: $sourceOperationId, ')
          ..write('fieldPath: $fieldPath, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $InspectionDraftsTable extends InspectionDrafts
    with TableInfo<$InspectionDraftsTable, InspectionDraft> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $InspectionDraftsTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _inspectionTypeMeta = const VerificationMeta(
    'inspectionType',
  );
  @override
  late final GeneratedColumn<String> inspectionType = GeneratedColumn<String>(
    'inspection_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _shiftReferenceJsonMeta =
      const VerificationMeta('shiftReferenceJson');
  @override
  late final GeneratedColumn<String> shiftReferenceJson =
      GeneratedColumn<String>(
        'shift_reference_json',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _tractorIdMeta = const VerificationMeta(
    'tractorId',
  );
  @override
  late final GeneratedColumn<String> tractorId = GeneratedColumn<String>(
    'tractor_id',
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
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _odometerMeta = const VerificationMeta(
    'odometer',
  );
  @override
  late final GeneratedColumn<String> odometer = GeneratedColumn<String>(
    'odometer',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _checklistJsonMeta = const VerificationMeta(
    'checklistJson',
  );
  @override
  late final GeneratedColumn<String> checklistJson = GeneratedColumn<String>(
    'checklist_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('{"items":[]}'),
  );
  static const VerificationMeta _photosJsonMeta = const VerificationMeta(
    'photosJson',
  );
  @override
  late final GeneratedColumn<String> photosJson = GeneratedColumn<String>(
    'photos_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('{}'),
  );
  static const VerificationMeta _damagesMeta = const VerificationMeta(
    'damages',
  );
  @override
  late final GeneratedColumn<String> damages = GeneratedColumn<String>(
    'damages',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _failuresMeta = const VerificationMeta(
    'failures',
  );
  @override
  late final GeneratedColumn<String> failures = GeneratedColumn<String>(
    'failures',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _commentsMeta = const VerificationMeta(
    'comments',
  );
  @override
  late final GeneratedColumn<String> comments = GeneratedColumn<String>(
    'comments',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _commandIdMeta = const VerificationMeta(
    'commandId',
  );
  @override
  late final GeneratedColumn<String> commandId = GeneratedColumn<String>(
    'command_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
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
  @override
  List<GeneratedColumn> get $columns => [
    id,
    userId,
    tenantId,
    inspectionType,
    shiftReferenceJson,
    tractorId,
    tripId,
    odometer,
    checklistJson,
    photosJson,
    damages,
    failures,
    comments,
    commandId,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'inspection_drafts';
  @override
  VerificationContext validateIntegrity(
    Insertable<InspectionDraft> instance, {
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
    if (data.containsKey('inspection_type')) {
      context.handle(
        _inspectionTypeMeta,
        inspectionType.isAcceptableOrUnknown(
          data['inspection_type']!,
          _inspectionTypeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_inspectionTypeMeta);
    }
    if (data.containsKey('shift_reference_json')) {
      context.handle(
        _shiftReferenceJsonMeta,
        shiftReferenceJson.isAcceptableOrUnknown(
          data['shift_reference_json']!,
          _shiftReferenceJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_shiftReferenceJsonMeta);
    }
    if (data.containsKey('tractor_id')) {
      context.handle(
        _tractorIdMeta,
        tractorId.isAcceptableOrUnknown(data['tractor_id']!, _tractorIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tractorIdMeta);
    }
    if (data.containsKey('trip_id')) {
      context.handle(
        _tripIdMeta,
        tripId.isAcceptableOrUnknown(data['trip_id']!, _tripIdMeta),
      );
    }
    if (data.containsKey('odometer')) {
      context.handle(
        _odometerMeta,
        odometer.isAcceptableOrUnknown(data['odometer']!, _odometerMeta),
      );
    }
    if (data.containsKey('checklist_json')) {
      context.handle(
        _checklistJsonMeta,
        checklistJson.isAcceptableOrUnknown(
          data['checklist_json']!,
          _checklistJsonMeta,
        ),
      );
    }
    if (data.containsKey('photos_json')) {
      context.handle(
        _photosJsonMeta,
        photosJson.isAcceptableOrUnknown(data['photos_json']!, _photosJsonMeta),
      );
    }
    if (data.containsKey('damages')) {
      context.handle(
        _damagesMeta,
        damages.isAcceptableOrUnknown(data['damages']!, _damagesMeta),
      );
    }
    if (data.containsKey('failures')) {
      context.handle(
        _failuresMeta,
        failures.isAcceptableOrUnknown(data['failures']!, _failuresMeta),
      );
    }
    if (data.containsKey('comments')) {
      context.handle(
        _commentsMeta,
        comments.isAcceptableOrUnknown(data['comments']!, _commentsMeta),
      );
    }
    if (data.containsKey('command_id')) {
      context.handle(
        _commandIdMeta,
        commandId.isAcceptableOrUnknown(data['command_id']!, _commandIdMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  InspectionDraft map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return InspectionDraft(
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
      inspectionType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}inspection_type'],
      )!,
      shiftReferenceJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}shift_reference_json'],
      )!,
      tractorId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tractor_id'],
      )!,
      tripId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trip_id'],
      ),
      odometer: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}odometer'],
      )!,
      checklistJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}checklist_json'],
      )!,
      photosJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}photos_json'],
      )!,
      damages: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}damages'],
      ),
      failures: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}failures'],
      ),
      comments: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}comments'],
      ),
      commandId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}command_id'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $InspectionDraftsTable createAlias(String alias) {
    return $InspectionDraftsTable(attachedDatabase, alias);
  }
}

class InspectionDraft extends DataClass implements Insertable<InspectionDraft> {
  final String id;
  final String userId;
  final String tenantId;
  final String inspectionType;
  final String shiftReferenceJson;
  final String tractorId;
  final String? tripId;
  final String odometer;
  final String checklistJson;
  final String photosJson;
  final String? damages;
  final String? failures;
  final String? comments;
  final String? commandId;
  final DateTime updatedAt;
  const InspectionDraft({
    required this.id,
    required this.userId,
    required this.tenantId,
    required this.inspectionType,
    required this.shiftReferenceJson,
    required this.tractorId,
    this.tripId,
    required this.odometer,
    required this.checklistJson,
    required this.photosJson,
    this.damages,
    this.failures,
    this.comments,
    this.commandId,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['inspection_type'] = Variable<String>(inspectionType);
    map['shift_reference_json'] = Variable<String>(shiftReferenceJson);
    map['tractor_id'] = Variable<String>(tractorId);
    if (!nullToAbsent || tripId != null) {
      map['trip_id'] = Variable<String>(tripId);
    }
    map['odometer'] = Variable<String>(odometer);
    map['checklist_json'] = Variable<String>(checklistJson);
    map['photos_json'] = Variable<String>(photosJson);
    if (!nullToAbsent || damages != null) {
      map['damages'] = Variable<String>(damages);
    }
    if (!nullToAbsent || failures != null) {
      map['failures'] = Variable<String>(failures);
    }
    if (!nullToAbsent || comments != null) {
      map['comments'] = Variable<String>(comments);
    }
    if (!nullToAbsent || commandId != null) {
      map['command_id'] = Variable<String>(commandId);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  InspectionDraftsCompanion toCompanion(bool nullToAbsent) {
    return InspectionDraftsCompanion(
      id: Value(id),
      userId: Value(userId),
      tenantId: Value(tenantId),
      inspectionType: Value(inspectionType),
      shiftReferenceJson: Value(shiftReferenceJson),
      tractorId: Value(tractorId),
      tripId: tripId == null && nullToAbsent
          ? const Value.absent()
          : Value(tripId),
      odometer: Value(odometer),
      checklistJson: Value(checklistJson),
      photosJson: Value(photosJson),
      damages: damages == null && nullToAbsent
          ? const Value.absent()
          : Value(damages),
      failures: failures == null && nullToAbsent
          ? const Value.absent()
          : Value(failures),
      comments: comments == null && nullToAbsent
          ? const Value.absent()
          : Value(comments),
      commandId: commandId == null && nullToAbsent
          ? const Value.absent()
          : Value(commandId),
      updatedAt: Value(updatedAt),
    );
  }

  factory InspectionDraft.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return InspectionDraft(
      id: serializer.fromJson<String>(json['id']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      inspectionType: serializer.fromJson<String>(json['inspectionType']),
      shiftReferenceJson: serializer.fromJson<String>(
        json['shiftReferenceJson'],
      ),
      tractorId: serializer.fromJson<String>(json['tractorId']),
      tripId: serializer.fromJson<String?>(json['tripId']),
      odometer: serializer.fromJson<String>(json['odometer']),
      checklistJson: serializer.fromJson<String>(json['checklistJson']),
      photosJson: serializer.fromJson<String>(json['photosJson']),
      damages: serializer.fromJson<String?>(json['damages']),
      failures: serializer.fromJson<String?>(json['failures']),
      comments: serializer.fromJson<String?>(json['comments']),
      commandId: serializer.fromJson<String?>(json['commandId']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'inspectionType': serializer.toJson<String>(inspectionType),
      'shiftReferenceJson': serializer.toJson<String>(shiftReferenceJson),
      'tractorId': serializer.toJson<String>(tractorId),
      'tripId': serializer.toJson<String?>(tripId),
      'odometer': serializer.toJson<String>(odometer),
      'checklistJson': serializer.toJson<String>(checklistJson),
      'photosJson': serializer.toJson<String>(photosJson),
      'damages': serializer.toJson<String?>(damages),
      'failures': serializer.toJson<String?>(failures),
      'comments': serializer.toJson<String?>(comments),
      'commandId': serializer.toJson<String?>(commandId),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  InspectionDraft copyWith({
    String? id,
    String? userId,
    String? tenantId,
    String? inspectionType,
    String? shiftReferenceJson,
    String? tractorId,
    Value<String?> tripId = const Value.absent(),
    String? odometer,
    String? checklistJson,
    String? photosJson,
    Value<String?> damages = const Value.absent(),
    Value<String?> failures = const Value.absent(),
    Value<String?> comments = const Value.absent(),
    Value<String?> commandId = const Value.absent(),
    DateTime? updatedAt,
  }) => InspectionDraft(
    id: id ?? this.id,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    inspectionType: inspectionType ?? this.inspectionType,
    shiftReferenceJson: shiftReferenceJson ?? this.shiftReferenceJson,
    tractorId: tractorId ?? this.tractorId,
    tripId: tripId.present ? tripId.value : this.tripId,
    odometer: odometer ?? this.odometer,
    checklistJson: checklistJson ?? this.checklistJson,
    photosJson: photosJson ?? this.photosJson,
    damages: damages.present ? damages.value : this.damages,
    failures: failures.present ? failures.value : this.failures,
    comments: comments.present ? comments.value : this.comments,
    commandId: commandId.present ? commandId.value : this.commandId,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  InspectionDraft copyWithCompanion(InspectionDraftsCompanion data) {
    return InspectionDraft(
      id: data.id.present ? data.id.value : this.id,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      inspectionType: data.inspectionType.present
          ? data.inspectionType.value
          : this.inspectionType,
      shiftReferenceJson: data.shiftReferenceJson.present
          ? data.shiftReferenceJson.value
          : this.shiftReferenceJson,
      tractorId: data.tractorId.present ? data.tractorId.value : this.tractorId,
      tripId: data.tripId.present ? data.tripId.value : this.tripId,
      odometer: data.odometer.present ? data.odometer.value : this.odometer,
      checklistJson: data.checklistJson.present
          ? data.checklistJson.value
          : this.checklistJson,
      photosJson: data.photosJson.present
          ? data.photosJson.value
          : this.photosJson,
      damages: data.damages.present ? data.damages.value : this.damages,
      failures: data.failures.present ? data.failures.value : this.failures,
      comments: data.comments.present ? data.comments.value : this.comments,
      commandId: data.commandId.present ? data.commandId.value : this.commandId,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('InspectionDraft(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('inspectionType: $inspectionType, ')
          ..write('shiftReferenceJson: $shiftReferenceJson, ')
          ..write('tractorId: $tractorId, ')
          ..write('tripId: $tripId, ')
          ..write('odometer: $odometer, ')
          ..write('checklistJson: $checklistJson, ')
          ..write('photosJson: $photosJson, ')
          ..write('damages: $damages, ')
          ..write('failures: $failures, ')
          ..write('comments: $comments, ')
          ..write('commandId: $commandId, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    userId,
    tenantId,
    inspectionType,
    shiftReferenceJson,
    tractorId,
    tripId,
    odometer,
    checklistJson,
    photosJson,
    damages,
    failures,
    comments,
    commandId,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is InspectionDraft &&
          other.id == this.id &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.inspectionType == this.inspectionType &&
          other.shiftReferenceJson == this.shiftReferenceJson &&
          other.tractorId == this.tractorId &&
          other.tripId == this.tripId &&
          other.odometer == this.odometer &&
          other.checklistJson == this.checklistJson &&
          other.photosJson == this.photosJson &&
          other.damages == this.damages &&
          other.failures == this.failures &&
          other.comments == this.comments &&
          other.commandId == this.commandId &&
          other.updatedAt == this.updatedAt);
}

class InspectionDraftsCompanion extends UpdateCompanion<InspectionDraft> {
  final Value<String> id;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> inspectionType;
  final Value<String> shiftReferenceJson;
  final Value<String> tractorId;
  final Value<String?> tripId;
  final Value<String> odometer;
  final Value<String> checklistJson;
  final Value<String> photosJson;
  final Value<String?> damages;
  final Value<String?> failures;
  final Value<String?> comments;
  final Value<String?> commandId;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const InspectionDraftsCompanion({
    this.id = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.inspectionType = const Value.absent(),
    this.shiftReferenceJson = const Value.absent(),
    this.tractorId = const Value.absent(),
    this.tripId = const Value.absent(),
    this.odometer = const Value.absent(),
    this.checklistJson = const Value.absent(),
    this.photosJson = const Value.absent(),
    this.damages = const Value.absent(),
    this.failures = const Value.absent(),
    this.comments = const Value.absent(),
    this.commandId = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  InspectionDraftsCompanion.insert({
    required String id,
    required String userId,
    required String tenantId,
    required String inspectionType,
    required String shiftReferenceJson,
    required String tractorId,
    this.tripId = const Value.absent(),
    this.odometer = const Value.absent(),
    this.checklistJson = const Value.absent(),
    this.photosJson = const Value.absent(),
    this.damages = const Value.absent(),
    this.failures = const Value.absent(),
    this.comments = const Value.absent(),
    this.commandId = const Value.absent(),
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       userId = Value(userId),
       tenantId = Value(tenantId),
       inspectionType = Value(inspectionType),
       shiftReferenceJson = Value(shiftReferenceJson),
       tractorId = Value(tractorId),
       updatedAt = Value(updatedAt);
  static Insertable<InspectionDraft> custom({
    Expression<String>? id,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? inspectionType,
    Expression<String>? shiftReferenceJson,
    Expression<String>? tractorId,
    Expression<String>? tripId,
    Expression<String>? odometer,
    Expression<String>? checklistJson,
    Expression<String>? photosJson,
    Expression<String>? damages,
    Expression<String>? failures,
    Expression<String>? comments,
    Expression<String>? commandId,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (inspectionType != null) 'inspection_type': inspectionType,
      if (shiftReferenceJson != null)
        'shift_reference_json': shiftReferenceJson,
      if (tractorId != null) 'tractor_id': tractorId,
      if (tripId != null) 'trip_id': tripId,
      if (odometer != null) 'odometer': odometer,
      if (checklistJson != null) 'checklist_json': checklistJson,
      if (photosJson != null) 'photos_json': photosJson,
      if (damages != null) 'damages': damages,
      if (failures != null) 'failures': failures,
      if (comments != null) 'comments': comments,
      if (commandId != null) 'command_id': commandId,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  InspectionDraftsCompanion copyWith({
    Value<String>? id,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? inspectionType,
    Value<String>? shiftReferenceJson,
    Value<String>? tractorId,
    Value<String?>? tripId,
    Value<String>? odometer,
    Value<String>? checklistJson,
    Value<String>? photosJson,
    Value<String?>? damages,
    Value<String?>? failures,
    Value<String?>? comments,
    Value<String?>? commandId,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return InspectionDraftsCompanion(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      inspectionType: inspectionType ?? this.inspectionType,
      shiftReferenceJson: shiftReferenceJson ?? this.shiftReferenceJson,
      tractorId: tractorId ?? this.tractorId,
      tripId: tripId ?? this.tripId,
      odometer: odometer ?? this.odometer,
      checklistJson: checklistJson ?? this.checklistJson,
      photosJson: photosJson ?? this.photosJson,
      damages: damages ?? this.damages,
      failures: failures ?? this.failures,
      comments: comments ?? this.comments,
      commandId: commandId ?? this.commandId,
      updatedAt: updatedAt ?? this.updatedAt,
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
    if (inspectionType.present) {
      map['inspection_type'] = Variable<String>(inspectionType.value);
    }
    if (shiftReferenceJson.present) {
      map['shift_reference_json'] = Variable<String>(shiftReferenceJson.value);
    }
    if (tractorId.present) {
      map['tractor_id'] = Variable<String>(tractorId.value);
    }
    if (tripId.present) {
      map['trip_id'] = Variable<String>(tripId.value);
    }
    if (odometer.present) {
      map['odometer'] = Variable<String>(odometer.value);
    }
    if (checklistJson.present) {
      map['checklist_json'] = Variable<String>(checklistJson.value);
    }
    if (photosJson.present) {
      map['photos_json'] = Variable<String>(photosJson.value);
    }
    if (damages.present) {
      map['damages'] = Variable<String>(damages.value);
    }
    if (failures.present) {
      map['failures'] = Variable<String>(failures.value);
    }
    if (comments.present) {
      map['comments'] = Variable<String>(comments.value);
    }
    if (commandId.present) {
      map['command_id'] = Variable<String>(commandId.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('InspectionDraftsCompanion(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('inspectionType: $inspectionType, ')
          ..write('shiftReferenceJson: $shiftReferenceJson, ')
          ..write('tractorId: $tractorId, ')
          ..write('tripId: $tripId, ')
          ..write('odometer: $odometer, ')
          ..write('checklistJson: $checklistJson, ')
          ..write('photosJson: $photosJson, ')
          ..write('damages: $damages, ')
          ..write('failures: $failures, ')
          ..write('comments: $comments, ')
          ..write('commandId: $commandId, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CameraCapturesTable extends CameraCaptures
    with TableInfo<$CameraCapturesTable, CameraCapture> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CameraCapturesTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _draftIdMeta = const VerificationMeta(
    'draftId',
  );
  @override
  late final GeneratedColumn<String> draftId = GeneratedColumn<String>(
    'draft_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _positionMeta = const VerificationMeta(
    'position',
  );
  @override
  late final GeneratedColumn<String> position = GeneratedColumn<String>(
    'position',
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
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('PENDING'),
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
    id,
    userId,
    tenantId,
    draftId,
    position,
    mediaId,
    state,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'camera_captures';
  @override
  VerificationContext validateIntegrity(
    Insertable<CameraCapture> instance, {
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
    if (data.containsKey('draft_id')) {
      context.handle(
        _draftIdMeta,
        draftId.isAcceptableOrUnknown(data['draft_id']!, _draftIdMeta),
      );
    } else if (isInserting) {
      context.missing(_draftIdMeta);
    }
    if (data.containsKey('position')) {
      context.handle(
        _positionMeta,
        position.isAcceptableOrUnknown(data['position']!, _positionMeta),
      );
    } else if (isInserting) {
      context.missing(_positionMeta);
    }
    if (data.containsKey('media_id')) {
      context.handle(
        _mediaIdMeta,
        mediaId.isAcceptableOrUnknown(data['media_id']!, _mediaIdMeta),
      );
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
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
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  CameraCapture map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CameraCapture(
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
      draftId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}draft_id'],
      )!,
      position: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}position'],
      )!,
      mediaId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}media_id'],
      ),
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $CameraCapturesTable createAlias(String alias) {
    return $CameraCapturesTable(attachedDatabase, alias);
  }
}

class CameraCapture extends DataClass implements Insertable<CameraCapture> {
  final String id;
  final String userId;
  final String tenantId;
  final String draftId;
  final String position;
  final String? mediaId;
  final String state;
  final DateTime createdAt;
  const CameraCapture({
    required this.id,
    required this.userId,
    required this.tenantId,
    required this.draftId,
    required this.position,
    this.mediaId,
    required this.state,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['draft_id'] = Variable<String>(draftId);
    map['position'] = Variable<String>(position);
    if (!nullToAbsent || mediaId != null) {
      map['media_id'] = Variable<String>(mediaId);
    }
    map['state'] = Variable<String>(state);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  CameraCapturesCompanion toCompanion(bool nullToAbsent) {
    return CameraCapturesCompanion(
      id: Value(id),
      userId: Value(userId),
      tenantId: Value(tenantId),
      draftId: Value(draftId),
      position: Value(position),
      mediaId: mediaId == null && nullToAbsent
          ? const Value.absent()
          : Value(mediaId),
      state: Value(state),
      createdAt: Value(createdAt),
    );
  }

  factory CameraCapture.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CameraCapture(
      id: serializer.fromJson<String>(json['id']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      draftId: serializer.fromJson<String>(json['draftId']),
      position: serializer.fromJson<String>(json['position']),
      mediaId: serializer.fromJson<String?>(json['mediaId']),
      state: serializer.fromJson<String>(json['state']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'draftId': serializer.toJson<String>(draftId),
      'position': serializer.toJson<String>(position),
      'mediaId': serializer.toJson<String?>(mediaId),
      'state': serializer.toJson<String>(state),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  CameraCapture copyWith({
    String? id,
    String? userId,
    String? tenantId,
    String? draftId,
    String? position,
    Value<String?> mediaId = const Value.absent(),
    String? state,
    DateTime? createdAt,
  }) => CameraCapture(
    id: id ?? this.id,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    draftId: draftId ?? this.draftId,
    position: position ?? this.position,
    mediaId: mediaId.present ? mediaId.value : this.mediaId,
    state: state ?? this.state,
    createdAt: createdAt ?? this.createdAt,
  );
  CameraCapture copyWithCompanion(CameraCapturesCompanion data) {
    return CameraCapture(
      id: data.id.present ? data.id.value : this.id,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      draftId: data.draftId.present ? data.draftId.value : this.draftId,
      position: data.position.present ? data.position.value : this.position,
      mediaId: data.mediaId.present ? data.mediaId.value : this.mediaId,
      state: data.state.present ? data.state.value : this.state,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CameraCapture(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('draftId: $draftId, ')
          ..write('position: $position, ')
          ..write('mediaId: $mediaId, ')
          ..write('state: $state, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    userId,
    tenantId,
    draftId,
    position,
    mediaId,
    state,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CameraCapture &&
          other.id == this.id &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.draftId == this.draftId &&
          other.position == this.position &&
          other.mediaId == this.mediaId &&
          other.state == this.state &&
          other.createdAt == this.createdAt);
}

class CameraCapturesCompanion extends UpdateCompanion<CameraCapture> {
  final Value<String> id;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> draftId;
  final Value<String> position;
  final Value<String?> mediaId;
  final Value<String> state;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const CameraCapturesCompanion({
    this.id = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.draftId = const Value.absent(),
    this.position = const Value.absent(),
    this.mediaId = const Value.absent(),
    this.state = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CameraCapturesCompanion.insert({
    required String id,
    required String userId,
    required String tenantId,
    required String draftId,
    required String position,
    this.mediaId = const Value.absent(),
    this.state = const Value.absent(),
    required DateTime createdAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       userId = Value(userId),
       tenantId = Value(tenantId),
       draftId = Value(draftId),
       position = Value(position),
       createdAt = Value(createdAt);
  static Insertable<CameraCapture> custom({
    Expression<String>? id,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? draftId,
    Expression<String>? position,
    Expression<String>? mediaId,
    Expression<String>? state,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (draftId != null) 'draft_id': draftId,
      if (position != null) 'position': position,
      if (mediaId != null) 'media_id': mediaId,
      if (state != null) 'state': state,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CameraCapturesCompanion copyWith({
    Value<String>? id,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? draftId,
    Value<String>? position,
    Value<String?>? mediaId,
    Value<String>? state,
    Value<DateTime>? createdAt,
    Value<int>? rowid,
  }) {
    return CameraCapturesCompanion(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      draftId: draftId ?? this.draftId,
      position: position ?? this.position,
      mediaId: mediaId ?? this.mediaId,
      state: state ?? this.state,
      createdAt: createdAt ?? this.createdAt,
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
    if (draftId.present) {
      map['draft_id'] = Variable<String>(draftId.value);
    }
    if (position.present) {
      map['position'] = Variable<String>(position.value);
    }
    if (mediaId.present) {
      map['media_id'] = Variable<String>(mediaId.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
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
    return (StringBuffer('CameraCapturesCompanion(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('draftId: $draftId, ')
          ..write('position: $position, ')
          ..write('mediaId: $mediaId, ')
          ..write('state: $state, ')
          ..write('createdAt: $createdAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $RoadDraftsTable extends RoadDrafts
    with TableInfo<$RoadDraftsTable, RoadDraft> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $RoadDraftsTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _tripIdMeta = const VerificationMeta('tripId');
  @override
  late final GeneratedColumn<String> tripId = GeneratedColumn<String>(
    'trip_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tractorIdMeta = const VerificationMeta(
    'tractorId',
  );
  @override
  late final GeneratedColumn<String> tractorId = GeneratedColumn<String>(
    'tractor_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _stopIdMeta = const VerificationMeta('stopId');
  @override
  late final GeneratedColumn<String> stopId = GeneratedColumn<String>(
    'stop_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _purposeMeta = const VerificationMeta(
    'purpose',
  );
  @override
  late final GeneratedColumn<String> purpose = GeneratedColumn<String>(
    'purpose',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _inputsJsonMeta = const VerificationMeta(
    'inputsJson',
  );
  @override
  late final GeneratedColumn<String> inputsJson = GeneratedColumn<String>(
    'inputs_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('{}'),
  );
  static const VerificationMeta _mediaJsonMeta = const VerificationMeta(
    'mediaJson',
  );
  @override
  late final GeneratedColumn<String> mediaJson = GeneratedColumn<String>(
    'media_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('{}'),
  );
  static const VerificationMeta _commandIdMeta = const VerificationMeta(
    'commandId',
  );
  @override
  late final GeneratedColumn<String> commandId = GeneratedColumn<String>(
    'command_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
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
  @override
  List<GeneratedColumn> get $columns => [
    id,
    userId,
    tenantId,
    tripId,
    tractorId,
    stopId,
    purpose,
    inputsJson,
    mediaJson,
    commandId,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'road_drafts';
  @override
  VerificationContext validateIntegrity(
    Insertable<RoadDraft> instance, {
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
    if (data.containsKey('trip_id')) {
      context.handle(
        _tripIdMeta,
        tripId.isAcceptableOrUnknown(data['trip_id']!, _tripIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tripIdMeta);
    }
    if (data.containsKey('tractor_id')) {
      context.handle(
        _tractorIdMeta,
        tractorId.isAcceptableOrUnknown(data['tractor_id']!, _tractorIdMeta),
      );
    }
    if (data.containsKey('stop_id')) {
      context.handle(
        _stopIdMeta,
        stopId.isAcceptableOrUnknown(data['stop_id']!, _stopIdMeta),
      );
    }
    if (data.containsKey('purpose')) {
      context.handle(
        _purposeMeta,
        purpose.isAcceptableOrUnknown(data['purpose']!, _purposeMeta),
      );
    } else if (isInserting) {
      context.missing(_purposeMeta);
    }
    if (data.containsKey('inputs_json')) {
      context.handle(
        _inputsJsonMeta,
        inputsJson.isAcceptableOrUnknown(data['inputs_json']!, _inputsJsonMeta),
      );
    }
    if (data.containsKey('media_json')) {
      context.handle(
        _mediaJsonMeta,
        mediaJson.isAcceptableOrUnknown(data['media_json']!, _mediaJsonMeta),
      );
    }
    if (data.containsKey('command_id')) {
      context.handle(
        _commandIdMeta,
        commandId.isAcceptableOrUnknown(data['command_id']!, _commandIdMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  RoadDraft map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return RoadDraft(
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
      tripId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trip_id'],
      )!,
      tractorId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tractor_id'],
      ),
      stopId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}stop_id'],
      ),
      purpose: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}purpose'],
      )!,
      inputsJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}inputs_json'],
      )!,
      mediaJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}media_json'],
      )!,
      commandId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}command_id'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $RoadDraftsTable createAlias(String alias) {
    return $RoadDraftsTable(attachedDatabase, alias);
  }
}

class RoadDraft extends DataClass implements Insertable<RoadDraft> {
  final String id;
  final String userId;
  final String tenantId;
  final String tripId;
  final String? tractorId;
  final String? stopId;
  final String purpose;
  final String inputsJson;
  final String mediaJson;
  final String? commandId;
  final DateTime updatedAt;
  const RoadDraft({
    required this.id,
    required this.userId,
    required this.tenantId,
    required this.tripId,
    this.tractorId,
    this.stopId,
    required this.purpose,
    required this.inputsJson,
    required this.mediaJson,
    this.commandId,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['trip_id'] = Variable<String>(tripId);
    if (!nullToAbsent || tractorId != null) {
      map['tractor_id'] = Variable<String>(tractorId);
    }
    if (!nullToAbsent || stopId != null) {
      map['stop_id'] = Variable<String>(stopId);
    }
    map['purpose'] = Variable<String>(purpose);
    map['inputs_json'] = Variable<String>(inputsJson);
    map['media_json'] = Variable<String>(mediaJson);
    if (!nullToAbsent || commandId != null) {
      map['command_id'] = Variable<String>(commandId);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  RoadDraftsCompanion toCompanion(bool nullToAbsent) {
    return RoadDraftsCompanion(
      id: Value(id),
      userId: Value(userId),
      tenantId: Value(tenantId),
      tripId: Value(tripId),
      tractorId: tractorId == null && nullToAbsent
          ? const Value.absent()
          : Value(tractorId),
      stopId: stopId == null && nullToAbsent
          ? const Value.absent()
          : Value(stopId),
      purpose: Value(purpose),
      inputsJson: Value(inputsJson),
      mediaJson: Value(mediaJson),
      commandId: commandId == null && nullToAbsent
          ? const Value.absent()
          : Value(commandId),
      updatedAt: Value(updatedAt),
    );
  }

  factory RoadDraft.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return RoadDraft(
      id: serializer.fromJson<String>(json['id']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      tripId: serializer.fromJson<String>(json['tripId']),
      tractorId: serializer.fromJson<String?>(json['tractorId']),
      stopId: serializer.fromJson<String?>(json['stopId']),
      purpose: serializer.fromJson<String>(json['purpose']),
      inputsJson: serializer.fromJson<String>(json['inputsJson']),
      mediaJson: serializer.fromJson<String>(json['mediaJson']),
      commandId: serializer.fromJson<String?>(json['commandId']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'tripId': serializer.toJson<String>(tripId),
      'tractorId': serializer.toJson<String?>(tractorId),
      'stopId': serializer.toJson<String?>(stopId),
      'purpose': serializer.toJson<String>(purpose),
      'inputsJson': serializer.toJson<String>(inputsJson),
      'mediaJson': serializer.toJson<String>(mediaJson),
      'commandId': serializer.toJson<String?>(commandId),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  RoadDraft copyWith({
    String? id,
    String? userId,
    String? tenantId,
    String? tripId,
    Value<String?> tractorId = const Value.absent(),
    Value<String?> stopId = const Value.absent(),
    String? purpose,
    String? inputsJson,
    String? mediaJson,
    Value<String?> commandId = const Value.absent(),
    DateTime? updatedAt,
  }) => RoadDraft(
    id: id ?? this.id,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    tripId: tripId ?? this.tripId,
    tractorId: tractorId.present ? tractorId.value : this.tractorId,
    stopId: stopId.present ? stopId.value : this.stopId,
    purpose: purpose ?? this.purpose,
    inputsJson: inputsJson ?? this.inputsJson,
    mediaJson: mediaJson ?? this.mediaJson,
    commandId: commandId.present ? commandId.value : this.commandId,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  RoadDraft copyWithCompanion(RoadDraftsCompanion data) {
    return RoadDraft(
      id: data.id.present ? data.id.value : this.id,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      tripId: data.tripId.present ? data.tripId.value : this.tripId,
      tractorId: data.tractorId.present ? data.tractorId.value : this.tractorId,
      stopId: data.stopId.present ? data.stopId.value : this.stopId,
      purpose: data.purpose.present ? data.purpose.value : this.purpose,
      inputsJson: data.inputsJson.present
          ? data.inputsJson.value
          : this.inputsJson,
      mediaJson: data.mediaJson.present ? data.mediaJson.value : this.mediaJson,
      commandId: data.commandId.present ? data.commandId.value : this.commandId,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('RoadDraft(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('tripId: $tripId, ')
          ..write('tractorId: $tractorId, ')
          ..write('stopId: $stopId, ')
          ..write('purpose: $purpose, ')
          ..write('inputsJson: $inputsJson, ')
          ..write('mediaJson: $mediaJson, ')
          ..write('commandId: $commandId, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    userId,
    tenantId,
    tripId,
    tractorId,
    stopId,
    purpose,
    inputsJson,
    mediaJson,
    commandId,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is RoadDraft &&
          other.id == this.id &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.tripId == this.tripId &&
          other.tractorId == this.tractorId &&
          other.stopId == this.stopId &&
          other.purpose == this.purpose &&
          other.inputsJson == this.inputsJson &&
          other.mediaJson == this.mediaJson &&
          other.commandId == this.commandId &&
          other.updatedAt == this.updatedAt);
}

class RoadDraftsCompanion extends UpdateCompanion<RoadDraft> {
  final Value<String> id;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> tripId;
  final Value<String?> tractorId;
  final Value<String?> stopId;
  final Value<String> purpose;
  final Value<String> inputsJson;
  final Value<String> mediaJson;
  final Value<String?> commandId;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const RoadDraftsCompanion({
    this.id = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.tripId = const Value.absent(),
    this.tractorId = const Value.absent(),
    this.stopId = const Value.absent(),
    this.purpose = const Value.absent(),
    this.inputsJson = const Value.absent(),
    this.mediaJson = const Value.absent(),
    this.commandId = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  RoadDraftsCompanion.insert({
    required String id,
    required String userId,
    required String tenantId,
    required String tripId,
    this.tractorId = const Value.absent(),
    this.stopId = const Value.absent(),
    required String purpose,
    this.inputsJson = const Value.absent(),
    this.mediaJson = const Value.absent(),
    this.commandId = const Value.absent(),
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       userId = Value(userId),
       tenantId = Value(tenantId),
       tripId = Value(tripId),
       purpose = Value(purpose),
       updatedAt = Value(updatedAt);
  static Insertable<RoadDraft> custom({
    Expression<String>? id,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? tripId,
    Expression<String>? tractorId,
    Expression<String>? stopId,
    Expression<String>? purpose,
    Expression<String>? inputsJson,
    Expression<String>? mediaJson,
    Expression<String>? commandId,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (tripId != null) 'trip_id': tripId,
      if (tractorId != null) 'tractor_id': tractorId,
      if (stopId != null) 'stop_id': stopId,
      if (purpose != null) 'purpose': purpose,
      if (inputsJson != null) 'inputs_json': inputsJson,
      if (mediaJson != null) 'media_json': mediaJson,
      if (commandId != null) 'command_id': commandId,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  RoadDraftsCompanion copyWith({
    Value<String>? id,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? tripId,
    Value<String?>? tractorId,
    Value<String?>? stopId,
    Value<String>? purpose,
    Value<String>? inputsJson,
    Value<String>? mediaJson,
    Value<String?>? commandId,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return RoadDraftsCompanion(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      tripId: tripId ?? this.tripId,
      tractorId: tractorId ?? this.tractorId,
      stopId: stopId ?? this.stopId,
      purpose: purpose ?? this.purpose,
      inputsJson: inputsJson ?? this.inputsJson,
      mediaJson: mediaJson ?? this.mediaJson,
      commandId: commandId ?? this.commandId,
      updatedAt: updatedAt ?? this.updatedAt,
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
    if (tripId.present) {
      map['trip_id'] = Variable<String>(tripId.value);
    }
    if (tractorId.present) {
      map['tractor_id'] = Variable<String>(tractorId.value);
    }
    if (stopId.present) {
      map['stop_id'] = Variable<String>(stopId.value);
    }
    if (purpose.present) {
      map['purpose'] = Variable<String>(purpose.value);
    }
    if (inputsJson.present) {
      map['inputs_json'] = Variable<String>(inputsJson.value);
    }
    if (mediaJson.present) {
      map['media_json'] = Variable<String>(mediaJson.value);
    }
    if (commandId.present) {
      map['command_id'] = Variable<String>(commandId.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('RoadDraftsCompanion(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('tripId: $tripId, ')
          ..write('tractorId: $tractorId, ')
          ..write('stopId: $stopId, ')
          ..write('purpose: $purpose, ')
          ..write('inputsJson: $inputsJson, ')
          ..write('mediaJson: $mediaJson, ')
          ..write('commandId: $commandId, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $RoadCapturesTable extends RoadCaptures
    with TableInfo<$RoadCapturesTable, RoadCapture> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $RoadCapturesTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _draftIdMeta = const VerificationMeta(
    'draftId',
  );
  @override
  late final GeneratedColumn<String> draftId = GeneratedColumn<String>(
    'draft_id',
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
  static const VerificationMeta _fieldMeta = const VerificationMeta('field');
  @override
  late final GeneratedColumn<String> field = GeneratedColumn<String>(
    'field',
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
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('PENDING'),
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
    id,
    userId,
    tenantId,
    draftId,
    documentType,
    field,
    mediaId,
    state,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'road_captures';
  @override
  VerificationContext validateIntegrity(
    Insertable<RoadCapture> instance, {
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
    if (data.containsKey('draft_id')) {
      context.handle(
        _draftIdMeta,
        draftId.isAcceptableOrUnknown(data['draft_id']!, _draftIdMeta),
      );
    } else if (isInserting) {
      context.missing(_draftIdMeta);
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
    if (data.containsKey('field')) {
      context.handle(
        _fieldMeta,
        field.isAcceptableOrUnknown(data['field']!, _fieldMeta),
      );
    } else if (isInserting) {
      context.missing(_fieldMeta);
    }
    if (data.containsKey('media_id')) {
      context.handle(
        _mediaIdMeta,
        mediaId.isAcceptableOrUnknown(data['media_id']!, _mediaIdMeta),
      );
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
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
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  RoadCapture map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return RoadCapture(
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
      draftId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}draft_id'],
      )!,
      documentType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}document_type'],
      )!,
      field: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}field'],
      )!,
      mediaId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}media_id'],
      ),
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $RoadCapturesTable createAlias(String alias) {
    return $RoadCapturesTable(attachedDatabase, alias);
  }
}

class RoadCapture extends DataClass implements Insertable<RoadCapture> {
  final String id;
  final String userId;
  final String tenantId;
  final String draftId;
  final String documentType;
  final String field;
  final String? mediaId;
  final String state;
  final DateTime createdAt;
  const RoadCapture({
    required this.id,
    required this.userId,
    required this.tenantId,
    required this.draftId,
    required this.documentType,
    required this.field,
    this.mediaId,
    required this.state,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['draft_id'] = Variable<String>(draftId);
    map['document_type'] = Variable<String>(documentType);
    map['field'] = Variable<String>(field);
    if (!nullToAbsent || mediaId != null) {
      map['media_id'] = Variable<String>(mediaId);
    }
    map['state'] = Variable<String>(state);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  RoadCapturesCompanion toCompanion(bool nullToAbsent) {
    return RoadCapturesCompanion(
      id: Value(id),
      userId: Value(userId),
      tenantId: Value(tenantId),
      draftId: Value(draftId),
      documentType: Value(documentType),
      field: Value(field),
      mediaId: mediaId == null && nullToAbsent
          ? const Value.absent()
          : Value(mediaId),
      state: Value(state),
      createdAt: Value(createdAt),
    );
  }

  factory RoadCapture.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return RoadCapture(
      id: serializer.fromJson<String>(json['id']),
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      draftId: serializer.fromJson<String>(json['draftId']),
      documentType: serializer.fromJson<String>(json['documentType']),
      field: serializer.fromJson<String>(json['field']),
      mediaId: serializer.fromJson<String?>(json['mediaId']),
      state: serializer.fromJson<String>(json['state']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'draftId': serializer.toJson<String>(draftId),
      'documentType': serializer.toJson<String>(documentType),
      'field': serializer.toJson<String>(field),
      'mediaId': serializer.toJson<String?>(mediaId),
      'state': serializer.toJson<String>(state),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  RoadCapture copyWith({
    String? id,
    String? userId,
    String? tenantId,
    String? draftId,
    String? documentType,
    String? field,
    Value<String?> mediaId = const Value.absent(),
    String? state,
    DateTime? createdAt,
  }) => RoadCapture(
    id: id ?? this.id,
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    draftId: draftId ?? this.draftId,
    documentType: documentType ?? this.documentType,
    field: field ?? this.field,
    mediaId: mediaId.present ? mediaId.value : this.mediaId,
    state: state ?? this.state,
    createdAt: createdAt ?? this.createdAt,
  );
  RoadCapture copyWithCompanion(RoadCapturesCompanion data) {
    return RoadCapture(
      id: data.id.present ? data.id.value : this.id,
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      draftId: data.draftId.present ? data.draftId.value : this.draftId,
      documentType: data.documentType.present
          ? data.documentType.value
          : this.documentType,
      field: data.field.present ? data.field.value : this.field,
      mediaId: data.mediaId.present ? data.mediaId.value : this.mediaId,
      state: data.state.present ? data.state.value : this.state,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('RoadCapture(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('draftId: $draftId, ')
          ..write('documentType: $documentType, ')
          ..write('field: $field, ')
          ..write('mediaId: $mediaId, ')
          ..write('state: $state, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    userId,
    tenantId,
    draftId,
    documentType,
    field,
    mediaId,
    state,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is RoadCapture &&
          other.id == this.id &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.draftId == this.draftId &&
          other.documentType == this.documentType &&
          other.field == this.field &&
          other.mediaId == this.mediaId &&
          other.state == this.state &&
          other.createdAt == this.createdAt);
}

class RoadCapturesCompanion extends UpdateCompanion<RoadCapture> {
  final Value<String> id;
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> draftId;
  final Value<String> documentType;
  final Value<String> field;
  final Value<String?> mediaId;
  final Value<String> state;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const RoadCapturesCompanion({
    this.id = const Value.absent(),
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.draftId = const Value.absent(),
    this.documentType = const Value.absent(),
    this.field = const Value.absent(),
    this.mediaId = const Value.absent(),
    this.state = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  RoadCapturesCompanion.insert({
    required String id,
    required String userId,
    required String tenantId,
    required String draftId,
    required String documentType,
    required String field,
    this.mediaId = const Value.absent(),
    this.state = const Value.absent(),
    required DateTime createdAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       userId = Value(userId),
       tenantId = Value(tenantId),
       draftId = Value(draftId),
       documentType = Value(documentType),
       field = Value(field),
       createdAt = Value(createdAt);
  static Insertable<RoadCapture> custom({
    Expression<String>? id,
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? draftId,
    Expression<String>? documentType,
    Expression<String>? field,
    Expression<String>? mediaId,
    Expression<String>? state,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (draftId != null) 'draft_id': draftId,
      if (documentType != null) 'document_type': documentType,
      if (field != null) 'field': field,
      if (mediaId != null) 'media_id': mediaId,
      if (state != null) 'state': state,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  RoadCapturesCompanion copyWith({
    Value<String>? id,
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? draftId,
    Value<String>? documentType,
    Value<String>? field,
    Value<String?>? mediaId,
    Value<String>? state,
    Value<DateTime>? createdAt,
    Value<int>? rowid,
  }) {
    return RoadCapturesCompanion(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      draftId: draftId ?? this.draftId,
      documentType: documentType ?? this.documentType,
      field: field ?? this.field,
      mediaId: mediaId ?? this.mediaId,
      state: state ?? this.state,
      createdAt: createdAt ?? this.createdAt,
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
    if (draftId.present) {
      map['draft_id'] = Variable<String>(draftId.value);
    }
    if (documentType.present) {
      map['document_type'] = Variable<String>(documentType.value);
    }
    if (field.present) {
      map['field'] = Variable<String>(field.value);
    }
    if (mediaId.present) {
      map['media_id'] = Variable<String>(mediaId.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
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
    return (StringBuffer('RoadCapturesCompanion(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('draftId: $draftId, ')
          ..write('documentType: $documentType, ')
          ..write('field: $field, ')
          ..write('mediaId: $mediaId, ')
          ..write('state: $state, ')
          ..write('createdAt: $createdAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CachedDriverUpdatesTable extends CachedDriverUpdates
    with TableInfo<$CachedDriverUpdatesTable, CachedDriverUpdate> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CachedDriverUpdatesTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _driverIdMeta = const VerificationMeta(
    'driverId',
  );
  @override
  late final GeneratedColumn<String> driverId = GeneratedColumn<String>(
    'driver_id',
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
  static const VerificationMeta _updateTypeMeta = const VerificationMeta(
    'updateType',
  );
  @override
  late final GeneratedColumn<String> updateType = GeneratedColumn<String>(
    'update_type',
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
  static const VerificationMeta _sequenceMeta = const VerificationMeta(
    'sequence',
  );
  @override
  late final GeneratedColumn<int> sequence = GeneratedColumn<int>(
    'sequence',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _downloadedAtMeta = const VerificationMeta(
    'downloadedAt',
  );
  @override
  late final GeneratedColumn<DateTime> downloadedAt = GeneratedColumn<DateTime>(
    'downloaded_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    userId,
    tenantId,
    driverId,
    id,
    tripId,
    updateType,
    projectionJson,
    sequence,
    downloadedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cached_driver_updates';
  @override
  VerificationContext validateIntegrity(
    Insertable<CachedDriverUpdate> instance, {
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
    if (data.containsKey('driver_id')) {
      context.handle(
        _driverIdMeta,
        driverId.isAcceptableOrUnknown(data['driver_id']!, _driverIdMeta),
      );
    } else if (isInserting) {
      context.missing(_driverIdMeta);
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
    if (data.containsKey('update_type')) {
      context.handle(
        _updateTypeMeta,
        updateType.isAcceptableOrUnknown(data['update_type']!, _updateTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_updateTypeMeta);
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
    if (data.containsKey('sequence')) {
      context.handle(
        _sequenceMeta,
        sequence.isAcceptableOrUnknown(data['sequence']!, _sequenceMeta),
      );
    } else if (isInserting) {
      context.missing(_sequenceMeta);
    }
    if (data.containsKey('downloaded_at')) {
      context.handle(
        _downloadedAtMeta,
        downloadedAt.isAcceptableOrUnknown(
          data['downloaded_at']!,
          _downloadedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_downloadedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {userId, tenantId, driverId, id};
  @override
  CachedDriverUpdate map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CachedDriverUpdate(
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      driverId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}driver_id'],
      )!,
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      tripId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trip_id'],
      )!,
      updateType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}update_type'],
      )!,
      projectionJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}projection_json'],
      )!,
      sequence: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sequence'],
      )!,
      downloadedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}downloaded_at'],
      )!,
    );
  }

  @override
  $CachedDriverUpdatesTable createAlias(String alias) {
    return $CachedDriverUpdatesTable(attachedDatabase, alias);
  }
}

class CachedDriverUpdate extends DataClass
    implements Insertable<CachedDriverUpdate> {
  final String userId;
  final String tenantId;
  final String driverId;
  final String id;
  final String tripId;
  final String updateType;
  final String projectionJson;
  final int sequence;
  final DateTime downloadedAt;
  const CachedDriverUpdate({
    required this.userId,
    required this.tenantId,
    required this.driverId,
    required this.id,
    required this.tripId,
    required this.updateType,
    required this.projectionJson,
    required this.sequence,
    required this.downloadedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['driver_id'] = Variable<String>(driverId);
    map['id'] = Variable<String>(id);
    map['trip_id'] = Variable<String>(tripId);
    map['update_type'] = Variable<String>(updateType);
    map['projection_json'] = Variable<String>(projectionJson);
    map['sequence'] = Variable<int>(sequence);
    map['downloaded_at'] = Variable<DateTime>(downloadedAt);
    return map;
  }

  CachedDriverUpdatesCompanion toCompanion(bool nullToAbsent) {
    return CachedDriverUpdatesCompanion(
      userId: Value(userId),
      tenantId: Value(tenantId),
      driverId: Value(driverId),
      id: Value(id),
      tripId: Value(tripId),
      updateType: Value(updateType),
      projectionJson: Value(projectionJson),
      sequence: Value(sequence),
      downloadedAt: Value(downloadedAt),
    );
  }

  factory CachedDriverUpdate.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CachedDriverUpdate(
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      driverId: serializer.fromJson<String>(json['driverId']),
      id: serializer.fromJson<String>(json['id']),
      tripId: serializer.fromJson<String>(json['tripId']),
      updateType: serializer.fromJson<String>(json['updateType']),
      projectionJson: serializer.fromJson<String>(json['projectionJson']),
      sequence: serializer.fromJson<int>(json['sequence']),
      downloadedAt: serializer.fromJson<DateTime>(json['downloadedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'driverId': serializer.toJson<String>(driverId),
      'id': serializer.toJson<String>(id),
      'tripId': serializer.toJson<String>(tripId),
      'updateType': serializer.toJson<String>(updateType),
      'projectionJson': serializer.toJson<String>(projectionJson),
      'sequence': serializer.toJson<int>(sequence),
      'downloadedAt': serializer.toJson<DateTime>(downloadedAt),
    };
  }

  CachedDriverUpdate copyWith({
    String? userId,
    String? tenantId,
    String? driverId,
    String? id,
    String? tripId,
    String? updateType,
    String? projectionJson,
    int? sequence,
    DateTime? downloadedAt,
  }) => CachedDriverUpdate(
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    driverId: driverId ?? this.driverId,
    id: id ?? this.id,
    tripId: tripId ?? this.tripId,
    updateType: updateType ?? this.updateType,
    projectionJson: projectionJson ?? this.projectionJson,
    sequence: sequence ?? this.sequence,
    downloadedAt: downloadedAt ?? this.downloadedAt,
  );
  CachedDriverUpdate copyWithCompanion(CachedDriverUpdatesCompanion data) {
    return CachedDriverUpdate(
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      driverId: data.driverId.present ? data.driverId.value : this.driverId,
      id: data.id.present ? data.id.value : this.id,
      tripId: data.tripId.present ? data.tripId.value : this.tripId,
      updateType: data.updateType.present
          ? data.updateType.value
          : this.updateType,
      projectionJson: data.projectionJson.present
          ? data.projectionJson.value
          : this.projectionJson,
      sequence: data.sequence.present ? data.sequence.value : this.sequence,
      downloadedAt: data.downloadedAt.present
          ? data.downloadedAt.value
          : this.downloadedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CachedDriverUpdate(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('driverId: $driverId, ')
          ..write('id: $id, ')
          ..write('tripId: $tripId, ')
          ..write('updateType: $updateType, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('sequence: $sequence, ')
          ..write('downloadedAt: $downloadedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    userId,
    tenantId,
    driverId,
    id,
    tripId,
    updateType,
    projectionJson,
    sequence,
    downloadedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CachedDriverUpdate &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.driverId == this.driverId &&
          other.id == this.id &&
          other.tripId == this.tripId &&
          other.updateType == this.updateType &&
          other.projectionJson == this.projectionJson &&
          other.sequence == this.sequence &&
          other.downloadedAt == this.downloadedAt);
}

class CachedDriverUpdatesCompanion extends UpdateCompanion<CachedDriverUpdate> {
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> driverId;
  final Value<String> id;
  final Value<String> tripId;
  final Value<String> updateType;
  final Value<String> projectionJson;
  final Value<int> sequence;
  final Value<DateTime> downloadedAt;
  final Value<int> rowid;
  const CachedDriverUpdatesCompanion({
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.driverId = const Value.absent(),
    this.id = const Value.absent(),
    this.tripId = const Value.absent(),
    this.updateType = const Value.absent(),
    this.projectionJson = const Value.absent(),
    this.sequence = const Value.absent(),
    this.downloadedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CachedDriverUpdatesCompanion.insert({
    required String userId,
    required String tenantId,
    required String driverId,
    required String id,
    required String tripId,
    required String updateType,
    required String projectionJson,
    required int sequence,
    required DateTime downloadedAt,
    this.rowid = const Value.absent(),
  }) : userId = Value(userId),
       tenantId = Value(tenantId),
       driverId = Value(driverId),
       id = Value(id),
       tripId = Value(tripId),
       updateType = Value(updateType),
       projectionJson = Value(projectionJson),
       sequence = Value(sequence),
       downloadedAt = Value(downloadedAt);
  static Insertable<CachedDriverUpdate> custom({
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? driverId,
    Expression<String>? id,
    Expression<String>? tripId,
    Expression<String>? updateType,
    Expression<String>? projectionJson,
    Expression<int>? sequence,
    Expression<DateTime>? downloadedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (driverId != null) 'driver_id': driverId,
      if (id != null) 'id': id,
      if (tripId != null) 'trip_id': tripId,
      if (updateType != null) 'update_type': updateType,
      if (projectionJson != null) 'projection_json': projectionJson,
      if (sequence != null) 'sequence': sequence,
      if (downloadedAt != null) 'downloaded_at': downloadedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CachedDriverUpdatesCompanion copyWith({
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? driverId,
    Value<String>? id,
    Value<String>? tripId,
    Value<String>? updateType,
    Value<String>? projectionJson,
    Value<int>? sequence,
    Value<DateTime>? downloadedAt,
    Value<int>? rowid,
  }) {
    return CachedDriverUpdatesCompanion(
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      driverId: driverId ?? this.driverId,
      id: id ?? this.id,
      tripId: tripId ?? this.tripId,
      updateType: updateType ?? this.updateType,
      projectionJson: projectionJson ?? this.projectionJson,
      sequence: sequence ?? this.sequence,
      downloadedAt: downloadedAt ?? this.downloadedAt,
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
    if (driverId.present) {
      map['driver_id'] = Variable<String>(driverId.value);
    }
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (tripId.present) {
      map['trip_id'] = Variable<String>(tripId.value);
    }
    if (updateType.present) {
      map['update_type'] = Variable<String>(updateType.value);
    }
    if (projectionJson.present) {
      map['projection_json'] = Variable<String>(projectionJson.value);
    }
    if (sequence.present) {
      map['sequence'] = Variable<int>(sequence.value);
    }
    if (downloadedAt.present) {
      map['downloaded_at'] = Variable<DateTime>(downloadedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CachedDriverUpdatesCompanion(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('driverId: $driverId, ')
          ..write('id: $id, ')
          ..write('tripId: $tripId, ')
          ..write('updateType: $updateType, ')
          ..write('projectionJson: $projectionJson, ')
          ..write('sequence: $sequence, ')
          ..write('downloadedAt: $downloadedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $DriverUpdateCursorsTable extends DriverUpdateCursors
    with TableInfo<$DriverUpdateCursorsTable, DriverUpdateCursor> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DriverUpdateCursorsTable(this.attachedDatabase, [this._alias]);
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
  static const VerificationMeta _driverIdMeta = const VerificationMeta(
    'driverId',
  );
  @override
  late final GeneratedColumn<String> driverId = GeneratedColumn<String>(
    'driver_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _cursorMeta = const VerificationMeta('cursor');
  @override
  late final GeneratedColumn<String> cursor = GeneratedColumn<String>(
    'cursor',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _accessBlockedMeta = const VerificationMeta(
    'accessBlocked',
  );
  @override
  late final GeneratedColumn<bool> accessBlocked = GeneratedColumn<bool>(
    'access_blocked',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("access_blocked" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  @override
  List<GeneratedColumn> get $columns => [
    userId,
    tenantId,
    driverId,
    cursor,
    accessBlocked,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'driver_update_cursors';
  @override
  VerificationContext validateIntegrity(
    Insertable<DriverUpdateCursor> instance, {
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
    if (data.containsKey('driver_id')) {
      context.handle(
        _driverIdMeta,
        driverId.isAcceptableOrUnknown(data['driver_id']!, _driverIdMeta),
      );
    } else if (isInserting) {
      context.missing(_driverIdMeta);
    }
    if (data.containsKey('cursor')) {
      context.handle(
        _cursorMeta,
        cursor.isAcceptableOrUnknown(data['cursor']!, _cursorMeta),
      );
    }
    if (data.containsKey('access_blocked')) {
      context.handle(
        _accessBlockedMeta,
        accessBlocked.isAcceptableOrUnknown(
          data['access_blocked']!,
          _accessBlockedMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {userId, tenantId, driverId};
  @override
  DriverUpdateCursor map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DriverUpdateCursor(
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}user_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      driverId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}driver_id'],
      )!,
      cursor: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cursor'],
      ),
      accessBlocked: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}access_blocked'],
      )!,
    );
  }

  @override
  $DriverUpdateCursorsTable createAlias(String alias) {
    return $DriverUpdateCursorsTable(attachedDatabase, alias);
  }
}

class DriverUpdateCursor extends DataClass
    implements Insertable<DriverUpdateCursor> {
  final String userId;
  final String tenantId;
  final String driverId;
  final String? cursor;
  final bool accessBlocked;
  const DriverUpdateCursor({
    required this.userId,
    required this.tenantId,
    required this.driverId,
    this.cursor,
    required this.accessBlocked,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['user_id'] = Variable<String>(userId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['driver_id'] = Variable<String>(driverId);
    if (!nullToAbsent || cursor != null) {
      map['cursor'] = Variable<String>(cursor);
    }
    map['access_blocked'] = Variable<bool>(accessBlocked);
    return map;
  }

  DriverUpdateCursorsCompanion toCompanion(bool nullToAbsent) {
    return DriverUpdateCursorsCompanion(
      userId: Value(userId),
      tenantId: Value(tenantId),
      driverId: Value(driverId),
      cursor: cursor == null && nullToAbsent
          ? const Value.absent()
          : Value(cursor),
      accessBlocked: Value(accessBlocked),
    );
  }

  factory DriverUpdateCursor.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DriverUpdateCursor(
      userId: serializer.fromJson<String>(json['userId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      driverId: serializer.fromJson<String>(json['driverId']),
      cursor: serializer.fromJson<String?>(json['cursor']),
      accessBlocked: serializer.fromJson<bool>(json['accessBlocked']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'userId': serializer.toJson<String>(userId),
      'tenantId': serializer.toJson<String>(tenantId),
      'driverId': serializer.toJson<String>(driverId),
      'cursor': serializer.toJson<String?>(cursor),
      'accessBlocked': serializer.toJson<bool>(accessBlocked),
    };
  }

  DriverUpdateCursor copyWith({
    String? userId,
    String? tenantId,
    String? driverId,
    Value<String?> cursor = const Value.absent(),
    bool? accessBlocked,
  }) => DriverUpdateCursor(
    userId: userId ?? this.userId,
    tenantId: tenantId ?? this.tenantId,
    driverId: driverId ?? this.driverId,
    cursor: cursor.present ? cursor.value : this.cursor,
    accessBlocked: accessBlocked ?? this.accessBlocked,
  );
  DriverUpdateCursor copyWithCompanion(DriverUpdateCursorsCompanion data) {
    return DriverUpdateCursor(
      userId: data.userId.present ? data.userId.value : this.userId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      driverId: data.driverId.present ? data.driverId.value : this.driverId,
      cursor: data.cursor.present ? data.cursor.value : this.cursor,
      accessBlocked: data.accessBlocked.present
          ? data.accessBlocked.value
          : this.accessBlocked,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DriverUpdateCursor(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('driverId: $driverId, ')
          ..write('cursor: $cursor, ')
          ..write('accessBlocked: $accessBlocked')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(userId, tenantId, driverId, cursor, accessBlocked);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DriverUpdateCursor &&
          other.userId == this.userId &&
          other.tenantId == this.tenantId &&
          other.driverId == this.driverId &&
          other.cursor == this.cursor &&
          other.accessBlocked == this.accessBlocked);
}

class DriverUpdateCursorsCompanion extends UpdateCompanion<DriverUpdateCursor> {
  final Value<String> userId;
  final Value<String> tenantId;
  final Value<String> driverId;
  final Value<String?> cursor;
  final Value<bool> accessBlocked;
  final Value<int> rowid;
  const DriverUpdateCursorsCompanion({
    this.userId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.driverId = const Value.absent(),
    this.cursor = const Value.absent(),
    this.accessBlocked = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  DriverUpdateCursorsCompanion.insert({
    required String userId,
    required String tenantId,
    required String driverId,
    this.cursor = const Value.absent(),
    this.accessBlocked = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : userId = Value(userId),
       tenantId = Value(tenantId),
       driverId = Value(driverId);
  static Insertable<DriverUpdateCursor> custom({
    Expression<String>? userId,
    Expression<String>? tenantId,
    Expression<String>? driverId,
    Expression<String>? cursor,
    Expression<bool>? accessBlocked,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (userId != null) 'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (driverId != null) 'driver_id': driverId,
      if (cursor != null) 'cursor': cursor,
      if (accessBlocked != null) 'access_blocked': accessBlocked,
      if (rowid != null) 'rowid': rowid,
    });
  }

  DriverUpdateCursorsCompanion copyWith({
    Value<String>? userId,
    Value<String>? tenantId,
    Value<String>? driverId,
    Value<String?>? cursor,
    Value<bool>? accessBlocked,
    Value<int>? rowid,
  }) {
    return DriverUpdateCursorsCompanion(
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      driverId: driverId ?? this.driverId,
      cursor: cursor ?? this.cursor,
      accessBlocked: accessBlocked ?? this.accessBlocked,
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
    if (driverId.present) {
      map['driver_id'] = Variable<String>(driverId.value);
    }
    if (cursor.present) {
      map['cursor'] = Variable<String>(cursor.value);
    }
    if (accessBlocked.present) {
      map['access_blocked'] = Variable<bool>(accessBlocked.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DriverUpdateCursorsCompanion(')
          ..write('userId: $userId, ')
          ..write('tenantId: $tenantId, ')
          ..write('driverId: $driverId, ')
          ..write('cursor: $cursor, ')
          ..write('accessBlocked: $accessBlocked, ')
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
  late final $CommandResultsTable commandResults = $CommandResultsTable(this);
  late final $CommandResultDependenciesTable commandResultDependencies =
      $CommandResultDependenciesTable(this);
  late final $InspectionDraftsTable inspectionDrafts = $InspectionDraftsTable(
    this,
  );
  late final $CameraCapturesTable cameraCaptures = $CameraCapturesTable(this);
  late final $RoadDraftsTable roadDrafts = $RoadDraftsTable(this);
  late final $RoadCapturesTable roadCaptures = $RoadCapturesTable(this);
  late final $CachedDriverUpdatesTable cachedDriverUpdates =
      $CachedDriverUpdatesTable(this);
  late final $DriverUpdateCursorsTable driverUpdateCursors =
      $DriverUpdateCursorsTable(this);
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
    commandResults,
    commandResultDependencies,
    inspectionDrafts,
    cameraCaptures,
    roadDrafts,
    roadCaptures,
    cachedDriverUpdates,
    driverUpdateCursors,
  ];
}
