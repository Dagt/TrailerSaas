package com.trailersaas.orders.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "load_manifests")
class LoadManifestEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) UUID tenantId;
    @Column(name = "trailer_id", updatable = false) UUID trailerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) ManifestStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version @Column(nullable = false) long version;
    @Column(name = "closed_at") Instant closedAt;
    protected LoadManifestEntity() {}
}
