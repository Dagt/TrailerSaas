package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "trip_assignment_events")
class TripAssignmentEventEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "trip_id", nullable = false)
  UUID tripId;

  @Column(name = "event_type", nullable = false)
  String eventType;

  @Column(name = "driver_id")
  UUID driverId;

  @Column(name = "tractor_id")
  UUID tractorId;

  @Column(name = "trailer_id")
  UUID trailerId;

  @Column(name = "actor_user_id")
  UUID actorUserId;

  @Column(name = "occurred_at", nullable = false)
  Instant occurredAt;

  @Column(columnDefinition = "text")
  String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  Map<String, Object> metadata;
}
