import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../ui/fleet_widgets.dart';

import '../persistence/app_database.dart';
import '../platform/connectivity.dart';
import '../session/session_controller.dart';
import 'driver_updates_store.dart';

class DriverUpdatesPanel extends StatefulWidget {
  const DriverUpdatesPanel({super.key, required this.store});
  final DriverUpdatesStore store;
  @override
  State<DriverUpdatesPanel> createState() => _UpdatesState();
}

class _UpdatesState extends State<DriverUpdatesPanel> {
  String? message, scope;
  bool busy = false, ready = false;
  StreamSubscription<ConnectivityStatus>? network;
  SessionController get session => widget.store.session;
  @override
  void initState() {
    super.initState();
    session.addListener(changed);
    widget.store.addListener(storedChanged);
    network = session.network.changes.listen((s) {
      if (s == ConnectivityStatus.available) unawaited(refresh());
    });
    changed();
  }

  void changed() {
    final s = session.snapshot,
        next = s == null
            ? null
            : '${s.userId}|${s.membership.tenantId}|${s.driver?.id}';
    final online = session.state == SessionState.ready;
    final fetch = online && (next != scope || !ready);
    if (next != scope) message = null;
    scope = next;
    ready = online;
    if (fetch) unawaited(refresh());
    if (mounted) setState(() {});
  }

  void storedChanged() {
    if (mounted) setState(() {});
  }

  Future<void> refresh() async {
    if (busy || session.state != SessionState.ready) return;
    final owner = scope;
    setState(() => busy = true);
    try {
      await widget.store.refresh();
      if (mounted && scope == owner) setState(() => message = null);
    } catch (_) {
      if (mounted && scope == owner) {
        setState(
          () => message = 'Instructions could not be refreshed. Downloaded instructions were retained; retry.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
      if (mounted && scope != owner && session.state == SessionState.ready) {
        unawaited(refresh());
      }
    }
  }

  @override
  void dispose() {
    session.removeListener(changed);
    widget.store.removeListener(storedChanged);
    unawaited(network?.cancel());
    super.dispose();
  }

  static String label(String type, Map payload) => switch (type) {
    'TRIP_REROUTED' => 'Destination or instruction change',
    'TRIP_CANCELLATION_STARTED' =>
      'Active cancellation: follow the resolution instructions',
    'TRIP_CANCELLED' || 'TRIP_CANCELLATION_FINISHED' => 'Trip cancelled',
    'TRAILER_SUBSTITUTION_AUTHORIZED' => 'Trailer substitution authorized',
    'TRAILER_SUBSTITUTION_REJECTED' => 'Substitution rejected',
    'TRAILER_EXPECTATION_CHANGED' => switch (payload['decision']) {
      'AUTHORIZE' => 'Trailer substitution authorized',
      'REJECT' => 'Substitution rejected',
      _ => 'Expected Trailer changed',
    },
    _ => 'Assignment changed / Trip updated',
  };
  @override
  Widget build(BuildContext context) {
    final s = session.snapshot;
    if (s == null || s.driver == null || !s.membership.road) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const FleetSectionTitle(
          'Operations instructions',
          icon: Icons.notifications_active_outlined,
        ),
        const Text(
          'Downloaded server information. Push notifications are not required to retrieve it.',
        ),
        TextButton.icon(
          onPressed: busy || session.state != SessionState.ready
              ? null
              : refresh,
          icon: const Icon(Icons.refresh),
          label: const Text('Refresh instructions'),
        ),
        if (busy) const LinearProgressIndicator(),
        if (message != null || widget.store.message != null)
          FleetNotice(
            fleetMessage(message ?? widget.store.message!),
            tone: FleetTone.attention,
          ),
        StreamBuilder<List<CachedDriverUpdate>>(
          key: ValueKey(scope),
          stream: widget.store.watch(s.userId, s.membership.tenantId),
          builder: (context, rows) {
            if (rows.hasError) {
              return const Text(
                'Local history could not be read. Retry refresh.',
              );
            }
            if (rows.data?.isEmpty ?? true) {
              return const Text('No downloaded instructions.');
            }
            return Column(
              children: [
                for (final row in rows.data!)
                  Builder(
                    builder: (context) {
                      final item = jsonDecode(row.projectionJson) as Map,
                          payload = item['payload'] as Map;
                      return ExpansionTile(
                        title: Text(label(row.updateType, payload)),
                        subtitle: Text(
                          'Downloaded: ${fleetTime(row.downloadedAt)}',
                        ),
                        children: [
                          if (payload['currentAssignment'] == false)
                            const ListTile(
                              title: Text('Assignment removed'),
                              subtitle: Text(
                                'Review current work and contact Operations. This instruction does not authorize continuing the Trip.',
                              ),
                            ),
                          if (row.updateType == 'TRAILER_EXPECTATION_CHANGED')
                            const ListTile(
                              title: Text('Trailer plan and authorization'),
                              subtitle: Text(
                                'Review the current plan and confirm the QR before Hook. A plan change is not physical coupling.',
                              ),
                            ),
                          if (payload['instructions'] is String)
                            ListTile(
                              title: const Text('Required action'),
                              subtitle: Text(payload['instructions'] as String),
                            ),
                          if (payload['reason'] is String)
                            ListTile(
                              title: const Text('Reason'),
                              subtitle: Text(payload['reason'] as String),
                            ),
                          if (payload['siteSnapshot'] is Map &&
                              (payload['siteSnapshot'] as Map)['name']
                                  is String)
                            ListTile(
                              title: const Text('New destination'),
                              subtitle: Text(
                                (payload['siteSnapshot'] as Map)['name']
                                    as String,
                              ),
                            ),
                          if (payload['status'] is String)
                            ListTile(
                              title: const Text('Reported state'),
                              subtitle: Text(payload['status'] as String),
                            ),
                          const Padding(
                            padding: EdgeInsets.all(12),
                            child: Text(
                              'Review current work before executing. A historical instruction does not grant authorization.',
                            ),
                          ),
                        ],
                      );
                    },
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}
