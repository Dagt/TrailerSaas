import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:http/http.dart' as http;
import 'package:firebase_auth/firebase_auth.dart';

import 'auth_gateway.dart';

class ApiProblem implements Exception {
  const ApiProblem(
    this.status,
    this.code,
    this.detail, {
    this.traceId,
    this.errors = const [],
  });
  final int status;
  final String code;
  final String detail;
  final String? traceId;
  final List<Object?> errors;
  @override
  String toString() => 'ApiProblem($status, $code)';
}

class ApiUnavailable implements Exception {
  @override
  String toString() => 'API unavailable';
}

class ApiClient {
  ApiClient(this.baseUrl, this.auth, this.client);
  final Uri baseUrl;
  final AuthGateway auth;
  final http.Client client;

  /// One submission only. The caller durably stores an immutable key/payload first.
  Future<Map<String, dynamic>> post(
    String path,
    Map<String, dynamic> body,
    String key,
  ) async {
    final uid = auth.uid;
    if (uid == null) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Sign in to continue.',
      );
    }
    String? token;
    try {
      token = await auth.token(forceRefresh: true);
    } on FirebaseAuthException catch (e) {
      if (e.code == 'network-request-failed') throw ApiUnavailable();
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Sign in to continue.',
      );
    } catch (_) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Sign in to continue.',
      );
    }
    if (auth.uid != uid || token == null) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Sign in to continue.',
      );
    }
    http.Response response;
    try {
      response = await client
          .post(
            Uri.parse(
              '${baseUrl.toString().replaceFirst(RegExp(r'/$'), '')}/$path',
            ),
            headers: {
              'Authorization': 'Bearer $token',
              'Accept': 'application/json',
              'Content-Type': 'application/json',
              'Idempotency-Key': key,
              'X-Request-Id': List.generate(
                16,
                (_) => Random.secure()
                    .nextInt(256)
                    .toRadixString(16)
                    .padLeft(2, '0'),
              ).join(),
            },
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 20));
    } on SocketException {
      throw ApiUnavailable();
    } on http.ClientException {
      throw ApiUnavailable();
    } on TimeoutException {
      throw ApiUnavailable();
    }
    if (auth.uid != uid) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Sign in to continue.',
      );
    }
    return _decode(response);
  }

  Map<String, dynamic> _decode(http.Response response) {
    Map<String, dynamic> body;
    try {
      body = jsonDecode(response.body) as Map<String, dynamic>;
    } catch (_) {
      throw ApiProblem(
        response.statusCode,
        'UNEXPECTED_RESPONSE',
        'The server could not complete the request.',
      );
    }
    if (response.statusCode >= 400) {
      throw ApiProblem(
        response.statusCode,
        body['code'] as String? ?? 'UNEXPECTED_FAILURE',
        response.statusCode >= 500
            ? 'The server could not complete the request.'
            : body['detail'] as String? ??
                  'The request could not be completed.',
        traceId: body['traceId'] as String?,
        errors: body['errors'] as List<Object?>? ?? [],
      );
    }
    return body;
  }

  Future<Map<String, dynamic>> get(
    String path, [
    Map<String, String> query = const {},
  ]) async {
    final uid = auth.uid;
    if (uid == null) {
      throw const ApiProblem(
        401,
        'AUTHENTICATION_REQUIRED',
        'Sign in to continue.',
      );
    }
    // Only GET may be retried once after 401. No mutation transport/replay in #42.
    for (var attempt = 0; attempt < 2; attempt++) {
      String? token;
      try {
        token = await auth.token(forceRefresh: attempt == 1);
      } on FirebaseAuthException catch (e) {
        if (e.code == 'network-request-failed') throw ApiUnavailable();
        throw const ApiProblem(
          401,
          'AUTHENTICATION_REQUIRED',
          'Sign in to continue.',
        );
      } catch (_) {
        throw const ApiProblem(
          401,
          'AUTHENTICATION_REQUIRED',
          'Sign in to continue.',
        );
      }
      if (auth.uid != uid || token == null) {
        throw const ApiProblem(
          401,
          'AUTHENTICATION_REQUIRED',
          'Sign in to continue.',
        );
      }
      http.Response response;
      try {
        final random = Random.secure();
        final requestId = List.generate(
          16,
          (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
        ).join();
        response = await client
            .get(
              Uri.parse(
                '${baseUrl.toString().replaceFirst(RegExp(r'/$'), '')}/$path',
              ).replace(queryParameters: query.isEmpty ? null : query),
              headers: {
                'Authorization': 'Bearer $token',
                'Accept': 'application/json',
                'X-Request-Id': requestId,
              },
            )
            .timeout(const Duration(seconds: 20));
      } on SocketException {
        throw ApiUnavailable();
      } on http.ClientException {
        throw ApiUnavailable();
      } on TimeoutException {
        throw ApiUnavailable();
      }
      if (auth.uid != uid) {
        throw const ApiProblem(
          401,
          'AUTHENTICATION_REQUIRED',
          'Sign in to continue.',
        );
      }
      if (response.statusCode == 401 && attempt == 0) continue;
      Map<String, dynamic> body;
      try {
        body = jsonDecode(response.body) as Map<String, dynamic>;
      } catch (_) {
        throw ApiProblem(
          response.statusCode,
          'UNEXPECTED_RESPONSE',
          'The server could not complete the request.',
        );
      }
      if (response.statusCode >= 400) {
        throw ApiProblem(
          response.statusCode,
          body['code'] as String? ?? 'UNEXPECTED_FAILURE',
          response.statusCode >= 500
              ? 'The server could not complete the request.'
              : body['detail'] as String? ??
                    'The request could not be completed.',
          traceId: body['traceId'] as String?,
          errors: body['errors'] as List<Object?>? ?? [],
        );
      }
      return body;
    }
    throw const ApiProblem(
      401,
      'AUTHENTICATION_REQUIRED',
      'Sign in to continue.',
    );
  }
}
