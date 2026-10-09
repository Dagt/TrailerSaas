package com.trailersaas.trips.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.*;

/** Orders no physical effects; the event and current projection share the caller's transaction. */
final class TripAssignmentHistory {
  static void append(
      TripRepository repository,
      TripEntity trip,
      AuthenticatedUserPrincipal actor,
      String type,
      UUID resource,
      String reason,
      Map<String, Object> metadata,
      Instant at) {
    var e = new TripAssignmentEventEntity();
    e.id = UUID.randomUUID();
    e.tenantId = trip.tenantId;
    e.tripId = trip.id;
    e.eventType = type;
    if (type.startsWith("DRIVER_")) {
      e.driverId = resource;
    } else if (type.startsWith("TRACTOR_")) {
      e.tractorId = resource;
    } else {
      e.trailerId = resource;
    }
    e.actorUserId = actor.userId();
    e.reason = reason;
    e.metadata = metadata;
    e.occurredAt = at;
    repository.insert(e);
  }
}
