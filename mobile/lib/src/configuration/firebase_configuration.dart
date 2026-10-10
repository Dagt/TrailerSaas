import 'package:firebase_core/firebase_core.dart';

import 'app_configuration.dart';

FirebaseOptions firebaseOptions(AppConfiguration configuration) =>
    checkedFirebaseOptions(
      configuration,
      environment: const String.fromEnvironment('FIREBASE_ENVIRONMENT'),
      apiKey: const String.fromEnvironment('FIREBASE_API_KEY'),
      appId: const String.fromEnvironment('FIREBASE_APP_ID'),
      projectId: const String.fromEnvironment('FIREBASE_PROJECT_ID'),
      senderId: const String.fromEnvironment('FIREBASE_MESSAGING_SENDER_ID'),
      iosBundleId: const String.fromEnvironment('FIREBASE_IOS_BUNDLE_ID'),
    );

FirebaseOptions checkedFirebaseOptions(
  AppConfiguration configuration, {
  required String environment,
  required String apiKey,
  required String appId,
  required String projectId,
  required String senderId,
  String iosBundleId = '',
}) {
  if (environment != configuration.environment.name ||
      [
        apiKey,
        appId,
        projectId,
        senderId,
      ].any((value) => value.trim().isEmpty)) {
    throw ArgumentError(
      'Firebase public configuration is required and must match APP_ENV.',
    );
  }
  return FirebaseOptions(
    apiKey: apiKey,
    appId: appId,
    messagingSenderId: senderId,
    projectId: projectId,
    iosBundleId: iosBundleId.isEmpty ? null : iosBundleId,
  );
}
