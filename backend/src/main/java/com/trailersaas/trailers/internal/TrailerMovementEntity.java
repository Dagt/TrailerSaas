package com.trailersaas.trailers.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "trailer_movement_events")
class TrailerMovementEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "trailer_id", nullable = false)
  UUID trailerId;

  @Column(name = "trip_id")
  UUID tripId;

  @Column(name = "trip_stop_id")
  UUID tripStopId;

  @Column(name = "driver_id")
  UUID driverId;

  @Column(name = "tractor_id")
  UUID tractorId;

  @Column(name = "location_id")
  UUID locationId;

  @Column(name = "event_type", nullable = false)
  String eventType;

  @Column(name = "actor_type", nullable = false)
  String actorType;

  @Column(name = "source_type", nullable = false)
  String sourceType;

  @Column(name = "actor_user_id")
  UUID actorUserId;

  @Column(name = "occurred_at", nullable = false)
  Instant occurredAt;

  @Column(name = "recorded_at", nullable = false)
  Instant recordedAt;

  @Column(name = "client_operation_id")
  UUID clientOperationId;

  @Column(columnDefinition = "text")
  String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  Map<String, Object> metadata;
}
