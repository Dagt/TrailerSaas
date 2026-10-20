import 'package:flutter/material.dart';

import '../ui/fleet_widgets.dart';

import 'notifications_controller.dart';

class NotificationsPanel extends StatelessWidget {
  const NotificationsPanel({super.key, required this.controller});
  final NotificationsController controller;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      if (controller.session.user == null) return const SizedBox.shrink();
      final detail = controller.detail;
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const FleetSectionTitle(
                'Notifications',
                icon: Icons.notifications_outlined,
              ),
              Semantics(liveRegion: true, child: Text(controller.status)),
              Wrap(
                spacing: 12,
                children: [
                  TextButton(
                    onPressed: controller.busy
                        ? null
                        : () => controller.refresh(requestPermission: true),
                    child: const Text('Enable notifications'),
                  ),
                  TextButton(
                    onPressed: controller.busy ? null : controller.refresh,
                    child: const Text('Retry push registration'),
                  ),
                ],
              ),
              if (controller.hint != null)
                FilledButton(
                  onPressed: controller.reviewHint,
                  child: const Text('Review Alert from server'),
                ),
              if (detail != null)
                Text(
                  'Server Alert · ${detail['status']}\nTrailer ${(detail['relatedResource'] as Map)['operationalNumber']}',
                ),
            ],
          ),
        ),
      );
    },
  );
}
