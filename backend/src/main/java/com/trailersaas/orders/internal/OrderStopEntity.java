package com.trailersaas.orders.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "order_stops")
class OrderStopEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "order_id", nullable = false) UUID orderId;
    @Column(name = "sequence_number", nullable = false) int sequenceNumber;
    @Column(name = "site_id") UUID siteId;
    @Column(name = "location_id") UUID locationId;
    @Column(name = "stop_type", nullable = false) String stopType;
    @Column(name = "scheduled_at") Instant scheduledAt;
    @Column(columnDefinition = "text") String instructions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "site_snapshot", columnDefinition = "jsonb") Map<String, Object> siteSnapshot;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version long version;
}
