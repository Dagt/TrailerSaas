import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/app/trailer_saas_app.dart';
import 'package:trailersaas_mobile/src/configuration/app_configuration.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';

void main() {
  testWidgets(
    'starts with SQLite ready and navigates Road / Yard placeholders',
    (tester) async {
      final database = AppDatabase(NativeDatabase.memory());
      await tester.runAsync(database.initialize);
      await tester.pumpWidget(
        TrailerSaasApp(
          configuration: AppConfiguration.fromValues(),
          database: database,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('TrailerSaaS'), findsOneWidget);
      expect(find.byKey(const Key('road-mode')), findsOneWidget);
      expect(find.byKey(const Key('yard-mode')), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsNothing);

      await tester.tap(
        find.descendant(
          of: find.byType(NavigationBar),
          matching: find.text('Yard Mode'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('yard-mode')), findsOneWidget);
      expect(find.byKey(const Key('road-mode')), findsNothing);

      await tester.tap(
        find.descendant(
          of: find.byType(NavigationBar),
          matching: find.text('Road Mode'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('road-mode')), findsOneWidget);
      expect(tester.takeException(), isNull);

      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
