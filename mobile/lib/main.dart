import 'package:flutter/widgets.dart';

import 'src/app/trailer_saas_app.dart';
import 'src/configuration/app_configuration.dart';
import 'src/persistence/app_database.dart';
import 'src/observability/client_observability.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final configuration = AppConfiguration.fromEnvironment();
  await runWithObservability(configuration, () {
    runApp(
      TrailerSaasApp(configuration: configuration, database: AppDatabase()),
    );
  });
}
