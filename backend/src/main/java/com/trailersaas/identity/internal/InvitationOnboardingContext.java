package com.trailersaas.identity.internal;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Internal hash-only onboarding context, never a public Tenant authorization API. */
@Component
class InvitationOnboardingContext {
    private final EntityManager manager;
    InvitationOnboardingContext(EntityManager manager) { this.manager = manager; }
    void establish(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid onboarding context."); }
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !manager.isJoinedToTransaction()) {
            throw new IllegalStateException("Onboarding requires an active database transaction.");
        }
        manager.unwrap(Session.class).doWork(connection -> {
            if (connection.getAutoCommit()) { throw new IllegalStateException("Onboarding requires a non-autocommit connection."); }
            try (var query = connection.prepareStatement("""
                    SELECT NULLIF(current_setting('app.current_tenant_id', true), ''),
                           NULLIF(current_setting('app.current_user_id', true), ''),
                           NULLIF(current_setting('app.current_invitation_hash', true), ''),
                           NULLIF(current_setting('app.current_trailer_qr_token', true), '')
                    """)) {
                try (var values = query.executeQuery()) {
                    values.next();
                    if (values.getString(1) != null || values.getString(2) != null || values.getString(4) != null
                            || (values.getString(3) != null && !hash.equals(values.getString(3)))) {
                        throw new IllegalStateException("Onboarding context cannot change or mix modes.");
                    }
                }
            }
            try (var query = connection.prepareStatement("SELECT set_config('app.current_invitation_hash', ?, true)")) {
                query.setString(1, hash);
                query.executeQuery().close();
            }
        });
    }
}
