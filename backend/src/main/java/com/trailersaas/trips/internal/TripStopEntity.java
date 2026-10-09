package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trip_stops")
class TripStopEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "trip_id", nullable = false)
  UUID tripId;

  @Column(name = "sequence_number", nullable = false)
  int sequenceNumber;

  @Column(name = "stop_type", nullable = false)
  String stopType;

  @Column(name = "site_id")
  UUID siteId;

  @Column(name = "location_id")
  UUID locationId;

  @Column(name = "scheduled_at")
  Instant scheduledAt;

  @Column(nullable = false)
  String status;

  @Column(name = "arrived_at")
  Instant arrivedAt;

  @Column(name = "service_started_at")
  Instant serviceStartedAt;

  @Column(name = "completed_at")
  Instant completedAt;

  @Column(name = "departed_at")
  Instant departedAt;

  @Column(columnDefinition = "text")
  String instructions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "site_snapshot", columnDefinition = "jsonb")
  Map<String, Object> siteSnapshot;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version long version;
}
