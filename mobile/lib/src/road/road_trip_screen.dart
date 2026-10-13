import 'package:flutter/material.dart';

import '../session/session_controller.dart';
import '../session/contracts.dart';
import '../platform/qr_scanner.dart';
import '../platform/native_qr_scanner.dart';
import 'online_command_journal.dart';
import 'road_trip_controller.dart';
import '../sync/replay_worker.dart';
import '../sync/offline_actions.dart';
import '../sync/sync_panel.dart';
import 'road_evidence_view.dart';
import '../platform/location_assistance.dart';
import 'driver_updates_store.dart';
import 'driver_updates_view.dart';

class RoadTripScreen extends StatefulWidget {
  const RoadTripScreen({
    super.key,
    required this.session,
    required this.cached,
    this.journal,
    this.scanner,
    this.worker,
    this.location,
    this.clock,
    this.updates,
  });
  final SessionController session;
  final WorkTrip cached;
  final OnlineCommandJournal? journal;
  final QrScanner? scanner;
  final ReplayWorker? worker;
  final LocationAssistancePort? location;
  final DateTime Function()? clock;
  final DriverUpdatesStore? updates;
  @override
  State<RoadTripScreen> createState() => _RoadTripScreenState();
}

class _RoadTripScreenState extends State<RoadTripScreen>
    with WidgetsBindingObserver {
  late final RoadTripController controller;
  late final SessionSnapshot owner;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    owner = widget.session.snapshot!;
    controller = RoadTripController(
      widget.session,
      widget.cached.id,
      widget.journal ?? FileOnlineCommandJournal(),
      worker: widget.worker,
      location: widget.location,
      clock: widget.clock,
      updates: widget.updates,
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
    controller.assistance.setForeground(state == AppLifecycleState.resumed);
    if (state == AppLifecycleState.resumed) {
      widget.updates?.refresh().catchError((Object _) {});
      controller.load();
    }
  }

  Future<bool> confirm(String title, String text) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: Text(text),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Confirm'),
            ),
          ],
        ),
      ) ??
      false;
  Future<void> action(
    String title,
    String path,
    Map<String, dynamic> fields,
  ) async {
    if (await confirm(
      title,
      widget.worker == null
          ? 'Record this action only after it physically happened. Wait for server confirmation.'
          : 'Record only an action that physically happened. It is saved on this device, pending server confirmation.',
    )) {
      await controller.submit(path, {
        'occurredAt': DateTime.now().toUtc().toIso8601String(),
        ...fields,
      });
    }
  }

  Future<void> scan() async {
    final token = await (widget.scanner ?? NativeQrScanner(context)).scan();
    if (token == null || !mounted) return;
    final resolved = await controller.identify(token);
    if (resolved == null || !mounted) return;
    final trip = controller.trip;
    if (trip == null) return;
    if (trip['plannedInitialTrailerId'] != resolved['trailerId']) {
      await confirm(
        'Trailer not authorized',
        'This Trailer differs from the expected Trailer. Contact dispatch; scanning does not authorize substitution.',
      );
      return;
    }
    await action(
      'Confirm Hook ${resolved['operationalNumber']}',
      'tenants/${owner.membership.tenantId}/trips/${widget.cached.id}/hooks',
      {
        'trailerId': resolved['trailerId'],
        'tractorId': trip['currentTractorId'],
        'driverId': widget.session.snapshot!.driver!.id,
        'expectedTrailerVersion': resolved['version'],
      },
    );
  }

  Future<void> dropCoupled([Map<String, dynamic>? recordedStop]) async {
    final trip = controller.trip;
    final coupling =
        controller.detail?['currentCoupling'] as Map<String, dynamic>?;
    if (trip == null || coupling == null) return;
    final stop =
        recordedStop ??
        (trip['stops'] as List)
            .cast<Map<String, dynamic>>()
            .where((s) => s['status'] == 'IN_SERVICE')
            .firstOrNull;
    if (stop == null || stop['locationId'] == null) {
      await confirm(
        'Location required',
        'Dispatch must provide an authorized Operational Location before physical Drop.',
      );
      return;
    }
    // This identity is from the authenticated active coupling, never manual input.
    // A public QR must not prevent closing an existing OOS/inactive coupling.
    await action(
      'Confirm Drop ${coupling['operationalNumber']}',
      'tenants/${owner.membership.tenantId}/trips/${widget.cached.id}/drops',
      {
        'trailerId': coupling['trailerId'],
        'stopId': stop['id'],
        'locationId': stop['locationId'],
      },
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([controller, controller.assistance]),
    builder: (context, _) {
      final session = widget.session;
      final s = session.snapshot;
      final same =
          s != null &&
          s.userId == owner.userId &&
          s.membership.tenantId == owner.membership.tenantId &&
          s.driver?.id == owner.driver?.id &&
          s.firebaseUid == owner.firebaseUid;
      if (!same) {
        return Scaffold(
          appBar: AppBar(title: const Text('Trip')),
          body: const Center(
            child: Text('Session changed. Return to current assigned work.'),
          ),
        );
      }
      final downloaded = s.trips
          .where((t) => t.id == widget.cached.id)
          .firstOrNull;
      if (controller.trip == null && downloaded == null) {
        return Scaffold(
          appBar: AppBar(title: const Text('Trip')),
          body: Column(
            children: [
              const Text('This Trip is no longer in downloaded assigned work.'),
              TextButton(
                onPressed: controller.sending ? null : controller.load,
                child: const Text('Reload authoritative state'),
              ),
            ],
          ),
        );
      }
      final trip = controller.trip ?? downloaded!.data;
      final stops = (trip['stops'] as List).cast<Map<String, dynamic>>();
      final active = stops
          .where(
            (s) => s['status'] != 'COMPLETED' && s['status'] != 'CANCELLED',
          )
          .firstOrNull;
      final prior = stops
          .where(
            (s) =>
                s['status'] == 'COMPLETED' &&
                (active == null ||
                    (s['sequenceNumber'] as int) <
                        (active['sequenceNumber'] as int)),
          )
          .lastOrNull;
      final enabled =
          (widget.worker == null ? controller.online : controller.canRecord) &&
          controller.detail != null &&
          !controller.hasQueued &&
          !controller.sending &&
          controller.pending == null;
      final capabilities = s.membership.capabilities;
      final execute = enabled && capabilities.contains('TRIP_EXECUTE_ASSIGNED');
      final base = 'tenants/${s.membership.tenantId}';
      final expected = trip['plannedTrailer'] as Map<String, dynamic>?;
      final coupled =
          controller.detail?['currentCoupling'] as Map<String, dynamic>?;
      return Scaffold(
        appBar: AppBar(
          title: const Text('Assigned Trip'),
          actions: [
            IconButton(
              tooltip: 'Reload authoritative state',
              onPressed: controller.sending ? null : controller.load,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Trip · ${trip['status']}',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            if (session.state == SessionState.offline)
              Text(
                widget.worker == null
                    ? 'Offline — downloaded work only. Execution requires connectivity; no command is queued.'
                    : 'Offline — downloaded server state. Recorded actions remain pending until synchronized.',
              ),
            if (session.state == SessionState.loading)
              const Text(
                'Refreshing session and assigned work. Execution is unavailable.',
              ),
            Text(
              'Tractor: ${controller.detail?['tractorOperationalNumber'] ?? 'Not refreshed'}',
            ),
            Text(
              'Expected Trailer: ${expected?['operationalNumber'] ?? 'Not planned'}',
            ),
            Text(
              'Physical coupling: ${coupled == null ? 'Uncoupled / not refreshed' : coupled['operationalNumber']}',
            ),
            if (controller.sending) const LinearProgressIndicator(),
            if (controller.message != null)
              Semantics(liveRegion: true, child: Text(controller.message!)),
            if (controller.traceId != null)
              Text('Support reference: ${controller.traceId}'),
            if (widget.updates != null)
              DriverUpdatesPanel(store: widget.updates!),
            if (widget.worker != null) ...[
              SyncPanel(worker: widget.worker!),
              OfflineActions(
                key: ObjectKey(session.snapshot),
                worker: widget.worker!,
                trip: downloaded ?? widget.cached,
                coupling: coupled,
                onDrop: dropCoupled,
                onHook: scan,
              ),
            ],
            if (controller.pending != null)
              FilledButton(
                onPressed: !controller.canRecover
                    ? null
                    : () async {
                        if (await confirm(
                          'Recover submission',
                          'Recover the previously recorded key and payload. Do not repeat the physical action.',
                        )) {
                          await controller.recover();
                        }
                      },
                child: const Text('Recover previous submission'),
              ),
            if (execute &&
                Set.of({'ASSIGNED', 'AT_STOP'}).contains(trip['status']) &&
                capabilities.contains('TRAILER_HOOK') &&
                coupled == null)
              FilledButton(
                onPressed: scan,
                child: const Text('Scan Trailer and confirm Hook'),
              ),
            if (trip['status'] == 'ASSIGNED' &&
                controller.detail?['departurePrerequisitesSatisfied'] == false)
              const Text(
                'First departure requires verified On Duty and Pre-Trip. A recorded departure waits for a matching pending inspection and server authorization.',
              ),
            if (execute &&
                trip['status'] == 'ASSIGNED' &&
                (controller.detail?['departurePrerequisitesSatisfied'] ==
                        true ||
                    controller.pendingPreTrip) &&
                capabilities.contains('DEPARTURE_CONFIRM'))
              FilledButton(
                onPressed: () => action(
                  'Confirm first departure',
                  '$base/trips/${widget.cached.id}/departures',
                  {'nextStopId': active?['id']},
                ),
                child: const Text('Confirm first departure'),
              ),
            if (active != null)
              Text(
                'Active destination: ${active['locationName'] ?? active['siteName'] ?? (active['siteSnapshot'] as Map?)?['name'] ?? 'Operational Stop'}',
              ),
            if (controller.assistance.available && execute) ...[
              const Text(
                'Use location once to suggest arrival. You confirm; your route is not continuously recorded.',
              ),
              TextButton(
                onPressed: controller.assistance.busy
                    ? null
                    : controller.assistance.check,
                child: const Text('Check arrival location'),
              ),
            ],
            if (controller.assistance.busy) const LinearProgressIndicator(),
            if (controller.assistance.message != null)
              Text(controller.assistance.message!),
            if (controller.assistance.suggestion case final suggestion?)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    children: [
                      Text(
                        'It looks like you arrived at ${suggestion.target.name}.',
                      ),
                      const Text(
                        'This suggestion does not change the Stop. Confirm only after you have arrived.',
                      ),
                      FilledButton(
                        onPressed: execute
                            ? controller.confirmAssistedArrival
                            : null,
                        child: const Text('Confirm Arrival'),
                      ),
                      TextButton(
                        onPressed: controller.assistance.notYet,
                        child: const Text('Not Yet'),
                      ),
                    ],
                  ),
                ),
              ),
            if (execute &&
                trip['status'] == 'IN_TRANSIT' &&
                active?['status'] == 'EN_ROUTE' &&
                capabilities.contains('ARRIVAL_CONFIRM'))
              FilledButton(
                onPressed: () => action(
                  'Confirm arrival',
                  '$base/stops/${active!['id']}/arrivals',
                  {'source': 'MANUAL'},
                ),
                child: const Text('Confirm manual arrival'),
              ),
            if (execute &&
                trip['status'] == 'AT_STOP' &&
                active?['status'] == 'ARRIVED')
              FilledButton(
                onPressed: () => action(
                  'Start service',
                  '$base/stops/${active!['id']}/service-starts',
                  {},
                ),
                child: const Text('Start service'),
              ),
            if (execute &&
                trip['status'] == 'AT_STOP' &&
                active?['status'] == 'IN_SERVICE' &&
                active?['stopType'] == 'DROP_HOOK') ...[
              const Text(
                'Drop & Hook: confirm the physically coupled Trailer Drop first, then scan the authorized replacement and confirm Hook.',
              ),
              if (coupled != null && capabilities.contains('TRAILER_DROP'))
                FilledButton(
                  onPressed: dropCoupled,
                  child: const Text('Confirm Drop of coupled Trailer'),
                ),
            ],
            if (active?['status'] == 'IN_SERVICE' &&
                active?['stopType'] == 'DELIVERY' &&
                widget.worker == null)
              const Text(
                'Delivery completion requires finalized POD and the persistent command queue.',
              ),
            if (widget.worker != null &&
                downloaded != null &&
                {
                  'AT_STOP',
                  'IN_TRANSIT',
                  'ASSIGNED',
                  'CANCELLING',
                }.contains(trip['status']))
              RoadEvidenceActions(worker: widget.worker!, trip: trip),
            if (execute &&
                trip['status'] == 'AT_STOP' &&
                active?['status'] == 'IN_SERVICE' &&
                active?['stopType'] != 'DELIVERY')
              FilledButton(
                onPressed: () => action(
                  'Complete Stop service',
                  '$base/stops/${active!['id']}/completions',
                  {},
                ),
                child: const Text('Complete non-delivery Stop'),
              ),
            if (execute &&
                trip['status'] == 'AT_STOP' &&
                active?['status'] == 'PENDING' &&
                prior != null &&
                capabilities.contains('DEPARTURE_CONFIRM'))
              FilledButton(
                onPressed: () => action(
                  'Depart to next Stop',
                  '$base/stops/${prior['id']}/departures',
                  {'nextStopId': active!['id']},
                ),
                child: const Text('Depart to next Stop'),
              ),
            if (trip['status'] == 'COMPLETED' && controller.detail != null)
              const Text(
                'Trip completion confirmed by server. Shift remains separate.',
              ),
            const Divider(),
            const Text('Executable Stops'),
            ...stops.map(
              (stop) => ListTile(
                title: Text(
                  '${stop['sequenceNumber']} · ${stop['siteName'] ?? (stop['siteSnapshot'] as Map?)?['name'] ?? 'Operational Stop'}',
                ),
                subtitle: Text(
                  '${stop['stopType']} · ${stop['status']}\n${stop['instructions'] ?? ''}',
                ),
              ),
            ),
          ],
        ),
      );
    },
  );
}
