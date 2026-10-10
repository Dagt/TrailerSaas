import 'dart:async';

import 'package:flutter/material.dart';

import '../configuration/app_configuration.dart';
import '../persistence/app_database.dart';
import '../session/session_controller.dart';
import '../session/session_view.dart';

class TrailerSaasApp extends StatefulWidget {
  const TrailerSaasApp({
    super.key,
    required this.configuration,
    required this.database,
    this.session,
  });

  final AppConfiguration configuration;
  final AppDatabase database;
  final SessionController? session;

  @override
  State<TrailerSaasApp> createState() => _TrailerSaasAppState();
}

class _TrailerSaasAppState extends State<TrailerSaasApp> {
  late Future<void> _databaseReady;

  @override
  void initState() {
    super.initState();
    _databaseReady = widget.database.initialize();
    if (widget.session != null) {
      _databaseReady = _databaseReady.then((_) => widget.session!.start());
    }
  }

  @override
  void dispose() {
    unawaited(widget.database.close());
    widget.session?.dispose();
    super.dispose();
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
                          _databaseReady = widget.database.initialize();
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
              : SessionView(session: widget.session!);
        },
      ),
    );
  }
}
