import 'dart:async';
import 'dart:io';

import '../persistence/app_database.dart';
import '../session/api_client.dart';
import 'media_file_store.dart';

class PendingDocumentUpload {
  const PendingDocumentUpload(this.documentId, this.url, this.expiresAt);
  final String documentId;
  final Uri url;
  final DateTime expiresAt;
}

abstract interface class DocumentTransport {
  Future<PendingDocumentUpload> initialize(LocalMediaData row);
  Future<DateTime> finalize(LocalMediaData row);
}

class DocumentsClient implements DocumentTransport {
  DocumentsClient(this.api, {DateTime Function()? clock})
    : now = clock ?? (() => DateTime.now().toUtc());
  final ApiClient api;
  final DateTime Function() now;
  Future<Map<String, dynamic>> _post(
    String path,
    Map<String, dynamic> body,
    String? key,
  ) async {
    try {
      return await api.post(path, body, key, forceRefresh: false);
    } on ApiProblem catch (e) {
      if (e.status != 401) rethrow;
      return api.post(path, body, key, forceRefresh: true);
    }
  }

  @override
  Future<PendingDocumentUpload> initialize(LocalMediaData row) async {
    final response = await _post('tenants/${row.tenantId}/documents/uploads', {
      'documentType': row.documentType,
      'contentType': row.contentType,
      'sizeBytes': row.sizeBytes,
      'checksumSha256': row.checksumSha256,
    }, null);
    final id = response['documentId'],
        url = response['uploadUrl'],
        expires = response['expiresAt'];
    if (id is! String ||
        !uuidPattern.hasMatch(id) ||
        response['uploadStatus'] != 'PENDING' ||
        url is! String ||
        expires is! String) {
      throw const FormatException('Invalid upload response');
    }
    final time = DateTime.parse(expires).toUtc();
    if (!time.isAfter(now())) {
      throw const MediaFileProblem('UPLOAD_URL_EXPIRED');
    }
    final uri = Uri.tryParse(url);
    if (uri == null) throw const FormatException('Invalid upload response');
    return PendingDocumentUpload(id, uri, time);
  }

  @override
  Future<DateTime> finalize(LocalMediaData row) async {
    final id = row.serverDocumentId;
    if (id == null || !uuidPattern.hasMatch(id)) {
      throw const FormatException('Missing document identity');
    }
    final response = await _post(
      'tenants/${row.tenantId}/documents/$id/upload-completion',
      {'checksumSha256': row.checksumSha256},
      row.finalizeKey,
    );
    if (response['id'] != id ||
        response['uploadStatus'] != 'UPLOADED' ||
        response['documentType'] != row.documentType ||
        response['contentType'] != row.contentType ||
        response['sizeBytes'] != row.sizeBytes ||
        response['checksumSha256'] != row.checksumSha256 ||
        response['uploadedAt'] is! String) {
      throw const FormatException('Unconfirmed document');
    }
    return DateTime.parse(response['uploadedAt'] as String).toUtc();
  }

  /// Future workflow owners attach only after resource authorization. Upload
  /// finalization is not business evidence acceptance or a permanent read grant.
  Future<void> link(
    LocalMediaData row,
    String entityType,
    String entityId,
  ) async {
    if (row.state != 'FINALIZED' ||
        !{'TRIP', 'TRIP_STOP'}.contains(entityType) ||
        !uuidPattern.hasMatch(entityId)) {
      throw const FormatException('Invalid attachment');
    }
    final result = await _post(
      'tenants/${row.tenantId}/documents/${row.serverDocumentId}/links',
      {'entityType': entityType, 'entityId': entityId},
      null,
    );
    if (result['entityType'] != entityType || result['entityId'] != entityId) {
      throw const FormatException('Unconfirmed attachment');
    }
  }
}

abstract interface class SignedPutTransport {
  Future<void> put(
    PendingDocumentUpload grant,
    LocalMediaData row,
    MediaFileStore files,
  );
}

class SignedPutProblem implements Exception {
  const SignedPutProblem(this.status, {this.retryAfter});
  final int status;
  final DateTime? retryAfter;
  @override
  String toString() => 'SignedPutProblem($status)';
}

/// Separate transport: never receives Firebase/AuthGateway or ApiClient headers.
/// Each PUT owns and closes its HttpClient, disables redirects, and streams bytes.
class R2SignedPutTransport implements SignedPutTransport {
  R2SignedPutTransport({
    this.allowTestLoopback = false,
    DateTime Function()? clock,
    this.timeout = const Duration(seconds: 60),
  }) : now = clock ?? (() => DateTime.now().toUtc());
  final bool allowTestLoopback;
  final DateTime Function() now;
  final Duration timeout;
  void validate(PendingDocumentUpload grant) {
    final u = grant.url;
    if (u.queryParameters.keys.any(
      (k) => !{
        'X-Amz-Algorithm',
        'X-Amz-Credential',
        'X-Amz-Date',
        'X-Amz-Expires',
        'X-Amz-SignedHeaders',
        'X-Amz-Signature',
      }.contains(k),
    )) {
      throw const MediaFileProblem('UNSAFE_UPLOAD_URL');
    }
    final test =
        allowTestLoopback &&
        u.scheme == 'http' &&
        {'127.0.0.1', 'localhost', '::1'}.contains(u.host);
    if (!grant.expiresAt.isAfter(now())) {
      throw const MediaFileProblem('UPLOAD_URL_EXPIRED');
    }
    if ((!test &&
            (u.scheme != 'https' ||
                !u.host.endsWith('.r2.cloudflarestorage.com'))) ||
        u.userInfo.isNotEmpty ||
        u.fragment.isNotEmpty ||
        u.queryParameters['X-Amz-Algorithm'] != 'AWS4-HMAC-SHA256' ||
        (u.queryParameters['X-Amz-Signature'] ?? '').isEmpty) {
      throw const MediaFileProblem('UNSAFE_UPLOAD_URL');
    }
  }

  @override
  Future<void> put(
    PendingDocumentUpload grant,
    LocalMediaData row,
    MediaFileStore files,
  ) async {
    validate(grant);
    final client = HttpClient()..autoUncompress = false;
    HttpClientRequest? request;
    try {
      await (() async {
        request = await client.putUrl(grant.url);
        request!.followRedirects = false;
        request!.contentLength = row.sizeBytes;
        request!.headers.set(HttpHeaders.contentTypeHeader, row.contentType);
        await request!.addStream(
          await files.open(row.userId, row.tenantId, row.fileKey),
        );
        final response = await request!.close();
        final status = response.statusCode;
        final retry = response.headers.value(HttpHeaders.retryAfterHeader);
        await response.drain<void>();
        if (status < 200 || status >= 300) {
          DateTime? retryAt;
          if (retry != null) {
            final seconds = int.tryParse(retry);
            try {
              retryAt = seconds != null
                  ? now().add(Duration(seconds: seconds))
                  : HttpDate.parse(retry).toUtc();
            } catch (_) {}
          }
          throw SignedPutProblem(status, retryAfter: retryAt);
        }
      })().timeout(timeout);
    } finally {
      request?.abort();
      client.close(force: true);
    }
  }
}
