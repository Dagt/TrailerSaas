enum ConnectivityStatus { available, unavailable, unknown }

/// An available network link does not guarantee that the API is reachable.
abstract interface class Connectivity {
  Future<ConnectivityStatus> currentStatus();
  Stream<ConnectivityStatus> get changes;
}
