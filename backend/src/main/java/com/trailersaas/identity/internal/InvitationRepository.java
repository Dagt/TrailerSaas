package com.trailersaas.identity.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.springframework.stereotype.Repository;

/** Narrow prepared queries on the current JPA transaction/physical connection.
 * Admin reads require Tenant; the sole onboarding lookup requires a token hash.
 * No global browsing, SDK types, raw tokens or generic save/findAll surface.
 */
@Repository
class InvitationRepository {
    private final EntityManager manager;
    InvitationRepository(EntityManager manager) { this.manager = manager; }

    Optional<Invitation> lockPending(UUID tenant, String email) {
        return locked("SELECT * FROM membership_invitations WHERE tenant_id=? AND email_normalized=? AND status='PENDING' FOR UPDATE", tenant, email);
    }
    Optional<Invitation> lockForTenant(UUID tenant, UUID id) {
        return locked("SELECT * FROM membership_invitations WHERE tenant_id=? AND id=? FOR UPDATE", tenant, id);
    }
    Optional<Invitation> lockForOnboardingHash(String hash) {
        return locked("SELECT * FROM membership_invitations WHERE token_hash=? FOR UPDATE", hash);
    }
    private Optional<Invitation> locked(String sql, Object... values) {
        return connection(c -> {
            try (var query = prepared(c, sql, values); var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(row(rows)) : Optional.empty();
            }
        });
    }
    List<Invitation> listForTenant(UUID tenant, InvitationStatus status) {
        return connection(c -> {
            var sql = "SELECT * FROM membership_invitations WHERE tenant_id=?"
                    + (status == null ? "" : " AND status=?") + " ORDER BY created_at DESC, id";
            try (var query = status == null ? prepared(c, sql, tenant) : prepared(c, sql, tenant, status.name());
                    var rows = query.executeQuery()) {
                var result = new ArrayList<Invitation>();
                while (rows.next()) { result.add(row(rows)); }
                return List.copyOf(result);
            }
        });
    }
    List<InvitationContracts.Role> roles(UUID tenant, UUID invitation) {
        return connection(c -> {
            try (var query = prepared(c, """
                    SELECT role_code, scope_type, customer_id FROM invitation_role_assignments
                    WHERE tenant_id=? AND invitation_id=? ORDER BY role_code, scope_type, customer_id
                    """, tenant, invitation); var rows = query.executeQuery()) {
                var result = new ArrayList<InvitationContracts.Role>();
                while (rows.next()) {
                    result.add(new InvitationContracts.Role(RoleCode.valueOf(rows.getString(1)),
                            RoleScopeType.valueOf(rows.getString(2)), rows.getObject(3, UUID.class)));
                }
                return List.copyOf(result);
            }
        });
    }
    void create(Invitation invitation, UUID creator, String hash, List<InvitationContracts.Role> roles) {
        write("""
                INSERT INTO membership_invitations
                (id, tenant_id, email, email_normalized, display_name, token_hash, status, expires_at, created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """, invitation.id(), invitation.tenantId(), invitation.email(), invitation.emailNormalized(),
                invitation.displayName(), hash, invitation.expiresAt(), creator, invitation.createdAt());
        for (var role : roles) {
            write("INSERT INTO invitation_role_assignments VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(),
                    invitation.tenantId(), invitation.id(), role.roleCode().name(), role.scopeType().name(), role.customerId());
        }
    }
    void status(Invitation invitation, InvitationStatus status) {
        write("UPDATE membership_invitations SET status=? WHERE tenant_id=? AND id=?",
                status.name(), invitation.tenantId(), invitation.id());
    }
    Optional<UUID> membershipForUser(UUID tenant, UUID user) {
        return connection(c -> {
            try (var query = prepared(c, "SELECT id FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, user);
                    var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(rows.getObject(1, UUID.class)) : Optional.empty();
            }
        });
    }
    UUID materialize(Invitation invitation, UUID user, Instant now, List<InvitationContracts.Role> roles) {
        UUID membership = UUID.randomUUID();
        write("INSERT INTO tenant_memberships VALUES (?, ?, ?, 'ACTIVE', ?, ?)", membership, invitation.tenantId(), user, now, now);
        for (var role : roles) {
            write("INSERT INTO role_assignments VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(),
                    invitation.tenantId(), membership, role.roleCode().name(), role.scopeType().name(), role.customerId(), now);
        }
        write("""
                UPDATE membership_invitations SET status='ACCEPTED', accepted_by_user_id=?, accepted_at=?
                WHERE tenant_id=? AND id=?
                """, user, now, invitation.tenantId(), invitation.id());
        return membership;
    }
    private void write(String sql, Object... values) {
        connection(c -> { try (var query = prepared(c, sql, values)) { return query.executeUpdate(); } });
    }
    private <T> T connection(ReturningWork<T> work) { return manager.unwrap(Session.class).doReturningWork(work); }
    private static PreparedStatement prepared(Connection connection, String sql, Object... values) throws SQLException {
        var statement = connection.prepareStatement(sql);
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i] instanceof Instant instant ? Timestamp.from(instant) : values[i]);
        }
        return statement;
    }
    private static Invitation row(ResultSet rows) throws SQLException {
        return new Invitation(rows.getObject("id", UUID.class), rows.getObject("tenant_id", UUID.class),
                rows.getString("email"), rows.getString("email_normalized"), rows.getString("display_name"),
                InvitationStatus.valueOf(rows.getString("status")), rows.getTimestamp("expires_at").toInstant(),
                rows.getObject("accepted_by_user_id", UUID.class), rows.getTimestamp("created_at").toInstant());
    }
    // No raw token or hash is retained in result objects.
    record Invitation(UUID id, UUID tenantId, String email, String emailNormalized, String displayName,
            InvitationStatus status, Instant expiresAt, UUID acceptedByUserId, Instant createdAt) {}
}
