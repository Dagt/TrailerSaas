import 'dart:convert';

import '../road/online_command_journal.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import 'command_store.dart';
import 'replay_worker.dart';

/// Records intent against previously downloaded own work. This is continuity,
/// never a local authorization decision or a fabricated server projection.
class RoadCommandCapture {
  RoadCommandCapture(this.session, this.worker);
  final SessionController session;
  final ReplayWorker worker;

  Future<String> record(
    SessionSnapshot owner,
    String tripId,
    String path,
    Map<String, dynamic> body, {
    List<String>? dependencies,
  }) async {
    bool current() =>
        identical(session.snapshot, owner) &&
        session.auth.uid == owner.firebaseUid &&
        session.snapshot?.userId == owner.userId &&
        session.snapshot?.membership.tenantId == owner.membership.tenantId &&
        {SessionState.ready, SessionState.offline}.contains(session.state);
    if (!current() || owner.driver == null) {
      throw StateError('No downloaded Driver context');
    }
    final downloaded = owner.trips.where((t) => t.id == tripId).firstOrNull;
    if (downloaded == null ||
        {'COMPLETED', 'CANCELLED', 'CANCELLING'}.contains(downloaded.status)) {
      throw StateError('Trip is not downloaded executable work');
    }
    final action = path.split('/').last, kind = path.split('/')[2];
    final caps = owner.membership.capabilities;
    if (!caps.contains('TRIP_EXECUTE_ASSIGNED') ||
        (action == 'arrivals' && !caps.contains('ARRIVAL_CONFIRM')) ||
        (action == 'departures' && !caps.contains('DEPARTURE_CONFIRM')) ||
        (action == 'hooks' && !caps.contains('TRAILER_HOOK')) ||
        (action == 'drops' && !caps.contains('TRAILER_DROP'))) {
      throw StateError('Action is not present in the validated context');
    }
    if (kind == 'stops') {
      final stop = downloaded.stops
          .where((s) => s['id'] == path.split('/')[3])
          .firstOrNull;
      if (stop == null ||
          (action == 'completions' && stop['stopType'] == 'DELIVERY')) {
        throw StateError('Stop is not supported downloaded work');
      }
    }
    if (action == 'hooks' &&
        (body['trailerId'] != downloaded.data['plannedInitialTrailerId'] ||
            body['tractorId'] != downloaded.data['currentTractorId'] ||
            body['driverId'] != owner.driver!.id)) {
      throw StateError('Trailer identity/expectation must already be verified');
    }
    if (action == 'drops') {
      final stop = downloaded.stops
          .where((s) => s['id'] == body['stopId'])
          .firstOrNull;
      final coupling =
          (downloaded.data['executionContext'] as Map?)?['currentCoupling']
              as Map?;
      if (stop == null ||
          stop['locationId'] == null ||
          body['locationId'] != stop['locationId'] ||
          coupling == null ||
          coupling['trailerId'] != body['trailerId']) {
        throw StateError(
          'Drop requires downloaded physical coupling and a real Stop Location',
        );
      }
    }
    await worker.store.importLegacy(
      owner.userId,
      owner.membership.tenantId,
      worker.legacy,
    );
    final rows = await worker.store.list(
      owner.userId,
      owner.membership.tenantId,
    );
    if (!current()) throw StateError('Session changed');
    String? preTrip;
    if (kind == 'trips' &&
        action == 'departures' &&
        (downloaded.data['executionContext']
                as Map?)?['departurePrerequisitesSatisfied'] !=
            true) {
      // Pending inspection grants no departure permission. The intent depends on
      // a real accepted inspection, then PostgreSQL revalidates every prerequisite.
      preTrip = rows
          .where((c) {
            if (c.commandType != 'pre-trip' ||
                c.aggregateType != 'INSPECTION' ||
                c.state == 'ACKNOWLEDGED') {
              return false;
            }
            final b = jsonDecode(c.payloadJson) as Map;
            return b['tractorId'] == downloaded.data['currentTractorId'] &&
                (c.tripId == null || c.tripId == tripId);
          })
          .lastOrNull
          ?.clientOperationId;
      if (preTrip == null) {
        throw StateError(
          'First departure requires a matching real or pending Pre-Trip.',
        );
      }
    }
    final related = rows
        .where(
          (c) =>
              c.tripId == tripId && c.state != CommandState.acknowledged.value,
        )
        .lastOrNull;
    final deps = {
      ...?dependencies,
      if (dependencies == null && related != null) related.clientOperationId,
      ?preTrip,
    }.toList();
    final command = OnlineCommand(operationUuid(), tripId, path, body);
    await worker.store.enqueue(
      owner.userId,
      owner.membership.tenantId,
      CommandIntent(
        command,
        dependencies: deps,
        resources: [
          if (downloaded.data['currentTractorId'] != null)
            'tractor:${downloaded.data['currentTractorId']}',
        ],
      ),
      followResources: true,
    );
    // Queue persistence never changes Trip/Stop/Trailer state. A late old-scope
    // write stays in its original namespace and cannot trigger a different User's POST.
    if (current() && session.state == SessionState.ready) worker.sync();
    return command.id;
  }
}
