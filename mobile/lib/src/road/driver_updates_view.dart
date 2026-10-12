import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

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
    network = session.network.changes.listen((s) {
      if (s == ConnectivityStatus.available) unawaited(refresh());
    });
    changed();
  }

  void changed() {
    final s = session.snapshot,
        next = s == null ? null : '${s.userId}|${s.membership.tenantId}';
    final online = session.state == SessionState.ready;
    final fetch = online && (next != scope || !ready);
    if (next != scope) message = null;
    scope = next;
    ready = online;
    if (fetch) unawaited(refresh());
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
          () => message = 'No se pudieron actualizar las instrucciones. Conservamos las descargadas; reintenta.',
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
    unawaited(network?.cancel());
    super.dispose();
  }

  static String label(String type) => switch (type) {
    'TRIP_REROUTED' => 'Cambio de destino / instrucciones',
    'TRIP_CANCELLATION_STARTED' =>
      'Cancelación activa: sigue la resolución indicada',
    'TRIP_CANCELLED' || 'TRIP_CANCELLATION_FINISHED' => 'Trip cancelado',
    'TRAILER_SUBSTITUTION_AUTHORIZED' => 'Sustitución de Trailer autorizada',
    'TRAILER_SUBSTITUTION_REJECTED' => 'Sustitución rechazada',
    _ => 'Cambio de asignación / Trip actualizado',
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
        const Text('Instrucciones de operaciones'),
        const Text(
          'Información descargada del servidor. Las notificaciones push no son necesarias para recuperarla.',
        ),
        TextButton.icon(
          onPressed: busy || session.state != SessionState.ready
              ? null
              : refresh,
          icon: const Icon(Icons.refresh),
          label: const Text('Actualizar instrucciones'),
        ),
        if (busy) const LinearProgressIndicator(),
        if (message != null) Text(message!),
        StreamBuilder<List<CachedDriverUpdate>>(
          key: ValueKey(scope),
          stream: widget.store.watch(s.userId, s.membership.tenantId),
          builder: (context, rows) {
            if (rows.hasError) {
              return const Text(
                'No se pudo leer el historial local. Reintenta actualizar.',
              );
            }
            if (rows.data?.isEmpty ?? true) {
              return const Text('Sin instrucciones descargadas.');
            }
            return Column(
              children: [
                for (final row in rows.data!)
                  Builder(
                    builder: (context) {
                      final item = jsonDecode(row.projectionJson) as Map,
                          payload = item['payload'] as Map;
                      return ExpansionTile(
                        title: Text(label(row.updateType)),
                        subtitle: Text(
                          'Descargado: ${row.downloadedAt.toLocal()}',
                        ),
                        children: [
                          if (payload['instructions'] is String)
                            ListTile(
                              title: const Text('Qué debes hacer'),
                              subtitle: Text(payload['instructions'] as String),
                            ),
                          if (payload['reason'] is String)
                            ListTile(
                              title: const Text('Motivo'),
                              subtitle: Text(payload['reason'] as String),
                            ),
                          if (payload['siteSnapshot'] is Map &&
                              (payload['siteSnapshot'] as Map)['name']
                                  is String)
                            ListTile(
                              title: const Text('Nuevo destino'),
                              subtitle: Text(
                                (payload['siteSnapshot'] as Map)['name']
                                    as String,
                              ),
                            ),
                          if (payload['status'] is String)
                            ListTile(
                              title: const Text('Estado informado'),
                              subtitle: Text(payload['status'] as String),
                            ),
                          const Padding(
                            padding: EdgeInsets.all(12),
                            child: Text(
                              'Consulta tu trabajo actual antes de ejecutar. Una instrucción histórica no concede autorización.',
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
