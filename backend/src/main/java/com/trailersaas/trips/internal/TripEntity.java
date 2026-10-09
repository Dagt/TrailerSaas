package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
class TripEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(nullable = false)
  String status;

  @Column(name = "current_driver_id")
  UUID currentDriverId;

  @Column(name = "current_tractor_id")
  UUID currentTractorId;

  @Column(name = "planned_initial_trailer_id")
  UUID plannedInitialTrailerId;

  @Column(name = "started_at")
  Instant startedAt;

  @Column(name = "completed_at")
  Instant completedAt;

  @Column(name = "cancelled_at")
  Instant cancelledAt;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version long version;
}
