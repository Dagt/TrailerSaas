package com.trailersaas.inspections.internal;

import com.trailersaas.documents.OperationalEvidencePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class RoadContracts {
  enum Category { DELAY_TRAFFIC,EQUIPMENT_DAMAGE,DESTINATION_CUSTOMER_ISSUE,LOAD_REJECTED,OTHER }
  record Delivery(OperationalEvidencePolicy.Mode evidenceMode,UUID bolDocumentId,
      UUID deliveryPhotoDocumentId,String comment,Instant occurredAt) {}
  record Fuel(UUID tripId,UUID tractorId,BigDecimal odometer,UUID receiptDocumentId,Instant occurredAt) {}
  record Problem(UUID stopId,Category category,String comment,List<UUID> documentIds,Instant occurredAt) {}
  record FuelResult(UUID id,UUID tripId,UUID tractorId,BigDecimal odometer,UUID receiptDocumentId,
      Instant occurredAt,Instant createdAt) {}
  record ProblemResult(UUID id,UUID tripId,UUID stopId,Category category,String comment,
      List<UUID> documentIds,Instant occurredAt,Instant recordedAt) {}
}
