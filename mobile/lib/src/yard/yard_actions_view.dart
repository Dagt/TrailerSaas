import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../shift/camera_capture.dart';
import '../sync/replay_worker.dart';
import '../media/media_sync_panel.dart';
import 'yard_controller.dart';
import 'yard_commands.dart';
import 'yard_damage_store.dart';

class YardActionsPanel extends StatefulWidget {
  const YardActionsPanel({
    super.key,
    required this.controller,
    required this.session,
    required this.worker,
    this.camera,
  });
  final YardModeController controller;
  final SessionController session;
  final ReplayWorker worker;
  final InspectionCamera? camera;
  @override
  State<YardActionsPanel> createState() => _YardActionsPanelState();
}

class _YardActionsPanelState extends State<YardActionsPanel> {
  DialogRoute<void>? _route;
  String? _token, _kind, _message;
  bool _opening = false;
  late final YardDamageStore damage = YardDamageStore(
    widget.session,
    widget.worker,
    widget.camera ?? NativeInspectionCamera(),
  );
  YardModeController get controller => widget.controller;
  bool get current =>
      mounted &&
      _token != null &&
      _token == controller.contextToken &&
      controller.permits(YardCommandIntent.capabilityFor(_kind ?? ''));
  @override
  void initState() {
    super.initState();
    controller.addListener(_fence);
    unawaited(damage.recover());
  }

  void _fence() {
    final route = _route;
    if (route != null && !current) {
      _route = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (route.isActive) {
          route.navigator?.removeRoute(route);
        }
      });
    }
  }

  @override
  void dispose() {
    controller.removeListener(_fence);
    final route = _route;
    _route = null;
    if (route != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (route.isActive) {
          route.navigator?.removeRoute(route);
        }
      });
    }
    super.dispose();
  }

  Future<void> action(String kind) async {
    if (_opening ||
        _route != null ||
        !controller.permits(YardCommandIntent.capabilityFor(kind))) {
      return;
    }
    _opening = true;
    var reviewedConflict = false;
    try {
      if (kind != 'DISCREPANCY_REPORT' &&
          await controller.hasPhysicalConflict()) {
        if (await controller.refreshScannedTrailer() == null) {
          throw StateError(
            'Connect to review the latest recorded Trailer before creating a new action.',
          );
        }
        reviewedConflict = true;
      }
      if (!mounted) {
        return;
      }
      final asset = controller.trailer, site = controller.selected;
      if (asset == null || site == null) {
        return;
      }
      _token = controller.contextToken;
      _kind = kind;
      final reason = TextEditingController();
      String? destination, load;
      final at = controller.now();
      bool saving = false;
      String? error;
      Map<String, dynamic>? accepted;
      final route = DialogRoute<void>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (dialogContext, update) => AlertDialog(
            title: Text(switch (kind) {
              'LOCATION_CORRECTION' => 'Correct Recorded Location',
              'LOAD_STATE_CHANGE' => 'Correct Load State',
              _ => 'Report Discrepancy',
            }),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Trailer ${asset.number} · ${site.name}'),
                  Text(
                    'Recorded: ${asset.location?['name'] ?? asset.data['locationState']}',
                  ),
                  Text(
                    'Version: ${asset.data['version']} · Observed: ${at.toLocal()}',
                  ),
                  if (reviewedConflict)
                    const Text(
                      'Prior actions failed. This explicit review creates a new action; the failed requests and their blocked descendants remain retained.',
                    ),
                  if (kind == 'LOAD_STATE_CHANGE') ...[
                    Text('Current Load State: ${asset.data['loadState']}'),
                    DropdownButton<String>(
                      key: const Key('yard-load-state'),
                      isExpanded: true,
                      value: load,
                      hint: const Text('New Load State'),
                      items: ['EMPTY', 'LOADED', 'UNKNOWN']
                          .where((s) => s != asset.data['loadState'])
                          .map(
                            (s) => DropdownMenuItem(value: s, child: Text(s)),
                          )
                          .toList(),
                      onChanged: saving ? null : (s) => update(() => load = s),
                    ),
                  ] else
                    DropdownButton<String>(
                      key: const Key('yard-observed-location'),
                      isExpanded: true,
                      value: destination,
                      hint: const Text('Where physically found'),
                      items: site.locations
                          .where((l) => l.id != asset.location?['id'])
                          .map(
                            (l) => DropdownMenuItem(
                              value: l.id,
                              child: Text(l.name),
                            ),
                          )
                          .toList(),
                      onChanged: saving
                          ? null
                          : (id) => update(() => destination = id),
                    ),
                  TextField(
                    key: const Key('yard-action-reason'),
                    controller: reason,
                    maxLength: 4000,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      labelText: 'Reason / comment (required)',
                    ),
                    enabled: !saving,
                  ),
                  if (kind == 'DISCREPANCY_REPORT')
                    const Text(
                      'This records a discrepancy. Location, Load State and serviceability will not change.',
                    ),
                  if (error != null) Text(error!),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: saving ? null : () => Navigator.pop(dialogContext),
                child: const Text('Not Yet'),
              ),
              FilledButton(
                key: const Key('yard-action-confirm'),
                onPressed: saving
                    ? null
                    : () {
                        if (!current ||
                            reason.text.trim().isEmpty ||
                            kind == 'LOAD_STATE_CHANGE' && load == null ||
                            kind != 'LOAD_STATE_CHANGE' &&
                                destination == null) {
                          update(
                            () => error =
                                'Choose the observed value and enter a reason.',
                          );
                          return;
                        }
                        update(() => saving = true);
                        accepted = {
                          'siteId': site.id,
                          'reason': reason.text.trim(),
                          if (kind == 'LOAD_STATE_CHANGE') 'loadState': load,
                          if (kind == 'LOCATION_CORRECTION')
                            'newLocationId': destination,
                          if (kind == 'DISCREPANCY_REPORT')
                            'reportedLocationId': destination,
                        };
                        Navigator.pop(dialogContext);
                      },
                child: Text(
                  kind == 'DISCREPANCY_REPORT'
                      ? 'Confirm Report'
                      : 'Confirm Correction',
                ),
              ),
            ],
          ),
        ),
      );
      if (!mounted || !current) {
        return;
      }
      _route = route;
      await Navigator.of(context).push(route);
      // popped completes before the exit animation; the TextField still owns its controller.
      await route.completed;
      if (identical(_route, route)) {
        _route = null;
      }
      reason.dispose();
      if (accepted != null && current) {
        await controller.confirmAction(
          kind,
          accepted!,
          at,
          reviewedConflict: reviewedConflict,
        );
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _message = 'Connect, refresh and review the latest recorded Trailer before retrying. Original operations remain retained.',
        );
      }
    } finally {
      _opening = false;
    }
  }

  Future<void> oos({bool fresh = false}) async {
    if (_opening ||
        _route != null ||
        !controller.permits('TRAILER_OOS_MARK') ||
        widget.worker.media == null) {
      return;
    }
    _opening = true;
    try {
      var review = false;
      if (await controller.hasPhysicalConflict()) {
        if (await controller.refreshScannedTrailer() == null) {
          throw StateError('Fresh authorized review required');
        }
        review = true;
      }
      if (!mounted) {
        return;
      }
      final asset = controller.trailer, site = controller.selected;
      if (asset == null || site == null) {
        return;
      }
      _token = controller.contextToken;
      _kind = 'OOS';
      var draft = await damage.begin(
        asset,
        site.id,
        () => current,
        fresh: fresh,
        reviewedConflict: review,
      );
      if (!mounted || !current) {
        return;
      }
      final comment = TextEditingController(text: draft.comment);
      bool working = false;
      String? error;
      final route = DialogRoute<void>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (dialogContext, update) => AlertDialog(
            title: const Text('Report Damage / Mark OOS'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Trailer ${asset.number} · ${site.name}'),
                  Text(
                    'Recorded: ${asset.location?['name'] ?? asset.data['locationState']}',
                  ),
                  Text(
                    'Version: ${asset.data['version']} · Observed: ${(jsonDecode(draft.requestJson) as Map)['observedAt']}',
                  ),
                  const Text(
                    'Photos remain in private storage. Upload/finalization alone does not mark the Trailer Out of Service.',
                  ),
                  TextField(
                    key: const Key('yard-oos-comment'),
                    controller: comment,
                    maxLength: 4000,
                    maxLines: 3,
                    enabled: !working,
                    decoration: const InputDecoration(
                      labelText: 'Damage comment (required)',
                    ),
                    onChanged: (text) {
                      unawaited(damage.comment(draft, text).catchError((_) {}));
                    },
                  ),
                  Text(
                    'Retained damage photographs: ${(jsonDecode(draft.photosJson) as List).length}',
                  ),
                  if (widget.worker.media != null)
                    StreamBuilder<List<LocalMediaData>>(
                      stream: widget.worker.media!.store.watch(
                        draft.userId,
                        draft.tenantId,
                      ),
                      builder: (context, snapshot) {
                        final selected = List<String>.from(
                          jsonDecode(draft.photosJson) as List,
                        );
                        final photos = (snapshot.data ?? [])
                            .where(
                              (p) =>
                                  p.trailerId == draft.trailerId &&
                                  p.documentType == 'DAMAGE_PHOTO' &&
                                  p.fileState == 'PRESENT',
                            )
                            .toList();
                        return Column(
                          children: photos
                              .map(
                                (p) => ListTile(
                                  dense: true,
                                  title: Text('Damage photo ${p.id}'),
                                  subtitle: Text(
                                    '${p.state}${p.lastErrorCode == null ? '' : ' · ${p.lastErrorCode}'}',
                                  ),
                                  trailing: selected.contains(p.id)
                                      ? const Icon(Icons.check)
                                      : TextButton(
                                          onPressed: working
                                              ? null
                                              : () async {
                                                  await damage.retainPhoto(
                                                    draft,
                                                    p.id,
                                                  );
                                                  final next = await damage
                                                      .find(
                                                        draft.userId,
                                                        draft.tenantId,
                                                        draft.id,
                                                      );
                                                  if (current && next != null) {
                                                    update(() => draft = next);
                                                  }
                                                },
                                          child: const Text(
                                            'Use retained photo',
                                          ),
                                        ),
                                ),
                              )
                              .toList(),
                        );
                      },
                    ),
                  FilledButton.icon(
                    key: const Key('yard-damage-photo'),
                    onPressed: working
                        ? null
                        : () async {
                            update(() => working = true);
                            try {
                              await damage.take(draft, () => current);
                              final next = await damage.find(
                                draft.userId,
                                draft.tenantId,
                                draft.id,
                              );
                              if (current && next != null) {
                                update(() => draft = next);
                              }
                            } catch (_) {
                              if (current) {
                                update(
                                  () => error = 'Photo capture failed or awaits recovery. The draft and existing photos are retained.',
                                );
                              }
                            } finally {
                              if (current) {
                                update(() => working = false);
                              }
                            }
                          },
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Capture Damage Photo'),
                  ),
                  if (error != null) Text(error!),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: working ? null : () => Navigator.pop(dialogContext),
                child: const Text('Not Yet'),
              ),
              FilledButton(
                key: const Key('yard-oos-confirm'),
                onPressed: working
                    ? null
                    : () async {
                        if (!current ||
                            comment.text.trim().isEmpty ||
                            (jsonDecode(draft.photosJson) as List).isEmpty) {
                          update(
                            () => error = 'Capture at least one photograph and enter a comment.',
                          );
                          return;
                        }
                        update(() => working = true);
                        try {
                          await damage.comment(draft, comment.text.trim());
                          final operation = await damage.queue(
                            draft,
                            asset,
                            () => current,
                          );
                          if (operation == null) {
                            throw StateError('Context changed');
                          }
                          if (current && dialogContext.mounted) {
                            Navigator.pop(dialogContext);
                            setState(
                              () => _message = 'Damage/OOS saved on this device — waiting for finalized evidence and server confirmation.',
                            );
                            unawaited(widget.worker.sync());
                          }
                        } catch (_) {
                          if (current) {
                            update(
                              () => error = 'Trailer or draft changed. Start a new reviewed action; photos remain retained.',
                            );
                          }
                        } finally {
                          if (current) {
                            update(() => working = false);
                          }
                        }
                      },
                child: const Text('Confirm Mark OOS'),
              ),
            ],
          ),
        ),
      );
      if (!mounted || !current) {
        return;
      }
      _route = route;
      await Navigator.of(context).push(route);
      await route.completed;
      if (identical(_route, route)) {
        _route = null;
      }
      comment.dispose();
    } catch (_) {
      if (mounted) {
        setState(
          () => _message = 'Damage workflow unavailable. Refresh and review current permissions/state; retained evidence was not deleted.',
        );
      }
    } finally {
      _opening = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final asset = controller.trailer;
    if (asset == null) {
      return const SizedBox.shrink();
    }
    final capabilities =
        widget.session.snapshot?.membership.capabilities ?? <String>{};
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Divider(),
        const Text('Explicit Yard actions'),
        if (_message != null)
          Semantics(liveRegion: true, child: Text(_message!)),
        OutlinedButton(
          key: const Key('yard-correct-location'),
          onPressed: controller.permits('TRAILER_LOCATION_CORRECT')
              ? () => action('LOCATION_CORRECTION')
              : null,
          child: const Text('Correct Recorded Location'),
        ),
        if (!capabilities.contains('TRAILER_LOCATION_CORRECT'))
          const Text(
            'Location correction is not authorized. Report the discrepancy instead.',
          ),
        OutlinedButton(
          key: const Key('yard-report-discrepancy'),
          onPressed: controller.usable
              ? () => action('DISCREPANCY_REPORT')
              : null,
          child: const Text('Report Discrepancy'),
        ),
        OutlinedButton(
          key: const Key('yard-correct-load'),
          onPressed: controller.permits('TRAILER_STATE_CORRECT')
              ? () => action('LOAD_STATE_CHANGE')
              : null,
          child: const Text('Correct Load State'),
        ),
        if (!capabilities.contains('TRAILER_STATE_CORRECT'))
          const Text('Load State correction is not authorized.'),
        OutlinedButton(
          key: const Key('yard-report-damage'),
          onPressed:
              controller.permits('TRAILER_OOS_MARK') &&
                  asset.data['serviceabilityState'] == 'IN_SERVICE' &&
                  widget.worker.media != null
              ? () => oos()
              : null,
          child: const Text('Report Damage / Mark OOS'),
        ),
        if (!capabilities.contains('TRAILER_OOS_MARK'))
          const Text(
            'Yard damage photographs and Mark OOS require TRAILER_OOS_MARK. Reporting a discrepancy does not change serviceability.',
          ),
        if (controller.permits('TRAILER_OOS_MARK') &&
            widget.worker.media != null)
          TextButton(
            key: const Key('yard-new-damage-action'),
            onPressed: () => oos(fresh: true),
            child: const Text(
              'Start new reviewed damage action — retain earlier drafts/photos',
            ),
          ),
        if (widget.worker.media != null) MediaSyncPanel(worker: widget.worker),
      ],
    );
  }
}
