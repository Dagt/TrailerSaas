package com.trailersaas.orders.internal;

import java.util.UUID;
import com.trailersaas.customers.CustomerSiteUsageGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class OrderCustomerSiteUsageGuard implements CustomerSiteUsageGuard {
    private final OrderRepository repository;
    OrderCustomerSiteUsageGuard(OrderRepository repository) { this.repository = repository; }

    @Override public boolean hasActiveUse(UUID tenantId, UUID customerId, UUID siteId) {
        return repository.hasActiveSiteUse(tenantId, customerId, siteId);
    }
}
