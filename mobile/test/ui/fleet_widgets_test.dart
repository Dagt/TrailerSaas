import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/ui/fleet_theme.dart';
import 'package:trailersaas_mobile/src/ui/fleet_widgets.dart';

void main() {
  test(
    'display time uses US English, 12-hour time and the actual local offset',
    () {
      final instant = DateTime(2026, 10, 19, 0, 5);
      final before = instant.toIso8601String();
      expect(fleetTime(instant), startsWith('Oct 19, 2026 · 12:05 AM (UTC'));
      expect(fleetTime(instant), matches(RegExp(r'UTC[+-]\d{2}:\d{2}\)$')));
      expect(instant.toIso8601String(), before);
    },
  );
  test(
    'upload and pending finalization never acquire finalized success tone',
    () {
      for (final state in [
        'LOCAL_ONLY',
        'INIT_PENDING',
        'UPLOAD_PENDING',
        'UPLOADING',
        'UPLOADED',
        'FINALIZE_PENDING',
        'FAILED',
        'UNKNOWN',
      ]) {
        expect(mediaTone(state), isNot(FleetTone.success));
      }
      expect(mediaTone('FINALIZED'), FleetTone.success);
    },
  );
  test('Fleet Simple maps approved Web tokens without a second palette', () {
    final theme = fleetTheme();
    expect(theme.scaffoldBackgroundColor, const Color(0xfff5f7fa));
    expect(theme.colorScheme.primary, const Color(0xff2563eb));
    expect(theme.colorScheme.secondary, const Color(0xff0f766e));
    expect(theme.colorScheme.surface, Colors.white);
    expect(theme.materialTapTargetSize, MaterialTapTargetSize.padded);
  });

  testWidgets(
    'mode navigation icons retain contrast in either selected state',
    (tester) async {
      var selected = 0;
      final theme = fleetTheme();
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: StatefulBuilder(
            builder: (context, setState) => Scaffold(
              bottomNavigationBar: NavigationBar(
                selectedIndex: selected,
                onDestinationSelected: (value) =>
                    setState(() => selected = value),
                destinations: const [
                  NavigationDestination(
                    icon: Icon(Icons.local_shipping_outlined),
                    label: 'Road Mode',
                  ),
                  NavigationDestination(
                    icon: Icon(Icons.warehouse_outlined),
                    label: 'Yard Mode',
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      for (final index in [0, 1]) {
        if (index == 1) {
          await tester.tap(find.text('Yard Mode'));
          await tester.pumpAndSettle();
        }
        for (final (position, icon) in [
          (0, Icons.local_shipping_outlined),
          (1, Icons.warehouse_outlined),
        ]) {
          final foreground = IconTheme.of(tester.element(find.byIcon(icon)))
              .color!;
          final background = position == index
              ? theme.navigationBarTheme.indicatorColor!
              : theme.navigationBarTheme.backgroundColor!;
          final a = foreground.computeLuminance(),
              b = background.computeLuminance();
          final contrast =
              (a > b ? a + .05 : b + .05) / (a > b ? b + .05 : a + .05);
          expect(contrast, greaterThanOrEqualTo(3));
        }
      }
    },
  );

  for (final state in [
    'QUEUED',
    'SENDING',
    'RETRYABLE_FAILURE',
    'CONFLICT',
    'BLOCKED',
    'PERMANENT_FAILURE',
    'UNRECOGNIZED',
  ]) {
    test('$state never looks server committed', () {
      expect(commandTone(state), isNot(FleetTone.success));
      expect(
        fleetTone(commandTone(state)).icon,
        isNot(Icons.check_circle_outline),
      );
    });
  }
  test('only an acknowledged command has success presentation', () {
    expect(commandTone('ACKNOWLEDGED'), FleetTone.success);
  });
  for (final tone in FleetTone.values) {
    test('status $tone foreground/background meets 4.5:1 contrast', () {
      final pair = fleetTone(tone);
      final a = pair.text.computeLuminance(),
          b = pair.background.computeLuminance();
      final contrast =
          (a > b ? a + .05 : b + .05) / (a > b ? b + .05 : a + .05);
      expect(contrast, greaterThanOrEqualTo(4.5));
    });
  }
  test(
    'primary action and functional control boundary meet contrast targets',
    () {
      expect(
        (Colors.white.computeLuminance() + .05) /
            (FleetColors.primary.computeLuminance() + .05),
        greaterThanOrEqualTo(4.5),
      );
      expect(
        (Colors.white.computeLuminance() + .05) /
            (FleetColors.control.computeLuminance() + .05),
        greaterThanOrEqualTo(3),
      );
    },
  );

  testWidgets(
    'long safety text, enlarged labels and keyboard inset retain actions',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(320, 640);
      tester.view.viewInsets = const FakeViewPadding(bottom: 240);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      var taps = 0;
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        MaterialApp(
          theme: fleetTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: TextScaler.linear(2),
              disableAnimations: true,
            ),
            child: child!,
          ),
          home: Scaffold(
            body: SafeArea(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: FleetFormBody(
                  children: [
                    const FleetHeader('Review the recorded physical operation'),
                    const FleetNotice(
                      'Saved locally. Waiting for server confirmation.',
                      tone: FleetTone.pending,
                    ),
                    FleetRecord(
                      title: const Text(
                        'Long operational Trailer identifier and recorded location',
                      ),
                      subtitle: const FleetStatus(
                        'Pending synchronization — not server committed',
                        tone: FleetTone.pending,
                      ),
                      action: FilledButton(
                        onPressed: () => taps++,
                        child: const Text('Review retained operation'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.ensureVisible(find.text('Review retained operation'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final button = find.widgetWithText(
        FilledButton,
        'Review retained operation',
      );
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
      await tester.tap(button);
      expect(taps, 1);
      expect(find.byType(FleetStatus), findsOneWidget);
      semantics.dispose();
    },
  );

  testWidgets(
    'keyboard dialog cancellation restores focus without confirming',
    (tester) async {
      final focus = FocusNode();
      addTearDown(focus.dispose);
      var confirmations = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: fleetTheme(),
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                focusNode: focus,
                onPressed: () async {
                  final result = await showDialog<bool>(
                    context: context,
                    builder: (context) => AlertDialog(
                      scrollable: true,
                      title: const Text('Confirm physical operation'),
                      content: const Text(
                        'Saved on device first; await server confirmation.',
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context, false),
                          child: const Text('Not Yet'),
                        ),
                        FilledButton(
                          onPressed: () => Navigator.pop(context, true),
                          child: const Text('Confirm'),
                        ),
                      ],
                    ),
                  );
                  if (result == true) confirmations++;
                },
                child: const Text('Review'),
              ),
            ),
          ),
        ),
      );
      focus.requestFocus();
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(find.text('Confirm physical operation'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(confirmations, 0);
      expect(focus.hasFocus, isTrue);
    },
  );
}
