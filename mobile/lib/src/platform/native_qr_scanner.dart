import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

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

class NativeQrScanner implements QrScanner {
  NativeQrScanner(this.context);
  final BuildContext context;
  @override
  Future<String?> scan() =>
      Navigator.of(context)
          .push<String>(MaterialPageRoute(builder: (_) => const _ScanPage()));
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
        if (_message != null) Text(_message!),
        Expanded(
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
              child: Text(
                'Camera access is unavailable. Enable camera permission and try again.',
              ),
            ),
          ),
        ),
      ],
    ),
  );
}
