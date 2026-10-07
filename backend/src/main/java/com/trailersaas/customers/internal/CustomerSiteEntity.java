package com.trailersaas.customers.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "customer_sites")
class CustomerSiteEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) UUID tenantId;
    @Column(name = "customer_id", nullable = false, updatable = false) UUID customerId;
    @Column(name = "site_id", nullable = false, updatable = false) UUID siteId;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    protected CustomerSiteEntity() {}
}
