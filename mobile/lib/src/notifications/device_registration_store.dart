import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';

import '../road/online_command_journal.dart';

abstract interface class DeviceRegistrationStore {
  Future<String> ensure(String userId);
}

/// Global owner-scoped registration identity only. No token, Tenant, HTTP intent
/// or replay queue. A stable proposed UUID makes uncertain online upserts safe.
class FileDeviceRegistrationStore implements DeviceRegistrationStore {
  FileDeviceRegistrationStore([Future<Directory> Function()? directory])
    : directory = directory ?? getApplicationSupportDirectory;
  final Future<Directory> Function() directory;
  static Future<void> _tail = Future.value();
  @override
  Future<String> ensure(String userId) async {
    final prior = _tail, release = Completer<void>();
    _tail = release.future;
    await prior;
    try {
      final folder = await directory();
      await folder.create(recursive: true);
      final key = sha256.convert(utf8.encode(userId));
      final file = File('${folder.path}/device-registration-$key.json');
      if (await file.exists()) {
        final data =
            jsonDecode(await file.readAsString()) as Map<String, dynamic>;
        if (data.length != 2 ||
            data['userId'] != userId ||
            data['deviceId'] is! String ||
            !RegExp(r'^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$')
                .hasMatch(data['deviceId'] as String)) {
          throw const FormatException('Registration metadata is invalid.');
        }
        return data['deviceId'] as String;
      }
      final id = operationUuid();
      final temporary = File('${file.path}.tmp');
      await temporary.writeAsString(
        jsonEncode({'userId': userId, 'deviceId': id}),
        flush: true,
      );
      await temporary.rename(file.path);
      return id;
    } finally {
      release.complete();
    }
  }
}
