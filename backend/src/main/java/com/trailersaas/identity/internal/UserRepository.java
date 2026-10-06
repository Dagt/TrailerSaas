package com.trailersaas.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface UserRepository extends Repository<UserEntity, UUID> {
    Optional<UserEntity> findByFirebaseUid(String firebaseUid);

    @Modifying
    @Query(value = """
            INSERT INTO users (id, firebase_uid, email, display_name, created_at, updated_at)
            VALUES (:id, :uid, :email, :name, :now, :now)
            ON CONFLICT (firebase_uid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("uid") String uid,
            @Param("email") String email, @Param("name") String name, @Param("now") Instant now);
}
