import '../road/online_execution_test.dart' show MemoryJournal;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/configuration/app_configuration.dart';
import 'package:trailersaas_mobile/src/configuration/firebase_configuration.dart';
import 'package:trailersaas_mobile/src/session/session_view.dart';
import 'package:trailersaas_mobile/src/platform/connectivity.dart';

import 'fixtures.dart';

void main() {
  testWidgets(
    'SQLite save failure shows safe refresh action and recovers without raw diagnostics',
    (tester) async {
      final f = (await tester.runAsync(() async {
        final fixture = MobileFixture();
        await fixture.db.initialize();
        await fixture.db.customStatement(
          "CREATE TRIGGER fail_widget_cache BEFORE INSERT ON cached_stops BEGIN SELECT RAISE(ABORT,'private-storage-message'); END",
        );
        await fixture.session.start();
        return fixture;
      }))!;
      addTearDown(() => tester.runAsync(f.close));
      await tester.pumpWidget(
        MaterialApp(
          home: SessionView(session: f.session, journal: MemoryJournal()),
        ),
      );
      expect(
        find.textContaining('Local storage could not be accessed'),
        findsOneWidget,
      );
      expect(find.text('Refresh session'), findsOneWidget);
      expect(find.textContaining('private-storage-message'), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      await tester.runAsync(() async {
        await f.db.customStatement('DROP TRIGGER fail_widget_cache');
        await f.session.refresh();
      });
      await tester.pump();
      expect(find.text('Road Mode — Jane Driver'), findsOneWidget);
    },
  );
  testWidgets('sign-in labels and safe validation without external Firebase', (
    tester,
  ) async {
    final f = (await tester.runAsync(() async {
      final fixture = MobileFixture(uid: null);
      await fixture.session.start();
      return fixture;
    }))!;
    addTearDown(() => tester.runAsync(f.close));
    await tester.pumpWidget(
      MaterialApp(
        home: SessionView(session: f.session, journal: MemoryJournal()),
      ),
    );
    expect(find.widgetWithText(TextFormField, 'Email'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Password'), findsOneWidget);
    await tester.tap(find.text('Sign in'));
    await tester.pump();
    expect(find.text('Enter your email.'), findsOneWidget);
    expect(find.text('Enter your password.'), findsOneWidget);
    expect(f.requests, isEmpty);
  });
  testWidgets(
    'Road work uses human destinations and Expected Trailer with no execution buttons',
    (tester) async {
      final f = (await tester.runAsync(() async {
        final fixture = MobileFixture();
        await fixture.session.start();
        return fixture;
      }))!;
      addTearDown(() => tester.runAsync(f.close));
      await tester.pumpWidget(
        MaterialApp(
          home: SessionView(session: f.session, journal: MemoryJournal()),
        ),
      );
      expect(find.text('Road Mode — Jane Driver'), findsOneWidget);
      expect(find.text('Expected Trailer: TR-123'), findsOneWidget);
      expect(find.text('1 · Dock 17'), findsOneWidget);
      for (final command in [
        'Hook',
        'Drop',
        'Depart',
        'Arrive',
        'On Duty',
        'Replay',
      ]) {
        expect(find.text(command), findsNothing);
      }
      expect(find.textContaining('Current Trailer'), findsNothing);
      await tester.tap(find.text('Yard Mode'));
      await tester.pump();
      expect(find.byKey(const Key('yard-mode')), findsOneWidget);
      expect(
        find.text(
          'Synchronization is unavailable. Restart the application to recover saved operations.',
        ),
        findsOneWidget,
      );
    },
  );
  testWidgets(
    'offline cached banner distinguishes server validation from continuity',
    (tester) async {
      final f = (await tester.runAsync(() async {
        final fixture = MobileFixture();
        await fixture.session.start();
        return fixture;
      }))!;
      addTearDown(() => tester.runAsync(f.close));
      f.network.status = ConnectivityStatus.unavailable;
      await tester.runAsync(f.session.refresh);
      await tester.pumpWidget(
        MaterialApp(
          home: SessionView(session: f.session, journal: MemoryJournal()),
        ),
      );
      expect(
        find.text('Offline — cached, not current authorization'),
        findsOneWidget,
      );
      expect(find.text('Connected — server validated'), findsNothing);
      expect(find.text('Change Tenant'), findsNothing);
      expect(find.textContaining('Last refreshed'), findsOneWidget);
    },
  );
  testWidgets('no eligible memberships reveal no cached resources', (
    tester,
  ) async {
    final f = (await tester.runAsync(() async {
      final fixture = MobileFixture();
      fixture.memberships = [];
      await fixture.session.start();
      return fixture;
    }))!;
    addTearDown(() => tester.runAsync(f.close));
    await tester.pumpWidget(
      MaterialApp(
        home: SessionView(session: f.session, journal: MemoryJournal()),
      ),
    );
    expect(find.text('No active mobile access is available.'), findsOneWidget);
    expect(find.text('Expected Trailer: TR-123'), findsNothing);
    expect(find.byType(NavigationBar), findsNothing);
  });
  for (final environment in ['development', 'staging', 'production']) {
    test(
      '$environment requires matching explicit public Firebase configuration',
      () {
        final config = AppConfiguration.fromValues(
          environment: environment,
          apiBaseUrl: 'https://api.example.test/api/v1',
        );
        expect(
          () => checkedFirebaseOptions(
            config,
            environment: 'wrong',
            apiKey: 'public-key',
            appId: 'public-app',
            projectId: 'public-project',
            senderId: '123',
          ),
          throwsArgumentError,
        );
        expect(
          checkedFirebaseOptions(
            config,
            environment: environment,
            apiKey: 'public-key',
            appId: 'public-app',
            projectId: 'public-project',
            senderId: '123',
          ).projectId,
          'public-project',
        );
        expect(
          () => checkedFirebaseOptions(
            config,
            environment: environment,
            apiKey: '',
            appId: '',
            projectId: '',
            senderId: '',
          ),
          throwsArgumentError,
        );
      },
    );
  }
}
