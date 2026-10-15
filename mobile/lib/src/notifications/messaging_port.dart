import 'dart:async';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

enum PushPermission { unknown, denied, authorized, provisional }

class PushHint {
  const PushHint(this.tenantId, this.alertId);
  final String tenantId, alertId;
  static PushHint? parse(Map<String, dynamic> data) {
    final uuid = RegExp(
      r'^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$',
    );
    if (data.length != 3 ||
        data['kind'] != 'ALERT' ||
        data['tenantId'] is! String ||
        data['alertId'] is! String ||
        !uuid.hasMatch(data['tenantId'] as String) ||
        !uuid.hasMatch(data['alertId'] as String)) {
      return null;
    }
    return PushHint(data['tenantId'] as String, data['alertId'] as String);
  }
}

abstract interface class MessagingPort {
  String? get platform;
  Future<PushPermission> permission({bool request = false});
  Future<void> enabled(bool value);
  Future<String?> token();
  Future<void> deleteToken();
  Stream<String> get tokenChanges;
  Stream<PushHint> get hints;
  Future<PushHint?> initialHint();
}

class NativeMessaging implements MessagingPort {
  NativeMessaging(this.messaging);
  final FirebaseMessaging messaging;
  @override
  String? get platform => kIsWeb
      ? null
      : switch (defaultTargetPlatform) {
          TargetPlatform.android => 'ANDROID',
          TargetPlatform.iOS => 'IOS',
          _ => null,
        };
  @override
  Future<PushPermission> permission({bool request = false}) async {
    final settings =
        await (request
                ? messaging.requestPermission(
                    alert: true,
                    badge: true,
                    sound: true,
                    provisional: false,
                  )
                : messaging.getNotificationSettings())
            .timeout(const Duration(seconds: 30));
    return switch (settings.authorizationStatus) {
      AuthorizationStatus.authorized => PushPermission.authorized,
      AuthorizationStatus.provisional => PushPermission.provisional,
      AuthorizationStatus.denied => PushPermission.denied,
      _ => PushPermission.unknown,
    };
  }

  @override
  Future<void> enabled(bool value) =>
      messaging.setAutoInitEnabled(value).timeout(const Duration(seconds: 10));
  @override
  Future<String?> token() async {
    if (platform == 'IOS' &&
        await messaging.getAPNSToken().timeout(const Duration(seconds: 10)) ==
            null) {
      throw StateError('APNs registration is not available.');
    }
    return messaging.getToken().timeout(const Duration(seconds: 10));
  }

  @override
  Future<void> deleteToken() =>
      messaging.deleteToken().timeout(const Duration(seconds: 10));
  @override
  Stream<String> get tokenChanges => messaging.onTokenRefresh;
  @override
  Stream<PushHint> get hints => Stream<PushHint>.multi((controller) {
    void receive(RemoteMessage message) {
      final hint = PushHint.parse(message.data);
      if (hint != null) controller.add(hint);
    }

    final foreground = FirebaseMessaging.onMessage.listen(
      receive,
      onError: (Object _) {},
    );
    final opened = FirebaseMessaging.onMessageOpenedApp.listen(
      receive,
      onError: (Object _) {},
    );
    controller.onCancel = () async {
      await foreground.cancel();
      await opened.cancel();
    };
  });
  @override
  Future<PushHint?> initialHint() async {
    final message = await messaging.getInitialMessage().timeout(
      const Duration(seconds: 10),
    );
    return message == null ? null : PushHint.parse(message.data);
  }
}
