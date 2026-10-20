import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../ui/fleet_widgets.dart';

import 'qr_scanner.dart';

String? trailerPublicToken(String value) {
  final raw = RegExp(r'^[A-Za-z0-9_-]{22}$');
  if (raw.hasMatch(value)) return value;
  final uri = Uri.tryParse(value);
  if (uri == null ||
      uri.scheme != 'https' ||
      uri.query.isNotEmpty ||
      uri.fragment.isNotEmpty ||
      uri.pathSegments.length < 2) {
    return null;
  }
  final segments = uri.pathSegments;
  if (segments[segments.length - 2] != 't' || !raw.hasMatch(segments.last)) {
    return null;
  }
  // Only the opaque token goes to configured backend. Never fetch a scanned URL.
  return segments.last;
}

class NativeQrScanner implements CancellableQrScanner {
  NativeQrScanner(this.context);
  final BuildContext context;
  MaterialPageRoute<String>? _route;
  NavigatorState? _navigator;
  @override
  Future<String?> scan() async {
    final navigator = Navigator.of(context);
    final route = MaterialPageRoute<String>(builder: (_) => const _ScanPage());
    _navigator = navigator;
    _route = route;
    try {
      return await navigator.push<String>(route);
    } finally {
      if (identical(_route, route)) {
        _route = null;
        _navigator = null;
      }
    }
  }

  @override
  void cancel() {
    final route = _route, navigator = _navigator;
    _route = null;
    _navigator = null;
    if (route != null && navigator != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (navigator.mounted && route.isCurrent && route.isActive) {
          navigator.removeRoute(route);
        }
      });
    }
  }
}

class _ScanPage extends StatefulWidget {
  const _ScanPage();
  @override
  State<_ScanPage> createState() => _ScanPageState();
}

class _ScanPageState extends State<_ScanPage> {
  bool _done = false;
  String? _message;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Scan Trailer QR')),
    body: Column(
      children: [
        Flexible(
          flex: 2,
          child: SingleChildScrollView(
            child: Column(
              children: [
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: FleetHeader(
                    'Identify the Trailer',
                    eyebrow: 'SCAN QR',
                    description: 'Center the Trailer QR label in the camera. Scanning identifies the asset; physical actions still require review and confirmation.',
                  ),
                ),
                if (_message != null)
                  FleetNotice(_message!, tone: FleetTone.attention),
              ],
            ),
          ),
        ),
        Expanded(
          flex: 3,
          child: MobileScanner(
            onDetect: (capture) {
              if (_done) return;
              final values = capture.barcodes
                  .map((b) => b.rawValue)
                  .whereType<String>();
              for (final value in values) {
                final token = trailerPublicToken(value);
                if (token != null) {
                  _done = true;
                  Navigator.pop(context, token);
                  return;
                }
              }
              setState(() => _message = 'Scan a valid Trailer QR label.');
            },
            errorBuilder: (context, error) => const Center(
              child: SingleChildScrollView(
                child: FleetNotice(
                  'Camera access is unavailable. Enable camera permission and try again.',
                  tone: FleetTone.attention,
                ),
              ),
            ),
          ),
        ),
      ],
    ),
  );
}
