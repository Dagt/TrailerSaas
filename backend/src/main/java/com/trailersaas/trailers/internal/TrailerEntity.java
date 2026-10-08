package com.trailersaas.trailers.internal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="trailers")
class TrailerEntity {
    @Id UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) UUID tenantId;
    @Column(name="operational_number",nullable=false) String operationalNumber;
    @Column(name="operational_number_normalized",insertable=false,updatable=false) String normalizedNumber;
    @Column(name="designated_customer_id",updatable=false) UUID designatedCustomerId;
    @Column(name="trailer_type") String trailerType;
    @Column(name="capability_notes",columnDefinition="text") String capabilityNotes;
    @Column(name="location_state",nullable=false) String locationState="LOCATION_UNKNOWN";
    @Column(name="current_location_id") UUID currentLocationId;
    @Column(name="last_known_location_id") UUID lastKnownLocationId;
    @Column(name="last_seen_at") Instant lastSeenAt;
    @Column(name="last_driver_id") UUID lastDriverId;
    @Column(name="load_state",nullable=false) String loadState="UNKNOWN";
    @Column(name="serviceability_state",nullable=false) String serviceabilityState="IN_SERVICE";
    @Column(name="usage_purpose") String usagePurpose;
    @Column(nullable=false) boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @Version @Column(nullable=false) long version;
    protected TrailerEntity() {}
    void initialize(UUID tenant,String number) {
        id=UUID.randomUUID(); tenantId=tenant; operationalNumber=number;
        createdAt=updatedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    @PreUpdate void updated() { updatedAt=Instant.now().truncatedTo(ChronoUnit.MICROS); }
    void requireVersion(long expected) { if(version!=expected) { throw TrailerFailure.stale(); } }
}
