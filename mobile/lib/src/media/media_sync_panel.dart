import 'package:drift/drift.dart' hide Column;
import 'package:flutter/material.dart';

import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../sync/replay_worker.dart';

class MediaSyncPanel extends StatefulWidget {
  const MediaSyncPanel({super.key, required this.worker});
  final ReplayWorker worker;
  @override
  State<MediaSyncPanel> createState() => _MediaSyncPanelState();
  static String label(String state) => switch (state) {
    'LOCAL_ONLY' => 'Saved on this device',
    'INIT_PENDING' || 'UPLOAD_PENDING' => 'Pending upload',
    'UPLOADING' => 'Uploading — not confirmed',
    'UPLOADED' || 'FINALIZE_PENDING' => 'Waiting for server finalization',
    'FINALIZED' => 'Finalized by server',
    _ => 'Requires attention',
  };
  static String typeLabel(String type) => switch (type) {
    'BOL' => 'Bill of lading',
    'POD' => 'Proof of delivery document',
    'DELIVERY_PHOTO' => 'Delivery photo',
    'PRETRIP_FRONT' => 'Pre-Trip: front',
    'PRETRIP_REAR' => 'Pre-Trip: rear',
    'PRETRIP_DRIVER_SIDE' => 'Pre-Trip: Driver side',
    'PRETRIP_PASSENGER_SIDE' => 'Pre-Trip: passenger side',
    'FUEL_RECEIPT' => 'Fuel receipt',
    _ => 'Damage photo',
  };
}

class _MediaSyncPanelState extends State<MediaSyncPanel> {
  String? scope, error;
  Stream<List<LocalMediaData>>? rows;
  Stream<List<CommandMediaDependency>>? dependencies;
  Future<void> review(LocalMediaData row) async {
    final w = widget.worker, owner = w.session.snapshot;
    if (owner == null ||
        owner.userId != row.userId ||
        owner.membership.tenantId != row.tenantId) {
      return;
    }
    try {
      await w.session.refresh();
      if (w.session.state != SessionState.ready ||
          w.scope !=
              '${owner.firebaseUid}|${owner.userId}|${owner.membership.tenantId}') {
        return;
      }
      await w.media!.store.retryReviewed(row.userId, row.tenantId, row.id);
      await w.sync();
    } catch (_) {
      if (mounted &&
          w.scope ==
              '${owner.firebaseUid}|${owner.userId}|${owner.membership.tenantId}') {
        setState(
          () => error = 'The saved file needs review. Its reference was retained; restore access or capture a new file in the owning workflow.',
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final w = widget.worker, owner = w.session.snapshot, store = w.media?.store;
    if (owner == null ||
        store == null ||
        w.session.auth.uid != owner.firebaseUid ||
        !{SessionState.ready, SessionState.offline}.contains(w.session.state)) {
      return const SizedBox.shrink();
    }
    final next = '${owner.userId}|${owner.membership.tenantId}';
    if (scope != next) {
      scope = next;
      error = null;
      rows = store.watch(owner.userId, owner.membership.tenantId);
      dependencies =
          (store.db.select(store.db.commandMediaDependencies)..where(
                (r) =>
                    r.userId.equals(owner.userId) &
                    r.tenantId.equals(owner.membership.tenantId),
              ))
              .watch();
    }
    return StreamBuilder<List<LocalMediaData>>(
      key: ValueKey(next),
      stream: rows,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const Text(
            'Saved media could not be read. Files and records are retained. Retry sync.',
          );
        }
        final files = snapshot.data ?? [];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Media synchronization',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const Text(
              'Local files are not accepted business evidence until the server validates the owning workflow.',
            ),
            if (error != null) Semantics(liveRegion: true, child: Text(error!)),
            if (files.isEmpty)
              const Text('No saved media in this account and Tenant.'),
            Wrap(
              spacing: 8,
              children: [
                for (final state in files.map((r) => r.state).toSet())
                  Chip(
                    label: Text(
                      '${MediaSyncPanel.label(state)}: ${files.where((r) => r.state == state).length}',
                    ),
                  ),
              ],
            ),
            StreamBuilder<List<CommandMediaDependency>>(
              stream: dependencies,
              builder: (context, edges) => Column(
                children: [
                  for (final row in files.reversed.take(50))
                    ListTile(
                      title: Text(MediaSyncPanel.typeLabel(row.documentType)),
                      subtitle: Text(
                        '${MediaSyncPanel.label(row.state)}\n${row.sizeBytes} bytes'
                        '${row.lastErrorCode == null ? '' : '\n${_error(row.lastErrorCode!)}'}'
                        '${row.nextRetryAt == null ? '' : '\nNext retry: ${row.nextRetryAt!.toLocal()}'}'
                        '${row.finalizedAt == null ? '' : '\nServer finalized: ${row.finalizedAt!.toLocal()}'}'
                        '\nReferenced by ${(edges.data ?? []).where((d) => d.mediaId == row.id).length} recorded operation(s).',
                      ),
                      trailing:
                          row.reviewRequired &&
                              row.lastErrorCode != 'IDEMPOTENCY_KEY_REUSED'
                          ? TextButton(
                              onPressed:
                                  w.session.state == SessionState.ready &&
                                      !w.sending
                                  ? () => review(row)
                                  : null,
                              child: const Text('Review media'),
                            )
                          : null,
                    ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  static String _error(String code) => switch (code) {
    'FILE_CHANGED' || 'FILE_MISSING' || 'FILE_UNAVAILABLE' => 'The saved file is missing or changed. Its original reference is retained.',
    'IDEMPOTENCY_KEY_REUSED' => 'Operation identity needs support review. No replacement key was created.',
    'UPLOAD_CONTENT_MISMATCH' =>
      'Uploaded bytes could not be accepted. Review the original file.',
    'CAPABILITY_DENIED' || 'RESOURCE_NOT_FOUND' || 'AUTHENTICATION_REQUIRED' => 'Current access requires review. Historical capture does not grant permission.',
    _ =>
      'Upload was not confirmed. The file and original attempt are retained.',
  };
}
