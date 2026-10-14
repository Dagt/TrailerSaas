package com.trailersaas.yard.internal;

import java.time.Instant;
import java.util.*;

final class YardCheckDtos {
  record Summary(UUID id,UUID siteId,UUID locationId,String status,long version,
      UUID startedByUserId,Instant startedAt,UUID completedByUserId,Instant completedAt,
      UUID cancelledByUserId,Instant cancelledAt,String cancellationReason,
      long expectedCount,long scannedCount,long matchedCount,long missingCount,
      long unexpectedCount,long mismatchCount,long remainingCount,long totalItemCount) {}
  record Item(UUID id,UUID trailerId,boolean expected,UUID expectedLocationId,
      Map<String,Object> recordedSnapshot,boolean found,String result,UUID observedLocationId,
      Instant scannedAt,UUID scannedByUserId,Instant recordedAt,UUID clientOperationId,
      UUID reconciliationId) {}
  record Detail(Summary check,List<Item> items,UUID nextCursor,boolean snapshotComplete) {}
}
