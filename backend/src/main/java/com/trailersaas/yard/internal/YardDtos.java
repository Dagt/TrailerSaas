package com.trailersaas.yard.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class YardDtos {
  record Page<T>(List<T> items, UUID nextCursor) {}
  record Customer(UUID id, String name) {}
  record Location(UUID id, UUID siteId, String name, String type, long version, Customer customer) {}
  record Site(UUID id, String name, String timezone, long version, List<Location> locations, UUID nextLocationCursor) {}
  record RecordedLocation(UUID id, String name, String type, UUID siteId, String siteName, boolean active, boolean siteActive) {}
  record Trailer(UUID trailerId, String operationalNumber, Customer designatedCustomer,
      String locationState, RecordedLocation currentLocation, UUID lastKnownLocationId,
      Instant lastSeenAt, String loadState, String serviceabilityState,
      String couplingState, boolean active, long version) {}
}
