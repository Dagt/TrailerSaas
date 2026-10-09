package com.trailersaas.trailers.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trailer_couplings")
class TrailerCouplingEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  UUID tenantId;

  @Column(name = "trailer_id", nullable = false, updatable = false)
  UUID trailerId;

  @Column(name = "tractor_id", nullable = false, updatable = false)
  UUID tractorId;

  @Column(name = "driver_id", nullable = false, updatable = false)
  UUID driverId;

  @Column(name = "trip_id", updatable = false)
  UUID tripId;

  @Column(name = "hooked_at", nullable = false, updatable = false)
  Instant hookedAt;

  @Column(name = "dropped_at")
  Instant droppedAt;

  @Column(name = "hook_event_id", updatable = false)
  UUID hookEventId;

  @Column(name = "drop_event_id")
  UUID dropEventId;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;
}
