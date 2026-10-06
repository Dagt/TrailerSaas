package com.trailersaas.identity.internal;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.trailersaas.identity.TenantContext;
import com.trailersaas.identity.TenantTransactionExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class PostgresTenantTransactionExecutor implements TenantTransactionExecutor {
    private final TransactionTemplate transaction;
    private final TenantContext context;

    PostgresTenantTransactionExecutor(PlatformTransactionManager manager, TenantContext context) {
        this.transaction = new TransactionTemplate(manager);
        this.context = context;
    }

    @Override
    public <T> T execute(UUID tenantId, Supplier<T> work) {
        Objects.requireNonNull(tenantId, "Tenant is required");
        Objects.requireNonNull(work, "Work is required");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Tenant executor requires no existing transaction");
        }
        return transaction.execute(status -> {
            context.establishTenant(tenantId);
            return work.get();
        });
    }
}
