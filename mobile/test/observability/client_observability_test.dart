import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:sentry_flutter/sentry_flutter.dart';
import 'package:trailersaas_mobile/src/configuration/app_configuration.dart';
import 'package:trailersaas_mobile/src/observability/client_observability.dart';

void main() {
  const dsn = 'https://example@sentry.example.test/1';

  test('without DSN runs the app without initializing the SDK', () async {
    var started = false;
    await runWithObservability(
      AppConfiguration.fromEnvironment(),
      () {
        started = true;
      },
      initialize: (configure, {appRunner}) async {
        fail('SDK must not initialize without DSN');
      },
    );
    expect(started, isTrue);
  });

  for (final environment in ['development', 'staging', 'production']) {
    test(
      '$environment initializes before running with privacy options',
      () async {
        var initialized = false;
        var started = false;
        await runWithObservability(
          AppConfiguration.fromValues(
            environment: environment,
            apiBaseUrl: 'https://api.example.test/api/v1',
            sentryDsn: dsn,
            sentryEnvironment: environment,
          ),
          () {
            expect(initialized, isTrue);
            started = true;
          },
          initialize: (configure, {appRunner}) async {
            final options = SentryFlutterOptions();
            await configure(options);
            expect(options.dsn, dsn);
            expect(options.environment, environment);
            expect(options.sendDefaultPii, isFalse);
            expect(options.maxBreadcrumbs, 0);
            expect(options.enableAutoNativeBreadcrumbs, isFalse);
            expect(options.enableNativeCrashHandling, isFalse);
            expect(options.enableAutoPerformanceTracing, isFalse);
            expect(options.enableUserInteractionTracing, isFalse);
            expect(options.enableLogs, isFalse);
            expect(options.enableMetrics, isFalse);
            expect(options.attachScreenshot, isFalse);
            expect(options.beforeSend, isNotNull);
            expect(
              options.beforeBreadcrumb!(Breadcrumb(message: 'secret'), Hint()),
              isNull,
            );
            initialized = true;
            await appRunner!();
          },
        );
        expect(started, isTrue);
      },
    );
  }

  test('DSN never implicitly changes environment', () {
    expect(
      AppConfiguration.fromValues(sentryDsn: dsn).environment,
      AppEnvironment.development,
    );
  });

  test('rejects mismatched and unknown Sentry environments', () {
    for (final environment in ['production', 'staging', 'unknown']) {
      expect(
        () => AppConfiguration.fromValues(
          sentryDsn: dsn,
          sentryEnvironment: environment,
        ),
        throwsArgumentError,
      );
    }
    expect(
      () => AppConfiguration.fromValues(sentryEnvironment: 'production'),
      throwsArgumentError,
    );
  });

  for (final value in [
    'invalid',
    'http://example@sentry.example.test/1',
    'https://sentry.example.test/1',
    'https://example:secret@sentry.example.test/1',
    'https://example@sentry.example.test/name',
    '$dsn?signature=secret',
    '$dsn#secret',
  ]) {
    test('rejects invalid ingestion DSN: $value', () {
      expect(
        () => AppConfiguration.fromValues(sentryDsn: value),
        throwsArgumentError,
      );
    });
  }

  test('sanitization preserves code frames without payloads, PII or attachments', () {
    const secret = 'synthetic-secret-token';
    final hint = Hint.withAttachments([
      SentryAttachment.fromIntList(utf8.encode(secret), 'document.txt'),
    ]);
    final event = SentryEvent(
      user: SentryUser(email: secret),
      request: SentryRequest(
        url: 'https://example.test/document?signature=$secret',
        headers: {'Authorization': 'Bearer $secret'},
        data: secret,
      ),
      message: SentryMessage(secret),
      breadcrumbs: [Breadcrumb(message: secret)],
      tags: {'commandPayload': secret},
      contexts: Contexts.fromJson({
        'custom': {'location': secret},
      }),
      exceptions: [
        SentryException(
          type: 'TypeError',
          value: secret,
          stackTrace: SentryStackTrace(
            frames: [
              SentryStackFrame(
                fileName:
                    'https://user:secret@web.example.test/app.dart?signature=$secret#secret',
                function: 'build',
                lineNo: 12,
                vars: {'token': secret},
                contextLine: secret,
              ),
            ],
          ),
        ),
      ],
    );
    final sanitized = sanitizeSentryEvent(event, hint);
    expect(jsonEncode(sanitized.toJson()), isNot(contains(secret)));
    expect(sanitized.user, isNull);
    expect(sanitized.request, isNull);
    expect(sanitized.tags, isNull);
    expect(hint.attachments, isEmpty);
    expect(sanitized.exceptions!.single.stackTrace!.frames.single.lineNo, 12);
    expect(
      sanitized.exceptions!.single.stackTrace!.frames.single.fileName,
      'https://web.example.test/app.dart',
    );
  });
}
