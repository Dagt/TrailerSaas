package com.trailersaas.drivers.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="drivers")
class DriverEntity {
    @Id UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) UUID tenantId;
    @Column(name="user_id") UUID userId;
    @Column(nullable=false) String name;
    @Column(name="license_number") String licenseNumber;
    @Column(name="medical_certificate_expires") java.time.LocalDate medicalCertificateExpires;
    @Column(nullable=false) boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @Version @Column(nullable=false) long version;
    protected DriverEntity() {}
    void initialize(UUID tenant, String value) {
        id=UUID.randomUUID(); tenantId=tenant; name=value;
        createdAt=updatedAt=Instant.now();
    }
    @PreUpdate void updated() { updatedAt=Instant.now(); }
    void requireVersion(long expected) { if(version!=expected) { throw DriverFailure.stale(); } }
}
