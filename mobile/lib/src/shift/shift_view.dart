import 'dart:convert';

import 'package:flutter/material.dart';

import '../ui/fleet_widgets.dart';

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
            () => message = 'Saved on this device. Only server confirmation completes the action.',
          );
        }
        await widget.worker.sync();
      }
    } catch (_) {
      if (mounted && owner == widget.worker.scope) {
        setState(
          () => message = 'The action could not be saved. Data and photos were retained; review and retry.',
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
        () => message = 'No Tractor is assigned to you. Contact Operations.',
      );
      return;
    }
    final owner = widget.worker.scope;
    final selected = await showDialog<ShiftTractor>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Confirm Tractor'),
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
              'Shift actions could not be read. Retry synchronization.',
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
                  const FleetSectionTitle(
                    'Shift and inspections',
                    icon: Icons.fact_check_outlined,
                  ),
                  Text(
                    home.current == null
                        ? 'Server state: Off Duty'
                        : 'Server state: On Duty · ${fleetTime(home.current!.onDutyAt)}',
                  ),
                  if (worker.session.state == SessionState.offline)
                    const Text(
                      'Downloaded state. No transition is confirmed while offline.',
                    ),
                  if (starts.isNotEmpty)
                    const Text(
                      'On Duty recorded locally — awaiting confirmation.',
                    ),
                  if (ends.isNotEmpty)
                    const Text(
                      'Off Duty pending — the server has not closed the Shift.',
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
                  if (owner.membership.capabilities.contains(
                    'YARD_CHECK_EXECUTE',
                  ))
                    const Text(
                      'Yard Check: a pending or uncertain associated Check blocks Off Duty. Refresh Checks before closing; offline cache does not prove there is no open work.',
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
                        'Pre-Trip accepted by the server for assigned equipment; each departure revalidates its context.',
                      )
                    else
                      const Text('Pre-Trip pending for assigned equipment.'),
                    if (pending.any((c) => c.commandType == 'pre-trip'))
                      const Text(
                        'Pre-Trip saved locally. Photos and confirmation are pending.',
                      ),
                    if (home.tractors.isEmpty)
                      const Text(
                        'No Tractor is assigned to you. Contact Operations.',
                      ),
                    if (owner.membership.capabilities.contains(
                      'PRETRIP_CREATE',
                    ))
                      OutlinedButton(
                        onPressed: usable && home.tractors.isNotEmpty
                            ? () => inspection(store, reference, true, home)
                            : null,
                        child: const Text('Pre-Trip · four photos'),
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
                            ? 'Closing blocked: active operational work remains unresolved.'
                            : 'Closing blocked: Post-Trip is missing for equipment used.',
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
                                    scrollable: true,
                                    title: const Text('Go Off Duty'),
                                    content: const Text(
                                      'The server checks Post-Trip and active work. This action does not complete Trips or change couplings. Offline requests remain pending.',
                                    ),
                                    actions: [
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(context, false),
                                        child: const Text('Cancel'),
                                      ),
                                      FilledButton(
                                        onPressed: () =>
                                            Navigator.pop(context, true),
                                        child: const Text('Save Off Duty'),
                                      ),
                                    ],
                                  ),
                                );
                                if (yes == true) {
                                  await store.offDuty(reference);
                                  if (mounted) {
                                    setState(
                                      () => message = 'Off Duty saved locally; the server must confirm closure.',
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
                          'Drafts could not be read. Nothing was deleted.',
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
                                  'Resume saved ${d.inspectionType == "PRE_TRIP" ? "Pre-Trip" : "Post-Trip"}',
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
          () => message = 'Capture could not be completed. Existing files were retained. Retry that position.',
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
      setState(() => message = 'Required photos are missing.');
      return;
    }
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        title: Text(pre ? 'Save Pre-Trip' : 'Save Post-Trip'),
        content: const Text(
          'Data will be retained locally. Only server acceptance confirms the inspection; photo uploads must be finalized.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Save inspection'),
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
          () => message = 'The inspection could not be saved. Review data and permissions. Photos and the draft were retained.',
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
          appBar: AppBar(title: const Text('Inspection')),
          body: const Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'The session or Tenant changed. Close this screen and return home.',
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
          'Previously selected Tractor — validate assignment on sync';
      return Scaffold(
        appBar: AppBar(title: Text(pre ? 'Pre-Trip' : 'Post-Trip')),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: form,
            child: FleetFormBody(
              children: [
                FleetHeader(
                  pre ? 'Before you drive' : 'End-of-shift inspection',
                  eyebrow: pre ? 'PRE-TRIP' : 'POST-TRIP',
                  description: 'Tractor: $label',
                ),
                const Text(
                  'Perform this inspection while the vehicle is stopped.',
                ),
                TextFormField(
                  controller: odometer,
                  enabled: !busy,
                  decoration: const InputDecoration(labelText: 'Odometer'),
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  validator: (v) {
                    final n = num.tryParse(v ?? '');
                    return n == null ||
                            !n.isFinite ||
                            n < 0 ||
                            n > 1000000000000 ||
                            !RegExp(r'^\d+(\.\d{1,3})?$').hasMatch(v ?? '')
                        ? 'Enter a valid odometer reading.'
                        : null;
                  },
                ),
                if (pre) ...[
                  const FleetSectionTitle(
                    'Four required photos — local capture is separate from server acceptance.',
                    icon: Icons.photo_camera_outlined,
                  ),
                  for (final position in InspectionCapture.positions.keys)
                    FleetRecord(
                      title: Text(switch (position) {
                        'front' => 'Front',
                        'rear' => 'Rear',
                        'driverSide' => 'Driver side',
                        _ => 'Passenger side',
                      }),
                      subtitle: Text(
                        photos[position] == null ? 'Not captured' : 'Saved on this device — check upload in Synchronization',
                      ),
                      action: OutlinedButton(
                        onPressed: busy ? null : () => take(position),
                        child: Text(
                          photos[position] == null ? 'Capture' : 'Retake',
                        ),
                      ),
                    ),
                  TextFormField(
                    controller: checklist,
                    enabled: !busy,
                    maxLines: 4,
                    maxLength: 4000,
                    decoration: const InputDecoration(
                      labelText: 'Reviewed checklist (one item per line)',
                    ),
                    validator: (v) {
                      final points = (v ?? '')
                          .split('\n')
                          .map((s) => s.trim())
                          .where((s) => s.isNotEmpty)
                          .toList();
                      return points.length > 100 ||
                              points.any((s) => s.length > 2000)
                          ? 'Maximum 100 items; 2,000 characters per item.'
                          : null;
                    },
                  ),
                ] else ...[
                  TextFormField(
                    controller: damages,
                    enabled: !busy,
                    maxLines: 3,
                    maxLength: 4000,
                    decoration: const InputDecoration(labelText: 'Damage'),
                  ),
                  TextFormField(
                    controller: failures,
                    enabled: !busy,
                    maxLines: 3,
                    maxLength: 4000,
                    decoration: const InputDecoration(labelText: 'Failures'),
                  ),
                  TextFormField(
                    controller: comments,
                    enabled: !busy,
                    maxLines: 3,
                    maxLength: 4000,
                    decoration: const InputDecoration(labelText: 'Comments'),
                  ),
                  const Text(
                    'Post-Trip does not close the Shift or change Trailer states. Photos are not required by default.',
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
                                () => message = 'Draft saved on this device.',
                              );
                            }
                          } catch (_) {
                            if (current) {
                              setState(
                                () => message =
                                    'Could not save; retry before closing.',
                              );
                            }
                          }
                        },
                  child: const Text('Save draft'),
                ),
                FilledButton(
                  onPressed: busy ? null : submit,
                  child: const Text('Confirm and save operation'),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}
