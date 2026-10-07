package com.trailersaas.customers.internal;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "operational_locations")
class OperationalLocationEntity extends VersionedMasterData {
    @Column(name = "site_id", nullable = false, updatable = false) UUID siteId;
    @Column(name = "customer_id") UUID customerId;
    @Column(nullable = false) String type;
    String code;
    @Column(nullable = false) boolean active = true;
    protected OperationalLocationEntity() {}
}
