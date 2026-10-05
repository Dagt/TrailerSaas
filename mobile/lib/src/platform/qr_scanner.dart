/// Returns raw scan data only. Asset resolution belongs to later issues.
abstract interface class QrScanner {
  Future<String?> scan();
}
