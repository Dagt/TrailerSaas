package com.trailersaas.trailers.internal;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.*;

/** Read-only cross-domain display projections; never command bodies or persistence entities. */
final class TrailerReadDtos {
  private TrailerReadDtos() {}

  record Items(List<Item> items) {}

  record Named(UUID id, String name) {}

  record Numbered(UUID id, String operationalNumber) {}

  record Location(UUID id, String name, String type, UUID siteId, String siteName) {}

  record Coupling(
      UUID id,
      UUID driverId,
      String driverName,
      UUID tractorId,
      String tractorOperationalNumber,
      UUID tripId,
      Instant hookedAt,
      Instant droppedAt) {}

  record Item(
      @JsonUnwrapped TrailerDtos.Trailer trailer,
      String designatedCustomerName,
      String currentLocationName,
      String lastKnownLocationName,
      String couplingState,
      UUID currentDriverId,
      String currentDriverName,
      UUID currentTractorId,
      String currentTractorOperationalNumber,
      String lastDriverName,
      UUID lastTractorId,
      String lastTractorOperationalNumber) {}

  record PlannedOrder(
      UUID id, UUID customerId, String customerName, String orderNumber, String status) {}

  record Manifest(
      UUID id,
      String status,
      long version,
      Instant updatedAt,
      List<PlannedOrder> orders,
      boolean ordersTruncated) {}

  record Detail(
      @JsonUnwrapped Item projection,
      Named designatedCustomer,
      Location currentLocation,
      Location lastKnownLocation,
      Named lastDriver,
      Numbered currentTractor,
      Numbered lastTractor,
      Coupling currentCoupling,
      Coupling lastCoupling,
      List<Manifest> openManifests,
      boolean manifestSummaryTruncated) {}

  record TimelineItem(
      UUID id,
      String eventType,
      String actorType,
      String sourceType,
      Instant occurredAt,
      Instant recordedAt,
      String reason,
      UUID locationId,
      String locationName,
      UUID driverId,
      String driverName,
      UUID tractorId,
      String tractorOperationalNumber,
      UUID tripId,
      UUID tripStopId,
      Map<String, Object> changes) {}

  record TimelinePage(List<TimelineItem> items, String nextCursor) {}
}
