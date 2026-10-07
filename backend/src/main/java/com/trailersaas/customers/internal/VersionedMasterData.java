package com.trailersaas.customers.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@MappedSuperclass
abstract class VersionedMasterData {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) UUID tenantId;
    @Column(nullable = false) String name;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version @Column(nullable = false) long version;

    void initialize(UUID tenant, String resourceName) {
        id = UUID.randomUUID();
        tenantId = tenant;
        name = resourceName;
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate void updated() { updatedAt = Instant.now(); }

    void requireVersion(long expected) {
        if (version != expected) { throw MasterDataFailure.stale(); }
    }
}
