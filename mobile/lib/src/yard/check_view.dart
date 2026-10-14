import 'dart:convert';

import 'package:flutter/material.dart';

import '../platform/native_qr_scanner.dart';
import '../platform/qr_scanner.dart';
import '../session/session_controller.dart';
import '../sync/replay_worker.dart';
import 'check_controller.dart';
import 'check_contracts.dart';
import 'yard_contracts.dart';

class YardChecksPanel extends StatefulWidget {
  const YardChecksPanel({
    super.key,
    required this.session,
    required this.worker,
    this.site,
    this.scanner,
  });
  final SessionController session;
  final ReplayWorker worker;
  final YardSite? site;
  final QrScanner? scanner;
  @override
  State<YardChecksPanel> createState() => _YardChecksPanelState();
}

class _YardChecksPanelState extends State<YardChecksPanel>
    with WidgetsBindingObserver {
  late final YardCheckController controller;
  String? startLocation, observedLocation;
  bool reviewing = false;
  DialogRoute<bool>? reviewRoute;
  NavigatorState? reviewNavigator;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    controller = YardCheckController(
      widget.session,
      widget.worker,
      widget.scanner ?? NativeQrScanner(context),
    );
    controller.start();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      controller.resume();
    } else {
      controller.suspend();
    }
  }

  @override
  void didUpdateWidget(YardChecksPanel old) {
    super.didUpdateWidget(old);
    if (old.site?.id != widget.site?.id) {
      controller.invalidateScope();
      startLocation = null;
      observedLocation = null;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller.dispose();
    final route = reviewRoute;
    if (route != null && route.isActive) reviewNavigator?.removeRoute(route);
    super.dispose();
  }

  Future<String?> confirm(
    String title,
    String detail, {
    bool reason = false,
  }) async {
    final version = controller.selected?.serverVersion;
    final owner = controller.key(widget.session.snapshot),
        id = controller.selected?.id,
        g = controller.generation;
    final text = TextEditingController();
    final navigator = Navigator.of(context);
    final route = DialogRoute<bool>(
      context: context,
      builder: (context) => ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final valid =
              mounted &&
              owner == controller.key(widget.session.snapshot) &&
              id == controller.selected?.id &&
              g == controller.generation &&
              version == controller.selected?.serverVersion &&
              controller.foreground;
          return AlertDialog(
            title: Text(valid ? title : 'Check context changed'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  valid ? detail : 'Close this review and refresh the current authorized Check.',
                ),
                if (reason && valid)
                  TextField(
                    controller: text,
                    maxLength: 4000,
                    decoration: const InputDecoration(
                      labelText: 'Required reason',
                    ),
                  ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => navigator.pop(false),
                child: const Text('Not Yet'),
              ),
              if (valid)
                FilledButton(
                  onPressed: () => navigator.pop(true),
                  child: const Text('Confirm'),
                ),
            ],
          );
        },
      ),
    );
    reviewRoute = route;
    reviewNavigator = navigator;
    try {
      final yes = await navigator.push<bool>(route);
      if (yes != true ||
          !mounted ||
          owner != controller.key(widget.session.snapshot) ||
          id != controller.selected?.id ||
          g != controller.generation ||
          version != controller.selected?.serverVersion ||
          !controller.foreground) {
        return null;
      }
      if (reason && text.text.trim().isEmpty) {
        controller.message = 'A reason is required.';
        controller.notify();
        return null;
      }
      return reason ? text.text.trim() : '';
    } finally {
      reviewRoute = null;
      reviewNavigator = null;
      text.dispose();
    }
  }

  Future<void> terminal(bool cancel) async {
    if (reviewing) return;
    setState(() => reviewing = true);
    try {
      final state = await controller.reviewState();
      if (state == null || !mounted) return;
      final s = state.check;
      final reason = await confirm(
        cancel ? 'Cancel Yard Check' : 'Complete Yard Check',
        'Server review: ${s.count('expectedCount')} expected, ${s.count('matchedCount')} matched, ${s.count('unexpectedCount')} unexpected, ${s.count('mismatchCount')} mismatches and ${s.count('remainingCount')} remaining. ${cancel ? 'Partial observations will remain in history.' : 'Remaining inventory becomes an authoritative MISSING finding only after confirmation. No Trailer is moved or marked unknown.'}',
        reason: cancel,
      );
      if (reason != null) {
        await controller.terminal(state, reason: cancel ? reason : null);
      }
    } catch (_) {
      if (mounted) {
        controller.message = 'Connect, synchronize and review all Check observations before completing or cancelling.';
        controller.notify();
      }
    } finally {
      if (mounted) setState(() => reviewing = false);
    }
  }

  Future<void> reconcile(CheckItem item) async {
    if (reviewing) return;
    setState(() => reviewing = true);
    try {
      final trailer = await controller.currentTrailer(item.trailerId);
      if (!mounted) return;
      final missing = item.json['result'] == 'MISSING';
      final reason = await confirm(
        missing ? 'Mark Location Unknown' : 'Correct Recorded Location',
        'Trailer ${trailer.number}. Current authorized location: ${trailer.location?['name'] ?? trailer.data['locationState']}; version ${trailer.data['version']}. ${missing ? 'This is a separately authorized physical reconciliation and will create a persistent server alert.' : 'Correction targets the original observed location ${item.json['observedLocationId']}.'}',
        reason: true,
      );
      if (reason != null) await controller.reconcile(item, trailer, reason);
    } catch (_) {
      if (mounted) {
        controller.message = 'The current Trailer or correction permission could not be verified. No location was changed.';
        controller.notify();
      }
    } finally {
      if (mounted) setState(() => reviewing = false);
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      if (widget.session.snapshot?.membership.capabilities.contains(
            'YARD_CHECK_EXECUTE',
          ) !=
          true) {
        return const SizedBox.shrink();
      }
      final c = controller, s = c.selected;
      final locations = (widget.site?.locations ?? <YardLocation>[])
          .where((l) => {'YARD', 'DOCK'}.contains(l.type))
          .toList();
      final scopeLocations = locations
          .where((l) => s?.locationId == null || l.id == s!.locationId)
          .toList();
      final matchingSite =
          widget.site?.id == s?.siteId &&
          widget.site?.nextLocationCursor == null;
      final pending = c.commands
          .where(
            (r) =>
                r.aggregateId == s?.id &&
                r.state != 'ACKNOWLEDGED' &&
                !c.reviewed.contains(r.clientOperationId),
          )
          .toList();
      final unresolved = c.observations
          .where(
            (o) =>
                o.resolvedTrailerId == null &&
                o.identifiedTrailerId == null &&
                !c.reviewed.contains(o.operationId),
          )
          .length;
      final confirmed = s == null
          ? null
          : CheckSummary(checkMap(jsonDecode(s.summaryJson)));
      final unique = {
        for (final o in c.observations)
          if (o.resolvedTrailerId ?? o.identifiedTrailerId case final String id)
            id,
      };
      final capturedExpected = c.items
          .where((i) => i.expected && unique.contains(i.trailerId))
          .length;
      final locallyRemaining = c.items
          .where(
            (i) =>
                i.expected &&
                !CheckItem(checkMap(jsonDecode(i.serverJson))).found &&
                !unique.contains(i.trailerId),
          )
          .length;
      final conflicts = pending
          .where(
            (r) =>
                {'CONFLICT', 'BLOCKED', 'PERMANENT_FAILURE'}.contains(r.state),
          )
          .length;
      final syncing = pending.length - conflicts;
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Yard Check inventory',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const Text(
                'Scanning records inventory evidence. It never moves or corrects a Trailer.',
              ),
              if (c.busy) const LinearProgressIndicator(),
              if (c.message != null)
                Semantics(liveRegion: true, child: Text(c.message!)),
              if (widget.site != null)
                DropdownButton<String>(
                  key: const Key('check-start-location'),
                  isExpanded: true,
                  value: startLocation,
                  hint: const Text('Whole Site (optional Yard / Dock scope)'),
                  items: [
                    const DropdownMenuItem<String>(
                      value: null,
                      child: Text('Whole Site'),
                    ),
                    for (final l in locations)
                      DropdownMenuItem(value: l.id, child: Text(l.name)),
                  ],
                  onChanged: c.busy
                      ? null
                      : (id) => setState(() => startLocation = id),
                ),
              FilledButton.icon(
                key: const Key('check-start'),
                onPressed:
                    c.busy ||
                        widget.site == null ||
                        widget.site?.nextLocationCursor != null
                    ? null
                    : () => c.begin(widget.site!, startLocation),
                icon: const Icon(Icons.playlist_add),
                label: const Text('Start Yard Check — online'),
              ),
              TextButton.icon(
                key: const Key('check-refresh'),
                onPressed: c.busy ? null : c.refresh,
                icon: const Icon(Icons.refresh),
                label: const Text('Refresh / recover Checks'),
              ),
              for (final check in c.checks)
                ListTile(
                  key: ValueKey('check-${check.id}'),
                  title: Text('Check ${check.id}'),
                  subtitle: Text(
                    '${check.status} · ${check.snapshotDownloaded ? 'Snapshot complete — available offline' : 'Download incomplete — offline scanning unavailable'}${check.accessBlocked ? ' · Access must be restored' : ''}',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: c.busy ? null : () => c.select(check.id),
                ),
              if (s != null) ...[
                Text(
                  'Active review: ${s.id} · server ${s.status} · version ${s.serverVersion}',
                ),
                Text(
                  'Expected ${s.expectedCount} · scanned by server ${confirmed!.count('scannedCount')} · remaining ${confirmed.count('remainingCount')}',
                ),
                Text(
                  'Matched ${confirmed.count('matchedCount')} · unexpected ${confirmed.count('unexpectedCount')} · location mismatches ${confirmed.count('mismatchCount')} · missing ${confirmed.count('missingCount')}',
                ),
                Text(
                  'Local unobserved expected $locallyRemaining · locally observed expected $capturedExpected · pending synchronization $syncing · unresolved QR $unresolved · conflicts requiring review $conflicts',
                ),
                if (!matchingSite)
                  const Text(
                    'Select this Check’s authorized Site in Yard Home to choose an observed location.',
                  ),
                if (matchingSite)
                  DropdownButton<String>(
                    key: const Key('check-observed-location'),
                    isExpanded: true,
                    value: scopeLocations.any((l) => l.id == observedLocation)
                        ? observedLocation
                        : null,
                    hint: const Text('Actual observed Yard / Dock location'),
                    items: [
                      for (final l in scopeLocations)
                        DropdownMenuItem(value: l.id, child: Text(l.name)),
                    ],
                    onChanged: c.busy
                        ? null
                        : (id) => setState(() => observedLocation = id),
                  ),
                SizedBox(
                  height: 56,
                  child: FilledButton.icon(
                    key: const Key('check-scan'),
                    onPressed:
                        c.canScan &&
                            matchingSite &&
                            scopeLocations.any((l) => l.id == observedLocation)
                        ? () => c.scan(observedLocation!)
                        : null,
                    icon: const Icon(Icons.qr_code_scanner),
                    label: const Text('Scan Trailer — continue inventory'),
                  ),
                ),
                if (s.status == 'IN_PROGRESS') ...[
                  OutlinedButton(
                    key: const Key('check-complete'),
                    onPressed: c.busy ? null : () => terminal(false),
                    child: const Text('Review & Complete — online'),
                  ),
                  TextButton(
                    key: const Key('check-cancel'),
                    onPressed: c.busy ? null : () => terminal(true),
                    child: const Text('Review & Cancel — retains evidence'),
                  ),
                ],
                for (final o in c.observations)
                  Builder(
                    builder: (context) {
                      final cmd = c.commands
                          .where((r) => r.clientOperationId == o.operationId)
                          .firstOrNull;
                      final accepted = cmd?.state == 'ACKNOWLEDGED';
                      final receipt = c.receipts
                          .where((r) => r.clientOperationId == o.operationId)
                          .firstOrNull;
                      final outcome = receipt == null
                          ? null
                          : (jsonDecode(receipt.projectionJson)
                                as Map)['outcome'];
                      final item = c.items
                          .where(
                            (i) =>
                                i.trailerId ==
                                (o.resolvedTrailerId ?? o.identifiedTrailerId),
                          )
                          .firstOrNull;
                      final identified =
                          o.resolvedTrailerId ?? o.identifiedTrailerId;
                      final number = item == null
                          ? identified == null
                                ? 'Unresolved Trailer QR'
                                : '${c.cachedTrailerNumbers[identified] ?? identified} (cached identity)'
                          : (jsonDecode(
                              item.originalJson,
                            ) as Map)['recordedSnapshot']['operationalNumber'];
                      return ListTile(
                        title: Text(
                          '$number · ${accepted
                              ? 'Server ACK · $outcome'
                              : cmd?.state == 'CONFLICT' && cmd?.lastHttpStatus != null
                              ? 'SERVER_CONFLICT'
                              : c.reviewed.contains(o.operationId)
                              ? 'Historical failed observation reviewed'
                              : o.localResult == 'UNRESOLVED_QR'
                              ? 'UNRESOLVED_QR'
                              : 'LOCAL_PENDING · ${o.localResult}'}',
                        ),
                        subtitle: Text(
                          '${o.scannedAt}\nObserved ${o.observedLocationId} · expected ${item?.expectedLocationId ?? 'not in original snapshot'}\n${cmd?.state ?? 'Saved'} ${cmd?.lastErrorCode ?? ''}',
                        ),
                        trailing:
                            cmd != null &&
                                {
                                  'CONFLICT',
                                  'BLOCKED',
                                  'PERMANENT_FAILURE',
                                }.contains(cmd.state) &&
                                !c.reviewed.contains(cmd.clientOperationId)
                            ? TextButton(
                                onPressed: c.busy
                                    ? null
                                    : () async {
                                        final reason = await confirm(
                                          'Review failed observation',
                                          'Retain this failed operation and evidence, without claiming a scan ACK or changing any location. Exclude it from completion only after checking the fresh authorized inventory.',
                                          reason: true,
                                        );
                                        if (reason != null) {
                                          await c.reviewFailure(cmd, reason);
                                        }
                                      },
                                child: const Text('Review conflict'),
                              )
                            : null,
                      );
                    },
                  ),
                for (final row in c.items)
                  Builder(
                    builder: (context) {
                      final original = CheckItem(
                            checkMap(jsonDecode(row.originalJson)),
                          ),
                          latest = CheckItem(
                            checkMap(jsonDecode(row.serverJson)),
                          );
                      return ListTile(
                        title: Text(
                          '${original.json['recordedSnapshot']['operationalNumber']} · ${latest.json['result'] ?? 'Remaining candidate'}',
                        ),
                        subtitle: Text(
                          'Original expected: ${original.expectedLocationId ?? 'Unexpected'}\nObserved: ${latest.json['observedLocationId'] ?? 'Not observed'}\nScan: ${latest.json['scannedAt'] ?? 'None'}${latest.json['reconciliationId'] == null ? '' : '\nHistorical reconciliation: ${latest.json['reconciliationId']}'}',
                        ),
                        trailing:
                            c.canCorrect &&
                                s.status == 'COMPLETED' &&
                                latest.json['reconciliationId'] == null &&
                                {
                                  'MISSING',
                                  'LOCATION_MISMATCH',
                                }.contains(latest.json['result'])
                            ? TextButton(
                                onPressed: c.busy
                                    ? null
                                    : () => reconcile(latest),
                                child: Text(
                                  latest.json['result'] == 'MISSING'
                                      ? 'Mark Location Unknown'
                                      : 'Correct Recorded Location',
                                ),
                              )
                            : null,
                      );
                    },
                  ),
                for (final receipt in c.receipts.where(
                  (r) =>
                      r.serverId == s.id &&
                      checkMap(jsonDecode(r.projectionJson))
                          .containsKey('movementEvent'),
                ))
                  Builder(
                    builder: (context) {
                      final result = checkMap(
                        jsonDecode(receipt.projectionJson),
                      );
                      return ListTile(
                        title: const Text(
                          'Server-confirmed historical reconciliation',
                        ),
                        subtitle: Text(
                          'Reconciliation ${result['reconciliationId']}\nEvent ${(result['movementEvent'] as Map)['id']}\nAdministration alert ${result['alertId'] ?? 'None'}',
                        ),
                      );
                    },
                  ),
              ],
            ],
          ),
        ),
      );
    },
  );
}
