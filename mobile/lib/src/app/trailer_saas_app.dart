import 'dart:async';

import 'package:flutter/material.dart';

import '../configuration/app_configuration.dart';
import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../session/session_view.dart';
import '../road/online_command_journal.dart';
import '../sync/command_store.dart';
import '../sync/replay_worker.dart';

class TrailerSaasApp extends StatefulWidget {
  const TrailerSaasApp({
    super.key,
    required this.configuration,
    required this.database,
    this.session,
    this.journal,
  });

  final AppConfiguration configuration;
  final AppDatabase database;
  final SessionController? session;
  final OnlineCommandJournal? journal;

  @override
  State<TrailerSaasApp> createState() => _TrailerSaasAppState();
}

class _TrailerSaasAppState extends State<TrailerSaasApp>
    with WidgetsBindingObserver {
  late Future<void> _databaseReady;
  ReplayWorker? _worker;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _databaseReady = _initialize();
  }

  Future<void> _initialize() async {
    await widget.database.initialize();
    await widget.session?.start();
    if (widget.session != null && widget.journal == null) {
      _worker ??= ReplayWorker(
        widget.session!,
        CommandStore(widget.database),
        FileOnlineCommandJournal(),
      );
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
    if (state == AppLifecycleState.resumed) unawaited(_worker?.sync());
  }

  Future<void> _close() async {
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
                );
        },
      ),
    );
  }
}
