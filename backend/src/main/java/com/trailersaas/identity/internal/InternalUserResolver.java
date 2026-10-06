package com.trailersaas.identity.internal;

import java.time.Instant;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class InternalUserResolver {
    private final UserRepository users;

    InternalUserResolver(UserRepository users) { this.users = users; }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthenticatedUserPrincipal resolve(VerifiedFirebaseIdentity identity) {
        // PostgreSQL unique-index arbitration serializes concurrent first logins.
        // After a competing insert commits, this separate SELECT sees its row.
        users.insertIfAbsent(UUID.randomUUID(), identity.uid(), identity.email(), identity.displayName(), Instant.now());
        return users.findByFirebaseUid(identity.uid()).orElseThrow().principal();
    }
}
