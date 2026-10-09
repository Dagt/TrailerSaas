package com.trailersaas.trips.internal;

import com.trailersaas.trips.TripDriverUpdateRecorded;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class TripDriverUpdateSink {
  private final TripRepository repository;
  private final ApplicationEventPublisher events;

  TripDriverUpdateSink(TripRepository repository, ApplicationEventPublisher events) {
    this.repository = repository;
    this.events = events;
  }

  void append(TripEntity trip, UUID driver, String type, Instant at, Map<String, Object> payload) {
    if (driver == null) return;
    var row = new TripDriverUpdateEntity();
    row.id = UUID.randomUUID();
    row.tenantId = trip.tenantId;
    row.tripId = trip.id;
    row.driverId = driver;
    row.updateType = type;
    row.occurredAt = at;
    row.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    row.payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    repository.insert(row);
    events.publishEvent(new TripDriverUpdateRecorded(row.tenantId, row.id, row.driverId));
  }

  void assignment(
      TripEntity trip,
      UUID target,
      boolean current,
      String changed,
      String decision,
      Instant at,
      long version) {
    var data = new LinkedHashMap<String, Object>();
    data.put("tripId", trip.id.toString());
    data.put("status", trip.status);
    data.put("currentAssignment", current);
    data.put("changedResource", changed);
    data.put("tripVersion", version);
    // A reassigned-away Driver needs only the removal indication, not the new assignee or future
    // plan.
    if (current) {
      data.put("tractorId", trip.currentTractorId);
      data.put("expectedTrailerId", trip.plannedInitialTrailerId);
      if (decision != null) data.put("decision", decision);
    }
    append(
        trip,
        target,
        changed.equals("TRAILER") ? "TRAILER_EXPECTATION_CHANGED" : "TRIP_ASSIGNMENT_CHANGED",
        at,
        data);
  }
}
