package com.trailersaas.documents.internal;

import java.time.Instant;
import java.util.*;

/** Closed Customer projections: no actors, free metadata, storage keys or internal equipment. */
final class CustomerReadContracts {
  record Page<T>(UUID customerId,List<T> items,String nextCursor) {}
  record Trailer(UUID id,String operationalNumber) {}
  record Document(UUID customerId,UUID id,String documentType,String uploadStatus,String contentType,long sizeBytes,
      Instant uploadedAt,UUID orderId,String orderNumber,UUID stopId,Instant acceptedAt,String evidenceMode) {}
  record Delivery(UUID customerId,UUID orderId,String orderNumber,String orderStatus,UUID stopId,UUID tripId,
      String destination,Instant scheduledAt,String status,Instant arrivedAt,Instant serviceStartedAt,
      Instant completedAt,Instant departedAt,Trailer expectedTrailer,String evidenceMode,Instant acceptedAt,
      boolean fileAssociationRecorded,List<Document> documents) {}
}
