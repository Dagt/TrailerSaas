import 'dart:convert';
import 'dart:async';
import 'dart:io';
import 'dart:math';

import 'package:path_provider/path_provider.dart';

String operationUuid() {
  final r = Random.secure();
  final bytes = List.generate(16, (_) => r.nextInt(256));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  final h = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}';
}

class OnlineCommand {
  OnlineCommand(this.id, this.tripId, this.path, Map<String, dynamic> body)
    : body = Map.unmodifiable(
        jsonDecode(jsonEncode(body)) as Map<String, dynamic>,
      );
  final String id, tripId, path;
  final Map<String, dynamic> body;

  /// Never replay a malformed, relative or other-Tenant endpoint from storage.
  bool belongsTo(String tenant) {
    final parts = path.split('/');
    if (parts.length != 5 ||
        parts[0] != 'tenants' ||
        parts[1] != tenant ||
        !RegExp(r'^[a-zA-Z0-9-]+$').hasMatch(parts[3])) {
      return false;
    }
    return (parts[2] == 'trips' &&
            parts[3] == tripId &&
            {'hooks', 'drops', 'departures'}.contains(parts[4])) ||
        (parts[2] == 'stops' &&
            {
              'arrivals',
              'service-starts',
              'completions',
              'departures',
            }.contains(parts[4]));
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'tripId': tripId,
    'path': path,
    'body': body,
  };
  factory OnlineCommand.fromJson(Map<String, dynamic> j) => OnlineCommand(
    j['id'] as String,
    j['tripId'] as String,
    j['path'] as String,
    j['body'] as Map<String, dynamic>,
  );
}

abstract interface class OnlineCommandJournal {
  Future<OnlineCommand?> read(String user, String tenant);
  Future<void> save(String user, String tenant, OnlineCommand command);
  Future<void> clear(String user, String tenant, {String? operationId});
}

/// One recoverable foreground submission per User/Tenant, not an offline replay queue.
/// No tokens, passwords, QR payloads, images or background worker. Retains uncertain outcomes.
class FileOnlineCommandJournal implements OnlineCommandJournal {
  FileOnlineCommandJournal([Future<Directory> Function()? directory])
    : _directory = directory ?? getApplicationSupportDirectory;
  final Future<Directory> Function() _directory;
  // Serialize even across reopened controller instances; only one foreground receipt exists.
  static Future<void> _tail = Future.value();
  Future<T> _serial<T>(Future<T> Function() work) async {
    final prior = _tail;
    final release = Completer<void>();
    _tail = release.future;
    await prior;
    try {
      return await work();
    } finally {
      release.complete();
    }
  }

  Future<File> _file(String user, String tenant) async {
    final directory = await _directory();
    final name = base64Url.encode(utf8.encode('$user|$tenant'));
    return File('${directory.path}/online-command-$name.json');
  }

  @override
  Future<OnlineCommand?> read(String user, String tenant) => _serial(() async {
    final file = await _file(user, tenant);
    if (!await file.exists()) return null;
    return OnlineCommand.fromJson(
      jsonDecode(await file.readAsString()) as Map<String, dynamic>,
    );
  });

  @override
  Future<void> save(String user, String tenant, OnlineCommand command) =>
      _serial(() async {
        final file = await _file(user, tenant);
        // Never replace an unresolved submission with a newly generated key.
        if (await file.exists()) {
          throw StateError('A submission already requires reconciliation.');
        }
        final temporary = File('${file.path}.tmp');
        await temporary.writeAsString(
          jsonEncode(command.toJson()),
          flush: true,
        );
        await temporary.rename(file.path);
      });

  @override
  Future<void> clear(String user, String tenant, {String? operationId}) =>
      _serial(() async {
        final file = await _file(user, tenant);
        if (await file.exists()) {
          if (operationId != null) {
            final saved = OnlineCommand.fromJson(
              jsonDecode(await file.readAsString()) as Map<String, dynamic>,
            );
            if (saved.id != operationId) return;
          }
          await file.delete();
        }
      });
}
