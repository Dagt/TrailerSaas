import 'package:flutter/material.dart';

import '../session/session_controller.dart';
import '../session/contracts.dart';
import '../platform/qr_scanner.dart';
import '../platform/native_qr_scanner.dart';
import 'online_command_journal.dart';
import 'road_trip_controller.dart';

class RoadTripScreen extends StatefulWidget {
  const RoadTripScreen({
    super.key,
    required this.session,
    required this.cached,
    this.journal,
    this.scanner,
  });
  final SessionController session;
  final WorkTrip cached;
  final OnlineCommandJournal? journal;
  final QrScanner? scanner;
  @override
  State<RoadTripScreen> createState() => _RoadTripScreenState();
}

class _RoadTripScreenState extends State<RoadTripScreen> {
  late final RoadTripController controller;
  late final SessionSnapshot owner;
  @override
  void initState() {
    super.initState();
    owner = widget.session.snapshot!;
    controller = RoadTripController(
      widget.session,
      widget.cached.id,
      widget.journal ?? FileOnlineCommandJournal(),
    );
    controller.load();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
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
      'Record this action only after it physically happened. Wait for server confirmation.',
    )) {
      await controller.submit(path, {
        'occurredAt': DateTime.now().toUtc().toIso8601String(),
        ...fields,
      });
    }
  }

  Future<void> scan(bool drop) async {
    final token = await (widget.scanner ?? NativeQrScanner(context)).scan();
    if (token == null || !mounted) return;
    final resolved = await controller.identify(token);
    if (resolved == null || !mounted) return;
    final trip = controller.trip;
    if (trip == null) return;
    final coupling =
        controller.detail?['currentCoupling'] as Map<String, dynamic>?;
    final expected = drop
        ? (coupling?['trailerId'])
        : trip['plannedInitialTrailerId'];
    if (expected != resolved['trailerId']) {
      await confirm(
        'Trailer not authorized',
        'This Trailer differs from the ${drop ? 'physically coupled' : 'expected'} Trailer. Contact dispatch; scanning does not authorize substitution.',
      );
      return;
    }
    final tenant = owner.membership.tenantId;
    final base = 'tenants/$tenant/trips/${widget.cached.id}';
    if (drop) {
      final stop = (trip['stops'] as List)
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
      await action(
        'Confirm Drop ${resolved['operationalNumber']}',
        '$base/drops',
        {
          'trailerId': resolved['trailerId'],
          'stopId': stop['id'],
          'locationId': stop['locationId'],
        },
      );
    } else {
      await action(
        'Confirm Hook ${resolved['operationalNumber']}',
        '$base/hooks',
        {
          'trailerId': resolved['trailerId'],
          'tractorId': trip['currentTractorId'],
          'driverId': owner.driver!.id,
          'expectedTrailerVersion': resolved['version'],
        },
      );
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final session = widget.session;
      final s = session.snapshot;
      final same =
          s != null &&
          s.userId == owner.userId &&
          s.membership.tenantId == owner.membership.tenantId &&
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
          controller.online &&
          controller.detail != null &&
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
              const Text(
                'Offline — downloaded work only. Execution requires connectivity; no command is queued.',
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
            if (controller.pending?.tripId == widget.cached.id)
              FilledButton(
                onPressed: controller.sending || !controller.online
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
                onPressed: () => scan(false),
                child: const Text('Scan Trailer and confirm Hook'),
              ),
            if (trip['status'] == 'ASSIGNED' &&
                controller.detail?['departurePrerequisitesSatisfied'] == false)
              const Text(
                'First departure requires verified On Duty and Pre-Trip. That integration is not available yet.',
              ),
            if (execute &&
                trip['status'] == 'ASSIGNED' &&
                controller.detail?['departurePrerequisitesSatisfied'] == true &&
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
                active?['status'] == 'IN_SERVICE' &&
                active?['stopType'] == 'DROP_HOOK') ...[
              const Text(
                'Drop & Hook: scan and confirm Drop first, then scan the authorized replacement and confirm Hook.',
              ),
              if (coupled != null && capabilities.contains('TRAILER_DROP'))
                FilledButton(
                  onPressed: () => scan(true),
                  child: const Text('Scan coupled Trailer and confirm Drop'),
                ),
            ],
            if (active?['status'] == 'IN_SERVICE' &&
                active?['stopType'] == 'DELIVERY')
              const Text(
                'Delivery completion requires finalized POD. Evidence integration is not available yet.',
              ),
            if (execute &&
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
