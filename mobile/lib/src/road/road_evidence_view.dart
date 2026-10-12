import 'dart:convert';

import 'package:flutter/material.dart';

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
            ),
            for (final stop in stops)
              if (stop['stopType'] == 'DELIVERY' &&
                  !{'COMPLETED', 'CANCELLED'}.contains(stop['status']) &&
                  (trip['status'] == 'AT_STOP' &&
                          stop['status'] == 'IN_SERVICE' ||
                      service.contains(stop['id']))) ...[
                Text(
                  'Entrega: ${stop['locationName'] ?? stop['siteName'] ?? (stop['siteSnapshot'] as Map?)?['name'] ?? 'Destino descargado'}',
                ),
                if (service.contains(stop['id']) &&
                    stop['status'] != 'IN_SERVICE')
                  const Text(
                    'Servicio registrado localmente. POD esperará su confirmación; el estado del Stop no cambió en servidor.',
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
  });
  final ReplayWorker worker;
  final String? tripId, stopId;
  final bool deliveryAvailable;
  final InspectionCamera? camera;
  final bool auxiliaryActions;
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
          () => message = 'No se pudo abrir el formulario. Conservamos el trabajo guardado. Actualiza la sesión.',
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
        const Text('Evidencia y operaciones Road'),
        if (message != null) Text(message!),
        if (widget.tripId != null && canRecord) ...[
          if (widget.deliveryAvailable &&
              caps.contains('POD_UPLOAD') &&
              caps.contains('TRIP_EXECUTE_ASSIGNED')) ...[
            FilledButton(
              onPressed: working ? null : () => open('SIGNED_BOL'),
              child: const Text('Entrega con BOL firmado'),
            ),
            FilledButton(
              onPressed: working ? null : () => open('NO_RECEIVER_AVAILABLE'),
              child: const Text('Entrega sin receptor disponible'),
            ),
          ],
          if (widget.auxiliaryActions && caps.contains('FUEL_EVENT_CREATE'))
            OutlinedButton(
              onPressed: working ? null : () => open('FUEL'),
              child: const Text('Registrar combustible'),
            ),
          if (widget.auxiliaryActions && caps.contains('EXCEPTION_REPORT'))
            OutlinedButton(
              onPressed: working ? null : () => open('PROBLEM'),
              child: const Text('Reportar problema'),
            ),
        ],
        StreamBuilder<List<RoadDraft>>(
          key: ValueKey('${s.userId}|${s.membership.tenantId}'),
          stream: store!.watch(s.userId, s.membership.tenantId),
          builder: (context, snapshot) => Column(
            children: [
              for (final draft in snapshot.data ?? <RoadDraft>[])
                if (widget.tripId == null || draft.tripId == widget.tripId)
                  ListTile(
                    title: Text(switch (draft.purpose) {
                      'SIGNED_BOL' => 'BOL firmado',
                      'NO_RECEIVER_AVAILABLE' => 'Sin receptor',
                      'FUEL' => 'Combustible',
                      _ => 'Problema operativo',
                    }),
                    subtitle: Text(
                      draft.commandId == null
                          ? 'Borrador guardado en este dispositivo'
                          : 'Operación guardada; revisa su confirmación en sincronización',
                    ),
                    trailing: draft.commandId == null
                        ? TextButton(
                            onPressed: canRecord && !working
                                ? () => open(draft.purpose, draft)
                                : null,
                            child: const Text('Continuar'),
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
          () => message = 'Guardado en este dispositivo. Pendiente de confirmación del servidor.',
        );
      }
    } catch (_) {
      if (mounted && widget.store.current(draft)) {
        setState(
          () => message = 'No se pudo completar la acción. Revisa campos y archivos; el borrador se conserva.',
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
        title: const Text('Confirmar intención'),
        content: const Text(
          'Registra solo una acción que ocurrió. El servidor validará la evidencia y la operación; guardar offline no confirma su ejecución.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Guardar operación'),
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
          appBar: AppBar(title: const Text('Evidencia')),
          body: const Text('La sesión cambió. Vuelve al trabajo actual.'),
        );
      }
      final photos = jsonDecode(draft.mediaJson) as Map;
      final fields = switch (draft.purpose) {
        'SIGNED_BOL' => {'bol': 'Fotografiar BOL firmado'},
        'NO_RECEIVER_AVAILABLE' => {
          'bol': 'Fotografiar BOL',
          'deliveryPhoto': 'Fotografiar entrega',
        },
        'FUEL' => {'receipt': 'Fotografiar recibo'},
        _ => {
          'damage0': 'Fotografiar daño',
          'damage1': 'Agregar foto de daño',
          'damage2': 'Agregar otra foto',
          'damage3': 'Agregar última foto',
        },
      };
      return Scaffold(
        appBar: AppBar(
          title: Text(switch (draft.purpose) {
            'FUEL' => 'Combustible',
            'PROBLEM' => 'Reportar problema',
            _ => 'Proof of Delivery',
          }),
        ),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const Text(
              'Datos y fotografías privados. El servidor es la autoridad. No uses este formulario mientras conduces.',
            ),
            if (draft.purpose == 'FUEL') ...[
              Text('Trip seleccionado: ${draft.tripId}'),
              Text(
                'Tractor asignado: ${widget.store.session.snapshot!.driver?.shiftContext?.tractors.where((t) => widget.store.session.snapshot!.trips.any((trip) => trip.id == draft.tripId && trip.data['currentTractorId'] == t.id)).firstOrNull?.number ?? 'revisa el Tractor en el detalle de Trip'}',
              ),
              const Text(
                'Trip y Tractor: asignación descargada; se validarán nuevamente al sincronizar.',
              ),
              TextField(
                controller: odometer,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                decoration: const InputDecoration(labelText: 'Odómetro'),
              ),
            ],
            if (draft.purpose == 'PROBLEM')
              DropdownButtonFormField<String>(
                initialValue: category,
                decoration: const InputDecoration(labelText: 'Categoría'),
                items: [
                  for (final entry in {
                    'DELAY_TRAFFIC': 'Demora por tráfico',
                    'EQUIPMENT_DAMAGE': 'Daño de equipo',
                    'DESTINATION_CUSTOMER_ISSUE': 'Problema en destino',
                    'LOAD_REJECTED': 'Carga rechazada',
                    'OTHER': 'Otro',
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
                  labelText: 'Comentario requerido',
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
                    '${entry.value}${photos.containsKey(entry.key) ? ' · guardada localmente' : ''}',
                  ),
                ),
              ),
            if (message != null)
              Semantics(liveRegion: true, child: Text(message!)),
            if (busy) const LinearProgressIndicator(),
            FilledButton(
              onPressed: busy || draft.commandId != null
                  ? null
                  : () => perform(saved),
              child: const Text('Guardar borrador'),
            ),
            FilledButton(
              onPressed: busy || draft.commandId != null ? null : queue,
              child: const Text('Confirmar y sincronizar'),
            ),
          ],
        ),
      );
    },
  );
}
