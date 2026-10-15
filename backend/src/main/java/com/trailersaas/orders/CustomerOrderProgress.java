package com.trailersaas.orders;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.*;

/** Order-authorized execution read SPI. Never returns a general Trip or other allocations. */
public interface CustomerOrderProgress {
    record Stop(UUID id, String stopType, String status, UUID siteId, UUID locationId,
            String destination, Instant scheduledAt, Instant arrivedAt, Instant completedAt, boolean canReroute) {}
    record Trailer(UUID id, String operationalNumber) {}
    record Change(UUID id, String eventType, Instant occurredAt, String reason, String previousDestination,
            String newDestination, String decision) {}
    record Trip(UUID id, String status, long version, Trailer expectedTrailer, boolean canSubstitute,
            List<Stop> stops, List<Change> history) {}
    record Page(List<Trip> items, String nextCursor) {}
    Page read(AuthenticatedUserPrincipal actor, UUID tenant, UUID order, UUID customer, UUID after, int limit);
}
