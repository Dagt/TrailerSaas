import 'dart:async';

import 'package:flutter/material.dart';

import '../configuration/app_configuration.dart';
import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../session/session_view.dart';
import '../road/online_command_journal.dart';
import '../sync/command_store.dart';
import '../sync/replay_worker.dart';
import '../media/media_file_store.dart';
import '../media/media_store.dart';
import '../media/media_processor.dart';
import '../media/documents_client.dart';
import '../shift/shift_commands.dart';
import '../shift/inspection_store.dart';
import '../shift/camera_capture.dart';
import '../road/road_evidence_replay.dart';
import '../road/road_evidence_store.dart';
import '../road/driver_updates_store.dart';
import '../notifications/messaging_port.dart';
import '../notifications/notifications_controller.dart';
import '../notifications/device_registration_store.dart';

class TrailerSaasApp extends StatefulWidget {
  const TrailerSaasApp({
    super.key,
    required this.configuration,
    required this.database,
    this.session,
    this.journal,
    this.messaging,
  });

  final AppConfiguration configuration;
  final AppDatabase database;
  final SessionController? session;
  final OnlineCommandJournal? journal;
  final MessagingPort? messaging;

  @override
  State<TrailerSaasApp> createState() => _TrailerSaasAppState();
}

class _TrailerSaasAppState extends State<TrailerSaasApp>
    with WidgetsBindingObserver {
  late Future<void> _databaseReady;
  ReplayWorker? _worker;
  DriverUpdatesStore? _updates;
  NotificationsController? _notifications;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _databaseReady = _initialize();
  }

  Future<void> _initialize() async {
    await widget.database.initialize();
    await widget.session?.start();
    if (widget.session != null && widget.messaging != null) {
      _notifications ??= NotificationsController(
        widget.session!,
        widget.messaging!,
        FileDeviceRegistrationStore(),
      );
      unawaited(_notifications!.start());
    }
    if (widget.session != null && widget.journal == null) {
      final shiftReplay = ShiftReplay(
        CommandStore(widget.database),
        widget.session!,
      );
      final mediaStore = MediaStore(widget.database, PrivateMediaFileStore());
      _worker ??= ReplayWorker(
        widget.session!,
        CommandStore(widget.database),
        FileOnlineCommandJournal(),
        shifts: shiftReplay,
        futureEvidence: RoadEvidenceReplay(shiftReplay, mediaStore),
        media: MediaProcessor(
          mediaStore,
          DocumentsClient(widget.session!.api),
          R2SignedPutTransport(),
        ),
      );
      await InspectionCapture(
        InspectionStore(shiftReplay),
        _worker!.media!.store,
        NativeInspectionCamera(),
      ).recover();
      await RoadEvidenceStore(_worker!.futureEvidence! as RoadEvidenceReplay)
          .recover();
      _updates ??= DriverUpdatesStore(widget.database, widget.session!);
      _worker!.start();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_close());
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_worker?.sync());
      unawaited(_updates?.refresh().catchError((Object _) {}));
      unawaited(_notifications?.resume());
    } else {
      _notifications?.suspend();
    }
  }

  Future<void> _close() async {
    await _notifications?.shutdown();
    await _updates?.shutdown();
    await _worker?.shutdown();
    await widget.session?.shutdown();
    // No UI survives disposal, and no request/storage details are logged.
    try {
      await widget.database.close();
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TrailerSaaS',
      debugShowCheckedModeBanner: false,
      home: FutureBuilder<void>(
        future: _databaseReady,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return Scaffold(
              appBar: AppBar(title: const Text('TrailerSaaS')),
              body: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('Local storage could not be opened.'),
                    TextButton.icon(
                      onPressed: () {
                        setState(() {
                          _databaseReady = _initialize();
                        });
                      },
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }
          if (snapshot.connectionState != ConnectionState.done) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          return widget.session == null
              ? const Scaffold(
                  body: Center(
                    child: Text('Authentication configuration is required.'),
                  ),
                )
              : SessionView(
                  session: widget.session!,
                  journal: widget.journal,
                  worker: _worker,
                  updates: _updates,
                  notifications: _notifications,
                );
        },
      ),
    );
  }
}
