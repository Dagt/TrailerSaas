package com.trailersaas.orders.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Immutable @Table(name="order_request_revisions")
class OrderRequestRevisionEntity {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="order_id") UUID orderId;
    @Column(name="actor_user_id") UUID actorUserId;
    @Column(name="order_version") long orderVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="previous_request",columnDefinition="jsonb") Map<String,Object> previous;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="replacement_request",columnDefinition="jsonb") Map<String,Object> replacement;
    @Column(name="recorded_at") Instant recordedAt;
}
