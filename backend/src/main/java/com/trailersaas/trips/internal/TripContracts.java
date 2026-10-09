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
      List<Assignment> assignmentHistory,
      List<Operational> operationalHistory) {
    static Detail of(
        TripEntity t, List<Stop> stops, List<Assignment> history, List<Operational> operational) {
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
          history,
          operational);
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
      Map<String, Object> metadata)
      implements HistoryItem {
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

  interface HistoryItem {
    UUID id();

    Instant occurredAt();
  }

  record Operational(
      UUID id,
      String eventType,
      UUID tripStopId,
      UUID actorUserId,
      UUID targetDriverId,
      Instant occurredAt,
      String reason,
      Map<String, Object> metadata)
      implements HistoryItem {
    static Operational of(TripOperationalEventEntity e) {
      return new Operational(
          e.id,
          e.eventType,
          e.tripStopId,
          e.actorUserId,
          e.targetDriverId,
          e.occurredAt,
          e.reason,
          e.metadata);
    }
  }

  record Timeline(List<HistoryItem> items) {}

  record DriverUpdate(
      UUID id, UUID tripId, String updateType, Instant occurredAt, Map<String, Object> payload) {
    static DriverUpdate of(TripDriverUpdateEntity u) {
      return new DriverUpdate(u.id, u.tripId, u.updateType, u.occurredAt, u.payload);
    }
  }

  record DriverUpdates(List<DriverUpdate> items, String nextCursor) {}

  record Reroute(
      long expectedVersion,
      UUID stopId,
      UUID siteId,
      UUID locationId,
      Instant scheduledAt,
      String reason,
      String instructions) {}

  record Cancellation(
      long expectedVersion,
      String reason,
      String resolutionType,
      UUID destinationSiteId,
      String instructions) {}

  record Assign(long expectedVersion, UUID resourceId, String reason) {}

  record Substitution(
      UUID expectedTrailerId, UUID requestedTrailerId, String decision, String reason) {}

  record Page(List<Summary> items, String nextCursor) {}
}
