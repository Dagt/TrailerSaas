package com.trailersaas.inspections.internal;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import com.trailersaas.documents.OperationalEvidencePolicy.PreTripPhotos;

final class ShiftContracts {
  enum ExpectedState { ON_DUTY }
  record OnDuty(Instant occurredAt) {}
  record OffDuty(Instant occurredAt,ExpectedState expectedShiftState) {}
  record PreTrip(UUID shiftId,UUID tripId,UUID tractorId,BigDecimal odometer,Map<String,Object> checklist,PreTripPhotos documentIds,Instant occurredAt) {}
  record PostTrip(UUID shiftId,UUID tripId,UUID tractorId,BigDecimal odometer,String damages,String failures,String comments,Instant occurredAt) {}
}
