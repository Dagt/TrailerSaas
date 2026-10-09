package com.trailersaas.trips.internal;

import java.time.Instant;
import java.util.*;

final class TripContracts {
  record Create(
      List<UUID> orderIds,
      List<StopInput> stops,
      UUID driverId,
      UUID tractorId,
      UUID plannedInitialTrailerId) {}

  record StopInput(
      int sequenceNumber,
      String stopType,
      UUID siteId,
      UUID locationId,
      Map<String, Object> siteSnapshot,
      Instant scheduledAt,
      List<UUID> orderIds,
      String instructions) {}

  record Summary(
      UUID id,
      String status,
      UUID currentDriverId,
      UUID currentTractorId,
      UUID plannedInitialTrailerId,
      long version,
      Instant createdAt,
      Instant updatedAt,
      Instant startedAt,
      Instant completedAt,
      Instant cancelledAt) {
    static Summary of(TripEntity t) {
      return new Summary(
          t.id,
          t.status,
          t.currentDriverId,
          t.currentTractorId,
          t.plannedInitialTrailerId,
          t.version,
          t.createdAt,
          t.updatedAt,
          t.startedAt,
          t.completedAt,
          t.cancelledAt);
    }
  }

  record Stop(
      UUID id,
      int sequenceNumber,
      String stopType,
      UUID siteId,
      UUID locationId,
      Map<String, Object> siteSnapshot,
      Instant scheduledAt,
      String status,
      Instant arrivedAt,
      Instant serviceStartedAt,
      Instant completedAt,
      Instant departedAt,
      String instructions,
      long version,
      Instant createdAt,
      Instant updatedAt,
      List<UUID> orderIds) {
    static Stop of(TripStopEntity s, List<UUID> orders) {
      return new Stop(
          s.id,
          s.sequenceNumber,
          s.stopType,
          s.siteId,
          s.locationId,
          s.siteSnapshot,
          s.scheduledAt,
          s.status,
          s.arrivedAt,
          s.serviceStartedAt,
          s.completedAt,
          s.departedAt,
          s.instructions,
          s.version,
          s.createdAt,
          s.updatedAt,
          orders);
    }
  }

  record Detail(
      UUID id,
      String status,
      UUID currentDriverId,
      UUID currentTractorId,
      UUID plannedInitialTrailerId,
      long version,
      Instant createdAt,
      Instant updatedAt,
      Instant startedAt,
      Instant completedAt,
      Instant cancelledAt,
      List<Stop> stops,
      List<Assignment> assignmentHistory) {
    static Detail of(TripEntity t, List<Stop> stops, List<Assignment> history) {
      return new Detail(
          t.id,
          t.status,
          t.currentDriverId,
          t.currentTractorId,
          t.plannedInitialTrailerId,
          t.version,
          t.createdAt,
          t.updatedAt,
          t.startedAt,
          t.completedAt,
          t.cancelledAt,
          stops,
          history);
    }
  }

  record Assignment(
      UUID id,
      String eventType,
      UUID driverId,
      UUID tractorId,
      UUID trailerId,
      UUID actorUserId,
      Instant occurredAt,
      String reason,
      Map<String, Object> metadata) {
    static Assignment of(TripAssignmentEventEntity e) {
      return new Assignment(
          e.id,
          e.eventType,
          e.driverId,
          e.tractorId,
          e.trailerId,
          e.actorUserId,
          e.occurredAt,
          e.reason,
          e.metadata);
    }
  }

  record Timeline(List<Assignment> items) {}

  record Assign(long expectedVersion, UUID resourceId, String reason) {}

  record Substitution(
      UUID expectedTrailerId, UUID requestedTrailerId, String decision, String reason) {}

  record Page(List<Summary> items, String nextCursor) {}
}
