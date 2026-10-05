import 'dart:async';

import 'package:flutter/material.dart';

import '../configuration/app_configuration.dart';
import '../persistence/app_database.dart';
import 'navigation/mode_shell.dart';

class TrailerSaasApp extends StatefulWidget {
  const TrailerSaasApp({
    super.key,
    required this.configuration,
    required this.database,
  });

  final AppConfiguration configuration;
  final AppDatabase database;

  @override
  State<TrailerSaasApp> createState() => _TrailerSaasAppState();
}

class _TrailerSaasAppState extends State<TrailerSaasApp> {
  late Future<void> _databaseReady;

  @override
  void initState() {
    super.initState();
    _databaseReady = widget.database.initialize();
  }

  @override
  void dispose() {
    unawaited(widget.database.close());
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
          return const ModeShell();
        },
      ),
    );
  }
}
