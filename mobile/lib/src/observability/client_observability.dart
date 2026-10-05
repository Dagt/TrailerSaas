import 'package:sentry_flutter/sentry_flutter.dart';

import '../configuration/app_configuration.dart';

typedef SentryInitializer = Future<void> Function(
  FlutterOptionsConfiguration configure, {
  AppRunner? appRunner,
});

Future<void> runWithObservability(
  AppConfiguration configuration,
  AppRunner appRunner, {
  SentryInitializer initialize = SentryFlutter.init,
}) async {
  if (configuration.sentryDsn == null) {
    await appRunner();
    return;
  }
  await initialize((options) {
    options
      ..dsn = configuration.sentryDsn
      ..environment = configuration.environment.name
      ..sendDefaultPii = false
      ..maxBreadcrumbs = 0
      ..enableAutoNativeBreadcrumbs = false
      ..enableUserInteractionBreadcrumbs = false
      ..enablePrintBreadcrumbs = false
      ..enableAutoSessionTracking = false
      ..enableNativeCrashHandling = false
      ..anrEnabled = false
      ..enableAppHangTracking = false
      ..enableAutoPerformanceTracing = false
      ..enableUserInteractionTracing = false
      ..enableNativeTraceSync = false
      ..tracesSampleRate = 0
      ..attachScreenshot = false
      ..enableLogs = false
      ..enableMetrics = false
      ..beforeBreadcrumb = ((breadcrumb, hint) => null)
      ..beforeSend = sanitizeSentryEvent;
  }, appRunner: appRunner);
}

String? _safeCodeUrl(String? value) {
  if (value == null) return null;
  final uri = Uri.tryParse(value);
  if (uri == null) return null;
  return uri.replace(userInfo: '', query: '', fragment: '').toString();
}

// Construct a new event: payloads, arbitrary messages, contexts and attachments never cross this boundary.
SentryEvent sanitizeSentryEvent(SentryEvent event, Hint hint) {
  hint.attachments.clear();
  return SentryEvent(
    eventId: event.eventId,
    timestamp: event.timestamp,
    platform: event.platform,
    release: event.release,
    dist: event.dist,
    environment: event.environment,
    level: event.level,
    exceptions: event.exceptions
        ?.map(
          (exception) => SentryException(
            type:
                const [
                  'Error',
                  'StateError',
                  'ArgumentError',
                  'TypeError',
                  'FormatException',
                ].contains(exception.type)
                ? exception.type
                : 'Exception',
            value: 'Client exception (message omitted for privacy)',
            stackTrace: exception.stackTrace == null
                ? null
                : SentryStackTrace(
                    frames: exception.stackTrace!.frames
                        .map(
                          (frame) => SentryStackFrame(
                            fileName: _safeCodeUrl(frame.fileName),
                            function: frame.function,
                            lineNo: frame.lineNo,
                            colNo: frame.colNo,
                            inApp: frame.inApp,
                          ),
                        )
                        .toList(),
                  ),
          ),
        )
        .toList(),
  );
}
