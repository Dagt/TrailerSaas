import 'dart:async';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';

final uuidPattern = RegExp(
  r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
);

class MediaFileProblem implements Exception {
  const MediaFileProblem(this.code);
  final String code;
  @override
  String toString() => 'MediaFileProblem($code)';
}

class StoredMediaFile {
  const StoredMediaFile(this.key, this.size, this.sha256);
  final String key, sha256;
  final int size;
}

abstract interface class MediaFileStore {
  Future<T> exclusive<T>(Future<T> Function() work);
  Future<StoredMediaFile> capture(
    String user,
    String tenant,
    String id,
    Stream<List<int>> source,
    int maxBytes,
  );
  Future<StoredMediaFile> inspect(String user, String tenant, String key);
  Future<Stream<List<int>>> open(String user, String tenant, String key);
  Future<void> remove(String user, String tenant, String key);
  Future<List<String>> inventory(String user, String tenant);
}

/// App-private UUID namespace. No paths/URLs are accepted from callers. Files
/// remain on all failures; orphan inspection is explicit, never destructive.
class PrivateMediaFileStore implements MediaFileStore {
  PrivateMediaFileStore([Future<Directory> Function()? directory])
    : directory = directory ?? getApplicationSupportDirectory;
  final Future<Directory> Function() directory;
  static final _lanes = <String, Future<void>>{};
  @override
  Future<T> exclusive<T>(Future<T> Function() work) async {
    final root = await directory();
    await root.create(recursive: true);
    final key = await root.resolveSymbolicLinks();
    final previous = _lanes[key] ?? Future<void>.value(),
        release = Completer<void>();
    _lanes[key] = release.future;
    await previous;
    RandomAccessFile? lock;
    try {
      final file = File('$key/media-sync.lock');
      final type = await FileSystemEntity.type(file.path, followLinks: false);
      if (type != FileSystemEntityType.notFound &&
          type != FileSystemEntityType.file) {
        throw const MediaFileProblem('UNSAFE_FILE');
      }
      lock = await file.open(mode: FileMode.append);
      await lock.lock(FileLock.blockingExclusive);
      return await work();
    } finally {
      try {
        await lock?.close();
      } finally {
        release.complete();
        if (identical(_lanes[key], release.future)) _lanes.remove(key);
      }
    }
  }

  Future<Directory> _namespace(String user, String tenant) async {
    if (!uuidPattern.hasMatch(user) || !uuidPattern.hasMatch(tenant)) {
      throw const MediaFileProblem('INVALID_NAMESPACE');
    }
    final root = await directory();
    await root.create(recursive: true);
    var current = root;
    for (final name in ['private-media', user, tenant]) {
      final path = '${current.path}/$name';
      final type = await FileSystemEntity.type(path, followLinks: false);
      if (type != FileSystemEntityType.notFound &&
          type != FileSystemEntityType.directory) {
        throw const MediaFileProblem('UNSAFE_FILE');
      }
      current = Directory(path);
      await current.create();
    }
    return current;
  }

  Future<File> _file(String user, String tenant, String key) async {
    if (!uuidPattern.hasMatch(key)) throw const MediaFileProblem('UNSAFE_FILE');
    final dir = await _namespace(user, tenant);
    final file = File('${dir.path}/$key.bin');
    final type = await FileSystemEntity.type(file.path, followLinks: false);
    if (type != FileSystemEntityType.file &&
        type != FileSystemEntityType.notFound) {
      throw const MediaFileProblem('UNSAFE_FILE');
    }
    return file;
  }

  @override
  Future<StoredMediaFile> capture(
    String user,
    String tenant,
    String id,
    Stream<List<int>> source,
    int maxBytes,
  ) async {
    final file = await _file(user, tenant, id);
    if (await file.exists() || await File('${file.path}.part').exists()) {
      throw const MediaFileProblem('DUPLICATE_CAPTURE');
    }
    final temporary = File('${file.path}.part');
    await temporary.create(exclusive: true);
    final output = await temporary.open(mode: FileMode.writeOnly);
    var size = 0;
    try {
      await for (final chunk in source) {
        size += chunk.length;
        if (size > maxBytes || size > 104857600) {
          throw const MediaFileProblem('FILE_TOO_LARGE');
        }
        await output.writeFrom(chunk);
      }
      if (size == 0) throw const MediaFileProblem('EMPTY_FILE');
      await output.flush();
    } finally {
      await output.close();
    }
    // Rename in the same private directory; a crash leaves an inspectable .part.
    await temporary.rename(file.path);
    return inspect(user, tenant, id);
  }

  @override
  Future<StoredMediaFile> inspect(
    String user,
    String tenant,
    String key,
  ) async {
    final file = await _file(user, tenant, key);
    if (!await file.exists()) throw const MediaFileProblem('FILE_MISSING');
    final size = await file.length();
    if (size < 1 || size > 104857600) {
      throw const MediaFileProblem('INVALID_FILE_SIZE');
    }
    final digest = await sha256.bind(file.openRead()).single;
    return StoredMediaFile(key, size, digest.toString());
  }

  @override
  Future<Stream<List<int>>> open(String user, String tenant, String key) async {
    final file = await _file(user, tenant, key);
    if (!await file.exists()) throw const MediaFileProblem('FILE_MISSING');
    return file.openRead();
  }

  @override
  Future<void> remove(String user, String tenant, String key) async {
    final file = await _file(user, tenant, key);
    if (await file.exists()) await file.delete();
  }

  @override
  Future<List<String>> inventory(String user, String tenant) async {
    final dir = await _namespace(user, tenant);
    return (await dir.list(followLinks: false).toList())
        .whereType<File>()
        .map((f) => f.uri.pathSegments.last)
        .where(
          (name) => RegExp(r'^[0-9a-f-]{36}\.bin(?:\.part)?$').hasMatch(name),
        )
        .toList();
  }
}
