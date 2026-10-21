import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/ui/fleet_theme.dart';
import 'package:trailersaas_mobile/src/ui/fleet_widgets.dart';

void main() {
  for (final size in [
    const Size(360, 800),
    const Size(430, 932),
    const Size(844, 390),
  ]) {
    for (final scale in [1.0, 2.0]) {
      testWidgets(
        'workspace context preserves readable truth and reflow at $size / $scale',
        (tester) async {
          tester.view.physicalSize = size;
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          final semantics = tester.ensureSemantics();
          var opened = 0;
          try {
            const offline = 'Offline — cached, not current authorization';
            await tester.pumpWidget(
              MaterialApp(
                theme: fleetTheme(),
                builder: (context, child) => MediaQuery(
                  data: MediaQuery.of(context)
                      .copyWith(textScaler: TextScaler.linear(scale)),
                  child: child!,
                ),
                home: Scaffold(
                  body: SafeArea(
                    child: ListView(
                      padding: const EdgeInsets.all(16),
                      children: [
                        FleetWorkspaceContext(
                          tenant: 'Regional Distribution — Extended Tenant Workspace',
                          message: offline,
                          refreshedAt: DateTime.utc(2026, 10, 19, 12),
                          tone: FleetTone.attention,
                        ),
                        FilledButton(
                          onPressed: () => opened++,
                          child: const Text('Open assigned work'),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
            expect(tester.takeException(), isNull);
            expect(
              find.bySemanticsLabel(RegExp(RegExp.escape(offline))),
              findsOneWidget,
            );
            final contextLabel = tester
                .getSemantics(find.byType(FleetWorkspaceContext))
                .getSemanticsData()
                .label;
            expect(contextLabel, contains('Tenant workspace'));
            expect(contextLabel, contains('Regional Distribution'));
            expect(contextLabel, contains(offline));
            expect(contextLabel, contains('Last refreshed:'));
            expect(find.textContaining('Last refreshed: Oct'), findsOneWidget);
            expect(find.textContaining('Connected'), findsNothing);
            final scroll = find.byType(Scrollable).first;
            await tester.scrollUntilVisible(
              find.byType(FilledButton),
              120,
              scrollable: scroll,
            );
            await tester.ensureVisible(find.byType(FilledButton));
            await tester.pumpAndSettle();
            await tester.tap(find.byType(FilledButton));
            expect(opened, 1);
            expect(tester.takeException(), isNull);
          } finally {
            semantics.dispose();
          }
        },
      );
    }
  }
}
