/// Returns raw scan data only. Asset resolution belongs to later issues.
abstract interface class QrScanner {
  Future<String?> scan();
}

abstract interface class CancellableQrScanner implements QrScanner {
  void cancel();
}
