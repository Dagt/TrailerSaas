package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "trip_operational_events")
class TripOperationalEventEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "trip_id", nullable = false)
  UUID tripId;

  @Column(name = "trip_stop_id")
  UUID tripStopId;

  @Column(name = "event_type")
  String eventType;

  @Column(name = "actor_user_id")
  UUID actorUserId;

  @Column(name = "target_driver_id")
  UUID targetDriverId;

  @Column(name = "occurred_at")
  Instant occurredAt;

  @Column(name = "reason")
  String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  Map<String, Object> metadata;
}
