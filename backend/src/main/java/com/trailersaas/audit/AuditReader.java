package com.trailersaas.audit;

import java.time.Instant;
import java.util.*;

/** Query port for an already authorized, Tenant-pinned transaction; no general HTTP write port. */
public interface AuditReader {
    record Actor(AuditWriter.ActorType type, UUID userId, UUID membershipId) {}
    record Event(UUID id, Actor actor, AuditWriter.SourceType sourceType, AuditWriter.Action actionCode,
        AuditWriter.EntityType entityType, UUID entityId, Map<String,Object> beforeData,
        Map<String,Object> afterData, String reason, Instant occurredAt) {}
    record Page(List<Event> items, String nextCursor) { public Page { items=List.copyOf(items); } }
    Page list(UUID tenant, UUID authenticatedUser, Map<String,String> filters);
    Event detail(UUID tenant, UUID id);
}
