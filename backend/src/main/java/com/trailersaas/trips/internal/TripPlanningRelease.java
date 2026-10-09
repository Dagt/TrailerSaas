package com.trailersaas.trips.internal;

import com.trailersaas.orders.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class TripPlanningRelease implements OrderPreExecutionPlanningRelease {
  private final TripRepository repository;

  TripPlanningRelease(TripRepository repository) {
    this.repository = repository;
  }

  public void release(UUID tenant, UUID order) {
    for (var id : repository.tripsForOrder(tenant, order)) {
      var trip = repository.resource(tenant, id, true);
      // A sibling cancellation may have released its own mappings, never this locked Order's
      // mappings.
      if (!trip.status.equals("PLANNED") && !trip.status.equals("ASSIGNED")) {
        throw new OrderPlanningRejectedException(OrderPlanningRejectedException.Reason.CONFLICT);
      }
      repository.releaseMappings(tenant, id, order);
      var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
      trip.updatedAt =
          now.isAfter(trip.updatedAt) ? now : trip.updatedAt.plus(1, ChronoUnit.MICROS);
    }
    repository.flush();
  }
}
