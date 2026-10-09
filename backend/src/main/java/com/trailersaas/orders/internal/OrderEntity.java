package com.trailersaas.orders.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "orders")
class OrderEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "customer_id", nullable = false) UUID customerId;
    @Column(name = "order_number", nullable = false) String orderNumber;
    @Column(name = "po_number") String poNumber;
    @Column(name = "shipment_number") String shipmentNumber;
    @Column(name = "bol_reference") String bolReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false) OrderStatus status;
    @Column(name = "created_by_user_id", nullable = false) UUID createdByUserId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version long version;
    @Column(name = "delivered_at") Instant deliveredAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "cancelled_at") Instant cancelledAt;
}
