import 'dart:convert';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../road/online_command_journal.dart';
import '../sync/command_store.dart';

enum EvidenceCommandKind { preTrip, signedBol, noReceiver, fuel, damage }

/// Typed intent for FUTURE owning workflows, not permission to send an endpoint.
/// Local document UUIDs are logical references; the original JSON never changes.
class EvidenceCommandIntent extends CommandIntent {
  EvidenceCommandIntent(
    this.kind,
    OnlineCommand command, {
    super.dependencies,
    super.resources,
  }) : super(command);
  final EvidenceCommandKind kind;
  static String path(
    EvidenceCommandKind kind,
    String tenant,
    String trip,
    String? stop,
  ) => switch (kind) {
    EvidenceCommandKind.preTrip =>
      'tenants/$tenant/driver/me/inspections/pre-trip',
    EvidenceCommandKind.signedBol || EvidenceCommandKind.noReceiver =>
      'tenants/$tenant/stops/$stop/delivery-completions',
    EvidenceCommandKind.fuel => 'tenants/$tenant/driver/me/fuel-events',
    EvidenceCommandKind.damage => 'tenants/$tenant/trips/$trip/exceptions',
  };
  @override
  void validate(String tenant) {
    if (!RegExp(r'^[0-9a-f-]{36}$').hasMatch(command.id) ||
        dependencies.toSet().length != dependencies.length ||
        dependencies.contains(command.id)) {
      throw const FormatException('Invalid evidence intent');
    }
    final parts = command.path.split('/');
    final stop = parts.length == 5 && parts[2] == 'stops' ? parts[3] : null;
    if (command.path != path(kind, tenant, command.tripId, stop) ||
        !RegExp(r'^[a-zA-Z0-9-]+$').hasMatch(command.tripId) ||
        stop != null && !RegExp(r'^[a-zA-Z0-9-]+$').hasMatch(stop)) {
      throw const FormatException('Invalid evidence endpoint');
    }
    final allowed = switch (kind) {
      EvidenceCommandKind.preTrip => {
        'shiftId',
        'tripId',
        'tractorId',
        'odometer',
        'checklist',
        'documentIds',
        'occurredAt',
      },
      EvidenceCommandKind.signedBol => {
        'evidenceMode',
        'bolDocumentId',
        'occurredAt',
      },
      EvidenceCommandKind.noReceiver => {
        'evidenceMode',
        'bolDocumentId',
        'deliveryPhotoDocumentId',
        'comment',
        'occurredAt',
      },
      EvidenceCommandKind.fuel => {
        'tripId',
        'tractorId',
        'odometer',
        'receiptDocumentId',
        'occurredAt',
      },
      EvidenceCommandKind.damage => {
        'stopId',
        'category',
        'comment',
        'documentIds',
        'occurredAt',
      },
    };
    if (!command.body.keys.every(allowed.contains) ||
        command.body['occurredAt'] is! String ||
        occurredAt.isAfter(DateTime.now().toUtc())) {
      throw const FormatException('Invalid evidence payload');
    }
    if (kind == EvidenceCommandKind.signedBol &&
            command.body['evidenceMode'] != 'SIGNED_BOL' ||
        kind == EvidenceCommandKind.noReceiver &&
            (command.body['evidenceMode'] != 'NO_RECEIVER_AVAILABLE' ||
                command.body['comment'] is! String ||
                (command.body['comment'] as String).trim().isEmpty)) {
      throw const FormatException('Invalid delivery intent');
    }
    if (kind == EvidenceCommandKind.preTrip &&
            command.body['documentIds'] is! Map ||
        kind == EvidenceCommandKind.damage &&
            command.body['documentIds'] is! List) {
      throw const FormatException('Invalid media set');
    }
  }

  static EvidenceCommandKind? kindFor(LocalCommand c) =>
      switch (c.commandType) {
        'pre-trip' => EvidenceCommandKind.preTrip,
        'delivery-completions' =>
          (jsonDecode(c.payloadJson) as Map)['evidenceMode'] == 'SIGNED_BOL'
              ? EvidenceCommandKind.signedBol
              : EvidenceCommandKind.noReceiver,
        'fuel-events' => EvidenceCommandKind.fuel,
        'exceptions' => EvidenceCommandKind.damage,
        _ => null,
      };
}

/// Future workflow-owned adapter. None is installed in production before #43/
/// #45. Tests use a controlled actual HTTP peer; this is not a generic executor.
abstract interface class EvidenceReplayPort {
  Future<Map<String, dynamic>> send(
    LocalCommand intent,
    Map<String, dynamic> frozenBody,
  );
}

class MediaDependencies {
  MediaDependencies(this.commands);
  final CommandStore commands;
  AppDatabase get db => commands.db;

  Future<void> enqueue(
    String user,
    String tenant,
    EvidenceCommandIntent intent,
  ) => db.transaction(() async {
    intent.validate(tenant);
    final references = _references(intent);
    if (references.values.toSet().length != references.length) {
      throw const FormatException('Duplicate media');
    }
    for (final entry in references.entries) {
      final rows =
          await (db.select(db.localMedia)..where(
                (r) =>
                    r.id.equals(entry.value) &
                    r.userId.equals(user) &
                    r.tenantId.equals(tenant),
              ))
              .get();
      if (rows.length != 1 ||
          (rows.single.tripId != null &&
              rows.single.tripId != intent.command.tripId) ||
          rows.single.fileState != 'PRESENT' ||
          rows.single.documentType != _type(intent.kind, entry.key)) {
        throw const FormatException('Unavailable media reference');
      }
    }
    final old = await commands.find(user, tenant, intent.command.id);
    if (old != null) {
      if (old.endpointPath != intent.command.path ||
          old.tripId != intent.command.tripId ||
          old.payloadJson != jsonEncode(intent.command.body)) {
        throw StateError('Intent cannot change');
      }
      final previous = {
        for (final edge in await edges(old)) edge.fieldPath: edge.mediaId,
      };
      if (previous.length != references.length ||
          references.entries.any((e) => previous[e.key] != e.value)) {
        throw StateError('Bindings cannot change');
      }
      return;
    }
    await commands.enqueue(user, tenant, intent, followResources: true);
    if (old != null) return; // Original immutable binding already committed.
    for (final entry in references.entries) {
      await db
          .into(db.commandMediaDependencies)
          .insert(
            CommandMediaDependenciesCompanion.insert(
              clientOperationId: intent.command.id,
              userId: user,
              tenantId: tenant,
              mediaId: entry.value,
              fieldPath: entry.key,
            ),
          );
    }
  });
  static Map<String, String> _references(EvidenceCommandIntent intent) {
    final b = intent.command.body;
    final result = <String, String>{};
    void add(String field, Object? id) {
      if (id is! String) throw const FormatException('Evidence is required');
      result[field] = id;
    }

    switch (intent.kind) {
      case EvidenceCommandKind.preTrip:
        final refs = b['documentIds'] as Map;
        if (refs.keys.toSet().difference({
          'front',
          'rear',
          'driverSide',
          'passengerSide',
        }).isNotEmpty) {
          throw const FormatException('Invalid positions');
        }
        for (final p in ['front', 'rear', 'driverSide', 'passengerSide']) {
          add('documentIds.$p', refs[p]);
        }
      case EvidenceCommandKind.signedBol:
        add('bolDocumentId', b['bolDocumentId']);
      case EvidenceCommandKind.noReceiver:
        add('bolDocumentId', b['bolDocumentId']);
        add('deliveryPhotoDocumentId', b['deliveryPhotoDocumentId']);
      case EvidenceCommandKind.fuel:
        add('receiptDocumentId', b['receiptDocumentId']);
      case EvidenceCommandKind.damage:
        final refs = b['documentIds'] as List;
        if (refs.isEmpty || refs.length > 50) {
          throw const FormatException('Invalid damage photos');
        }
        for (var n = 0; n < refs.length; n++) {
          add('documentIds.$n', refs[n]);
        }
    }
    return result;
  }

  static String _type(EvidenceCommandKind kind, String field) => switch (kind) {
    EvidenceCommandKind.preTrip => switch (field) {
      'documentIds.front' => 'PRETRIP_FRONT',
      'documentIds.rear' => 'PRETRIP_REAR',
      'documentIds.driverSide' => 'PRETRIP_DRIVER_SIDE',
      _ => 'PRETRIP_PASSENGER_SIDE',
    },
    EvidenceCommandKind.signedBol => 'BOL',
    EvidenceCommandKind.noReceiver =>
      field == 'bolDocumentId' ? 'BOL' : 'DELIVERY_PHOTO',
    EvidenceCommandKind.fuel => 'FUEL_RECEIPT',
    EvidenceCommandKind.damage => 'DAMAGE_PHOTO',
  };
  Future<List<CommandMediaDependency>> edges(LocalCommand c) =>
      (db.select(db.commandMediaDependencies)..where(
            (r) =>
                r.clientOperationId.equals(c.clientOperationId) &
                r.userId.equals(c.userId) &
                r.tenantId.equals(c.tenantId),
          ))
          .get();
  Future<bool> ready(LocalCommand c) async {
    final kind = EvidenceCommandIntent.kindFor(c), links = await edges(c);
    if (kind == null && links.isNotEmpty) return false;
    if (kind != null && links.isEmpty) return false;
    Map<String, String>? expected;
    if (kind != null) {
      try {
        final intent = EvidenceCommandIntent(
          kind,
          OnlineCommand(
            c.clientOperationId,
            c.tripId,
            c.endpointPath,
            jsonDecode(c.payloadJson) as Map<String, dynamic>,
          ),
        );
        intent.validate(c.tenantId);
        expected = _references(intent);
      } catch (_) {
        return false;
      }
      if (links.length != expected.length ||
          links.any((l) => expected![l.fieldPath] != l.mediaId)) {
        return false;
      }
    }
    final serverIds = <String>{};
    for (final link in links) {
      final row =
          await (db.select(db.localMedia)..where(
                (r) =>
                    r.id.equals(link.mediaId) &
                    r.userId.equals(c.userId) &
                    r.tenantId.equals(c.tenantId),
              ))
              .getSingleOrNull();
      if (row == null ||
          row.state != 'FINALIZED' ||
          row.serverDocumentId == null ||
          kind != null && row.documentType != _type(kind, link.fieldPath) ||
          !serverIds.add(row.serverDocumentId!)) {
        return false;
      }
    }
    return true;
  }

  Future<Map<String, dynamic>?> materialize(LocalCommand c) =>
      db.transaction(() async {
        if (EvidenceCommandIntent.kindFor(c) == null &&
            (await edges(c)).isEmpty) {
          return jsonDecode(c.payloadJson) as Map<String, dynamic>;
        }
        final existing =
            await (db.select(db.materializedRequests)..where(
                  (r) =>
                      r.clientOperationId.equals(c.clientOperationId) &
                      r.userId.equals(c.userId) &
                      r.tenantId.equals(c.tenantId),
                ))
                .getSingleOrNull();
        if (existing != null) {
          return jsonDecode(existing.payloadJson) as Map<String, dynamic>;
        }
        if (!await ready(c)) return null;
        if (c.attemptCount > 0 || c.state == 'SENDING') {
          throw StateError('Cannot first materialize after a send');
        }
        final body = jsonDecode(c.payloadJson) as Map<String, dynamic>;
        for (final edge in await edges(c)) {
          final row =
              (await (db.select(db.localMedia)..where(
                    (r) =>
                        r.id.equals(edge.mediaId) &
                        r.userId.equals(c.userId) &
                        r.tenantId.equals(c.tenantId),
                  ))
                  .getSingle());
          final parts = edge.fieldPath.split('.');
          if (parts.length == 1) {
            body[parts.single] = row.serverDocumentId;
          } else {
            final nested = body[parts.first];
            if (nested is Map) {
              nested[parts.last] = row.serverDocumentId;
            } else if (nested is List) {
              nested[int.parse(parts.last)] = row.serverDocumentId;
            } else {
              throw const FormatException('Invalid binding');
            }
          }
        }
        await db
            .into(db.materializedRequests)
            .insert(
              MaterializedRequestsCompanion.insert(
                clientOperationId: c.clientOperationId,
                userId: c.userId,
                tenantId: c.tenantId,
                endpointPath: c.endpointPath,
                httpMethod: c.httpMethod,
                payloadJson: jsonEncode(body),
                createdAt: commands.now(),
              ),
            );
        return body;
      });
}
