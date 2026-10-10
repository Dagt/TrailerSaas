import 'package:connectivity_plus/connectivity_plus.dart' as plugin;

import 'connectivity.dart';

class NetworkConnectivity implements Connectivity {
  NetworkConnectivity(this.network);
  final plugin.Connectivity network;
  ConnectivityStatus _status(List<plugin.ConnectivityResult> results) =>
      results.contains(plugin.ConnectivityResult.none)
      ? ConnectivityStatus.unavailable
      : ConnectivityStatus.available;
  @override
  Future<ConnectivityStatus> currentStatus() async =>
      _status(await network.checkConnectivity());
  @override
  Stream<ConnectivityStatus> get changes =>
      network.onConnectivityChanged.map(_status);
}
