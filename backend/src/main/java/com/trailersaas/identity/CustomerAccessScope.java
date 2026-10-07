package com.trailersaas.identity;

import java.util.Set;
import java.util.UUID;

/** Immutable capability-specific query scope. Contains no roles or persistence details. */
public record CustomerAccessScope(boolean tenantWide, Set<UUID> customerIds) {
    public CustomerAccessScope {
        customerIds = Set.copyOf(customerIds);
        if ((tenantWide && !customerIds.isEmpty()) || (!tenantWide && customerIds.isEmpty())) {
            throw new IllegalArgumentException("Scope must be Tenant-wide or contain Customer IDs");
        }
    }
}
