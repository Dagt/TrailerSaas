import 'dart:convert';

import 'package:flutter/material.dart';

import '../persistence/app_database.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import 'command_store.dart';
import 'road_command_capture.dart';
import 'replay_worker.dart';

/// A pending intent guide, not a client replica of Trip/Stop state. It offers the
/// next explicit recorded action without marking any Stop executed/completed.
class OfflineActions extends StatefulWidget {
  const OfflineActions({
    super.key,
    required this.worker,
    required this.trip,
    this.coupling,
    this.onDrop,
    this.onHook,
  });
  final ReplayWorker worker;
  final WorkTrip trip;
  final Map<String, dynamic>? coupling;
  final Future<void> Function(Map<String, dynamic>)? onDrop;
  final Future<void> Function()? onHook;
  @override
  State<OfflineActions> createState() => _OfflineActionsState();
}

class _OfflineActionsState extends State<OfflineActions> {
  late final owner = widget.worker.session.snapshot!;
  late final rows = widget.worker.store.watch(
    owner.userId,
    owner.membership.tenantId,
  );
  bool saving = false;
  String? message;

  Future<void> record(
    String title,
    String path,
    Map<String, dynamic> fields,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: const Text(
          'Record only an action that physically happened. It is saved on this device and is NOT confirmed by the server yet.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Save pending action'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => saving = true);
    try {
      await RoadCommandCapture(widget.worker.session, widget.worker).record(
        owner,
        widget.trip.id,
        path,
        {'occurredAt': DateTime.now().toUtc().toIso8601String(), ...fields},
      );
      message = 'Saved on this device — pending synchronization. Server state has not been changed locally.';
    } catch (_) {
      message = 'This action could not be safely recorded. Review downloaded work and pending operations.';
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => StreamBuilder<List<LocalCommand>>(
    stream: rows,
    builder: (context, snapshot) {
      final session = widget.worker.session, current = session.snapshot;
      if (!snapshot.hasData) {
        return Text(
          snapshot.hasError
              ? 'Saved operations could not be read. Retry sync before recording another action.'
              : 'Loading saved operations…',
        );
      }
      if (current?.userId != owner.userId ||
          current?.membership.tenantId != owner.membership.tenantId ||
          session.auth.uid != owner.firebaseUid ||
          current?.driver == null ||
          !{SessionState.ready, SessionState.offline}.contains(session.state) ||
          !owner.membership.capabilities.contains('TRIP_EXECUTE_ASSIGNED')) {
        return const SizedBox.shrink();
      }
      final pending = (snapshot.data ?? [])
          .where(
            (c) =>
                c.tripId == widget.trip.id &&
                c.state != CommandState.acknowledged.value,
          )
          .toList();
      if (session.state != SessionState.offline && pending.isEmpty) {
        return const SizedBox.shrink();
      }
      final stops = widget.trip.stops;
      final last = pending.lastOrNull;
      Map<String, dynamic>? stop = last == null
          ? stops
                .where((s) => !{'COMPLETED', 'CANCELLED'}.contains(s['status']))
                .firstOrNull
          : stops.where((s) => s['id'] == last.aggregateId).firstOrNull;
      String? action;
      if (last == null) {
        action = switch (stop?['status']) {
          'EN_ROUTE' => 'arrivals',
          'ARRIVED' => 'service-starts',
          'IN_SERVICE' => 'completions',
          _ => null,
        };
      } else if (last.commandType == 'departures') {
        final body = CommandIntentBody.from(last);
        stop = stops.where((s) => s['id'] == body['nextStopId']).firstOrNull;
        action = 'arrivals';
      } else if (last.commandType == 'hooks') {
        final dropped = pending
            .where((c) => c.commandType == 'drops')
            .lastOrNull;
        if (dropped != null) {
          final dropBody = CommandIntentBody.from(dropped),
              hookBody = CommandIntentBody.from(last);
          if (dropBody['trailerId'] != hookBody['trailerId']) {
            stop = stops
                .where((s) => s['id'] == dropBody['stopId'])
                .firstOrNull;
            action = 'completions';
          }
        }
      } else if (last.commandType == 'drops') {
        stop = stops
            .where((s) => s['id'] == CommandIntentBody.from(last)['stopId'])
            .firstOrNull;
      } else {
        action = switch (last.commandType) {
          'arrivals' => 'service-starts',
          'service-starts' => 'completions',
          'completions' => 'departures',
          _ => null,
        };
      }
      final type = stop?['stopType'];
      // POD and physical exchange obligations cannot be fabricated by a pending
      // intent. Their guided physical/evidence commands must establish the facts.
      if (action == 'completions' &&
          (type == 'DELIVERY' ||
              (type == 'DROP_HOOK' && last?.commandType != 'hooks'))) {
        action = null;
      }
      final index = stop == null ? -1 : stops.indexOf(stop);
      final next = index >= 0 && index + 1 < stops.length
          ? stops
                .skip(index + 1)
                .where((s) => !{'COMPLETED', 'CANCELLED'}.contains(s['status']))
                .firstOrNull
          : null;
      if (action == 'departures' &&
          (next == null ||
              !owner.membership.capabilities.contains('DEPARTURE_CONFIRM'))) {
        action = null;
      }
      if (action == 'arrivals' &&
          !owner.membership.capabilities.contains('ARRIVAL_CONFIRM')) {
        action = null;
      }
      final selectedAction = action, selectedStop = stop;
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Record offline intent'),
              const Text(
                'Downloaded server state stays unchanged. Each action depends on the prior recorded action; acceptance is checked on reconnect.',
              ),
              if (message != null)
                Semantics(liveRegion: true, child: Text(message!)),
              if (type == 'DELIVERY' && action == null)
                const Text(
                  'Delivery completion requires finalized POD; it cannot be recorded here.',
                ),
              if (type == 'DROP_HOOK' && action == null)
                const Text(
                  'Record the real Drop and identified authorized replacement Hook. No physical exchange is inferred.',
                ),
              if (type == 'DROP_HOOK' &&
                  widget.coupling != null &&
                  selectedStop != null &&
                  (last?.commandType == 'service-starts' ||
                      (last == null &&
                          selectedStop['status'] == 'IN_SERVICE')) &&
                  owner.membership.capabilities.contains('TRAILER_DROP') &&
                  widget.onDrop != null)
                FilledButton(
                  onPressed: saving ? null : () => widget.onDrop!(selectedStop),
                  child: const Text('Record physical Drop'),
                ),
              if (type == 'DROP_HOOK' &&
                  last?.commandType == 'drops' &&
                  widget.onHook != null &&
                  owner.membership.capabilities.contains('TRAILER_HOOK'))
                FilledButton(
                  onPressed: saving ? null : widget.onHook,
                  child: const Text(
                    'Scan authorized replacement and record Hook',
                  ),
                ),
              if (selectedAction != null && selectedStop != null)
                FilledButton(
                  onPressed: saving || snapshot.hasError
                      ? null
                      : () => record(
                          'Record ${selectedAction.replaceAll('-', ' ')} at ${selectedStop['siteName'] ?? 'downloaded Stop'}',
                          'tenants/${owner.membership.tenantId}/stops/${selectedStop['id']}/$selectedAction',
                          {
                            if (selectedAction == 'arrivals')
                              'source': 'MANUAL',
                            if (selectedAction == 'departures')
                              'nextStopId': next!['id'],
                          },
                        ),
                  child: Text('Record ${selectedAction.replaceAll('-', ' ')}'),
                ),
            ],
          ),
        ),
      );
    },
  );
}

// JSON is parsed only to retain the original explicit next Stop identity. No
// text/address matching or local status mutation is performed.
class CommandIntentBody {
  static Map<String, dynamic> from(LocalCommand command) =>
      jsonDecode(command.payloadJson) as Map<String, dynamic>;
}
