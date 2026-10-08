package com.trailersaas.identity.internal;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.TenantContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class PostgresRlsContext implements TenantContext {
    private final EntityManager entityManager;

    PostgresRlsContext(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    public void establishTenant(UUID tenantId) {
        establish(Objects.requireNonNull(tenantId, "Tenant is required"), null);
    }

    @Override
    public void establishBootstrapUser(AuthenticatedUserPrincipal principal) {
        Objects.requireNonNull(principal, "Authenticated User is required");
        establish(null, Objects.requireNonNull(principal.userId(), "Internal User is required"));
    }

    private void establish(UUID tenantId, UUID userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !entityManager.isJoinedToTransaction()) {
            throw new IllegalStateException("RLS context requires an active database transaction");
        }
        // Hibernate supplies the same physical connection used by JPA repositories.
        entityManager.unwrap(Session.class).doWork(connection -> {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("RLS context requires a non-autocommit connection");
            }
            try (var query = connection.prepareStatement("""
                    SELECT NULLIF(current_setting('app.current_tenant_id', true), ''),
                           NULLIF(current_setting('app.current_user_id', true), ''),
                           NULLIF(current_setting('app.current_trailer_qr_token', true), '')
                    """)) {
                try (var settings = query.executeQuery()) {
                    settings.next();
                    var currentTenant = settings.getString(1);
                    var currentUser = settings.getString(2);
                    if (userId != null && settings.getString(3) != null) {
                        throw new IllegalStateException("QR lookup cannot mix bootstrap context");
                    }
                    if ((currentTenant != null || currentUser != null)
                            && (!matches(currentTenant, tenantId) || !matches(currentUser, userId))) {
                        throw new IllegalStateException("RLS context cannot change within a transaction");
                    }
                }
            }
            try (var query = connection.prepareStatement("""
                    SELECT set_config('app.current_tenant_id', ?, true),
                           set_config('app.current_user_id', ?, true)
                    """)) {
                query.setString(1, tenantId == null ? "" : tenantId.toString());
                query.setString(2, userId == null ? "" : userId.toString());
                query.executeQuery().close();
            }
        });
    }

    private boolean matches(String value, UUID expected) {
        return value == null ? expected == null : expected != null && expected.toString().equals(value);
    }
}
