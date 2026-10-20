import 'package:flutter/material.dart';

import 'fleet_theme.dart';

/// Presentation only: retain the instant and expose the device's actual offset.
/// No assumed operating timezone or appointment/command payload conversion.
String fleetTime(DateTime instant) {
  final local = instant.toLocal();
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  final offset = local.timeZoneOffset;
  final sign = offset.isNegative ? '-' : '+';
  final minutes = offset.inMinutes.abs();
  final zone =
      'UTC$sign${(minutes ~/ 60).toString().padLeft(2, '0')}:${(minutes % 60).toString().padLeft(2, '0')}';
  final hour = local.hour % 12 == 0 ? 12 : local.hour % 12;
  return '${months[local.month - 1]} ${local.day}, ${local.year} · $hour:${local.minute.toString().padLeft(2, '0')} ${local.hour < 12 ? 'AM' : 'PM'} ($zone)';
}

String fleetMessage(String message) => switch (message) {
  'No se pudieron actualizar las instrucciones. Conservamos las descargadas; reintenta.' => 'Instructions could not be refreshed. Downloaded instructions were retained; retry.',
  _ => message,
};

enum FleetTone { neutral, success, attention, danger, unknown, pending }

({Color text, Color background, IconData icon}) fleetTone(FleetTone tone) =>
    switch (tone) {
      FleetTone.success => (
        text: const Color(0xff166534),
        background: const Color(0xfff0fdf4),
        icon: Icons.check_circle_outline,
      ),
      FleetTone.attention => (
        text: const Color(0xff92400e),
        background: const Color(0xfffffbeb),
        icon: Icons.warning_amber_rounded,
      ),
      FleetTone.danger => (
        text: FleetColors.danger,
        background: const Color(0xfffef2f2),
        icon: Icons.error_outline,
      ),
      FleetTone.pending => (
        text: const Color(0xff1e40af),
        background: const Color(0xffeff6ff),
        icon: Icons.schedule,
      ),
      FleetTone.unknown => (
        text: FleetColors.secondary,
        background: FleetColors.muted,
        icon: Icons.help_outline,
      ),
      FleetTone.neutral => (
        text: const Color(0xff334155),
        background: FleetColors.disabled,
        icon: Icons.info_outline,
      ),
    };

/// Explicit presentation mapping only. Unknown states never acquire success tone.
FleetTone commandTone(String state) => switch (state) {
  'ACKNOWLEDGED' => FleetTone.success,
  'QUEUED' || 'SENDING' => FleetTone.pending,
  'RETRYABLE_FAILURE' || 'BLOCKED' || 'CONFLICT' => FleetTone.attention,
  'PERMANENT_FAILURE' => FleetTone.danger,
  _ => FleetTone.unknown,
};

FleetTone mediaTone(String state) => switch (state) {
  'FINALIZED' => FleetTone.success,
  'LOCAL_ONLY' ||
  'INIT_PENDING' ||
  'UPLOAD_PENDING' ||
  'UPLOADING' ||
  'UPLOADED' ||
  'FINALIZE_PENDING' => FleetTone.pending,
  'FAILED' => FleetTone.attention,
  _ => FleetTone.unknown,
};

class FleetStatus extends StatelessWidget {
  const FleetStatus(this.label, {super.key, this.tone = FleetTone.neutral});
  final String label;
  final FleetTone tone;
  @override
  Widget build(BuildContext context) {
    final colors = fleetTone(tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: colors.background,
        border: Border.all(color: colors.text),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(colors.icon, size: 18, color: colors.text),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              style: TextStyle(
                color: colors.text,
                fontWeight: FontWeight.w600,
                fontSize: 14,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class FleetNotice extends StatelessWidget {
  const FleetNotice(this.message, {super.key, this.tone = FleetTone.neutral});
  final String message;
  final FleetTone tone;
  @override
  Widget build(BuildContext context) {
    final colors = fleetTone(tone);
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        margin: const EdgeInsets.symmetric(vertical: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: colors.background,
          border: Border(left: BorderSide(color: colors.text, width: 3)),
          borderRadius: BorderRadius.circular(6),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(colors.icon, color: colors.text, size: 22),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                message,
                style: TextStyle(color: colors.text, height: 1.5),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class FleetHeader extends StatelessWidget {
  const FleetHeader(this.title, {super.key, this.eyebrow, this.description});
  final String title;
  final String? eyebrow, description;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 8, bottom: 16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (eyebrow != null) ...[
          Text(
            eyebrow!,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: FleetColors.accent,
            ),
          ),
          const SizedBox(height: 8),
        ],
        Semantics(
          header: true,
          child: Text(title, style: Theme.of(context).textTheme.headlineSmall),
        ),
        if (description != null) ...[
          const SizedBox(height: 8),
          Text(description!, style: Theme.of(context).textTheme.bodySmall),
        ],
      ],
    ),
  );
}

class FleetSectionTitle extends StatelessWidget {
  const FleetSectionTitle(this.title, {super.key, this.icon});
  final String title;
  final IconData? icon;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 16, bottom: 12),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (icon != null) ...[
          Icon(icon, color: FleetColors.accent, size: 22),
          const SizedBox(width: 10),
        ],
        Expanded(
          child: Semantics(
            header: true,
            child: Text(title, style: Theme.of(context).textTheme.titleMedium),
          ),
        ),
      ],
    ),
  );
}

class FleetFact extends StatelessWidget {
  const FleetFact(this.label, this.value, {super.key});
  final String label, value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 2),
        Text(
          value,
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
            height: 1.4,
          ),
        ),
      ],
    ),
  );
}

/// Natural-height event/task row; actions stack so long safety labels survive
/// small phones, keyboard insets and enlarged text without a trailing-width cap.
class FleetRecord extends StatelessWidget {
  const FleetRecord({
    super.key,
    required this.title,
    this.subtitle,
    this.action,
    this.leading,
  });
  final Widget title;
  final Widget? subtitle, action, leading;
  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.symmetric(vertical: 12),
    decoration: const BoxDecoration(
      border: Border(bottom: BorderSide(color: FleetColors.border)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (leading != null) ...[leading!, const SizedBox(width: 12)],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DefaultTextStyle.merge(
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 16,
                    ),
                    child: title,
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 6),
                    subtitle!,
                  ],
                ],
              ),
            ),
          ],
        ),
        if (action != null)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Align(alignment: Alignment.centerLeft, child: action!),
          ),
      ],
    ),
  );
}

class FleetStop extends StatelessWidget {
  const FleetStop(this.stop, {super.key});
  final Map<String, dynamic> stop;
  @override
  Widget build(BuildContext context) => FleetRecord(
    leading: CircleAvatar(
      radius: 18,
      backgroundColor: FleetColors.selected,
      foregroundColor: FleetColors.text,
      child: Text('${stop['sequenceNumber']}'),
    ),
    title: Text(
      '${stop['sequenceNumber']} · ${stop['siteName'] ?? (stop['siteSnapshot'] as Map?)?['name'] ?? 'Operational Stop'}',
    ),
    subtitle: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('${stop['stopType']} · ${stop['status']}'),
        if ((stop['instructions'] ?? '').toString().isNotEmpty)
          Text(stop['instructions'] as String),
      ],
    ),
  );
}

/// Consistent field rhythm without fixed heights or suppressing text scaling.
class FleetFormBody extends StatelessWidget {
  const FleetFormBody({super.key, required this.children});
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      for (final child in children)
        Padding(padding: const EdgeInsets.only(bottom: 16), child: child),
    ],
  );
}

class FleetAccessState extends StatelessWidget {
  const FleetAccessState({
    super.key,
    required this.title,
    required this.message,
    this.loading = false,
    this.action,
  });
  final String title, message;
  final bool loading;
  final Widget? action;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('TrailerSaaS')),
    body: SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: FleetFormBody(
              children: [
                FleetHeader(title, eyebrow: 'TRAILERSAAS'),
                FleetNotice(
                  message,
                  tone: loading ? FleetTone.neutral : FleetTone.attention,
                ),
                if (loading) const Center(child: CircularProgressIndicator()),
                ?action,
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
