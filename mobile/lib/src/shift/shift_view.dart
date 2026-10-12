import 'dart:convert';

import 'package:flutter/material.dart';

import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../sync/replay_worker.dart';
import 'contracts.dart';
import 'shift_commands.dart';
import 'inspection_store.dart';
import 'camera_capture.dart';

class ShiftHomePanel extends StatefulWidget {
  const ShiftHomePanel({super.key, required this.worker, this.camera});
  final ReplayWorker worker;
  final InspectionCamera? camera;
  @override
  State<ShiftHomePanel> createState() => _ShiftHomePanelState();
}

class _ShiftHomePanelState extends State<ShiftHomePanel> {
  String? scope, message;
  Stream<List<LocalCommand>>? commands;
  bool busy = false;
  Future<void> act(
    Future<void> Function() action, {
    bool showSaved = true,
  }) async {
    if (busy) return;
    final owner = widget.worker.scope;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      await action();
      if (mounted && owner == widget.worker.scope) {
        if (showSaved) {
          setState(
            () => message = 'Guardado en este dispositivo. Solo la confirmación del servidor completa la acción.',
          );
        }
        await widget.worker.sync();
      }
    } catch (_) {
      if (mounted && owner == widget.worker.scope) {
        setState(
          () => message = 'No se pudo guardar la acción. Los datos y fotografías se conservaron; revisa y reintenta.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> inspection(
    InspectionStore store,
    ShiftReference shift,
    bool pre,
    DriverShiftHome home,
  ) async {
    final source = pre ? home.tractors : await store.usedTractors();
    if (!mounted) return;
    final choices = {
      for (final tractor in source) tractor.id: tractor,
    }.values.toList()..sort((a, b) => a.number.compareTo(b.number));
    if (choices.isEmpty) {
      setState(
        () =>
            message = 'No tienes un Tractor asignado. Contacta a operaciones.',
      );
      return;
    }
    final owner = widget.worker.scope;
    final selected = await showDialog<ShiftTractor>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Confirmar Tractor'),
        children: choices
            .map(
              (t) => SimpleDialogOption(
                onPressed: () => Navigator.pop(context, t),
                child: Text(t.number),
              ),
            )
            .toList(),
      ),
    );
    if (selected == null || !mounted || owner != widget.worker.scope) return;
    await act(() async {
      final draft = await store.create(
        pre ? 'PRE_TRIP' : 'POST_TRIP',
        shift,
        selected.id,
      );
      if (!mounted || owner != widget.worker.scope) return;
      await Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (_) => InspectionScreen(
            worker: widget.worker,
            draft: draft,
            camera: widget.camera,
          ),
        ),
      );
    }, showSaved: false);
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.worker.session,
    builder: (context, _) {
      final worker = widget.worker,
          owner = worker.session.snapshot,
          home = owner?.driver?.shiftContext,
          replay = worker.shifts;
      if (owner == null ||
          home == null ||
          replay == null ||
          worker.session.auth.uid != owner.firebaseUid) {
        return const SizedBox.shrink();
      }
      final next = '${owner.userId}|${owner.membership.tenantId}';
      if (scope != next) {
        scope = next;
        message = null;
        commands = worker.store.watch(owner.userId, owner.membership.tenantId);
      }
      final store = InspectionStore(replay);
      return StreamBuilder<List<LocalCommand>>(
        key: ValueKey(next),
        stream: commands,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return const Text(
              'No se pudieron leer las acciones de Shift. Reintenta sincronización.',
            );
          }
          final rows = snapshot.data ?? [],
              pending = rows
                  .where(
                    (c) =>
                        ShiftCommandIntent.recognizes(c) &&
                        c.state != 'ACKNOWLEDGED',
                  )
                  .toList();
          final starts = pending
              .where((c) => c.commandType == 'on-duty')
              .toList();
          final reference = home.current != null
              ? ShiftReference.server(home.current!.id)
              : starts.isEmpty
              ? null
              : ShiftReference.pending(starts.last.clientOperationId);
          final usable =
              {
                SessionState.ready,
                SessionState.offline,
              }.contains(worker.session.state) &&
              !busy;
          final ends = pending
              .where((c) => c.commandType == 'off-duty')
              .toList();
          final localPost = pending.any((c) => c.commandType == 'post-trip');
          final localPre = pending.any((c) => c.commandType == 'pre-trip');
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Shift / inspecciones',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text(
                    home.current == null
                        ? 'Estado del servidor: Off Duty'
                        : 'Estado del servidor: On Duty · ${home.current!.onDutyAt.toLocal()}',
                  ),
                  if (worker.session.state == SessionState.offline)
                    const Text(
                      'Estado descargado. Sin conexión no se confirma ninguna transición.',
                    ),
                  if (starts.isNotEmpty)
                    const Text(
                      'On Duty registrado localmente — pendiente de confirmación.',
                    ),
                  if (ends.isNotEmpty)
                    const Text(
                      'Off Duty pendiente — el Shift no está cerrado en servidor.',
                    ),
                  if (home.current == null &&
                      starts.isEmpty &&
                      owner.membership.capabilities.contains('SHIFT_START'))
                    FilledButton(
                      onPressed: usable
                          ? () => act(() async {
                              await store.onDuty();
                            })
                          : null,
                      child: const Text('Go On Duty'),
                    ),
                  if (reference != null) ...[
                    if (home.inspections.any(
                      (i) =>
                          i.type == 'PRE_TRIP' &&
                          home.tractors.any(
                            (t) =>
                                t.id == i.tractorId &&
                                (i.tripId == null || i.tripId == t.tripId),
                          ),
                    ))
                      const Text(
                        'Pre-Trip aceptado por servidor para equipo asignado; cada salida revalida su contexto.',
                      )
                    else
                      const Text('Pre-Trip pendiente para el equipo asignado.'),
                    if (pending.any((c) => c.commandType == 'pre-trip'))
                      const Text(
                        'Pre-Trip guardado localmente. Fotos/confirmación pendientes.',
                      ),
                    if (home.tractors.isEmpty)
                      const Text(
                        'No tienes un Tractor asignado. Contacta a operaciones.',
                      ),
                    if (owner.membership.capabilities.contains(
                      'PRETRIP_CREATE',
                    ))
                      OutlinedButton(
                        onPressed: usable && home.tractors.isNotEmpty
                            ? () => inspection(store, reference, true, home)
                            : null,
                        child: const Text('Pre-Trip · cuatro fotografías'),
                      ),
                    if (owner.membership.capabilities.contains(
                      'POSTTRIP_CREATE',
                    ))
                      OutlinedButton(
                        onPressed:
                            usable &&
                                (home.usedTractors.isNotEmpty ||
                                    localPre && home.tractors.isNotEmpty)
                            ? () => inspection(store, reference, false, home)
                            : null,
                        child: const Text('Post-Trip'),
                      ),
                    for (final block in home.offDutyBlockers)
                      Text(
                        block == 'ACTIVE_WORK'
                            ? 'Cierre bloqueado: hay trabajo operativo activo sin resolver.'
                            : 'Cierre bloqueado: falta Post-Trip del equipo utilizado.',
                      ),
                    if (owner.membership.capabilities.contains('SHIFT_END'))
                      OutlinedButton(
                        onPressed:
                            usable &&
                                ends.isEmpty &&
                                (home.offDutyBlockers.isEmpty || localPost)
                            ? () => act(() async {
                                final yes = await showDialog<bool>(
                                  context: context,
                                  builder: (context) => AlertDialog(
                                    title: const Text('Go Off Duty'),
                                    content: const Text(
                                      'El servidor comprobará Post-Trip y trabajo activo. Esta acción no completa Trips ni cambia acoplamientos. Offline queda pendiente.',
                                    ),
                                    actions: [
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(context, false),
                                        child: const Text('Cancelar'),
                                      ),
                                      FilledButton(
                                        onPressed: () =>
                                            Navigator.pop(context, true),
                                        child: const Text('Guardar Off Duty'),
                                      ),
                                    ],
                                  ),
                                );
                                if (yes == true) {
                                  await store.offDuty(reference);
                                  if (mounted) {
                                    setState(
                                      () => message = 'Off Duty guardado localmente; el servidor debe confirmar el cierre.',
                                    );
                                  }
                                }
                              }, showSaved: false)
                            : null,
                        child: const Text('Go Off Duty'),
                      ),
                  ],
                  FutureBuilder<List<InspectionDraft>>(
                    key: ValueKey('$next|${rows.length}|$busy'),
                    future: store.drafts(
                      owner.userId,
                      owner.membership.tenantId,
                    ),
                    builder: (context, s) {
                      if (s.hasError) {
                        return const Text(
                          'No se pudieron leer borradores. Nada fue eliminado.',
                        );
                      }
                      return Column(
                        children: (s.data ?? [])
                            .map(
                              (d) => TextButton(
                                onPressed: usable
                                    ? () => Navigator.of(context).push<void>(
                                        MaterialPageRoute(
                                          builder: (_) => InspectionScreen(
                                            worker: worker,
                                            draft: d,
                                            camera: widget.camera,
                                          ),
                                        ),
                                      )
                                    : null,
                                child: Text(
                                  'Continuar ${d.inspectionType == "PRE_TRIP" ? "Pre-Trip" : "Post-Trip"} guardado',
                                ),
                              ),
                            )
                            .toList(),
                      );
                    },
                  ),
                  if (message != null)
                    Semantics(liveRegion: true, child: Text(message!)),
                ],
              ),
            ),
          );
        },
      );
    },
  );
}

class InspectionScreen extends StatefulWidget {
  const InspectionScreen({
    super.key,
    required this.worker,
    required this.draft,
    this.camera,
  });
  final ReplayWorker worker;
  final InspectionDraft draft;
  final InspectionCamera? camera;
  @override
  State<InspectionScreen> createState() => _InspectionScreenState();
}

class _InspectionScreenState extends State<InspectionScreen> {
  final form = GlobalKey<FormState>();
  late final InspectionStore store;
  late final InspectionCapture capture;
  late final TextEditingController odometer,
      damages,
      failures,
      comments,
      checklist;
  late InspectionDraft draft;
  late final String ownerScope;
  bool busy = false;
  String? message;
  @override
  void initState() {
    super.initState();
    store = InspectionStore(widget.worker.shifts!);
    capture = InspectionCapture(
      store,
      widget.worker.media!.store,
      widget.camera ?? NativeInspectionCamera(),
    );
    draft = widget.draft;
    ownerScope = widget.worker.scope ?? '';
    odometer = TextEditingController(text: draft.odometer);
    damages = TextEditingController(text: draft.damages);
    failures = TextEditingController(text: draft.failures);
    comments = TextEditingController(text: draft.comments);
    final items = (jsonDecode(draft.checklistJson) as Map)['items'] as List;
    checklist = TextEditingController(
      text: items
          .map((i) => (i as Map)['label'] ?? '')
          .where((s) => s.toString().isNotEmpty)
          .join('\n'),
    );
  }

  @override
  void dispose() {
    for (final controller in [
      odometer,
      damages,
      failures,
      comments,
      checklist,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  bool get current =>
      mounted &&
      widget.worker.scope == ownerScope &&
      {
        SessionState.ready,
        SessionState.offline,
      }.contains(widget.worker.session.state);
  Future<void> save() async {
    await store.save(
      draft,
      odometer: odometer.text,
      checklist: {
        'items': checklist.text
            .split('\n')
            .map((s) => s.trim())
            .where((s) => s.isNotEmpty)
            .map((s) => {'label': s, 'reviewed': true})
            .toList(),
      },
      damages: damages.text,
      failures: failures.text,
      comments: comments.text,
    );
    final fresh = await store.find(draft.userId, draft.tenantId, draft.id);
    if (fresh != null && current) setState(() => draft = fresh);
  }

  Future<void> take(String position) async {
    if (busy || !current) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      await save();
      await capture.take(draft, position);
      final fresh = await store.find(draft.userId, draft.tenantId, draft.id);
      if (current && fresh != null) setState(() => draft = fresh);
    } catch (_) {
      if (current) {
        setState(
          () => message = 'La captura no pudo completarse. Los archivos existentes se conservaron. Reintenta esa posición.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> submit() async {
    if (busy || !current || !form.currentState!.validate()) return;
    final pre = draft.inspectionType == 'PRE_TRIP',
        photos = jsonDecode(draft.photosJson) as Map;
    if (pre && InspectionCapture.positions.keys.any((p) => photos[p] == null)) {
      setState(() => message = 'Faltan fotografías obligatorias.');
      return;
    }
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(pre ? 'Guardar Pre-Trip' : 'Guardar Post-Trip'),
        content: const Text(
          'Se conservarán los datos localmente. Solo la aceptación del servidor confirma la inspección; las fotos deben finalizar su carga.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Guardar inspección'),
          ),
        ],
      ),
    );
    if (yes != true || !current) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      await save();
      await store.enqueue(draft);
      if (mounted && current) {
        Navigator.pop(context);
        await widget.worker.sync();
      }
    } catch (_) {
      if (current) {
        setState(
          () => message = 'No se pudo guardar la inspección. Revisa los datos y permisos. Fotografías y borrador se conservaron.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.worker.session,
    builder: (context, _) {
      if (!current) {
        return Scaffold(
          appBar: AppBar(title: const Text('Inspección')),
          body: const Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'La sesión o Tenant cambió. Cierra esta pantalla y vuelve al inicio.',
            ),
          ),
        );
      }
      final pre = draft.inspectionType == 'PRE_TRIP',
          photos = jsonDecode(draft.photosJson) as Map;
      final home = widget.worker.session.snapshot!.driver!.shiftContext;
      final tractors = [
        ...home?.tractors ?? <ShiftTractor>[],
        ...home?.usedTractors ?? <ShiftTractor>[],
      ];
      final label =
          tractors.where((t) => t.id == draft.tractorId).firstOrNull?.number ??
          'Tractor previamente seleccionado — validar asignación al sincronizar';
      return Scaffold(
        appBar: AppBar(title: Text(pre ? 'Pre-Trip' : 'Post-Trip')),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: form,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Tractor: $label'),
                const Text('Realiza esta inspección con el vehículo detenido.'),
                TextFormField(
                  controller: odometer,
                  enabled: !busy,
                  decoration: const InputDecoration(labelText: 'Odómetro'),
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  validator: (v) {
                    final n = num.tryParse(v ?? '');
                    return n == null || !n.isFinite || n < 0
                        ? 'Introduce un odómetro válido.'
                        : null;
                  },
                ),
                if (pre) ...[
                  const Text(
                    'Cuatro fotografías obligatorias — estado local separado de aceptación del servidor.',
                  ),
                  for (final position in InspectionCapture.positions.keys)
                    ListTile(
                      title: Text(switch (position) {
                        'front' => 'Frontal',
                        'rear' => 'Trasera',
                        'driverSide' => 'Lado Driver',
                        _ => 'Lado Passenger',
                      }),
                      subtitle: Text(
                        photos[position] == null ? 'Falta captura' : 'Guardada en este dispositivo — consultar carga en Sincronización',
                      ),
                      trailing: OutlinedButton(
                        onPressed: busy ? null : () => take(position),
                        child: Text(
                          photos[position] == null
                              ? 'Capturar'
                              : 'Volver a capturar',
                        ),
                      ),
                    ),
                  TextFormField(
                    controller: checklist,
                    enabled: !busy,
                    maxLines: 4,
                    maxLength: 4000,
                    decoration: const InputDecoration(
                      labelText: 'Checklist revisado (un punto por línea)',
                    ),
                  ),
                ] else ...[
                  TextFormField(
                    controller: damages,
                    enabled: !busy,
                    maxLines: 3,
                    maxLength: 4000,
                    decoration: const InputDecoration(labelText: 'Daños'),
                  ),
                  TextFormField(
                    controller: failures,
                    enabled: !busy,
                    maxLines: 3,
                    maxLength: 4000,
                    decoration: const InputDecoration(labelText: 'Fallas'),
                  ),
                  TextFormField(
                    controller: comments,
                    enabled: !busy,
                    maxLines: 3,
                    maxLength: 4000,
                    decoration: const InputDecoration(labelText: 'Comentarios'),
                  ),
                  const Text(
                    'Post-Trip no cierra el Shift ni modifica estados de Trailer. No requiere fotografías por defecto.',
                  ),
                ],
                if (message != null)
                  Semantics(liveRegion: true, child: Text(message!)),
                if (busy) const LinearProgressIndicator(),
                TextButton(
                  onPressed: busy
                      ? null
                      : () async {
                          try {
                            await save();
                            if (current) {
                              setState(
                                () => message =
                                    'Borrador guardado en este dispositivo.',
                              );
                            }
                          } catch (_) {
                            if (current) {
                              setState(
                                () => message = 'No se pudo guardar; reintenta antes de cerrar.',
                              );
                            }
                          }
                        },
                  child: const Text('Guardar borrador'),
                ),
                FilledButton(
                  onPressed: busy ? null : submit,
                  child: const Text('Confirmar y guardar operación'),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}
