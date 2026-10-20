import 'dart:convert';

import 'package:flutter/material.dart';

import '../ui/fleet_widgets.dart';

import 'package:drift/drift.dart' hide Column;

import '../persistence/app_database.dart';
import '../platform/native_qr_scanner.dart';
import '../platform/qr_scanner.dart';
import '../session/session_controller.dart';
import '../sync/replay_worker.dart';
import '../sync/sync_panel.dart';
import 'yard_commands.dart';
import 'check_view.dart';
import 'yard_controller.dart';
import 'yard_contracts.dart';
import 'yard_actions_view.dart';
import '../shift/camera_capture.dart';

class YardHomePanel extends StatefulWidget {
  const YardHomePanel({
    super.key,
    required this.session,
    required this.worker,
    this.scanner,
    this.camera,
  });
  final SessionController session;
  final ReplayWorker worker;
  final QrScanner? scanner;
  final InspectionCamera? camera;
  @override
  State<YardHomePanel> createState() => _YardHomePanelState();
}

class _YardHomePanelState extends State<YardHomePanel> {
  late final YardModeController controller;
  String? _destination;
  DialogRoute<bool>? _reviewRoute;
  NavigatorState? _reviewNavigator;
  Future<bool?> review(String summary, String? owner, String assetId) async {
    final navigator = Navigator.of(context);
    final route = DialogRoute<bool>(
      context: context,
      builder: (dialogContext) => ListenableBuilder(
        listenable: widget.session,
        builder: (context, _) {
          if (!mounted ||
              controller.key(widget.session.snapshot) != owner ||
              controller.trailer?.id != assetId) {
            return AlertDialog(
              scrollable: true,
              title: const Text('Yard context changed'),
              actions: [
                TextButton(
                  onPressed: () => navigator.pop(false),
                  child: const Text('Close'),
                ),
              ],
            );
          }
          return AlertDialog(
            scrollable: true,
            title: const Text('Confirm Yard Move'),
            content: Text(summary),
            actions: [
              TextButton(
                onPressed: () => navigator.pop(false),
                child: const Text('Not Yet'),
              ),
              FilledButton(
                key: const Key('yard-confirm'),
                onPressed: () => navigator.pop(true),
                child: const Text('Confirm Move'),
              ),
            ],
          );
        },
      ),
    );
    _reviewRoute = route;
    _reviewNavigator = navigator;
    try {
      return await navigator.push<bool>(route);
    } finally {
      if (identical(_reviewRoute, route)) {
        _reviewRoute = null;
        _reviewNavigator = null;
      }
    }
  }

  @override
  void initState() {
    super.initState();
    controller = YardModeController(
      widget.session,
      widget.worker,
      widget.scanner ?? NativeQrScanner(context),
    );
    controller.start();
  }

  @override
  void dispose() {
    final route = _reviewRoute, navigator = _reviewNavigator;
    _reviewRoute = null;
    _reviewNavigator = null;
    if (route != null && navigator != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (navigator.mounted && route.isActive) navigator.removeRoute(route);
      });
    }
    controller.dispose();
    super.dispose();
  }

  String locationName(String id) =>
      controller.selected?.locations
          .where((l) => l.id == id)
          .firstOrNull
          ?.name ??
      id;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final owner = widget.session.snapshot;
      if (owner == null) return const SizedBox.shrink();
      final asset = controller.trailer;
      return FleetFormBody(
        children: [
          const FleetHeader(
            'Yard Mode',
            eyebrow: 'YARD WORK',
            description: 'Identify a Trailer, review its recorded state and confirm the physical move.',
          ),
          TextButton.icon(
            key: const Key('yard-refresh'),
            onPressed: controller.loading || controller.busy
                ? null
                : () => controller.load(manual: true),
            icon: const Icon(Icons.refresh),
            label: const Text('Refresh Yard'),
          ),
          if (controller.loading) const LinearProgressIndicator(),
          if (controller.refreshedAt != null)
            Text(
              'Yard context refreshed: ${fleetTime(controller.refreshedAt!)}',
            ),
          if (controller.message != null)
            Semantics(liveRegion: true, child: Text(controller.message!)),
          if (controller.sites.isNotEmpty)
            InputDecorator(
              decoration: const InputDecoration(labelText: 'Site / Yard'),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  itemHeight: null,
                  key: const Key('yard-site'),
                  isExpanded: true,
                  value: controller.selectedSiteId,
                  hint: const Text('Select an authorized Site'),
                  items: controller.sites
                      .map(
                        (s) =>
                            DropdownMenuItem(value: s.id, child: Text(s.name)),
                      )
                      .toList(),
                  onChanged:
                      controller.loading ||
                          controller.busy ||
                          controller.blocked
                      ? null
                      : (id) {
                          if (id != null) {
                            _destination = null;
                            controller.selectSite(id);
                          }
                        },
                ),
              ),
            ),
          FilledButton.icon(
            key: const Key('yard-scan'),
            onPressed: controller.usable
                ? () {
                    _destination = null;
                    controller.scan();
                  }
                : null,
            icon: const Icon(Icons.qr_code_scanner),
            label: const Text('Move Trailer — Scan QR'),
          ),
          if (asset != null)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    FleetHeader(
                      'Trailer ${asset.number}',
                      eyebrow: 'RECORDED TRAILER',
                    ),
                    Text(
                      'Customer: ${(asset.data['designatedCustomer'] as Map?)?['name'] ?? 'Not designated'}',
                    ),
                    Text(
                      'Recorded location: ${asset.location?['name'] ?? asset.data['locationState']}',
                    ),
                    if (asset.location != null)
                      Text('Recorded Site: ${asset.location!['siteName']}'),
                    Text(
                      'Load: ${asset.data['loadState']} · Serviceability: ${asset.data['serviceabilityState']}',
                    ),
                    Text(
                      'Coupling: ${asset.data['couplingState']} · Version: ${asset.data['version']}',
                    ),
                    const FleetNotice(
                      'Recorded server projection; queued moves do not change this location.',
                      tone: FleetTone.pending,
                    ),
                    FutureBuilder<String?>(
                      future: controller.plannedOrigin(),
                      builder: (context, origin) {
                        final from = origin.data;
                        if (from == null) {
                          return Text(
                            asset.movable
                                ? 'This Trailer has an unresolved move. Refresh and review its original operation.'
                                : 'This Trailer cannot be moved in its recorded state.',
                          );
                        }
                        final destinations =
                            controller.selected?.locations
                                .where((l) => l.id != from)
                                .toList() ??
                            [];
                        final selectedDestination =
                            destinations.any((l) => l.id == _destination)
                            ? _destination
                            : null;
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Move origin: ${locationName(from)}${from != asset.location?['id'] ? ' (planned after pending move; not yet server confirmed)' : ''}',
                            ),
                            InputDecorator(
                              decoration: const InputDecoration(
                                labelText: 'Destination',
                              ),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  itemHeight: null,
                                  key: const Key('yard-destination'),
                                  isExpanded: true,
                                  value: selectedDestination,
                                  hint: const Text('Select destination'),
                                  items: destinations
                                      .map(
                                        (l) => DropdownMenuItem(
                                          value: l.id,
                                          child: Text(l.name),
                                        ),
                                      )
                                      .toList(),
                                  onChanged: controller.canMove
                                      ? (id) =>
                                            setState(() => _destination = id)
                                      : null,
                                ),
                              ),
                            ),
                            FilledButton(
                              key: const Key('yard-confirm-review'),
                              onPressed:
                                  controller.canMove &&
                                      selectedDestination != null
                                  ? () async {
                                      final generationOwner = controller.key(
                                            widget.session.snapshot,
                                          ),
                                          reviewedAsset = asset.id,
                                          at = controller.now();
                                      final accepted = await review(
                                        'Trailer ${asset.number}\nFrom: ${locationName(from)}\nTo: ${locationName(selectedDestination)}\nObserved: ${fleetTime(at)}\nConfirm only if the physical origin matches this reviewed origin. A discrepancy must be reviewed without correcting it here.\nSaved locally first; Backend must confirm the move.',
                                        generationOwner,
                                        reviewedAsset,
                                      );
                                      if (!mounted ||
                                          accepted != true ||
                                          generationOwner !=
                                              controller.key(
                                                widget.session.snapshot,
                                              ) ||
                                          controller.trailer?.id !=
                                              reviewedAsset) {
                                        return;
                                      }
                                      await controller.confirmMove(
                                        selectedDestination,
                                        from,
                                        observedAt: at,
                                      );
                                      if (mounted) {
                                        setState(() => _destination = null);
                                      }
                                    }
                                  : null,
                              child: const Text('Review Move'),
                            ),
                          ],
                        );
                      },
                    ),
                    YardActionsPanel(
                      controller: controller,
                      session: widget.session,
                      worker: widget.worker,
                      camera: widget.camera,
                    ),
                  ],
                ),
              ),
            ),
          YardChecksPanel(
            session: widget.session,
            worker: widget.worker,
            site: controller.selected,
            scanner: widget.scanner,
          ),
          SyncPanel(worker: widget.worker),
          StreamBuilder<List<LocalCommand>>(
            stream: widget.worker.store.watch(
              owner.userId,
              owner.membership.tenantId,
            ),
            builder: (context, snapshot) {
              final moves = (snapshot.data ?? [])
                  .where(YardCommandIntent.recognizes)
                  .toList()
                  .reversed
                  .take(20);
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const FleetSectionTitle(
                    'Recent Yard moves / pending operations',
                    icon: Icons.history,
                  ),
                  ...moves.map((c) {
                    final body = jsonDecode(c.payloadJson) as Map;
                    final confirmed = c.state == 'ACKNOWLEDGED';
                    final label = switch (c.commandType) {
                      'LOCATION_CORRECTION' => 'Location Correction',
                      'LOAD_STATE_CHANGE' => 'Load State Change',
                      'OOS' => 'Out of Service',
                      'DISCREPANCY_REPORT' => 'Discrepancy Report',
                      _ => 'Yard Move',
                    };
                    final detail = switch (c.commandType) {
                      'LOCATION_CORRECTION' =>
                        'Recorded location correction to ${locationName(body['newLocationId'] as String)}\nReason: ${body['reason']}',
                      'LOAD_STATE_CHANGE' =>
                        'Load State: ${body['loadState']}\nReason: ${body['reason']}',
                      'OOS' =>
                        'Damage photos: ${(body['documentIds'] as List).length}\nComment: ${body['comment']}',
                      'DISCREPANCY_REPORT' =>
                        'Reported physical location: ${locationName(body['reportedLocationId'] as String)}\nReason: ${body['reason']}',
                      _ =>
                        '${locationName(body['fromLocationId'] as String)} → ${locationName(body['toLocationId'] as String)}',
                    };
                    return FleetRecord(
                      key: ValueKey(c.clientOperationId),

                      title: Text(
                        confirmed
                            ? 'Server confirmed $label'
                            : 'Local $label · ${c.state}',
                      ),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '$detail\nObserved: ${fleetTime(c.occurredAt)}${c.lastErrorCode == null ? '' : '\n${c.lastErrorCode}: Original command retained. Refresh and review; no automatic correction applied.'}',
                          ),
                          if (c.state == 'CONFLICT')
                            _YardConflictLocation(
                              worker: widget.worker,
                              command: c,
                            ),
                        ],
                      ),
                    );
                  }),
                ],
              );
            },
          ),
        ],
      );
    },
  );
}

class _YardConflictLocation extends StatelessWidget {
  const _YardConflictLocation({required this.worker, required this.command});
  final ReplayWorker worker;
  final LocalCommand command;
  @override
  Widget build(BuildContext context) => StreamBuilder<List<CachedTrailer>>(
    stream:
        (worker.store.db.select(worker.store.db.cachedTrailers)..where(
              (r) =>
                  r.userId.equals(command.userId) &
                  r.tenantId.equals(command.tenantId) &
                  r.id.equals(command.aggregateId!),
            ))
            .watch(),
    builder: (context, snapshot) {
      try {
        final row = snapshot.data?.singleOrNull;
        if (row != null) {
          final raw = jsonDecode(row.projectionJson) as Map<String, dynamic>;
          if (raw['yardProjection'] == true) {
            final trailer = YardTrailer.fromJson(
              {...raw}
                ..remove('yardProjection')
                ..remove('qrPublicToken'),
            );
            return Text(
              'Last downloaded recorded location: ${trailer.location?['name'] ?? trailer.data['locationState']} · Version ${trailer.data['version']}\nRefreshed: ${fleetTime(row.refreshedAt)}',
            );
          }
        }
      } catch (_) {
        /* A malformed cache cannot supply a physical assertion. */
      }
      return const Text(
        'Refresh and scan to view the current authorized recorded location.',
      );
    },
  );
}
