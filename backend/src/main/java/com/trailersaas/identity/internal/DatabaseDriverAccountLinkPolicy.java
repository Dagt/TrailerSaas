package com.trailersaas.identity.internal;

import java.util.UUID;
import com.trailersaas.identity.DriverAccountLinkPolicy;
import com.trailersaas.identity.DriverAccountLinkRejectedException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseDriverAccountLinkPolicy implements DriverAccountLinkPolicy {
    private final EntityManager manager;
    DatabaseDriverAccountLinkPolicy(EntityManager manager) { this.manager = manager; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireEligible(UUID tenantId, UUID targetUserId) {
        // No context establishment/bypass here: the actor's authorization must already have pinned it.
        boolean eligible = Boolean.TRUE.equals(manager.createNativeQuery("""
                SELECT COALESCE(
                    NULLIF(current_setting('app.current_tenant_id',true),'')::uuid = :tenant
                    AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL
                    AND EXISTS (
                        SELECT 1 FROM tenant_memberships m JOIN role_assignments r
                            ON r.tenant_id=m.tenant_id AND r.membership_id=m.id
                        WHERE m.tenant_id=:tenant AND m.user_id=:user AND m.status='ACTIVE'
                            AND r.role_code='DRIVER' AND r.scope_type='TENANT' AND r.customer_id IS NULL
                    ), false)
                """, Boolean.class).setParameter("tenant", tenantId).setParameter("user", targetUserId).getSingleResult());
        if (!eligible) { throw new DriverAccountLinkRejectedException(); }
    }
}
