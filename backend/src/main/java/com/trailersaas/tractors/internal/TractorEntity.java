package com.trailersaas.tractors.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="tractors")
class TractorEntity {
    @Id UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) UUID tenantId;
    @Column(name="operational_number",nullable=false) String operationalNumber;
    @Column(name="operational_number_normalized",insertable=false,updatable=false) String normalizedNumber;
    @Column(nullable=false) boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @Version @Column(nullable=false) long version;
    protected TractorEntity() {}
    void initialize(UUID tenant, String value) {
        id=UUID.randomUUID(); tenantId=tenant; operationalNumber=value;
        createdAt=updatedAt=Instant.now();
    }
    @PreUpdate void updated() { updatedAt=Instant.now(); }
    void requireVersion(long expected) { if(version!=expected) { throw TractorFailure.stale(); } }
}
