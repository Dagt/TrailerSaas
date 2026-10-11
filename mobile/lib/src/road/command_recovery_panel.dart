import 'package:flutter/material.dart';

import '../session/session_controller.dart';
import 'online_command_journal.dart';
import 'road_trip_controller.dart';

/// Foreground reconciliation is reachable without an assigned Trip or cached detail.
class CommandRecoveryPanel extends StatefulWidget {
  const CommandRecoveryPanel({
    super.key,
    required this.session,
    required this.journal,
  });
  final SessionController session;
  final OnlineCommandJournal journal;
  @override
  State<CommandRecoveryPanel> createState() => _CommandRecoveryPanelState();
}

class _CommandRecoveryPanelState extends State<CommandRecoveryPanel>
    with WidgetsBindingObserver {
  late final RoadTripController controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    controller = RoadTripController(
      widget.session,
      '',
      widget.journal,
      recoveryOnly: true,
    );
    controller.load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !controller.sending) {
      controller.load();
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      if (controller.pending == null && controller.message == null) {
        return const SizedBox.shrink();
      }
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Previous online submission'),
              if (controller.pending != null)
                const Text(
                  'An earlier operation needs reconciliation. Do not repeat the physical action.',
                ),
              if (controller.message != null &&
                  controller.message != widget.session.message)
                Semantics(liveRegion: true, child: Text(controller.message!)),
              if (controller.traceId != null)
                Text('Support reference: ${controller.traceId}'),
              if (controller.retryNotBefore != null)
                Text(
                  'Recovery available after: ${controller.retryNotBefore!.toLocal()}',
                ),
              if (controller.pending != null)
                FilledButton(
                  onPressed: controller.canRecover
                      ? () async {
                          final confirmed = await showDialog<bool>(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: const Text('Recover previous submission'),
                              content: const Text(
                                'Use the saved key and payload with current server authorization. This does not repeat the physical action.',
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () =>
                                      Navigator.pop(context, false),
                                  child: const Text('Cancel'),
                                ),
                                FilledButton(
                                  onPressed: () => Navigator.pop(context, true),
                                  child: const Text('Recover'),
                                ),
                              ],
                            ),
                          );
                          if (confirmed == true && mounted) {
                            await controller.recover();
                          }
                        }
                      : null,
                  child: const Text('Reconcile previous operation'),
                ),
            ],
          ),
        ),
      );
    },
  );
}
