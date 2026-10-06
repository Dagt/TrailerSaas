package com.trailersaas.identity.internal;

import java.time.Instant;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
class UserEntity {
    @Id
    private UUID id;
    @Column(name = "firebase_uid", nullable = false, unique = true, updatable = false)
    private String firebaseUid;
    private String email;
    @Column(name = "display_name")
    private String displayName;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {}

    AuthenticatedUserPrincipal principal() {
        return new AuthenticatedUserPrincipal(id, firebaseUid, email, displayName);
    }
}
