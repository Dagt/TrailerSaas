package com.trailersaas.trailers.internal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="trailer_qr_identities")
class TrailerQrEntity {
    @Id UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) UUID tenantId;
    @Column(name="trailer_id",nullable=false,updatable=false) UUID trailerId;
    @Column(name="public_token",nullable=false,updatable=false) String publicToken;
    @Column(nullable=false) boolean active=true;
    @Column(name="issued_at",nullable=false,updatable=false) Instant issuedAt;
    @Column(name="revoked_at") Instant revokedAt;
    protected TrailerQrEntity() {}
}
