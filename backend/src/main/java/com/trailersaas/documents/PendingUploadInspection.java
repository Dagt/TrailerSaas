package com.trailersaas.documents;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Trusted future jobs hook, not HTTP and not deletion authorization. Each call pins its Tenant. */
public interface PendingUploadInspection {
  List<UUID> candidates(UUID tenant,Instant createdBefore,int limit);
  Optional<Eligibility> inspect(UUID tenant,UUID document,Instant createdBefore);
  record Eligibility(UUID documentId,Instant createdAt,boolean temporaryObjectPresent) {}
}
