import 'package:flutter/widgets.dart';

import 'src/app/trailer_saas_app.dart';
import 'src/configuration/app_configuration.dart';
import 'src/persistence/app_database.dart';
import 'src/observability/client_observability.dart';
import 'src/configuration/firebase_configuration.dart';
import 'src/session/auth_gateway.dart';
import 'src/session/api_client.dart';
import 'src/session/session_controller.dart';
import 'src/persistence/session_cache.dart';
import 'src/platform/network_connectivity.dart';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:http/http.dart' as http;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final configuration = AppConfiguration.fromEnvironment();
  await runWithObservability(configuration, () {
    runApp(_Bootstrap(configuration));
  });
}

class _Bootstrap extends StatefulWidget {
  const _Bootstrap(this.configuration);
  final AppConfiguration configuration;
  @override
  State<_Bootstrap> createState() => _BootstrapState();
}

class _BootstrapState extends State<_Bootstrap> {
  late final Future<SessionController> _session;
  final _database = AppDatabase();
  final _http = http.Client();
  @override
  void initState() {
    super.initState();
    _session = _initialize();
  }

  Future<SessionController> _initialize() async {
    final app = await Firebase.initializeApp(
      options: firebaseOptions(widget.configuration),
    );
    final auth = FirebaseAuthGateway(FirebaseAuth.instanceFor(app: app));
    return SessionController(
      auth,
      ApiClient(widget.configuration.apiBaseUrl, auth, _http),
      SessionCache(_database),
      NetworkConnectivity(Connectivity()),
    );
  }

  @override
  void dispose() {
    _http.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<SessionController>(
    future: _session,
    builder: (context, snapshot) {
      if (snapshot.hasData) {
        return TrailerSaasApp(
          configuration: widget.configuration,
          database: _database,
          session: snapshot.data,
        );
      }
      return Directionality(
        textDirection: TextDirection.ltr,
        child: Center(
          child: Text(
            snapshot.hasError
                ? 'Authentication configuration could not be initialized.'
                : 'Initializing authentication…',
          ),
        ),
      );
    },
  );
}
