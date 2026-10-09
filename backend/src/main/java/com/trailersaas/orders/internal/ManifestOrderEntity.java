package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "manifest_orders")
class ManifestOrderEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) UUID tenantId;
    @Column(name = "manifest_id", nullable = false, updatable = false) UUID manifestId;
    @Column(name = "order_id", nullable = false, updatable = false) UUID orderId;
    @Column(name = "allocated_quantity", columnDefinition = "numeric", updatable = false) BigDecimal allocatedQuantity;
    @Column(name = "allocated_pallet_count", updatable = false) Integer allocatedPalletCount;
    @Column(name = "allocated_weight", columnDefinition = "numeric", updatable = false) BigDecimal allocatedWeight;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    protected ManifestOrderEntity() {}
}
