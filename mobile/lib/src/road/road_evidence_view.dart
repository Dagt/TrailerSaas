import 'dart:convert';

import 'package:flutter/material.dart';

import '../ui/fleet_widgets.dart';

import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../shift/camera_capture.dart';
import '../sync/replay_worker.dart';
import 'road_evidence_replay.dart';
import 'road_evidence_store.dart';

/// Offline service intent permits capturing POD, never advancing the cached Stop.
/// The existing resource chain makes the delivery wait for that service ACK.
class RoadEvidenceActions extends StatelessWidget {
  const RoadEvidenceActions({
    super.key,
    required this.worker,
    required this.trip,
  });
  final ReplayWorker worker;
  final Map<String, dynamic> trip;
  @override
  Widget build(BuildContext context) {
    final owner = worker.session.snapshot;
    if (owner == null) return const SizedBox.shrink();
    return StreamBuilder<List<LocalCommand>>(
      key: ValueKey(
        '${owner.userId}|${owner.membership.tenantId}|${trip['id']}',
      ),
      stream: worker.store.watch(owner.userId, owner.membership.tenantId),
      builder: (context, snapshot) {
        final service = (snapshot.data ?? <LocalCommand>[])
            .where(
              (c) =>
                  c.tripId == trip['id'] &&
                  c.commandType == 'service-starts' &&
                  {
                    'QUEUED',
                    'SENDING',
                    'RETRYABLE_FAILURE',
                    'BLOCKED',
                  }.contains(c.state),
            )
            .map((c) => c.aggregateId)
            .toSet();
        final stops = (trip['stops'] as List).cast<Map<String, dynamic>>();
        final active = stops
            .where((s) => !{'COMPLETED', 'CANCELLED'}.contains(s['status']))
            .firstOrNull;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            RoadEvidencePanel(
              worker: worker,
              tripId: trip['id'] as String,
              stopId: active?['id'] as String?,
              fuelAvailable:
                  {'AT_STOP', 'IN_TRANSIT'}.contains(trip['status']) ||
                  (snapshot.data ?? <LocalCommand>[]).any(
                    (c) =>
                        c.tripId == trip['id'] &&
                        c.commandType == 'departures' &&
                        {
                          'QUEUED',
                          'SENDING',
                          'RETRYABLE_FAILURE',
                          'BLOCKED',
                        }.contains(c.state),
                  ),
            ),
            for (final stop in stops)
              if (stop['stopType'] == 'DELIVERY' &&
                  !{'COMPLETED', 'CANCELLED'}.contains(stop['status']) &&
                  (trip['status'] == 'AT_STOP' &&
                          stop['status'] == 'IN_SERVICE' ||
                      service.contains(stop['id']))) ...[
                Text(
                  'Delivery: ${stop['locationName'] ?? stop['siteName'] ?? (stop['siteSnapshot'] as Map?)?['name'] ?? 'Downloaded destination'}',
                ),
                if (service.contains(stop['id']) &&
                    stop['status'] != 'IN_SERVICE')
                  const Text(
                    'Service recorded locally. POD waits for confirmation; the server Stop state has not changed.',
                  ),
                RoadEvidencePanel(
                  worker: worker,
                  tripId: trip['id'] as String,
                  stopId: stop['id'] as String,
                  deliveryAvailable: true,
                  auxiliaryActions: false,
                ),
              ],
          ],
        );
      },
    );
  }
}

class RoadEvidencePanel extends StatefulWidget {
  const RoadEvidencePanel({
    super.key,
    required this.worker,
    this.tripId,
    this.stopId,
    this.deliveryAvailable = false,
    this.camera,
    this.auxiliaryActions = true,
    this.fuelAvailable,
  });
  final ReplayWorker worker;
  final String? tripId, stopId;
  final bool deliveryAvailable;
  final InspectionCamera? camera;
  final bool auxiliaryActions;
  final bool? fuelAvailable;
  @override
  State<RoadEvidencePanel> createState() => _PanelState();
}

class _PanelState extends State<RoadEvidencePanel> {
  RoadEvidenceStore? _store;
  RoadEvidenceStore? get store {
    if (widget.worker.futureEvidence is! RoadEvidenceReplay) return null;
    return _store ??= RoadEvidenceStore(
      widget.worker.futureEvidence! as RoadEvidenceReplay,
      camera: widget.camera,
    );
  }

  String? message;
  bool working = false;
  Future<void> open(String purpose, [RoadDraft? draft]) async {
    if (store == null) return;
    setState(() => working = true);
    try {
      draft ??= await store!.create(
        purpose,
        widget.tripId!,
        stop: purpose == 'FUEL' ? null : widget.stopId,
      );
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => RoadEvidenceForm(
            store: store!,
            draft: draft!,
            worker: widget.worker,
          ),
        ),
      );
    } catch (_) {
      if (mounted) {
        setState(
          () => message = 'The form could not be opened. Saved work was retained. Refresh your session.',
        );
      }
    } finally {
      if (mounted) setState(() => working = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.worker.session.snapshot;
    if (s == null || store == null) return const SizedBox.shrink();
    final caps = s.membership.capabilities;
    final canRecord =
        {
          SessionState.ready,
          SessionState.offline,
        }.contains(widget.worker.session.state) &&
        s.driver != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const FleetSectionTitle(
          'Road evidence and operations',
          icon: Icons.photo_library_outlined,
        ),
        if (message != null) Text(message!),
        if (widget.tripId != null && canRecord) ...[
          if (widget.deliveryAvailable &&
              caps.contains('POD_UPLOAD') &&
              caps.contains('TRIP_EXECUTE_ASSIGNED')) ...[
            FilledButton(
              onPressed: working ? null : () => open('SIGNED_BOL'),
              child: const Text('Delivery with signed BOL'),
            ),
            FilledButton(
              onPressed: working ? null : () => open('NO_RECEIVER_AVAILABLE'),
              child: const Text('Delivery with no receiver available'),
            ),
          ],
          if (widget.auxiliaryActions &&
              caps.contains('FUEL_EVENT_CREATE') &&
              (widget.fuelAvailable ??
                  s.trips.any(
                    (t) =>
                        t.id == widget.tripId &&
                        {'AT_STOP', 'IN_TRANSIT'}.contains(t.status),
                  )))
            OutlinedButton(
              onPressed: working ? null : () => open('FUEL'),
              child: const Text('Record fuel'),
            ),
          if (widget.auxiliaryActions && caps.contains('EXCEPTION_REPORT'))
            OutlinedButton(
              onPressed: working ? null : () => open('PROBLEM'),
              child: const Text('Report a problem'),
            ),
        ],
        StreamBuilder<List<RoadDraft>>(
          key: ValueKey('${s.userId}|${s.membership.tenantId}'),
          stream: store!.watch(s.userId, s.membership.tenantId),
          builder: (context, snapshot) => Column(
            children: [
              for (final draft in snapshot.data ?? <RoadDraft>[])
                if (widget.tripId == null || draft.tripId == widget.tripId)
                  FleetRecord(
                    title: Text(switch (draft.purpose) {
                      'SIGNED_BOL' => 'Signed BOL',
                      'NO_RECEIVER_AVAILABLE' => 'No receiver',
                      'FUEL' => 'Fuel',
                      _ => 'Operational problem',
                    }),
                    subtitle: Text(
                      draft.commandId == null ? 'Draft saved on this device' : 'Operation saved; check confirmation in Synchronization',
                    ),
                    action: draft.commandId == null
                        ? TextButton(
                            onPressed: canRecord && !working
                                ? () => open(draft.purpose, draft)
                                : null,
                            child: const Text('Resume'),
                          )
                        : null,
                  ),
            ],
          ),
        ),
      ],
    );
  }
}

class RoadEvidenceForm extends StatefulWidget {
  const RoadEvidenceForm({
    super.key,
    required this.store,
    required this.draft,
    required this.worker,
  });
  final RoadEvidenceStore store;
  final RoadDraft draft;
  final ReplayWorker worker;
  @override
  State<RoadEvidenceForm> createState() => _FormState();
}

class _FormState extends State<RoadEvidenceForm> {
  late RoadDraft draft = widget.draft;
  late final comment = TextEditingController(
    text: (jsonDecode(draft.inputsJson) as Map)['comment'] as String?,
  );
  late final odometer = TextEditingController(
    text: (jsonDecode(draft.inputsJson) as Map)['odometer']?.toString(),
  );
  late String category =
      (jsonDecode(draft.inputsJson) as Map)['category'] as String? ?? 'OTHER';
  bool busy = false;
  String? message;
  @override
  void dispose() {
    comment.dispose();
    odometer.dispose();
    super.dispose();
  }

  Future<void> saved() async {
    final input = draft.purpose == 'FUEL'
        ? <String, dynamic>{'odometer': num.tryParse(odometer.text)}
        : draft.purpose == 'PROBLEM'
        ? <String, dynamic>{'category': category, 'comment': comment.text}
        : draft.purpose == 'NO_RECEIVER_AVAILABLE'
        ? <String, dynamic>{'comment': comment.text}
        : <String, dynamic>{};
    await widget.store.save(draft, input);
    draft = (await widget.store.find(draft.userId, draft.tenantId, draft.id))!;
  }

  Future<void> perform(Future<void> Function() action) async {
    setState(() => busy = true);
    try {
      await action();
      draft = (await widget.store.find(
        draft.userId,
        draft.tenantId,
        draft.id,
      ))!;
      if (mounted && widget.store.current(draft)) {
        setState(
          () => message = 'Saved on this device. Awaiting server confirmation.',
        );
      }
    } catch (error) {
      if (mounted && widget.store.current(draft)) {
        setState(
          () => message = error is FormatException && error.message == 'El Tractor cambió. Conserva el recibo y revisa con operaciones.'
              ? 'The Tractor changed. Keep the receipt and review with Operations.'
              : 'The action could not be completed. Review fields and files; the draft was retained.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> queue() async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        title: const Text('Confirm recorded intent'),
        content: const Text(
          'Record only an action that occurred. The server validates the evidence and operation; saving offline does not confirm execution.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Save operation'),
          ),
        ],
      ),
    );
    if (accepted != true || !mounted) return;
    await perform(() async {
      await saved();
      await widget.store.queue(draft);
    });
    if (mounted && widget.store.current(draft) && draft.commandId != null) {
      Navigator.pop(context);
      await widget.worker.sync();
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.store.session,
    builder: (context, _) {
      if (!widget.store.current(draft)) {
        return Scaffold(
          appBar: AppBar(title: const Text('Evidence')),
          body: const Text('The session changed. Return to current work.'),
        );
      }
      final photos = jsonDecode(draft.mediaJson) as Map;
      final fields = switch (draft.purpose) {
        'SIGNED_BOL' => {'bol': 'Capture signed BOL'},
        'NO_RECEIVER_AVAILABLE' => {
          'bol': 'Capture BOL',
          'deliveryPhoto': 'Capture delivery photo',
        },
        'FUEL' => {'receipt': 'Capture receipt'},
        _ => {
          'damage0': 'Capture damage photo',
          'damage1': 'Add damage photo',
          'damage2': 'Add another photo',
          'damage3': 'Add final photo',
        },
      };
      return Scaffold(
        appBar: AppBar(
          title: Text(switch (draft.purpose) {
            'FUEL' => 'Fuel',
            'PROBLEM' => 'Report a problem',
            _ => 'Proof of Delivery',
          }),
        ),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            FleetFormBody(
              children: [
                const FleetHeader(
                  'Capture and review',
                  eyebrow: 'EVIDENCE',
                  description: 'Save private evidence, then review the recorded operation.',
                ),
                const FleetNotice(
                  'Private data and photos. The server is authoritative. Do not use this form while driving.',
                ),
                if (draft.purpose == 'FUEL') ...[
                  Text('Selected Trip: ${draft.tripId}'),
                  Text(
                    'Recorded Tractor: ${widget.store.session.snapshot!.driver?.shiftContext?.tractors.where((t) => t.id == draft.tractorId).firstOrNull?.number ?? draft.tractorId}',
                  ),
                  const Text(
                    'Trip and Tractor are downloaded assignments; they are revalidated on sync.',
                  ),
                  TextField(
                    controller: odometer,
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    decoration: const InputDecoration(labelText: 'Odometer'),
                  ),
                ],
                if (draft.purpose == 'PROBLEM')
                  DropdownButtonFormField<String>(
                    isExpanded: true,
                    itemHeight: null,
                    initialValue: category,
                    decoration: const InputDecoration(labelText: 'Category'),
                    items: [
                      for (final entry in {
                        'DELAY_TRAFFIC': 'Traffic delay',
                        'EQUIPMENT_DAMAGE': 'Equipment damage',
                        'DESTINATION_CUSTOMER_ISSUE': 'Destination issue',
                        'LOAD_REJECTED': 'Load rejected',
                        'OTHER': 'Other',
                      }.entries)
                        DropdownMenuItem(
                          value: entry.key,
                          child: Text(entry.value),
                        ),
                    ],
                    onChanged: busy
                        ? null
                        : (value) => setState(() => category = value!),
                  ),
                if (draft.purpose == 'NO_RECEIVER_AVAILABLE' ||
                    draft.purpose == 'PROBLEM')
                  TextField(
                    controller: comment,
                    maxLength: 4000,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      labelText: 'Required comment',
                    ),
                  ),
                for (final entry in fields.entries)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: OutlinedButton.icon(
                      onPressed: busy || draft.commandId != null
                          ? null
                          : () => perform(() async {
                              await saved();
                              await widget.store.take(draft, entry.key);
                            }),
                      icon: Icon(
                        photos.containsKey(entry.key)
                            ? Icons.photo
                            : Icons.camera_alt,
                      ),
                      label: Text(
                        '${entry.value}${photos.containsKey(entry.key) ? ' · saved locally' : ''}',
                      ),
                    ),
                  ),
                if (message != null)
                  Semantics(liveRegion: true, child: Text(message!)),
                if (busy) const LinearProgressIndicator(),
                OutlinedButton(
                  onPressed: busy || draft.commandId != null
                      ? null
                      : () => perform(saved),
                  child: const Text('Save draft'),
                ),
                FilledButton(
                  onPressed: busy || draft.commandId != null ? null : queue,
                  child: const Text('Confirm and synchronize'),
                ),
              ],
            ),
          ],
        ),
      );
    },
  );
}
