package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "order_cargo_lines")
class OrderCargoEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "order_id", nullable = false) UUID orderId;
    @Column(nullable = false) String description;
    BigDecimal quantity;
    @Column(name = "pallet_count") Integer palletCount;
    BigDecimal weight;
    @Column(name = "weight_unit") String weightUnit;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}
