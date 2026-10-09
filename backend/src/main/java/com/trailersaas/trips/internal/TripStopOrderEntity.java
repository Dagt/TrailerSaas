package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "trip_stop_orders")
class TripStopOrderEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "trip_stop_id", nullable = false)
  UUID tripStopId;

  @Column(name = "order_id", nullable = false)
  UUID orderId;

  @Column(name = "order_stop_id")
  UUID orderStopId;
}
