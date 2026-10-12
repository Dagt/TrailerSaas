package com.trailersaas.inspections;
import java.time.Instant;
import java.util.UUID;
public record DriverWentOnDuty(UUID tenantId,UUID driverId,UUID shiftId,UUID actorUserId,Instant occurredAt) {}
