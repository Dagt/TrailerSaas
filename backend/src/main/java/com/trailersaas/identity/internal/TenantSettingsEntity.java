package com.trailersaas.identity.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "tenant_settings")
class TenantSettingsEntity {
    @Id @Column(name = "tenant_id", updatable = false) UUID tenantId;
    @Column(name = "stale_trailer_threshold_hours", nullable = false) int staleTrailerThresholdHours;
    @Column(name = "default_timezone") String defaultTimezone;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version @Column(nullable = false) long version;
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
