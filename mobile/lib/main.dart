import 'package:flutter/widgets.dart';

import 'src/app/trailer_saas_app.dart';
import 'src/configuration/app_configuration.dart';
import 'src/persistence/app_database.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    TrailerSaasApp(
      configuration: AppConfiguration.fromEnvironment(),
      database: AppDatabase(),
    ),
  );
}
