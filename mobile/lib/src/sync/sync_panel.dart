import 'package:flutter/material.dart';

import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import 'command_store.dart';
import 'replay_worker.dart';
import '../media/media_sync_panel.dart';

class SyncPanel extends StatefulWidget {
  const SyncPanel({super.key, required this.worker});
  final ReplayWorker worker;
  @override
  State<SyncPanel> createState() => _SyncPanelState();
}

class _SyncPanelState extends State<SyncPanel> with WidgetsBindingObserver {
  String? _scope, _reviewError;
  Stream<List<LocalCommand>>? _rows;
  Stream<List<LocalSyncIssue>>? _issues;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) widget.worker.sync();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> review(LocalCommand c) async {
    final worker = widget.worker, owner = worker.session.snapshot;
    if (owner == null ||
        owner.userId != c.userId ||
        owner.membership.tenantId != c.tenantId) {
      return;
    }
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Review recorded operation'),
        content: const Text(
          'Refresh current work and review with dispatch. Retry only the SAME recorded action; do not repeat the physical action. The server may still deny it.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Refresh and retry same operation'),
          ),
        ],
      ),
    );
    if (yes != true) return;
    try {
      await worker.session.refresh();
      final current = worker.session.snapshot;
      if (worker.session.state != SessionState.ready ||
          current?.userId != owner.userId ||
          current?.firebaseUid != owner.firebaseUid ||
          current?.membership.tenantId != owner.membership.tenantId) {
        return;
      }
      await worker.store.retryReviewed(
        c.userId,
        c.tenantId,
        c.clientOperationId,
      );
      await worker.sync();
    } catch (_) {
      if (mounted &&
          worker.scope ==
              '${owner.firebaseUid}|${owner.userId}|${owner.membership.tenantId}') {
        setState(
          () => _reviewError = 'Review could not access saved operations. The original operation is retained. Retry after restoring storage and connectivity.',
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.worker,
    builder: (context, _) {
      final w = widget.worker, owner = w.session.snapshot;
      if (owner == null || w.session.auth.uid != owner.firebaseUid) {
        return const SizedBox.shrink();
      }
      final scope = '${owner.userId}|${owner.membership.tenantId}';
      if (_scope != scope) {
        _scope = scope;
        _reviewError = null;
        _rows = w.store.watch(owner.userId, owner.membership.tenantId);
        _issues = w.store.watchIssues(owner.userId, owner.membership.tenantId);
      }
      return StreamBuilder<List<LocalCommand>>(
        key: ValueKey(scope),
        stream: _rows,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return const Text(
              'Saved operations could not be read. Retry synchronization; nothing was discarded.',
            );
          }
          final rows = snapshot.data ?? [];
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Synchronization',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const Text(
                    'Recorded intent is separate from current server state.',
                  ),
                  FilledButton.icon(
                    onPressed:
                        w.sending || w.session.state != SessionState.ready
                        ? null
                        : w.sync,
                    icon: const Icon(Icons.sync),
                    label: const Text('Sync now'),
                  ),
                  if (w.sending)
                    const Text('Syncing — awaiting server confirmation'),
                  if (_reviewError != null)
                    Semantics(liveRegion: true, child: Text(_reviewError!)),
                  if (w.message != null)
                    Semantics(liveRegion: true, child: Text(w.message!)),
                  if (rows.isEmpty) const Text('No recorded operations.'),
                  if (w.media != null) MediaSyncPanel(worker: w),
                  StreamBuilder<List<LocalSyncIssue>>(
                    stream: _issues,
                    builder: (context, issues) => Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (issues.hasError)
                          const Text('Sync issues could not be loaded.'),
                        ...(issues.data ?? [])
                            .take(50)
                            .map((issue) => Text(issue.message)),
                      ],
                    ),
                  ),
                  ...rows.reversed
                      .take(50)
                      .map(
                        (c) => ListTile(
                          title: Text(commandLabel(c.commandType)),
                          subtitle: Text(
                            '${{'EVIDENCE_PENDING', 'FUTURE_WORKFLOW'}.contains(c.lastErrorCode) ? 'Blocked by evidence or pending workflow' : stateLabel(c.state)}${c.lastErrorCode == null ? '' : '\n${issueMessage(c)}'}'
                            '${c.nextRetryAt == null ? '' : '\nNext retry: ${c.nextRetryAt!.toLocal()}'}',
                          ),
                          trailing:
                              {
                                    CommandState.conflict.value,
                                    CommandState.permanentFailure.value,
                                  }.contains(c.state) &&
                                  c.lastErrorCode != 'IDEMPOTENCY_KEY_REUSED'
                              ? TextButton(
                                  onPressed:
                                      w.session.state == SessionState.ready &&
                                          !w.sending
                                      ? () => review(c)
                                      : null,
                                  child: const Text('Review'),
                                )
                              : null,
                        ),
                      ),
                ],
              ),
            ),
          );
        },
      );
    },
  );

  static String commandLabel(String type) => switch (type) {
    'hooks' => 'Hook Trailer',
    'drops' => 'Drop Trailer',
    'arrivals' => 'Confirm arrival',
    'service-starts' => 'Start service',
    'completions' => 'Complete non-delivery service',
    'departures' => 'Confirm departure',
    _ => 'Recorded operation',
  };
  static String stateLabel(String state) => switch (state) {
    'QUEUED' => 'Pending synchronization',
    'SENDING' => 'Syncing',
    'ACKNOWLEDGED' => 'Confirmed by server',
    'RETRYABLE_FAILURE' => 'Pending retry — result uncertain',
    'CONFLICT' => 'Requires review',
    'BLOCKED' => 'Blocked by another operation',
    'PERMANENT_FAILURE' => 'Requires review — rejected request',
    _ => 'Requires review',
  };
  static String issueMessage(LocalCommand c) =>
      CommandStore.safeMessage(switch (c.lastErrorCode) {
        'AUTHENTICATION_REQUIRED' ||
        'CAPABILITY_DENIED' ||
        'TENANT_ACCESS_DENIED' ||
        'RESOURCE_NOT_FOUND' => 'AUTHORIZATION',
        'IDEMPOTENCY_KEY_REUSED' => 'IDENTITY',
        'INVALID_DEPENDENCY' => 'INVALID_SEQUENCE',
        'DEPENDENCY_FAILED' => 'DEPENDENCY',
        'VALIDATION_FAILED' => 'VALIDATION',
        _ => 'STATE',
      });
}
