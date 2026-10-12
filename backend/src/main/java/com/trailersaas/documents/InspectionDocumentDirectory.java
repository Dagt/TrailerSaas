package com.trailersaas.documents;

import java.util.Optional;
import java.util.UUID;

/** Inspection-owned persisted facts behind a Documents-owned port: no module cycle. */
public interface InspectionDocumentDirectory {
  record Context(UUID driverId,UUID userId,UUID tractorId,UUID tripId,String inspectionType,boolean openShift,boolean createdInCurrentTransaction) {}
  Optional<Context> find(UUID tenant,UUID inspectionId);
}
