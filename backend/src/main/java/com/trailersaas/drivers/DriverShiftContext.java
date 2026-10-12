package com.trailersaas.drivers;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Own-driver home enrichment port; implementing workflows depend on Drivers, never the reverse. */
public interface DriverShiftContext {
  record Shift(UUID id,String state,Instant onDutyAt,Instant offDutyAt,Instant createdAt,Instant updatedAt) {}
  record Inspection(UUID id,UUID shiftId,UUID tripId,UUID tractorId,String inspectionType,BigDecimal odometer,Instant occurredAt,Instant createdAt) {}
  record Equipment(UUID tractorId,String operationalNumber,UUID tripId) {}
  record Home(Shift currentShift,List<Inspection> inspections,List<Equipment> authorizedTractors,List<String> offDutyBlockers) {
    public Home { inspections=List.copyOf(inspections);authorizedTractors=List.copyOf(authorizedTractors);offDutyBlockers=List.copyOf(offDutyBlockers); }
  }
  Home read(UUID tenant,UUID ownDriver);
}
