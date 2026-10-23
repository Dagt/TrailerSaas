import 'package:flutter/material.dart';

/// Fleet Simple Option D, mapped from web/src/shared/ui/tokens.css.
/// These presentation tokens never determine permission or operational state.
abstract final class FleetColors {
  static const canvas = Color(0xfff5f7fa);
  static const surface = Color(0xffffffff);
  static const muted = Color(0xfff8fafc);
  static const text = Color(0xff1e293b);
  static const secondary = Color(0xff475569);
  static const primary = Color(0xff2563eb);
  static const selected = Color(0xffdbeafe);
  static const accent = Color(0xff0f766e);
  static const border = Color(0xffe2e8f0);
  static const control = Color(0xff64748b);
  static const disabled = Color(0xfff1f5f9);
  static const danger = Color(0xff991b1b);
}

ThemeData fleetTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: const ColorScheme.light(
      primary: FleetColors.primary,
      onPrimary: FleetColors.surface,
      secondary: FleetColors.accent,
      onSecondary: FleetColors.surface,
      surface: FleetColors.surface,
      onSurface: FleetColors.text,
      onSurfaceVariant: FleetColors.secondary,
      error: FleetColors.danger,
      onError: FleetColors.surface,
      outline: FleetColors.control,
      outlineVariant: FleetColors.border,
      surfaceContainerLow: FleetColors.muted,
    ),
  );
  final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(8));
  ButtonStyle button({bool primary = false}) => ButtonStyle(
    minimumSize: const WidgetStatePropertyAll(Size(48, 48)),
    padding: const WidgetStatePropertyAll(
      EdgeInsets.symmetric(horizontal: 20, vertical: 14),
    ),
    shape: WidgetStatePropertyAll(shape),
    textStyle: const WidgetStatePropertyAll(
      TextStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.25),
    ),
    foregroundColor: WidgetStateProperty.resolveWith(
      (states) => states.contains(WidgetState.disabled)
          ? FleetColors.secondary
          : primary
          ? FleetColors.surface
          : FleetColors.primary,
    ),
    backgroundColor: primary
        ? WidgetStateProperty.resolveWith(
            (states) => states.contains(WidgetState.disabled)
                ? FleetColors.disabled
                : FleetColors.primary,
          )
        : null,
    side: WidgetStateProperty.resolveWith(
      (states) => states.contains(WidgetState.focused)
          ? const BorderSide(color: FleetColors.accent, width: 3)
          : null,
    ),
  );
  const fieldBorder = OutlineInputBorder(
    borderRadius: BorderRadius.all(Radius.circular(8)),
    borderSide: BorderSide(color: FleetColors.control),
  );
  return base.copyWith(
    scaffoldBackgroundColor: FleetColors.canvas,
    visualDensity: VisualDensity.standard,
    materialTapTargetSize: MaterialTapTargetSize.padded,
    textTheme: base.textTheme.copyWith(
      headlineSmall: const TextStyle(
        fontSize: 26,
        height: 1.2,
        fontWeight: FontWeight.w700,
        color: FleetColors.text,
      ),
      titleLarge: const TextStyle(
        fontSize: 22,
        height: 1.3,
        fontWeight: FontWeight.w700,
        color: FleetColors.text,
      ),
      titleMedium: const TextStyle(
        fontSize: 18,
        height: 1.3,
        fontWeight: FontWeight.w600,
        color: FleetColors.text,
      ),
      bodyLarge: const TextStyle(
        fontSize: 16,
        height: 1.5,
        color: FleetColors.text,
      ),
      bodyMedium: const TextStyle(
        fontSize: 14,
        height: 1.5,
        color: FleetColors.text,
      ),
      bodySmall: const TextStyle(
        fontSize: 14,
        height: 1.4,
        color: FleetColors.secondary,
      ),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: FleetColors.surface,
      foregroundColor: FleetColors.text,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 1,
      elevation: 1,
      shadowColor: FleetColors.border,
      centerTitle: false,
      titleTextStyle: TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        color: FleetColors.text,
      ),
    ),
    cardTheme: CardThemeData(
      color: FleetColors.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: const EdgeInsets.symmetric(vertical: 8),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: FleetColors.border),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(style: button(primary: true)),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: button().copyWith(
        side: WidgetStateProperty.resolveWith(
          (states) => BorderSide(
            color: states.contains(WidgetState.focused)
                ? FleetColors.primary
                : FleetColors.control,
            width: states.contains(WidgetState.focused) ? 3 : 1,
          ),
        ),
      ),
    ),
    textButtonTheme: TextButtonThemeData(style: button()),
    inputDecorationTheme: const InputDecorationTheme(
      filled: true,
      fillColor: FleetColors.surface,
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: fieldBorder,
      enabledBorder: fieldBorder,
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(8)),
        borderSide: BorderSide(color: FleetColors.primary, width: 2),
      ),
      errorMaxLines: 5,
      labelStyle: TextStyle(color: FleetColors.secondary),
      alignLabelWithHint: true,
    ),
    listTileTheme: const ListTileThemeData(
      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      minVerticalPadding: 12,
    ),
    dividerTheme: const DividerThemeData(color: FleetColors.border, space: 24),
    dialogTheme: DialogThemeData(
      backgroundColor: FleetColors.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: FleetColors.surface,
      surfaceTintColor: Colors.transparent,
      indicatorColor: FleetColors.selected,
      iconTheme: WidgetStateProperty.resolveWith(
        (states) => IconThemeData(
          color: states.contains(WidgetState.selected)
              ? FleetColors.primary
              : FleetColors.secondary,
        ),
      ),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => TextStyle(
          fontSize: 14,
          fontWeight: states.contains(WidgetState.selected)
              ? FontWeight.w700
              : FontWeight.w500,
          color: FleetColors.text,
        ),
      ),
    ),
  );
}
