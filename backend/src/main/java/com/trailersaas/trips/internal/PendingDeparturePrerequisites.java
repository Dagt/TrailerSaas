package com.trailersaas.trips.internal;

import com.trailersaas.trips.TripDeparturePrerequisites;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Fail closed: no fabricated Shift/inspection or configurable production bypass. */
@Component
class PendingDeparturePrerequisites implements TripDeparturePrerequisites {
  public boolean satisfied(UUID tenant, UUID driver, UUID tractor, UUID trip) { return false; }
}
