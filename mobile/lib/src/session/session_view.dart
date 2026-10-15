import 'package:flutter/material.dart';

import '../shift/shift_view.dart';

import 'session_controller.dart';
import '../road/road_trip_screen.dart';
import '../road/command_recovery_panel.dart';
import '../road/online_command_journal.dart';
import '../sync/replay_worker.dart';
import '../sync/sync_panel.dart';
import '../road/road_evidence_view.dart';
import '../road/driver_updates_store.dart';
import '../road/driver_updates_view.dart';
import '../yard/yard_view.dart';
import '../platform/qr_scanner.dart';
import '../shift/camera_capture.dart';
import '../notifications/notifications_controller.dart';
import '../notifications/notifications_panel.dart';

class SessionView extends StatefulWidget {
  const SessionView({
    super.key,
    required this.session,
    this.journal,
    this.worker,
    this.updates,
    this.yardScanner,
    this.yardCamera,
    this.notifications,
  });
  final OnlineCommandJournal? journal;
  final SessionController session;
  final ReplayWorker? worker;
  final DriverUpdatesStore? updates;
  final QrScanner? yardScanner;
  final InspectionCamera? yardCamera;
  final NotificationsController? notifications;
  @override
  State<SessionView> createState() => _SessionViewState();
}

class _SessionViewState extends State<SessionView> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  int _mode = 0;
  int _recoveryRevision = 0;
  late final OnlineCommandJournal _journal =
      widget.journal ?? FileOnlineCommandJournal();
  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.session,
    builder: (context, _) {
      final session = widget.session;
      final snapshot = session.snapshot;
      if (session.state == SessionState.loading) {
        return const Scaffold(body: Center(child: CircularProgressIndicator()));
      }
      if (snapshot == null) {
        return Scaffold(
          appBar: AppBar(title: const Text('TrailerSaaS')),
          body: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: ListView(
                shrinkWrap: true,
                padding: const EdgeInsets.all(24),
                children: [
                  if (widget.notifications != null)
                    NotificationsPanel(controller: widget.notifications!),
                  if (session.message != null)
                    Text(session.message!, semanticsLabel: session.message),
                  if (session.traceId != null)
                    Text('Support reference: ${session.traceId}'),
                  if (session.state == SessionState.signedOut)
                    Form(
                      key: _form,
                      child: Column(
                        children: [
                          TextFormField(
                            controller: _email,
                            decoration: const InputDecoration(
                              labelText: 'Email',
                            ),
                            keyboardType: TextInputType.emailAddress,
                            autofillHints: const [AutofillHints.username],
                            validator: (v) => v == null || !v.contains('@')
                                ? 'Enter your email.'
                                : null,
                          ),
                          TextFormField(
                            controller: _password,
                            decoration: const InputDecoration(
                              labelText: 'Password',
                            ),
                            obscureText: true,
                            autofillHints: const [AutofillHints.password],
                            validator: (v) => v == null || v.isEmpty
                                ? 'Enter your password.'
                                : null,
                          ),
                          FilledButton(
                            onPressed: () {
                              if (_form.currentState!.validate()) {
                                final password = _password.text;
                                _password.clear();
                                session.signIn(_email.text.trim(), password);
                              }
                            },
                            child: const Text('Sign in'),
                          ),
                        ],
                      ),
                    ),
                  if (session.state == SessionState.chooseTenant)
                    ...session.user!.memberships.map(
                      (m) => ListTile(
                        title: Text(m.name),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => session.selectTenant(m),
                      ),
                    ),
                  if (session.state != SessionState.signedOut)
                    TextButton(
                      onPressed: session.refresh,
                      child: const Text('Refresh session'),
                    ),
                  if (session.state != SessionState.signedOut)
                    TextButton(
                      onPressed: session.signOut,
                      child: const Text('Sign out'),
                    ),
                ],
              ),
            ),
          ),
        );
      }
      final road = snapshot.membership.road && snapshot.driver != null;
      final yard = snapshot.membership.yard;
      final showYard = yard && (!road || _mode == 1);
      return Scaffold(
        appBar: AppBar(
          title: const Text('TrailerSaaS'),
          actions: [
            IconButton(
              tooltip: 'Refresh',
              onPressed: session.refresh,
              icon: const Icon(Icons.refresh),
            ),
            IconButton(
              tooltip: 'Sign out',
              onPressed: session.signOut,
              icon: const Icon(Icons.logout),
            ),
          ],
        ),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (widget.notifications != null)
                NotificationsPanel(controller: widget.notifications!),
              Text(
                snapshot.membership.name,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              Text(
                session.state == SessionState.offline
                    ? 'Offline — cached, not current authorization'
                    : 'Connected — server validated',
              ),
              Text('Last refreshed: ${snapshot.refreshedAt.toLocal()}'),
              if (session.message != null)
                Semantics(liveRegion: true, child: Text(session.message!)),
              if (session.traceId != null)
                Text('Support reference: ${session.traceId}'),
              if (session.state != SessionState.offline &&
                  (session.user?.memberships.length ?? 0) > 1)
                TextButton(
                  onPressed: () {
                    showModalBottomSheet<void>(
                      context: context,
                      builder: (context) => SafeArea(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: session.user!.memberships
                              .map(
                                (m) => ListTile(
                                  title: Text(m.name),
                                  onTap: () {
                                    Navigator.pop(context);
                                    session.selectTenant(m);
                                  },
                                ),
                              )
                              .toList(),
                        ),
                      ),
                    );
                  },
                  child: const Text('Change Tenant'),
                ),
              if (showYard)
                Column(
                  key: const Key('yard-mode'),
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (widget.worker != null)
                      YardHomePanel(
                        key: ValueKey(
                          'yard-${snapshot.firebaseUid}-${snapshot.userId}-${snapshot.membership.tenantId}-${snapshot.driver?.id}',
                        ),
                        session: session,
                        worker: widget.worker!,
                        scanner: widget.yardScanner,
                        camera: widget.yardCamera,
                      )
                    else
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Yard Mode'),
                          Text(
                            'Synchronization is unavailable. Restart the application to recover saved operations.',
                          ),
                        ],
                      ),
                  ],
                )
              else if (snapshot.membership.alerts && !road && !yard)
                const Text(
                  'Alerts workspace. Open notifications here; browse the complete inbox in the Web application.',
                )
              else
                Column(
                  key: const Key('road-mode'),
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Road Mode — ${snapshot.driver?.name ?? ''}'),
                    if (road && widget.worker?.shifts != null)
                      ShiftHomePanel(
                        key: ValueKey(
                          'shift-${snapshot.userId}-${snapshot.membership.tenantId}',
                        ),
                        worker: widget.worker!,
                      ),
                    if (road && widget.worker == null)
                      CommandRecoveryPanel(
                        key: ValueKey(
                          '${snapshot.firebaseUid}|${snapshot.userId}|${snapshot.membership.tenantId}|$_recoveryRevision',
                        ),
                        session: session,
                        journal: _journal,
                      ),
                    if (road && widget.worker != null)
                      SyncPanel(
                        key: ValueKey(
                          '${snapshot.userId}|${snapshot.membership.tenantId}',
                        ),
                        worker: widget.worker!,
                      ),
                    if (road && widget.updates != null)
                      DriverUpdatesPanel(store: widget.updates!),
                    if (road && widget.worker != null)
                      RoadEvidencePanel(worker: widget.worker!),
                    if (snapshot.trips.isEmpty)
                      const Text('No assigned work downloaded.'),
                    ...snapshot.trips.map(
                      (trip) => Card(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Assigned Trip · ${trip.status}'),
                              TextButton(
                                onPressed: () =>
                                    Navigator.of(context)
                                        .push<void>(
                                          MaterialPageRoute(
                                            builder: (_) => RoadTripScreen(
                                              session: session,
                                              cached: trip,
                                              journal: _journal,
                                              worker: widget.worker,
                                              updates: widget.updates,
                                            ),
                                          ),
                                        )
                                        .then((_) {
                                          if (mounted) {
                                            setState(() => _recoveryRevision++);
                                          }
                                        }),
                                child: const Text('Open assigned Trip'),
                              ),
                              if (trip.plannedTrailer != null)
                                Text(
                                  'Expected Trailer: ${trip.plannedTrailer!['operationalNumber']}',
                                ),
                              ...trip.stops.map(
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
                        ),
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ),
        bottomNavigationBar: road && yard
            ? NavigationBar(
                selectedIndex: _mode,
                onDestinationSelected: (i) => setState(() => _mode = i),
                destinations: const [
                  NavigationDestination(
                    icon: Icon(Icons.local_shipping_outlined),
                    label: 'Road Mode',
                  ),
                  NavigationDestination(
                    icon: Icon(Icons.warehouse_outlined),
                    label: 'Yard Mode',
                  ),
                ],
              )
            : null,
      );
    },
  );
}
