package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "trip_driver_updates")
class TripDriverUpdateEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "trip_id", nullable = false)
  UUID tripId;

  @Column(name = "driver_id")
  UUID driverId;

  @Column(name = "update_type")
  String updateType;

  @Column(name = "occurred_at")
  Instant occurredAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  Map<String, Object> payload;

  @Column(name = "created_at")
  Instant createdAt;
}
