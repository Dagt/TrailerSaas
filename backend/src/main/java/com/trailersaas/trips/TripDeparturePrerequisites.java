package com.trailersaas.trips;

import java.util.UUID;

/** #43 supplies fresh On Duty and completed required Pre-Trip evidence in the same transaction. */
public interface TripDeparturePrerequisites {
  boolean satisfied(UUID tenantId, UUID driverId, UUID tractorId, UUID tripId);
}
